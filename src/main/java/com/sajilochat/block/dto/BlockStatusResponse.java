
package com.sajilochat.block.dto;

public record BlockStatusResponse(
        boolean blockedByMe,
        boolean blockedMe
) {}
