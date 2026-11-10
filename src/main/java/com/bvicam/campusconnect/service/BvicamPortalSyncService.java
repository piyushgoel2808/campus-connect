package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.BvicamSyncStatusDto;
import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.SourceType;
import com.bvicam.campusconnect.repository.KnowledgeDocumentRepository;
import com.bvicam.campusconnect.service.BvicamPortalClient.CircularEntry;
import com.bvicam.campusconnect.service.BvicamPortalClient.CourseMaterialLink;
import com.bvicam.campusconnect.service.BvicamPortalClient.DropdownOption;
import com.bvicam.campusconnect.service.BvicamPortalClient.HttpStatusException;
import com.bvicam.campusconnect.service.BvicamPortalClient.RemoteThrottleException;
import com.bvicam.campusconnect.service.BvicamPortalClient.SubjectEntry;
import com.bvicam.campusconnect.service.BvicamPortalClient.SyllabusLink;
import com.bvicam.campusconnect.service.BvicamPortalClient.WebPageContent;
import com.bvicam.campusconnect.service.KnowledgeIngestionService.SyncResultStatus;
import com.bvicam.campusconnect.service.KnowledgeIngestionService.SyncUpsertResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automated, polite, idempotent synchronization pipeline for BVICAM portal notices,
 * syllabi, informational pages, and MCA/BA(JMC) course materials.
 * <p>
 * Runs strictly single-threaded via {@link AtomicBoolean} lock, checks Ollama readiness before
 * touching {@code bvicam.in}, skips already-indexed immutable PDFs prior to download, and
 * backs off cleanly on HTTP 429/503 or repeated remote errors.
 */
@Service
public class BvicamPortalSyncService {

    private static final Logger log = LoggerFactory.getLogger(BvicamPortalSyncService.class);
    private static final int MAX_CONSECUTIVE_REMOTE_ERRORS = 3;

    private static final List<NoticeFeed> NOTICE_FEEDS = List.of(
            new NoticeFeed("/WebsiteGetFunction/GetCircularAdmissionList", "Admission Notice"),
            new NoticeFeed("/WebsiteGetFunction/GetExaminationList", "Examination Notice"),
            new NoticeFeed("/WebsiteGetFunction/GetPlacementList", "Placement Notice"),
            new NoticeFeed("/WebsiteGetFunction/GetCircularTimeTableList", "Time Table Notice"),
            new NoticeFeed("/WebsiteGetFunction/GetCircularStudentWelfareList", "Student Welfare Notice"),
            new NoticeFeed("/WebsiteGetFunction/GetCircularAttendanceList", "Attendance Notice"),
            new NoticeFeed("/WebsiteGetFunction/GetCircularList", "General Notice")
    );

    private static final List<SyllabusFeed> SYLLABUS_FEEDS = List.of(
            new SyllabusFeed("/Home/MCASyllabus", "MCA Syllabus"),
            new SyllabusFeed("/Home/BajmcSyllabus", "BA(JMC) Syllabus")
    );

    private static final List<InfoPageSpec> INFO_PAGES = List.of(
            new InfoPageSpec("/Home/Feestructure", "BVICAM Fee Structure", DocumentCategory.BVICAM_WEB),
            new InfoPageSpec("/Home/Admission", "BVICAM Admission Guidelines & Eligibility", DocumentCategory.BVICAM_WEB),
            new InfoPageSpec("/Home/MCAProgram", "BVICAM MCA Programme Overview", DocumentCategory.SYLLABUS),
            new InfoPageSpec("/Home/BAjmcProgram", "BVICAM BA(JMC) Programme Overview", DocumentCategory.SYLLABUS),
            new InfoPageSpec("/Home/ContactUs", "BVICAM Campus Contact & Address", DocumentCategory.BVICAM_WEB),
            new InfoPageSpec("/Home/PlacementHistoryMCA", "BVICAM MCA Placement History & Recruiters", DocumentCategory.BVICAM_WEB),
            new InfoPageSpec("/Home/TPCellMCA", "BVICAM Training & Placement Cell (MCA)", DocumentCategory.BVICAM_WEB)
    );

    /**
     * Supported synchronization scopes.
     */
    public enum SyncMode {
        NOTICES,
        SYLLABUS_AND_INFO,
        COURSE_MATERIALS,
        FULL
    }

