# VthreadMQ Architecture & Message Flow

This document provides visual representations of the VthreadMQ system architecture and message processing flow using Mermaid diagrams.

## 🏗️ Overall System Architecture

```mermaid
graph TB
    subgraph "Client Layer"
        Producer[Producer Client]
        Consumer[Consumer Client]
        WebSocketClient[WebSocket Client]
    end

    subgraph "API Layer"
        RestAPI[REST API Controller]
        WebSocketHandler[WebSocket Handler]
    end

    subgraph "Service Layer"
        MessageQueueService[Message Queue Service]
        NotificationService[Notification Service]
    end

    subgraph "Virtual Thread Pool"
        VT1[Virtual Thread 1]
        VT2[Virtual Thread 2]
        VTN[Virtual Thread N]
        Scheduler[Background Scheduler]
    end

    subgraph "Plugin System"
        EmailPlugin[Email Plugin]
        SlackPlugin[Slack Plugin]
        CustomPlugin[Custom Plugin]
    end

    subgraph "Repository Layer"
        MessageRepo[Message Repository]
        CursorRepo[Cursor Repository]
    end

    subgraph "Database Layer"
        SQLite[(SQLite Database)]
    end

    subgraph "Monitoring"
        Prometheus[Prometheus Metrics]
        Grafana[Grafana Dashboard]
        HealthCheck[Health Check]
    end

    Producer --> RestAPI
    Consumer --> RestAPI
    WebSocketClient --> WebSocketHandler
    
    RestAPI --> MessageQueueService
    WebSocketHandler --> NotificationService
    
    MessageQueueService --> VT1
    MessageQueueService --> VT2
    MessageQueueService --> VTN
    MessageQueueService --> Scheduler
    
    VT1 --> EmailPlugin
    VT2 --> SlackPlugin
    VTN --> CustomPlugin
    
    MessageQueueService --> MessageRepo
    MessageQueueService --> CursorRepo
    NotificationService --> WebSocketClient
    
    MessageRepo --> SQLite
    CursorRepo --> SQLite
    
    MessageQueueService --> Prometheus
    Prometheus --> Grafana
    RestAPI --> HealthCheck

    classDef clientLayer fill:#e1f5fe
    classDef apiLayer fill:#f3e5f5
    classDef serviceLayer fill:#e8f5e8
    classDef threadLayer fill:#fff3e0
    classDef pluginLayer fill:#fce4ec
    classDef repoLayer fill:#f1f8e9
    classDef dbLayer fill:#e3f2fd
    classDef monitorLayer fill:#fafafa

    class Producer,Consumer,WebSocketClient clientLayer
    class RestAPI,WebSocketHandler apiLayer
    class MessageQueueService,NotificationService serviceLayer
    class VT1,VT2,VTN,Scheduler threadLayer
    class EmailPlugin,SlackPlugin,CustomPlugin pluginLayer
    class MessageRepo,CursorRepo repoLayer
    class SQLite dbLayer
    class Prometheus,Grafana,HealthCheck monitorLayer
```

## 📨 Message Production Flow

```mermaid
sequenceDiagram
    participant P as Producer
    participant API as REST API
    participant MQS as MessageQueueService
    participant MR as MessageRepository
    participant DB as SQLite
    participant NS as NotificationService
    participant WS as WebSocket Clients

    P->>API: POST /api/produce
    API->>MQS: produceMessage(request)
    
    MQS->>MQS: Create Message Object
    MQS->>MQS: Determine Status (PENDING/SCHEDULED)
    MQS->>MQS: Calculate Scheduled Time
    
    MQS->>MR: save(message)
    MR->>DB: INSERT INTO messages
    MR->>DB: Get next offset
    DB-->>MR: Generated offset
    MR-->>MQS: Saved message with offset
    
    alt Message is immediately available
        MQS->>NS: notifyNewMessage(topic, message)
        NS->>WS: Broadcast notification
    end
    
    MQS-->>API: Message saved
    API-->>P: HTTP 200 + Message details
```

## 🔄 Message Consumption Flow

