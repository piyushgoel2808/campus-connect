package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.BotAskResponse;
import com.bvicam.campusconnect.dto.CitationDto;
import com.bvicam.campusconnect.dto.QdrantSearchResultDto;
import com.bvicam.campusconnect.entity.*;
import com.bvicam.campusconnect.repository.DocumentChunkRepository;
import com.bvicam.campusconnect.repository.EventRepository;
import com.bvicam.campusconnect.repository.JobRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.util.VectorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core AI and RAG orchestration service for GradBot.
 * Coordinates vector similarity search (Qdrant with Relational DB fallback),
 * live database context injection, prompt engineering, and Ollama inference.
 */
@Service
public class CampusAiService {

    private static final Logger log = LoggerFactory.getLogger(CampusAiService.class);
    private static final double SIMILARITY_THRESHOLD = 0.25;
    private static final int TOP_K_CHUNKS = 4;
    private static final Pattern BATCH_YEAR_PATTERN = Pattern.compile("\\b(20\\d{2})\\b");

    private final DocumentChunkRepository chunkRepository;
    private final EventRepository eventRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final OllamaClientService ollamaClientService;
    private final QdrantClientService qdrantClientService;

    @Autowired
    public CampusAiService(DocumentChunkRepository chunkRepository,
                           EventRepository eventRepository,
                           JobRepository jobRepository,
                           UserRepository userRepository,
                           OllamaClientService ollamaClientService,
                           QdrantClientService qdrantClientService) {
        this.chunkRepository = chunkRepository;
        this.eventRepository = eventRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.ollamaClientService = ollamaClientService;
        this.qdrantClientService = qdrantClientService;
    }

    public CampusAiService(DocumentChunkRepository chunkRepository,
                           EventRepository eventRepository,
                           JobRepository jobRepository,
                           UserRepository userRepository,
                           OllamaClientService ollamaClientService) {
        this(chunkRepository, eventRepository, jobRepository, userRepository, ollamaClientService, null);
    }

    /**
     * Answers a user query using hybrid RAG:
     * 1. Vector similarity search over ingested notices and web documents.
     * 2. Live campus context injection from MySQL (Events, Jobs, Batch Directory).
     * 3. Response generation via local Ollama (gemma4:e4b).
     *
     * @param rawQuestion user input string
     * @return structured response with answer, model metadata, and source citations
     */
    @Transactional(readOnly = true)
    public BotAskResponse answerQuestion(String rawQuestion) {
        if (rawQuestion == null || rawQuestion.trim().isEmpty()) {
            String defaultModel = ollamaClientService != null && ollamaClientService.getChatModel() != null
                    ? ollamaClientService.getChatModel()
                    : "gemma4:e4b";
            return BotAskResponse.promptOnly(
                    "Please ask a question about BVICAM courses, placements, events, notices, or batchmates.",
                    defaultModel
            );
        }

        String question = rawQuestion.trim();
        String lowerQuestion = question.toLowerCase();

        // 1. Vector Similarity Search (RAG Knowledge Base)
        List<ScoredChunk> relevantChunks = findRelevantChunks(question);
        List<CitationDto> citations = extractCitations(relevantChunks);

        StringBuilder knowledgeContext = new StringBuilder();
        if (!relevantChunks.isEmpty()) {
            knowledgeContext.append("\n[KNOWLEDGE BASE CITATIONS & CONTEXT]\n");
            for (ScoredChunk sc : relevantChunks) {
                KnowledgeDocument doc = sc.chunk.getDocument();
                String sourceInfo = doc.getSourceUrl() != null ? doc.getSourceUrl() : doc.getOriginalFileName();
                knowledgeContext.append("• Document: \"").append(doc.getTitle()).append("\"")
                        .append(" (Category: ").append(doc.getCategory()).append(")\n")
                        .append("  Source: ").append(sourceInfo != null ? sourceInfo : "Campus Portal").append("\n")
                        .append("  Content: ").append(sc.chunk.getChunkText()).append("\n\n");
            }
        }

        // 2. Real-Time MySQL Live Domain Context
        StringBuilder liveContext = assembleLiveDomainContext(question, lowerQuestion);

        // 3. System Prompt Synthesis
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("You are GradBot, the official intelligent AI campus assistant for BVICAM ")
                .append("(Bharati Vidyapeeth's Institute of Computer Applications and Management, New Delhi).\n")
                .append("Answer questions accurately, politely, and concisely for students, alumni, and faculty.\n")
                .append("Base your answers primarily on the retrieved official knowledge base documents and live campus data provided below.\n")
                .append("If you use information from a notice, circular, or web page, cite the document title.\n")
                .append("Use clear Markdown formatting with bullet points where appropriate.\n");

        if (knowledgeContext.length() > 0) {
            systemPrompt.append(knowledgeContext);
        }
        if (liveContext.length() > 0) {
            systemPrompt.append(liveContext);
        }

        // 4. Generate answer via Ollama
        String answer = ollamaClientService.generateChatResponse(systemPrompt.toString(), question);
        String chatModel = ollamaClientService.getChatModel() != null
                ? ollamaClientService.getChatModel()
                : "gemma4:e4b";

        return new BotAskResponse(answer, chatModel, citations);
    }

