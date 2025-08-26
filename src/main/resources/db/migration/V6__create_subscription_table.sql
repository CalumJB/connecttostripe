CREATE TABLE subscriptions (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    stripe_customer_id VARCHAR(255) NOT NULL,
    stripe_subscription_id VARCHAR(255) NOT NULL UNIQUE,
    stripe_account_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    plan_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (stripe_account_id) REFERENCES stripe_users(stripe_account_id)
);

CREATE INDEX idx_subscriptions_customer_id ON subscriptions(stripe_customer_id);
CREATE INDEX idx_subscriptions_account_id ON subscriptions(stripe_account_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);