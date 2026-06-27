# babyDataLog

An Android app for tracking newborn daily stats — feeding sessions, nappy changes, and milestones — with summary charts and full JSON/CSV export/import.

Built with Kotlin + Jetpack Compose, Room (SQLite), Hilt DI, and Vico charts. All data stays local on device. Future phases will add a self-hosted sync server for sharing data between multiple phones.

---

## Project structure

```
babyMonitorApp/
├── gradle/
│   ├── libs.versions.toml          # All dependency versions in one place
│   └── wrapper/
│       └── gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts            # App module build config, all dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/
│       │   │   ├── values/
│       │   │   │   ├── strings.xml     # Every UI string in the app
│       │   │   │   ├── themes.xml      # Minimal XML theme (Compose takes over at runtime)
│       │   │   │   └── colors.xml      # Palette reference values
│       │   │   ├── drawable/           # Launcher icon vector drawables
│       │   │   └── mipmap-anydpi-v26/ # Adaptive launcher icons
│       │   └── java/com/babydatalog/app/
│       │       ├── BabyDataLogApp.kt      # @HiltAndroidApp Application class
│       │       ├── MainActivity.kt     # Single activity — hosts NavGraph
│       │       ├── data/
│       │       │   ├── database/
│       │       │   │   ├── BabyDataLogDatabase.kt   # Room database (version 1)
│       │       │   │   ├── Converters.kt         # Enum ↔ String TypeConverters
│       │       │   │   ├── entity/               # Room entities (tables)
│       │       │   │   │   ├── Baby.kt
│       │       │   │   │   ├── FeedingSession.kt  # + BreastSide, BabyState, LatchQuality enums
│       │       │   │   │   ├── NappyChange.kt     # + NappyType, NappyAmount, PooColour enums
│       │       │   │   │   └── Milestone.kt       # + MilestoneCategory enum
│       │       │   │   └── dao/                  # Room DAOs (database queries)
│       │       │   │       ├── BabyDao.kt
│       │       │   │       ├── FeedingDao.kt
│       │       │   │       ├── NappyDao.kt
│       │       │   │       └── MilestoneDao.kt
│       │       │   └── repository/               # Data access layer (used by ViewModels)
│       │       │       ├── BabyRepository.kt
│       │       │       ├── FeedingRepository.kt
│       │       │       ├── NappyRepository.kt
│       │       │       └── MilestoneRepository.kt
│       │       ├── di/
│       │       │   └── DatabaseModule.kt         # Hilt module — wires DB and DAOs
│       │       ├── ui/
│       │       │   ├── theme/
│       │       │   │   ├── Color.kt              # Full warm amber/peach/sage palette
│       │       │   │   ├── Theme.kt              # BabyDataLogTheme (dynamic color API 31+)
│       │       │   │   ├── Type.kt               # Typography scale
│       │       │   │   └── Shape.kt              # Corner radius scale
│       │       │   ├── navigation/
│       │       │   │   └── NavGraph.kt           # All routes + bottom navigation bar
│       │       │   ├── components/               # Reusable Compose components
│       │       │   │   ├── DateTimePickerRow.kt  # Date + time picker (native dialogs)
│       │       │   │   ├── ToggleChipGroup.kt    # Generic FilterChip row
│       │       │   │   ├── SectionHeader.kt      # Small coloured section label
│       │       │   │   ├── ConfirmDeleteDialog.kt
│       │       │   │   └── EmptyStateMessage.kt
│       │       │   └── screens/
│       │       │       ├── home/
│       │       │       │   └── HomeScreen.kt     # Dashboard: last feed, last nappy, quick-add buttons
│       │       │       ├── feeding/
│       │       │       │   ├── FeedingListScreen.kt  # History list with swipe-to-delete
│       │       │       │   └── FeedingFormScreen.kt  # Add/edit: timer or manual duration
│       │       │       ├── nappy/
│       │       │       │   ├── NappyListScreen.kt
│       │       │       │   └── NappyFormScreen.kt    # Poo colour dropdown, conditionally shown
│       │       │       ├── milestone/
│       │       │       │   ├── MilestoneListScreen.kt
│       │       │       │   └── MilestoneFormScreen.kt # Photo picker + Coil thumbnail
│       │       │       ├── summary/
│       │       │       │   └── SummaryScreen.kt  # Vico bar charts, period selector (today/week/month)
│       │       │       └── settings/
│       │       │           └── SettingsScreen.kt # JSON export, CSV export, JSON import
│       │       ├── viewmodel/
│       │       │   ├── HomeViewModel.kt
│       │       │   ├── FeedingViewModel.kt       # Includes live feed timer logic
│       │       │   ├── NappyViewModel.kt
│       │       │   ├── MilestoneViewModel.kt
│       │       │   ├── SummaryViewModel.kt       # SummaryStats + SummaryPeriod enum
│       │       │   └── ExportViewModel.kt        # Drives all export/import operations
│       │       └── utils/
│       │           ├── DateUtils.kt              # 13 date/time helper functions
│       │           └── ExportImportUtils.kt      # JSON + CSV export, JSON import, ExportData model
│       ├── test/                                 # JVM unit tests (no device needed)
│       │   └── java/com/babydatalog/app/utils/
│       │       ├── DateUtilsTest.kt
│       │       └── ExportImportTest.kt
│       └── androidTest/                          # Instrumentation tests (require device/emulator)
│           └── java/com/babydatalog/app/data/
│               ├── BabyDaoTest.kt
│               ├── FeedingDaoTest.kt
│               └── NappyDaoTest.kt
├── build.gradle.kts                # Root build file — plugin declarations
├── settings.gradle.kts
├── gradle.properties
└── gradlew / gradlew.bat
```

