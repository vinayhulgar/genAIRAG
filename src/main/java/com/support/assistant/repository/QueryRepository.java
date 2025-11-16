package com.support.assistant.repository;

import com.support.assistant.model.entity.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for Query entities
 */
@Repository
public interface QueryRepository extends JpaRepository<Query, String> {

    List<Query> findByTimestampBetween(Instant start, Instant end);
}
