// AANO Cloud Sync API — OPEN SOURCE EDITION (2026)
// Zero-Abuse Guarantee: Official Gmail Mailer Bridge + IP Rate Limiter + Anti-DDoS
// NOTE: All secrets are supplied via Cloudflare Environment Variables (env)

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-AANO-KEY",
};

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    const path = url.pathname;
    const clientIp = request.headers.get("CF-Connecting-IP") || "0.0.0.0";

    // Public healthcheck
    if (path === "/api/health" || path === "/") {
      return jsonResponse({ status: "ok", service: "AANO Shielded Edge (Open Source Edition)" });
    }

    // SECURITY GUARD: App-Key Handshake (Checked against env.SECRET_APP_KEY)
    const secretKey = env.SECRET_APP_KEY;
    const clientAppKey = request.headers.get("X-AANO-KEY");
    if (!secretKey || clientAppKey !== secretKey) {
      return jsonResponse({ error: "Access Denied: Invalid Client Signature." }, 403);
    }

    // Auto-create required security tables if not exists
    await ensureSchema(env);

    try {
      if (path === "/api/request-otp" && request.method === "POST") {
        return await handleRequestOtp(request, env, clientIp);
      }
      if (path === "/api/verify-and-register" && request.method === "POST") {
        return await handleVerifyAndRegister(request, env, clientIp);
      }
      if (path === "/api/reset-password" && request.method === "POST") {
        return await handleResetPassword(request, env, clientIp);
      }
      if (path === "/api/login" && request.method === "POST") {
        return await handleLogin(request, env, clientIp);
      }
      if (path === "/api/sync" && request.method === "POST") {
        return await handleSync(request, env);
      }

      return jsonResponse({ error: "Endpoint not found" }, 404);
    } catch (err) {
      return jsonResponse({ error: err.message || "Secure request failed" }, 500);
    }
  },
};

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}

async function ensureSchema(env) {
  await env.DB.prepare(`
    CREATE TABLE IF NOT EXISTS email_otps (
      canonical_email TEXT PRIMARY KEY,
      otp_code TEXT,
      created_at INTEGER,
      attempts INTEGER DEFAULT 0
    );
  `).run();

  await env.DB.prepare(`
    CREATE TABLE IF NOT EXISTS ip_rate_limits (
      ip TEXT,
      action TEXT,
      attempts INTEGER,
      window_start INTEGER,
      PRIMARY KEY (ip, action)
    );
  `).run();

  try {
    await env.DB.prepare("ALTER TABLE notes ADD COLUMN archived INTEGER DEFAULT 0;").run();
  } catch (_) {}
}

// -------------------------------------------------------------
// IP RATE LIMITER (DDoS & Brute-Force Guard)
// -------------------------------------------------------------
async function checkIpRateLimit(env, ip, action, maxAttempts, windowMs) {
  const now = Date.now();
  const record = await env.DB.prepare(
    "SELECT attempts, window_start FROM ip_rate_limits WHERE ip = ? AND action = ?"
  ).bind(ip, action).first();

  if (!record) {
    await env.DB.prepare(
      "INSERT INTO ip_rate_limits (ip, action, attempts, window_start) VALUES (?, ?, 1, ?)"
    ).bind(ip, action, now).run();
    return { allowed: true };
  }

  // Window expired -> reset
  if (now - record.window_start > windowMs) {
    await env.DB.prepare(
      "UPDATE ip_rate_limits SET attempts = 1, window_start = ? WHERE ip = ? AND action = ?"
    ).bind(now, ip, action).run();
    return { allowed: true };
  }

  // Still within window
  if (record.attempts >= maxAttempts) {
    const retryAfterSec = Math.ceil((record.window_start + windowMs - now) / 1000);
    return { allowed: false, retryAfterSec };
  }

  await env.DB.prepare(
    "UPDATE ip_rate_limits SET attempts = attempts + 1 WHERE ip = ? AND action = ?"
  ).bind(ip, action).run();

  return { allowed: true };
}

