CREATE TABLE IF NOT EXISTS preapproved (
    pre_approved_id VARCHAR(40) PRIMARY KEY,
    customer_id VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    available_amount NUMERIC(19, 2) NOT NULL CHECK (available_amount >= 0)
);

CREATE TABLE IF NOT EXISTS preapproved_usage (
    id BIGSERIAL PRIMARY KEY,
    request_reference VARCHAR(80) NOT NULL UNIQUE,
    pre_approved_id VARCHAR(40) NOT NULL,
    customer_id VARCHAR(40) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(40),
    payload_hash VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    remaining_amount NUMERIC(19, 2)
);

CREATE INDEX IF NOT EXISTS idx_preapproved_usage_processed_at
    ON preapproved_usage (processed_at DESC);
