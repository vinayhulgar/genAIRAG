package com.support.assistant.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a query session
 */
@Entity
@Table(name = "query_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuerySession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String sessionId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "session_id")
    @Builder.Default
    private List<Query> queries = new ArrayList<>();

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "user_id")
    private String userId;
}