    /**
     * Checks if the external local AI runner (Ollama & Qdrant) is active and reachable.
     */
    public Map<String, Object> getHealthStatus() {
        boolean ollamaHealthy = ollamaClientService != null && ollamaClientService.isHealthy();
        boolean qdrantHealthy = qdrantClientService != null && qdrantClientService.isHealthy();
        String model = ollamaClientService != null && ollamaClientService.getChatModel() != null
                ? ollamaClientService.getChatModel()
                : "gemma4:e4b";
        String embedding = ollamaClientService != null && ollamaClientService.getEmbeddingModel() != null
                ? ollamaClientService.getEmbeddingModel()
                : "embeddinggemma-2:latest";

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", ollamaHealthy ? "ONLINE" : "STANDBY");
        status.put("model", model);
        status.put("embeddingModel", embedding);
        status.put("vectorDb", qdrantHealthy ? "Qdrant (HNSW Vector Engine)" : "Relational Cosine Fallback");
        status.put("vectorDbStatus", qdrantHealthy ? "ONLINE" : "OFFLINE");
        status.put("mode", ollamaHealthy
                ? (qdrantHealthy ? "Mac AI Runner Connected (gemma4:e4b + Qdrant)" : "Mac AI Runner Connected (gemma4:e4b)")
                : "Standby (Rule-Based Fallback)");
        return status;
    }

