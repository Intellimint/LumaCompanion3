# HIPAA Controls in Luma Companion

This document outlines the technical measures implemented to ensure HIPAA compliance for the Luma Companion application. These controls protect patient health information (PHI) according to HIPAA requirements.

## Encryption at Rest

- **Patient Settings**: Encrypted using `EncryptedSharedPreferences` with AES-256-GCM for key encryption and AES-256-SIV for value encryption
- **Conversation Logs**: Stored in encrypted format using `EncryptedFile` with AES-256-GCM-HKDF-4KB encryption
- **Master Key**: Generated securely using Android's KeyStore system

## Encryption in Transit

- Enforced TLS 1.2+ for all network communications
- Disabled older, insecure TLS/SSL versions
- Used modern cipher suites for highest security standards

## PHI Redaction

- Implemented a `RedactingInterceptor` that automatically detects and redacts potential PHI from outbound requests
- PHI detection includes:
  - Social Security Numbers (e.g., 123-45-6789)
  - Zip codes (5-digit numeric patterns)
  - Medical record numbers
- Redaction happens before any data leaves the device

## User Data Control (HIPAA §164.524, §164.526)

- Users can export their conversation logs at any time
- Users can completely delete their data from the device
- No PHI is stored on remote servers

## Code Protection

- ProGuard enabled to obfuscate code in release builds
- Protection against reverse engineering and tampering

## Future Improvements

- Replace simple regex PHI detection with Bloom filter approach
- Add further PHI pattern detection
- Integration with Play Integrity API once app is published

## External Vendor Compliance

- No user PHI is shared with external vendors
- Consider BAAs (Business Associate Agreements) with any future cloud providers

## Disclosure

This implementation is designed to support HIPAA compliance requirements but should be reviewed by a qualified security professional before use in a regulated healthcare environment. 