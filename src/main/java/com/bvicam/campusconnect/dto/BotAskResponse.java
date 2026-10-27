package com.bvicam.campusconnect.dto;

import java.util.List;

/**
 * Structured response payload returned by GradBot Q&A endpoints.
 */
public record BotAskResponse(
        String answer,
        String model,
        List<CitationDto> citations
) {
    public static BotAskResponse promptOnly(String message, String model) {
        return new BotAskResponse(message, model, List.of());
    }
}
