# Changelog

## [Unreleased]

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
