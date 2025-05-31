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
    
    private String consumerGroup = "default";
    private int maxMessages = 10;
    private Long timeoutMs = 30000L; // 30 seconds default
    private boolean autoCommit = true;
    private Long fromOffset;
} 