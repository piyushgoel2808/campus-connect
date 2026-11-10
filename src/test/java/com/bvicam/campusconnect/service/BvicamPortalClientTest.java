package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.service.BvicamPortalClient.CircularEntry;
import com.bvicam.campusconnect.service.BvicamPortalClient.CourseMaterialLink;
import com.bvicam.campusconnect.service.BvicamPortalClient.DropdownOption;
import com.bvicam.campusconnect.service.BvicamPortalClient.RawHttpResponse;
import com.bvicam.campusconnect.service.BvicamPortalClient.RemoteThrottleException;
import com.bvicam.campusconnect.service.BvicamPortalClient.SubjectEntry;
import com.bvicam.campusconnect.service.BvicamPortalClient.SyllabusLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BvicamPortalClientTest {

    @Test
    @DisplayName("Parses both normal and ASP.NET MVC double-encoded circular JSON payloads")
    void parseCircularListJson_unwrapsDoubleEncodedAspNetJson() throws IOException {
        String innerJson = """
                [
                  {"Circular_Id": 4337, "Circular_Description": "Notification for Admission 2026-27", "Circular_Date": "19-08-2026"},
                  {"Circular_Id": 0, "Circular_Description": "Invalid Entry", "Circular_Date": "01-01-2026"},
                  {"Circular_Id": 4110, "Circular_Description": "MCA End Term Examination Date Sheet", "Circular_Date": "10-12-2024"}
                ]
                """;
        String doubleEncoded = "\"[\\n  {\\\"Circular_Id\\\": 4337, \\\"Circular_Description\\\": \\\"Notification for Admission 2026-27\\\", \\\"Circular_Date\\\": \\\"19-08-2026\\\"},\\n  {\\\"Circular_Id\\\": 4110, \\\"Circular_Description\\\": \\\"MCA End Term Examination Date Sheet\\\", \\\"Circular_Date\\\": \\\"10-12-2024\\\"}\\n]\"";

        List<CircularEntry> direct = BvicamPortalClient.parseCircularListJson(innerJson);
        assertEquals(2, direct.size());
        assertEquals(4337L, direct.get(0).circularId());
        assertEquals("Notification for Admission 2026-27", direct.get(0).description());
        assertEquals("19-08-2026", direct.get(0).date());

        List<CircularEntry> unwrapped = BvicamPortalClient.parseCircularListJson(doubleEncoded);
        assertEquals(2, unwrapped.size());
        assertEquals(4110L, unwrapped.get(1).circularId());
    }

    @Test
    @DisplayName("Parses dropdown options while filtering empty and --Select-- placeholder rows")
    void parseDropdownOptions_filtersPlaceholders() throws IOException {
        String doubleEncoded = "\"[{\\\"Value\\\":\\\"0\\\",\\\"Text\\\":\\\"--Select Course--\\\"},{\\\"Value\\\":\\\"1\\\",\\\"Text\\\":\\\"MCA\\\"},{\\\"Value\\\":\\\"2\\\",\\\"Text\\\":\\\"BA(JMC)\\\"}]\"";

        List<DropdownOption> options = BvicamPortalClient.parseDropdownOptions(doubleEncoded);

        assertEquals(2, options.size());
        assertEquals("1", options.get(0).value());
        assertEquals("MCA", options.get(0).text());
        assertEquals("2", options.get(1).value());
        assertEquals("BA(JMC)", options.get(1).text());
    }

    @Test
    @DisplayName("Parses MCA and BA(JMC) subject payloads using Paper_Code, Paper_Title, and fallback IDs")
    void parseSubjectListJson_extractsPaperCodeAndTitle() throws IOException {
        String json = """
                [
                  {"P_Id": 215, "Paper_Code": "MCA-101", "Paper_Title": "Discrete Structures"},
                  {"P_Id": 0, "Paper_Code": "BAD", "Paper_Title": "Skip Me"}
                ]
                """;

        List<SubjectEntry> subjects = BvicamPortalClient.parseSubjectListJson(json, "1", "1");

        assertEquals(1, subjects.size());
        assertEquals("1", subjects.get(0).courseId());
        assertEquals("1", subjects.get(0).semId());
        assertEquals(215L, subjects.get(0).paperId());
        assertEquals("MCA-101", subjects.get(0).paperCode());
        assertEquals("Discrete Structures", subjects.get(0).paperTitle());
    }

    @Test
    @DisplayName("Extracts SubjectContentView resource PDFs grouped by panels, encoding spaces and deduplicating URLs")
    void parseSubjectResourceHtml_groupsPanelsEncodesSpacesAndDeduplicates() {
        String html = """
                <html><body>
                  <div class="detail-content-area">
                    <div class="panel">
                      <div class="panel-heading"><h4 class="panel-title">Unit I Notes</h4></div>
                      <ul class="list-group">
                        <li class="list-group-item">
                          <a href="\\UploadDocuments\\CourseMaterial\\BA(JMC) 203 Notes.pdf">Unit I Lecture Notes</a>
                        </li>
                        <li class="list-group-item">
                          <a href="/UploadDocuments/CourseMaterial/BA(JMC)%20203%20Notes.pdf">Duplicate Link</a>
                        </li>
                      </ul>
                    </div>
                  </div>
                </body></html>
                """;

        List<CourseMaterialLink> links = BvicamPortalClient.parseSubjectResourceHtml(
                html,
                "https://bvicam.in/WebsiteGetFunction/SubjectContentViewBajmc?Course_Id=2&P_Id=5&Sem_Id=3"
        );

        assertEquals(1, links.size());
        assertEquals("Unit I Notes", links.get(0).sectionTitle());
        assertEquals("Unit I Lecture Notes", links.get(0).title());
        assertEquals("https://bvicam.in/UploadDocuments/CourseMaterial/BA(JMC)%20203%20Notes.pdf", links.get(0).pdfUrl());
    }

    @Test
    @DisplayName("Extracts syllabus PDF titles from table row labels when anchor text is generic 'Download'")
    void parseSyllabusLinksHtml_usesTableRowTitleWhenAnchorTextIsGeneric() {
        String html = """
                <html><body>
                  <div class="detail-content-area">
                    <table>
                      <tr>
                        <td>MCA 2 Year Scheme &amp; Syllabus (2024 Onwards)</td>
                        <td><a href="/Content/ForntEnd/PDF/MCA Syllabus 2024.pdf">Download</a></td>
                      </tr>
                    </table>
                  </div>
                </body></html>
                """;

        List<SyllabusLink> links = BvicamPortalClient.parseSyllabusLinksHtml(html, "https://bvicam.in/Home/MCASyllabus");

        assertEquals(1, links.size());
        assertEquals("MCA 2 Year Scheme & Syllabus (2024 Onwards)", links.get(0).title());
        assertEquals("https://bvicam.in/Content/ForntEnd/PDF/MCA%20Syllabus%202024.pdf", links.get(0).pdfUrl());
    }

    @Test
    @DisplayName("Enforces polite >=500ms floor delay sequentially between outbound portal calls")
    void politeDelay_clampsMinimum500MsAndEnforcesSpacingBetweenSequentialCalls() throws IOException {
        List<Long> recordedSleeps = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger(0);
        BvicamPortalClient.HttpTransport transport = (req, maxBytes) -> {
            callCount.incrementAndGet();
            return new RawHttpResponse(200, "application/json", null, "[]".getBytes(StandardCharsets.UTF_8));
        };

        BvicamPortalClient client = new BvicamPortalClient(null, transport, recordedSleeps::add);
        client.setRequestDelayMs(25L); // Attempt too-fast delay -> must clamp to 500ms floor

        client.fetchCirculars("/WebsiteGetFunction/GetCircularList");
        client.fetchCirculars("/WebsiteGetFunction/GetExaminationList");
        client.fetchCirculars("/WebsiteGetFunction/GetPlacementList");

        assertEquals(500L, client.getEffectiveRequestDelayMs());
        assertEquals(3, callCount.get());
        assertEquals(2, recordedSleeps.size());
        assertTrue(recordedSleeps.get(0) > 0L && recordedSleeps.get(0) <= 500L,
                "Expected floor <=500ms wait before second request, got " + recordedSleeps.get(0));
        assertTrue(recordedSleeps.get(1) > 0L && recordedSleeps.get(1) <= 500L,
                "Expected floor <=500ms wait before third request, got " + recordedSleeps.get(1));
    }

    @Test
    @DisplayName("Throws RemoteThrottleException immediately on HTTP 429 or 503 responses")
    void fetchPdfBytes_throwsRemoteThrottleExceptionOn429And503() {
        BvicamPortalClient client429 = new BvicamPortalClient(
                null,
                (req, max) -> new RawHttpResponse(429, "text/plain", null, new byte[0]),
                ms -> {}
        );
        RemoteThrottleException ex429 = assertThrows(RemoteThrottleException.class,
                () -> client429.fetchPdfBytes("https://bvicam.in/WebsiteGetFunction/ViewPdf?id=4337"));
        assertEquals(429, ex429.getStatusCode());

        BvicamPortalClient client503 = new BvicamPortalClient(
                null,
                (req, max) -> new RawHttpResponse(503, "text/html", null, new byte[0]),
                ms -> {}
        );
        RemoteThrottleException ex503 = assertThrows(RemoteThrottleException.class,
                () -> client503.fetchPdfBytes("https://bvicam.in/WebsiteGetFunction/ViewPdf?id=4337"));
        assertEquals(503, ex503.getStatusCode());
    }

    @Test
    @DisplayName("Rejects HTML error pages returned with HTTP 200 when a PDF binary is expected")
    void fetchPdfBytes_rejectsHtmlErrorPageDisguisedAs200() {
        BvicamPortalClient client = new BvicamPortalClient(
                null,
                (req, max) -> new RawHttpResponse(200, "text/html; charset=utf-8", null,
                        "<html><body>Server Error in '/lms' Application</body></html>".getBytes(StandardCharsets.UTF_8)),
                ms -> {}
        );

        IOException ex = assertThrows(IOException.class,
                () -> client.fetchPdfBytes("https://bvicam.in/WebsiteGetFunction/ViewPdf?id=999999"));
        assertTrue(ex.getMessage().contains("Expected PDF content"));
    }

    @Test
    @DisplayName("Blocks off-domain SSRF targets and unsafe redirects away from bvicam.in")
    void validateUrl_blocksOffDomainSsrfRedirectAndNonBvicamHost() {
        BvicamPortalClient client = new BvicamPortalClient(
                null,
                (req, max) -> new RawHttpResponse(302, "", "http://127.0.0.1:8080/internal", new byte[0]),
                ms -> {}
        );

        assertThrows(IllegalArgumentException.class,
                () -> client.fetchPdfBytes("https://evil.example.com/notice.pdf"));

        IllegalArgumentException redirectEx = assertThrows(IllegalArgumentException.class,
                () -> client.fetchHtmlPage("/Home/Admission"));
        assertFalse(redirectEx.getMessage().isBlank());
    }
}
