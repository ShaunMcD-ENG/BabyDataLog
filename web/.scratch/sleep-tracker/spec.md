# Sleep tracker — spec

Status: ready for implementation
Source: grilled 2026-09-15 (wayfinder, single-session — no map/tickets needed, see Process note at bottom)

## 1. Overview

A new top-level "Sleep" section, alongside Feeding / Nappy / Growth / Milestones. Records both naps and overnight sleep as one underlying session concept (`SleepSession`), tagged `NAP` or `NIGHT`, with:

- Start/stop recording (persists immediately, syncs across devices).
- A "currently asleep" live status shown on the Home screen.
- Three lenses of stats — Nap-only, Night-only, Combined — as numbers and as an hourly "when do they usually sleep" histogram.
- A condensed summary on the existing Summary screen.

Follows the same architectural patterns already used for `FeedingSession` end to end (entity → DAO → repository → sync DTO → sync push/pull → screens/viewmodel), since that entity is structurally the closest existing analog (start/end timestamps, nullable end, soft delete, natural-key dedup).

## 2. Data model

### 2.1 Entity — `SleepSession`

New file `android/app/src/main/java/com/babydatalog/app/data/database/entity/SleepSession.kt`, mirroring `FeedingSession.kt`:

```
enum class SleepType { NAP, NIGHT }

@Entity(
    tableName = "sleep_sessions",
    foreignKeys = [ForeignKey(entity = Baby::class, parentColumns = ["id"], childColumns = ["babyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("babyId")]
)
data class SleepSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String,
    val babyId: Long,
    val startTimeMs: Long,
    val endTimeMs: Long?,       // null while the session is open ("currently asleep")
    val type: SleepType,        // user-editable, defaulted from start hour — see 4.3
    val notes: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val deletedAtMs: Long? = null
)
```

No `durationMinutes` column — duration is always derived from `endTimeMs - startTimeMs` (unlike `FeedingSession`, which stores it because feeding recording is duration-first; sleep recording is clock-time-first, see 4.1).

`Converters.kt` needs a `SleepType` enum converter, same pattern as the existing `BreastSide`/`BabyState`/etc. converters.

### 2.2 DAO — `SleepDao`

Mirrors `FeedingDao.kt`:

- `insertSleep(session: SleepSession): Long`
- `updateSleep(session: SleepSession)`
- `deleteSleep(session: SleepSession)`
- `softDeleteAllForBaby(babyId, now)`
- `getSleepSessionsForBaby(babyId): Flow<List<SleepSession>>` — `ORDER BY startTimeMs DESC`, `deletedAtMs IS NULL`
- `getSleepById(id): Flow<SleepSession?>`
- `getSleepInRange(babyId, startMs, endMs): Flow<List<SleepSession>>` — sessions overlapping `[startMs, endMs)` (needs `endTimeMs IS NULL OR endTimeMs > :startMs` AND `startTimeMs < :endMs`, not a strict `startTimeMs BETWEEN`, since sessions crossing the range boundary — e.g. midnight — must still be picked up; see 5.1)
- `getOpenSleepSession(babyId): Flow<SleepSession?>` — `WHERE babyId = :babyId AND endTimeMs IS NULL AND deletedAtMs IS NULL LIMIT 1`, backs the Home-screen "currently asleep" indicator
- `getAllForSync(): List<SleepSession>`
- `getByUuid(uuid): SleepSession?`
- `getByNaturalKey(babyId, minuteStart, minuteEnd): SleepSession?` — same minute-bucket dedup as `FeedingDao.getByNaturalKey`, keyed on `startTimeMs`
- `deleteAll()`

### 2.3 Repository — `SleepRepository`

Mirrors `FeedingRepository.kt`. Two additions beyond the standard CRUD wrapper:

