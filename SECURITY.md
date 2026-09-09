# Security Policy

## Reporting a vulnerability

This is an independent, MIT-licensed, offline-first project. Security matters
because it handles audio recordings and transcripts for students.

Please **do not open a public issue** for a security vulnerability. Instead,
report it privately to the maintainers via GitHub's **private vulnerability
reporting**:

https://github.com/Franck-Sorel/voice-to-pdf/security/advisories/new

or by opening a **draft security advisory** through the repository's Security
tab.

## What to include

- Affected version / commit
- Steps to reproduce (or a proof of concept)
- Impact and any suggested fix (optional)

## Response

- A maintainer will acknowledge the report within **5 business days**.
- Once triaged, we will coordinate a disclosure timeline with you.
- We ask for a reasonable embargo window before public disclosure.

## Scope

- The Android app (Kotlin + native whisper.cpp) and its build/CI.
- Out of scope: the GGML model files themselves (MIT), the Android OS, and
  third-party libraries — report those to their respective projects.

## Our posture

- 100% offline by default; all P0 features work in airplane mode.
- Telemetry is opt-in and deliberately **least-data / PII-free** (see
  `docs/NFR.md "Telemetry & privacy"`).
- We never collect audio or transcript contents.

Thank you for helping keep the project safe.
