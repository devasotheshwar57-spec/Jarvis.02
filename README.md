# Jarvis — Futuristic Personal AI Assistant (Android)

An Iron Man-inspired personal AI assistant built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Firebase AI Logic (Gemini 3.8 Flash)**, and **Cloud Firestore**.

## Key Features
- **Futuristic HUD Interface**: Deep obsidian black backdrop with neon cyan accents, custom typography (`Rajdhani`, `Space Grotesk`, `Inter`, `JetBrains Mono`), and an animated `ArcReactorVisualizer` reacting in real time to voice input, AI processing, and speech output.
- **AI Text & Vision Conversations**: Multi-turn streaming chat powered by `Firebase.ai` (`gemini-3.8-flash`), plus camera photo capture and zero-permission Android Photo Picker uploads for multimodal image analysis.
- **Voice Input & Speech Synthesis**: Native Android `SpeechRecognizer` with live RMS audio reactivity and partial transcripts, paired with Android `TextToSpeech` (adjustable rate, pitch, and auto-speak toggle).
- **Multilingual Support**: Seamless switching between **English (`en-US`)**, **Hindi (`hi-IN`)**, and **Telugu (`te-IN`)** across AI responses, speech recognition, and TTS.
- **Cloud-Synced History, Notes & Reminders**: Authenticated via Google Sign-In (`CredentialManager`) and persisted securely in Cloud Firestore with user-isolated security rules and local reminder notifications.
- **Built-in Toolkit**:
  - **Scientific & Standard Calculator** with live expression evaluation and computation tape.
  - **Notes & Reminders** with pinning, completion tracking, and scheduled local notifications.
  - **Live Web Search** querying DuckDuckGo Instant Answer & Wikipedia APIs with one-tap AI summarization and browser handoff.
  - **Live Telemetry Clock** displaying current date, time, and day on the HUD.

## Setup & Deployment
1. **Firebase Provisioning**: Configured via `app/google-services.json`, `app/src/main/res/values/firebase_applet_config.xml`, and `firestore.rules` (enforcing per-user authentication and schema validation).
2. **App Check**: Initialized in `MainActivity.onCreate()` via `configureAppCheck(this, intent)` before Compose UI or ViewModels.
3. **Permissions**:
   - `RECORD_AUDIO`: Requested with rationale before starting voice commands.
   - `CAMERA`: Requested with rationale before capturing photos (gallery uploads use the zero-permission Android Photo Picker).
   - `POST_NOTIFICATIONS`: Requested on Android 13+ before scheduling local reminder notifications.
4. **Build & Test**:
   - Compile APK: `gradle :app:assembleDebug`
   - Run JVM & Robolectric unit tests: `gradle :app:testDebugUnitTest`
   - Run Firestore security rules tests: `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`
