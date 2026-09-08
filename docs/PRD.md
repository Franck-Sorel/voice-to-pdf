# Product Requirements Document — Voice to PDF

- **Product:** Voice to PDF (working title)
- **Platform:** Android, min API 26 (Android 8.0)
- **License:** MIT
- **Status:** MVP 1 in development (scaffold); MVP 2 specified but not started
- **Last updated:** 2026-09-08

---

## 1. Problem

Students in low-connectivity environments (e.g. African universities) cannot
quickly document lecture notes or presentation content. They rely on slow
handwriting, expensive typists, or unreliable internet for transcription. The
"last mile" — getting spoken words into a print-ready PDF — is broken.

## 2. Target user

- University student
- Android device, mid-range, **4 GB RAM minimum**
- Unreliable or no internet
- Needs to capture **30–90 minutes** of lecture/presentation content and print
  it **same-day**

## 3. Product principles

1. **100% offline.** Every P0 feature works in airplane mode.
2. **$0 for the user.** No ads, no in-app purchases, no accounts.
3. **Free to redistribute.** Open source under MIT.
4. **Respect low-end hardware.** Must run on 4 GB RAM, no GPU assumed.
5. **Privacy by default.** Nothing ever leaves the device in the default flow.

---

## 4. MVP 1 — Voice to PDF (foundation)

### 4.1 Scope — In

| #  | Requirement | Prio |
|----|-------------|------|
| F1 | Record audio in-app (mic input) or import from file (MP3/WAV/OGG) | P0 |
| F2 | Transcribe audio using a local Whisper model (tiny or base) | P0 |
| F3 | Display transcript with editable text field (user can fix errors) | P0 |
| F4 | Export transcript as a print-ready PDF (A4, 11 pt, 1.5 line spacing, header with title/date) | P0 |
| F5 | Save sessions locally (list of past recordings + transcripts) | P0 |
| F6 | Share PDF via Android intent (WhatsApp, email, print) | P1 |
| F7 | Basic text formatting in PDF: auto-paragraph breaks on pause > 2 s | P1 |
| F8 | Language selection (EN, FR, PT, Swahili, Arabic — top 5 by target region) | P1 |

### 4.2 Scope — Out (explicitly excluded from MVP 1)

- Cloud processing of any kind
- AI structuring / auto-sectioning
- Multi-speaker diarization
- Real-time transcription (transcription happens after recording stops)
- Sync, accounts, subscriptions
- iOS, tablet, desktop

### 4.3 Non-functional requirements (MVP 1)

| Requirement | Target |
|-------------|--------|
| Transcription speed | ≤ 1.5× real-time on 4 GB RAM device (30 min audio → ≤ 45 min processing) |
| APK size | ≤ 80 MB (Whisper tiny is ~39 MB) |
| Battery | ≤ 15% drain per 30-min recording + transcription session |
| Storage | Transcripts as plain text; audio retained only if user opts in |
| Offline | 100% of P0 features work in airplane mode |
| Cost to user | $0, no ads, no in-app purchases |
| Min Android | API 26 (Android 8.0) |

### 4.4 Success metrics (6 months post-launch)

- 5,000+ installs in target markets (Nigeria, Kenya, South Africa, Senegal, Ethiopia)
- ≥ 70% of sessions complete (record → transcribe → export PDF) without crash
- Play Store rating ≥ 4.2
- Zero revenue target (open-source or freemium with no paywall on core flow)

### 4.5 Risks & mitigations (MVP 1)

| Risk | Mitigation |
|------|------------|
| Whisper tiny accuracy poor on accented speech | Let user pick model size (tiny/base/small) with trade-off warning; ship base as default |
| Low-end devices (< 3 GB RAM) OOM | Detect RAM at launch; show "requires 4 GB" screen if below threshold |
| PDF formatting looks ugly | Keep layout minimal (single column, no fancy styling); test on 3 common printers |
| No funding for development | Open-source under MIT; seek university partnership for hosting CI/CD |

---

## 5. MVP 2 — AI-enhanced structuring (future)

### 5.1 Problem

Raw transcription is a wall of text. Students need structured,
presentation-ready documents: sections, sub-headings, bullet points, speaker
labels. Doing this manually defeats the purpose of voice capture.

### 5.2 Target user

Same as MVP 1, but with **intermittent connectivity** (can process 1–2× per day
when Wi-Fi is available) **or** a more capable device (6 GB+ RAM) for on-device
LLM.

### 5.3 Scope — In

