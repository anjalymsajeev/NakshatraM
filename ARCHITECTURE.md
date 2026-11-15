# Architecture

## Principles

- **MVVM + Clean Architecture**, single `:app` module with strict package layering. Feature modules can be split out later without changing the code inside each layer.
- **The domain is pure Kotlin.** It contains no Android types and no library types, so it's testable on the JVM and independent of the UI toolkit.
- **No calendar logic in the app.** Malayalam dates and nakshatras come from the library, behind domain interfaces.
- **The UI only renders.** Fragments collect an immutable `UiState` from a `StateFlow`; ViewModels hold no View references.

## Layers and packages

```
io.github.anjalymsajeev.nakshatracalendar
├── core/          common (AppResult, AppError, dispatchers), database (Room), di (Clock, dispatchers)
├── domain/        model, repository (interfaces), usecase
├── data/          local (Room entities, DAOs), repository (implementations, DataStore)
├── integration/   malayalamcalendar, nakshatra (library adapters), geocoding, googlecalendar (OAuth, REST, worker)
└── feature/       home, person, nakshatra, finddates, calendar, sync (preview, account, results, history), settings
```

Dependencies point inward: `feature → domain ← data / integration`. Hilt modules bind the implementations.

```mermaid
graph LR
  F[feature: Fragment + ViewModel] --> UC[domain: use cases]
  UC --> RI[domain: repository interfaces]
  D[data: Room, DataStore] -. implements .-> RI
  I[integration: library adapters] -. implements .-> RI
  I --> LIB[(kollavarsham-panchangam / -locale / -core)]
```

## Library integration

| Domain interface | Implementation | Library API used |
|---|---|---|
| `MalayalamDateProvider` | `KollavarshamDateProvider` | `KollavarshamCalendar.fromGregorian`, `monthBoundary`, `supportedRange`, `MalayalamMonth.displayName`, `MalayalamDate.format` |
| `NakshatraEngine` | `PanchangamNakshatraEngine` | `Panchangam.create(PanchangamLocation)`, `sunrise`, `nakshatraSpan`, `nakshatraOccurrences` (0.2.0), `Nakshatra.displayName` |
| `PlaceSearchRepository` | `GeocoderPlaceSearchRepository` | Android `Geocoder` (via `GeocoderClient`), `android.icu.util.TimeZone` (via `TimeZoneResolver`) |

- Library exceptions are mapped to `AppError` at the adapter: `DateOutOfSupportedRangeException` becomes `UnsupportedDate`, and failed calculations become `CalculationFailed`.
- Nakshatra (astronomy) and Malayalam date conversion are separate interfaces backed by separate library modules.
- Date-range results (`FindNakshatraDatesUseCase`) and calendar family markers (`ObserveCalendarDaysUseCase`) both use `NakshatraEngine.occurrences`, backed by the library's `Panchangam.nakshatraOccurrences` (0.2.0).
- Malayalam month views take their first and last days from the library's month boundaries (`MalayalamDateProvider.monthContaining`); the app never computes month starts.

## State and data flow

`GetTodayOverviewUseCase` combines the settings `Flow`, the family-count `Flow` (Room) and the library calls into an `AppResult<TodayOverview>`. `HomeViewModel` maps it to `HomeUiState` (`Loading`, `Content` or `Error`) with `stateIn(WhileSubscribed)`. Heavy work runs on `@DefaultDispatcher`.

## Persistence

- **Room** (`AppDatabase`, schema v3, exported to `app/schemas`). v1→v2 (birth place columns) and v2→v3 (sync tables) are auto-migrations, each with a `MigrationTestHelper` test. Every version change ships a migration and a migration test.
- Sync tables: `nakshatra_occurrence` (snapshot) ← `calendar_event` (unique per occurrence and calendar) → `calendar_sync` (run); `reminder_configuration` (one row).
- **DataStore:** theme, star-day convention, and the chosen Google calendar (id and name).
- **Language:** AppCompat's per-app locale (`setApplicationLocales`), the single source of truth.
- **OAuth tokens are never stored by the app.** Google Identity Services manages the grant.

## Google Calendar sync

```mermaid
graph LR
  P[EventPreviewViewModel] -->|confirm + consent| S[StartSyncUseCase]
  S --> R[(Room: run, occurrences, events)]
  S --> W[SyncScheduler → CalendarSyncWorker]
  W --> E[CalendarSyncEngine]
  E --> G[CalendarGateway: GoogleCalendarApi / OkHttp]
  E --> A[GoogleAuthRepository: AuthorizationClient]
  E --> R
  H[SyncResult / SyncHistory] --> R
```

`CalendarSyncEngine` is plain domain code (no Android types) and is unit-tested with fakes; the worker only maps its outcome to WorkManager results.

## Compose migration path

Screens can be swapped one at a time: `HomeUiState` and the ViewModels don't depend on Views, so a Composable can collect the same `StateFlow`. Repositories, use cases, the database and the integration layer stay unchanged. No Compose dependency is added until migration starts.