- `startSleep(babyId, type, startTimeMs = now): Long` — inserts a row with `endTimeMs = null`, triggers the debounced sync (same as every other write — see `SyncScheduler`).
- `endSleep(sessionId, endTimeMs = now)` — sets `endTimeMs`, `updatedAtMs = now`. Callable from either device (the session isn't "owned" by the device that started it — any device with the open row can end it, per the requirement that either parent can log the wake-up).

### 2.4 Room database

`BabyDataLogDatabase.kt` is at `version = 7`. Bump to `version = 8`, add `SleepSession::class` to the entities list, register `SleepDao`. Add a `Migration(7, 8)` that creates `sleep_sessions` with the same column set as 2.1 plus an index on `babyId`.

## 3. Sync (web + android)

Mirrors the Feeding sync path exactly.

### 3.1 Web schema — `web/lib/db/schema.ts`

Add to `runMigrations()`:

```sql
CREATE TABLE IF NOT EXISTS sleep_sessions (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  syncUuid    TEXT    NOT NULL UNIQUE,
  babyId      INTEGER NOT NULL REFERENCES babies(id) ON DELETE CASCADE,
  startTimeMs INTEGER NOT NULL,
  endTimeMs   INTEGER,
  type        TEXT    NOT NULL,
  notes       TEXT,
  createdAtMs INTEGER NOT NULL,
  updatedAtMs INTEGER NOT NULL DEFAULT 0,
  deletedAtMs INTEGER
);
CREATE INDEX IF NOT EXISTS idx_sleep_babyId    ON sleep_sessions(babyId);
CREATE INDEX IF NOT EXISTS idx_sleep_updatedAt ON sleep_sessions(updatedAtMs);
```

Add `"sleep_sessions"` to the `DATA_TABLES` array (the loop that backfills `updatedAtMs`/`deletedAtMs` on existing installs) — harmless no-op on a fresh table, keeps the migration loop uniform. Not part of the `CHILD_TABLES` one-time baby-uuid-merge migrations (those are historical, one-off fixes for data that predates this table).

### 3.2 Sync route — `web/app/api/sync/route.ts`

- Add `"sleep_sessions"` to `SYNC_TABLES`.
- `timeCol` for dedup/conflict-window logic: `startTimeMs` (same branch as `feeding_sessions`, not `timestampMs` like the point-in-time tables).

### 3.3 Android sync DTO — `SyncModels.kt`

```
@Serializable
data class SyncSleep(
    val id: Long, val syncUuid: String, val babyId: Long,
    val startTimeMs: Long, val endTimeMs: Long?, val type: String,
    val notes: String?, val createdAtMs: Long, val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
    val babySyncUuid: String = ""
)

fun SleepSession.toSync() = SyncSleep(id, syncUuid, babyId, startTimeMs, endTimeMs, type.name, notes, createdAtMs, updatedAtMs, deletedAtMs)
fun SyncSleep.toEntity() = SleepSession(id, syncUuid, babyId, startTimeMs, endTimeMs, SleepType.valueOf(type), notes, createdAtMs, updatedAtMs, deletedAtMs)
```

### 3.4 `SyncRepository.kt`

Add a `TablePush("sleep_sessions", sleepDao.getAllForSync().mapNotNull { ... })` block (push side) and a `pull.data["sleep_sessions"]?.jsonArray?.forEach { ... }` block (pull side), following the `feeding_sessions` blocks exactly — including `describeSleep(...)` for the hold-back/defer message (e.g. `"Sleep session, <formatted start time>"`) and the `"sleep_sessions" ->` branch in the conflict/apply `when`.

An open session (`endTimeMs = null`) syncs like any other row — no special-casing needed; last-write-wins on `updatedAtMs` (existing conflict resolution) is sufficient for the rare case both parents end the same session within one sync cycle.

## 4. Recording UX

### 4.1 Screens

New `android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/` package: `SleepListScreen.kt`, `SleepFormScreen.kt` — same shape as the `nappy`/`feeding` packages (list with tap-to-edit, `ConfirmDeleteDialog` for delete, `SleepViewModel` with a `formState`).

Unlike Feeding's Timer/Manual tabs (duration-first), the sleep form is clock-time-first:

- A single primary action button that's contextual: **"Went to sleep"** when no session is open for this baby, **"Woke up"** when one is (bound to `getOpenSleepSession`). Tapping either calls `startSleep`/`endSleep` immediately — no separate save step for the common path.
- A manual-entry mode (reachable from the list screen's "+" like other sections, or an "Edit times" affordance on an open/closed session) using two `DateTimePickerRow`s (start, end) for back-filling or correcting — end may be left blank only if this is meant to represent the still-open session.
- `SleepType` toggle (`ToggleChipGroup`, `NAP`/`NIGHT`), defaulted per 4.3, always visible and changeable.
- `Notes` field, same as other sections.

### 4.2 Home screen

`HomeViewModel` gains a `currentSleepSession: StateFlow<SleepSession?>` sourced from `sleepRepository.getOpenSleepSession(activeBabyId)`.

`HomeScreen` shows a prominent status card when non-null: "Baby is asleep 😴" with elapsed time (live-ticking, same `LaunchedEffect` timer pattern as the feeding form's live timer) and a "Wake up" button that calls `endSleep` directly from Home — no navigation required. This is new UI territory (no other in-progress state is currently surfaced on Home); it's the one thing worth a dedicated card given it's genuinely live, cross-device state.

### 4.3 Type default

On starting a session (or opening the manual-entry form fresh), `type` pre-selects based on **start hour only**: local hour in `[18:00, 06:00)` → `NIGHT`, else `NAP`. This is a default, not a constraint — the toggle is changeable regardless of what hour it is, at creation or on later edit.

## 5. Stats

All stats are computed per-baby, over a selected period, with sessions crossing a day/period boundary split as described below. Three lenses throughout: **Nap-only**, **Night-only**, **Combined** (Nap + Night).

### 5.1 Day-boundary splitting

A session `[startTimeMs, endTimeMs)` that spans local midnight is split into per-day portions before any daily aggregation: the pre-midnight portion attributes to the day it started, the post-midnight portion to the next day. Use `java.time.ZoneId.systemDefault()` / `LocalDate` for this, consistent with `SummaryViewModel`'s existing bucketing (`todayStartMs`/`weekStartMs`/etc. helpers) — no new timezone handling needed, DST is whatever `java.time` already does for local-date arithmetic elsewhere in the app.

A consequence, intentional: an overnight session increments the session **count** for both days it touches (it's genuinely present on both calendar days).

### 5.2 Per-lens numeric stats

For a selected filter period (5.4) — computed once per lens (filter sessions to `type == NAP`, `type == NIGHT`, or no filter for Combined) over the day-split portions from 5.1:

- **Session count** (Nap lens: "daily nap count" / avg naps-per-day derives from this; Night lens: nights recorded; Combined: not typically shown as a count, since a Combined "count" double-counts split overnight sessions — expose count for Nap and Night lenses only)
- **Total duration** — sum of (split) durations in the period
- **Avg duration per day** — total duration / number of calendar days in the period (Combined lens: this is "how long in the day they slept" / "weekly avg sleep per day" from the original ask; Nap lens: avg nap-time per day; Night lens: avg night-sleep per day)
- **Avg sessions per day** (Nap and Night lenses) — session count / number of calendar days in the period
- **Avg single-session duration** (Nap and Night lenses) — total duration / session count, guarded against divide-by-zero

An **open session is excluded** from all of the above until it ends (per Q19) — no partial/live contribution to counts or durations.

### 5.3 Hourly histogram

For each lens (Nap / Night / Combined) and each hour-of-day bucket `0..23`:

```
% for hour h = (number of days in the period where the baby was recorded asleep,
                per that lens, at any point during local hour h)
               / (number of days in the period)
```

"At any point during the hour" is binary presence (any overlap counts), not duration-weighted — a session overlapping hour `h` at all marks that day as "asleep during h" for that lens, using the same day-split portions as 5.1 so a session's contribution to a given calendar day only counts hours that actually fall on that day.

Rendered as a 24-bar Vico `columnChart` (no new charting dependency), x-axis hour labels matching the existing `SummaryViewModel` today-chart convention (label every 2 hours). A segmented button group (Nap / Night / Combined) above or below the chart switches which lens is plotted — one chart, not three stacked — per Q18.

### 5.4 Filters

Independent of the existing `SummaryPeriod` (Today/This Week/This Month) — a new enum local to the Sleep section, e.g.:

```
enum class SleepStatsPeriod { LAST_7_DAYS, LAST_30_DAYS, LAST_365_DAYS, ALL_TIME }
```

Rolling windows ending "now" (not calendar week/month/year boundaries) — `LAST_7_DAYS` = the 7 most recent days including today, etc. `ALL_TIME` spans from the baby's earliest recorded session to now. This filter set applies to both 5.2 (numeric stats) and 5.3 (histogram) uniformly.

### 5.5 Sleep screen layout

New `SleepStatsScreen` (or a stats section within the Sleep tab): period selector (5.4) at top, numeric stat cards per lens (5.2) — likely three cards or three columns, Nap / Night / Combined — then the histogram (5.3) with its own lens switcher below.

## 6. Summary screen integration

`SummaryViewModel` gains a `sleepStats: StateFlow<SleepSummaryStats>` combined the same way `stats` already is, keyed on `_defaultBabyId` and `_selectedPeriod` (the existing `SummaryPeriod` — Today/This Week/This Month — reused as-is here, not the new 5.4 filter set, since this card is meant to sit alongside the existing Feeding/Nappy numbers which already use that period).

```
data class SleepSummaryStats(val sessionCount: Int = 0, val totalSleepMinutes: Float = 0f)
```

`sessionCount`/`totalSleepMinutes` = Combined lens (5.2) totals for the selected `SummaryPeriod`, day-split per 5.1.

`SummaryScreen.kt`: a new condensed card rendered immediately after `WeightGrowthCard(weightStats)` (currently line ~138) — two stat rows (session count, total time asleep), no chart. Reuses the existing `StatRow` composable already used inside `WeightGrowthCard`.

## 7. Navigation

`NavGraph.kt` gains routes for `SleepListScreen`, `SleepFormScreen`, and the stats screen (7.1's naming TBD at implementation time — mirror however Feeding/Nappy's routes are named). Bottom/side nav (wherever Feeding/Nappy/Growth/Milestones are listed) gains a "Sleep" entry.

## 8. Out of scope (explicitly, per grilling)

- No auto-filtering of short/accidental sessions — same as every other entry type, the user edits/deletes bad data manually.
- No push/instant-sync special-casing for the "currently asleep" status — existing ~10s debounce + 30-min periodic sync is accepted as fast enough.
- No changes to `SummaryPeriod`'s Today/This Week/This Month semantics or its existing Feeding/Nappy charts.
- Open (in-progress) sessions never contribute to aggregate stats — only the Home-screen live card shows "in progress" state.

## Process note

This spec was produced by a single wayfinder grilling session that resolved every open decision breadth-first (destination type, scope, data model, recording UX, day-boundary math, classification, histogram semantics, filters, UI placement) without leaving fog for a later round. Per the wayfinder skill, when a session surfaces no fog the effort doesn't need a full map-with-tickets structure — this spec file is the complete destination artifact. No `map.md` or `issues/` tickets were created for this effort.
