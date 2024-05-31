package org.ai.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.ai.gemini.dto.GeminiRequest;

public interface GeminiService {
    JsonNode text(GeminiRequest request) throws JsonProcessingException;
}
