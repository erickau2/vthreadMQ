package com.vthreadMQ001.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String id;
    private String topic;
    private String content;
    private Map<String, Object> headers;
    private Instant createdAt;
    private Instant scheduledAt;
    private Instant processedAt;
    private MessageStatus status;
    private int retryCount;
    private int maxRetries;
    private String errorMessage;
    private String consumerGroup;
    private Long offset;
    
    public enum MessageStatus {
        PENDING,
        SCHEDULED,
        PROCESSING,
        COMPLETED,
        FAILED,
        DEAD_LETTER
    }
} 