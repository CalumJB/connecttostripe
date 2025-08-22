CREATE TABLE stripe_webhook_counters (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    stripe_account_id VARCHAR(255) NOT NULL,
    year_month VARCHAR(7) NOT NULL,
    session_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(stripe_account_id, year_month)
);

CREATE INDEX idx_stripe_webhook_counters_account_month ON stripe_webhook_counters(stripe_account_id, year_month);