package org.ai.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.ai.gemini.dto.GeminiRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gemini")
@RequiredArgsConstructor
public class GeminiController {

    private final GeminiService geminiService;

    @PostMapping("/text")
    public String text(@RequestBody GeminiRequest request) throws JsonProcessingException {
        return geminiService.text(request);
    }
}
