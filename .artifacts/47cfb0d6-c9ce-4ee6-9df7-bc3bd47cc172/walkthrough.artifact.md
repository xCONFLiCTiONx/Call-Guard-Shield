# Walkthrough - Fix Unresolved Reference 'repository'

I have fixed the unresolved reference in `MainViewModel.kt` where `repository` was being used instead of `settingsRepo`.

## Changes Made

### [ui]

#### [MainViewModel.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/ui/MainViewModel.kt)

Modified the `togglePause` function to use the correct repository reference:

```diff
-        viewModelScope.launch {
-            repository.updateSettings(currentSettings.copy(isPaused = newPausedState))
-        }
+        viewModelScope.launch {
+            settingsRepo.updateIsPaused(newPausedState)
+        }
```

## Verification Results

### Automated Tests
- Ran `./gradlew :app:compileDebugKotlin`.
- The error `Unresolved reference 'repository'` in `MainViewModel.kt` is no longer present.

> [!WARNING]
> While the reported issue is fixed, the build is still failing due to several unresolved references in `SettingsScreen.kt`. These seem to be calls to methods that are either missing or have different signatures in `MainViewModel`.

Would you like me to help fix the remaining build errors in `SettingsScreen.kt`?
