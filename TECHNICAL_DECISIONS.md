# Technical decisions

## TD-1: Single `:app` module with strict packages

Clean layering by package (core, domain, data, integration, feature) in one module. This avoids multi-module overhead at the start. Features can be split into modules later, because dependencies already point inward.

## TD-2: The calendar library sits behind domain interfaces

`MalayalamDateProvider` and `NakshatraEngine` are app-owned interfaces, with adapters in `integration/`. Library types never reach the domain or the UI. That keeps the app independent of library internals, makes fakes trivial in tests, and puts error mapping in one place.

## TD-3: The library's panchangam module is the astronomical engine

It is validated against independent ephemerides: nakshatra boundaries agree to about 5 s, and sankranti times to about 1 s against a JPL-based list. It is separate from date conversion, as the brief requires. No second engine is introduced.

## TD-4: Results and state

`AppResult` (`Success` or `Failure(AppError)`) is used for expected failures, and exceptions are mapped at the boundaries, never swallowed. UI state is an immutable sealed `UiState`, exposed through `StateFlow` with `stateIn(WhileSubscribed(5 s))`.

## TD-5: Language and theme

- **Language:** AppCompat per-app locales, with `generateLocaleConfig` and `autoStoreLocales`. They integrate with Android 13+ system settings and need no extra storage.
- **Theme:** stored in DataStore and applied with `AppCompatDelegate.setDefaultNightMode`.

## TD-6: Fonts are bundled, not downloaded

Noto Serif Malayalam and Hanken Grotesk (SIL OFL) are bundled so that Malayalam renders correctly offline and on every device. The 1.9 MB Noto Serif (Latin) is omitted; Latin headlines fall back to the system font.

## TD-7: `minSdk` 24 with core-library desugaring

The library's API uses `java.time`. Desugaring makes it available below API 26.

## TD-9: Place search uses the platform Geocoder

It's free and needs no API key; it works through Google Play services. It doesn't return time zones, so the zone comes from the country through ICU (`android.icu.util.TimeZone.getAvailableIDs`, API 24+): automatic for single-zone countries, chosen by the user otherwise. When offline or unavailable, the user enters the place manually (name, latitude, longitude, zone). A `GeocoderClient` wrapper keeps the mapping unit-testable.

## TD-10: Birth-star uncertainty is shown, not hidden

Within 60 minutes of a nakshatra change (birth times are often approximate), or when the birth time is unknown, the app lists the possible stars and lets the user choose. It never silently picks one.

## TD-11: Event colours are Google Calendar's colours

The user picks one of Google Calendar's 11 event colours, stored as its `colorId` (1–11), so that synced events (Sprint 4) keep that colour.

## TD-12: Results recalculate automatically

The Find dates screen recalculates whenever the selected members or dates change, cancelling any calculation in progress (`collectLatest`), instead of needing a Calculate button. A 5-year search for one member takes well under a second.

## TD-13: Which occurrences belong to a range

An occurrence is listed when its observance day (Settings convention) falls inside the range. Its start and end times are shown in the member's calculation zone, and it is marked "spans 2 days" when it crosses local midnight. An occurrence without an observance day (a star too short to cover the checkpoint) is listed when it overlaps the range.

## TD-14: The calendar grid is a RecyclerView, not the library's Compose view

The library's `KollavarshamMonthView` is Jetpack Compose, which the app does not use yet (XML only). The app draws its own 7-column grid with the same data. Weeks start on Sunday, as in Kerala almanacs and the design.

## TD-8: Google Calendar integration

- **Authorization:** Google Identity Services `AuthorizationClient` with `calendar.events` and `calendar.calendarlist.readonly` only. Google Play services keeps the grant; the app asks for a short-lived token when it needs one and never stores tokens. Consent is always shown in the foreground (before a sync starts, or from Retry); the background worker only uses a silent token and marks the run "Reconnect needed" otherwise. Disconnect calls Google's revoke endpoint.
- **API:** Calendar API v3 over OkHttp and kotlinx.serialization, not the Google API client library: four calls, a smaller app, and easy MockWebServer tests.
- **Idempotency:** each event's id is base32hex(SHA-1(person, nakshatra, date, calendar)). Inserting an existing id returns 409, which the client turns into an update, so duplicates are impossible even after a reinstall. Locally, `calendar_event` is unique per (occurrence, calendar).
- **Updates:** a SHA-256 hash of everything sent is stored per event. A re-sync skips events whose hash is unchanged and updates the rest. An event the user deleted in Google is restored by the update (`status: confirmed`), but only when its content changed, so an unchanged re-sync costs no API calls.
- **Retries:** WorkManager (network constraint, exponential backoff from 30 s, 5 attempts). Offline, 429, 403 rate/quota and 5xx are retried; other 4xx fail the event with its error kept for the history. A 401 drops the token and retries once with a fresh one.
- **Data:** `nakshatra_occurrence` keeps a snapshot of each synced occurrence, so retries need no recalculation. Deleting a member clears the person link (`SET NULL`) instead of cascading, so their events can still be removed from Google later.

## TD-15: All-day events and reminders

Birth-star days are all-day events (D12). Google counts all-day reminders back from midnight at the start of the day, so "1 hour before" fires at 11 pm the evening before. Settings explains this next to the reminder choice. The default is 1 day before.

## TD-16: UI tests replace Google with a test-only module

Instrumented tests run in `HiltTestApplication` (custom `HiltTestRunner`). `FakeGoogleCalendarModule` (`@TestInstallIn`, androidTest only) replaces the OAuth client, the REST gateway and WorkManager with fakes that run the real sync engine, so the whole flow is tested on an emulator without a Google account. The real Google API is checked manually on a device.
