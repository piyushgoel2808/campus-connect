package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying {@link KnowledgeDocumentRepository} and {@link DocumentChunkRepository}
 * persistence, cascading delete, and category queries against the test database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KnowledgeDocumentRepositoryTest {

    @Autowired
    private KnowledgeDocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Saving KnowledgeDocument with DocumentChunks cascades and persists both")
    void saveDocumentWithChunks_cascadesAndPersists() {
        User admin = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .findFirst()
                .orElse(null);

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle("MCA Exam Schedule");
        doc.setCategory(DocumentCategory.NOTICE);
        doc.setSourceType(SourceType.FILE_UPLOAD);
        doc.setOriginalFileName("MCA_Exam_2026.pdf");
        doc.setUploadedBy(admin);

        DocumentChunk chunk1 = new DocumentChunk();
        chunk1.setDocument(doc);
        chunk1.setChunkIndex(0);
        chunk1.setChunkText("MCA Semester 3 exams start Nov 14, 2026.");
        chunk1.setEmbeddingJson("[0.1, 0.2, 0.3]");

        DocumentChunk chunk2 = new DocumentChunk();
        chunk2.setDocument(doc);
        chunk2.setChunkIndex(1);
        chunk2.setChunkText("Lab practicals are held in Lab 2.");
        chunk2.setEmbeddingJson("[0.4, 0.5, 0.6]");

        doc.setChunks(List.of(chunk1, chunk2));
        doc.setChunkCount(2);

        KnowledgeDocument saved = documentRepository.save(doc);
        assertThat(saved.getId()).isNotNull();

        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(saved.getId());
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getChunkText()).contains("MCA Semester 3");

        // Verify cascading delete
        documentRepository.delete(saved);
        documentRepository.flush();

        List<DocumentChunk> remainingChunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(saved.getId());
        assertThat(remainingChunks).isEmpty();
    }
}
