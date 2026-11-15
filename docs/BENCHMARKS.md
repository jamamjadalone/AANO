# AANO Performance & Stress Benchmarks

This document details empirical benchmarks and stress testing results for the **AANO** offline-first architecture. All tests are conducted against production database schemas, encryption modules, and full-text search pipelines.

---

## 1. Test Environment Specification

Tests are executed on an isolated Linux benchmark runner to measure real-world performance without background hardware interference:

| Parameter | Specification |
| :--- | :--- |
| **Operating System** | Linux (Ubuntu / x86_64) |
| **Processor** | 4-Core Cloud Compute CPU |
| **System Memory** | 32 GB RAM |
| **Database Engine** | SQLite 3.45+ (WAL Journal Mode, Normal Sync) |
| **Search Subsystem** | SQLite FTS5 (Unicode61 Tokenizer) |
| **Cryptography** | Hardware AES-256-GCM + PBKDF2-HMAC-SHA256 (600,000 rounds) |

---

## 2. Empirical Benchmark Summary

The table below summarizes the results of the 6-tier stress testing suite:

| Tier | Test Domain | Workload | Benchmark Result | Status |
| :--- | :--- | :--- | :--- | :---: |
| **Tier 1** | **Massive Ingestion & FTS5** | 25,000 Multilingual Notes + 300 FTS5 queries | **70,333 notes/sec** ingest<br>**2.01 ms** avg search latency<br>DB footprint: **8.34 MB** | **PASS** |
| **Tier 2** | **Multi-Thread Concurrency** | 30 Concurrent Threads (15 Writers, 15 Readers) | **631 atomic writes** & **30,479 reads** in 5s<br>**0 Lock Errors** (WAL zero-deadlock) | **PASS** |
| **Tier 3** | **Vault Cryptography** | 600,000 PBKDF2 rounds + 1,000 AES-GCM payloads | KDF: **484.48 ms**<br>1,000 Encryptions: **0.021 s**<br>Tamper Detection: **100% (Bit-flip blocked)** | **PASS** |
| **Tier 4** | **Large Document Resilience** | 10,000 Markdown checkboxes & URLs | **0.009 s** parse time<br>Zero ReDoS / Catastrophic backtracking | **PASS** |
| **Tier 5** | **WAL Crash & Recovery** | SQLite WAL recovery & page integrity checks | `integrity_check`: **ok**<br>`quick_check`: **ok** | **PASS** |
| **Tier 6** | **Memory Footprint** | RSS Memory tracking during 25k notes | Peak RAM: **42.82 MB**<br>Baseline RSS: **29.00 MB** | **PASS** |

---

## 3. Tier Deep-Dive & Methodology

### Tier 1: Scalability & Full-Text Search (FTS5)
- **Ingestion**: 25,000 realistic notes containing mixed English text, Urdu/Arabic Unicode characters, code blocks, task checklists, and metadata.
- **Indexing**: Notes are indexed in SQLite FTS5 virtual tables using the `unicode61` tokenizer.
- **Results**: Complete 25,000-note database was generated and indexed in **0.36 seconds** (70,333 notes/second). Database size on disk remained at a compact **8.34 MB**.
- **Search Query Latency**: 300 complex token and prefix search queries across the 25k notes completed in **0.60 seconds** with an average latency of **2.01 milliseconds per query**.

### Tier 2: 30-Thread Concurrency & WAL Contention
- **Contention Scenario**: 15 worker threads executing rapid atomic note updates while 15 simultaneous worker threads perform continuous read lookups.
- **SQLite Configuration**: `PRAGMA journal_mode = WAL` with 10-second busy handlers.
- **Results**: Over 5 seconds of unthrottled contention, the engine serviced **631 atomic writes** and **30,479 reads** with **0 lock errors** (`sqlite3.OperationalError: database is locked` = 0).

### Tier 3: Cryptographic Vault & Tamper-Proofing
- **Key Derivation**: Evaluated against OWASP-recommended standard of **600,000 iterations** of PBKDF2-HMAC-SHA256 with 256-bit salt.
  - Latency: **484.48 milliseconds** (responsive user experience while maintaining robust brute-force resistance).
- **Payload Encryption**: 1,000 individual note payloads encrypted with authenticated AES-256-GCM in **21 milliseconds**.
- **Tamper Resistance**: Simulated ciphertext bit-flip attacks on encrypted payloads confirmed **100% authentication rejection** (tampered data is rejected immediately before decryption).

### Tier 4: Large Document Parsing
- Evaluated against a single massive text document containing 10,000 markdown checklist items (`- [x]` / `- [ ]`) and URLs.
- Parsing and regular expression extraction completed in **0.009 seconds** with zero regex hang or memory runaway.

### Tier 5 & 6: Data Integrity & Memory Footprint
- Full database health validation confirmed zero corrupted B-tree pages (`PRAGMA integrity_check = ok`).
- Total process memory remained under **43 MB RSS** throughout the entire test lifecycle, confirming low memory usage on resource-constrained devices.
