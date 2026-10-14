package com.personalassistant.backend.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** JSON body for POST /api/chat, e.g. {"message":"What are my tasks today?"} */
public record ChatRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 500, message = "Message must be at most 500 characters")
        String message) {
}
