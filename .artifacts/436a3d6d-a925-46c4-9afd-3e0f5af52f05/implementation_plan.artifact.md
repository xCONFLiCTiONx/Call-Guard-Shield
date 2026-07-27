# Rename Project to Call Guard Shield

Renaming the project from "Call Filter" to "Call Guard Shield", including updates to Gradle files, strings, themes, and classes.

## Proposed Changes

### [Root Project]

#### [MODIFY] [settings.gradle.kts](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/settings.gradle.kts)
- Update `rootProject.name` to `"Call Guard Shield"`.

### [App Resources]

#### [MODIFY] [strings.xml](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/res/values/strings.xml)
- Update `app_name` to `"Call Guard Shield"`.

#### [MODIFY] [themes.xml](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/res/values/themes.xml)
- Rename `Theme.CallFilter` to `Theme.CallGuardShield`.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/AndroidManifest.xml)
- Update all occurrences of `Theme.CallFilter` to `Theme.CallGuardShield`.
- Update `.service.CallFilterService` to `.service.CallGuardShieldService`.

### [Source Code]

#### [MODIFY] [Theme.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/ui/theme/Theme.kt)
- Rename `CallFilterTheme` to `CallGuardShieldTheme`.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/MainActivity.kt)
- Update `CallFilterTheme` usage to `CallGuardShieldTheme`.

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/data/AppDatabase.kt)
- Rename `callFilterDao()` method to `callGuardShieldDao()`.
- Update database name from `"call_filter_database"` to `"call_guard_shield_database"` (optional, but consistent).

#### [MODIFY] [CallFilterDao.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/data/dao/CallFilterDao.kt) -> [NEW] `CallGuardShieldDao.kt`
- Rename class `CallFilterDao` to `CallGuardShieldDao`.

#### [MODIFY] [CallFilterService.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/service/CallFilterService.kt) -> [NEW] `CallGuardShieldService.kt`
- Rename class `CallFilterService` to `CallGuardShieldService`.
- Update log tag `"CallFilter"` to `"CallGuardShield"`.

#### [MODIFY] [CallFilterEngine.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/logic/CallFilterEngine.kt) -> [NEW] `CallGuardShieldEngine.kt`
- Rename class `CallFilterEngine` to `CallGuardShieldEngine`.

#### [MODIFY] [GeminiManager.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/logic/GeminiManager.kt)
- Update system prompt text from "call filter assistant" to "call guard shield assistant".

#### [MODIFY] [ListManagementScreen.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/ui/screen/ListManagementScreen.kt)
- Update filenames `callfilter_blacklist.txt` to `callguardshield_blacklist.txt` and `callfilter_whitelist.txt` to `callguardshield_whitelist.txt`.

#### [MODIFY] Other Files
- Update all references to `CallFilterDao`, `CallFilterService`, `CallFilterEngine`, and `callFilterDao()`.

## Verification Plan

### Automated Tests
- Build the project to ensure all references are updated correctly.
- Run existing unit tests (if any) to verify logic remains intact.

### Manual Verification
- Deploy to a device/emulator.
- Verify the app name "Call Guard Shield" appears in the launcher and UI.
- Verify the call screening service still works (check logs for "CallGuardShield").
