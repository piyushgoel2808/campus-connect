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
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automated, polite, fault-isolated, idempotent synchronization pipeline for BVICAM portal
 * syllabi, info pages, notices (past + latest), and MCA/BA(JMC) course materials.
 * <p>
 * Runs single-threaded via {@link AtomicBoolean}, checks Ollama availability before contacting
 * {@code bvicam.in}, skips already-indexed immutable PDFs prior to download, isolates individual
 * broken PDFs from aborting full backfills, and pauses immediately on HTTP 429/503 throttling.
 */
@Service
public class BvicamPortalSyncService {

    private static final Logger log = LoggerFactory.getLogger(BvicamPortalSyncService.class);
    private static final int MAX_CONSECUTIVE_TRANSPORT_ERRORS = 5;
    private static final int MAX_RECENT_ISSUES = 25;

    private static final List<NoticeFeed> NOTICE_FEEDS = List.of(
            new NoticeFeed("/WebsiteGetFunction/GetCircularAdmissionList", "Admission Notice", true),
            new NoticeFeed("/WebsiteGetFunction/GetExaminationList", "Examination Notice", true),
            new NoticeFeed("/WebsiteGetFunction/GetPlacementList", "Placement Notice", true),
            new NoticeFeed("/WebsiteGetFunction/GetCircularTimeTableList", "Time Table Notice", true),
            new NoticeFeed("/WebsiteGetFunction/GetCircularStudentWelfareList", "Student Welfare Notice", true),
            new NoticeFeed("/WebsiteGetFunction/GetCircularAttendanceList", "Attendance Notice", true),
            new NoticeFeed("/WebsiteGetFunction/GetCircularActivitiesList", "Activities & Webinar Notice", true),
            new NoticeFeed("/Circular/GetCircularAchievementsList", "Achievements Notice", false),
            new NoticeFeed("/WebsiteGetFunction/GetCircularList", "General Notice", true)
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
        SYLLABUS_AND_INFO,
        NOTICES,
        COURSE_MATERIALS,
        COURSE_MATERIALS_MCA,
        COURSE_MATERIALS_BAJMC,
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
     * Runs the weekly full portal sync (syllabi, info pages, notices, and course materials).
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
            tracker.setPhase("CHECKING_OLLAMA", "Verifying Ollama availability before contacting BVICAM...",
                    "Checking local Ollama runtime...");
            if (!ollamaClientService.isHealthy()) {
                tracker.abort("ABORTED_OLLAMA_OFFLINE",
                        "Ollama service or required chat/embedding models are offline; skipped BVICAM requests.",
                        "Ollama unavailable before sync start");
                log.warn("Aborting BVICAM sync ({}) before hitting portal: Ollama is offline or missing models.", effectiveMode);
                return tracker.snapshot(false);
            }

            // Phase 1: Syllabi & Core Info Pages first (~12 docs, fastest immediate value for GradBot)
            if (effectiveMode == SyncMode.SYLLABUS_AND_INFO || effectiveMode == SyncMode.FULL) {
                syncSyllabusAndInfoPages(tracker);
            }

            // Phase 2: Notices & Circulars (newest first, across all active feeds including ActivitiesNotices)
            if (!tracker.isAborted() && (effectiveMode == SyncMode.NOTICES || effectiveMode == SyncMode.FULL)) {
                syncNotices(tracker);
            }

            // Phase 3: MCA & BA(JMC) Course Materials (subject directory index + individual PDFs)
            if (!tracker.isAborted() && isCourseMaterialMode(effectiveMode)) {
                syncCourseMaterials(tracker, effectiveMode);
            }

            if (!tracker.isAborted()) {
                tracker.complete();
            }
            BvicamSyncStatusDto finalSnapshot = tracker.snapshot(false);
            log.info("BVICAM sync finished: mode={}, phase={}, discovered={}, new={}, ocrOrStub={}, updated={}, skipped={}, failed={}",
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
    // Phase 1: Syllabi & Core HTML Pages (Fees, Admissions, Placements, Contact)
    // -------------------------------------------------------------------------

    private void syncSyllabusAndInfoPages(SyncTracker tracker) {
        tracker.setPhase("SYLLABUS_AND_INFO",
                "Syncing BVICAM fee structure, admission guidelines, and official syllabi...",
                "Fetching core portal pages...");

        for (InfoPageSpec page : INFO_PAGES) {
            if (tracker.isAborted()) {
                return;
            }
            tracker.addDiscovered(1);
            tracker.setCurrentActivity("Portal page: " + page.title());
            try {
                WebPageContent content = portalClient.fetchHtmlPage(page.path());
                tracker.resetTransportErrors();
                if (content.extractedText() == null || content.extractedText().isBlank()) {
                    tracker.recordItemFailure("Info page " + page.path() + " returned empty text");
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
                handleUpsertIllegalState("Info page " + page.path(), ex, tracker);
            } catch (IOException ex) {
                handleRemoteException("Info page " + page.path(), ex, true, tracker);
            } catch (Exception ex) {
                tracker.recordItemFailure("Info page " + page.path() + ": " + ex.getMessage());
            }
        }

        Map<String, ScopedSyllabusLink> syllabusLinksByUrl = new LinkedHashMap<>();
        for (SyllabusFeed feed : SYLLABUS_FEEDS) {
            if (tracker.isAborted()) {
                return;
            }
            tracker.setCurrentActivity("Scanning syllabus links: " + feed.programmeLabel());
            try {
                List<SyllabusLink> links = portalClient.fetchSyllabusLinks(feed.path());
                tracker.resetTransportErrors();
                for (SyllabusLink link : links) {
                    syllabusLinksByUrl.putIfAbsent(link.pdfUrl(), new ScopedSyllabusLink(feed.programmeLabel(), link));
                }
            } catch (RemoteThrottleException ex) {
                throw ex;
            } catch (IOException ex) {
                handleRemoteException("Syllabus page " + feed.path(), ex, true, tracker);
            }
        }

        List<ScopedSyllabusLink> allSyllabi = new ArrayList<>(syllabusLinksByUrl.values());
        tracker.addDiscovered(allSyllabi.size());

        for (int i = 0; i < allSyllabi.size(); i++) {
            if (tracker.isAborted()) {
                return;
            }
            ScopedSyllabusLink scoped = allSyllabi.get(i);
            SyllabusLink link = scoped.link();
            String externalKey = buildUrlExternalKey("syllabus:", link.pdfUrl());
            if (ingestionService.isDocumentIndexed(externalKey)) {
                tracker.incrementSkipped();
                continue;
            }

            tracker.setCurrentActivity(String.format("Syllabus %d/%d: %s", i + 1, allSyllabi.size(), link.title()));
            try {
                byte[] pdfBytes = portalClient.fetchPdfBytes(link.pdfUrl());
                tracker.resetTransportErrors();
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
                handleUpsertIllegalState("Syllabus PDF " + link.title(), ex, tracker);
            } catch (IOException ex) {
                handleRemoteException("Syllabus PDF " + link.pdfUrl(), ex, false, tracker);
            } catch (Exception ex) {
                tracker.recordItemFailure("Syllabus PDF " + link.pdfUrl() + ": " + ex.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 2: All Historical & Recent Circulars / Notices (Newest First)
    // -------------------------------------------------------------------------

    private void syncNotices(SyncTracker tracker) {
        tracker.setPhase("NOTICES", "Fetching BVICAM circular and notice feeds...",
                "Querying notice feeds...");
        Map<Long, DiscoveredCircular> discoveredById = new LinkedHashMap<>();

        for (NoticeFeed feed : NOTICE_FEEDS) {
            if (tracker.isAborted()) {
                return;
            }
            tracker.setCurrentActivity("Scanning notice feed: " + feed.label());
            try {
                List<CircularEntry> entries = portalClient.fetchCirculars(feed.path());
                tracker.resetTransportErrors();
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
                boolean isBrokenOptional = !feed.required()
                        || (ex instanceof HttpStatusException s && s.getStatusCode() == 404);
                if (isBrokenOptional) {
                    log.debug("Optional notice feed {} skipped ({})", feed.path(), ex.getMessage());
                } else {
                    handleRemoteException("Notice feed " + feed.path(), ex, true, tracker);
                }
            }
        }

        // Index newest circulars first so recent notices become searchable immediately
        List<DiscoveredCircular> allCirculars = new ArrayList<>(discoveredById.values());
        allCirculars.sort(Comparator.comparingLong((DiscoveredCircular c) -> c.entry().circularId()).reversed());

        tracker.addDiscovered(allCirculars.size());
        tracker.setMessage("Syncing " + allCirculars.size() + " discovered BVICAM notices (newest first; skipping already-indexed)...");

        for (int i = 0; i < allCirculars.size(); i++) {
            if (tracker.isAborted()) {
                return;
            }
            DiscoveredCircular item = allCirculars.get(i);
            CircularEntry entry = item.entry();
            String externalKey = "circular:" + entry.circularId();

            // Critical politeness check: never download immutable circular PDFs already indexed in DB/Qdrant
            if (ingestionService.isDocumentIndexed(externalKey)) {
                tracker.incrementSkipped();
                continue;
            }

            tracker.setCurrentActivity(String.format("Notice %d/%d (#%d): %s",
                    i + 1, allCirculars.size(), entry.circularId(), abbreviate(entry.description(), 64)));

            String pdfUrl = portalClient.buildCircularPdfUrl(entry.circularId());
            String labels = String.join(", ", item.labels());
            String date = entry.date() != null ? entry.date().trim() : "";
            String contextPrefix = "BVICAM Official Notice (" + labels + ")"
                    + (date.isBlank() ? "" : " | Published Date: " + date)
                    + " | Circular ID: " + entry.circularId();

            try {
                byte[] pdfBytes = portalClient.fetchPdfBytes(pdfUrl);
                tracker.resetTransportErrors();

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
                handleUpsertIllegalState("Circular #" + entry.circularId(), ex, tracker);
            } catch (IOException ex) {
                // If ViewPdf returns 404/500 or an HTML error page, index the circular metadata stub instead of dropping it
                if (!isTransportFailure(ex)) {
                    tracker.resetTransportErrors();
                    tryIndexMissingNoticeMetadata(externalKey, entry, labels, date, pdfUrl, contextPrefix, ex.getMessage(), tracker);
                } else {
                    handleRemoteException("Circular #" + entry.circularId(), ex, true, tracker);
                }
            } catch (Exception ex) {
                tracker.recordItemFailure("Circular #" + entry.circularId() + ": " + ex.getMessage());
            }
        }
    }

    private void tryIndexMissingNoticeMetadata(String externalKey,
                                               CircularEntry entry,
                                               String labels,
                                               String date,
                                               String pdfUrl,
                                               String contextPrefix,
                                               String fetchError,
                                               SyncTracker tracker) {
        try {
            String stubText = contextPrefix
                    + "\nOfficial Notice Title: " + entry.description()
                    + (date.isBlank() ? "" : "\nPublished Date: " + date)
                    + "\nOfficial Portal Link: " + pdfUrl
                    + "\nNote: PDF binary was unavailable on BVICAM portal during sync (" + fetchError + ").";
            SyncUpsertResult stubResult = ingestionService.upsertAutoSyncedDocument(
                    externalKey,
                    entry.description(),
                    DocumentCategory.NOTICE,
                    pdfUrl,
                    "circular-" + entry.circularId() + ".pdf",
                    date,
                    contextPrefix,
                    stubText,
                    null
            );
            tracker.recordUpsertOutcome(stubResult.status());
            tracker.addIssueOnly("Circular #" + entry.circularId() + " (" + abbreviate(entry.description(), 45)
                    + "): PDF missing on portal (" + fetchError + "); indexed metadata stub.");
        } catch (IllegalStateException ex) {
            handleUpsertIllegalState("Circular #" + entry.circularId() + " metadata stub", ex, tracker);
        } catch (Exception stubEx) {
            tracker.recordItemFailure("Circular #" + entry.circularId() + " metadata fallback: " + stubEx.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3: Course Materials across MCA & BA(JMC) Programmes / Semesters
    // -------------------------------------------------------------------------

    private void syncCourseMaterials(SyncTracker tracker, SyncMode mode) {
        tracker.setPhase("COURSE_MATERIALS",
                "Discovering BVICAM courses, semesters, and subject materials...",
                "Fetching BVICAM course list...");

        List<DropdownOption> courses;
        try {
            courses = new ArrayList<>(portalClient.fetchCourses());
            tracker.resetTransportErrors();
        } catch (RemoteThrottleException ex) {
            throw ex;
        } catch (IOException ex) {
            handleRemoteException("Course dropdown fetch", ex, true, tracker);
            return;
        }

        // Order MCA programmes before BA(JMC) and filter if caller asked for MCA-only or BA(JMC)-only
        courses.sort(Comparator.comparingInt(c -> isBajmcCourse(c) ? 1 : 0));
        List<DropdownOption> targetCourses = courses.stream()
                .filter(c -> mode != SyncMode.COURSE_MATERIALS_MCA || !isBajmcCourse(c))
                .filter(c -> mode != SyncMode.COURSE_MATERIALS_BAJMC || isBajmcCourse(c))
                .toList();

        Map<String, ScopedMaterial> discoveredMaterials = new LinkedHashMap<>();

        for (DropdownOption course : targetCourses) {
            if (tracker.isAborted()) {
                return;
            }
            boolean isBajmc = isBajmcCourse(course);
            List<DropdownOption> semesters;
            tracker.setCurrentActivity("Fetching semesters for " + course.text());
            try {
                semesters = portalClient.fetchSemesters(course.value());
                tracker.resetTransportErrors();
            } catch (RemoteThrottleException ex) {
                throw ex;
            } catch (IOException ex) {
                handleRemoteException("Semester dropdown for " + course.text(), ex, isTransportFailure(ex), tracker);
                continue;
            }

            for (DropdownOption semester : semesters) {
                if (tracker.isAborted()) {
                    return;
                }
                List<SubjectEntry> subjects;
                tracker.setCurrentActivity("Fetching subjects: " + course.text() + " / " + semester.text());
                try {
                    subjects = portalClient.fetchSubjects(course.value(), semester.value(), isBajmc);
                    tracker.resetTransportErrors();
                } catch (RemoteThrottleException ex) {
                    throw ex;
                } catch (IOException ex) {
                    handleRemoteException("Subject list for " + course.text() + " " + semester.text(),
                            ex, isTransportFailure(ex), tracker);
                    continue;
                }

                for (SubjectEntry subject : subjects) {
                    if (tracker.isAborted()) {
                        return;
                    }
                    String subjectLabel = buildSubjectLabel(subject);
                    tracker.setCurrentActivity("Scanning resources: " + course.text() + " " + semester.text() + " — " + subjectLabel);
                    try {
                        List<CourseMaterialLink> resources = portalClient.fetchSubjectResources(subject, isBajmc);
                        tracker.resetTransportErrors();
                        if (!resources.isEmpty()) {
                            indexSubjectDirectoryOverview(course, semester, subject, subjectLabel, resources, isBajmc, tracker);
                            for (CourseMaterialLink link : resources) {
                                discoveredMaterials.putIfAbsent(
                                        link.pdfUrl(),
                                        new ScopedMaterial(course.text(), semester.text(), subject, link)
                                );
                            }
                        }
                    } catch (RemoteThrottleException ex) {
                        throw ex;
                    } catch (IOException ex) {
                        handleRemoteException("Subject resources P_Id=" + subject.paperId(),
                                ex, isTransportFailure(ex), tracker);
                    }
                }
            }
        }

        List<ScopedMaterial> allMaterials = new ArrayList<>(discoveredMaterials.values());
        tracker.addDiscovered(allMaterials.size());
        tracker.setMessage("Indexing " + allMaterials.size() + " discovered course material PDFs (skipping already-indexed)...");

        for (int i = 0; i < allMaterials.size(); i++) {
            if (tracker.isAborted()) {
                return;
            }
            ScopedMaterial item = allMaterials.get(i);
            CourseMaterialLink link = item.link();
            String externalKey = buildUrlExternalKey("material:", link.pdfUrl());
            if (ingestionService.isDocumentIndexed(externalKey)) {
                tracker.incrementSkipped();
                continue;
            }

            SubjectEntry subject = item.subject();
            String subjectLabel = buildSubjectLabel(subject);
            String title = subjectLabel.isBlank() ? link.title() : (subjectLabel + ": " + link.title());
            tracker.setCurrentActivity(String.format("Course PDF %d/%d: %s — %s",
                    i + 1, allMaterials.size(), abbreviate(subjectLabel, 28), abbreviate(link.title(), 40)));

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
                tracker.resetTransportErrors();
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
                handleUpsertIllegalState("Material PDF " + link.title(), ex, tracker);
            } catch (IOException ex) {
                // Individual missing/broken PDF under /UploadDocuments/ must never abort the remaining materials
                handleRemoteException("Material PDF " + link.title() + " (" + link.pdfUrl() + ")",
                        ex, isTransportFailure(ex), tracker);
            } catch (Exception ex) {
                tracker.recordItemFailure("Material PDF " + link.pdfUrl() + ": " + ex.getMessage());
            }
        }
    }

    /**
     * Immediately indexes a lightweight subject resource directory entry so students can ask GradBot
     * for any subject's units, assignments, and past paper links right away while full PDFs continue embedding.
     */
    private void indexSubjectDirectoryOverview(DropdownOption course,
                                               DropdownOption semester,
                                               SubjectEntry subject,
                                               String subjectLabel,
                                               List<CourseMaterialLink> resources,
                                               boolean isBajmc,
                                               SyncTracker tracker) {
        String subjectUrl = portalClient.getBaseUrl()
                + (isBajmc ? "/WebsiteGetFunction/SubjectContentViewBajmc" : "/WebsiteGetFunction/SubjectContentView")
                + "?Course_Id=" + subject.courseId()
                + "&P_Id=" + subject.paperId()
                + "&Sem_Id=" + subject.semId();
        String externalKey = "subject-index:" + subject.courseId() + ":" + subject.semId() + ":" + subject.paperId();
        String title = course.text() + " " + semester.text() + " | " + subjectLabel + " (Material Index)";
        String contextPrefix = "BVICAM Course Material Directory | Programme: " + course.text()
                + " | Semester: " + semester.text()
                + " | Subject: " + subjectLabel;

        StringBuilder body = new StringBuilder();
        body.append(contextPrefix).append("\nOfficial Subject Page: ").append(subjectUrl).append("\n");
        String lastSection = null;
        for (CourseMaterialLink link : resources) {
            String sec = (link.sectionTitle() != null && !link.sectionTitle().isBlank())
                    ? link.sectionTitle().trim()
                    : "Resources";
            if (!sec.equals(lastSection)) {
                body.append("\n[").append(sec).append("]\n");
                lastSection = sec;
            }
            body.append("- ").append(link.title()).append(": ").append(link.pdfUrl()).append("\n");
        }

        tracker.addDiscovered(1);
        try {
            SyncUpsertResult res = ingestionService.upsertAutoSyncedDocument(
                    externalKey,
                    title,
                    DocumentCategory.COURSE_MATERIAL,
                    subjectUrl,
                    null,
                    null,
                    contextPrefix,
                    body.toString(),
                    null
            );
            tracker.recordUpsertOutcome(res.status());
        } catch (IllegalStateException ex) {
            handleUpsertIllegalState("Subject index " + subjectLabel, ex, tracker);
        } catch (Exception ex) {
            tracker.recordItemFailure("Subject index " + subjectLabel + ": " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Error classification, helpers & live progress tracker
    // -------------------------------------------------------------------------

    private void handleUpsertIllegalState(String itemLabel, IllegalStateException ex, SyncTracker tracker) {
        if (!ollamaClientService.isHealthy()) {
            throw new AbortSyncException("ABORTED_OLLAMA_OFFLINE", ex.getMessage(),
                    "Ollama became unreachable mid-sync while indexing " + itemLabel);
        }
        tracker.recordItemFailure(itemLabel + " embedding error: " + ex.getMessage());
    }

    private void handleRemoteException(String label, IOException ex, boolean countsAsTransportOutage, SyncTracker tracker) {
        tracker.recordRemoteError(label + ": " + ex.getMessage(), countsAsTransportOutage && isTransportFailure(ex));
    }

    private static boolean isCourseMaterialMode(SyncMode mode) {
        return mode == SyncMode.COURSE_MATERIALS
                || mode == SyncMode.COURSE_MATERIALS_MCA
                || mode == SyncMode.COURSE_MATERIALS_BAJMC
                || mode == SyncMode.FULL;
    }

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

    /**
     * Distinguishes true network/connect/feed outages from single-document HTTP 404/500 or HTML error responses.
     */
    private static boolean isTransportFailure(IOException ex) {
        if (ex instanceof ConnectException
                || ex instanceof SocketTimeoutException
                || ex instanceof HttpTimeoutException
                || ex instanceof UnknownHostException) {
            return true;
        }
        if (ex instanceof HttpStatusException statusEx) {
            return statusEx.getStatusCode() >= 500;
        }
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase(Locale.ROOT) : "";
        return msg.contains("timed out") || msg.contains("connection refused") || msg.contains("reset by peer");
    }

    private static String abbreviate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String clean = text.trim().replaceAll("\\s+", " ");
        return clean.length() > maxLen ? clean.substring(0, maxLen) + "..." : clean;
    }

    private record NoticeFeed(String path, String label, boolean required) {
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
        private String currentActivity = "Initializing sync pipeline...";
        private String lastMessage = "Starting BVICAM portal sync...";
        private String stoppedReason;
        private boolean aborted = false;
        private int discoveredCount = 0;
        private int indexedNewCount = 0;
        private int scannedFallbackCount = 0;
        private int updatedCount = 0;
        private int skippedExistingCount = 0;
        private int failedCount = 0;
        private int consecutiveTransportErrors = 0;
        private final Deque<String> recentIssues = new ArrayDeque<>();

        SyncTracker(String mode, String triggerReason) {
            this.mode = mode;
            this.triggerReason = triggerReason;
        }

        synchronized void setPhase(String phase, String message, String activity) {
            this.phase = phase;
            this.lastMessage = message;
            this.currentActivity = activity;
        }

        synchronized void setMessage(String message) {
            this.lastMessage = message;
        }

        synchronized void setCurrentActivity(String currentActivity) {
            this.currentActivity = currentActivity;
        }

        synchronized void addDiscovered(int count) {
            this.discoveredCount += count;
        }

        synchronized void incrementSkipped() {
            this.skippedExistingCount++;
        }

        synchronized void resetTransportErrors() {
            this.consecutiveTransportErrors = 0;
        }

        synchronized void addIssueOnly(String issue) {
            log.warn("BVICAM sync notice: {}", issue);
            appendIssue(issue);
        }

        synchronized void recordItemFailure(String detail) {
            this.failedCount++;
            log.warn("BVICAM sync item failed: {}", detail);
            appendIssue(detail);
        }

        synchronized void recordRemoteError(String detail, boolean countsTowardTransportBreaker) {
            this.failedCount++;
            log.warn("BVICAM sync error ({}): {}", countsTowardTransportBreaker ? "transport" : "item", detail);
            appendIssue(detail);
            if (countsTowardTransportBreaker) {
                this.consecutiveTransportErrors++;
                if (this.consecutiveTransportErrors >= MAX_CONSECUTIVE_TRANSPORT_ERRORS) {
                    throw new AbortSyncException(
                            "ABORTED_REMOTE_ERRORS",
                            "Paused sync politely after " + MAX_CONSECUTIVE_TRANSPORT_ERRORS
                                    + " consecutive BVICAM connection/server errors. Last error: " + detail,
                            "Consecutive BVICAM transport/server errors (" + MAX_CONSECUTIVE_TRANSPORT_ERRORS + ")"
                    );
                }
            }
        }

        private void appendIssue(String issue) {
            if (issue == null || issue.isBlank()) {
                return;
            }
            if (recentIssues.size() >= MAX_RECENT_ISSUES) {
                recentIssues.removeFirst();
            }
            recentIssues.addLast(issue);
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
            this.currentActivity = "Stopped: " + phase;
            this.stoppedReason = stoppedReason;
            this.finishedAt = LocalDateTime.now();
            appendIssue(message);
        }

        synchronized void complete() {
            this.phase = "COMPLETED";
            this.finishedAt = LocalDateTime.now();
            this.currentActivity = "Completed";
            this.lastMessage = String.format(
                    "BVICAM sync (%s) complete: %d discovered, %d new (%d OCR/stubs), %d updated, %d skipped, %d failed.",
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
                    currentActivity,
                    startedAt,
                    running ? null : finishedAt,
                    discoveredCount,
                    indexedNewCount,
                    scannedFallbackCount,
                    updatedCount,
                    skippedExistingCount,
                    failedCount,
                    lastMessage,
                    stoppedReason,
                    List.copyOf(recentIssues)
            );
        }
    }
}
