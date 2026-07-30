CREATE TABLE sentiment_provider (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_code VARCHAR(60) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    weight DECIMAL(10,6) NOT NULL DEFAULT 0,
    collection_interval_seconds BIGINT NOT NULL DEFAULT 300,
    last_collection_at TIMESTAMP(6) NULL,
    last_success_at TIMESTAMP(6) NULL,
    last_status VARCHAR(30) NOT NULL DEFAULT 'NEVER_RUN',
    last_message VARCHAR(1000) NULL,
    api_key_env_var VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_sentiment_provider_code UNIQUE (provider_code)
);