```mermaid
sequenceDiagram
    participant C as Consumer
    participant API as REST API
    participant MQS as MessageQueueService
    participant CR as CursorRepository
    participant MR as MessageRepository
    participant VT as Virtual Thread
    participant Plugin as Plugin System
    participant DB as SQLite

    C->>API: GET /api/consume
    API->>MQS: consumeMessages(request)
    
    MQS->>CR: getCommittedOffset(consumerGroup, topic)
    CR->>DB: SELECT offset FROM consumer_cursors
    DB-->>CR: Current offset
    CR-->>MQS: Committed offset
    
    MQS->>MR: findByTopicAndStatusAndOffset(topic, PENDING, offset, limit)
    MR->>DB: SELECT * FROM messages WHERE...
    DB-->>MR: Message list
    MR-->>MQS: Available messages
    
    loop For each message
        MQS->>VT: Start virtual thread
        VT->>MR: updateStatus(id, PROCESSING)
        VT->>Plugin: process(message)
        Plugin-->>VT: PluginResult
        
        alt Success
            VT->>MR: updateStatus(id, COMPLETED)
            VT->>CR: commitOffset(consumerGroup, topic, offset)
        else Failure
            VT->>MQS: handleMessageProcessingError()
            alt Retry available
                MQS->>MR: save(retryMessage)
            else Max retries exceeded
                MQS->>MR: updateStatus(id, DEAD_LETTER)
            end
        end
    end
    
    MQS-->>API: Processed messages
    API-->>C: HTTP 200 + Message list
```

## ⚡ Virtual Thread Processing

```mermaid
graph TD
    subgraph "Message Queue Service"
        MessageReceived[Message Received]
        CreateVirtualThread[Create Virtual Thread]
    end

    subgraph "Virtual Thread Pool"
        VThread1[Virtual Thread 1]
        VThread2[Virtual Thread 2]
        VThread3[Virtual Thread 3]
        VThreadN[Virtual Thread N]
    end

    subgraph "Processing Pipeline"
        UpdateStatus1[Update Status: PROCESSING]
        ProcessMessage[Process Message Business Logic]
        ExecutePlugin[Execute Plugin]
        UpdateStatus2[Update Status: COMPLETED/FAILED]
        CommitOffset[Commit Consumer Offset]
    end

    subgraph "Error Handling"
        CheckRetries{Retries Available?}
        ScheduleRetry[Schedule Retry with Backoff]
        DeadLetter[Move to Dead Letter Queue]
    end

    MessageReceived --> CreateVirtualThread
    CreateVirtualThread --> VThread1
    CreateVirtualThread --> VThread2
    CreateVirtualThread --> VThread3
    CreateVirtualThread --> VThreadN

    VThread1 --> UpdateStatus1
    VThread2 --> UpdateStatus1
    VThread3 --> UpdateStatus1
    VThreadN --> UpdateStatus1

    UpdateStatus1 --> ProcessMessage
    ProcessMessage --> ExecutePlugin
    ExecutePlugin --> UpdateStatus2
    UpdateStatus2 --> CommitOffset

    ProcessMessage --> CheckRetries
    CheckRetries -->|Yes| ScheduleRetry
    CheckRetries -->|No| DeadLetter

    classDef vthread fill:#fff3e0,stroke:#ff9800
    classDef processing fill:#e8f5e8,stroke:#4caf50
    classDef error fill:#ffebee,stroke:#f44336

    class VThread1,VThread2,VThread3,VThreadN vthread
    class UpdateStatus1,ProcessMessage,ExecutePlugin,UpdateStatus2,CommitOffset processing
    class CheckRetries,ScheduleRetry,DeadLetter error
```

## 🔌 Plugin System Architecture

