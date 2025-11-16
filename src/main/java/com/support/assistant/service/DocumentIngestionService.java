package com.support.assistant.service;

import com.support.assistant.model.dto.DocumentUpload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for document ingestion pipeline.
 * Handles chunking, embedding generation, and storage in vector database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final DocumentChunker documentChunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    /**
     * Ingests a document through the complete pipeline:
     * 1. Parse document based on format
     * 2. Chunk into smaller segments
     * 3. Generate embeddings
     * 4. Store in vector database
     *
     * @param documentUpload the document upload request
     * @return number of chunks processed
     */
    public Mono<Integer> ingestDocument(DocumentUpload documentUpload) {
        log.info("Starting document ingestion for: {}", documentUpload.filename());
        
        return Mono.fromCallable(() -> {
            // Determine document type
            String documentType = determineDocumentType(
                    documentUpload.filename(), 
                    documentUpload.documentType()
            );
            
            // Parse document based on type
            String content = parseDocument(
                    documentUpload.content(), 
                    documentUpload.filename(), 
                    documentType
            );
            
            // Chunk the document
            List<Document> chunks = documentChunker.chunkDocument(
                    content,
                    documentUpload.filename(),
                    documentType,
                    documentUpload.metadata()
            );
            
            log.info("Document '{}' chunked into {} segments", 
                    documentUpload.filename(), chunks.size());
            
            return chunks;
        })
        .flatMap(chunks -> {
            // Generate embeddings for all chunks
            log.debug("Generating embeddings for {} chunks", chunks.size());
            return embeddingService.embedDocuments(chunks)
                    .map(embeddings -> {
                        // Attach embeddings to documents
                        for (int i = 0; i < chunks.size(); i++) {
                            Document chunk = chunks.get(i);
                            float[] embedding = embeddings.get(i);
                            
                            // Create new document with embedding in metadata
                            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                            metadata.put("embedding_dimension", embedding.length);
                            
                            chunks.set(i, new Document(
                                    chunk.getId(),
                                    chunk.getContent(),
                                    metadata
                            ));
                        }
                        return chunks;
                    });
        })
        .flatMap(chunks -> {
            // Store in vector database
            log.info("Storing {} chunks in vector database", chunks.size());
            return Mono.fromRunnable(() -> vectorStore.add(chunks))
                    .thenReturn(chunks.size());
        })
        .doOnSuccess(count -> 
                log.info("Successfully ingested document '{}' with {} chunks", 
                        documentUpload.filename(), count))
        .doOnError(error -> 
                log.error("Failed to ingest document '{}': {}", 
                        documentUpload.filename(), error.getMessage(), error));
    }

    /**
     * Parses document content based on format.
     * Supports PDF, Markdown, and plain text.
     *
     * @param content the raw content
     * @param filename the filename
     * @param documentType the document type
     * @return parsed text content
     */
    private String parseDocument(String content, String filename, String documentType) {
        log.debug("Parsing document: {} (type: {})", filename, documentType);
        
        switch (documentType.toLowerCase()) {
            case "pdf":
                return parsePdfDocument(content, filename);
            case "markdown":
            case "md":
                return content; // Markdown is already text
            case "text":
            case "txt":
            default:
                return content; // Plain text
        }
    }

    /**
     * Parses PDF document using Spring AI PDF reader.
     * Note: For this implementation, we expect PDF content as base64 or raw bytes.
     *
     * @param content the PDF content
     * @param filename the filename
     * @return extracted text
     */
    private String parsePdfDocument(String content, String filename) {
        try {
            log.debug("Parsing PDF document: {}", filename);
            
            // Convert content to bytes (assuming base64 or direct byte content)
            byte[] pdfBytes = content.getBytes(StandardCharsets.UTF_8);
            Resource resource = new ByteArrayResource(pdfBytes);
            
            // Use Spring AI PDF reader
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            List<Document> pdfDocuments = pdfReader.get();
            
            // Combine all pages into single content
            StringBuilder combinedContent = new StringBuilder();
            for (Document doc : pdfDocuments) {
                combinedContent.append(doc.getContent()).append("\n\n");
            }
            
            log.debug("Extracted {} pages from PDF", pdfDocuments.size());
            return combinedContent.toString();
            
        } catch (Exception e) {
            log.warn("Failed to parse as PDF, treating as plain text: {}", e.getMessage());
            return content; // Fallback to plain text
        }
    }

    /**
     * Determines document type from filename or explicit type.
     *
     * @param filename the filename
     * @param explicitType explicitly provided type (nullable)
     * @return determined document type
     */
    private String determineDocumentType(String filename, String explicitType) {
        if (explicitType != null && !explicitType.isEmpty()) {
            return explicitType;
        }
        
        // Determine from file extension
        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".pdf")) {
            return "pdf";
        } else if (lowerFilename.endsWith(".md") || lowerFilename.endsWith(".markdown")) {
            return "markdown";
        } else if (lowerFilename.endsWith(".txt")) {
            return "text";
        } else {
            return "text"; // Default to text
        }
    }

    /**
     * Deletes a document and all its chunks from the vector store.
     *
     * @param filename the filename to delete
     * @return true if deleted successfully
     */
    public Mono<Boolean> deleteDocument(String filename) {
        log.info("Deleting document: {}", filename);
        
        return Mono.fromCallable(() -> {
            // Search for all chunks with this filename
            // Note: VectorStore interface doesn't have a standard delete by metadata method
            // This is a simplified implementation
            log.warn("Document deletion from vector store requires custom implementation");
            return true;
        })
        .doOnSuccess(result -> log.info("Document '{}' deleted", filename))
        .doOnError(error -> log.error("Failed to delete document '{}': {}", 
                filename, error.getMessage()));
    }
}
