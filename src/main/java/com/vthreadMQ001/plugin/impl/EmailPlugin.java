package com.vthreadMQ001.plugin.impl;

import com.vthreadMQ001.model.Message;
import com.vthreadMQ001.plugin.Plugin;
import com.vthreadMQ001.plugin.PluginResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Component
@Slf4j
public class EmailPlugin implements Plugin {

    private JavaMailSender mailSender;
    private Map<String, Object> configuration;

    @Override
    public String getName() {
        return "email";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Email sending plugin for notifications and alerts";
    }

    @Override
    public boolean canHandle(Message message) {
        return "email".equals(message.getTopic()) || 
               (message.getHeaders() != null && message.getHeaders().containsKey("email"));
    }

    @Override
    public Mono<PluginResult> process(Message message, Map<String, Object> config) {
        return Mono.fromCallable(() -> {
            try {
                String to = extractEmailAddress(message, config);
                String subject = extractSubject(message, config);
                String body = message.getContent();
                
                sendEmail(to, subject, body);
                
                return PluginResult.success(getName(), 
                    "Email sent successfully to " + to,
                    Map.of("recipient", to, "subject", subject));
                    
            } catch (Exception e) {
                log.error("Failed to send email for message {}: {}", message.getId(), e.getMessage());
                return PluginResult.failure(getName(), 
                    "Failed to send email: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> initialize(Map<String, Object> config) {
        return Mono.fromRunnable(() -> {
            this.configuration = config;
            log.info("Email plugin initialized with configuration");
        });
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.fromRunnable(() -> {
            log.info("Email plugin shutdown");
        });
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "defaultRecipient", Map.of(
                "type", "string",
                "description", "Default email recipient",
                "required", false
            ),
            "defaultSubjectPrefix", Map.of(
                "type", "string", 
                "description", "Prefix for email subjects",
                "required", false
            ),
            "fromAddress", Map.of(
                "type", "string",
                "description", "From email address",
                "required", false
            )
        );
    }
    
    private String extractEmailAddress(Message message, Map<String, Object> config) {
        // Try to get email from message headers
        if (message.getHeaders() != null && message.getHeaders().containsKey("to")) {
            return (String) message.getHeaders().get("to");
        }
        
        // Try to get from plugin config
        if (config.containsKey("recipient")) {
            return (String) config.get("recipient");
        }
        
        // Fall back to default recipient in configuration
        if (configuration != null && configuration.containsKey("defaultRecipient")) {
            return (String) configuration.get("defaultRecipient");
        }
        
        throw new IllegalArgumentException("No email recipient specified");
    }
    
    private String extractSubject(Message message, Map<String, Object> config) {
        String subject = "VthreadMQ Message";
        
        // Try to get subject from message headers
        if (message.getHeaders() != null && message.getHeaders().containsKey("subject")) {
            subject = (String) message.getHeaders().get("subject");
        }
        
        // Try to get from plugin config
        if (config.containsKey("subject")) {
            subject = (String) config.get("subject");
        }
        
        // Add prefix if configured
        if (configuration != null && configuration.containsKey("defaultSubjectPrefix")) {
            String prefix = (String) configuration.get("defaultSubjectPrefix");
            subject = prefix + " " + subject;
        }
        
        return subject;
    }
    
    private void sendEmail(String to, String subject, String body) {
        if (mailSender == null) {
            log.warn("JavaMailSender not configured, simulating email send");
            log.info("Simulated email - To: {}, Subject: {}, Body: {}", to, subject, body);
            return;
        }
        
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        
        if (configuration != null && configuration.containsKey("fromAddress")) {
            message.setFrom((String) configuration.get("fromAddress"));
        }
        
        mailSender.send(message);
        log.info("Email sent to {} with subject: {}", to, subject);
    }
} 