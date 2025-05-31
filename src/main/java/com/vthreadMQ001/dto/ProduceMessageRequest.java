package com.vthreadMQ001.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProduceMessageRequest {
    @NotBlank
    private String topic;
    
    @NotBlank
    private String content;
    
    private Map<String, Object> headers;
    private Long delaySec;
    private String scheduledAt; // ISO 8601 timestamp
    private int maxRetries = 3;
    private String consumerGroup;
} 