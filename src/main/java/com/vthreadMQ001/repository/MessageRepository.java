package com.vthreadMQ001.repository;

import com.vthreadMQ001.model.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public interface MessageRepository {
    Mono<Message> save(Message message);
    Mono<Message> findById(String id);
    Flux<Message> findByTopicAndStatus(String topic, Message.MessageStatus status, int limit);
    Flux<Message> findByTopicAndStatusAndOffset(String topic, Message.MessageStatus status, Long fromOffset, int limit);
    Flux<Message> findScheduledMessages(Instant now);
    Mono<Void> updateStatus(String id, Message.MessageStatus status);
    Mono<Void> updateStatusAndError(String id, Message.MessageStatus status, String errorMessage);
    Mono<Long> getNextOffset(String topic);
    Mono<Long> getMaxOffset(String topic);
    Flux<Message> findByTopicAndOffsetRange(String topic, Long fromOffset, Long toOffset);
    Mono<Void> deleteOldMessages(String topic, Instant before);
} 