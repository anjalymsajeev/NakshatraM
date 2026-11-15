# Nakshatra Calendar (NakshatraM)

An Android app for Malayalam families: keep each person's birth nakshatra, see their Pirannal (birth-star) days, and add them to Google Calendar.

Built with Kotlin, XML layouts and MVVM + Clean Architecture. All Malayalam calendar and nakshatra calculations come from the separate [Malayalam Calendar Kotlin library](https://github.com/anjalymsajeev/malayalam-calendar-kotlin) (`io.github.anjalymsajeev:kollavarsham-*` on Maven Central). This app contains no calendar engine of its own.

> **Status: Sprint 4 (Google Calendar).** Family members, Nakshatra dates in a range, Gregorian and Malayalam calendars, and Google Calendar sync (preview, automatic or selected dates, no duplicates, reminders, history) are done. Production hardening follows in Sprint 5. See [CHANGELOG.md](CHANGELOG.md).

## Features (planned for 1.0)

- Family members with their birth nakshatra (all 27, in Malayalam and English).
- Every occurrence of a person's nakshatra in a date range, with Malayalam and Gregorian dates, start and end times, and the observance day (at sunrise or by the 6-nazhika rule).
- Gregorian and Malayalam month calendars with family markers.
- Google Calendar events, created automatically or for chosen dates, with no duplicates and a sync history.
- Malayalam and English UI; light and dark themes; works offline except for Google Calendar sync.

## Tech stack

Kotlin · XML + ViewBinding · MVVM + Clean Architecture · Hilt · Room · DataStore · Coroutines + Flow · Navigation Component · Material Components · WorkManager · Google Identity Services + Google Calendar API v3 (OkHttp) · JUnit, MockK, Turbine, MockWebServer, Espresso.

## Run on an emulator

1. Install the latest stable [Android Studio](https://developer.android.com/studio).
2. **File → Open** this project, and wait for Gradle sync to finish.
3. **Device Manager → Create Virtual Device** → Pixel 8 → system image **API 35 with "Google Play"** (needed for online place search now, and Google sign-in in Sprint 4) → Finish, then start it with ▶.
4. Choose the emulator in the toolbar and click **Run ▶** (`app`).

From the command line, with an emulator running:

```bash
./gradlew installDebug
```

Every CI run also uploads a ready-made `app-debug-apk`. Download it from the run's **Artifacts**, then drag the APK onto the emulator window.

## Set up Google Calendar (once)

Google Calendar sync needs a Google Cloud project that knows this app. No API key or secret goes into the app: Google matches an Android OAuth client by package name and signing certificate.

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create a project (for example "Nakshatra Calendar").
2. **APIs & Services → Library** → search **Google Calendar API** → **Enable**.
3. **APIs & Services → OAuth consent screen** (Google Auth Platform): app name, your email, audience **External**. Leave it in **Testing** and add your Gmail address under **Test users**.
4. Under **Data access**, add the scopes `.../auth/calendar.events` and `.../auth/calendar.calendarlist.readonly`.
5. **Clients → Create client → Android**: package name `io.github.anjalymsajeev.nakshatracalendar`, and the SHA-1 of your debug key:

   ```bash
   keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android | grep SHA1
   ```

6. Run the app on a device or emulator **with Google Play**, signed in with that test user. Open **Find Nakshatra dates → Add to Google Calendar** and connect.

A release build signed with another key needs its own SHA-1 added to the client.

## Build and test

Requires JDK 17 or newer. Android Studio's bundled JDK works; no separate Java installation is needed.

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug ktlintCheck
```

```bash
./gradlew connectedDebugAndroidTest
```

The second command needs a running emulator or a connected device.

## Documentation

| Document | Contents |
|---|---|
| [REQUIREMENTS.md](REQUIREMENTS.md) | Product requirements and decisions |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layers, packages, data flow, library integration |
| [TECHNICAL_DECISIONS.md](TECHNICAL_DECISIONS.md) | Decision log |
| [CHANGELOG.md](CHANGELOG.md) | Changes per sprint |
