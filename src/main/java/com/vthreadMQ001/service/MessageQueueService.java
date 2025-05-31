package com.vthreadMQ001.service;

import com.vthreadMQ001.dto.ConsumeMessageRequest;
import com.vthreadMQ001.dto.ProduceMessageRequest;
import com.vthreadMQ001.model.Message;
import com.vthreadMQ001.repository.ConsumerCursorRepository;
import com.vthreadMQ001.repository.MessageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageQueueService {

    private final MessageRepository messageRepository;
    private final ConsumerCursorRepository cursorRepository;
    private final MeterRegistry meterRegistry;
    private final NotificationService notificationService;
    
    private final AtomicInteger activeVirtualThreads = new AtomicInteger(0);
    private final ConcurrentHashMap<String, AtomicInteger> topicWorkerCounts = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduledExecutor;
    
    // Metrics
    private Counter producedMessages;
    private Counter consumedMessages;
    private Counter failedMessages;
    
    @PostConstruct
    public void init() {
        // Initialize metrics
        producedMessages = Counter.builder("vthreadmq_messages_produced_total")
            .description("Total number of messages produced")
            .register(meterRegistry);
            
        consumedMessages = Counter.builder("vthreadmq_messages_consumed_total")
            .description("Total number of messages consumed")
            .register(meterRegistry);
            
        failedMessages = Counter.builder("vthreadmq_messages_failed_total")
            .description("Total number of failed messages")
            .register(meterRegistry);
            
        Gauge.builder("vthreadmq_active_virtual_threads")
            .description("Number of active virtual threads")
            .register(meterRegistry, activeVirtualThreads, AtomicInteger::get);
            
        // Initialize scheduled executor for background tasks
        scheduledExecutor = Executors.newScheduledThreadPool(2);
        
        // Start scheduled message processor
        startScheduledMessageProcessor();
        
        log.info("MessageQueueService initialized with virtual threads support");
    }
    
    @PreDestroy
    public void cleanup() {
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
    }
    
    public Mono<Message> produceMessage(ProduceMessageRequest request) {
        return Mono.fromCallable(() -> {
            Message message = Message.builder()
                .topic(request.getTopic())
                .content(request.getContent())
                .headers(request.getHeaders())
                .status(determineInitialStatus(request))
                .scheduledAt(calculateScheduledTime(request))
                .retryCount(0)
                .maxRetries(request.getMaxRetries())
                .consumerGroup(request.getConsumerGroup())
                .build();
                
            return message;
        })
        .flatMap(messageRepository::save)
        .doOnSuccess(message -> {
            producedMessages.increment();
            log.debug("Produced message {} to topic {}", message.getId(), message.getTopic());
            
            // Notify consumers via WebSocket if message is immediately available
            if (message.getStatus() == Message.MessageStatus.PENDING) {
                notificationService.notifyNewMessage(message.getTopic(), message);
            }
        })
        .doOnError(error -> {
            failedMessages.increment();
            log.error("Failed to produce message", error);
        });
    }
    
    public Flux<Message> consumeMessages(ConsumeMessageRequest request) {
        String consumerId = UUID.randomUUID().toString();
        
        return Mono.fromCallable(() -> {
            // Update consumer active status
            cursorRepository.updateActiveStatus(
                request.getConsumerGroup(), 
                request.getTopic(), 
                consumerId, 
                true
            ).subscribe();
            
            return consumerId;
        })
        .flatMapMany(id -> getConsumerOffset(request.getConsumerGroup(), request.getTopic(), request.getFromOffset())
            .flatMapMany(offset -> fetchMessages(request, offset))
            .take(request.getMaxMessages())
            .flatMap(message -> processMessage(message, request.getConsumerGroup(), consumerId))
            .doOnComplete(() -> {
                // Mark consumer as inactive
                cursorRepository.updateActiveStatus(
                    request.getConsumerGroup(), 
                    request.getTopic(), 
                    id, 
                    false
                ).subscribe();
            }));
    }
    
    public Mono<Void> commitOffset(String consumerGroup, String topic, Long offset) {
        return cursorRepository.commitOffset(consumerGroup, topic, offset)
            .doOnSuccess(v -> log.debug("Committed offset {} for group {} topic {}", offset, consumerGroup, topic));
    }
    
    public Mono<Long> getCommittedOffset(String consumerGroup, String topic) {
        return cursorRepository.getCommittedOffset(consumerGroup, topic);
    }
    
    // Private helper methods
    
    private Message.MessageStatus determineInitialStatus(ProduceMessageRequest request) {
        if (request.getDelaySec() != null || request.getScheduledAt() != null) {
            return Message.MessageStatus.SCHEDULED;
        }
        return Message.MessageStatus.PENDING;
    }
    
    private Instant calculateScheduledTime(ProduceMessageRequest request) {
        if (request.getDelaySec() != null) {
            return Instant.now().plusSeconds(request.getDelaySec());
        } else if (request.getScheduledAt() != null) {
            return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(request.getScheduledAt()));
        }
        return null;
    }
    
    private Mono<Long> getConsumerOffset(String consumerGroup, String topic, Long fromOffset) {
        if (fromOffset != null) {
            return Mono.just(fromOffset);
        }
        return cursorRepository.getCommittedOffset(consumerGroup, topic);
    }
    
    private Flux<Message> fetchMessages(ConsumeMessageRequest request, Long fromOffset) {
        return messageRepository.findByTopicAndStatusAndOffset(
            request.getTopic(), 
            Message.MessageStatus.PENDING, 
            fromOffset, 
            request.getMaxMessages()
        );
    }
    
    private Mono<Message> processMessage(Message message, String consumerGroup, String consumerId) {
        return Mono.fromCallable(() -> {
            // Simulate virtual thread processing
            Thread.ofVirtual().start(() -> {
                activeVirtualThreads.incrementAndGet();
                try {
                    // Update message status to processing
                    messageRepository.updateStatus(message.getId(), Message.MessageStatus.PROCESSING)
                        .subscribe();
                    
                    // Simulate message processing (this would be actual business logic)
                    processMessageBusinessLogic(message);
                    
                    // Mark as completed
                    messageRepository.updateStatus(message.getId(), Message.MessageStatus.COMPLETED)
                        .subscribe();
                    
                    // Auto-commit offset if enabled
                    cursorRepository.commitOffset(consumerGroup, message.getTopic(), message.getOffset())
                        .subscribe();
                    
                    consumedMessages.increment();
                    log.debug("Processed message {} from topic {}", message.getId(), message.getTopic());
                    
                } catch (Exception e) {
                    handleMessageProcessingError(message, e);
                } finally {
                    activeVirtualThreads.decrementAndGet();
                }
            });
            
            return message;
        });
    }
    
    private void processMessageBusinessLogic(Message message) {
        // This is where actual message processing would happen
        // For now, just simulate some work
        try {
            Thread.sleep(100); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Message processing interrupted", e);
        }
    }
    
    private void handleMessageProcessingError(Message message, Exception error) {
        int newRetryCount = message.getRetryCount() + 1;
        
        if (newRetryCount <= message.getMaxRetries()) {
            // Retry the message
            Message retryMessage = message.toBuilder()
                .retryCount(newRetryCount)
                .status(Message.MessageStatus.PENDING)
                .scheduledAt(Instant.now().plusSeconds(newRetryCount * 60)) // Exponential backoff
                .build();
                
            messageRepository.save(retryMessage).subscribe();
            log.warn("Retrying message {} (attempt {}/{})", message.getId(), newRetryCount, message.getMaxRetries());
        } else {
            // Move to dead letter queue
            messageRepository.updateStatusAndError(
                message.getId(), 
                Message.MessageStatus.DEAD_LETTER, 
                error.getMessage()
            ).subscribe();
            
            failedMessages.increment();
            log.error("Message {} moved to dead letter queue after {} retries", 
                message.getId(), message.getMaxRetries(), error);
        }
    }
    
    private void startScheduledMessageProcessor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                // Process scheduled messages that are ready
                messageRepository.findScheduledMessages(Instant.now())
                    .flatMap(message -> {
                        message.setStatus(Message.MessageStatus.PENDING);
                        return messageRepository.save(message);
                    })
                    .doOnNext(message -> {
                        log.debug("Scheduled message {} is now available for processing", message.getId());
                        notificationService.notifyNewMessage(message.getTopic(), message);
                    })
                    .subscribe();
                    
            } catch (Exception e) {
                log.error("Error processing scheduled messages", e);
            }
        }, 10, 10, TimeUnit.SECONDS);
        
        // Cleanup old completed messages
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                Instant cutoff = Instant.now().minusSeconds(24 * 60 * 60); // 24 hours ago
                
                // Get all unique topics and clean them up
                messageRepository.findByTopicAndStatus("*", Message.MessageStatus.COMPLETED, 1000)
                    .map(Message::getTopic)
                    .distinct()
                    .flatMap(topic -> messageRepository.deleteOldMessages(topic, cutoff))
                    .subscribe();
                    
            } catch (Exception e) {
                log.error("Error cleaning up old messages", e);
            }
        }, 1, 6, TimeUnit.HOURS);
    }
} 