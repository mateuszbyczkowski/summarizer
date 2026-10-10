# Redesign plan

Implements the approved redesign (mockups: https://claude.ai/artifact/EDpVyy8fYJfXzX3btYUnvg).
Summary-first, Material 3, dynamic color, edge-to-edge, Polish UI with English fallback.

## Ground rules for every stage

- **Devices:** use only the emulator `emulator-5554`. Always `export ANDROID_SERIAL=emulator-5554`
  before `adb` or any `connected*` Gradle task. Never touch a physical phone.
- **Validation without models:** the emulator cannot download models (corporate network blocks
  Hugging Face). Debug builds offer a **Demo** model (`debug-demo`, no download) that streams a
  canned summary of the debug sample chat, so generation, progress, cancel and notifications can
  be exercised end to end.
- **Screenshots:** `FLAG_SECURE` is skipped only on emulator debug builds (`Build.HARDWARE` is
  `ranchu`/`goldfish`), so `adb exec-out screencap -p` works there. Never elsewhere.
- **Strings:** every user-visible string in resources. Each feature adds its own file,
  `res/values/strings_<feature>.xml` (avoids merge conflicts). Polish translations come in
  stage 3 (`values-pl`), English stays the default.
- **Build:** `./gradlew assembleDebug testDebugUnitTest` must pass with no new `w:` warnings.
- **Privacy:** notifications never show summary content on the lock screen (public version
  without content). Logs never contain message or summary text.
- **Architecture:** follow CLAUDE.md (MVVM, use cases talk to `AIEngine`, Room migrations with
  tests, no destructive migration).

## Stage 1: foundations

### A. Data and engine (no UI changes beyond what is needed to compile)

1. **Structured summaries**
   - `KeyDate(label, date: String? /* ISO yyyy-MM-dd */, time: String? /* HH:mm */, detail: String?)`
   - `ActionItem` gains `due: String?` (ISO date) and `done: Boolean = false` (old JSON stays readable).
   - `Summary` gains `keyDates: List<KeyDate>`, `engine: String?` (e.g. "Gemma 4 E2B · GPU", "OpenAI gpt-4o-mini"), `rating: Int?` (+1 / -1).
   - Room 9 -> 10: `key_dates TEXT NOT NULL DEFAULT '[]'`, `engine TEXT`, `rating INTEGER`; migration test.
   - Prompt: add a `DATES:` section ("- 17.10 7:30 | Wycieczka, autobus"), due dates in actions ("(do 15.10)").
     Keep it short; it must still work for Gemma 4 E2B.
   - Parser: `DATES` lines and due dates in actions; Polish and numeric date formats
     ("17.10", "17.10.2026", "17 października", "pt 17.10", "w środę" stays unresolved), year
     resolved relative to the summary's last message (nearest upcoming date). Unit tests.
2. **Task state and feedback:** `SummaryRepository.setActionItemDone(summaryId, index, done)`, `setRating(summaryId, rating)`.
3. **Thread overview for Home:** `ThreadOverview(thread, latestSummary: Summary?, newSinceSummary: Int, openTasks: Int)`
   as a Room-backed `Flow` (new messages = timestamp > latest summary's `endTimestamp`; all messages when no summary).
4. **Agenda for Zadania / "Ten tydzień" / widget:** `AgendaItem(threadId, threadName, summaryId, kind: DATE|TASK, label, date, time, index, done)`
   from the latest summary of each followed thread; `Flow<List<AgendaItem>>` sorted by date, undated open tasks last.
5. **Generation progress, streaming and cancel**
   - `AIEngine.generate(..., onPartial: ((String) -> Unit)? = null)`; LiteRT via `sendMessageAsync`, llama.cpp via `onDelta`,
     OpenAI may report only the final text.
   - `SummaryGenerationWorker` publishes `setProgress`: stage (`PREPARING`, `LOADING_MODEL`, `READING`, `WRITING`),
     `startedAt`, partial text (overview only, max ~2 KB).
   - Coordinator: `State.Running(stage, startedAt, partialText)`, `cancel(threadId)`; ongoing notification has a Cancel action.
   - Ready notification: "Podsumowanie gotowe · N zadań", first task as text (private on lock screen), actions Pokaż / Kopiuj.
6. **Demo engine (debug only):** catalog entry `debug-demo` "Demo (debug)" only in debug builds, marked downloaded without
   a file download; `DemoEngine` streams a canned Polish summary (with DATES, ACTIONS with due dates) over ~10 s and honours cancel.
7. **Summary as text:** `SummaryFormatter.toPlainText(summary)` for copy/share (Polish-friendly, dates formatted).

### B. App lock

- Default: the phone's own lock via `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`; keep the 24 h validity in `AppLockManager`.
- Phones without a screen lock (`KeyguardManager.isDeviceSecure == false`): keep the app PIN, with a new keypad UI
  (6 dots, on-screen digits, fingerprint key when available, shake + haptic on error, lockout countdown in place).
- Onboarding asks for a PIN only when the phone has no screen lock. Setting "Blokada aplikacji" on/off (default on).
- Existing users with a PIN: switch to the system prompt when the phone is secure.

## Stage 2: main screens

- **C. Shell + Home + Zadania:** 3-item `NavigationBar` (Czaty / Zadania / Ustawienia); plain surface top bars that change on
  scroll; Home with M3 SearchBar entry, "Ten tydzień" card (next agenda items), "Obserwowane" section and collapsed
  "Pozostałe czaty"; thread rows show teaser (summary overview, else last message), freshness, open-task chip, new-since-summary
  badge, inline progress while generating, "Podsumuj" when no summary; follow/unfollow via long-press menu + TalkBack action.
  Zadania tab: agenda grouped by date (Dziś / Jutro / Ten tydzień / Później / Bez terminu), checkable tasks, add-to-calendar.
- **D. Thread screen:** merges ThreadDetail + SummaryDisplay. Top bar (name, "48 wiadomości · 10–13.10", share, overflow:
  full re-summary, mode, follow). Tabs Podsumowanie / Wiadomości (badge). Summary tab: history chip ("Dziś, 17:02 ▾"),
  "W skrócie" card with highlighted dates, "Ważne daty" tiles (add to calendar via `Intent.ACTION_INSERT`, no permission;
  near deadline tinted), "Do zrobienia" checkboxes (persisted, strike-through, undo snackbar), "Ogłoszenia", topic chips,
  footer (messages range, engine, thumbs up/down), copy/share. Messages tab: bubbles, date separators, "Nowe od podsumowania"
  divider. Extended FAB "Aktualizuj · N nowe" (incremental), collapses on scroll. Generating state: progress card with stages,
  elapsed vs "zwykle 30–60 s", streaming overview text, "Możesz wyjść z aplikacji", Cancel; previous summary dimmed; sections
  fade in when done. Raw AI output only in debug builds.
- **E. Settings, model manager, search:** Settings as grouped list (Podsumowania / Powiadomienia / Prywatność i bezpieczeństwo),
  time-picker dialog, status banner when something is wrong, About as one row, reset row in error color with consequences in
  the dialog. Model manager: segmented Na urządzeniu / OpenAI, active-model hero (ready, time per summary, GPU/CPU, size,
  language), "Wypróbuj na przykładzie", other models with plain names, inline progress/pause, RAM-disabled with reason,
  storage bar, storage location, Wi-Fi only. Search: M3 SearchBar with recent searches, filter chips
  (Podsumowania / Wiadomości / Zadania / chat), highlighted matches, results open in context.

## Stage 3: onboarding, language, widget

- **F. Onboarding:** 3 steps (value-first welcome, notification access, engine choice with on-device preselected); model
  picked by RAM and downloaded in the background with a progress card on Home; PIN only when the phone has no screen lock.
- **G. Polish:** `values-pl` for every string with correct plurals; dates and times in the phone's locale everywhere.
- **H. Widget + notifications:** Glance home-screen widget with upcoming agenda items (privacy: hidden while app lock is
  active and the setting says so); notification polish from stage 1.

## Validation per stage

Build, unit tests, `connectedDebugAndroidTest` on `emulator-5554`, launch on the emulator, walk the changed flows with the
Demo model, screenshots of every changed screen in light and dark, crash buffer empty, then code review and fixes.
