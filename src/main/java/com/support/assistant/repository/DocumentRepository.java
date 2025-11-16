package com.support.assistant.repository;

import com.support.assistant.model.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Document entities
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {

    List<Document> findByMetadataDocumentType(String documentType);
    
    List<Document> findByMetadataSource(String source);
}
