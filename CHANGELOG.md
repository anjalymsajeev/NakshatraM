# Changelog

## [Unreleased]

### Sprint 1: foundation
- Gradle (Kotlin DSL, version catalog), Hilt, Room (schema v1 exported), DataStore, Navigation, Material 3 theme.
- "Heritage & Horizon" design system: light and derived dark colours, typography, gold-trimmed cards, buttons, bundled fonts.
- Library integration through `MalayalamDateProvider` and `NakshatraEngine` adapters (`kollavarsham-panchangam` 0.1.0-alpha01).
- Home dashboard: today in Gregorian and Malayalam, Malayalam month and Kollavarsham year, nakshatra at sunrise, family count.
- Settings: language (English/മലയാളം), theme, birth-star day convention. Family and Calendar placeholders.
- English and Malayalam strings; splash screen; adaptive launcher icon.
- Unit tests (adapters against the real library, use case, ViewModel, settings repository), an Espresso smoke test, and GitHub Actions (build, lint, ktlint, unit tests, emulator tests).
