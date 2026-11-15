
package com.sajilochat.reaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddReactionRequest(

        @NotBlank(message = "Emoji is required")
        @Size(max = 32, message = "Emoji must not exceed 32 characters")
        String emoji

) {
}
