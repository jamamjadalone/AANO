-- AANO Cloudflare D1 Database Schema - Dedicated Email Newsletter Engine
-- Execute in Cloudflare Dashboard -> Workers & Pages -> D1 -> aano-db -> Console

CREATE TABLE IF NOT EXISTS email_otps (
    canonical_email TEXT PRIMARY KEY,
    otp_code TEXT,
    created_at INTEGER,
    attempts INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ip_rate_limits (
    ip TEXT,
    action TEXT,
    attempts INTEGER,
    window_start INTEGER,
    PRIMARY KEY (ip, action)
);

CREATE TABLE IF NOT EXISTS newsletter_subscribers (
    email TEXT PRIMARY KEY,
    source TEXT DEFAULT 'app',
    subscribed_at INTEGER,
    confirmed INTEGER DEFAULT 1,
    unsubscribed INTEGER DEFAULT 0
);

