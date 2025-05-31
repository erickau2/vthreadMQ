package com.vthreadMQ001.plugin;

import lombok.Data;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class PluginResult {
    private boolean success;
    private String message;
    private Map<String, Object> data;
    private Instant processedAt;
    private long processingTimeMs;
    private String pluginName;
    
    public static PluginResult success(String pluginName, String message) {
        return PluginResult.builder()
            .success(true)
            .pluginName(pluginName)
            .message(message)
            .processedAt(Instant.now())
            .build();
    }
    
    public static PluginResult success(String pluginName, String message, Map<String, Object> data) {
        return PluginResult.builder()
            .success(true)
            .pluginName(pluginName)
            .message(message)
            .data(data)
            .processedAt(Instant.now())
            .build();
    }
    
    public static PluginResult failure(String pluginName, String message) {
        return PluginResult.builder()
            .success(false)
            .pluginName(pluginName)
            .message(message)
            .processedAt(Instant.now())
            .build();
    }
    
    public static PluginResult failure(String pluginName, String message, Map<String, Object> data) {
        return PluginResult.builder()
            .success(false)
            .pluginName(pluginName)
            .message(message)
            .data(data)
            .processedAt(Instant.now())
            .build();
    }
} 