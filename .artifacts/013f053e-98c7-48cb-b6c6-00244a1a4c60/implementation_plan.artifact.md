# Implementation Plan - Refinements & AGP Upgrade

Address technical debt, history management, and UI rendering improvements.

## User Review Required

> [!IMPORTANT]
> **AGP Upgrade**: Upgrading AGP to 9.3.1 requires a project sync and potentially small adjustments to the build script.
> **History Capping**: I will set the history limit to **100 entries**. When the 101st call comes in, the oldest entry will be automatically removed.
> **Markdown Library**: I will add the `dev.jeziellago:compose-markdown` library to render Gemini's responses correctly.

## Proposed Changes

### 1. Build Configuration
- [MODIFY] `gradle/libs.versions.toml`: Update `agp` version to `9.3.1`.
- [NEW] Add `dev.jeziellago:compose-markdown:0.5.0` to dependencies.

### 2. Data & Logic (History Management)
- [MODIFY] `CallFilterDao.kt`:
    - Add `deleteOldestCallLogEntries(limit: Int)` or a simple logic to trim the table.
- [MODIFY] `CallFilterService.kt`:
    - After inserting a new call log, trigger a cleanup task to keep only the latest 100 entries.
- [MODIFY] `MainViewModel.kt`:
    - Implement `deleteCallLogEntry(entry: CallLogEntry)` to handle the "Remove from list" action in the History tab.

### 3. UI Layer (Markdown & History Menu)
- [MODIFY] `ChatScreen.kt`:
    - Use the `MarkdownText` component to render Gemini's messages.
- [MODIFY] `HistoryScreen.kt`:
    - Wire the `onRemoveFromList` callback to `viewModel.deleteCallLogEntry(log)`. This will remove the entry from the history log.
- [MODIFY] `NumberActionMenu.kt`:
    - Ensure the "Remove from List" label is clear (e.g., "Delete from History" when in the history context).

## Verification Plan

### Manual Verification
- **AGP**: Run a successful build with version 9.3.1.
- **History Cap**: Simulate 105 calls and verify only the latest 100 remain.
- **History Delete**: Click a history entry, select "Remove," and verify it disappears from the list.
- **Markdown**: Ask Gemini a question that returns markdown (e.g., "Give me a bulleted list of why spammers call") and verify it's formatted correctly.