// -------------------------------------------------------------
// MAXIMUM GMAIL SANITIZER (Kills +, dots, googlemail, unicode)
// -------------------------------------------------------------
function sanitizeGmail(rawEmail) {
  let email = (rawEmail || "").trim().toLowerCase();
  email = email.replace(/[\u200B-\u200D\uFEFF]/g, "");

  const isGmail = email.endsWith("@gmail.com");
  const isGoogleMail = email.endsWith("@googlemail.com");

  if (!isGmail && !isGoogleMail) {
    return { valid: false, error: "Only official Google Gmail accounts are allowed." };
  }

  // Plus '+' alias trick BANNED
  if (email.includes("+")) {
    return { valid: false, error: "Gmail alias '+' tricks are strictly prohibited. Use your real address." };
  }

  const atIndex = email.lastIndexOf("@");
  const rawUsername = email.substring(0, atIndex);

  // Only ASCII letters, numbers and dots allowed
  if (!/^[a-z0-9.]+$/.test(rawUsername)) {
    return { valid: false, error: "Gmail address contains invalid or deceptive characters." };
  }

  // Cannot start or end with a dot, no consecutive dots
  if (rawUsername.startsWith(".") || rawUsername.endsWith(".") || rawUsername.includes("..")) {
    return { valid: false, error: "Invalid Gmail username structure." };
  }

  // Strip ALL dots to get canonical base username
  const canonicalUsername = rawUsername.replace(/\./g, "");

  // Google rule: 6 to 30 characters
  if (canonicalUsername.length < 6 || canonicalUsername.length > 30) {
    return { valid: false, error: "Gmail username must be between 6 and 30 characters." };
  }

  const canonicalEmail = `${canonicalUsername}@gmail.com`;
  return { valid: true, canonicalEmail, displayEmail: `${rawUsername}@gmail.com` };
}

