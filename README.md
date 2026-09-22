# 🛡️ Call Guard Shield

**Call Guard Shield** is an advanced, privacy-focused Android application designed for real-time call filtering, spam detection, and AI-powered phone number threat intelligence. Built natively with **Jetpack Compose**, **Room**, and **Google Gemini AI**, Call Guard Shield provides powerful protection against unwanted calls, scammers, and telemarketers while keeping user data secure and private.

---

## 🌟 Key Features

### 🚫 Advanced Call Screening & Filtering
* **Real-Time Telecom Service Integration**: Built using Android's native `CallScreeningService` (`BIND_SCREENING_SERVICE`) to evaluate and block incoming calls before your phone rings.
* **Firewall Toggle**: Instantly activate or pause call protection with a single tap.
* **Smart Contacts Protection**: Automatically allow verified phonebook contacts while intercepting suspicious callers.
* **Custom Blacklist & Whitelist**: Define custom blocking rules or whitelist trusted numbers with absolute bypass priority.
* **Prefix & Pattern Rules**: Block entire area codes or phone number prefixes (e.g., wildcards/country codes).
* **International & Unknown Caller Blocking**: Option to block unknown numbers or non-domestic calls automatically.

### 🤖 Gemini AI Threat Intelligence
* **AI Phone Lookup**: Leverages Google Gemini AI models (e.g., `gemini-1.5-flash`, `gemini-3.1-flash-lite`) to analyze phone numbers, summarize caller identities, and determine threat categories (telemarketer, debt collector, scammer, business, etc.).
* **Real-Time AI Blocking**: Automatically block high-risk numbers based on AI risk assessments and user-configurable accuracy thresholds.
* **Bulk Number Identification**: Background workers dynamically analyze past call logs and populate caller insights.

### 🌐 Global Spam Protection & Synchronization
* **Community Threat Database**: Offline global spam database with pattern matching capabilities.
* **Automated Background Sync**: Scheduled updates using Android **WorkManager** (`SpamSyncWorker`) ensure threat signatures remain up-to-date.

### 🔐 Encrypted Cloud Backup & Sync
* **Google Drive Integration**: Sync rules, blacklists, whitelists, and logs across devices using private Google Drive application storage (`appDataFolder`).
* **Client-Side Encryption**: Employs `AndroidX Security Crypto` for encrypted local state and token storage.

### 📊 History Analytics & Management
* **Comprehensive Call Logs**: Detailed breakdown of blocked and allowed calls with stats on neutralized threats.
* **Interactive Number Action Menus**: Search, filter, batch select, block, whitelist, or invoke Gemini AI lookups directly from history or list management screens.

### 💻 Developer Console & Diagnostics
* **Live In-App Console**: Monitor real-time engine decisions, screening triggers, background jobs, and API logs for debugging and transparency.

---

## 🏗️ Architecture & Tech Stack

Call Guard Shield follows **Modern Android Development (MAD)** best practices and clean architectural patterns (MVVM + Repository).

| Layer | Technology |
| :--- | :--- |
| **Language** | 100% [Kotlin](https://kotlinlang.org/) |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 Design |
| **State Management** | ViewModel, Kotlin Coroutines, StateFlow, SharedFlow |
| **Local Database** | [Room Database](https://developer.android.com/training/data-storage/room) with KSP compiler |
| **Preferences** | [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore) |
| **Background Scheduling** | [Android WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) |
| **Networking & APIs** | Retrofit 2, OkHttp 3, Jsoup, Google Generative AI SDK |
| **Cloud & Identity** | Google Credential Manager, Google Play Services Auth, Google Drive API v3 |
| **Security** | AndroidX Security Crypto (`EncryptedSharedPreferences`, `MasterKeys`) |

---

## 📋 System Requirements & Permissions

### Android Requirements
* **Minimum SDK**: Android 8.0 (API level 26)
* **Target SDK**: Android 15 (API level 35)
* **Compile SDK**: API level 37

### Key Android Permissions
* `android.permission.BIND_SCREENING_SERVICE` — Intercept and screen incoming calls.
* `android.permission.READ_CALL_LOG` & `READ_PHONE_STATE` — Process incoming caller IDs and history logs.
* `android.permission.READ_CONTACTS` — Match callers against local contacts.
* `android.permission.INTERNET` — Connect to Gemini AI intelligence and Google Drive sync services.

---

## 🛠️ Project Structure

```
Call Guard Shield/
├── app/
│   ├── src/main/
│   │   ├── java/com/xconflictionx/callguardshield/
│   │   │   ├── data/             # Room Database, DAOs, Entities & Repositories
│   │   │   ├── logic/            # Call Engine, Gemini Service, Encryption, Phone Utilities
│   │   │   ├── service/          # CallScreeningService & InCallService implementations
│   │   │   ├── ui/               # Jetpack Compose UI (Screens, Components, Theme, ViewModel)
│   │   │   └── worker/           # WorkManager Background Workers (SpamSync, CloudBackup, BulkIdentify)
│   │   ├── res/                  # Android Resources & Graphics
│   │   └── assets/               # Local Privacy Policy & Assets
│   └── build.gradle.kts          # Module-level Gradle configuration
└── build.gradle.kts              # Root build script
```

---

## 🚀 Getting Started

### Prerequisites
1. **Android Studio**: Install Android Studio Ladybug or newer.
2. **JDK Version**: Java 11 or higher.
3. **Gemini API Key** *(Optional)*: Obtain an API key from [Google AI Studio](https://aistudio.google.com/) for AI lookup capabilities.

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/call-guard-shield.git
   cd call-guard-shield
   ```
2. Open the project in **Android Studio**.
3. Sync Gradle dependencies.
4. Run the application on an Android device or emulator running API level 26+.
5. Grant required permissions (Call Screening Role & Call Log access) on first launch.

---

## 🔒 Privacy & Security

Call Guard Shield is designed with privacy as a foundational principle:
* **Local Processing**: Contact details and personal logs are evaluated on-device and are never transmitted to external servers.
* **Minimal Scope**: Gemini AI phone lookups transmit **only** the queried phone number—never personal contact metadata.
* **Encrypted Storage**: Private credentials and backup states are secured using `AndroidX Security Crypto`.

For full details, review our in-app [Privacy Policy](app/src/main/assets/privacy.md).

---

## 📧 Contact & Support

For questions, issues, or suggestions, please contact:
* **Email**: info@xconflictionx.cc
