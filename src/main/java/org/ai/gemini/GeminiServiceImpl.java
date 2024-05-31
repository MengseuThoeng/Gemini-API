package org.ai.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ai.gemini.dto.GeminiRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiServiceImpl implements GeminiService {

    @Value("${API_KEY}")
    private String API_KEY;

    @Value("${GEMINI_URL}")
    private String GEMINI_URL;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 1000;



    private boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
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
                "Generate %s true/false questions on %s (difficulty: %s). Format as JSON: {\"questions\":[{\"question\":\"<text>\",\"answer\":<true/false>},...]}\n" +
                        "Example: {\"questions\": [{\"question\": \"Is the sky blue?\", \"answer\": true}]}",
                request.numberOfQuestions(),
                request.subject(),
                request.difficultyLevel()
        );
    }

    private String generateMultipleChoicePrompt(GeminiRequest request) {
        return String.format(
                "Generate %s multiple-choice questions on %s (difficulty: %s). Format as JSON: {\"questions\":[{\"question\":\"<text>\",\"options\":[\"<option1>\",\"<option2>\",...],\"answer\":<index>},...]}\n" +
                        "Example: {\"questions\": [{\"question\": \"What is 2+2?\", \"options\": [\"3\", \"4\", \"5\"], \"answer\": 1}]}",
                request.numberOfQuestions(),
                request.subject(),
                request.difficultyLevel()
        );
    }

    private String generateFillInTheBlankPrompt(GeminiRequest request) {
        return String.format(
                "Generate %s fill-in-the-blank questions on %s (difficulty: %s). Format as JSON: {\"questions\":[{\"question\":\"<text>\",\"answer\":[\"<answer1>\",\"<answer2>\",...]}]}\n" +
                        "Example: {\"questions\": [{\"question\": \"The capital of France is ___\", \"answer\": [\"Paris\"]}]}",
                request.numberOfQuestions(),
                request.subject(),
                request.difficultyLevel()
        );
    }

    private String generateQuestionPrompt(GeminiRequest request) {
        return String.format(
                "Generate %s questions on %s (difficulty: %s). Format as JSON: {\"questions\":[{\"question\":\"<text>\"}]}\n" +
                        "Example: {\"questions\": [{\"question\": \"What is the capital of France?\"}]}",
                request.numberOfQuestions(),
                request.subject(),
                request.difficultyLevel()
        );
    }

    private JsonNode extractJsonFromResponse(String responseBody) throws JsonProcessingException {
        String cleanResponse = responseBody.replaceAll("```json", "").replaceAll("```", "").trim();
        JsonNode responseNode = objectMapper.readTree(cleanResponse);

        JsonNode candidates = responseNode.get("candidates");
        if (candidates != null && candidates.isArray() && candidates.size() > 0) {
            JsonNode firstCandidate = candidates.get(0);
            JsonNode content = firstCandidate.get("content");
            if (content != null) {
                JsonNode parts = content.get("parts");
                if (parts != null && parts.isArray() && parts.size() > 0) {
                    return objectMapper.readTree(parts.get(0).get("text").asText());
                }
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

    @Override
    public JsonNode text(GeminiRequest request) throws JsonProcessingException {
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

        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    String responseBody = response.getBody();
                    if (isValidJson(responseBody)) {
                        return extractJsonFromResponse(responseBody);
                    } else {
                        log.error("Invalid JSON response from Gemini API: " + responseBody);
                        throw new RuntimeException("Invalid JSON response from Gemini API");
                    }
                } else {
                    throw new RuntimeException("Unexpected response from Gemini API: " + response.getStatusCodeValue());
                }
            } catch (HttpServerErrorException.ServiceUnavailable e) {
                retryCount++;
                if (retryCount >= MAX_RETRIES) {
                    throw new RuntimeException("Gemini API is currently unavailable after multiple attempts", e);
                }
                try {
                    Thread.sleep(INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, retryCount - 1));
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", interruptedException);
                }
            } catch (HttpClientErrorException e) {
                throw new HttpClientErrorException(e.getStatusCode(), "Error calling Gemini API: " + e.getMessage());
            }
        }

        throw new RuntimeException("Exceeded maximum retry attempts for Gemini API");
    }
}
