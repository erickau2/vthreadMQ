package com.vthreadMQ001.repository;

import com.vthreadMQ001.model.ConsumerCursor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConsumerCursorRepository {
    Mono<ConsumerCursor> save(ConsumerCursor cursor);
    Mono<ConsumerCursor> findByConsumerGroupAndTopic(String consumerGroup, String topic);
    Flux<ConsumerCursor> findByConsumerGroup(String consumerGroup);
    Mono<Void> commitOffset(String consumerGroup, String topic, Long offset);
    Mono<Long> getCommittedOffset(String consumerGroup, String topic);
    Mono<Void> updateActiveStatus(String consumerGroup, String topic, String consumerId, boolean active);
    Flux<ConsumerCursor> findActiveConsumers();
} 