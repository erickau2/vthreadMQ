-- Messages table for storing queue messages
CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    topic TEXT NOT NULL,
    content TEXT NOT NULL,
    headers TEXT,
    created_at INTEGER NOT NULL,
    scheduled_at INTEGER,
    processed_at INTEGER,
    status TEXT NOT NULL,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    error_message TEXT,
    consumer_group TEXT,
    offset INTEGER NOT NULL
);

-- Consumer cursors table for tracking consumer progress
CREATE TABLE IF NOT EXISTS consumer_cursors (
    id TEXT PRIMARY KEY,
    consumer_group TEXT NOT NULL,
    topic TEXT NOT NULL,
    offset INTEGER NOT NULL,
    last_committed INTEGER NOT NULL,
    consumer_id TEXT,
    active BOOLEAN DEFAULT FALSE,
    UNIQUE(consumer_group, topic)
);

-- Indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_messages_topic_status ON messages(topic, status);
CREATE INDEX IF NOT EXISTS idx_messages_topic_offset ON messages(topic, offset);
CREATE INDEX IF NOT EXISTS idx_messages_scheduled_at ON messages(scheduled_at);
CREATE INDEX IF NOT EXISTS idx_messages_status ON messages(status);
CREATE INDEX IF NOT EXISTS idx_consumer_cursors_group_topic ON consumer_cursors(consumer_group, topic);
CREATE INDEX IF NOT EXISTS idx_consumer_cursors_active ON consumer_cursors(active); 