    /**
     * Computes query vector embedding and retrieves top matching chunks.
     * Uses high-speed Qdrant HNSW vector index when available, with automatic
     * fallback to relational database cosine similarity if Qdrant is offline.
     */
    private List<ScoredChunk> findRelevantChunks(String question) {
        try {
            List<Double> queryVec = ollamaClientService.generateEmbedding(question);
            if (queryVec == null || queryVec.isEmpty()) {
                return Collections.emptyList();
            }

            // 1. Try Qdrant Vector Engine First
            if (qdrantClientService != null && qdrantClientService.isHealthy()) {
                List<QdrantSearchResultDto> hits = qdrantClientService.search(queryVec, TOP_K_CHUNKS, SIMILARITY_THRESHOLD);
                if (hits != null && !hits.isEmpty()) {
                    List<ScoredChunk> qdrantScored = new ArrayList<>();
                    for (QdrantSearchResultDto hit : hits) {
                        Map<String, Object> payload = hit.payload();
                        String chunkText = payload.containsKey("chunk_text") ? String.valueOf(payload.get("chunk_text")) : "";
                        String title = payload.containsKey("title") ? String.valueOf(payload.get("title")) : "Knowledge Document";
                        String catStr = payload.containsKey("category") ? String.valueOf(payload.get("category")) : "GENERAL";
                        String sourceInfo = payload.containsKey("source_info") ? String.valueOf(payload.get("source_info")) : "Campus Knowledge";
                        Long docId = payload.get("document_id") != null ? Long.valueOf(payload.get("document_id").toString()) : hit.id();

                        KnowledgeDocument doc = new KnowledgeDocument();
                        doc.setId(docId);
                        doc.setTitle(title);
                        try {
                            doc.setCategory(DocumentCategory.valueOf(catStr));
                        } catch (Exception ex) {
                            doc.setCategory(DocumentCategory.GENERAL);
                        }
                        if (sourceInfo.startsWith("http")) {
                            doc.setSourceUrl(sourceInfo);
                            doc.setSourceType(SourceType.WEB_URL);
                        } else {
                            doc.setOriginalFileName(sourceInfo);
                            doc.setSourceType(SourceType.FILE_UPLOAD);
                        }

                        DocumentChunk chunk = new DocumentChunk();
                        chunk.setId(hit.id());
                        chunk.setDocument(doc);
                        chunk.setChunkText(chunkText);
                        qdrantScored.add(new ScoredChunk(chunk, hit.score()));
                    }
                    return qdrantScored;
                }
            }

            // 2. Relational Database Fallback (In-memory Cosine Similarity)
            double[] queryArr = VectorUtil.toArray(queryVec);
            if (queryArr.length == 0) {
                return Collections.emptyList();
            }

            List<DocumentChunk> allChunks = chunkRepository.findAllWithDocument();
            if (allChunks == null || allChunks.isEmpty()) {
                return Collections.emptyList();
            }

            List<ScoredChunk> scored = new ArrayList<>();
            for (DocumentChunk chunk : allChunks) {
                double[] chunkVec = VectorUtil.fromJson(chunk.getEmbeddingJson());
                if (chunkVec.length == 0) continue;

                double similarity = VectorUtil.cosineSimilarity(queryArr, chunkVec);
                if (similarity >= SIMILARITY_THRESHOLD) {
                    scored.add(new ScoredChunk(chunk, similarity));
                }
            }

            scored.sort((a, b) -> Double.compare(b.similarity, a.similarity));
            return scored.stream().limit(TOP_K_CHUNKS).toList();
        } catch (Exception e) {
            log.warn("Vector search failed, proceeding without RAG context: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts unique document citation badges from top scored chunks.
     */
    private List<CitationDto> extractCitations(List<ScoredChunk> scoredChunks) {
        Map<Long, CitationDto> uniqueCitations = new LinkedHashMap<>();
        for (ScoredChunk sc : scoredChunks) {
            KnowledgeDocument doc = sc.chunk.getDocument();
            if (doc != null && !uniqueCitations.containsKey(doc.getId())) {
                uniqueCitations.put(doc.getId(), new CitationDto(
                        doc.getId(),
                        doc.getTitle(),
                        doc.getCategory(),
                        doc.getSourceType(),
                        doc.getSourceUrl(),
                        Math.round(sc.similarity * 100.0) / 100.0
                ));
            }
        }
        return new ArrayList<>(uniqueCitations.values());
    }

    /**
     * Dynamically queries live MySQL tables for real-time awareness.
     */
    private StringBuilder assembleLiveDomainContext(String question, String lowerQuestion) {
        StringBuilder live = new StringBuilder();
        try {
            // Live Upcoming Events
            if (lowerQuestion.contains("event") || lowerQuestion.contains("workshop") ||
                    lowerQuestion.contains("seminar") || lowerQuestion.contains("schedule") ||
                    lowerQuestion.contains("happening") || lowerQuestion.contains("fest") ||
                    lowerQuestion.contains("conference")) {
                var upcomingEvents = eventRepository.findTop5ByDateTimeAfterOrderByDateTimeAsc(LocalDateTime.now());
                if (upcomingEvents != null && !upcomingEvents.isEmpty()) {
                    live.append("\n[LIVE UPCOMING EVENTS IN DATABASE]\n");
                    for (var e : upcomingEvents) {
                        live.append("• ").append(e.getTitle()).append(" at ").append(e.getLocation())
                                .append(" on ").append(e.getDateTime()).append("\n");
                    }
                }
            }

            // Live Active Jobs & Referrals
            if (lowerQuestion.contains("job") || lowerQuestion.contains("intern") ||
                    lowerQuestion.contains("hiring") || lowerQuestion.contains("referral") ||
                    lowerQuestion.contains("opening") || lowerQuestion.contains("vacancy")) {
                List<Job> activeJobs = jobRepository.findAllByOrderByPostedAtDesc();
                if (activeJobs != null && !activeJobs.isEmpty()) {
                    live.append("\n[LIVE ACTIVE JOBS & REFERRALS IN DATABASE]\n");
                    for (Job j : activeJobs.stream().limit(5).toList()) {
                        live.append("• ").append(j.getTitle()).append(" at ").append(j.getCompany())
                                .append(" (").append(j.getLocation() != null ? j.getLocation() : "Remote").append(")")
                                .append(j.getApplyLink() != null ? " - Apply: " + j.getApplyLink() : "")
                                .append("\n");
                    }
                }
            }

            // Dynamic Batch Directory Lookup
            if (lowerQuestion.contains("batch") || lowerQuestion.contains("class of") || lowerQuestion.contains("alumni")) {
                Matcher matcher = BATCH_YEAR_PATTERN.matcher(question);
                if (matcher.find()) {
                    int year = Integer.parseInt(matcher.group(1));
                    var batchMembers = userRepository.findAll().stream()
                            .filter(u -> u.getBatchYear() != null && u.getBatchYear() == year)
                            .map(u -> u.getName() != null ? u.getName() : u.getEmail())
                            .limit(20)
                            .toList();
                    if (!batchMembers.isEmpty()) {
                        live.append("\n[STUDENTS ENROLLED IN BATCH ").append(year).append("]\n")
                                .append(String.join(", ", batchMembers)).append("\n");
                    }
                }
            }

            // Placement Statistics Highlights
            if (lowerQuestion.contains("placement") || lowerQuestion.contains("package") ||
                    lowerQuestion.contains("salary") || lowerQuestion.contains("recruit")) {
                live.append("\n[BVICAM OFFICIAL PLACEMENT HIGHLIGHTS]\n")
                        .append("• Highest Package: 12 LPA (Microsoft)\n")
                        .append("• Average Package: 6.5 LPA\n")
                        .append("• Prominent Recruiters: TCS, Nagarro, Knoldus, Microsoft, BVICAM Research Lab\n");
            }
        } catch (Exception e) {
            log.warn("Live domain context lookup degraded: {}", e.getMessage());
        }
        return live;
    }

    private record ScoredChunk(DocumentChunk chunk, double similarity) {
    }
}
