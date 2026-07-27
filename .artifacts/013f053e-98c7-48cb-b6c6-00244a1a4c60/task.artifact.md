# Task Tracker - Refinements & AGP Upgrade

- [ ] **Phase 1: Build & Dependencies**
    - [ ] Upgrade AGP to `9.3.1` in `libs.versions.toml`.
    - [ ] Add `dev.jeziellago:compose-markdown` dependency.
    - [ ] Perform Gradle Sync.
- [ ] **Phase 2: History Management**
    - [ ] Add `deleteCallLogEntry` and `trimCallLog` to `CallFilterDao`.
    - [ ] Update `CallFilterService` to trim history to 100 entries on new insertions.
    - [ ] Implement `deleteCallLogEntry` in `MainViewModel`.
- [ ] **Phase 3: UI Enhancements**
    - [ ] Update `HistoryScreen` to wire up the "Remove from list" action.
    - [ ] Update `ChatScreen` to use `MarkdownText` for Gemini responses.
    - [ ] Ensure `NumberActionMenu` reflects "Delete from History" when appropriate.
- [ ] **Phase 4: Verification**
    - [ ] Verify build completion.
    - [ ] Verify history deletion and capping logic.
    - [ ] Verify markdown rendering in chat.
