-- AANO Cloudflare D1 Database Schema
-- Execute in Cloudflare Dashboard -> Workers & Pages -> D1 -> aano-db -> Console

CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    email TEXT UNIQUE,
    password_hash TEXT,
    created_at INTEGER
);

CREATE TABLE IF NOT EXISTS notes (
    id TEXT PRIMARY KEY,
    user_id TEXT,
    title TEXT,
    content TEXT,
    color INTEGER,
    folder TEXT,
    pinned INTEGER,
    updated_at INTEGER,
    deleted INTEGER
);

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