---

## Tech stack

| Concern | Library | Version |
|---|---|---|
| UI | Jetpack Compose + Material 3 | BOM 2024.02.00 |
| Navigation | Navigation Compose | 2.7.6 |
| Database | Room (SQLite) | 2.6.1 |
| Dependency injection | Hilt | 2.50 |
| Charts | Vico (Compose-native) | 1.13.1 |
| Image loading | Coil | 2.5.0 |
| JSON serialization | kotlinx.serialization | 1.6.3 |
| Coroutines | kotlinx.coroutines | 1.7.3 |
| Language | Kotlin | 1.9.22 |
| Build | AGP + KSP (no kapt) | 8.2.2 / 1.9.22-1.0.17 |
| Min SDK | Android 8.0 | API 26 |

---

## Database schema

All entities carry a `syncUuid` column (UUID string) reserved for Phase 2 multi-device sync — no schema migration will be needed when sync is added.

### `babies`
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | autoincrement |
| syncUuid | TEXT | UUID for sync |
| name | TEXT | baby's name |
| birthDateMs | INTEGER | Unix ms |
| birthWeightGrams | INTEGER | nullable |
| createdAtMs | INTEGER | |

### `feeding_sessions`
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | |
| syncUuid | TEXT | |
| babyId | INTEGER FK | → babies.id (CASCADE) |
| startTimeMs | INTEGER | feed start |
| endTimeMs | INTEGER | nullable |
| durationMinutes | REAL | nullable, computed or manual |
| breastSide | TEXT | LEFT / RIGHT / BOTH / BOTTLE |
| babyState | TEXT | SLEEPY / ENGAGED / null |
| latchQuality | TEXT | GOOD / POOR / null |
| notes | TEXT | nullable |
| createdAtMs | INTEGER | |

### `nappy_changes`
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | |
| syncUuid | TEXT | |
| babyId | INTEGER FK | |
| timestampMs | INTEGER | |
| type | TEXT | PEE / POO / BOTH |
| amount | TEXT | SMALL / LARGE |
| pooColour | TEXT | MECONIUM / DARK_GREEN / YELLOW_SEEDY / BRIGHT_YELLOW / GREEN / BROWN / PALE_WHITE / RED_BLOOD / NA / null |
| notes | TEXT | nullable |
| createdAtMs | INTEGER | |

