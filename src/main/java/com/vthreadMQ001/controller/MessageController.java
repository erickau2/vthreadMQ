package com.vthreadMQ001.controller;

import com.vthreadMQ001.dto.ConsumeMessageRequest;
import com.vthreadMQ001.dto.ProduceMessageRequest;
import com.vthreadMQ001.model.Message;
import com.vthreadMQ001.service.MessageQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageQueueService messageQueueService;

    @PostMapping("/produce")
    public Mono<ResponseEntity<Message>> produceMessage(@Valid @RequestBody ProduceMessageRequest request) {
        return messageQueueService.produceMessage(request)
            .map(message -> ResponseEntity.ok(message))
            .doOnSuccess(response -> log.info("Produced message to topic {}", request.getTopic()))
            .onErrorReturn(ResponseEntity.badRequest().build());
    }

    @GetMapping("/produce")
    public Mono<ResponseEntity<Message>> produceMessageViaGet(
            @RequestParam String topic,
            @RequestParam String content,
            @RequestParam(required = false) Long delaySec,
            @RequestParam(required = false) String consumerGroup,
            @RequestParam(defaultValue = "3") int maxRetries) {
        
        ProduceMessageRequest request = ProduceMessageRequest.builder()
            .topic(topic)
            .content(content)
            .delaySec(delaySec)
            .consumerGroup(consumerGroup)
            .maxRetries(maxRetries)
            .build();
            
        return produceMessage(request);
    }

    @PostMapping("/consume")
    public Flux<Message> consumeMessages(@Valid @RequestBody ConsumeMessageRequest request) {
        return messageQueueService.consumeMessages(request)
            .doOnSubscribe(subscription -> log.info("Starting consumption from topic {} for group {}", 
                request.getTopic(), request.getConsumerGroup()));
    }

    @GetMapping("/consume")
    public Flux<Message> consumeMessagesViaGet(
            @RequestParam String topic,
            @RequestParam(defaultValue = "default") String consumerGroup,
            @RequestParam(defaultValue = "10") int maxMessages,
            @RequestParam(defaultValue = "30000") Long timeoutMs,
            @RequestParam(defaultValue = "true") boolean autoCommit,
            @RequestParam(required = false) Long fromOffset) {
        
        ConsumeMessageRequest request = ConsumeMessageRequest.builder()
            .topic(topic)
            .consumerGroup(consumerGroup)
            .maxMessages(Math.min(maxMessages, 100)) // Limit to prevent abuse
            .timeoutMs(timeoutMs)
            .autoCommit(autoCommit)
            .fromOffset(fromOffset)
            .build();
            
        return consumeMessages(request);
    }

    @PostMapping("/commit")
    public Mono<ResponseEntity<String>> commitOffset(
            @RequestParam String consumerGroup,
            @RequestParam String topic,
            @RequestParam Long offset) {
        
        return messageQueueService.commitOffset(consumerGroup, topic, offset)
            .then(Mono.just(ResponseEntity.ok("Offset committed successfully")))
            .onErrorReturn(ResponseEntity.badRequest().body("Failed to commit offset"));
    }

    @GetMapping("/offset")
    public Mono<ResponseEntity<Map<String, Object>>> getCommittedOffset(
            @RequestParam String consumerGroup,
            @RequestParam String topic) {
        
        return messageQueueService.getCommittedOffset(consumerGroup, topic)
            .map(offset -> ResponseEntity.ok(Map.<String, Object>of(
                "consumerGroup", consumerGroup,
                "topic", topic,
                "offset", offset
            )))
            .onErrorReturn(ResponseEntity.badRequest().build());
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "VthreadMQ",
            "timestamp", java.time.Instant.now()
        )));
    }
} 