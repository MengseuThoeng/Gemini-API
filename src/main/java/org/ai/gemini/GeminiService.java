package org.ai.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.ai.gemini.dto.GeminiRequest;

public interface GeminiService {
    String text(GeminiRequest request) throws JsonProcessingException;
}
