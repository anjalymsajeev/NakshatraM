# Changelog

## [Unreleased]

### Sprint 4: Google Calendar
- **Google account** screen: connect with Google Identity Services (OAuth 2.0), with only the `calendar.events` and `calendar.calendarlist.readonly` scopes; choose the calendar; disconnect (revokes access). Tokens are never stored by the app.
- **Event preview** (from Find dates → "Add to Google Calendar"): every date with a checkbox, Select all / Deselect all, and an "Already in Google Calendar" mark. **Create all** (automatic) or **Create selected** (manual), always after a confirmation dialog.
- Events are **all-day** on the observance day, titled "Anvidh - Atham Nakshatra", with the brief's description (Malayalam date, month, Kollavarsham, star start and end, location, reminder), the member's colour and the chosen reminder.
- **No duplicates**: deterministic Google event ids plus a unique local mapping. Re-syncing skips unchanged events and updates changed ones (name, colour, reminder); an event deleted in Google is restored.
- Background sync with WorkManager: waits for a connection, retries offline, quota and server errors with exponential backoff, refreshes an expired token once, and asks to reconnect when access was revoked.
- **Sync results** (live) and **Sync history** (Home and Settings): person, nakshatra, range, totals, successes, failures, last sync, status; Retry (with reconnect) and Remove events.
- **Reminder** setting: none, 10 min, 30 min, 1 hour, 1 day (default) or custom (up to 4 weeks).
- Room schema v3 (`nakshatra_occurrence`, `calendar_sync`, `calendar_event`, `reminder_configuration`) with a tested auto-migration.
- Tests: 71 unit tests (event format and ids, REST client against MockWebServer, sync engine, preview ViewModel), plus emulator tests for the DAO, migration 2→3 and an end-to-end sync flow with a test-only fake Google module.

### Sprint 3: date range, Nakshatra results and calendars
- **Find Nakshatra dates** (from the Family tab or a member's menu): choose members and a start and end date with the Material date picker (limited to the library's 1900–2100 range). Every occurrence is listed per member, recalculated automatically when the members or dates change: observance date, Malayalam date, month and Kollavarsham year, star start and end times in the member's time zone, and a "spans 2 days" badge. Ranges are validated (end after start, at most 5 years).
- Occurrences come from the library's `nakshatraOccurrences`, which walks the real nakshatra spans; no 27-day cycle is assumed.
- **Calendar tab**: Gregorian and Malayalam month views with a toggle, previous/next and Today. Each day shows the other calendar's date, the nakshatra at sunrise, and coloured dots for family birth-star days. Tapping a day opens its details. A sync indicator is ready for Sprint 4.
- Fix: deleting the last family member no longer leaves a stale card in the hidden list.
- Tests: 52 unit tests (range validation, results and calendar days against the real library, both ViewModels), Espresso tests for Find dates and the calendar.

### Sprint 2: family members
- Family list (design "Pirannal Scheduler"): gold-trimmed cards with colour, name, star in Malayalam and English, and next Pirannal (Gregorian and Malayalam date); empty, loading and error states; delete with confirmation and undo.
- Add and edit screen: name, birth nakshatra (searchable picker with all 27 stars), optional birth date, time and place, calculation location, and an event colour matching Google Calendar's 11 colours.
- **Calculate Malayalam star** from birth details using the library. Offers both stars within 60 minutes of a nakshatra change, and every star of the day when the birth time is unknown.
- Online place search with the platform `Geocoder` (no API key). Time zone from the country (ICU), with a chooser for multi-zone countries; manual entry when offline.
- Home: "Upcoming birth-star days" for the next five family Pirannals.
- Room schema v2 (birth place coordinates and zone) with a tested auto-migration from v1.
- Library dependency raised to `kollavarsham-panchangam:0.2.0-alpha01` (`nakshatraOccurrences`).
- Tests: 36 unit tests, plus emulator tests (DAO, migration 1→2, ICU zones, Espresso add/edit/delete and validation).


### Sprint 1: foundation
- Gradle (Kotlin DSL, version catalog), Hilt, Room (schema v1 exported), DataStore, Navigation, Material 3 theme.
- "Heritage & Horizon" design system: light and derived dark colours, typography, gold-trimmed cards, buttons, bundled fonts.
- Library integration through `MalayalamDateProvider` and `NakshatraEngine` adapters (`kollavarsham-panchangam` 0.1.0-alpha01).
- Home dashboard: today in Gregorian and Malayalam, Malayalam month and Kollavarsham year, nakshatra at sunrise, family count.
- Settings: language (English/മലയാളം), theme, birth-star day convention. Family and Calendar placeholders.
- English and Malayalam strings; splash screen; adaptive launcher icon.
- Unit tests (adapters against the real library, use case, ViewModel, settings repository), an Espresso smoke test, and GitHub Actions (build, lint, ktlint, unit tests, emulator tests).
