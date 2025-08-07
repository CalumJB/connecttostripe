CREATE TABLE mailchimp_users (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    stripe_account_id VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    server_prefix VARCHAR(255) NOT NULL,
    UNIQUE (stripe_account_id)
);
