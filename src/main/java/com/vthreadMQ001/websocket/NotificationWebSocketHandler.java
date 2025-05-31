package com.vthreadMQ001.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vthreadMQ001.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketHandler implements WebSocketHandler {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("WebSocket connection established: {}", session.getId());
        
        return session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .flatMap(message -> handleIncomingMessage(session, message))
            .doOnError(error -> log.error("WebSocket error for session {}: {}", session.getId(), error.getMessage()))
            .doFinally(signalType -> {
                log.info("WebSocket connection closed: {} ({})", session.getId(), signalType);
                notificationService.unsubscribe(session);
            })
            .then();
    }

    private Mono<Void> handleIncomingMessage(WebSocketSession session, String message) {
        return Mono.fromRunnable(() -> {
            try {
                Map<String, Object> payload = objectMapper.readValue(message, Map.class);
                String action = (String) payload.get("action");
                
                switch (action) {
                    case "subscribe_topic" -> {
                        String topic = (String) payload.get("topic");
                        if (topic != null) {
                            notificationService.subscribeToTopic(topic, session);
                            sendAcknowledgment(session, "subscribed_to_topic", topic);
                        }
                    }
                    case "subscribe_consumer_group" -> {
                        String consumerGroup = (String) payload.get("consumerGroup");
                        if (consumerGroup != null) {
                            notificationService.subscribeToConsumerGroup(consumerGroup, session);
                            sendAcknowledgment(session, "subscribed_to_consumer_group", consumerGroup);
                        }
                    }
                    case "unsubscribe" -> {
                        notificationService.unsubscribe(session);
                        sendAcknowledgment(session, "unsubscribed", null);
                    }
                    case "ping" -> {
                        sendPong(session);
                    }
                    default -> {
                        log.warn("Unknown action from session {}: {}", session.getId(), action);
                        sendError(session, "unknown_action", "Action not recognized: " + action);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing WebSocket message from session {}: {}", session.getId(), e.getMessage());
                sendError(session, "message_error", "Failed to process message: " + e.getMessage());
            }
        });
    }

    private void sendAcknowledgment(WebSocketSession session, String type, String target) {
        try {
            Map<String, Object> response = Map.of(
                "type", type,
                "target", target != null ? target : "",
                "timestamp", java.time.Instant.now().toString()
            );
            
            String responseJson = objectMapper.writeValueAsString(response);
            session.send(Mono.just(session.textMessage(responseJson))).subscribe();
            
        } catch (Exception e) {
            log.error("Failed to send acknowledgment to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String type, String message) {
        try {
            Map<String, Object> response = Map.of(
                "type", "error",
                "errorType", type,
                "message", message,
                "timestamp", java.time.Instant.now().toString()
            );
            
            String responseJson = objectMapper.writeValueAsString(response);
            session.send(Mono.just(session.textMessage(responseJson))).subscribe();
            
        } catch (Exception e) {
            log.error("Failed to send error to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void sendPong(WebSocketSession session) {
        try {
            Map<String, Object> response = Map.of(
                "type", "pong",
                "timestamp", java.time.Instant.now().toString()
            );
            
            String responseJson = objectMapper.writeValueAsString(response);
            session.send(Mono.just(session.textMessage(responseJson))).subscribe();
            
        } catch (Exception e) {
            log.error("Failed to send pong to session {}: {}", session.getId(), e.getMessage());
        }
    }
} 