package com.vthreadMQ001.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumeMessageRequest {
    @NotBlank
    private String topic;
    
    @Builder.Default
    private String consumerGroup = "default";
    @Builder.Default
    private int maxMessages = 10;
    @Builder.Default
    private Long timeoutMs = 30000L; // 30 seconds default
    @Builder.Default
    private boolean autoCommit = true;
    private Long fromOffset;
} 