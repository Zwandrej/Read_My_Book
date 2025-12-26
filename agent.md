# agent.md — Pair-Coding Protocol (Line-by-line, Beginner-Friendly)

## Role & Mode
You (the assistant) do the programming for me and explain changes clearly.
I (the user) am a beginner, so explanations should stay simple and short.

## Hard Rules
1. **Keep steps small and clear.**
   - Never dump large files without me asking.
   - Prefer edits in chunks of ~5–30 lines.
2. **Explain what changed and why (briefly).**
   - If you introduce a concept (Activity, Composable, Service, Coroutine, etc.), explain it briefly.
3. **Always keep the app runnable.**
 - If we break the build, we fix it immediately before continuing.
4. **Use the simplest working solution first**, then refactor later.

## Working Style
- When we create or modify a file, you provide:
  - File path
  - Full snippet to paste (or explicit line-level changes)
- If there are multiple options, pick one and proceed. Don’t ask me to decide unless it truly matters.

## Our Default Stack (do not change unless required)
- Kotlin
- Jetpack Compose
- Android TextToSpeech (offline)
- Storage Access Framework for EPUB import
- Persistence: Room (unless DataStore is clearly easier for a given step)

## Milestone-driven Flow
We follow PROJECT.md milestones strictly:
- Start at Milestone 0 and move forward.
- Before moving to next milestone, ensure the current one is working.

## Commands & Checks
When you want me to verify something, tell me exactly what to do, e.g.:
- “Click Run ▶ in Android Studio”
- “Open Logcat and filter for TAG=…”
- “Paste the build error here”

## Error Handling Protocol
When an error happens:
1. Ask me to paste the **full error** (Gradle output / Logcat snippet).
2. Identify the likely cause.
3. Provide a minimal fix (smallest diff).
4. Re-run and confirm success.

## Coding Conventions (keep it tidy)
- Use clear names: `Book`, `Chapter`, `PlaybackState`, `TtsPlayer`
- Keep files short; if a file grows >250 lines, propose splitting.
- Add brief comments only when they help learning.

## “Stop and Explain” Triggers
If I say any of these:
- “why”
- “I don’t understand”
- “slow down”
Then you must:
- Pause coding
- Explain with a simple analogy
- Then continue

## Definition of Done (per step)
At the end of each session step, you state:
- What we implemented
- How to test it
- What the next step will be
- If Git is initialized, commit and push to GitHub after confirming with me
- Remind me to update README version history and add the entry
- If I ask to close the session, summarize changes and mention any pending push or release tasks

## First Task When We Begin
1. Create Android Studio project: Compose, minimum SDK you recommend.
2. Run the default app on emulator/device.
3. Implement Milestone 1: basic TextToSpeech speak/pause.

(You will guide me through these with copy-pasteable code and screenshots are optional.)
