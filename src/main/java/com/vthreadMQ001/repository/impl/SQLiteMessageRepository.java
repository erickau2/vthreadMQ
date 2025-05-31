package com.vthreadMQ001.repository.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vthreadMQ001.model.Message;
import com.vthreadMQ001.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Slf4j
public class SQLiteMessageRepository implements MessageRepository {
    
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    
    @Override
    public Mono<Message> save(Message message) {
        return Mono.fromCallable(() -> {
            if (message.getId() == null) {
                message.setId(UUID.randomUUID().toString());
            }
            if (message.getCreatedAt() == null) {
                message.setCreatedAt(Instant.now());
            }
            
            String sql = """
                INSERT INTO messages (id, topic, content, headers, created_at, scheduled_at, 
                                    status, retry_count, max_retries, consumer_group, offset)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
                
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                // Get next offset for the topic
                Long offset = getNextOffsetSync(conn, message.getTopic());
                message.setOffset(offset);
                
                stmt.setString(1, message.getId());
                stmt.setString(2, message.getTopic());
                stmt.setString(3, message.getContent());
                stmt.setString(4, serializeHeaders(message.getHeaders()));
                stmt.setLong(5, message.getCreatedAt().toEpochMilli());
                stmt.setObject(6, message.getScheduledAt() != null ? 
                    message.getScheduledAt().toEpochMilli() : null);
                stmt.setString(7, message.getStatus().toString());
                stmt.setInt(8, message.getRetryCount());
                stmt.setInt(9, message.getMaxRetries());
                stmt.setString(10, message.getConsumerGroup());
                stmt.setLong(11, offset);
                
                stmt.executeUpdate();
                return message;
                
            } catch (SQLException | JsonProcessingException e) {
                throw new RuntimeException("Failed to save message", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Message> findById(String id) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT * FROM messages WHERE id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, id);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return mapResultSetToMessage(rs);
                }
                return null;
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to find message", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<Message> findByTopicAndStatus(String topic, Message.MessageStatus status, int limit) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM messages WHERE topic = ? AND status = ? ORDER BY offset LIMIT ?";
            
            return Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    
                    stmt.setString(1, topic);
                    stmt.setString(2, status.toString());
                    stmt.setInt(3, limit);
                    
                    ResultSet rs = stmt.executeQuery();
                    return resultSetToMessageList(rs);
                    
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to find messages", e);
                }
            }).flatMapMany(Flux::fromIterable);
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<Message> findByTopicAndStatusAndOffset(String topic, Message.MessageStatus status, Long fromOffset, int limit) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM messages WHERE topic = ? AND status = ? AND offset > ? ORDER BY offset LIMIT ?";
            
            return Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    
                    stmt.setString(1, topic);
                    stmt.setString(2, status.toString());
                    stmt.setLong(3, fromOffset);
                    stmt.setInt(4, limit);
                    
                    ResultSet rs = stmt.executeQuery();
                    return resultSetToMessageList(rs);
                    
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to find messages", e);
                }
            }).flatMapMany(Flux::fromIterable);
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<Message> findScheduledMessages(Instant now) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM messages WHERE status = 'SCHEDULED' AND scheduled_at <= ?";
            
            return Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    
                    stmt.setLong(1, now.toEpochMilli());
                    ResultSet rs = stmt.executeQuery();
                    return resultSetToMessageList(rs);
                    
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to find scheduled messages", e);
                }
            }).flatMapMany(Flux::fromIterable);
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Void> updateStatus(String id, Message.MessageStatus status) {
        return Mono.fromRunnable(() -> {
            String sql = "UPDATE messages SET status = ?, processed_at = ? WHERE id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, status.toString());
                stmt.setLong(2, Instant.now().toEpochMilli());
                stmt.setString(3, id);
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update message status", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
    
    @Override
    public Mono<Void> updateStatusAndError(String id, Message.MessageStatus status, String errorMessage) {
        return Mono.fromRunnable(() -> {
            String sql = "UPDATE messages SET status = ?, error_message = ?, processed_at = ? WHERE id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, status.toString());
                stmt.setString(2, errorMessage);
                stmt.setLong(3, Instant.now().toEpochMilli());
                stmt.setString(4, id);
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update message status and error", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
    
    @Override
    public Mono<Long> getNextOffset(String topic) {
        return Mono.fromCallable(() -> {
            try (Connection conn = dataSource.getConnection()) {
                return getNextOffsetSync(conn, topic);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get next offset", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Long> getMaxOffset(String topic) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT MAX(offset) FROM messages WHERE topic = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, topic);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    long maxOffset = rs.getLong(1);
                    return rs.wasNull() ? 0L : maxOffset;
                }
                return 0L;
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get max offset", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<Message> findByTopicAndOffsetRange(String topic, Long fromOffset, Long toOffset) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM messages WHERE topic = ? AND offset >= ? AND offset <= ? ORDER BY offset";
            
            return Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    
                    stmt.setString(1, topic);
                    stmt.setLong(2, fromOffset);
                    stmt.setLong(3, toOffset);
                    
                    ResultSet rs = stmt.executeQuery();
                    return resultSetToMessageList(rs);
                    
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to find messages by offset range", e);
                }
            }).flatMapMany(Flux::fromIterable);
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Void> deleteOldMessages(String topic, Instant before) {
        return Mono.fromRunnable(() -> {
            String sql = "DELETE FROM messages WHERE topic = ? AND created_at < ? AND status IN ('COMPLETED', 'DEAD_LETTER')";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, topic);
                stmt.setLong(2, before.toEpochMilli());
                int deleted = stmt.executeUpdate();
                log.info("Deleted {} old messages from topic {}", deleted, topic);
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete old messages", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
    
    // Helper methods
    
    private Long getNextOffsetSync(Connection conn, String topic) throws SQLException {
        String sql = "SELECT MAX(offset) FROM messages WHERE topic = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, topic);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                long maxOffset = rs.getLong(1);
                return rs.wasNull() ? 1L : maxOffset + 1;
            }
            return 1L;
        }
    }
    
    private String serializeHeaders(Map<String, Object> headers) throws JsonProcessingException {
        return headers != null ? objectMapper.writeValueAsString(headers) : null;
    }
    
    private Map<String, Object> deserializeHeaders(String headersJson) {
        if (headersJson == null || headersJson.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(headersJson, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize headers: {}", headersJson, e);
            return null;
        }
    }
    
    private Message mapResultSetToMessage(ResultSet rs) throws SQLException {
        return Message.builder()
            .id(rs.getString("id"))
            .topic(rs.getString("topic"))
            .content(rs.getString("content"))
            .headers(deserializeHeaders(rs.getString("headers")))
            .createdAt(Instant.ofEpochMilli(rs.getLong("created_at")))
            .scheduledAt(rs.getLong("scheduled_at") != 0 ? 
                Instant.ofEpochMilli(rs.getLong("scheduled_at")) : null)
            .processedAt(rs.getLong("processed_at") != 0 ? 
                Instant.ofEpochMilli(rs.getLong("processed_at")) : null)
            .status(Message.MessageStatus.valueOf(rs.getString("status")))
            .retryCount(rs.getInt("retry_count"))
            .maxRetries(rs.getInt("max_retries"))
            .errorMessage(rs.getString("error_message"))
            .consumerGroup(rs.getString("consumer_group"))
            .offset(rs.getLong("offset"))
            .build();
    }
    
    private java.util.List<Message> resultSetToMessageList(ResultSet rs) throws SQLException {
        java.util.List<Message> messages = new java.util.ArrayList<>();
        while (rs.next()) {
            messages.add(mapResultSetToMessage(rs));
        }
        return messages;
    }
} 