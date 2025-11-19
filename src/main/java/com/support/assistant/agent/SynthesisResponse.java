package com.support.assistant.agent;

import com.support.assistant.model.dto.Source;

import java.util.List;

/**
 * Response object from the SynthesizerAgent.
 */
public record SynthesisResponse(
    String response,
    List<Source> sources,
    int tokensUsed,
    String modelUsed
) {
}