| #  | Requirement | Prio |
|----|-------------|------|
| F1 | Accept a raw transcript (from MVP 1 or manual paste) | P0 |
| F2 | Auto-detect and insert section headings ("Introduction", "Main Points", "Conclusion") | P0 |
| F3 | Convert run-on sentences into bullet points where appropriate | P0 |
| F4 | Detect speaker changes (diarization) and label "Speaker 1 / Speaker 2" | P1 |
| F5 | Suggest a title for the document | P1 |
| F6 | "Polish" mode: fix grammar, remove filler words ("um", "like", "so basically") | P1 |
| F7 | User can accept/reject each structural change (diff view) before finalizing | P0 |
| F8 | Export structured PDF (same format as MVP 1, with heading hierarchy) | P0 |
| F9 | "Presentation mode": reformat as slide-style PDF (one section per page, larger font) | P2 |

### 5.4 Scope — Out

- Real-time structuring during recording (always post-hoc)
- Generating new content (summaries, flashcards, quiz questions) — that's MVP 3
- Multi-document projects / workspaces
- Collaboration / sharing of structured docs

### 5.5 Architecture decision: on-device vs cloud

The critical fork for MVP 2:

| Approach | Pros | Cons |
|----------|------|------|
| On-device LLM (Phi-3-mini 3.8B, Llama-3.2 1B, via MLC-LLM or llama.cpp) | Works offline, no cost, privacy | Requires 6+ GB RAM; slow (30–60 s/page); lower quality |
| Cloud API (user's own API key or free-tier provider) | Higher quality, faster, smaller app | Requires internet; potential cost; privacy concern |
| Hybrid (default on-device, optional cloud "enhance" button) | Best of both | More complex UX; two code paths to maintain |

**Decision: ship hybrid.** Default = on-device (Phi-3-mini or Llama-3.2 1B
quantized to Q4). Optional "Enhance with Cloud" button for users with
connectivity who want higher quality. No accounts, no mandatory API keys —
cloud is opt-in with a clearly labeled "uses your data over the network"
consent.

### 5.6 Structuring prompt (on-device)

> "You are a note-taker. Given raw lecture transcript text, restructure it
> into: a title, 3–7 section headings (## level), bullet points under each
> section. Preserve all factual content; do NOT add information not in the
> source. Output in Markdown."

Output is parsed, rendered as a diff against the raw transcript, and user
approves before PDF export.

### 5.7 Non-functional requirements (MVP 2)

| Requirement | Target |
|-------------|--------|
| Structuring latency (on-device) | ≤ 60 s for 2,000-word transcript on 6 GB RAM device |
| Structuring latency (cloud) | ≤ 10 s |
| Model size (on-device) | ≤ 2.5 GB (Q4 quantized) |
| Privacy | On-device: zero data leaves device. Cloud: data deleted after processing, no logging |
| Cost to user | Free (on-device). Cloud: free up to 5 docs/day, then $1/month or bring-your-own-key |

### 5.8 Success metrics (6 months post-launch)

- ≥ 40% of MVP 1 users upgrade to / use MVP 2 structuring
- ≥ 60% of structured documents accepted without manual edit (i.e. "Accept All")
- Cloud opt-in rate < 20% (validates on-device is sufficient for most)

### 5.9 Risks & mitigations (MVP 2)

| Risk | Mitigation |
|------|------------|
| On-device LLM hallucinates / adds content | Constrain prompt aggressively; show diff; accept/reject per section |
| Model too slow on target devices | Ship 1B model as default; 3B as optional download; show progress bar |
| Structuring quality inconsistent across languages | Test on 5 target languages; fall back to "paragraph breaks only" if confidence < threshold |
| Scope creep toward "AI tutor" | Hard boundary: MVP 2 only restructures existing text, never generates new knowledge |

---

## 6. Relationship between the two MVPs

```mermaid
flowchart LR
    subgraph MVP1["MVP 1 (foundation)"]
        A["Record / Import"] --> B["Transcribe (local Whisper)"]
        B --> C["Edit (manual)"]
        C --> D["Export PDF (plain)"]
    end
    subgraph MVP2["MVP 2 (enhancement)"]
        E["Raw transcript"] --> F["Structure (sections, bullets, speakers)"]
        F --> G["Diff review: accept/reject"]
        G --> H["Export PDF (formatted)"]
    end
    C -->|"transcript"| E
    D -.->|"fallback: paste text"| E
```

- MVP 2 depends on MVP 1's transcription pipeline (or accepts pasted text as a
  fallback).
- MVP 2 is a separate APK or in-app module, installable/updatable
  independently.
- A user can install only MVP 1 and never see MVP 2 — the app must be fully
  functional without it.
