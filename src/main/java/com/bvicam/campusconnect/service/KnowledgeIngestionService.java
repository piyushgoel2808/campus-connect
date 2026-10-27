package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.KnowledgeDocumentDto;
import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.DocumentChunk;
import com.bvicam.campusconnect.entity.KnowledgeDocument;
import com.bvicam.campusconnect.entity.SourceType;
import com.bvicam.campusconnect.repository.DocumentChunkRepository;
import com.bvicam.campusconnect.repository.KnowledgeDocumentRepository;
import com.bvicam.campusconnect.util.TextChunker;
import com.bvicam.campusconnect.util.VectorUtil;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing document extraction (PDF, text, web HTML), chunking,
 * vector embedding generation via Ollama, and knowledge persistence.
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final OllamaClientService ollamaClientService;

    public KnowledgeIngestionService(KnowledgeDocumentRepository documentRepository,
                                     DocumentChunkRepository chunkRepository,
                                     OllamaClientService ollamaClientService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.ollamaClientService = ollamaClientService;
    }

    /**
     * Ingests an uploaded document file (PDF, TXT, MD, etc.), chunks content,
     * computes vector embeddings, and stores chunks with document metadata.
     *
     * @param file     multipart uploaded file
     * @param title    user-specified or default document title
     * @param category categorization of the document
     * @return persisted document projection
     * @throws IOException on file read or extraction errors
     */
    @Transactional
    public KnowledgeDocumentDto ingestFile(MultipartFile file, String title, DocumentCategory category) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot ingest an empty or missing file.");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String extractedText = extractTextFromFile(file);

        if (extractedText == null || extractedText.trim().isEmpty()) {
            throw new IllegalArgumentException("Uploaded file contains no extractable text content.");
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
        document.setChunkCount(chunks.size());

        KnowledgeDocument savedDoc = documentRepository.save(document);

        List<DocumentChunk> chunkEntities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            List<Double> embedding = ollamaClientService.generateEmbedding(chunkText);
            String embeddingJson = VectorUtil.toJson(embedding);

            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(savedDoc);
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunkText);
            chunk.setEmbeddingJson(embeddingJson);
            chunkEntities.add(chunk);
        }

        chunkRepository.saveAll(chunkEntities);
        log.info("Successfully ingested document id={}, title='{}' with {} chunks",
                savedDoc.getId(), savedDoc.getTitle(), chunkEntities.size());

        return KnowledgeDocumentDto.fromEntity(savedDoc);
    }

    /**
     * Scrapes an external web URL (e.g. bvicam.in), extracts clean text,
     * splits into overlapping chunks, computes vector embeddings, and persists.
     *
     * @param url      web address to scrape
     * @param title    optional title override
     * @param category categorization
     * @return persisted document projection
     * @throws IOException on network or parsing failure
     */
    @Transactional
    public KnowledgeDocumentDto scrapeAndIndexUrl(String url, String title, DocumentCategory category) throws IOException {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            throw new IllegalArgumentException("Invalid URL: must start with http:// or https://");
        }

        org.jsoup.nodes.Document htmlDoc = Jsoup.connect(url)
                .userAgent("CampusConnect-GradBot/1.0 (+http://bvicam.in)")
                .timeout(10000)
                .get();

        String pageTitle = (title != null && !title.isBlank()) ? title.trim() : htmlDoc.title();
        if (pageTitle.isBlank()) {
            pageTitle = url;
        }

        String pageText = htmlDoc.body() != null ? htmlDoc.body().text() : "";
        if (pageText.isBlank()) {
            throw new IllegalArgumentException("Target URL contains no readable body text.");
        }

        List<String> chunks = TextChunker.chunkText(pageText, 600, 100);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Scraped web content could not be converted into chunks.");
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(pageTitle);
        document.setCategory(category != null ? category : DocumentCategory.BVICAM_WEB);
        document.setSourceType(SourceType.WEB_URL);
        document.setSourceUrl(url);
        document.setChunkCount(chunks.size());

        KnowledgeDocument savedDoc = documentRepository.save(document);

        List<DocumentChunk> chunkEntities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            List<Double> embedding = ollamaClientService.generateEmbedding(chunkText);
            String embeddingJson = VectorUtil.toJson(embedding);

            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(savedDoc);
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunkText);
            chunk.setEmbeddingJson(embeddingJson);
            chunkEntities.add(chunk);
        }

        chunkRepository.saveAll(chunkEntities);
        log.info("Successfully scraped & indexed URL '{}' as document id={}, chunks={}",
                url, savedDoc.getId(), chunkEntities.size());

        return KnowledgeDocumentDto.fromEntity(savedDoc);
    }

    /**
     * Lists all indexed knowledge documents sorted by latest created.
     *
     * @return list of document DTOs
     */
    @Transactional(readOnly = true)
    public List<KnowledgeDocumentDto> getAllDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(KnowledgeDocumentDto::fromEntity)
                .toList();
    }

    /**
     * Deletes a knowledge document and its associated vector chunks.
     *
     * @param documentId document ID
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        documentRepository.deleteById(documentId);
        log.info("Deleted knowledge document id={}", documentId);
    }

    /**
     * Extracts raw text from multipart file using Apache PDFBox or UTF-8 decoding.
     */
    private String extractTextFromFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";

        if (filename.endsWith(".pdf") || contentType.contains("pdf")) {
            try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(pdf);
            }
        }

        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
}
