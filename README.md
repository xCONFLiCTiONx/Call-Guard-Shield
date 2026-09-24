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
* **JDK Version**: Java 17 or Java 21 recommended (Java 11 minimum compatible)

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
├── gradle/                       # Gradle wrapper files
├── gradlew / gradlew.bat         # Gradle build scripts
└── build.gradle.kts              # Root build script
```

---

## 🔑 Configuration & API Key Setup

To use the full AI threat intelligence and Google Drive sync features, you need to configure your API keys and Google Cloud credentials.

### 1. 🤖 Obtain Google Gemini API Key
1. Go to [Google AI Studio](https://aistudio.google.com/).
2. Sign in with your Google account.
3. Click **Get API key** (or **Create API key in new project**).
4. Copy your newly generated API key.
5. In the **Call Guard Shield** app:
   - Go to **Settings** > **Gemini AI Intelligence**.
   - Paste your key into the **Gemini API Key** field.
   - Tap **Test & Save Key** to verify connectivity.
   - Select your preferred model (e.g., `gemini-1.5-flash` or `gemini-3.1-flash-lite`).

### 2. 🔑 Gradle Signing Report (SHA-1 / SHA-256 Fingerprints)
To set up Google Drive Cloud Sync or Google Sign-In with Google Play Services, you need your app's SHA-1 and SHA-256 signing fingerprints.

Run the Gradle `signingReport` task from your terminal:

* **Linux / macOS**:
  ```bash
  ./gradlew signingReport
  ```
* **Windows (PowerShell / Command Prompt)**:
  ```cmd
  gradlew.bat signingReport
  ```

Look for the output under **Variant: debug**:
```text
Variant: debug
Config: debug
Store: C:\Users\<Username>\.android\debug.keystore
Alias: AndroidDebugKey
MD5: XX:XX:XX:...
SHA1: 12:34:56:78:90:AB:CD:EF:...
SHA-256: A1:B2:C3:D4:E5:F6:...
```

### 3. ☁️ Google Cloud Console Setup (for Google Drive Sync)
If you wish to enable Google Drive Backup & Sync:
1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project or select an existing project.
3. Enable the **Google Drive API**:
   - Go to **APIs & Services > Library**.
   - Search for **Google Drive API** and click **Enable**.
4. Configure OAuth 2.0 Credentials:
   - Go to **APIs & Services > Credentials**.
   - Click **Create Credentials > OAuth client ID**.
   - Application type: **Android**.
   - Package name: `com.xconflictionx.callguardshield`
   - SHA-1 certificate fingerprint: Paste the SHA-1 from `./gradlew signingReport`.
5. *(Optional)* If using Web Server OAuth / Google ID Token sign-in:
   - Create an OAuth Client ID of type **Web application**.
   - Copy the Client ID and insert it into `DriveSyncManager.kt` (`setServerClientId("YOUR_SERVER_CLIENT_ID")`).

---

## 🚀 Complete Build & Deployment Guide

### Option A: Building from Android Studio IDE
1. Open **Android Studio** (Ladybug 2024.2.1 or newer).
2. Select **File > Open...** and navigate to the `Call Guard Shield` directory.
3. Wait for Gradle Sync to complete. (If prompted, accept SDK component installations).
4. Connect an Android device (Android 8.0+ / API 26+) with USB Debugging enabled, or launch an Android Virtual Device (AVD).
5. Select the `:app` configuration in the top toolbar.
6. Click **Run** (`Shift + F10` or the green play button).

### Option B: Building from Command Line (CLI)

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/your-username/call-guard-shield.git
   cd call-guard-shield
   ```

2. **Generate Signing Report** (to view SHA-1/SHA-256 fingerprints):
   ```bash
   # Linux/macOS
   ./gradlew signingReport

   # Windows
   gradlew.bat signingReport
   ```

3. **Build Debug APK**:
   ```bash
   # Linux/macOS
   ./gradlew assembleDebug

   # Windows
   gradlew.bat assembleDebug
   ```
   The generated APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

4. **Install onto Connected Device/Emulator**:
   ```bash
   # Linux/macOS
   ./gradlew installDebug

   # Windows
   gradlew.bat installDebug
   ```

5. **Run Unit Tests**:
   ```bash
   # Linux/macOS
   ./gradlew test

   # Windows
   gradlew.bat test
   ```

---

## 📱 Post-Installation & App Setup

1. **Launch Call Guard Shield** on your device.
2. **Set Default Call Screening App**: On first launch, Android will prompt you to set Call Guard Shield as your default Call Screening service. Tap **Set as Default**.
3. **Grant Required Permissions**:
   - **Call Log**: Required to identify incoming numbers and populate call logs.
   - **Contacts**: Required to automatically trust verified contacts.
   - **Phone State**: Required for real-time call screening triggers.
4. **Configure Gemini AI**:
   - Open **Settings** > **Gemini AI Intelligence**.
   - Input your **Google Gemini API Key** and tap **Test & Save Key**.
5. **Enable Firewall**:
   - Ensure the **Call Firewall** switch on the main screen is toggled **ON**.

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
