package com.bvicam.campusconnect.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Polite, strictly read-only HTTP client for public BVICAM portal endpoints and pages.
 * <p>
 * Enforces sequential inter-request throttling ({@code >= 500ms}), bounded payload sizes,
 * SSRF URL/redirect validation, and immediate abort signals on HTTP 429/503 throttling.
 */
@Service
public class BvicamPortalClient {

    private static final Logger log = LoggerFactory.getLogger(BvicamPortalClient.class);
    private static final String USER_AGENT = "CampusConnect-GradBot/1.0 (+https://bvicam.in; non-intrusive-educational-sync)";
    private static final long MIN_POLITE_DELAY_MS = 500L;
    private static final long DEFAULT_DELAY_MS = 1200L;
    private static final long DEFAULT_MAX_HTML_BYTES = 2L * 1024 * 1024;
    private static final long DEFAULT_MAX_PDF_BYTES = 20L * 1024 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KnowledgeIngestionService knowledgeIngestionService;
    private final HttpTransport httpTransport;
    private final Sleeper sleeper;
    private final AtomicLong lastRequestAtMillis = new AtomicLong(0L);
    private volatile String cachedAntiForgeryToken;

    @Value("${ai.sync.base-url:https://bvicam.in}")
    private String baseUrl = "https://bvicam.in";

    @Value("${ai.sync.request-delay-ms:1200}")
    private long requestDelayMs = DEFAULT_DELAY_MS;

    @Value("${ai.sync.max-pdf-bytes:20971520}")
    private long maxPdfBytes = DEFAULT_MAX_PDF_BYTES;

    @Autowired
    public BvicamPortalClient(KnowledgeIngestionService knowledgeIngestionService) {
        this(knowledgeIngestionService, buildDefaultTransport(), Thread::sleep);
    }

    /**
     * Package-private constructor enabling deterministic unit tests with stubbed HTTP transport and sleep hooks.
     */
    BvicamPortalClient(KnowledgeIngestionService knowledgeIngestionService,
                       HttpTransport httpTransport,
                       Sleeper sleeper) {
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.httpTransport = httpTransport;
        this.sleeper = sleeper;
    }

