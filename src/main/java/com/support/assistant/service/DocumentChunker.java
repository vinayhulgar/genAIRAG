package com.support.assistant.service;

import com.support.assistant.model.entity.DocumentMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for chunking documents into smaller segments for embedding.
 * Uses Spring AI's TokenTextSplitter with configurable chunk size and overlap.
 */
@Service
@Slf4j
public class DocumentChunker {

    private final TokenTextSplitter textSplitter;
    private final int chunkSize;
    private final int chunkOverlap;

    // Patterns for metadata extraction
    private static final Pattern TITLE_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern HEADER_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{4})\\b");

    public DocumentChunker(
            @Value("${document.chunking.chunk-size:800}") int chunkSize,
            @Value("${document.chunking.chunk-overlap:100}") int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.textSplitter = new TokenTextSplitter(chunkSize, chunkOverlap, 5, 10000, true);
        log.info("DocumentChunker initialized with chunk size: {} tokens, overlap: {} tokens", 
                chunkSize, chunkOverlap);
    }

    /**
     * Chunks a document into smaller segments with metadata extraction.
     *
     * @param content the document content to chunk
     * @param filename the original filename
     * @param documentType the type of document (e.g., "pdf", "markdown", "text")
     * @param additionalMetadata any additional metadata to include
     * @return list of Document chunks with extracted metadata
     */
    public List<Document> chunkDocument(
            String content, 
            String filename, 
            String documentType,
            Map<String, Object> additionalMetadata) {
        
        log.debug("Chunking document: {} (type: {})", filename, documentType);
        
        // Extract metadata from content
        DocumentMetadata extractedMetadata = extractMetadata(content, filename, documentType);
        
        // Create base metadata map
        Map<String, Object> baseMetadata = new HashMap<>();
        baseMetadata.put("filename", filename);
        baseMetadata.put("documentType", documentType);
        baseMetadata.put("title", extractedMetadata.getTitle());
        baseMetadata.put("source", extractedMetadata.getSource());
        baseMetadata.put("createdAt", extractedMetadata.getCreatedAt().toString());
        baseMetadata.put("chunkSize", chunkSize);
        baseMetadata.put("chunkOverlap", chunkOverlap);
        
        if (additionalMetadata != null) {
            baseMetadata.putAll(additionalMetadata);
        }
        
        // Create a Spring AI Document for splitting
        Document sourceDocument = new Document(content, baseMetadata);
        
        // Split the document using TokenTextSplitter
        List<Document> chunks = textSplitter.apply(List.of(sourceDocument));
        
        // Enrich each chunk with index and additional metadata
        List<Document> enrichedChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> chunkMetadata = new HashMap<>(chunk.getMetadata());
            chunkMetadata.put("chunkIndex", i);
            chunkMetadata.put("totalChunks", chunks.size());
            
            // Extract headers from this specific chunk
            List<String> headers = extractHeaders(chunk.getContent());
            if (!headers.isEmpty()) {
                chunkMetadata.put("headers", headers);
            }
            
            enrichedChunks.add(new Document(chunk.getId(), chunk.getContent(), chunkMetadata));
        }
        
        log.info("Document '{}' chunked into {} segments", filename, enrichedChunks.size());
        return enrichedChunks;
    }

    /**
     * Extracts metadata from document content.
     *
     * @param content the document content
     * @param filename the filename
     * @param documentType the document type
     * @return extracted DocumentMetadata
     */
    private DocumentMetadata extractMetadata(String content, String filename, String documentType) {
        String title = extractTitle(content, filename);
        String source = filename;
        Instant createdAt = Instant.now();
        
        return DocumentMetadata.builder()
                .title(title)
                .source(source)
                .documentType(documentType)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    /**
     * Extracts the title from document content.
     * Looks for markdown H1 headers or uses filename as fallback.
     *
     * @param content the document content
     * @param filename fallback filename
     * @return extracted or derived title
     */
    private String extractTitle(String content, String filename) {
        Matcher matcher = TITLE_PATTERN.matcher(content);
        if (matcher.find()) {
            String title = matcher.group(1).trim();
            log.debug("Extracted title from content: {}", title);
            return title;
        }
        
        // Fallback to filename without extension
        String title = filename.replaceAll("\\.[^.]+$", "");
        log.debug("Using filename as title: {}", title);
        return title;
    }

    /**
     * Extracts all headers from a text chunk.
     *
     * @param content the chunk content
     * @return list of header texts
     */
    private List<String> extractHeaders(String content) {
        List<String> headers = new ArrayList<>();
        Matcher matcher = HEADER_PATTERN.matcher(content);
        while (matcher.find()) {
            headers.add(matcher.group(1).trim());
        }
        return headers;
    }

    /**
     * Extracts dates from document content.
     *
     * @param content the document content
     * @return list of found dates
     */
    private List<String> extractDates(String content) {
        List<String> dates = new ArrayList<>();
        Matcher matcher = DATE_PATTERN.matcher(content);
        while (matcher.find()) {
            dates.add(matcher.group(1));
        }
        return dates;
    }

    /**
     * Gets the configured chunk size.
     *
     * @return chunk size in tokens
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Gets the configured chunk overlap.
     *
     * @return chunk overlap in tokens
     */
    public int getChunkOverlap() {
        return chunkOverlap;
    }
}