### `milestones`
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | |
| syncUuid | TEXT | |
| babyId | INTEGER FK | |
| timestampMs | INTEGER | |
| title | TEXT | |
| description | TEXT | nullable |
| category | TEXT | DEVELOPMENT / MEDICAL / SOCIAL / PHYSICAL / FIRST_TIME |
| photoUri | TEXT | content URI string, nullable |
| createdAtMs | INTEGER | |

---

## Key design decisions

**Single-baby UX** — the database schema supports multiple babies (`babyId` FK on all records) but the UI targets one baby to keep things simple. Multi-baby support is a one-screen addition later.

**Enums stored as strings** — all enum values are stored in SQLite as their `.name` string (e.g. `"LEFT"`, `"MECONIUM"`). This makes the database file human-readable, SQL queries easy to write, and export files self-documenting.

**No permissions required** — export/import uses the Android Storage Access Framework (SAF) — the system file picker handles access. Photo picking uses `GetContent` which is also SAF-based. No `READ_EXTERNAL_STORAGE` needed.

**KSP not kapt** — annotation processing for Hilt and Room uses KSP (faster incremental builds). No kapt configuration.

---

## Export / import

Access from the **Settings tab** (gear icon in the bottom nav).

**Export as JSON** — full fidelity export of all tables into a single `.json` file. Includes `schemaVersion: 1` for forward-compatibility checking on import.

**Export as CSV** — produces 4 files (`babies.csv`, `feedings.csv`, `nappies.csv`, `milestones.csv`) written into a folder you choose. Human-readable in Excel / Sheets.

**Import from JSON** — reads a previously exported `.json` file and inserts all records. Validates `schemaVersion` before importing. Records are inserted with fresh auto-generated IDs (original IDs are not preserved to avoid PK conflicts).

The export/import data model lives in `utils/ExportImportUtils.kt`. The `ExportData` class and its `@Serializable` wrappers (`BabyExport`, `FeedingExport`, etc.) are separate from the Room entities so Room annotations don't bleed into the serialization layer.

---

## Navigation

`ui/navigation/NavGraph.kt` defines all routes via `object Routes`:

| Route | Screen |
|---|---|
| `home` | Dashboard |
| `feeding_list` | Feeding history |
| `feeding_add` | New feeding form |
| `feeding_edit/{feedingId}` | Edit existing feeding |
| `nappy_list` | Nappy history |
| `nappy_add` | New nappy form |
| `nappy_edit/{nappyId}` | Edit existing nappy |
| `milestone_list` | Milestone history |
| `milestone_add` | New milestone form |
| `milestone_edit/{milestoneId}` | Edit existing milestone |
| `summary` | Charts + stats |
| `settings` | Export / import |

---

## Running tests

**Unit tests** (no device needed):
```bash
./gradlew test
```

**Instrumentation tests** (requires connected device or emulator):
```bash
./gradlew connectedAndroidTest
```

---

## Building and sideloading

1. Install **Android Studio** (includes SDK, Gradle, Java): `sudo snap install android-studio --classic`
2. Open this project in Android Studio — it will sync Gradle automatically
3. Enable **Developer Options** on your phone: Settings → About → tap Build Number 7 times
4. Enable **USB Debugging** in Developer Options
5. Connect phone via USB, accept the debugging prompt
6. Click **Run ▶** in Android Studio and select your device

---

## Phase 2 — Sync server (planned)

- Self-hosted server (Node.js or Go)
- Each device generates a UUID on install and sets a friendly name
- Syncthing-style pairing: device A sends a request, device B approves it locally
- Bidirectional sync with last-write-wins conflict resolution (server as authority)
- All Room entities already have `syncUuid` columns — no migration needed when sync is added
- A companion web app will share the same interface and act as an additional sync peer
