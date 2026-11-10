package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.KnowledgeDocumentDto;
import com.bvicam.campusconnect.dto.QdrantPointDto;
import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.DocumentChunk;
import com.bvicam.campusconnect.entity.KnowledgeDocument;
import com.bvicam.campusconnect.entity.SourceType;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.DocumentChunkRepository;
import com.bvicam.campusconnect.repository.KnowledgeDocumentRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.util.TextChunker;
import com.bvicam.campusconnect.util.VectorUtil;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service managing document extraction (PDF, scanned PDF/image OCR via Ollama Vision, text, web HTML),
 * chunking, vector embedding generation via Ollama, and synchronization with relational storage & Qdrant.
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
            ".pdf", ".txt", ".md", ".csv", ".html", ".png", ".jpg", ".jpeg", ".webp"
    );
    private static final Set<String> IMAGE_FILE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");
    private static final String CRAWLER_USER_AGENT = "CampusConnect-GradBot/1.0 (+https://bvicam.in; educational-rag-sync)";
    private static final long DEFAULT_MAX_HTML_BYTES = 2L * 1024 * 1024;
    private static final long DEFAULT_MAX_PDF_BYTES = 20L * 1024 * 1024;
    private static final int MIN_DIGITAL_PDF_CHARS = 25;

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final OllamaClientService ollamaClientService;
    private final QdrantClientService qdrantClientService;
    private final HttpClient httpClient;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Value("${ai.scrape.allowed-domains:bvicam.in}")
    private String allowedScrapeDomains = "bvicam.in";

    @Value("${ai.sync.max-pdf-bytes:20971520}")
    private long maxPdfBytes = DEFAULT_MAX_PDF_BYTES;

    @Value("${ai.sync.max-chunks-per-doc:120}")
    private int maxChunksPerDoc = 120;

    @Value("${ai.ocr.max-pages:15}")
    private int maxOcrPages = 15;

    @Value("${ai.ocr.dpi:150}")
    private float ocrDpi = 150f;

    @Value("${ai.sync.ocr-scanned-pdfs:true}")
    private boolean autoSyncOcrEnabled = true;

    @Value("${ai.sync.ocr-max-pages:3}")
    private int autoSyncOcrMaxPages = 3;

    @Autowired
    public KnowledgeIngestionService(KnowledgeDocumentRepository documentRepository,
                                     DocumentChunkRepository chunkRepository,
                                     OllamaClientService ollamaClientService,
                                     QdrantClientService qdrantClientService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.ollamaClientService = ollamaClientService;
        this.qdrantClientService = qdrantClientService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public KnowledgeIngestionService(KnowledgeDocumentRepository documentRepository,
                                     DocumentChunkRepository chunkRepository,
                                     OllamaClientService ollamaClientService) {
        this(documentRepository, chunkRepository, ollamaClientService, null);
    }

    /**
     * Outcome states returned by idempotent automated sync upserts.
     */
    public enum SyncResultStatus {
        CREATED,
        UPDATED,
        SKIPPED_UNCHANGED,
        SCANNED_FALLBACK_CREATED
    }

    /**
     * Structured outcome of an idempotent knowledge-document sync operation.
     */
    public record SyncUpsertResult(SyncResultStatus status, KnowledgeDocumentDto document) {
    }

    /**
     * Ingests an uploaded document file (PDF, TXT, MD, CSV, HTML) without explicit uploader metadata.
     */
    @Transactional
    public KnowledgeDocumentDto ingestFile(MultipartFile file, String title, DocumentCategory category) throws IOException {
        return ingestFile(file, title, category, null);
    }

    /**
     * Ingests an uploaded document file (PDF, TXT, MD, CSV, HTML), chunks content,
     * computes vector embeddings, and stores chunks with document metadata.
     */
    @Transactional
    public KnowledgeDocumentDto ingestFile(MultipartFile file,
                                           String title,
                                           DocumentCategory category,
                                           String uploaderEmail) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot ingest an empty or missing file.");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename().trim() : "document.txt";
        validateFileExtension(originalFilename);

        String extractedText = extractTextFromFile(file, originalFilename);
        if (extractedText == null || extractedText.trim().isEmpty()) {
            if (originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                throw new IllegalArgumentException(
                        "The uploaded PDF contains no extractable digital text (it appears to be a scanned image), "
                                + "and automatic AI Vision OCR could not extract readable text. "
                                + "Ensure your AI runner (gemma4:e4b) is online and retry, or upload a clearer file.");
            }
            if (isImageFile(originalFilename, file.getContentType())) {
                throw new IllegalArgumentException(
                        "Could not read text from the uploaded image via AI Vision OCR. "
                                + "Ensure your AI runner (gemma4:e4b) is online and the image contains readable text.");
            }
            throw new IllegalArgumentException("Uploaded file contains no readable text content.");
        }

        String effectiveTitle = (title != null && !title.isBlank()) ? title.trim() : originalFilename;
        DocumentCategory effectiveCategory = (category != null) ? category : DocumentCategory.GENERAL;

        List<String> chunks = TextChunker.chunkText(extractedText, 600, 100);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Extracted content could not be partitioned into text chunks.");
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(effectiveTitle);
        document.setCategory(effectiveCategory);
        document.setSourceType(SourceType.FILE_UPLOAD);
        document.setOriginalFileName(originalFilename);
        document.setContentHash(sha256Hex(extractedText));

        return persistAndIndexDocument(document, chunks, uploaderEmail,
                "doc '" + effectiveTitle + "'");
    }

    /**
     * Scrapes an allowed web URL without explicit uploader metadata.
     */
    @Transactional
    public KnowledgeDocumentDto scrapeAndIndexUrl(String url, String title, DocumentCategory category) throws IOException {
        return scrapeAndIndexUrl(url, title, category, null);
    }

    /**
     * Scrapes an allowlisted external HTML or PDF URL (e.g. bvicam.in), extracts clean text,
     * splits into overlapping chunks, computes vector embeddings, and persists.
     */
    @Transactional
    public KnowledgeDocumentDto scrapeAndIndexUrl(String url,
                                                  String title,
                                                  DocumentCategory category,
                                                  String uploaderEmail) throws IOException {
        URI safeUri = validateScrapeUrl(url);
        RemoteResource resource = fetchValidatedRemoteResource(safeUri, maxPdfBytes > 0 ? maxPdfBytes : DEFAULT_MAX_PDF_BYTES);

        boolean isPdf = isPdfResource(resource.contentType(), resource.finalUri().toString(), resource.body());
        String effectiveTitle;
        String extractedText;
        String originalFileName = null;

        if (isPdf) {
            extractedText = extractTextFromPdfWithVisionFallback(resource.body());
            if (extractedText.isBlank()) {
                throw new IllegalArgumentException(
                        "Target PDF URL contains no extractable text (even after AI Vision OCR check).");
            }
            originalFileName = extractFilenameFromUri(resource.finalUri());
            effectiveTitle = (title != null && !title.isBlank())
                    ? title.trim()
                    : (originalFileName != null ? originalFileName : resource.finalUri().toString());
        } else {
            if (resource.body().length > DEFAULT_MAX_HTML_BYTES) {
                throw new IllegalArgumentException("Web page exceeds maximum HTML size limit.");
            }
            String html = new String(resource.body(), StandardCharsets.UTF_8);
            Document htmlDoc = Jsoup.parse(html, resource.finalUri().toString());
            String pageTitle = (title != null && !title.isBlank()) ? title.trim() : htmlDoc.title();
            if (pageTitle == null || pageTitle.isBlank()) {
                pageTitle = resource.finalUri().toString();
            }
            effectiveTitle = pageTitle;
            extractedText = extractReadableHtmlText(htmlDoc);
            if (extractedText.isBlank()) {
                throw new IllegalArgumentException("Target URL contains no readable body text.");
            }
        }

        List<String> chunks = TextChunker.chunkText(extractedText, 600, 100);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Scraped web content could not be converted into chunks.");
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(effectiveTitle);
        document.setCategory(category != null ? category : DocumentCategory.BVICAM_WEB);
        document.setSourceType(SourceType.WEB_URL);
        document.setSourceUrl(resource.finalUri().toString());
        document.setOriginalFileName(originalFileName);
        document.setContentHash(sha256Hex(extractedText));

        return persistAndIndexDocument(document, chunks, uploaderEmail,
                "URL '" + resource.finalUri() + "'");
    }

    /**
     * Checks whether a given {@code externalKey} is already fully indexed in DB and Qdrant.
     */
    @Transactional(readOnly = true)
    public boolean isDocumentIndexed(String externalKey) {
        if (externalKey == null || externalKey.isBlank()) {
            return false;
        }
        return documentRepository.existsByExternalKeyAndSyncStatus(externalKey, KnowledgeDocument.SyncStatus.INDEXED);
    }

    /**
     * Idempotently ingests or updates a synced document (notice PDF, syllabus PDF, course material PDF, or web page).
     * Skips embedding and Qdrant writes when the normalized SHA-256 content hash matches an already {@code INDEXED} row.
     */
    @Transactional
    public SyncUpsertResult upsertAutoSyncedDocument(String externalKey,
                                                     String title,
                                                     DocumentCategory category,
                                                     String sourceUrl,
                                                     String originalFileName,
                                                     String publishedDate,
                                                     String contextPrefix,
                                                     String preExtractedText,
                                                     byte[] rawPdfBytes) {
        if (externalKey == null || externalKey.isBlank()) {
            throw new IllegalArgumentException("externalKey is required for synced documents.");
        }
        String effectiveTitle = (title != null && !title.isBlank()) ? title.trim() : "BVICAM Resource";
        if (effectiveTitle.length() > 250) {
            effectiveTitle = effectiveTitle.substring(0, 250);
        }

        boolean scannedFallback = false;
        String bodyText = preExtractedText != null ? preExtractedText.trim() : "";
        if (rawPdfBytes != null) {
            String pdfText = extractTextFromPdfBytes(rawPdfBytes);
            String compact = pdfText.replaceAll("\\s+", "");
            if (compact.length() < MIN_DIGITAL_PDF_CHARS) {
                String ocrText = (autoSyncOcrEnabled && ollamaClientService != null)
                        ? extractTextFromPdfWithVisionFallback(rawPdfBytes, Math.max(1, autoSyncOcrMaxPages))
                        : "";
                if (ocrText != null && ocrText.replaceAll("\\s+", "").length() >= MIN_DIGITAL_PDF_CHARS) {
                    scannedFallback = true;
                    StringBuilder ocrDoc = new StringBuilder();
                    if (contextPrefix != null && !contextPrefix.isBlank()) {
                        ocrDoc.append(contextPrefix.trim()).append("\n");
                    }
                    ocrDoc.append("Official Document Title: ").append(effectiveTitle);
                    if (publishedDate != null && !publishedDate.isBlank()) {
                        ocrDoc.append("\nPublished Date: ").append(publishedDate.trim());
                    }
                    if (sourceUrl != null && !sourceUrl.isBlank()) {
                        ocrDoc.append("\nOfficial PDF Link: ").append(sourceUrl);
                    }
                    ocrDoc.append("\n[Extracted via AI Vision OCR]\n").append(ocrText.trim());
                    bodyText = ocrDoc.toString();
                } else {
                    scannedFallback = true;
                    StringBuilder fallback = new StringBuilder();
                    if (contextPrefix != null && !contextPrefix.isBlank()) {
                        fallback.append(contextPrefix.trim()).append("\n");
                    }
                    fallback.append("Official Document Title: ").append(effectiveTitle);
                    if (publishedDate != null && !publishedDate.isBlank()) {
                        fallback.append("\nPublished Date: ").append(publishedDate.trim());
                    }
                    if (sourceUrl != null && !sourceUrl.isBlank()) {
                        fallback.append("\nOfficial PDF Link: ").append(sourceUrl);
                    }
                    fallback.append("\nNote: This circular/document is a scanned PDF image without embedded machine-readable text. Open the official link above to view the full scanned document.");
                    bodyText = fallback.toString();
                }
            } else {
                bodyText = pdfText;
            }
        }

        if (bodyText.isBlank()) {
            throw new IllegalArgumentException("Synced resource '" + effectiveTitle + "' has no extractable text or metadata.");
        }

        String normalizedHash = sha256Hex(effectiveTitle
                + "|" + (publishedDate != null ? publishedDate.trim() : "")
                + "|" + (contextPrefix != null ? contextPrefix.trim() : "")
                + "|" + bodyText);

        Optional<KnowledgeDocument> existingOpt = documentRepository.findByExternalKey(externalKey);
        if (existingOpt.isEmpty() && sourceUrl != null && !sourceUrl.isBlank()) {
            existingOpt = documentRepository.findFirstBySourceUrl(sourceUrl);
        }

        boolean isUpdate = false;
        if (existingOpt.isPresent()) {
            KnowledgeDocument existing = existingOpt.get();
            if (existing.getSyncStatus() == KnowledgeDocument.SyncStatus.INDEXED
                    && normalizedHash.equals(existing.getContentHash())
                    && externalKey.equals(existing.getExternalKey())) {
                return new SyncUpsertResult(SyncResultStatus.SKIPPED_UNCHANGED, KnowledgeDocumentDto.fromEntity(existing));
            }
            deleteDocument(existing.getId());
            documentRepository.flush();
            isUpdate = true;
        }

        int cap = maxChunksPerDoc > 0 ? maxChunksPerDoc : 120;
        List<String> rawChunks = TextChunker.chunkText(bodyText, 520, 90);
        if (rawChunks.isEmpty()) {
            throw new IllegalArgumentException("Could not partition synced content into chunks for '" + effectiveTitle + "'.");
        }

        boolean truncated = rawChunks.size() > cap;
        List<String> boundedChunks = truncated ? new ArrayList<>(rawChunks.subList(0, cap)) : new ArrayList<>(rawChunks);
        String prefix = (contextPrefix != null && !contextPrefix.isBlank()) ? contextPrefix.trim() + "\n" : "";

        List<String> finalChunks = new ArrayList<>(boundedChunks.size());
        for (int i = 0; i < boundedChunks.size(); i++) {
            String chunkBody = boundedChunks.get(i);
            if (truncated && i == boundedChunks.size() - 1) {
                chunkBody = chunkBody + "\n[Truncated long PDF after " + cap + " sections; view full document at " + sourceUrl + "]";
            }
            finalChunks.add(prefix + chunkBody);
        }

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle(effectiveTitle);
        doc.setCategory(category != null ? category : DocumentCategory.BVICAM_WEB);
        doc.setSourceType(SourceType.AUTO_SYNC);
        doc.setSourceUrl(sourceUrl);
        doc.setOriginalFileName(originalFileName);
        doc.setPublishedDate(publishedDate);
        doc.setExternalKey(externalKey);
        doc.setContentHash(normalizedHash);

        KnowledgeDocumentDto savedDto = persistAndIndexDocument(doc, finalChunks, null, "auto-sync '" + externalKey + "'");
        SyncResultStatus outcome = isUpdate
                ? SyncResultStatus.UPDATED
                : (scannedFallback ? SyncResultStatus.SCANNED_FALLBACK_CREATED : SyncResultStatus.CREATED);
        return new SyncUpsertResult(outcome, savedDto);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentDto> getAllDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(KnowledgeDocumentDto::fromEntity)
                .toList();
    }

    /**
     * Deletes a knowledge document and attempts to purge its associated vector chunks from Qdrant.
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        documentRepository.deleteById(documentId);
        if (qdrantClientService != null) {
            boolean purged = qdrantClientService.deleteByDocumentId(documentId);
            if (!purged) {
                log.warn("Deleted knowledge document id={} from relational DB, but failed to purge vectors from Qdrant",
                        documentId);
            }
        }
        log.info("Deleted knowledge document id={}", documentId);
    }

    private KnowledgeDocumentDto persistAndIndexDocument(KnowledgeDocument document,
                                                         List<String> chunks,
                                                         String uploaderEmail,
                                                         String sourceLabel) {
        // Compute embeddings first so a downed Ollama service aborts cleanly without persisting empty vectors
        List<List<Double>> embeddings = new ArrayList<>(chunks.size());
        int vectorDim = 0;
        for (String chunkText : chunks) {
            List<Double> embedding = ollamaClientService.generateEmbedding(chunkText);
            if (embedding == null || embedding.isEmpty()) {
                throw new IllegalStateException(
                        "Embedding service (Ollama) returned an empty vector. Ensure Ollama is online before ingesting documents.");
            }
            if (vectorDim == 0) {
                vectorDim = embedding.size();
            }
            embeddings.add(embedding);
        }

        if (uploaderEmail != null && !uploaderEmail.isBlank() && userRepository != null) {
            User uploader = userRepository.findByEmail(uploaderEmail).orElse(null);
            document.setUploadedBy(uploader);
        }

        document.setChunkCount(chunks.size());
        document.setSyncStatus(qdrantClientService == null
                ? KnowledgeDocument.SyncStatus.DB_ONLY
                : KnowledgeDocument.SyncStatus.PENDING_QDRANT_SYNC);

        KnowledgeDocument savedDoc = documentRepository.save(document);

        List<DocumentChunk> chunkEntities = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(savedDoc);
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunks.get(i));
            chunk.setEmbeddingJson(VectorUtil.toJson(embeddings.get(i)));
            chunkEntities.add(chunk);
        }

        List<DocumentChunk> savedChunks = chunkRepository.saveAll(chunkEntities);
        if (qdrantClientService != null) {
            boolean synced = syncToQdrant(savedDoc, savedChunks, vectorDim);
            if (synced) {
                savedDoc.setSyncStatus(KnowledgeDocument.SyncStatus.INDEXED);
                savedDoc = documentRepository.save(savedDoc);
            }
        }

        log.info("Successfully ingested {} as document id={}, syncStatus={}, chunks={}",
                sourceLabel, savedDoc.getId(), savedDoc.getSyncStatus(), savedChunks.size());

        return KnowledgeDocumentDto.fromEntity(savedDoc);
    }

    private boolean syncToQdrant(KnowledgeDocument doc, List<DocumentChunk> chunks, int vectorDim) {
        if (qdrantClientService == null || chunks == null || chunks.isEmpty()) {
            return false;
        }
        int effectiveDim = vectorDim > 0 ? vectorDim : 768;
        if (!qdrantClientService.ensureCollection(effectiveDim)) {
            log.warn("Could not ensure Qdrant collection '{}' for doc id={}; marked PENDING_QDRANT_SYNC",
                    qdrantClientService.getCollectionName(), doc.getId());
            return false;
        }

        List<QdrantPointDto> points = new ArrayList<>(chunks.size());
        String sourceInfo = doc.getSourceUrl() != null ? doc.getSourceUrl()
                : (doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "Document");

        for (DocumentChunk chunk : chunks) {
            double[] chunkArr = VectorUtil.fromJson(chunk.getEmbeddingJson());
            if (chunkArr.length > 0 && chunk.getId() != null) {
                List<Double> vector = new ArrayList<>(chunkArr.length);
                for (double d : chunkArr) {
                    vector.add(d);
                }
                Map<String, Object> payload = Map.of(
                        "chunk_id", chunk.getId(),
                        "document_id", doc.getId(),
                        "chunk_index", chunk.getChunkIndex(),
                        "title", doc.getTitle(),
                        "category", doc.getCategory().name(),
                        "chunk_text", chunk.getChunkText(),
                        "source_info", sourceInfo
                );
                points.add(new QdrantPointDto(chunk.getId(), vector, payload));
            }
        }

        if (points.isEmpty()) {
            return false;
        }

        boolean upserted = qdrantClientService.upsertPoints(points);
        if (upserted) {
            log.info("Synced {} points to Qdrant collection '{}' for doc id={}",
                    points.size(), qdrantClientService.getCollectionName(), doc.getId());
        } else {
            log.warn("Qdrant upsert failed for doc id={}; marked PENDING_QDRANT_SYNC", doc.getId());
        }
        return upserted;
    }

    private void validateFileExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_FILE_EXTENSIONS.stream().anyMatch(lower::endsWith);
        if (!allowed) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Allowed formats: .pdf, .png, .jpg, .jpeg, .webp, .txt, .md, .csv, .html");
        }
    }

    /**
     * Validates an external HTTP/HTTPS URL against domain allowlist and DNS private-IP SSRF protections.
     */
    public URI validateScrapeUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("Invalid URL: must start with http:// or https://");
        }
        String trimmed = url.trim().replace(" ", "%20");
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("Invalid URL: must start with http:// or https://");
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed URL: " + e.getMessage());
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must include a valid hostname.");
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(lowerHost) || lowerHost.endsWith(".local") || lowerHost.endsWith(".internal")) {
            throw new IllegalArgumentException("Scraping internal or localhost URLs is not permitted.");
        }

        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new IllegalArgumentException("Only standard HTTP (80) and HTTPS (443) ports are allowed.");
        }

        List<String> allowedDomains = Arrays.stream((allowedScrapeDomains != null ? allowedScrapeDomains : "bvicam.in").split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .toList();

        boolean domainMatched = allowedDomains.stream()
                .anyMatch(domain -> lowerHost.equals(domain) || lowerHost.endsWith("." + domain));
        if (!domainMatched) {
            throw new IllegalArgumentException(
                    "URL domain '" + host + "' is not in the approved scraping allowlist.");
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("Unable to resolve hostname: " + host);
            }
            for (InetAddress addr : addresses) {
                if (isPrivateOrReservedAddress(addr)) {
                    throw new IllegalArgumentException(
                            "URL resolves to a private, loopback, or restricted network address.");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve URL hostname: " + host);
        }

        return uri;
    }

    private boolean isPrivateOrReservedAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            int first = raw[0] & 0xFF;
            int second = raw[1] & 0xFF;
            if (first == 100 && (second >= 64 && second <= 127)) {
                return true;
            }
        } else if (raw.length == 16) {
            if ((raw[0] & 0xFE) == 0xFC) {
                return true;
            }
        }
        return false;
    }

    private RemoteResource fetchValidatedRemoteResource(URI initialUri, long maxBytes) throws IOException {
        URI currentUri = initialUri;
        for (int hop = 0; hop <= 2; hop++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(currentUri)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", CRAWLER_USER_AGENT)
                    .GET()
                    .build();
            try {
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    Optional<String> location = response.headers().firstValue("Location");
                    try (InputStream ignored = response.body()) {
                        // Drain/close response stream before following validated redirect
                    }
                    if (location.isEmpty() || hop == 2) {
                        throw new IOException("Too many or empty redirects from " + currentUri);
                    }
                    URI resolved = currentUri.resolve(location.get().replace(" ", "%20"));
                    currentUri = validateScrapeUrl(resolved.toString());
                    continue;
                }
                if (status < 200 || status >= 300) {
                    try (InputStream ignored = response.body()) {
                    }
                    throw new IOException("HTTP " + status + " fetching " + currentUri);
                }

                byte[] body = readBoundedBytes(response.body(), maxBytes);
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                return new RemoteResource(currentUri, contentType, body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching " + currentUri, e);
            }
        }
        throw new IOException("Redirect limit exceeded for " + initialUri);
    }

    private byte[] readBoundedBytes(InputStream is, long maxBytes) throws IOException {
        try (is) {
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            long total = 0;
            int n;
            while ((n = is.read(buffer)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("Remote document exceeds maximum allowed size of " + (maxBytes / (1024 * 1024)) + " MB.");
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    /**
     * Extracts clean readable text from BVICAM / web HTML, stripping navigation/footer boilerplate
     * and preserving table columns with {@code |} separators.
     */
    public static String extractReadableHtmlText(Document htmlDoc) {
        if (htmlDoc == null) {
            return "";
        }
        Document working = htmlDoc.clone();
        Element contentRoot = working.selectFirst(".detail-content-area");
        if (contentRoot == null) {
            working.select("script, style, noscript, iframe, svg, header, footer, nav, .custom-header, .custom-footer, .custom-marque, .detail-section-left").remove();
            contentRoot = working.body();
        } else {
            contentRoot.select("script, style, noscript, iframe, svg, form#__AjaxAntiForgeryForm select, .dataTables_paginate, .dataTables_length").remove();
        }
        if (contentRoot == null) {
            return "";
        }

        // Convert HTML tables into clean Markdown-style lines before reading text
        for (Element table : contentRoot.select("table")) {
            StringBuilder tableText = new StringBuilder("\n");
            for (Element row : table.select("tr")) {
                Elements cells = row.select("th, td");
                if (cells.isEmpty()) {
                    continue;
                }
                String line = cells.stream()
                        .map(c -> c.text().trim())
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining(" | "));
                if (!line.isEmpty()) {
                    tableText.append(line).append("\n");
                }
            }
            table.replaceWith(new org.jsoup.nodes.TextNode(tableText.toString()));
        }

        for (Element block : contentRoot.select("h1, h2, h3, h4, h5, p, li, tr, div.panel-heading")) {
            block.prependText("\n");
            block.appendText("\n");
        }

        return contentRoot.text()
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .trim();
    }

    /**
     * Extracts embedded text from raw PDF bytes safely using Apache PDFBox.
     */
    public static String extractTextFromPdfBytes(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length < 5) {
            return "";
        }
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(pdf);
            return text != null ? text : "";
        } catch (Exception e) {
            log.warn("PDFBox could not extract text cleanly: {}", e.getMessage());
            return "";
        }
    }

    private boolean isPdfResource(String contentType, String url, byte[] body) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf")) {
            return true;
        }
        if (url != null && url.toLowerCase(Locale.ROOT).split("\\?")[0].endsWith(".pdf")) {
            return true;
        }
        return body != null && body.length >= 5
                && body[0] == '%' && body[1] == 'P' && body[2] == 'D' && body[3] == 'F' && body[4] == '-';
    }

    private String extractFilenameFromUri(URI uri) {
        if (uri == null || uri.getPath() == null) {
            return null;
        }
        String path = uri.getPath();
        int idx = path.lastIndexOf('/');
        String candidate = idx >= 0 ? path.substring(idx + 1) : path;
        return candidate.isBlank() ? null : candidate;
    }

    /**
     * Extracts text from a PDF, automatically falling back to multimodal AI Vision OCR
     * (rendering pages via PDFBox and extracting text via Gemma 4 Vision) when the PDF
     * lacks an embedded digital text layer (e.g., scanned exam date sheets or circulars).
     */
    public String extractTextFromPdfWithVisionFallback(byte[] pdfBytes) {
        return extractTextFromPdfWithVisionFallback(pdfBytes, maxOcrPages);
    }

    /**
     * Extracts text from a PDF with an explicit maximum OCR page cap for automated sync throughput.
     */
    public String extractTextFromPdfWithVisionFallback(byte[] pdfBytes, int pageCapOverride) {
        if (pdfBytes == null || pdfBytes.length < 5) {
            return "";
        }
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String digitalText = stripper.getText(pdf);
            String cleanDigital = digitalText != null ? digitalText : "";
            if (cleanDigital.replaceAll("\\s+", "").length() >= MIN_DIGITAL_PDF_CHARS) {
                return cleanDigital;
            }

            if (ollamaClientService == null) {
                log.info("PDF has fewer than {} non-whitespace digital characters and Ollama client is unavailable for OCR.",
                        MIN_DIGITAL_PDF_CHARS);
                return "";
            }

            int totalPages = pdf.getNumberOfPages();
            if (totalPages <= 0) {
                return "";
            }

            int pageCap = Math.max(1, pageCapOverride > 0 ? pageCapOverride : maxOcrPages);
            int pagesToProcess = Math.min(totalPages, pageCap);
            float dpi = ocrDpi > 0 ? ocrDpi : 150f;
            log.info("PDF appears to be a scanned image (digitalChars={}). Running AI Vision OCR on {}/{} page(s) at {} DPI...",
                    cleanDigital.replaceAll("\\s+", "").length(), pagesToProcess, totalPages, dpi);

            PDFRenderer renderer = new PDFRenderer(pdf);
            StringBuilder combined = new StringBuilder();
            for (int pageIndex = 0; pageIndex < pagesToProcess; pageIndex++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(pageImage, "png", baos);
                    String base64Png = Base64.getEncoder().encodeToString(baos.toByteArray());
                    String pageText = ollamaClientService.extractTextFromImage(
                            base64Png,
                            "Page " + (pageIndex + 1) + " of " + totalPages
                    );
                    if (pageText != null && !pageText.isBlank()) {
                        if (pagesToProcess > 1) {
                            combined.append("\n--- Page ").append(pageIndex + 1).append(" ---\n");
                        }
                        combined.append(pageText.trim()).append("\n");
                    }
                } finally {
                    pageImage.flush();
                }
            }

            String ocrResult = combined.toString().trim();
            if (!ocrResult.isBlank() && totalPages > pagesToProcess) {
                ocrResult = ocrResult + "\n\n[Note: Automatic AI Vision OCR processed the first "
                        + pagesToProcess + " of " + totalPages + " pages.]";
            }
            return ocrResult;
        } catch (Exception e) {
            log.warn("PDFBox could not extract or render PDF cleanly: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Extracts readable text from a raw uploaded image (PNG, JPG, WEBP) via Ollama Vision OCR.
     */
    private String extractTextFromImageBytes(byte[] imageBytes, String filename) {
        if (imageBytes == null || imageBytes.length == 0 || ollamaClientService == null) {
            return "";
        }
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        return ollamaClientService.extractTextFromImage(base64, filename);
    }

    private boolean isImageFile(String filename, String contentType) {
        if (filename != null) {
            String lower = filename.toLowerCase(Locale.ROOT);
            if (IMAGE_FILE_EXTENSIONS.stream().anyMatch(lower::endsWith)) {
                return true;
            }
        }
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private String extractTextFromFile(MultipartFile file, String filename) throws IOException {
        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";

        if (lowerFilename.endsWith(".pdf") || contentType.contains("pdf")) {
            return extractTextFromPdfWithVisionFallback(file.getBytes());
        }
        if (isImageFile(lowerFilename, contentType)) {
            return extractTextFromImageBytes(file.getBytes(), filename);
        }

        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input != null ? input : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private record RemoteResource(URI finalUri, String contentType, byte[] body) {
    }
}
