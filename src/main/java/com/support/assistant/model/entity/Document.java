package com.support.assistant.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a document in the knowledge base
 */
@Entity
@Table(name = "documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Embedded
    private DocumentMetadata metadata;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    // Note: Embedding storage will be handled by vector database
    // This entity is for relational metadata only
}
