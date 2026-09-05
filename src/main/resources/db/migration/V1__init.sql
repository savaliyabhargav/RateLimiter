-- Business table: the one API we protect just stores the string it receives.
CREATE TABLE message (
    id         BIGSERIAL PRIMARY KEY,
    client_id  TEXT        NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_message_created_at ON message (created_at DESC);

-- ---------------------------------------------------------------------------
-- Rate limiter state. All three algorithms keep their counters in Postgres,
-- so the limit holds across restarts and across multiple app instances.
-- ---------------------------------------------------------------------------

-- FIXED WINDOW: one row per client holding the current window and its counter.
CREATE TABLE fixed_window_counter (
    client_id     TEXT   PRIMARY KEY,
    window_start  BIGINT NOT NULL, -- epoch millis of the window start
    request_count INT    NOT NULL
);

-- SLIDING WINDOW (log): one row per accepted request, pruned as it ages out.
CREATE TABLE sliding_window_log (
    id         BIGSERIAL PRIMARY KEY,
    client_id  TEXT   NOT NULL,
    request_at BIGINT NOT NULL -- epoch millis of the request
);

CREATE INDEX idx_sliding_window_log_client_time ON sliding_window_log (client_id, request_at);

-- TOKEN BUCKET: one row per client holding the token balance and last refill.
CREATE TABLE token_bucket (
    client_id   TEXT             PRIMARY KEY,
    tokens      DOUBLE PRECISION NOT NULL,
    last_refill BIGINT           NOT NULL -- epoch millis of the last refill
);
