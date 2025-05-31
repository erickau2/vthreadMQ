package com.vthreadMQ001.plugin;

import com.vthreadMQ001.model.Message;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface Plugin {
    
    /**
     * Get the unique name of this plugin
     */
    String getName();
    
    /**
     * Get the version of this plugin
     */
    String getVersion();
    
    /**
     * Get the description of what this plugin does
     */
    String getDescription();
    
    /**
     * Check if this plugin can handle the given message
     */
    boolean canHandle(Message message);
    
    /**
     * Process the message using this plugin
     */
    Mono<PluginResult> process(Message message, Map<String, Object> config);
    
    /**
     * Initialize the plugin with configuration
     */
    Mono<Void> initialize(Map<String, Object> config);
    
    /**
     * Cleanup resources when plugin is disabled
     */
    Mono<Void> shutdown();
    
    /**
     * Get the configuration schema for this plugin
     */
    Map<String, Object> getConfigSchema();
} 