```mermaid
graph TB
    subgraph "Message Processing"
        Message[Incoming Message]
        PluginManager[Plugin Manager]
    end

    subgraph "Plugin Interface"
        CanHandle{canHandle message ?}
        ProcessPlugin[process->message, config]
        PluginResult[PluginResult]
    end

    subgraph "Built-in Plugins"
        EmailPlugin[Email Plugin]
        SlackPlugin[Slack Plugin]
        WebhookPlugin[Webhook Plugin]
    end

    subgraph "Custom Plugins"
        CustomPlugin1[Custom Plugin 1]
        CustomPlugin2[Custom Plugin 2]
        CustomPluginN[Custom Plugin N]
    end

    subgraph "External Services"
        SMTP[SMTP Server]
        SlackAPI[Slack API]
        WebhookURL[Webhook URL]
        CustomAPI[Custom API]
    end

    Message --> PluginManager
    PluginManager --> CanHandle

    CanHandle -->|Yes| ProcessPlugin
    CanHandle -->|No| PluginManager

    ProcessPlugin --> EmailPlugin
    ProcessPlugin --> SlackPlugin
    ProcessPlugin --> WebhookPlugin
    ProcessPlugin --> CustomPlugin1
    ProcessPlugin --> CustomPlugin2
    ProcessPlugin --> CustomPluginN

    EmailPlugin --> SMTP
    SlackPlugin --> SlackAPI
    WebhookPlugin --> WebhookURL
    CustomPlugin1 --> CustomAPI
    CustomPlugin2 --> CustomAPI
    CustomPluginN --> CustomAPI

    EmailPlugin --> PluginResult
    SlackPlugin --> PluginResult
    WebhookPlugin --> PluginResult
    CustomPlugin1 --> PluginResult
    CustomPlugin2 --> PluginResult
    CustomPluginN --> PluginResult

    classDef plugin fill:#fce4ec,stroke:#e91e63
    classDef external fill:#e3f2fd,stroke:#2196f3
    classDef interface fill:#f3e5f5,stroke:#9c27b0

    class EmailPlugin,SlackPlugin,WebhookPlugin,CustomPlugin1,CustomPlugin2,CustomPluginN plugin
    class SMTP,SlackAPI,WebhookURL,CustomAPI external
    class CanHandle,ProcessPlugin,PluginResult interface
```

## 💾 Database Schema & Operations

```mermaid
erDiagram
    MESSAGES {
        string id PK
        string topic
        string content
        string headers
        integer created_at
        integer scheduled_at
        integer processed_at
        string status
        integer retry_count
        integer max_retries
        string error_message
        string consumer_group
        integer offset
    }

    CONSUMER_CURSORS {
        string id PK
        string consumer_group
        string topic
        integer offset
        integer last_committed
        string consumer_id
        boolean active
    }

    MESSAGES ||--o{ CONSUMER_CURSORS : "tracks consumption"
```

## 🔄 Scheduled Message Processing

```mermaid
graph TD
    subgraph "Background Scheduler"
        SchedulerTimer[Scheduler Timer: Every 10s]
        FindScheduled[Find Scheduled Messages]
    end

    subgraph "Message Processing"
        CheckTime{scheduled_at <= now?}
        UpdateToPending[Update Status: PENDING]
        NotifyConsumers[Notify WebSocket Subscribers]
    end

    subgraph "Cleanup Process"
        CleanupTimer[Cleanup Timer: Every 6h]
        FindOldMessages[Find Old Completed Messages]
        DeleteOld[Delete Messages > 24h old]
    end

    SchedulerTimer --> FindScheduled
    FindScheduled --> CheckTime
    CheckTime -->|Yes| UpdateToPending
    CheckTime -->|No| SchedulerTimer
    UpdateToPending --> NotifyConsumers
    NotifyConsumers --> SchedulerTimer

    CleanupTimer --> FindOldMessages
    FindOldMessages --> DeleteOld
    DeleteOld --> CleanupTimer

    classDef scheduler fill:#fff3e0,stroke:#ff9800
    classDef cleanup fill:#f1f8e9,stroke:#689f38

    class SchedulerTimer,FindScheduled,CheckTime,UpdateToPending,NotifyConsumers scheduler
    class CleanupTimer,FindOldMessages,DeleteOld cleanup
```

## 📊 Monitoring & Metrics Flow

