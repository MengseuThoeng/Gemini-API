package org.ai.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ai.gemini.dto.GeminiRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiServiceImpl implements GeminiService {

    @Value("${API_KEY}")
    private String API_KEY;

    @Value("${GEMINI_URL}")
    private String GEMINI_URL;

    @Override
    public String text(GeminiRequest request) throws JsonProcessingException, HttpClientErrorException {
        RestTemplate restTemplate = new RestTemplate();

        // Generate prompt based on the request
        String prompt = generatePrompt(request);

        // Create headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create the request body with the prompt
        String requestBody = String.format("{\"contents\":[{\"role\": \"user\",\"parts\":[{\"text\": \"%s\"}]}]}", escapeJson(prompt));
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // Build the URL with the API key
        String url = GEMINI_URL + "?key=" + API_KEY;
        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (HttpClientErrorException e) {
            throw new HttpClientErrorException(e.getStatusCode(), "Error calling Gemini API: " + e.getMessage());
        }

        if (response.getStatusCode() == HttpStatus.OK) {
            String responseBody = response.getBody();
            return extractTextFromJson(responseBody);
        } else {
            throw new RuntimeException("Unexpected response from Gemini API: " + response.getStatusCodeValue());
        }
    }

    private String generatePrompt(GeminiRequest request) {
        return switch (request.questionType().toLowerCase()) {
            case "true/false" -> generateTrueFalsePrompt(request);
            case "multiple choice" -> generateMultipleChoicePrompt(request);
            case "fill-in-the-blank" -> generateFillInTheBlankPrompt(request);
            case "question" -> generateQuestionPrompt(request);
            default -> throw new IllegalArgumentException("Unknown question type: " + request.questionType());
        };
    }

    private String generateTrueFalsePrompt(GeminiRequest request) {
        return String.format(
                "Generate %s true/false questions on %s (difficulty: %s). Format as JSON: {\"questions\":[{\"question\":\"<text>\",\"answer\":<true/false>},...]}",
                request.numberOfQuestions(),
                request.subject(),
                request.difficultyLevel()
        );
    }

    private String generateMultipleChoicePrompt(GeminiRequest request) {
        return String.format(
                "Generate %s multiple-choice questions on %s (difficulty: %s). Format as JSON: {\"questions\":[{\"question\":\"<text>\",\"options\":[\"<option1>\",\"<option2>\",...],\"answer\":<index>},...]}",
                request.numberOfQuestions(),
                request.subject(),
                request.difficultyLevel()
        );
    }

    private String generateFillInTheBlankPrompt(GeminiRequest request) {
        return String.format(
                "Generate %s fill-in-the-blank questions on %s (difficulty: %s). Format as JSON: {\"questions\":[{\"question\":\"<text>\",\"answer\":[\"<answer1>\",\"<answer2>\",...]}]}",
                request.numberOfQuestions(),
                request.subject(),
                request.difficultyLevel()
        );
    }
    private String generateQuestionPrompt(GeminiRequest request) {
        return String.format(
                "Generate %s questions on %s (difficulty: %s). Format as JSON: {\"questions\":[{\"question\":\"<text>\"}]}",
                request.numberOfQuestions(),
                request.subject(),
                request.difficultyLevel()
        );
    }


    private String extractTextFromJson(String responseBody) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> responseMap = mapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
        if (candidates != null && !candidates.isEmpty()) {
            Map<String, Object> firstCandidate = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts != null && !parts.isEmpty()) {
                return (String) parts.get(0).get("text");
            }
        }

        throw new RuntimeException("Invalid response format from Gemini API");
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
