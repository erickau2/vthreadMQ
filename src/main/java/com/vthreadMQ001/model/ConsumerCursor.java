package com.vthreadMQ001.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerCursor {
    private String id;
    private String consumerGroup;
    private String topic;
    private Long offset;
    private Instant lastCommitted;
    private String consumerId;
    private boolean active;
} 