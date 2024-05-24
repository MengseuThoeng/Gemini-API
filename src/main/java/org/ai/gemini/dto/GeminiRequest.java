package org.ai.gemini.dto;

public record GeminiRequest(
        String subject,
        String questionType,
        String difficultyLevel,
        String numberOfQuestions

) {
}
