# ClearChoice

**ClearChoice** is a high-performance, privacy-first mobile app for recording conversations, interviews, and lectures — with optional real-time transcription, redaction, and speaker separation. Built to run fully offline on modern smartphones like the Samsung S23+ and iPhone 14+, ClearChoice prioritizes speed, control, and security.

---

## ⚙️ Core Features

| Feature                  | Description                                                                 |
|--------------------------|-----------------------------------------------------------------------------|
| 🎙️ Offline Recording      | High-quality audio recording with local storage per session                |
| 📝 Transcription (Toggle) | Runs Whisper (`tiny.en.q8`) locally using optimized C++/Kotlin bindings     |
| 🗣️ Speaker Separation      | Optional speaker diarization using lightweight embedding comparisons       |
| 🕵️ Redaction (Toggle)     | Removes common personally identifiable info (PII) using regex-based filters |
| 📁 Session History         | Saved by time/date, browsable within the app                               |
| 📄 Export Formats (Toggle) | Export as `.txt`, `.json`, or `.pdf`, with or without redactions            |
| 🔐 Biometric Lock (Toggle) | Require fingerprint or FaceID to access app or session list                 |
| ⚠️ Disclaimer              | Redaction may not catch all PII due to device-based processing limitations  |

---

## 🔒 Privacy & Security

ClearChoice runs entirely offline and does **not** require an internet connection. All audio, metadata, and transcripts are stored locally on the user's device. Nothing is uploaded, shared, or cloud-synced unless the user explicitly exports it.

You control:
- Whether transcription is enabled
- Whether redaction is applied
- Whether export includes PII
- Whether biometric unlock is required

---

## 📂 Session File Structure

Each recording is saved with its own timestamped folder:
/clearchoice/
├── 2025-06-04_14-32/
│   ├── audio.wav
│   ├── transcript.txt
│   ├── redacted.txt
│   ├── speakers.json
│   ├── metadata.json
---

## 🛠️ Technical Overview

- **Platform:** Native Android (Kotlin), iOS planned (Swift)
- **Transcription Engine:** `whisper.cpp` (quantized `tiny.en.q8`)
- **Diarization:** Embedded speaker embedding with clustering
- **Redaction:** Regex and wordlist-based offline redactor
- **UI:** Native Material Design (basic now, polish later)
- **Storage:** Local file system, encrypted where possible

---

## ⚠️ Redaction Disclaimer

Redaction is performed locally using keyword and pattern detection. Due to mobile processing limits, redaction may not identify all sensitive information. Users are encouraged to review content manually before sharing or exporting.

---

## 📌 Internal Roadmap

- [ ] Refine waveform UI and live transcription overlay
- [ ] Add export presets (e.g., “Clean for Email”)
- [ ] Integrate local summary/auto-tagging with LLM (optional)
- [ ] Offer full session encryption with user PIN

---

*Built for clarity. Owned with purpose.*

This project is not open-source. All rights reserved.