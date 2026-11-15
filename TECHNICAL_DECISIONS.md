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

## TD-8: Google Calendar (planned for Sprint 4)

- Google Identity Services `AuthorizationClient` with the scopes `calendar.events` and `calendar.calendarlist.readonly`.
- The Calendar REST API over Retrofit/OkHttp.
- Deterministic client-side event IDs plus a unique Room mapping, so that inserts are idempotent and duplicates impossible.
- WorkManager for retries with backoff.