```mermaid
graph LR
    subgraph "VthreadMQ Application"
        MQS[Message Queue Service]
        Micrometer[Micrometer Metrics]
        ActuatorEndpoints[Actuator Endpoints]
    end

    subgraph "Metrics Collection"
        ProducedCounter[Messages Produced Counter]
        ConsumedCounter[Messages Consumed Counter]
        FailedCounter[Failed Messages Counter]
        VThreadGauge[Active Virtual Threads Gauge]
    end

    subgraph "Monitoring Stack"
        Prometheus[Prometheus Server]
        Grafana[Grafana Dashboard]
        AlertManager[Alert Manager]
    end

    subgraph "Health Monitoring"
        HealthEndpoint[ /api/health ]
        HealthCheck[Health Check]
        StatusMonitor[Status Monitor]
    end

    MQS --> ProducedCounter
    MQS --> ConsumedCounter
    MQS --> FailedCounter
    MQS --> VThreadGauge

    ProducedCounter --> Micrometer
    ConsumedCounter --> Micrometer
    FailedCounter --> Micrometer
    VThreadGauge --> Micrometer

    Micrometer --> ActuatorEndpoints
    ActuatorEndpoints --> Prometheus
    Prometheus --> Grafana
    Prometheus --> AlertManager

    HealthEndpoint --> HealthCheck
    HealthCheck --> StatusMonitor
    StatusMonitor --> Prometheus

    classDef metrics fill:#e8f5e8,stroke:#4caf50
    classDef monitoring fill:#e3f2fd,stroke:#2196f3
    classDef health fill:#fff3e0,stroke:#ff9800

    class ProducedCounter,ConsumedCounter,FailedCounter,VThreadGauge,Micrometer metrics
    class Prometheus,Grafana,AlertManager monitoring
    class HealthEndpoint,HealthCheck,StatusMonitor health
```

## 🌐 WebSocket Real-time Notifications

```mermaid
sequenceDiagram
    participant C as Client
    participant WS as WebSocket Handler
    participant NS as Notification Service
    participant MQS as Message Queue Service

    C->>WS: Connect to /ws/notifications
    WS-->>C: Connection established

    C->>WS: {"action": "subscribe_topic", "topic": "notifications"}
    WS->>NS: subscribeToTopic("notifications", session)
    NS-->>WS: Subscription registered
    WS-->>C: {"type": "subscribed_to_topic", "target": "notifications"}

    Note over MQS: New message arrives
    MQS->>NS: notifyNewMessage("notifications", message)
    NS->>NS: Find subscribed sessions
    NS->>C: {"type": "NEW_MESSAGE", "topic": "notifications", ...}

    C->>WS: {"action": "ping"}
    WS-->>C: {"type": "pong", "timestamp": "..."}

    C->>WS: {"action": "unsubscribe"}
    WS->>NS: unsubscribe(session)
    NS-->>WS: Unsubscribed
    WS-->>C: {"type": "unsubscribed"}

    C->>WS: Disconnect
    WS->>NS: unsubscribe(session)
    Note over WS: Connection closed
```

---

## 🎯 Key Benefits Illustrated

### Virtual Threads Advantage
- **Traditional Threads**: Limited by OS thread count (~thousands)
- **Virtual Threads**: Can handle millions of concurrent operations
- **Memory Efficient**: Each virtual thread uses ~few KB vs ~1MB for OS threads
- **Simplified Code**: No need for complex async/await patterns

### Message Delivery Guarantees
- **At-least-once delivery**: Messages processed until successful or moved to dead letter queue
- **Offset-based tracking**: Reliable cursor management with persistent offsets
- **Automatic retry**: Configurable retry logic with exponential backoff
- **Dead letter queue**: Failed messages preserved for analysis

### Scalability Features
- **Auto-scaling**: Virtual thread pool grows/shrinks based on workload
- **Horizontal ready**: Designed for future cluster mode support
- **Efficient storage**: SQLite with proper indexing and cleanup
- **Plugin architecture**: Extensible without core system changes

This architecture provides a robust, scalable, and maintainable message queue system leveraging the latest Java 21 virtual threads technology. 