    private final BvicamPortalClient portalClient;
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeDocumentRepository documentRepository;
    private final OllamaClientService ollamaClientService;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private volatile SyncTracker activeTracker;
    private volatile BvicamSyncStatusDto lastStatus = BvicamSyncStatusDto.idle();

    @Value("${ai.sync.enabled:true}")
    private boolean syncEnabled = true;

    @Value("${ai.sync.startup-backfill:true}")
    private boolean startupBackfillEnabled = true;

    @Autowired
    public BvicamPortalSyncService(BvicamPortalClient portalClient,
                                   KnowledgeIngestionService ingestionService,
                                   KnowledgeDocumentRepository documentRepository,
                                   OllamaClientService ollamaClientService) {
        this.portalClient = portalClient;
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
        this.ollamaClientService = ollamaClientService;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public void setStartupBackfillEnabled(boolean startupBackfillEnabled) {
        this.startupBackfillEnabled = startupBackfillEnabled;
    }

    /**
     * Returns the current live progress snapshot when running, or the last completed snapshot otherwise.
     */
    public BvicamSyncStatusDto getStatus() {
        SyncTracker tracker = activeTracker;
        if (tracker != null && syncInProgress.get()) {
            return tracker.snapshot(true);
        }
        return lastStatus;
    }

    /**
     * Automatically triggers an initial historical backfill once on application startup
     * if no {@link SourceType#AUTO_SYNC} documents exist yet.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!syncEnabled || !startupBackfillEnabled) {
            return;
        }
        try {
            long existingCount = documentRepository.countBySourceType(SourceType.AUTO_SYNC);
            if (existingCount == 0L) {
                log.info("No auto-synced BVICAM documents found; launching initial full historical backfill...");
                runSync(SyncMode.FULL, "startup-initial-backfill");
            } else {
                log.info("BVICAM knowledge base already contains {} auto-synced documents; skipping startup backfill.", existingCount);
            }
        } catch (Exception e) {
            log.warn("Startup BVICAM backfill aborted safely: {}", e.getMessage());
        }
    }

    /**
     * Runs the 24-hour notice & circular connector schedule.
     */
    @Scheduled(cron = "${ai.sync.notice-cron:0 0 2 * * *}")
    public void scheduledNoticeSync() {
        if (!syncEnabled) {
            return;
        }
        runSync(SyncMode.NOTICES, "scheduled-24h");
    }

    /**
     * Runs the weekly full portal sync (notices, syllabi, info pages, and course materials).
     */
    @Scheduled(cron = "${ai.sync.full-cron:0 30 3 * * 0}")
    public void scheduledFullSync() {
        if (!syncEnabled) {
            return;
        }
        runSync(SyncMode.FULL, "scheduled-weekly-full");
    }

    /**
     * Triggers an asynchronous sync execution requested by an administrator.
     */
    @Async
    public void triggerSyncAsync(SyncMode mode) {
        runSync(mode != null ? mode : SyncMode.FULL, "admin-manual");
    }

    /**
     * Executes a single-flight, polite synchronization cycle and returns the final status snapshot.
     */
    public BvicamSyncStatusDto runSync(SyncMode mode, String triggerReason) {
        SyncMode effectiveMode = mode != null ? mode : SyncMode.FULL;
        if (!syncInProgress.compareAndSet(false, true)) {
            log.info("BVICAM sync ({}, reason={}) skipped because another sync is already in progress.", effectiveMode, triggerReason);
            return getStatus();
        }

        SyncTracker tracker = new SyncTracker(effectiveMode.name(), triggerReason);
        activeTracker = tracker;
        lastStatus = tracker.snapshot(true);

        try {
            tracker.setPhase("CHECKING_OLLAMA", "Verifying local Ollama model availability before contacting BVICAM...");
            if (!ollamaClientService.isHealthy()) {
                tracker.abort("ABORTED_OLLAMA_OFFLINE",
                        "Ollama service or required chat/embedding models are offline; skipped BVICAM requests.",
                        "Ollama unavailable before sync start");
                log.warn("Aborting BVICAM sync ({}) before hitting portal: Ollama is offline or missing models.", effectiveMode);
                return tracker.snapshot(false);
            }

            if (effectiveMode == SyncMode.NOTICES || effectiveMode == SyncMode.FULL) {
                syncNotices(tracker);
            }
            if (!tracker.isAborted() && (effectiveMode == SyncMode.SYLLABUS_AND_INFO || effectiveMode == SyncMode.FULL)) {
                syncSyllabusAndInfoPages(tracker);
            }
            if (!tracker.isAborted() && (effectiveMode == SyncMode.COURSE_MATERIALS || effectiveMode == SyncMode.FULL)) {
                syncCourseMaterials(tracker);
            }

            if (!tracker.isAborted()) {
                tracker.complete();
            }
            BvicamSyncStatusDto finalSnapshot = tracker.snapshot(false);
            log.info("BVICAM sync finished: mode={}, phase={}, discovered={}, new={}, scannedFallback={}, updated={}, skipped={}, failed={}",
                    finalSnapshot.mode(),
                    finalSnapshot.phase(),
                    finalSnapshot.discoveredCount(),
                    finalSnapshot.indexedNewCount(),
                    finalSnapshot.scannedFallbackCount(),
                    finalSnapshot.updatedCount(),
                    finalSnapshot.skippedExistingCount(),
                    finalSnapshot.failedCount());
            return finalSnapshot;
        } catch (RemoteThrottleException ex) {
            tracker.abort("STOPPED_THROTTLED", ex.getMessage(), ex.getMessage());
            log.warn("BVICAM sync paused politely after throttle response: {}", ex.getMessage());
            return tracker.snapshot(false);
        } catch (AbortSyncException ex) {
            tracker.abort(ex.getPhase(), ex.getMessage(), ex.getReason());
            log.warn("BVICAM sync stopped early ({}): {}", ex.getPhase(), ex.getMessage());
            return tracker.snapshot(false);
        } catch (Exception ex) {
            tracker.abort("ABORTED_ERROR", "Unexpected sync error: " + ex.getMessage(), ex.getMessage());
            log.error("Unexpected error in BVICAM sync pipeline", ex);
            return tracker.snapshot(false);
        } finally {
            lastStatus = tracker.snapshot(false);
            activeTracker = null;
            syncInProgress.set(false);
        }
    }

    // -------------------------------------------------------------------------
    // Phase A: Historical & Recent Circulars / Notices
    // -------------------------------------------------------------------------

    private void syncNotices(SyncTracker tracker) {
        tracker.setPhase("NOTICES", "Fetching BVICAM circular and notice feeds...");
        Map<Long, DiscoveredCircular> discoveredById = new LinkedHashMap<>();

        for (NoticeFeed feed : NOTICE_FEEDS) {
            if (tracker.isAborted()) {
                return;
            }
            try {
                List<CircularEntry> entries = portalClient.fetchCirculars(feed.path());
                tracker.resetRemoteErrors();
                for (CircularEntry entry : entries) {
                    DiscoveredCircular existing = discoveredById.get(entry.circularId());
                    if (existing == null) {
                        discoveredById.put(entry.circularId(), new DiscoveredCircular(entry, new ArrayList<>(List.of(feed.label()))));
                    } else if (!existing.labels().contains(feed.label())) {
                        existing.labels().add(feed.label());
                    }
                }
            } catch (RemoteThrottleException ex) {
                throw ex;
            } catch (IOException ex) {
                tracker.recordRemoteError("Notice feed " + feed.path() + ": " + ex.getMessage(), isServerOrNetworkError(ex));
            }
        }

        List<DiscoveredCircular> allCirculars = new ArrayList<>(discoveredById.values());
        tracker.addDiscovered(allCirculars.size());
        tracker.setMessage("Processing " + allCirculars.size() + " discovered BVICAM notices (skipping already-indexed items)...");

        for (DiscoveredCircular item : allCirculars) {
            if (tracker.isAborted()) {
                return;
            }
            CircularEntry entry = item.entry();
            String externalKey = "circular:" + entry.circularId();

            // Critical politeness check: never download immutable circular PDFs already indexed in DB/Qdrant
            if (ingestionService.isDocumentIndexed(externalKey)) {
                tracker.incrementSkipped();
                continue;
            }

            String pdfUrl = portalClient.buildCircularPdfUrl(entry.circularId());
            String labels = String.join(", ", item.labels());
            String date = entry.date() != null ? entry.date().trim() : "";
            String contextPrefix = "BVICAM Official Notice (" + labels + ")"
                    + (date.isBlank() ? "" : " | Published Date: " + date)
                    + " | Circular ID: " + entry.circularId();

            try {
                byte[] pdfBytes = portalClient.fetchPdfBytes(pdfUrl);
                tracker.resetRemoteErrors();

                SyncUpsertResult result = ingestionService.upsertAutoSyncedDocument(
                        externalKey,
                        entry.description(),
                        DocumentCategory.NOTICE,
                        pdfUrl,
                        "circular-" + entry.circularId() + ".pdf",
                        date,
                        contextPrefix,
                        null,
                        pdfBytes
                );
                tracker.recordUpsertOutcome(result.status());
            } catch (RemoteThrottleException ex) {
                throw ex;
            } catch (IllegalStateException ex) {
                throw new AbortSyncException("ABORTED_OLLAMA_OFFLINE", ex.getMessage(), "Ollama became unreachable mid-sync");
            } catch (IOException ex) {
                tracker.recordRemoteError("Circular #" + entry.circularId() + ": " + ex.getMessage(), isServerOrNetworkError(ex));
            } catch (Exception ex) {
                tracker.incrementFailed();
                log.warn("Failed processing circular #{}: {}", entry.circularId(), ex.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase B: Syllabi & Core HTML Pages (Fees, Admissions, Placements, Contact)
    // -------------------------------------------------------------------------

    private void syncSyllabusAndInfoPages(SyncTracker tracker) {
        tracker.setPhase("SYLLABUS_AND_INFO", "Syncing BVICAM fee structure, admission pages, and syllabi...");

        // 1. Core HTML pages (lightweight text GETs, SHA-256 hash prevents redundant Ollama re-embedding)
        for (InfoPageSpec page : INFO_PAGES) {
            if (tracker.isAborted()) {
                return;
            }
            tracker.addDiscovered(1);
            try {
                WebPageContent content = portalClient.fetchHtmlPage(page.path());
                tracker.resetRemoteErrors();
                if (content.extractedText() == null || content.extractedText().isBlank()) {
                    tracker.incrementFailed();
                    continue;
                }
                String externalKey = "web:" + page.path();
                String contextPrefix = "BVICAM Official Portal Page: " + page.title() + " | URL: " + content.url();
                SyncUpsertResult result = ingestionService.upsertAutoSyncedDocument(
                        externalKey,
                        page.title(),
                        page.category(),
                        content.url(),
                        null,
                        null,
                        contextPrefix,
                        content.extractedText(),
                        null
                );
                tracker.recordUpsertOutcome(result.status());
            } catch (RemoteThrottleException ex) {
                throw ex;
            } catch (IllegalStateException ex) {
                throw new AbortSyncException("ABORTED_OLLAMA_OFFLINE", ex.getMessage(), "Ollama became unreachable mid-sync");
            } catch (IOException ex) {
                tracker.recordRemoteError("Info page " + page.path() + ": " + ex.getMessage(), isServerOrNetworkError(ex));
            } catch (Exception ex) {
                tracker.incrementFailed();
                log.warn("Failed syncing info page {}: {}", page.path(), ex.getMessage());
            }
        }

        // 2. Syllabus PDFs linked from /Home/MCASyllabus and /Home/BajmcSyllabus
        Map<String, ScopedSyllabusLink> syllabusLinksByUrl = new LinkedHashMap<>();
        for (SyllabusFeed feed : SYLLABUS_FEEDS) {
            if (tracker.isAborted()) {
                return;
            }
            try {
                List<SyllabusLink> links = portalClient.fetchSyllabusLinks(feed.path());
                tracker.resetRemoteErrors();
                for (SyllabusLink link : links) {
                    syllabusLinksByUrl.putIfAbsent(link.pdfUrl(), new ScopedSyllabusLink(feed.programmeLabel(), link));
                }
            } catch (RemoteThrottleException ex) {
                throw ex;
            } catch (IOException ex) {
                tracker.recordRemoteError("Syllabus page " + feed.path() + ": " + ex.getMessage(), isServerOrNetworkError(ex));
            }
        }

        List<ScopedSyllabusLink> allSyllabi = new ArrayList<>(syllabusLinksByUrl.values());
        tracker.addDiscovered(allSyllabi.size());

        for (ScopedSyllabusLink scoped : allSyllabi) {
            if (tracker.isAborted()) {
                return;
            }
            SyllabusLink link = scoped.link();
            String externalKey = buildUrlExternalKey("syllabus:", link.pdfUrl());
            if (ingestionService.isDocumentIndexed(externalKey)) {
                tracker.incrementSkipped();
                continue;
            }

            try {
                byte[] pdfBytes = portalClient.fetchPdfBytes(link.pdfUrl());
                tracker.resetRemoteErrors();
                String title = scoped.programmeLabel() + " - " + link.title();
                String contextPrefix = "BVICAM Official Syllabus (" + scoped.programmeLabel() + "): " + link.title();
                SyncUpsertResult result = ingestionService.upsertAutoSyncedDocument(
                        externalKey,
                        title,
                        DocumentCategory.SYLLABUS,
                        link.pdfUrl(),
                        extractFileNameFromUrl(link.pdfUrl()),
                        null,
                        contextPrefix,
                        null,
                        pdfBytes
                );
                tracker.recordUpsertOutcome(result.status());
            } catch (RemoteThrottleException ex) {
                throw ex;
            } catch (IllegalStateException ex) {
                throw new AbortSyncException("ABORTED_OLLAMA_OFFLINE", ex.getMessage(), "Ollama became unreachable mid-sync");
            } catch (IOException ex) {
                tracker.recordRemoteError("Syllabus PDF " + link.pdfUrl() + ": " + ex.getMessage(), isServerOrNetworkError(ex));
            } catch (Exception ex) {
                tracker.incrementFailed();
                log.warn("Failed syncing syllabus PDF {}: {}", link.pdfUrl(), ex.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase C: Course Materials across MCA & BA(JMC) Programmes / Semesters
    // -------------------------------------------------------------------------

    private void syncCourseMaterials(SyncTracker tracker) {
        tracker.setPhase("COURSE_MATERIALS", "Discovering BVICAM courses, semesters, and subject materials...");
        List<DropdownOption> courses;
        try {
            courses = portalClient.fetchCourses();
            tracker.resetRemoteErrors();
        } catch (RemoteThrottleException ex) {
            throw ex;
        } catch (IOException ex) {
            tracker.recordRemoteError("Course dropdown fetch: " + ex.getMessage(), isServerOrNetworkError(ex));
            return;
        }

        Map<String, ScopedMaterial> discoveredMaterials = new LinkedHashMap<>();

        for (DropdownOption course : courses) {
            if (tracker.isAborted()) {
                return;
            }
            boolean isBajmc = isBajmcCourse(course);
            List<DropdownOption> semesters;
            try {
                semesters = portalClient.fetchSemesters(course.value());
                tracker.resetRemoteErrors();
            } catch (RemoteThrottleException ex) {
                throw ex;
            } catch (IOException ex) {
                tracker.recordRemoteError("Semester dropdown for course " + course.text() + ": " + ex.getMessage(), isServerOrNetworkError(ex));
                continue;
            }

            for (DropdownOption semester : semesters) {
                if (tracker.isAborted()) {
                    return;
                }
                List<SubjectEntry> subjects;
                try {
                    subjects = portalClient.fetchSubjects(course.value(), semester.value(), isBajmc);
                    tracker.resetRemoteErrors();
                } catch (RemoteThrottleException ex) {
                    throw ex;
                } catch (IOException ex) {
                    tracker.recordRemoteError("Subject list for " + course.text() + " sem " + semester.text() + ": " + ex.getMessage(),
                            isServerOrNetworkError(ex));
                    continue;
                }

                for (SubjectEntry subject : subjects) {
                    if (tracker.isAborted()) {
                        return;
                    }
                    try {
                        List<CourseMaterialLink> resources = portalClient.fetchSubjectResources(subject, isBajmc);
                        tracker.resetRemoteErrors();
                        for (CourseMaterialLink link : resources) {
                            discoveredMaterials.putIfAbsent(link.pdfUrl(), new ScopedMaterial(course.text(), semester.text(), subject, link));
                        }
                    } catch (RemoteThrottleException ex) {
                        throw ex;
                    } catch (IOException ex) {
                        tracker.recordRemoteError("Subject resources P_Id=" + subject.paperId() + ": " + ex.getMessage(),
                                isServerOrNetworkError(ex));
                    }
                }
            }
        }

        List<ScopedMaterial> allMaterials = new ArrayList<>(discoveredMaterials.values());
        tracker.addDiscovered(allMaterials.size());
        tracker.setMessage("Indexing " + allMaterials.size() + " discovered course material PDFs...");

        for (ScopedMaterial item : allMaterials) {
            if (tracker.isAborted()) {
                return;
            }
            CourseMaterialLink link = item.link();
            String externalKey = buildUrlExternalKey("material:", link.pdfUrl());
            if (ingestionService.isDocumentIndexed(externalKey)) {
                tracker.incrementSkipped();
                continue;
            }

            SubjectEntry subject = item.subject();
            String subjectLabel = buildSubjectLabel(subject);
            String title = subjectLabel.isBlank() ? link.title() : (subjectLabel + ": " + link.title());
            StringBuilder prefix = new StringBuilder();
            prefix.append("BVICAM Course Material | Programme: ").append(item.courseName())
                    .append(" | Semester: ").append(item.semesterName());
            if (!subjectLabel.isBlank()) {
                prefix.append(" | Subject: ").append(subjectLabel);
            }
            if (link.sectionTitle() != null && !link.sectionTitle().isBlank()) {
                prefix.append(" | Unit/Section: ").append(link.sectionTitle());
            }

            try {
                byte[] pdfBytes = portalClient.fetchPdfBytes(link.pdfUrl());
                tracker.resetRemoteErrors();
                SyncUpsertResult result = ingestionService.upsertAutoSyncedDocument(
                        externalKey,
                        title,
                        DocumentCategory.COURSE_MATERIAL,
                        link.pdfUrl(),
                        extractFileNameFromUrl(link.pdfUrl()),
                        null,
                        prefix.toString(),
                        null,
                        pdfBytes
                );
                tracker.recordUpsertOutcome(result.status());
            } catch (RemoteThrottleException ex) {
                throw ex;
            } catch (IllegalStateException ex) {
                throw new AbortSyncException("ABORTED_OLLAMA_OFFLINE", ex.getMessage(), "Ollama became unreachable mid-sync");
            } catch (IOException ex) {
                tracker.recordRemoteError("Material PDF " + link.pdfUrl() + ": " + ex.getMessage(), isServerOrNetworkError(ex));
            } catch (Exception ex) {
                tracker.incrementFailed();
                log.warn("Failed processing material PDF {}: {}", link.pdfUrl(), ex.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pure helpers & internal state tracking
    // -------------------------------------------------------------------------

    static boolean isBajmcCourse(DropdownOption course) {
        if (course == null) {
            return false;
        }
        String val = course.value() != null ? course.value().trim() : "";
        String txt = course.text() != null ? course.text().toUpperCase(Locale.ROOT) : "";
        return "2".equals(val) || "4".equals(val) || txt.contains("BA") || txt.contains("JMC");
    }

    static String buildUrlExternalKey(String prefix, String url) {
        String candidate = prefix + (url != null ? url.trim() : "");
        if (candidate.length() <= 500) {
            return candidate;
        }
        return prefix + "sha256:" + KnowledgeIngestionService.sha256Hex(url);
    }

    private static String buildSubjectLabel(SubjectEntry subject) {
        String code = subject.paperCode() != null ? subject.paperCode().trim() : "";
        String title = subject.paperTitle() != null ? subject.paperTitle().trim() : "";
        if (!code.isEmpty() && !title.isEmpty()) {
            return code + " - " + title;
        }
        return !title.isEmpty() ? title : code;
    }

    private static String extractFileNameFromUrl(String url) {
        if (url == null) {
            return null;
        }
        try {
            String path = URI.create(url).getPath();
            if (path == null) {
                return null;
            }
            int idx = path.lastIndexOf('/');
            String name = idx >= 0 ? path.substring(idx + 1) : path;
            if (name.isBlank()) {
                return null;
            }
            return name.length() > 250 ? name.substring(name.length() - 250) : name;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isServerOrNetworkError(IOException ex) {
        if (ex instanceof HttpStatusException statusEx) {
            int code = statusEx.getStatusCode();
            return code >= 500;
        }
        return true;
    }

    private record NoticeFeed(String path, String label) {
    }

    private record DiscoveredCircular(CircularEntry entry, List<String> labels) {
    }

    private record SyllabusFeed(String path, String programmeLabel) {
    }

    private record ScopedSyllabusLink(String programmeLabel, SyllabusLink link) {
    }

    private record InfoPageSpec(String path, String title, DocumentCategory category) {
    }

    private record ScopedMaterial(String courseName, String semesterName, SubjectEntry subject, CourseMaterialLink link) {
    }

    private static class AbortSyncException extends RuntimeException {
        private final String phase;
        private final String reason;

        AbortSyncException(String phase, String message, String reason) {
            super(message);
            this.phase = phase;
            this.reason = reason;
        }

        String getPhase() {
            return phase;
        }

        String getReason() {
            return reason;
        }
    }

    private static final class SyncTracker {
        private final String mode;
        private final String triggerReason;
        private final LocalDateTime startedAt = LocalDateTime.now();
        private LocalDateTime finishedAt;
        private String phase = "INITIALIZING";
        private String lastMessage = "Starting BVICAM portal sync...";
        private String stoppedReason;
        private boolean aborted = false;
        private int discoveredCount = 0;
        private int indexedNewCount = 0;
        private int scannedFallbackCount = 0;
        private int updatedCount = 0;
        private int skippedExistingCount = 0;
        private int failedCount = 0;
        private int consecutiveRemoteErrors = 0;

        SyncTracker(String mode, String triggerReason) {
            this.mode = mode;
            this.triggerReason = triggerReason;
        }

        synchronized void setPhase(String phase, String message) {
            this.phase = phase;
            this.lastMessage = message;
        }

        synchronized void setMessage(String message) {
            this.lastMessage = message;
        }

        synchronized void addDiscovered(int count) {
            this.discoveredCount += count;
        }

        synchronized void incrementSkipped() {
            this.skippedExistingCount++;
        }

        synchronized void incrementFailed() {
            this.failedCount++;
        }

        synchronized void resetRemoteErrors() {
            this.consecutiveRemoteErrors = 0;
        }

        synchronized void recordRemoteError(String detail, boolean countsTowardCircuitBreaker) {
            this.failedCount++;
            log.warn("BVICAM sync item error ({}): {}", countsTowardCircuitBreaker ? "remote" : "client", detail);
            if (countsTowardCircuitBreaker) {
                this.consecutiveRemoteErrors++;
                if (this.consecutiveRemoteErrors >= MAX_CONSECUTIVE_REMOTE_ERRORS) {
                    throw new AbortSyncException(
                            "ABORTED_REMOTE_ERRORS",
                            "Paused sync politely after " + MAX_CONSECUTIVE_REMOTE_ERRORS
                                    + " consecutive BVICAM network/5xx errors. Last error: " + detail,
                            "Consecutive BVICAM server/network errors (" + MAX_CONSECUTIVE_REMOTE_ERRORS + ")"
                    );
                }
            }
        }

        synchronized void recordUpsertOutcome(SyncResultStatus status) {
            switch (status) {
                case CREATED -> this.indexedNewCount++;
                case SCANNED_FALLBACK_CREATED -> {
                    this.indexedNewCount++;
                    this.scannedFallbackCount++;
                }
                case UPDATED -> this.updatedCount++;
                case SKIPPED_UNCHANGED -> this.skippedExistingCount++;
            }
        }

        synchronized void abort(String phase, String message, String stoppedReason) {
            this.aborted = true;
            this.phase = phase;
            this.lastMessage = message;
            this.stoppedReason = stoppedReason;
            this.finishedAt = LocalDateTime.now();
        }

        synchronized void complete() {
            this.phase = "COMPLETED";
            this.finishedAt = LocalDateTime.now();
            this.lastMessage = String.format(
                    "BVICAM sync (%s) complete: %d discovered, %d new (%d scanned-PDF stubs), %d updated, %d skipped, %d failed.",
                    triggerReason, discoveredCount, indexedNewCount, scannedFallbackCount, updatedCount, skippedExistingCount, failedCount
            );
        }

        synchronized boolean isAborted() {
            return aborted;
        }

        synchronized BvicamSyncStatusDto snapshot(boolean running) {
            return new BvicamSyncStatusDto(
                    running,
                    mode,
                    phase,
                    startedAt,
                    running ? null : finishedAt,
                    discoveredCount,
                    indexedNewCount,
                    scannedFallbackCount,
                    updatedCount,
                    skippedExistingCount,
                    failedCount,
                    lastMessage,
                    stoppedReason
            );
        }
    }
}
