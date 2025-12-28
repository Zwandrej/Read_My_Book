<img width="1536" height="1024" alt="Logo and Text - Vertical white blue" src="https://github.com/user-attachments/assets/8f76a359-1132-477b-9d65-67c2491238c9" />
# Read My Book

Version: 0.4.1

Offline EPUB reader with Text-to-Speech for Android (Boox friendly).

## Features
- Import EPUB via Storage Access Framework
- Parse EPUB spine and table of contents (nav.xhtml or toc.ncx)
- Chapter list screen with selectable chapters
- Text-to-Speech reading with sentence preview and controls
- Resume position (chapter + sentence index)

<img width="418" height="884" alt="Screenshot 2025-12-28 at 09 25 13" src="https://github.com/user-attachments/assets/5bfbaee3-43ff-4179-b126-7a162305f38d" /> <img width="418" height="884" alt="Screenshot 2025-12-28 at 09 24 53" src="https://github.com/user-attachments/assets/e7a16cf2-4b42-43ff-962b-7bf9764e1746" />

## Version History
### 0.4.1
- Update app name, icon, and start screen logo

### 0.4
- Add PDF import and Text-to-Speech reading

### 0.3.0
- Display book cover on start and reading screens
- Improve TTS chunking to reduce long pauses

### 0.2.1
- Add Settings page for TTS rate/pitch (persisted)
- Add About page with version + GitHub link
- UI cleanups on start and reading screens

### 0.2.0
- Move TTS playback into a foreground service for background reading
- Add notification permissions for Android 13+

### 0.1.1
- Improve HTML to text cleanup for chapter content

### 0.1.0
- Initial app flow with start screen and chapter list
- EPUB TOC parsing for human-readable chapter titles
- TTS reading with sentence preview and resume position

## Planned Next
- Better TTS voices (voice picker, offline voice checks)
- Notification playback controls (Read/Stop/Next/Prev)
- Persist TTS voice selection
- Resume screen polish (show saved position clearly)
- Multi-book library with last 5 recent books
- Resume cards per book (title, chapter, sentence)
- Bookmark/save position per book
- Chapter search and filter
- Sleep timer (15/30/60 minutes)
- Skip by paragraph or sentence count
- "Now Reading" header with chapter title
- Export/import reading state (backup)

## Extensibility
- Swapping TTS backends (local vs API)
- Future formats (PDF, Markdown, HTML)
- Text-to-audio quality improvements
- Smarter chunking (sentence/paragraph aware, not raw length)
- Optional normalization (footnotes, references, page numbers)
- Voice presets per book or genre
- Optional semantic indexing of books (future: concept navigation)
- "Explain this passage" hooks (offline LLM later)
- Voice change for quotations

## Build
```bash
./gradlew assembleDebug
```

## Test
```bash
./gradlew test
```

## Run
- Open the project in Android Studio
- Click Run ▶ on a device or emulator
