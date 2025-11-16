package com.support.assistant.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity representing a single query
 */
@Entity
@Table(name = "queries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Query {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String queryId;

    @Column(name = "query_text", nullable = false)
    private String queryText;

    @Column(name = "response", columnDefinition = "TEXT")
    private String response;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "timestamp")
    private Instant timestamp;
}