    private static HttpTransport buildDefaultTransport() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return (request, maxBytes) -> {
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                String location = response.headers().firstValue("Location").orElse(null);
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                byte[] body = readBoundedBytes(response.body(), maxBytes);
                return new RawHttpResponse(response.statusCode(), contentType, location, body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during BVICAM portal request to " + request.uri(), e);
            }
        };
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            this.baseUrl = stripTrailingSlash(baseUrl.trim());
        }
    }

    public String getBaseUrl() {
        return stripTrailingSlash(baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://bvicam.in");
    }

    public void setRequestDelayMs(long requestDelayMs) {
        this.requestDelayMs = requestDelayMs;
    }

    public long getEffectiveRequestDelayMs() {
        return Math.max(MIN_POLITE_DELAY_MS, requestDelayMs);
    }

    public void setMaxPdfBytes(long maxPdfBytes) {
        this.maxPdfBytes = maxPdfBytes > 0 ? maxPdfBytes : DEFAULT_MAX_PDF_BYTES;
    }

    // -------------------------------------------------------------------------
    // Public BVICAM portal fetch operations
    // -------------------------------------------------------------------------

    /**
     * Fetches circular/notice entries from a BVICAM {@code /WebsiteGetFunction/*} POST JSON endpoint.
     */
    public List<CircularEntry> fetchCirculars(String endpointPath) throws IOException {
        String url = buildPortalUrl(endpointPath);
        String body = executePostForm(url, Map.of());
        return parseCircularListJson(body);
    }

    /**
     * Builds the canonical PDF download URL for a BVICAM circular identifier.
     */
    public String buildCircularPdfUrl(long circularId) {
        return getBaseUrl() + "/WebsiteGetFunction/ViewPdf?id=" + circularId;
    }

    /**
     * Fetches course dropdown options from {@code /dropdown/getCourse}.
     */
    public List<DropdownOption> fetchCourses() throws IOException {
        String body = executePostForm(buildPortalUrl("/dropdown/getCourse"), Map.of());
        return parseDropdownOptions(body);
    }

    /**
     * Fetches semester dropdown options for a course from {@code /dropdown/getSem}.
     */
    public List<DropdownOption> fetchSemesters(String courseId) throws IOException {
        String body = executePostForm(buildPortalUrl("/dropdown/getSem"), Map.of("c_id", courseId));
        return parseDropdownOptions(body);
    }

    /**
     * Fetches subject entries for a given course and semester, automatically supplying the
     * ASP.NET MVC {@code __RequestVerificationToken} form field and session cookie required by
     * {@code GetMCASubjectData}.
     */
    public List<SubjectEntry> fetchSubjects(String courseId, String semId, boolean bajmc) throws IOException {
        String formPagePath = bajmc ? "/Home/BajmcCourse" : "/Home/MCACourse";
        String endpoint = bajmc
                ? "/WebsiteGetFunction/GetBajmcSubjectData"
                : "/WebsiteGetFunction/GetMCASubjectData";
        String targetUrl = buildPortalUrl(endpoint);
        String refererUrl = buildPortalUrl(formPagePath);

        String token = ensureAntiForgeryToken(formPagePath, false);
        Map<String, String> form = buildSubjectForm(courseId, semId, token);

        try {
            String body = executePostForm(targetUrl, form, refererUrl);
            return parseSubjectListJson(body, courseId, semId);
        } catch (HttpStatusException ex) {
            if (ex.getStatusCode() == 500 || ex.getStatusCode() == 403) {
                log.info("Refreshing BVICAM anti-forgery token after HTTP {} on {} (courseId={}, semId={})",
                        ex.getStatusCode(), endpoint, courseId, semId);
                String refreshedToken = ensureAntiForgeryToken(formPagePath, true);
                if (refreshedToken != null && !refreshedToken.isBlank()) {
                    Map<String, String> retryForm = buildSubjectForm(courseId, semId, refreshedToken);
                    String retryBody = executePostForm(targetUrl, retryForm, refererUrl);
                    return parseSubjectListJson(retryBody, courseId, semId);
                }
            }
            throw ex;
        }
    }

    private static Map<String, String> buildSubjectForm(String courseId, String semId, String antiForgeryToken) {
        Map<String, String> form = new LinkedHashMap<>();
        if (antiForgeryToken != null && !antiForgeryToken.isBlank()) {
            form.put("__RequestVerificationToken", antiForgeryToken);
        }
        form.put("Course_Id", courseId);
        form.put("Sem_Id", semId);
        return form;
    }

    /**
     * Fetches the ASP.NET MVC anti-forgery hidden token (while storing its matching cookie in {@link CookieManager}).
     */
    String ensureAntiForgeryToken(String formPagePath, boolean forceRefresh) throws IOException {
        String existing = cachedAntiForgeryToken;
        if (!forceRefresh && existing != null) {
            return existing;
        }
        try {
            RawHttpResponse pageResp = executeGet(buildPortalUrl(formPagePath), DEFAULT_MAX_HTML_BYTES);
            String html = new String(pageResp.body(), StandardCharsets.UTF_8);
            String token = extractAntiForgeryToken(html);
            if (token != null && !token.isEmpty()) {
                cachedAntiForgeryToken = token;
                return token;
            }
            return existing != null ? existing : "";
        } catch (RemoteThrottleException ex) {
            throw ex;
        } catch (IOException ex) {
            if (forceRefresh) {
                throw ex;
            }
            log.debug("Could not pre-fetch anti-forgery token from {}: {}", formPagePath, ex.getMessage());
            return "";
        }
    }

    /**
     * Extracts {@code __RequestVerificationToken} from a BVICAM HTML form.
     */
    public static String extractAntiForgeryToken(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        Element tokenInput = doc.selectFirst("#__AjaxAntiForgeryForm input[name='__RequestVerificationToken'], input[name='__RequestVerificationToken']");
        return tokenInput != null ? tokenInput.val().trim() : "";
    }

    /**
     * Fetches and parses subject course-material PDF links from the BVICAM subject content view page.
     */
    public List<CourseMaterialLink> fetchSubjectResources(SubjectEntry subject, boolean bajmc) throws IOException {
        String path = bajmc
                ? "/WebsiteGetFunction/SubjectContentViewBajmc"
                : "/WebsiteGetFunction/SubjectContentView";
        String url = getBaseUrl() + path
                + "?Course_Id=" + urlEncode(subject.courseId())
                + "&P_Id=" + subject.paperId()
                + "&Sem_Id=" + urlEncode(subject.semId());
        RawHttpResponse response = executeGet(url, DEFAULT_MAX_HTML_BYTES);
        String html = new String(response.body(), StandardCharsets.UTF_8);
        return parseSubjectResourceHtml(html, response.finalUrl() != null ? response.finalUrl() : url);
    }

    /**
     * Fetches and extracts direct PDF links from a BVICAM syllabus page.
     */
    public List<SyllabusLink> fetchSyllabusLinks(String pagePath) throws IOException {
        String url = buildPortalUrl(pagePath);
        RawHttpResponse response = executeGet(url, DEFAULT_MAX_HTML_BYTES);
        String html = new String(response.body(), StandardCharsets.UTF_8);
        return parseSyllabusLinksHtml(html, response.finalUrl() != null ? response.finalUrl() : url);
    }

    /**
     * Fetches and cleans readable HTML text from a core BVICAM informational page.
     */
    public WebPageContent fetchHtmlPage(String pagePath) throws IOException {
        String url = buildPortalUrl(pagePath);
        RawHttpResponse response = executeGet(url, DEFAULT_MAX_HTML_BYTES);
        String finalUrl = response.finalUrl() != null ? response.finalUrl() : url;
        String html = new String(response.body(), StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(html, finalUrl);
        String title = extractPageTitle(doc, finalUrl);
        String text = KnowledgeIngestionService.extractReadableHtmlText(doc);
        return new WebPageContent(finalUrl, title, text);
    }

    /**
     * Downloads raw PDF bytes from an approved BVICAM URL, verifying the payload is not an HTML error page.
     */
    public byte[] fetchPdfBytes(String pdfUrl) throws IOException {
        long limit = maxPdfBytes > 0 ? maxPdfBytes : DEFAULT_MAX_PDF_BYTES;
        RawHttpResponse response = executeGet(pdfUrl, limit);
        byte[] bytes = response.body();
        String contentType = response.contentType() != null ? response.contentType().toLowerCase(Locale.ROOT) : "";
        boolean looksPdf = (bytes.length >= 5
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-');
        if (!looksPdf && !contentType.contains("pdf")) {
            throw new IOException("Expected PDF content from " + pdfUrl + " but received content-type '" + contentType + "'");
        }
        return bytes;
    }

    // -------------------------------------------------------------------------
    // Pure parsers (ASP.NET MVC JSON double-encoding & HTML extractors)
    // -------------------------------------------------------------------------

    /**
     * Parses BVICAM circular JSON payloads, automatically unwrapping ASP.NET MVC double-encoded JSON strings.
     */
    public static List<CircularEntry> parseCircularListJson(String rawBody) throws IOException {
        JsonNode array = parsePotentiallyDoubleEncodedArray(rawBody);
        List<CircularEntry> entries = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isObject()) {
                continue;
            }
            long id = node.path("Circular_Id").asLong(0L);
            if (id <= 0L) {
                id = node.path("circular_Id").asLong(node.path("circularId").asLong(0L));
            }
            if (id <= 0L) {
                continue;
            }
            String desc = firstNonBlankText(node, "Circular_Description", "circular_Description", "circularDescription", "Title");
            String date = firstNonBlankText(node, "Circular_Date", "circular_Date", "circularDate", "Date");
            entries.add(new CircularEntry(
                    id,
                    desc.isBlank() ? ("BVICAM Circular #" + id) : desc,
                    date
            ));
        }
        return entries;
    }

    /**
     * Parses dropdown options ({@code Value}, {@code Text}), filtering empty or "--Select--" placeholder rows.
     */
    public static List<DropdownOption> parseDropdownOptions(String rawBody) throws IOException {
        JsonNode array = parsePotentiallyDoubleEncodedArray(rawBody);
        List<DropdownOption> options = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isObject()) {
                continue;
            }
            String value = firstNonBlankText(node, "Value", "value", "Id", "id");
            String text = firstNonBlankText(node, "Text", "text", "Name", "name");
            if (value.isBlank() || "-1".equals(value) || "0".equals(value)) {
                continue;
            }
            String normalizedText = text.toLowerCase(Locale.ROOT);
            if (normalizedText.contains("select")) {
                continue;
            }
            options.add(new DropdownOption(value, text.isBlank() ? value : text));
        }
        return options;
    }

    /**
     * Parses MCA / BA(JMC) subject lists returned by {@code GetMCASubjectData} and {@code GetBajmcSubjectData}.
     */
    public static List<SubjectEntry> parseSubjectListJson(String rawBody,
                                                            String fallbackCourseId,
                                                            String fallbackSemId) throws IOException {
        JsonNode array = parsePotentiallyDoubleEncodedArray(rawBody);
        List<SubjectEntry> subjects = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isObject()) {
                continue;
            }
            long paperId = node.path("P_Id").asLong(node.path("p_Id").asLong(node.path("paperId").asLong(0L)));
            if (paperId <= 0L) {
                continue;
            }
            String courseId = firstNonBlankText(node, "Course_Id", "course_Id", "courseId");
            String semId = firstNonBlankText(node, "Sem_Id", "sem_Id", "semId");
            String paperCode = firstNonBlankText(node, "Paper_Code", "paper_Code", "paperCode");
            String paperTitle = firstNonBlankText(node, "Paper_Title", "paper_Title", "paperTitle");
            subjects.add(new SubjectEntry(
                    courseId.isBlank() ? fallbackCourseId : courseId,
                    semId.isBlank() ? fallbackSemId : semId,
                    paperId,
                    paperCode,
                    paperTitle
            ));
        }
        return subjects;
    }

    /**
     * Parses BVICAM {@code SubjectContentView} HTML pages and extracts grouped course-material PDF links.
     */
    public static List<CourseMaterialLink> parseSubjectResourceHtml(String html, String pageBaseUrl) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Document doc = Jsoup.parse(html, pageBaseUrl != null ? pageBaseUrl : "https://bvicam.in");
        Element root = doc.selectFirst(".detail-content-area");
        if (root == null) {
            root = doc.body();
        }
        if (root == null) {
            return List.of();
        }

        Map<String, CourseMaterialLink> byUrl = new LinkedHashMap<>();
        for (Element panel : root.select(".panel")) {
            Element titleEl = panel.selectFirst(".panel-heading .panel-title, .panel-heading");
            String sectionTitle = titleEl != null ? titleEl.text().trim() : "";
            for (Element anchor : panel.select(".list-group-item a[href], a[href]")) {
                addCourseMaterialIfPdf(anchor, sectionTitle, pageBaseUrl, byUrl);
            }
        }

        // Capture any additional direct PDF links inside detail-content-area outside Bootstrap .panel blocks
        for (Element anchor : root.select("a[href]")) {
            addCourseMaterialIfPdf(anchor, "", pageBaseUrl, byUrl);
        }

        return new ArrayList<>(byUrl.values());
    }

    /**
     * Parses BVICAM syllabus HTML pages and extracts deduplicated syllabus PDF links with meaningful titles.
     */
    public static List<SyllabusLink> parseSyllabusLinksHtml(String html, String pageBaseUrl) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Document doc = Jsoup.parse(html, pageBaseUrl != null ? pageBaseUrl : "https://bvicam.in");
        Element root = doc.selectFirst(".detail-content-area");
        if (root == null) {
            root = doc.body();
        }
        if (root == null) {
            return List.of();
        }

        Map<String, SyllabusLink> byUrl = new LinkedHashMap<>();
        for (Element anchor : root.select("a[href]")) {
            String resolved = resolvePdfUrl(anchor, pageBaseUrl);
            if (resolved == null || byUrl.containsKey(resolved)) {
                continue;
            }
            String title = extractAnchorTitle(anchor, resolved);
            byUrl.put(resolved, new SyllabusLink(title, resolved));
        }
        return new ArrayList<>(byUrl.values());
    }

    // -------------------------------------------------------------------------
    // HTTP execution, politeness pacing, validation & helpers
    // -------------------------------------------------------------------------

    private RawHttpResponse executeGet(String initialUrl, long maxBytes) throws IOException {
        URI currentUri = validateUrl(initialUrl);
        for (int hop = 0; hop <= 2; hop++) {
            awaitPoliteInterval();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(currentUri)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .GET()
                    .build();

            RawHttpResponse response = httpTransport.send(request, maxBytes);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                if (response.location() == null || response.location().isBlank() || hop == 2) {
                    throw new IOException("Too many or invalid redirects while requesting " + currentUri);
                }
                URI resolved = currentUri.resolve(normalizeRawHref(response.location()));
                currentUri = validateUrl(resolved.toString());
                continue;
            }
            checkStatusOrThrow(status, currentUri.toString());
            return new RawHttpResponse(status, response.contentType(), currentUri.toString(), response.body());
        }
        throw new IOException("Redirect limit exceeded for " + initialUrl);
    }

    private String executePostForm(String url, Map<String, String> formFields) throws IOException {
        return executePostForm(url, formFields, null);
    }

    private String executePostForm(String url, Map<String, String> formFields, String refererUrl) throws IOException {
        URI uri = validateUrl(url);
        awaitPoliteInterval();

        String encodedBody = formFields.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString(encodedBody, StandardCharsets.UTF_8));
        if (refererUrl != null && !refererUrl.isBlank()) {
            reqBuilder.header("Referer", refererUrl);
        }

        RawHttpResponse response = httpTransport.send(reqBuilder.build(), DEFAULT_MAX_HTML_BYTES);
        checkStatusOrThrow(response.statusCode(), uri.toString());
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private void checkStatusOrThrow(int status, String url) throws IOException {
        if (status == 429 || status == 503) {
            throw new RemoteThrottleException(status,
                    "BVICAM server signaled HTTP " + status + " rate limit/busy on " + url + "; pausing sync politely.");
        }
        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, "HTTP " + status + " returned for " + url);
        }
    }

    private synchronized void awaitPoliteInterval() throws IOException {
        long delayMs = getEffectiveRequestDelayMs();
        long prev = lastRequestAtMillis.get();
        long now = System.currentTimeMillis();
        if (prev > 0L) {
            long elapsed = now - prev;
            if (elapsed < delayMs) {
                try {
                    sleeper.sleep(delayMs - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Sync interrupted during polite rate-limit sleep", e);
                }
            }
        }
        lastRequestAtMillis.set(System.currentTimeMillis());
    }

    private URI validateUrl(String url) {
        if (knowledgeIngestionService != null) {
            return knowledgeIngestionService.validateScrapeUrl(url);
        }
        String normalized = url != null ? url.trim().replace(" ", "%20") : "";
        URI uri = URI.create(normalized);
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
        if ((!"http".equals(scheme) && !"https".equals(scheme))
                || (!"bvicam.in".equals(host) && !host.endsWith(".bvicam.in"))) {
            throw new IllegalArgumentException("URL is outside approved BVICAM portal domain: " + url);
        }
        return uri;
    }

    private String buildPortalUrl(String path) {
        String cleanPath = path.startsWith("/") ? path : "/" + path;
        return getBaseUrl() + cleanPath;
    }

    private static JsonNode parsePotentiallyDoubleEncodedArray(String rawBody) throws IOException {
        if (rawBody == null || rawBody.isBlank()) {
            return OBJECT_MAPPER.createArrayNode();
        }
        JsonNode node = OBJECT_MAPPER.readTree(rawBody);
        for (int depth = 0; depth < 2 && node != null && node.isTextual(); depth++) {
            String inner = node.asText("").trim();
            if (inner.isEmpty()) {
                return OBJECT_MAPPER.createArrayNode();
            }
            node = OBJECT_MAPPER.readTree(inner);
        }
        if (node == null || !node.isArray()) {
            return OBJECT_MAPPER.createArrayNode();
        }
        return node;
    }

    private static void addCourseMaterialIfPdf(Element anchor,
                                               String sectionTitle,
                                               String pageBaseUrl,
                                               Map<String, CourseMaterialLink> byUrl) {
        String resolved = resolvePdfUrl(anchor, pageBaseUrl);
        if (resolved == null || byUrl.containsKey(resolved)) {
            return;
        }
        String title = extractAnchorTitle(anchor, resolved);
        byUrl.put(resolved, new CourseMaterialLink(sectionTitle, title, resolved));
    }

    private static String resolvePdfUrl(Element anchor, String pageBaseUrl) {
        String rawHref = anchor.attr("href");
        if (rawHref == null || rawHref.isBlank()) {
            return null;
        }
        String trimmed = rawHref.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("#") || lower.startsWith("javascript:") || lower.startsWith("mailto:") || lower.startsWith("tel:")) {
            return null;
        }

        try {
            URI base = URI.create((pageBaseUrl != null ? pageBaseUrl : "https://bvicam.in").replace(" ", "%20"));
            URI resolved = base.resolve(normalizeRawHref(trimmed));
            String resolvedStr = resolved.toString();
            String pathPart = resolved.getPath() != null ? resolved.getPath().toLowerCase(Locale.ROOT) : "";
            boolean isPdf = pathPart.endsWith(".pdf")
                    || resolvedStr.toLowerCase(Locale.ROOT).contains("/uploaddocuments/coursematerial/");
            if (!isPdf) {
                return null;
            }
            String scheme = resolved.getScheme() != null ? resolved.getScheme().toLowerCase(Locale.ROOT) : "";
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            return resolvedStr;
        } catch (IllegalArgumentException e) {
            log.debug("Skipping malformed href '{}': {}", rawHref, e.getMessage());
            return null;
        }
    }

    private static String extractAnchorTitle(Element anchor, String resolvedUrl) {
        String linkText = anchor.text().trim();
        String normalized = linkText.toLowerCase(Locale.ROOT);
        boolean generic = linkText.isBlank()
                || normalized.equals("download")
                || normalized.equals("click here")
                || normalized.equals("view")
                || normalized.equals("pdf")
                || normalized.equals("open");

        if (generic) {
            Element row = anchor.closest("tr");
            if (row != null) {
                String rowLabel = row.select("td, th").stream()
                        .map(cell -> cell.text().trim())
                        .filter(text -> !text.isEmpty() && !text.equalsIgnoreCase(linkText))
                        .collect(Collectors.joining(" - "));
                if (!rowLabel.isBlank()) {
                    return rowLabel;
                }
            }
            Element listItem = anchor.closest("li, .list-group-item");
            if (listItem != null && !listItem.text().isBlank() && !listItem.text().trim().equalsIgnoreCase(linkText)) {
                return listItem.text().trim();
            }
        }

        if (!linkText.isBlank() && !generic) {
            return linkText;
        }
        String fileName = extractFileName(resolvedUrl);
        return fileName != null ? fileName : "BVICAM Document";
    }

    private static String extractPageTitle(Document doc, String fallbackUrl) {
        Element heading = doc.selectFirst(".detail-content-area h1, .detail-content-area h2, .breadcrumb-title, h1");
        if (heading != null && !heading.text().isBlank()) {
            return heading.text().trim();
        }
        return doc.title() != null && !doc.title().isBlank() ? doc.title().trim() : fallbackUrl;
    }

    private static String extractFileName(String url) {
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
            return name.isBlank() ? null : name;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeRawHref(String rawHref) {
        return rawHref.trim()
                .replace("\r", "")
                .replace("\n", "")
                .replace("\t", "")
                .replace("\\", "/")
                .replace(" ", "%20")
                .replace("[", "%5B")
                .replace("]", "%5D")
                .replace("{", "%7B")
                .replace("}", "%7D");
    }

    private static String firstNonBlankText(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode child = node.get(field);
            if (child != null && !child.isNull()) {
                String val = child.asText("").trim();
                if (!val.isEmpty()) {
                    return val;
                }
            }
        }
        return "";
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }

    private static byte[] readBoundedBytes(InputStream is, long maxBytes) throws IOException {
        if (is == null) {
            return new byte[0];
        }
        try (is) {
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            long total = 0L;
            int n;
            while ((n = is.read(buffer)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException("BVICAM payload exceeded max byte limit of " + maxBytes);
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    // -------------------------------------------------------------------------
    // Public records, SPI hooks & polite-abort exceptions
    // -------------------------------------------------------------------------

    public record CircularEntry(long circularId, String description, String date) {
    }

    public record DropdownOption(String value, String text) {
    }

    public record SubjectEntry(String courseId, String semId, long paperId, String paperCode, String paperTitle) {
    }

    public record CourseMaterialLink(String sectionTitle, String title, String pdfUrl) {
    }

    public record SyllabusLink(String title, String pdfUrl) {
    }

    public record WebPageContent(String url, String title, String extractedText) {
    }

    /**
     * Minimal HTTP response container (where {@code location} carries the {@code Location} header
     * on 3xx responses or final URL after redirects).
     */
    record RawHttpResponse(int statusCode, String contentType, String location, byte[] body) {
        String finalUrl() {
            return location;
        }
    }

    @FunctionalInterface
    interface HttpTransport {
        RawHttpResponse send(HttpRequest request, long maxBytes) throws IOException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /**
     * Signals that the BVICAM server returned HTTP 429 or 503 and sync must abort gracefully.
     */
    public static class RemoteThrottleException extends RuntimeException {
        private final int statusCode;

        public RemoteThrottleException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    /**
     * Signals a non-2xx HTTP status other than 429/503.
     */
    public static class HttpStatusException extends IOException {
        private final int statusCode;

        public HttpStatusException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
