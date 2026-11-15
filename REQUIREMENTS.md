# Requirements

The full product brief was provided by the owner on 2026-11-14. This file summarises it and records the decisions taken since.

## Product

A Malayalam Nakshatra Calendar: family members with birth nakshatras, every occurrence of each nakshatra in a chosen date range (Malayalam and Gregorian dates, start and end times, observance day), Gregorian and Malayalam calendar views, Google Calendar events (automatic or manual, idempotent, with history), reminders, Malayalam and English, light and dark themes.

## Constraints

- Kotlin, XML + ViewBinding, MVVM + Clean Architecture, Hilt, Room, DataStore, WorkManager, Coroutines + Flow, Navigation, Material Components, java.time, Google Calendar API with OAuth 2.0, JUnit, MockK and Espresso. No Compose in the initial release.
- Use the existing Malayalam Calendar library. Don't duplicate its logic.
- No hard-coded dates or invented astronomical results; validate against independent reference data.
- No OAuth tokens in ordinary tables; minimum permissions; never create events without consent.

## Decisions

| # | Decision | Date |
|---|---|---|
| D1 | Repository `anjalymsajeev/NakshatraM`; application id and package `io.github.anjalymsajeev.nakshatracalendar` (Google Play rejects `com.example`) | 2026-11-14 |
| D2 | Searching for every occurrence of a nakshatra in a range lives in the library (`Panchangam.nakshatraOccurrences`, library 0.2.0), not in the app | 2026-11-14 |
| D3 | The birth-star day is a setting: "at sunrise" by default, or "6 nazhika" (2 h 24 min after sunrise) | 2026-11-14 |
| D4 | Bottom navigation: Home · Family · Calendar · Settings. Sync history opens from Home and Settings. The Alerts screen is deferred. | 2026-11-14 |
| D5 | Design: Stitch "Heritage & Horizon". The RouteRelay (bus app) screens in the same export are out of scope. | 2026-11-14 |
| D6 | "Calculate Malayalam star" from birth date, time and place is included (optional; manual pick always available) | 2026-11-15 |
| D7 | Locations use online place search (platform Geocoder) with a manual fallback | 2026-11-15 |
| D8 | Family cards show the next Pirannal; this needs library 0.2.0-alpha01 | 2026-11-15 |
| D9 | No relation field or relation filter chips for now | 2026-11-15 |

## Sprint plan

| Sprint | Scope | Status |
|---|---|---|
| 1 | Foundation: Gradle, Hilt, Room, DataStore, theme, navigation, library integration, Home | Done |
| 2 | Family members (CRUD, nakshatra picker, birth-star calculation, place search, colours, next Pirannal) | Done |
| 3 | Date range, nakshatra results, Gregorian and Malayalam calendars | Planned |
| 4 | Google Calendar: OAuth, calendar picker, preview, auto and manual sync, history | Planned |
| 5 | Production readiness | Planned |
