package com.vthreadMQ001.repository.impl;

import com.vthreadMQ001.model.ConsumerCursor;
import com.vthreadMQ001.repository.ConsumerCursorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Slf4j
public class SQLiteConsumerCursorRepository implements ConsumerCursorRepository {
    
    private final DataSource dataSource;
    
    @Override
    public Mono<ConsumerCursor> save(ConsumerCursor cursor) {
        return Mono.fromCallable(() -> {
            if (cursor.getId() == null) {
                cursor.setId(UUID.randomUUID().toString());
            }
            if (cursor.getLastCommitted() == null) {
                cursor.setLastCommitted(Instant.now());
            }
            
            String sql = """
                INSERT OR REPLACE INTO consumer_cursors 
                (id, consumer_group, topic, offset, last_committed, consumer_id, active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
                
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, cursor.getId());
                stmt.setString(2, cursor.getConsumerGroup());
                stmt.setString(3, cursor.getTopic());
                stmt.setLong(4, cursor.getOffset());
                stmt.setLong(5, cursor.getLastCommitted().toEpochMilli());
                stmt.setString(6, cursor.getConsumerId());
                stmt.setBoolean(7, cursor.isActive());
                
                stmt.executeUpdate();
                return cursor;
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save consumer cursor", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<ConsumerCursor> findByConsumerGroupAndTopic(String consumerGroup, String topic) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT * FROM consumer_cursors WHERE consumer_group = ? AND topic = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, consumerGroup);
                stmt.setString(2, topic);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return mapResultSetToConsumerCursor(rs);
                }
                return null;
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to find consumer cursor", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<ConsumerCursor> findByConsumerGroup(String consumerGroup) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM consumer_cursors WHERE consumer_group = ?";
            
            return Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    
                    stmt.setString(1, consumerGroup);
                    ResultSet rs = stmt.executeQuery();
                    return resultSetToConsumerCursorList(rs);
                    
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to find consumer cursors", e);
                }
            }).flatMapMany(Flux::fromIterable);
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Void> commitOffset(String consumerGroup, String topic, Long offset) {
        return Mono.fromRunnable(() -> {
            String sql = """
                INSERT OR REPLACE INTO consumer_cursors 
                (id, consumer_group, topic, offset, last_committed, consumer_id, active)
                VALUES (
                    COALESCE((SELECT id FROM consumer_cursors WHERE consumer_group = ? AND topic = ?), ?),
                    ?, ?, ?, ?, 
                    COALESCE((SELECT consumer_id FROM consumer_cursors WHERE consumer_group = ? AND topic = ?), 'default'),
                    true
                )
                """;
                
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                String newId = UUID.randomUUID().toString();
                stmt.setString(1, consumerGroup);
                stmt.setString(2, topic);
                stmt.setString(3, newId);
                stmt.setString(4, consumerGroup);
                stmt.setString(5, topic);
                stmt.setLong(6, offset);
                stmt.setLong(7, Instant.now().toEpochMilli());
                stmt.setString(8, consumerGroup);
                stmt.setString(9, topic);
                
                stmt.executeUpdate();
                log.debug("Committed offset {} for consumer group {} topic {}", offset, consumerGroup, topic);
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to commit offset", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
    
    @Override
    public Mono<Long> getCommittedOffset(String consumerGroup, String topic) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT offset FROM consumer_cursors WHERE consumer_group = ? AND topic = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, consumerGroup);
                stmt.setString(2, topic);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return rs.getLong("offset");
                }
                return 0L; // Start from beginning if no cursor exists
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get committed offset", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Void> updateActiveStatus(String consumerGroup, String topic, String consumerId, boolean active) {
        return Mono.fromRunnable(() -> {
            String sql = "UPDATE consumer_cursors SET active = ?, consumer_id = ?, last_committed = ? WHERE consumer_group = ? AND topic = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setBoolean(1, active);
                stmt.setString(2, consumerId);
                stmt.setLong(3, Instant.now().toEpochMilli());
                stmt.setString(4, consumerGroup);
                stmt.setString(5, topic);
                
                int updated = stmt.executeUpdate();
                log.debug("Updated active status for consumer {} group {} topic {} to {}", 
                    consumerId, consumerGroup, topic, active);
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update active status", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
    
    @Override
    public Flux<ConsumerCursor> findActiveConsumers() {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM consumer_cursors WHERE active = true";
            
            return Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    
                    ResultSet rs = stmt.executeQuery();
                    return resultSetToConsumerCursorList(rs);
                    
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to find active consumers", e);
                }
            }).flatMapMany(Flux::fromIterable);
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    // Helper methods
    
    private ConsumerCursor mapResultSetToConsumerCursor(ResultSet rs) throws SQLException {
        return ConsumerCursor.builder()
            .id(rs.getString("id"))
            .consumerGroup(rs.getString("consumer_group"))
            .topic(rs.getString("topic"))
            .offset(rs.getLong("offset"))
            .lastCommitted(Instant.ofEpochMilli(rs.getLong("last_committed")))
            .consumerId(rs.getString("consumer_id"))
            .active(rs.getBoolean("active"))
            .build();
    }
    
    private List<ConsumerCursor> resultSetToConsumerCursorList(ResultSet rs) throws SQLException {
        List<ConsumerCursor> cursors = new ArrayList<>();
        while (rs.next()) {
            cursors.add(mapResultSetToConsumerCursor(rs));
        }
        return cursors;
    }
} 