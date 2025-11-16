package com.support.assistant.repository;

import com.support.assistant.model.entity.QuerySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for QuerySession entities
 */
@Repository
public interface QuerySessionRepository extends JpaRepository<QuerySession, String> {

    List<QuerySession> findByUserId(String userId);
}
