# Security Policy

AANO is designed from the ground up with a **security-first, privacy-by-design** philosophy. All notes, checklists, and sensitive user payloads are stored using authenticated hardware-backed encryption.

## Threat Model & Cryptographic Guarantees

1. **At-Rest Encryption**:
   - Data stored on-device is encrypted using **AES-256-GCM** with 128-bit authentication tags.
   - Master encryption keys are generated and protected by the hardware-backed **Android Keystore**, ensuring keys cannot be extracted from device memory by unprivileged processes or root users.
2. **Authentication & PIN Storage**:
   - App PINs are never stored in plaintext or with basic MD5/SHA-1 hashing.
   - PINs are hashed using **PBKDF2WithHmacSHA256** with high iteration counts and cryptographic per-user random salts.
3. **Biometric Security**:
   - Integrates with Android's official `BiometricPrompt` framework with crypto-object binding.
4. **Cloud Privacy**:
   - Google Drive backups are strictly confined to the isolated **`appdata`** folder scope.
   - No external analytics, telemetry trackers, or proprietary third-party advertising SDKs exist in the codebase.
5. **Screen Privacy**:
   - Includes screenshot and app-switcher preview protection via `FLAG_SECURE` when lock mode is engaged.

## Reporting a Vulnerability

If you discover any security vulnerabilities, memory leak risks exposing key material, or cryptographic weaknesses, please report them privately:

- **Email**: Create a private vulnerability disclosure report via [GitLab Security Advisories](https://gitlab.com/jamamjadalone/AANO/security/advisories)
- **Direct Contact**: Reach out via GitLab profile issues or private channel.

Please allow up to 48 hours for a response before publicly disclosing any sensitive findings.
