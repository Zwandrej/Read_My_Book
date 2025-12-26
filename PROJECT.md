201~200~# PROJECT.md — Offline EPUB TTS Player (Android / Boox) — Build Plan

## Goal (MVP)
Build an Android app that:
- Imports an EPUB file
- Lists chapters (basic chapter navigation)
- Reads aloud using **local/offline Android TTS**
- Remembers where you stopped and resumes
- Offers skip backward/forward (implemented reliably as "jump by sentences", optionally displayed as ~seconds)

This must work well on e-ink devices (Boox): minimal UI animations, stable background audio.

---

## Current Status
- Milestone 0: complete (Android Studio project created, runs on emulator)
- Milestone 1: complete (TextToSpeech speak/pause with rate slider)
- Milestone 2: complete (sentence chunking with back/forward and play-all)
- Milestone 3: complete (SAF import, persisted URI, filename display)
- Milestone 4: in progress (EPUB zip scan works; OPF spine parsing still falling back)
- Milestone 6: complete (foreground service playback for background TTS)

## Open Issues
- App icon still shows default Android icon on device
  - Tried: replace mipmap PNGs, add adaptive icon XMLs in `mipmap-anydpi-v26`,
    switch adaptive layers to PNG foreground + transparent background,
    clean/rebuild, uninstall/reinstall
  - Next: inspect APK with `aapt`/`apkanalyzer` and verify launcher cache on device

---

## Non-goals (for MVP)
- Cloud sync, online voices
- Fancy typography or full ebook reader UI
- Perfect “skip by exact seconds” mapping (we’ll do sentence-based skipping first)

---

## Recommended Tech Stack (beginner-friendly)
- Language: **Kotlin**
- UI: **Jetpack Compose** (simple screens, fewer XML headaches)
- Storage:
  - EPUB import via Android **Storage Access Framework** (user picks file)
  - Persist state with **Room** (SQLite) or DataStore
- EPUB parsing:
   - Start with a library that can parse EPUB structure, then extract HTML → text
   - Convert HTML to readable text (basic)
- TTS:
  - Android `TextToSpeech` API (offline voices installed on device)
- Audio session / media:
  - Use a foreground service for reliable playback (later milestone)
  - For MVP we can start with in-app playback, then upgrade to background

---

## Architecture Overview
We’ll keep it simple but structured:

### Data Model
- Book
  - id (hash)
  - title
  - fileUri (persisted permission)
  - chapters: list of Chapter
- Chapter
  - index
  - title
  - text (or extracted on demand)
- PlaybackState
  - bookId
  - chapterIndex
  - textOffset (character offset within chapter text)
  - ttsRate, voiceName
  - lastUpdated

### Core Modules
- `epub/` EPUB import + parse + chapter extraction
- `tts/` Text chunking, queue, play/pause, skip
- `storage/` persistence for books + playback state
- `ui/` Compose screens

---

## Build Milestones (do these in order)

### Milestone 0 — Environment Setup (Mac)
- Install Android Studio
- Create new Android project (Compose)
- Run on emulator or device

**Done when:** you can build + run a “Hello” app.

---

### Milestone 1 — TTS Proof-of-Life (No EPUB yet)
- One screen with:
  - Text field (multi-line)
  - Play / Pause buttons
  - Rate slider (optional)
- Uses Android `TextToSpeech` to speak the entered text

**Done when:** it speaks locally on your test device.

---

### Milestone 2 — Chunking + Skip (Reliable Navigation)
TTS works better when speaking smaller chunks.
- Implement text splitter into sentences (or paragraphs)
- Maintain:
  - `currentChunkIndex`
    - `offsetWithinText`
    - Add:
- Skip back / forward (by N sentences)
  - Next / previous “chapter” placeholder (we’ll simulate chapters for now)

**Done when:** you can jump around predictably.

---

### Milestone 3 — Import an EPUB file
- Add “Import EPUB” button
- Use Storage Access Framework to select `.epub`
- Persist URI permission (so it still works after reboot)

**Done when:** the app remembers the selected EPUB and can reopen it.

---

### Milestone 4 — Parse EPUB spine → chapters
- Open EPUB (zip)
- Read OPF, build spine
- Extract each chapter XHTML/HTML
- Convert HTML → plain text (basic)
- Show chapter list; tap to open chapter and speak from beginning

**Done when:** you can pick a chapter and it speaks.

---

### Milestone 5 — Resume + Bookmarking
- Persist PlaybackState:
  - bookId, chapterIndex, charOffset
- On reopen:
  - show “Resume” and continue where you stopped

**Done when:** you can force-close app, reopen, and resume correctly.

---

### Milestone 6 — Background playback (Boox-friendly)
- Foreground service (notification with play/pause/skip)
- Handle screen-off, app switching, battery optimizations

**Done when:** you can lock screen or switch apps and it keeps reading.

---

## Quality Notes (Boox / e-ink)
- Avoid animated progress bars
- Update progress only on user action or every few seconds
- Provide a “Simple UI mode” toggle (later)

---

## Repo Layout (target)
app/
  src/main/java/.../
    ui/
    tts/
    epub/
    storage/
    util/

---

## Definition of “Finished MVP”
- Import EPUB
- Show chapter list
- Play chapter audio via local TTS
- Skip back/forward
- Remember position and resume

---

## After MVP (nice upgrades)
- Voice selection UI + offline voice check
- Better HTML → text (preserve headings, remove footnotes)
- Speed + pitch settings
- Sleep timer
- “Skip by time” approximation (requires timing model)
- Display book cover art in the UI
