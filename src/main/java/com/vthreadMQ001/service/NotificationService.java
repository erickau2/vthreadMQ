package com.vthreadMQ001.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vthreadMQ001.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final ObjectMapper objectMapper;
    
    // Map of topic -> set of WebSocket sessions
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> topicSubscriptions = new ConcurrentHashMap<>();
    
    // Map of consumer group -> set of WebSocket sessions
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> consumerGroupSubscriptions = new ConcurrentHashMap<>();
    
    public void subscribeToTopic(String topic, WebSocketSession session) {
        topicSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("Session {} subscribed to topic {}", session.getId(), topic);
    }
    
    public void subscribeToConsumerGroup(String consumerGroup, WebSocketSession session) {
        consumerGroupSubscriptions.computeIfAbsent(consumerGroup, k -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("Session {} subscribed to consumer group {}", session.getId(), consumerGroup);
    }
    
    public void unsubscribe(WebSocketSession session) {
        // Remove from all topic subscriptions
        topicSubscriptions.values().forEach(sessions -> sessions.remove(session));
        
        // Remove from all consumer group subscriptions
        consumerGroupSubscriptions.values().forEach(sessions -> sessions.remove(session));
        
        log.debug("Session {} unsubscribed from all notifications", session.getId());
    }
    
    public void notifyNewMessage(String topic, Message message) {
        // Notify topic subscribers
        CopyOnWriteArraySet<WebSocketSession> topicSessions = topicSubscriptions.get(topic);
        if (topicSessions != null && !topicSessions.isEmpty()) {
            String notification = createMessageNotification(message);
            broadcastToSessions(topicSessions, notification);
        }
        
        // Notify consumer group subscribers
        if (message.getConsumerGroup() != null) {
            CopyOnWriteArraySet<WebSocketSession> groupSessions = consumerGroupSubscriptions.get(message.getConsumerGroup());
            if (groupSessions != null && !groupSessions.isEmpty()) {
                String notification = createMessageNotification(message);
                broadcastToSessions(groupSessions, notification);
            }
        }
    }
    
    public void notifyConsumerGroupStatus(String consumerGroup, String status, Object data) {
        CopyOnWriteArraySet<WebSocketSession> sessions = consumerGroupSubscriptions.get(consumerGroup);
        if (sessions != null && !sessions.isEmpty()) {
            try {
                String notification = objectMapper.writeValueAsString(new StatusNotification(
                    "CONSUMER_GROUP_STATUS", 
                    consumerGroup, 
                    status, 
                    data
                ));
                broadcastToSessions(sessions, notification);
            } catch (Exception e) {
                log.error("Error creating consumer group status notification", e);
            }
        }
    }
    
    private String createMessageNotification(Message message) {
        try {
            return objectMapper.writeValueAsString(new MessageNotification(
                "NEW_MESSAGE",
                message.getTopic(),
                message.getId(),
                message.getStatus().toString(),
                message.getCreatedAt(),
                message.getOffset()
            ));
        } catch (Exception e) {
            log.error("Error creating message notification", e);
            return "{}";
        }
    }
    
    private void broadcastToSessions(CopyOnWriteArraySet<WebSocketSession> sessions, String notification) {
        sessions.removeIf(session -> {
            if (!session.isOpen()) {
                return true; // Remove closed sessions
            }
            
            try {
                session.textMessage(notification)
                    .doOnError(error -> log.warn("Failed to send notification to session {}: {}", 
                        session.getId(), error.getMessage()))
                    .subscribe();
                return false;
            } catch (Exception e) {
                log.warn("Failed to send notification to session {}: {}", session.getId(), e.getMessage());
                return true; // Remove problematic sessions
            }
        });
    }
    
    // Notification DTOs
    public static class MessageNotification {
        public String type;
        public String topic;
        public String messageId;
        public String status;
        public java.time.Instant timestamp;
        public Long offset;
        
        public MessageNotification(String type, String topic, String messageId, String status, 
                                 java.time.Instant timestamp, Long offset) {
            this.type = type;
            this.topic = topic;
            this.messageId = messageId;
            this.status = status;
            this.timestamp = timestamp;
            this.offset = offset;
        }
    }
    
    public static class StatusNotification {
        public String type;
        public String target;
        public String status;
        public Object data;
        public java.time.Instant timestamp;
        
        public StatusNotification(String type, String target, String status, Object data) {
            this.type = type;
            this.target = target;
            this.status = status;
            this.data = data;
            this.timestamp = java.time.Instant.now();
        }
    }
} 