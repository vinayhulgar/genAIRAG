package com.support.assistant.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request DTO for document upload
 */
public record DocumentUpload(
    @NotBlank(message = "Content cannot be blank")
    String content,
    @NotBlank(message = "Filename cannot be blank")
    String filename,
    String documentType,
    Map<String, Object> metadata
) {}
