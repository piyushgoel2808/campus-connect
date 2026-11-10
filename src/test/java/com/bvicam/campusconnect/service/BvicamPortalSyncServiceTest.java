package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.BvicamSyncStatusDto;
import com.bvicam.campusconnect.dto.KnowledgeDocumentDto;
import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.SourceType;
import com.bvicam.campusconnect.repository.KnowledgeDocumentRepository;
import com.bvicam.campusconnect.service.BvicamPortalClient.CircularEntry;
import com.bvicam.campusconnect.service.BvicamPortalClient.CourseMaterialLink;
import com.bvicam.campusconnect.service.BvicamPortalClient.DropdownOption;
import com.bvicam.campusconnect.service.BvicamPortalClient.SubjectEntry;
import com.bvicam.campusconnect.service.BvicamPortalClient.SyllabusLink;
import com.bvicam.campusconnect.service.BvicamPortalClient.WebPageContent;
import com.bvicam.campusconnect.service.BvicamPortalSyncService.SyncMode;
import com.bvicam.campusconnect.service.KnowledgeIngestionService.SyncResultStatus;
import com.bvicam.campusconnect.service.KnowledgeIngestionService.SyncUpsertResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BvicamPortalSyncServiceTest {

    @Mock
    private BvicamPortalClient portalClient;

    @Mock
    private KnowledgeIngestionService ingestionService;

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private OllamaClientService ollamaClientService;

    private BvicamPortalSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new BvicamPortalSyncService(
                portalClient,
                ingestionService,
                documentRepository,
                ollamaClientService
        );
        lenient().when(portalClient.buildCircularPdfUrl(anyLong()))
                .thenAnswer(inv -> "https://bvicam.in/WebsiteGetFunction/ViewPdf?id=" + inv.getArgument(0));
    }

    @Test
    @DisplayName("Aborts immediately before making any HTTP call to BVICAM if Ollama is offline")
    void runSync_abortsBeforePortalRequestsWhenOllamaOffline() {
        when(ollamaClientService.isHealthy()).thenReturn(false);

        BvicamSyncStatusDto status = syncService.runSync(SyncMode.FULL, "test");

        assertFalse(status.running());
        assertEquals("ABORTED_OLLAMA_OFFLINE", status.phase());
        assertTrue(status.stoppedReason().contains("Ollama unavailable"));
        verifyNoInteractions(portalClient);
        verifyNoInteractions(ingestionService);
    }

    @Test
    @DisplayName("Deduplicates notices across feeds, skips indexed PDFs without downloading, and indexes new + scanned notices")
    void runSync_noticesSkipsExistingWithoutHttpDownloadAndIndexesNewAndScanned() throws IOException {
        when(ollamaClientService.isHealthy()).thenReturn(true);

        when(portalClient.fetchCirculars("/WebsiteGetFunction/GetCircularAdmissionList"))
                .thenReturn(List.of(new CircularEntry(4001L, "Admission Notice 2025", "10-08-2025")));
        when(portalClient.fetchCirculars("/WebsiteGetFunction/GetCircularList"))
                .thenReturn(List.of(
                        new CircularEntry(4001L, "Admission Notice 2025", "10-08-2025"),
                        new CircularEntry(4002L, "MCA Exam Datesheet 2024", "12-12-2024"),
                        new CircularEntry(4003L, "Scanned Welfare Circular", "15-01-2025")
                ));
        when(portalClient.fetchCirculars("/WebsiteGetFunction/GetExaminationList")).thenReturn(List.of());
        when(portalClient.fetchCirculars("/WebsiteGetFunction/GetPlacementList")).thenReturn(List.of());
        when(portalClient.fetchCirculars("/WebsiteGetFunction/GetCircularTimeTableList")).thenReturn(List.of());
        when(portalClient.fetchCirculars("/WebsiteGetFunction/GetCircularStudentWelfareList")).thenReturn(List.of());
        when(portalClient.fetchCirculars("/WebsiteGetFunction/GetCircularAttendanceList")).thenReturn(List.of());

        when(ingestionService.isDocumentIndexed("circular:4001")).thenReturn(true);
        when(ingestionService.isDocumentIndexed("circular:4002")).thenReturn(false);
        when(ingestionService.isDocumentIndexed("circular:4003")).thenReturn(false);

        byte[] samplePdf = "%PDF-1.4\nsample".getBytes(StandardCharsets.UTF_8);
        when(portalClient.fetchPdfBytes("https://bvicam.in/WebsiteGetFunction/ViewPdf?id=4002")).thenReturn(samplePdf);
        when(portalClient.fetchPdfBytes("https://bvicam.in/WebsiteGetFunction/ViewPdf?id=4003")).thenReturn(samplePdf);

        when(ingestionService.upsertAutoSyncedDocument(
                eq("circular:4002"), anyString(), eq(DocumentCategory.NOTICE),
                eq("https://bvicam.in/WebsiteGetFunction/ViewPdf?id=4002"), eq("circular-4002.pdf"),
                eq("12-12-2024"), argThat(p -> p.contains("General Notice")), isNull(), eq(samplePdf)))
                .thenReturn(new SyncUpsertResult(SyncResultStatus.CREATED, dummyDto(1L)));

        when(ingestionService.upsertAutoSyncedDocument(
                eq("circular:4003"), anyString(), eq(DocumentCategory.NOTICE),
                eq("https://bvicam.in/WebsiteGetFunction/ViewPdf?id=4003"), eq("circular-4003.pdf"),
                eq("15-01-2025"), anyString(), isNull(), eq(samplePdf)))
                .thenReturn(new SyncUpsertResult(SyncResultStatus.SCANNED_FALLBACK_CREATED, dummyDto(2L)));

        BvicamSyncStatusDto status = syncService.runSync(SyncMode.NOTICES, "test-notices");

        assertEquals("COMPLETED", status.phase());
        assertEquals(3, status.discoveredCount());
        assertEquals(1, status.skippedExistingCount());
        assertEquals(2, status.indexedNewCount());
        assertEquals(1, status.scannedFallbackCount());
        assertEquals(0, status.failedCount());

        verify(portalClient, never()).fetchPdfBytes("https://bvicam.in/WebsiteGetFunction/ViewPdf?id=4001");
        verify(portalClient, never()).fetchCourses();
    }

    @Test
    @DisplayName("Full sync ingests HTML fee/admission pages, syllabus PDFs, and both MCA & BA(JMC) course materials")
    void runSync_fullSyncIngestsInfoPagesSyllabiAndCourseMaterials() throws IOException {
        when(ollamaClientService.isHealthy()).thenReturn(true);
        when(portalClient.fetchCirculars(anyString())).thenReturn(List.of());

        when(portalClient.fetchHtmlPage(anyString())).thenAnswer(inv -> {
            String path = inv.getArgument(0);
            return new WebPageContent("https://bvicam.in" + path, "BVICAM Page " + path, "Structured text for " + path);
        });

        when(portalClient.fetchSyllabusLinks("/Home/MCASyllabus"))
                .thenReturn(List.of(new SyllabusLink("MCA 2024 Syllabus", "https://bvicam.in/Content/ForntEnd/PDF/MCA_2024.pdf")));
        when(portalClient.fetchSyllabusLinks("/Home/BajmcSyllabus")).thenReturn(List.of());

        when(portalClient.fetchCourses()).thenReturn(List.of(
                new DropdownOption("1", "MCA"),
                new DropdownOption("2", "BA(JMC)")
        ));
        when(portalClient.fetchSemesters("1")).thenReturn(List.of(new DropdownOption("1", "Sem I")));
        when(portalClient.fetchSemesters("2")).thenReturn(List.of(new DropdownOption("3", "Sem III")));

        SubjectEntry mcaSub = new SubjectEntry("1", "1", 10L, "MCA-101", "Java Programming");
        SubjectEntry bajmcSub = new SubjectEntry("2", "3", 22L, "BAJMC-203", "Media Ethics");
        when(portalClient.fetchSubjects("1", "1", false)).thenReturn(List.of(mcaSub));
        when(portalClient.fetchSubjects("2", "3", true)).thenReturn(List.of(bajmcSub));

        when(portalClient.fetchSubjectResources(mcaSub, false))
                .thenReturn(List.of(new CourseMaterialLink("Unit I", "OOP Basics", "https://bvicam.in/UploadDocuments/CourseMaterial/java_u1.pdf")));
        when(portalClient.fetchSubjectResources(bajmcSub, true))
                .thenReturn(List.of(new CourseMaterialLink("Unit II", "Press Council Notes", "https://bvicam.in/UploadDocuments/CourseMaterial/media_u2.pdf")));

        byte[] pdfBytes = "%PDF-1.4\ntext".getBytes(StandardCharsets.UTF_8);
        when(portalClient.fetchPdfBytes(anyString())).thenReturn(pdfBytes);
        when(ingestionService.isDocumentIndexed(anyString())).thenReturn(false);
        when(ingestionService.upsertAutoSyncedDocument(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SyncUpsertResult(SyncResultStatus.CREATED, dummyDto(10L)));

        BvicamSyncStatusDto status = syncService.runSync(SyncMode.FULL, "test-full");

        assertEquals("COMPLETED", status.phase());
        assertEquals(10, status.discoveredCount()); // 7 info pages + 1 syllabus + 2 course materials
        assertEquals(10, status.indexedNewCount());

        verify(ingestionService).upsertAutoSyncedDocument(
                anyString(), argThat(t -> t.contains("MCA-101 - Java Programming")),
                eq(DocumentCategory.COURSE_MATERIAL),
                eq("https://bvicam.in/UploadDocuments/CourseMaterial/java_u1.pdf"),
                eq("java_u1.pdf"), isNull(),
                argThat(p -> p.contains("Programme: MCA") && p.contains("Semester: Sem I")),
                isNull(), eq(pdfBytes)
        );
        verify(ingestionService).upsertAutoSyncedDocument(
                anyString(), argThat(t -> t.contains("BAJMC-203 - Media Ethics")),
                eq(DocumentCategory.COURSE_MATERIAL),
                eq("https://bvicam.in/UploadDocuments/CourseMaterial/media_u2.pdf"),
                eq("media_u2.pdf"), isNull(),
                argThat(p -> p.contains("Programme: BA(JMC)") && p.contains("Semester: Sem III")),
                isNull(), eq(pdfBytes)
        );
    }

    @Test
    @DisplayName("Stops politely right away when BVICAM responds with HTTP 429 or 503 throttling")
    void runSync_stopsImmediatelyOnRemoteThrottleException() throws IOException {
        when(ollamaClientService.isHealthy()).thenReturn(true);
        when(portalClient.fetchCirculars("/WebsiteGetFunction/GetCircularAdmissionList"))
                .thenThrow(new BvicamPortalClient.RemoteThrottleException(429, "Rate limit signaled"));

        BvicamSyncStatusDto status = syncService.runSync(SyncMode.FULL, "test-throttle");

        assertEquals("STOPPED_THROTTLED", status.phase());
        assertTrue(status.stoppedReason().contains("Rate limit signaled"));
        verify(portalClient, times(1)).fetchCirculars(anyString());
        verify(portalClient, never()).fetchCourses();
    }

    @Test
    @DisplayName("Trips circuit breaker cleanly after 3 consecutive network/5xx errors without hammering portal")
    void runSync_tripsCircuitBreakerAfterThreeConsecutiveServerErrors() throws IOException {
        when(ollamaClientService.isHealthy()).thenReturn(true);
        when(portalClient.fetchCirculars(anyString())).thenThrow(new IOException("Connection timed out"));

        BvicamSyncStatusDto status = syncService.runSync(SyncMode.FULL, "test-circuit");

        assertEquals("ABORTED_REMOTE_ERRORS", status.phase());
        assertEquals(3, status.failedCount());
        verify(portalClient, times(3)).fetchCirculars(anyString());
        verify(portalClient, never()).fetchCourses();
    }

    @Test
    @DisplayName("Triggers initial startup backfill only when database contains zero AUTO_SYNC documents")
    void onApplicationReady_runsBackfillOnlyWhenAutoSyncEmpty() throws IOException {
        when(documentRepository.countBySourceType(SourceType.AUTO_SYNC)).thenReturn(12L);
        syncService.onApplicationReady();
        verifyNoInteractions(ollamaClientService);
        verifyNoInteractions(portalClient);

        when(documentRepository.countBySourceType(SourceType.AUTO_SYNC)).thenReturn(0L);
        when(ollamaClientService.isHealthy()).thenReturn(false);
        syncService.onApplicationReady();
        verify(ollamaClientService, times(1)).isHealthy();
    }

    @Test
    @DisplayName("Honors ai.sync.enabled toggle on scheduled 24-hour notice sync")
    void scheduledNoticeSync_respectsEnabledFlag() throws IOException {
        syncService.setSyncEnabled(false);
        syncService.scheduledNoticeSync();
        verifyNoInteractions(ollamaClientService);
        verifyNoInteractions(portalClient);

        syncService.setSyncEnabled(true);
        when(ollamaClientService.isHealthy()).thenReturn(true);
        when(portalClient.fetchCirculars(anyString())).thenReturn(List.of());
        syncService.scheduledNoticeSync();

        verify(portalClient, times(7)).fetchCirculars(anyString());
        verify(portalClient, never()).fetchCourses();
        verify(portalClient, never()).fetchHtmlPage(anyString());
    }

    private static KnowledgeDocumentDto dummyDto(Long id) {
        return new KnowledgeDocumentDto(
                id,
                "Test Doc " + id,
                DocumentCategory.NOTICE,
                SourceType.AUTO_SYNC,
                "https://bvicam.in/WebsiteGetFunction/ViewPdf?id=" + id,
                "doc.pdf",
                2,
                LocalDateTime.now(),
                "01-01-2025",
                "INDEXED"
        );
    }
}
