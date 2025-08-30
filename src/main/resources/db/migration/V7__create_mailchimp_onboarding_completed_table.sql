CREATE TABLE mailchimp_onboarding_completed (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    stripe_account_id VARCHAR(255) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    completed_at TIMESTAMP NOT NULL
);