// Password Hashing with Salt
async function hashPassword(password) {
  const msgUint8 = new TextEncoder().encode(password + "_aano_ultra_salt_2026_pk");
  const hashBuffer = await crypto.subtle.digest("SHA-256", msgUint8);
  return Array.from(new Uint8Array(hashBuffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

// -------------------------------------------------------------
// 1. STEP 1: REQUEST 6-DIGIT OTP (Supports 'register' & 'reset')
// -------------------------------------------------------------
async function handleRequestOtp(request, env, clientIp) {
  // IP Rate Limit: Max 5 OTP requests per 15 minutes per IP
  const rateCheck = await checkIpRateLimit(env, clientIp, "request_otp", 5, 15 * 60 * 1000);
  if (!rateCheck.allowed) {
    return jsonResponse({ error: `Too many requests from this IP. Please wait ${rateCheck.retryAfterSec} seconds.` }, 429);
  }

  const body = await request.json();
  const email = body.email;
  const purpose = (body.purpose || "register").toLowerCase().trim();

  const check = sanitizeGmail(email);
  if (!check.valid) {
    return jsonResponse({ error: check.error }, 400);
  }

  // Check if account already exists
  const existing = await env.DB.prepare("SELECT id FROM users WHERE email = ?").bind(check.canonicalEmail).first();

  if (purpose === "reset") {
    // For password reset, user MUST already exist
    if (!existing) {
      return jsonResponse({ error: "No account found registered with this Gmail address." }, 404);
    }
  } else {
    // For registration, user MUST NOT exist
    if (existing) {
      return jsonResponse({ error: "An account already exists for this Google mailbox. Please login or reset password." }, 400);
    }
  }

  // Rate limit: 60s cooldown per specific email
  const existingOtp = await env.DB.prepare("SELECT created_at FROM email_otps WHERE canonical_email = ?")
    .bind(check.canonicalEmail)
    .first();

  if (existingOtp && Date.now() - existingOtp.created_at < 60000) {
    return jsonResponse({ error: "Please wait 60 seconds before requesting another code." }, 429);
  }

  // Generate 6-Digit Cryptographic OTP
  const array = new Uint32Array(1);
  crypto.getRandomValues(array);
  const otp = String(100000 + (array[0] % 900000));

  // Store in D1
  await env.DB.prepare(`
    INSERT INTO email_otps (canonical_email, otp_code, created_at, attempts)
    VALUES (?, ?, ?, 0)
    ON CONFLICT(canonical_email) DO UPDATE SET
      otp_code = excluded.otp_code,
      created_at = excluded.created_at,
      attempts = 0
  `).bind(check.canonicalEmail, otp, Date.now()).run();

  // Send Email via Official Google Mail Bridge
  const bridgeUrl = env.MAIL_BRIDGE_URL;
  const bridgeSecret = env.MAIL_BRIDGE_SECRET;

  if (!bridgeUrl || !bridgeSecret) {
    return jsonResponse({ error: "Server mail bridge is not configured. Contact administrator." }, 500);
  }

  const mailRes = await fetch(bridgeUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    redirect: "follow",
    body: JSON.stringify({
      key: bridgeSecret,
      to: check.canonicalEmail,
      otp: otp,
    }),
  });

  const mailData = await mailRes.json();
  if (!mailData.success) {
    return jsonResponse({ error: mailData.error || "Failed to dispatch email via Google Bridge." }, 500);
  }

  return jsonResponse({
    success: true,
    message: "6-Digit verification code sent to your Gmail inbox.",
    canonicalEmail: check.canonicalEmail,
  });
}

// -------------------------------------------------------------
// 2. STEP 2: VERIFY OTP AND REGISTER
// -------------------------------------------------------------
async function handleVerifyAndRegister(request, env, clientIp) {
  const { email, otp, password } = await request.json();
  const check = sanitizeGmail(email);
  if (!check.valid) {
    return jsonResponse({ error: check.error }, 400);
  }

  if (!password || password.length < 8) {
    return jsonResponse({ error: "Password must be at least 8 characters." }, 400);
  }

  const record = await env.DB.prepare("SELECT otp_code, created_at, attempts FROM email_otps WHERE canonical_email = ?")
    .bind(check.canonicalEmail)
    .first();

  if (!record) {
    return jsonResponse({ error: "No pending verification code found. Request a new one." }, 400);
  }

  // 10 minutes expiry
  if (Date.now() - record.created_at > 10 * 60 * 1000) {
    await env.DB.prepare("DELETE FROM email_otps WHERE canonical_email = ?").bind(check.canonicalEmail).run();
    return jsonResponse({ error: "Verification code has expired. Request a new one." }, 400);
  }

  if (record.attempts >= 5) {
    await env.DB.prepare("DELETE FROM email_otps WHERE canonical_email = ?").bind(check.canonicalEmail).run();
    return jsonResponse({ error: "Too many failed attempts. Code invalidated." }, 429);
  }

  if (String(record.otp_code).trim() !== String(otp || "").trim()) {
    await env.DB.prepare("UPDATE email_otps SET attempts = attempts + 1 WHERE canonical_email = ?")
      .bind(check.canonicalEmail)
      .run();
    return jsonResponse({ error: "Invalid verification code." }, 400);
  }

  // OTP verified! Clean up
  await env.DB.prepare("DELETE FROM email_otps WHERE canonical_email = ?").bind(check.canonicalEmail).run();

  const userId = crypto.randomUUID();
  const passHash = await hashPassword(password);

  await env.DB.prepare("INSERT INTO users (id, email, password_hash, created_at) VALUES (?, ?, ?, ?)")
    .bind(userId, check.canonicalEmail, passHash, Date.now())
    .run();

  const token = btoa(`${userId}:${Date.now()}:${passHash.substring(0, 16)}`);
  return jsonResponse({ success: true, userId, email: check.canonicalEmail, token });
}

// -------------------------------------------------------------
// 3. STEP 3: RESET PASSWORD VIA OTP
// -------------------------------------------------------------
async function handleResetPassword(request, env, clientIp) {
  const { email, otp, password } = await request.json();
  const check = sanitizeGmail(email);
  if (!check.valid) {
    return jsonResponse({ error: check.error }, 400);
  }

  if (!password || password.length < 8) {
    return jsonResponse({ error: "Password must be at least 8 characters." }, 400);
  }

  const user = await env.DB.prepare("SELECT id, email FROM users WHERE email = ?").bind(check.canonicalEmail).first();
  if (!user) {
    return jsonResponse({ error: "No account found registered with this Gmail address." }, 404);
  }

  const record = await env.DB.prepare("SELECT otp_code, created_at, attempts FROM email_otps WHERE canonical_email = ?")
    .bind(check.canonicalEmail)
    .first();

  if (!record) {
    return jsonResponse({ error: "No pending verification code found. Request a new one." }, 400);
  }

  // 10 minutes expiry
  if (Date.now() - record.created_at > 10 * 60 * 1000) {
    await env.DB.prepare("DELETE FROM email_otps WHERE canonical_email = ?").bind(check.canonicalEmail).run();
    return jsonResponse({ error: "Verification code has expired. Request a new one." }, 400);
  }

  if (record.attempts >= 5) {
    await env.DB.prepare("DELETE FROM email_otps WHERE canonical_email = ?").bind(check.canonicalEmail).run();
    return jsonResponse({ error: "Too many failed attempts. Code invalidated." }, 429);
  }

  if (String(record.otp_code).trim() !== String(otp || "").trim()) {
    await env.DB.prepare("UPDATE email_otps SET attempts = attempts + 1 WHERE canonical_email = ?")
      .bind(check.canonicalEmail)
      .run();
    return jsonResponse({ error: "Invalid verification code." }, 400);
  }

  // OTP verified! Clean up
  await env.DB.prepare("DELETE FROM email_otps WHERE canonical_email = ?").bind(check.canonicalEmail).run();

  const passHash = await hashPassword(password);
  await env.DB.prepare("UPDATE users SET password_hash = ? WHERE id = ?")
    .bind(passHash, user.id)
    .run();

  const token = btoa(`${user.id}:${Date.now()}:${passHash.substring(0, 16)}`);
  return jsonResponse({ success: true, userId: user.id, email: user.email, token });
}

// -------------------------------------------------------------
// 4. USER LOGIN (Brute-Force Shield)
// -------------------------------------------------------------
async function handleLogin(request, env, clientIp) {
  // IP Rate Limit: Max 10 attempts per 15 minutes per IP
  const rateCheck = await checkIpRateLimit(env, clientIp, "login", 10, 15 * 60 * 1000);
  if (!rateCheck.allowed) {
    return jsonResponse({ error: `Too many login attempts. Please wait ${rateCheck.retryAfterSec} seconds.` }, 429);
  }

  const { email, password } = await request.json();
  const check = sanitizeGmail(email);
  if (!check.valid) {
    return jsonResponse({ error: check.error }, 400);
  }

  const passHash = await hashPassword(password);
  const user = await env.DB.prepare("SELECT id, email, password_hash FROM users WHERE email = ? AND password_hash = ?")
    .bind(check.canonicalEmail, passHash)
    .first();

  if (!user) {
    return jsonResponse({ error: "Invalid Gmail address or password." }, 401);
  }

  const token = btoa(`${user.id}:${Date.now()}:${passHash.substring(0, 16)}`);
  return jsonResponse({ success: true, userId: user.id, email: user.email, token });
}

// Helper: Token validation
async function authUser(request, env) {
  const authHeader = request.headers.get("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) return null;
  const token = authHeader.substring(7);
  try {
    const decoded = atob(token);
    const [userId, _, hashPrefix] = decoded.split(":");
    const user = await env.DB.prepare("SELECT id, password_hash FROM users WHERE id = ?").bind(userId).first();
    if (user && user.password_hash.startsWith(hashPrefix)) {
      return user.id;
    }
  } catch (e) {
    return null;
  }
  return null;
}

// -------------------------------------------------------------
// 5. BI-DIRECTIONAL SYNC (Strict Notes only, with Size Caps)
// -------------------------------------------------------------
async function handleSync(request, env) {
  const userId = await authUser(request, env);
  if (!userId) {
    return jsonResponse({ error: "Unauthorized access token." }, 401);
  }

  const payload = await request.json();
  const clientNotes = payload.notes || [];
  const lastSyncTime = Number(payload.lastSyncTime) || 0;

  // Process Notes (Max 50KB limit per note, max 100 notes batch)
  for (const n of clientNotes.slice(0, 100)) {
    const title = String(n.title || "").substring(0, 250);
    const content = String(n.content || "").substring(0, 50000); // 50KB cap
    const folder = String(n.folder || "").substring(0, 50);
    const color = Number(n.color) || 0;
    const pinned = n.pinned ? 1 : 0;
    const deleted = n.deleted ? 1 : 0;
    const archived = n.archived ? 1 : 0;
    const updatedAt = Number(n.updatedAt) || Date.now();
    const noteId = String(n.id || "").substring(0, 64);

    if (!noteId) continue;

    const existing = await env.DB.prepare("SELECT updated_at FROM notes WHERE id = ? AND user_id = ?")
      .bind(noteId, userId)
      .first();

    if (!existing || updatedAt > existing.updated_at) {
      await env.DB.prepare(`
        INSERT INTO notes (id, user_id, title, content, color, folder, pinned, updated_at, deleted, archived)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          title = excluded.title,
          content = excluded.content,
          color = excluded.color,
          folder = excluded.folder,
          pinned = excluded.pinned,
          updated_at = excluded.updated_at,
          deleted = excluded.deleted,
          archived = excluded.archived
      `).bind(noteId, userId, title, content, color, folder, pinned, updatedAt, deleted, archived).run();
    }
  }

  // Fetch updated notes from server
  const serverNotes = await env.DB.prepare(
    "SELECT id, title, content, color, folder, pinned, updated_at, deleted, archived FROM notes WHERE user_id = ? AND updated_at > ?"
  ).bind(userId, lastSyncTime).all();

  return jsonResponse({
    success: true,
    serverTime: Date.now(),
    notes: (serverNotes.results || []).map((r) => ({
      id: r.id,
      title: r.title,
      content: r.content,
      color: r.color,
      folder: r.folder,
      pinned: r.pinned === 1,
      updatedAt: r.updated_at,
      deleted: r.deleted === 1,
      archived: r.archived === 1,
    })),
  });
}
