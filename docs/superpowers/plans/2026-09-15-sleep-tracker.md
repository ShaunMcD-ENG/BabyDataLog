# Sleep Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Sleep tracker section (naps + overnight sleep) to BabyDataLog: recording, a live cross-device "currently asleep" status, three-lens (Nap/Night/Combined) stats with an hourly histogram, and a condensed card on the existing Summary screen.

**Architecture:** Mirrors the existing Feeding-session architecture end to end: a Room entity/DAO/repository, a sync DTO pushed/pulled through the existing generic sync pipeline (Android `SyncRepository` + the Next.js `/api/sync` route backed by SQLite), Compose screens wired through Hilt ViewModels, and pure, unit-testable Kotlin functions for the day-boundary math and stats (mirroring `WeightGrowthStats.kt`'s pattern).

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, `kotlinx.serialization`, Vico charts (already a dependency — no new charting library), `java.time`. Web: Next.js route handlers, `better-sqlite3`, TypeScript (no test framework present in `web/` — verified manually).

**Spec:** `.scratch/sleep-tracker/spec.md` — this plan implements that spec section by section; task headers below reference the spec section they satisfy.

## Global Constraints

- Room database version bumps from 7 to 8 (`android/app/src/main/java/com/babydatalog/app/data/database/BabyDataLogDatabase.kt:29`) — every schema-affecting task must extend the same `MIGRATION_7_8`, not add a second migration.
- No new charting dependency — the hourly histogram uses Vico's existing `columnChart()` (already used in `SummaryScreen.kt` and `NappyListScreen.kt`).
- No new date/time library — use `java.time` with `ZoneId.systemDefault()`, matching `DateUtils.kt` and `SummaryViewModel.kt`.
- All local-time day/hour bucketing must be computed via `java.time`, never raw UTC millisecond arithmetic (the existing `floorToDay` in `SyncUuidUtils.kt` is UTC-based and is only ever used for sync natural-key derivation — never reuse it for stats).
- Every write to `sleep_sessions` must call `syncScheduler.requestSyncSoon()`, matching every other repository.
- Web `web/` has no test framework (`web/package.json` has no `test` script or test dependency) — web tasks are verified by running the dev server / migration script manually, not by writing new test infrastructure.
- Follow existing naming: table `sleep_sessions`, DTO `SyncSleep`, entity `SleepSession` — consistent with `feeding_sessions`/`SyncFeeding`/`FeedingSession`.

---

## File Structure

New files:
- `android/app/src/main/java/com/babydatalog/app/data/database/entity/SleepSession.kt` — entity + `SleepType` enum
- `android/app/src/main/java/com/babydatalog/app/data/database/dao/SleepDao.kt`
- `android/app/src/main/java/com/babydatalog/app/data/repository/SleepRepository.kt`
- `android/app/src/main/java/com/babydatalog/app/utils/SleepStats.kt` — all pure day-split/default-type/stats/histogram functions
- `android/app/src/main/java/com/babydatalog/app/viewmodel/SleepViewModel.kt`
- `android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepListScreen.kt`
- `android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepFormScreen.kt`
- `android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepStatsScreen.kt`
- `android/app/src/test/java/com/babydatalog/app/utils/SleepStatsTest.kt`
- `android/app/src/androidTest/java/com/babydatalog/app/data/SleepDaoTest.kt`

Modified files:
- `android/app/src/main/java/com/babydatalog/app/data/database/Converters.kt` — `SleepType` converter
- `android/app/src/main/java/com/babydatalog/app/data/database/BabyDataLogDatabase.kt` — entity, DAO, `MIGRATION_7_8`, version bump
- `android/app/src/main/java/com/babydatalog/app/di/DatabaseModule.kt` — `provideSleepDao`
- `android/app/src/main/java/com/babydatalog/app/data/sync/SyncModels.kt` — `SyncSleep` DTO
- `android/app/src/main/java/com/babydatalog/app/data/sync/SyncRepository.kt` — push/pull/defer blocks for `sleep_sessions`
- `android/app/src/main/java/com/babydatalog/app/viewmodel/HomeViewModel.kt` — `currentSleepSession`, `startSleep`/`endSleep`
- `android/app/src/main/java/com/babydatalog/app/ui/screens/home/HomeScreen.kt` — "currently asleep" card
- `android/app/src/main/java/com/babydatalog/app/ui/navigation/NavGraph.kt` — routes, bottom nav item
- `android/app/src/main/java/com/babydatalog/app/viewmodel/SummaryViewModel.kt` — `sleepStats`
- `android/app/src/main/java/com/babydatalog/app/ui/screens/summary/SummaryScreen.kt` — condensed card
- `web/lib/db/schema.ts` — `sleep_sessions` table
- `web/app/api/sync/route.ts` — `SYNC_TABLES`, `timeCol` branch

---

## Task 1: Data layer — `SleepSession` entity, DAO, Room migration

*Implements spec §2.1, §2.2, §2.4.*

**Files:**
- Create: `android/app/src/main/java/com/babydatalog/app/data/database/entity/SleepSession.kt`
- Create: `android/app/src/main/java/com/babydatalog/app/data/database/dao/SleepDao.kt`
- Create: `android/app/src/androidTest/java/com/babydatalog/app/data/SleepDaoTest.kt`
- Modify: `android/app/src/main/java/com/babydatalog/app/data/database/Converters.kt`
- Modify: `android/app/src/main/java/com/babydatalog/app/data/database/BabyDataLogDatabase.kt`
- Modify: `android/app/src/main/java/com/babydatalog/app/di/DatabaseModule.kt`

**Interfaces:**
- Produces: `SleepSession(id: Long, syncUuid: String, babyId: Long, startTimeMs: Long, endTimeMs: Long?, type: SleepType, notes: String?, createdAtMs: Long, updatedAtMs: Long, deletedAtMs: Long?)`, `enum class SleepType { NAP, NIGHT }`, `SleepDao` with `insertSleep`, `updateSleep`, `deleteSleep`, `softDeleteAllForBaby`, `getSleepSessionsForBaby`, `getSleepById`, `getOpenSleepSession`, `getAllForSync`, `getByUuid`, `getByNaturalKey`, `deleteAll` — all later tasks depend on these exact names.

- [ ] **Step 1: Write the failing DAO test**

Create `android/app/src/androidTest/java/com/babydatalog/app/data/SleepDaoTest.kt`:

```kotlin
package com.babydatalog.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babydatalog.app.data.database.BabyDataLogDatabase
import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.SleepDao
import com.babydatalog.app.data.database.entity.Baby
import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SleepDaoTest {

    private lateinit var db: BabyDataLogDatabase
    private lateinit var babyDao: BabyDao
    private lateinit var sleepDao: SleepDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BabyDataLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        babyDao = db.babyDao()
        sleepDao = db.sleepDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun testBaby(): Baby = Baby(
        syncUuid = UUID.randomUUID().toString(),
        name = "Test Baby",
        birthDateMs = System.currentTimeMillis(),
        birthWeightGrams = 3500,
        createdAtMs = System.currentTimeMillis()
    )

    private fun testSleep(
        babyId: Long,
        startMs: Long = System.currentTimeMillis(),
        endMs: Long? = startMs + 1_800_000L,
        type: SleepType = SleepType.NAP
    ): SleepSession = SleepSession(
        syncUuid = UUID.randomUUID().toString(),
        babyId = babyId,
        startTimeMs = startMs,
        endTimeMs = endMs,
        type = type,
        notes = null,
        createdAtMs = System.currentTimeMillis()
    )

    @Test
    fun insertAndRetrieveSleep() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        sleepDao.insertSleep(testSleep(babyId, type = SleepType.NIGHT))

        val sessions = sleepDao.getSleepSessionsForBaby(babyId).first()
        assertEquals(1, sessions.size)
        assertEquals(SleepType.NIGHT, sessions[0].type)
    }

    @Test
    fun getOpenSleepSession_returnsOnlyUnendedSession() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val now = System.currentTimeMillis()
        sleepDao.insertSleep(testSleep(babyId, startMs = now - 3_600_000L, endMs = now - 1_800_000L)) // closed
        val openId = sleepDao.insertSleep(testSleep(babyId, startMs = now, endMs = null)) // open

        val open = sleepDao.getOpenSleepSession(babyId).first()
        assertNotNull(open)
        assertEquals(openId, open!!.id)
        assertNull(open.endTimeMs)
    }

    @Test
    fun getOpenSleepSession_nullWhenNoneOpen() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        sleepDao.insertSleep(testSleep(babyId))

        val open = sleepDao.getOpenSleepSession(babyId).first()
        assertNull(open)
    }

    @Test
    fun getByNaturalKey_matchesSameMinute() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val now = System.currentTimeMillis()
        val minuteStart = now / 60_000L * 60_000L
        sleepDao.insertSleep(testSleep(babyId, startMs = minuteStart + 10_000L))

        val found = sleepDao.getByNaturalKey(babyId, minuteStart, minuteStart + 60_000L)
        assertNotNull(found)
    }

    @Test
    fun deleteSleep_removesRow() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        sleepDao.insertSleep(testSleep(babyId))
        val inserted = sleepDao.getSleepSessionsForBaby(babyId).first().first()

        sleepDao.deleteSleep(inserted)

        val remaining = sleepDao.getSleepSessionsForBaby(babyId).first()
        assertTrue("Sleep list should be empty after deletion", remaining.isEmpty())
    }

    @Test
    fun updateSleep_persistsEndTime() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        sleepDao.insertSleep(testSleep(babyId, endMs = null))
        val inserted = sleepDao.getSleepSessionsForBaby(babyId).first().first()

        val ended = inserted.copy(endTimeMs = System.currentTimeMillis())
        sleepDao.updateSleep(ended)

        val retrieved = sleepDao.getSleepById(inserted.id).first()
        assertNotNull(retrieved!!.endTimeMs)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails to compile (entity/DAO don't exist yet)**

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest --tests "com.babydatalog.app.data.SleepDaoTest"` (requires a connected device/emulator, same as the existing `FeedingDaoTest`/`NappyDaoTest`).
Expected: Build FAILURE — `Unresolved reference: SleepSession` / `SleepDao` / `SleepType`.

- [ ] **Step 3: Create the entity**

Create `android/app/src/main/java/com/babydatalog/app/data/database/entity/SleepSession.kt`:

```kotlin
package com.babydatalog.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SleepType { NAP, NIGHT }

@Entity(
    tableName = "sleep_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Baby::class,
            parentColumns = ["id"],
            childColumns = ["babyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("babyId")]
)
data class SleepSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String,
    val babyId: Long,
    val startTimeMs: Long,
    val endTimeMs: Long?,       // null while the session is open ("currently asleep")
    val type: SleepType,
    val notes: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val deletedAtMs: Long? = null
)
```

- [ ] **Step 4: Create the DAO**

Create `android/app/src/main/java/com/babydatalog/app/data/database/dao/SleepDao.kt`:

```kotlin
package com.babydatalog.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babydatalog.app.data.database.entity.SleepSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {

    @Insert
    suspend fun insertSleep(session: SleepSession): Long

    @Update
    suspend fun updateSleep(session: SleepSession)

    @Update
    suspend fun deleteSleep(session: SleepSession)

    @Query("UPDATE sleep_sessions SET deletedAtMs = :now, updatedAtMs = :now WHERE babyId = :babyId AND deletedAtMs IS NULL")
    suspend fun softDeleteAllForBaby(babyId: Long, now: Long)

    @Query("SELECT * FROM sleep_sessions WHERE babyId = :babyId AND deletedAtMs IS NULL ORDER BY startTimeMs DESC")
    fun getSleepSessionsForBaby(babyId: Long): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_sessions WHERE id = :id AND deletedAtMs IS NULL")
    fun getSleepById(id: Long): Flow<SleepSession?>

    @Query("SELECT * FROM sleep_sessions WHERE babyId = :babyId AND endTimeMs IS NULL AND deletedAtMs IS NULL LIMIT 1")
    fun getOpenSleepSession(babyId: Long): Flow<SleepSession?>

    @Query("SELECT * FROM sleep_sessions")
    suspend fun getAllForSync(): List<SleepSession>

    @Query("SELECT * FROM sleep_sessions WHERE syncUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): SleepSession?

    @Query("SELECT * FROM sleep_sessions WHERE babyId = :babyId AND startTimeMs >= :minuteStart AND startTimeMs < :minuteEnd LIMIT 1")
    suspend fun getByNaturalKey(babyId: Long, minuteStart: Long, minuteEnd: Long): SleepSession?

    @Query("DELETE FROM sleep_sessions")
    suspend fun deleteAll()
}
```

Note: `deleteSleep` uses `@Update` (not `@Delete`) intentionally — every other DAO in this codebase soft-deletes by updating `deletedAtMs` from the repository layer (see `FeedingRepository.deleteFeeding`), never hard-deletes a row a device might still need to sync a tombstone for. `SleepRepository` (Task 5) will call it with `deletedAtMs` already set.

- [ ] **Step 5: Add the `SleepType` converter**

In `android/app/src/main/java/com/babydatalog/app/data/database/Converters.kt`, add the import and converter pair, following the existing `NappyAmount` pattern:

```kotlin
import com.babydatalog.app.data.database.entity.SleepType
```

```kotlin
    // SleepType
    @TypeConverter
    fun fromSleepType(value: SleepType): String = value.name

    @TypeConverter
    fun toSleepType(value: String): SleepType = SleepType.valueOf(value)
```

- [ ] **Step 6: Register the entity, DAO, and migration in `BabyDataLogDatabase.kt`**

Add imports:

```kotlin
import com.babydatalog.app.data.database.dao.SleepDao
import com.babydatalog.app.data.database.entity.SleepSession
```

Update the `@Database` annotation's `entities` list and bump the version:

```kotlin
@Database(
    entities = [
        Baby::class,
        FeedingSession::class,
        NappyChange::class,
        Milestone::class,
        GrowthMeasurement::class,
        SleepSession::class
    ],
    version = 8,
    exportSchema = true
)
```

Add the abstract DAO accessor:

```kotlin
    abstract fun sleepDao(): SleepDao
```

Add `MIGRATION_7_8` inside the `companion object`, after `MIGRATION_6_7`:

```kotlin
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sleep_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `syncUuid` TEXT NOT NULL,
                        `babyId` INTEGER NOT NULL,
                        `startTimeMs` INTEGER NOT NULL,
                        `endTimeMs` INTEGER,
                        `type` TEXT NOT NULL,
                        `notes` TEXT,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `deletedAtMs` INTEGER,
                        FOREIGN KEY(`babyId`) REFERENCES `babies`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sleep_sessions_babyId` ON `sleep_sessions` (`babyId`)"
                )
            }
        }
```

- [ ] **Step 7: Register the migration and DAO provider in `DatabaseModule.kt`**

Add the import:

```kotlin
import com.babydatalog.app.data.database.dao.SleepDao
```

Add `BabyDataLogDatabase.MIGRATION_7_8` to the `.addMigrations(...)` call (after `MIGRATION_6_7`), and add a provider:

```kotlin
    @Provides
    fun provideSleepDao(database: BabyDataLogDatabase): SleepDao =
        database.sleepDao()
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest --tests "com.babydatalog.app.data.SleepDaoTest"`
Expected: BUILD SUCCESSFUL, all 6 tests pass.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/data/database/entity/SleepSession.kt \
        android/app/src/main/java/com/babydatalog/app/data/database/dao/SleepDao.kt \
        android/app/src/androidTest/java/com/babydatalog/app/data/SleepDaoTest.kt \
        android/app/src/main/java/com/babydatalog/app/data/database/Converters.kt \
        android/app/src/main/java/com/babydatalog/app/data/database/BabyDataLogDatabase.kt \
        android/app/src/main/java/com/babydatalog/app/di/DatabaseModule.kt
git commit -m "feat(sleep): add SleepSession entity, DAO, and DB migration to v8"
```

---

## Task 2: Pure utils — day-boundary splitting and default type

*Implements spec §4.3 and §5.1.*

**Files:**
- Create: `android/app/src/main/java/com/babydatalog/app/utils/SleepStats.kt`
- Create: `android/app/src/test/java/com/babydatalog/app/utils/SleepStatsTest.kt`

**Interfaces:**
- Consumes: `SleepSession`, `SleepType` from Task 1.
- Produces: `data class DaySlice(val date: LocalDate, val startMs: Long, val endMs: Long, val type: SleepType)`, `fun defaultSleepTypeForStart(startTimeMs: Long, zone: ZoneId = ZoneId.systemDefault()): SleepType`, `fun splitAcrossDays(session: SleepSession, zone: ZoneId = ZoneId.systemDefault()): List<DaySlice>`, `enum class SleepStatsPeriod { LAST_7_DAYS, LAST_30_DAYS, LAST_365_DAYS, ALL_TIME }`, `fun periodStartMs(period: SleepStatsPeriod, sessions: List<SleepSession>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long` — Tasks 3, 4, 5, 8, 10 depend on these exact names.

- [ ] **Step 1: Write the failing tests**

Create `android/app/src/test/java/com/babydatalog/app/utils/SleepStatsTest.kt`:

```kotlin
package com.babydatalog.app.utils

import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val UTC: ZoneId = ZoneOffset.UTC

private fun session(
    id: Long = 1L,
    startMs: Long,
    endMs: Long?,
    type: SleepType = SleepType.NAP
) = SleepSession(
    id = id,
    syncUuid = "uuid-$id",
    babyId = 1L,
    startTimeMs = startMs,
    endTimeMs = endMs,
    type = type,
    notes = null,
    createdAtMs = startMs
)

class SleepStatsTest {

    // ─── defaultSleepTypeForStart ───────────────────────────────────────────

    @Test
    fun defaultSleepTypeForStart_earlyAfternoon_isNap() {
        // 2024-01-01 14:00 UTC
        val ms = LocalDate.of(2024, 1, 1).atTime(14, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        assertEquals(SleepType.NAP, defaultSleepTypeForStart(ms, UTC))
    }

    @Test
    fun defaultSleepTypeForStart_evening_isNight() {
        val ms = LocalDate.of(2024, 1, 1).atTime(19, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        assertEquals(SleepType.NIGHT, defaultSleepTypeForStart(ms, UTC))
    }

    @Test
    fun defaultSleepTypeForStart_earlyMorning_isNight() {
        val ms = LocalDate.of(2024, 1, 1).atTime(3, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        assertEquals(SleepType.NIGHT, defaultSleepTypeForStart(ms, UTC))
    }

    @Test
    fun defaultSleepTypeForStart_boundaryHours() {
        val sixAm = LocalDate.of(2024, 1, 1).atTime(6, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        val sixPm = LocalDate.of(2024, 1, 1).atTime(18, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        assertEquals(SleepType.NAP, defaultSleepTypeForStart(sixAm, UTC))   // 6am is the first NAP hour
        assertEquals(SleepType.NIGHT, defaultSleepTypeForStart(sixPm, UTC)) // 6pm is the first NIGHT hour
    }

    // ─── splitAcrossDays ─────────────────────────────────────────────────────

    @Test
    fun splitAcrossDays_withinOneDay_returnsSingleSlice() {
        val dayStart = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val s = session(startMs = dayStart + 3_600_000L, endMs = dayStart + 5_400_000L) // 1h-1h30

        val slices = splitAcrossDays(s, UTC)

        assertEquals(1, slices.size)
        assertEquals(LocalDate.of(2024, 1, 1), slices[0].date)
        assertEquals(1_800_000L, slices[0].endMs - slices[0].startMs)
    }

    @Test
    fun splitAcrossDays_crossingMidnight_splitsIntoTwo() {
        val day1Start = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        // 8pm day 1 to 6am day 2 — 10 hours total
        val startMs = day1Start + 20 * 3_600_000L
        val endMs = day1Start + 30 * 3_600_000L
        val s = session(startMs = startMs, endMs = endMs, type = SleepType.NIGHT)

        val slices = splitAcrossDays(s, UTC)

        assertEquals(2, slices.size)
        assertEquals(LocalDate.of(2024, 1, 1), slices[0].date)
        assertEquals(LocalDate.of(2024, 1, 2), slices[1].date)
        val firstMinutes = (slices[0].endMs - slices[0].startMs) / 60_000
        val secondMinutes = (slices[1].endMs - slices[1].startMs) / 60_000
        assertEquals(4 * 60L, firstMinutes)  // 8pm-midnight = 4h
        assertEquals(6 * 60L, secondMinutes) // midnight-6am = 6h
        assertEquals(SleepType.NIGHT, slices[0].type)
        assertEquals(SleepType.NIGHT, slices[1].type)
    }

    @Test
    fun splitAcrossDays_openSession_returnsEmpty() {
        val s = session(startMs = System.currentTimeMillis(), endMs = null)
        assertTrue(splitAcrossDays(s, UTC).isEmpty())
    }

    @Test
    fun splitAcrossDays_zeroOrNegativeDuration_returnsEmpty() {
        val now = System.currentTimeMillis()
        assertTrue(splitAcrossDays(session(startMs = now, endMs = now), UTC).isEmpty())
        assertTrue(splitAcrossDays(session(startMs = now, endMs = now - 1000L), UTC).isEmpty())
    }

    // ─── periodStartMs ───────────────────────────────────────────────────────

    @Test
    fun periodStartMs_last7Days_isSevenDaysBeforeNow() {
        val now = 1_700_000_000_000L
        val start = periodStartMs(SleepStatsPeriod.LAST_7_DAYS, emptyList(), now, UTC)
        assertEquals(now - 7 * 86_400_000L, start)
    }

    @Test
    fun periodStartMs_allTime_isEarliestSessionStart() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 100 * 86_400_000L, endMs = now - 99 * 86_400_000L),
            session(id = 2, startMs = now - 5 * 86_400_000L, endMs = now - 4 * 86_400_000L)
        )
        val start = periodStartMs(SleepStatsPeriod.ALL_TIME, sessions, now, UTC)
        assertEquals(now - 100 * 86_400_000L, start)
    }

    @Test
    fun periodStartMs_allTime_noSessions_isNow() {
        val now = 1_700_000_000_000L
        assertEquals(now, periodStartMs(SleepStatsPeriod.ALL_TIME, emptyList(), now, UTC))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.babydatalog.app.utils.SleepStatsTest"`
Expected: FAIL — `Unresolved reference: defaultSleepTypeForStart` (compile failure).

- [ ] **Step 3: Implement**

Create `android/app/src/main/java/com/babydatalog/app/utils/SleepStats.kt`:

```kotlin
package com.babydatalog.app.utils

import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val DAY_MS = 86_400_000L

enum class SleepStatsPeriod { LAST_7_DAYS, LAST_30_DAYS, LAST_365_DAYS, ALL_TIME }

/** One session's contribution to a single local calendar day, after splitting at local midnight. */
data class DaySlice(val date: LocalDate, val startMs: Long, val endMs: Long, val type: SleepType)

// A session starting in the evening/overnight window defaults to NIGHT; anything
// else defaults to NAP. This is only ever a pre-selected default — the type field
// is always user-visible and overridable (spec §4.3).
fun defaultSleepTypeForStart(startTimeMs: Long, zone: ZoneId = ZoneId.systemDefault()): SleepType {
    val hour = Instant.ofEpochMilli(startTimeMs).atZone(zone).hour
    return if (hour >= 18 || hour < 6) SleepType.NIGHT else SleepType.NAP
}

/**
 * Splits a closed session ([startTimeMs, endTimeMs)) into per-local-day portions
 * at midnight, so a session spanning two calendar days contributes to both day's
 * totals and hour-buckets independently (spec §5.1). An open session (no end yet)
 * returns nothing — callers must exclude open sessions from stats until they end
 * (spec §5.2's "open session excluded" rule is enforced by never calling this on one).
 */
fun splitAcrossDays(session: SleepSession, zone: ZoneId = ZoneId.systemDefault()): List<DaySlice> {
    val end = session.endTimeMs ?: return emptyList()
    val start = session.startTimeMs
    if (end <= start) return emptyList()

    val slices = mutableListOf<DaySlice>()
    var cursorMs = start
    while (cursorMs < end) {
        val cursorDate = Instant.ofEpochMilli(cursorMs).atZone(zone).toLocalDate()
        val nextMidnightMs = cursorDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sliceEndMs = minOf(end, nextMidnightMs)
        slices += DaySlice(cursorDate, cursorMs, sliceEndMs, session.type)
        cursorMs = sliceEndMs
    }
    return slices
}

/**
 * Start of the rolling window for a stats period. ALL_TIME spans from the
 * earliest recorded session's start to now (spec §5.4); the others are fixed
 * rolling windows ending "now", not calendar-boundary windows.
 */
fun periodStartMs(
    period: SleepStatsPeriod,
    sessions: List<SleepSession>,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Long = when (period) {
    SleepStatsPeriod.LAST_7_DAYS -> nowMs - 7 * DAY_MS
    SleepStatsPeriod.LAST_30_DAYS -> nowMs - 30 * DAY_MS
    SleepStatsPeriod.LAST_365_DAYS -> nowMs - 365 * DAY_MS
    SleepStatsPeriod.ALL_TIME -> sessions.minOfOrNull { it.startTimeMs } ?: nowMs
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.babydatalog.app.utils.SleepStatsTest"`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/utils/SleepStats.kt \
        android/app/src/test/java/com/babydatalog/app/utils/SleepStatsTest.kt
git commit -m "feat(sleep): add day-boundary splitting and default-type pure functions"
```

---

## Task 3: Pure utils — per-lens numeric stats

*Implements spec §5.2.*

**Files:**
- Modify: `android/app/src/main/java/com/babydatalog/app/utils/SleepStats.kt`
- Modify: `android/app/src/test/java/com/babydatalog/app/utils/SleepStatsTest.kt`

**Interfaces:**
- Consumes: `DaySlice`, `splitAcrossDays`, `periodStartMs`, `SleepStatsPeriod` from Task 2.
- Produces: `data class SleepLensStats(val sessionCount: Int?, val totalMinutes: Float, val avgMinutesPerDay: Float, val avgSessionsPerDay: Float?, val avgSessionDurationMinutes: Float?)`, `data class SleepStats(val nap: SleepLensStats, val night: SleepLensStats, val combined: SleepLensStats)`, `fun computeSleepStats(sessions: List<SleepSession>, period: SleepStatsPeriod, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): SleepStats` — Tasks 8, 12 depend on these exact names.

- [ ] **Step 1: Write the failing tests**

Append to `android/app/src/test/java/com/babydatalog/app/utils/SleepStatsTest.kt` (inside the `SleepStatsTest` class):

```kotlin

    // ─── computeSleepStats ───────────────────────────────────────────────────

    @Test
    fun computeSleepStats_emptySessions_allZero() {
        val stats = computeSleepStats(emptyList(), SleepStatsPeriod.LAST_7_DAYS, nowMs = 1_700_000_000_000L, zone = UTC)
        assertEquals(0, stats.nap.sessionCount)
        assertEquals(0f, stats.nap.totalMinutes, 0.01f)
        assertEquals(0, stats.night.sessionCount)
        assertEquals(null, stats.combined.sessionCount)
        assertEquals(0f, stats.combined.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_napsOnly_countedSeparatelyFromNight() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 3_600_000L, endMs = now - 1_800_000L, type = SleepType.NAP),   // 30 min nap
            session(id = 2, startMs = now - 7_200_000L, endMs = now - 5_400_000L, type = SleepType.NAP),   // 30 min nap
            session(id = 3, startMs = now - 36_000_000L, endMs = now - 7_200_000L, type = SleepType.NIGHT) // 8h "night"
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)

        assertEquals(2, stats.nap.sessionCount)
        assertEquals(60f, stats.nap.totalMinutes, 0.01f)
        assertEquals(1, stats.night.sessionCount)
        assertEquals(480f, stats.night.totalMinutes, 0.01f)
        // combined has no session count but sums both
        assertEquals(null, stats.combined.sessionCount)
        assertEquals(540f, stats.combined.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_openSession_excludedEntirely() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 1_800_000L, endMs = null, type = SleepType.NAP) // still asleep
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        assertEquals(0, stats.nap.sessionCount)
        assertEquals(0f, stats.nap.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_overnightSession_countsOnBothDays() {
        val day1Start = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = day1Start + 40 * 3_600_000L // well into day 2
        val overnight = session(
            id = 1,
            startMs = day1Start + 20 * 3_600_000L, // 8pm day 1
            endMs = day1Start + 30 * 3_600_000L,   // 6am day 2
            type = SleepType.NIGHT
        )
        val stats = computeSleepStats(listOf(overnight), SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        // one physical session, split across 2 days -> count of 2 (spec §5.1's stated consequence)
        assertEquals(2, stats.night.sessionCount)
        assertEquals(600f, stats.night.totalMinutes, 0.01f) // 10 hours total, unaffected by the split
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.babydatalog.app.utils.SleepStatsTest"`
Expected: FAIL — `Unresolved reference: computeSleepStats`.

- [ ] **Step 3: Implement**

Append to `android/app/src/main/java/com/babydatalog/app/utils/SleepStats.kt`:

```kotlin

data class SleepLensStats(
    val sessionCount: Int?,             // null for the Combined lens — summing split-session counts across Nap+Night double-counts, so Combined only reports duration (spec §5.2)
    val totalMinutes: Float,
    val avgMinutesPerDay: Float,
    val avgSessionsPerDay: Float?,
    val avgSessionDurationMinutes: Float?
)

data class SleepStats(val nap: SleepLensStats, val night: SleepLensStats, val combined: SleepLensStats)

private fun lensStats(slices: List<DaySlice>, dayCount: Float, includeCount: Boolean): SleepLensStats {
    val totalMinutes = slices.sumOf { (it.endMs - it.startMs) / 60_000.0 }.toFloat()
    if (!includeCount) {
        return SleepLensStats(
            sessionCount = null,
            totalMinutes = totalMinutes,
            avgMinutesPerDay = totalMinutes / dayCount,
            avgSessionsPerDay = null,
            avgSessionDurationMinutes = null
        )
    }
    val count = slices.size
    return SleepLensStats(
        sessionCount = count,
        totalMinutes = totalMinutes,
        avgMinutesPerDay = totalMinutes / dayCount,
        avgSessionsPerDay = count / dayCount,
        avgSessionDurationMinutes = if (count > 0) totalMinutes / count else null
    )
}

/**
 * Per-lens (Nap / Night / Combined) totals for the selected period. Open
 * sessions are excluded entirely (spec §5.2 / Q19); overnight sessions are
 * split at local midnight first (spec §5.1) so a session that only partly
 * overlaps the period contributes only its overlapping portion.
 */
fun computeSleepStats(
    sessions: List<SleepSession>,
    period: SleepStatsPeriod,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): SleepStats {
    val closed = sessions.filter { it.endTimeMs != null }
    val startMs = periodStartMs(period, closed, nowMs, zone)
    val dayCount = ((nowMs - startMs) / DAY_MS.toDouble()).coerceAtLeast(1.0).toFloat()

    val allSlices = closed.flatMap { splitAcrossDays(it, zone) }
        .filter { it.startMs < nowMs && it.endMs > startMs }

    val napSlices = allSlices.filter { it.type == SleepType.NAP }
    val nightSlices = allSlices.filter { it.type == SleepType.NIGHT }

    return SleepStats(
        nap = lensStats(napSlices, dayCount, includeCount = true),
        night = lensStats(nightSlices, dayCount, includeCount = true),
        combined = lensStats(allSlices, dayCount, includeCount = false)
    )
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.babydatalog.app.utils.SleepStatsTest"`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/utils/SleepStats.kt \
        android/app/src/test/java/com/babydatalog/app/utils/SleepStatsTest.kt
git commit -m "feat(sleep): add per-lens numeric sleep stats computation"
```

---

## Task 4: Pure utils — hourly histogram

*Implements spec §5.3.*

**Files:**
- Modify: `android/app/src/main/java/com/babydatalog/app/utils/SleepStats.kt`
- Modify: `android/app/src/test/java/com/babydatalog/app/utils/SleepStatsTest.kt`

**Interfaces:**
- Consumes: `DaySlice`, `splitAcrossDays`, `periodStartMs`, `SleepStatsPeriod` from Task 2.
- Produces: `enum class SleepLens { NAP, NIGHT, COMBINED }`, `data class SleepHistogram(val hourlyPercent: List<Float>)` (size 24, index = local hour-of-day, value 0..100), `fun computeSleepHistogram(sessions: List<SleepSession>, period: SleepStatsPeriod, lens: SleepLens, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): SleepHistogram` — Task 13 depends on these exact names.

- [ ] **Step 1: Write the failing tests**

Append to `android/app/src/test/java/com/babydatalog/app/utils/SleepStatsTest.kt` (inside the class):

```kotlin

    // ─── computeSleepHistogram ───────────────────────────────────────────────

    @Test
    fun computeSleepHistogram_singleDaySingleNap_marksOnlyOverlappingHours() {
        val dayStart = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = dayStart + 2 * DAYS_MS_TEST
        // Nap from 14:45 to 15:15 -> overlaps hour 14 and hour 15
        val nap = session(startMs = dayStart + 14 * 3_600_000L + 45 * 60_000L, endMs = dayStart + 15 * 3_600_000L + 15 * 60_000L)

        val histogram = computeSleepHistogram(listOf(nap), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NAP, now, UTC)

        assertEquals(24, histogram.hourlyPercent.size)
        assertTrue("hour 14 should show >0%", histogram.hourlyPercent[14] > 0f)
        assertTrue("hour 15 should show >0%", histogram.hourlyPercent[15] > 0f)
        assertEquals(0f, histogram.hourlyPercent[10], 0.01f)
    }

    @Test
    fun computeSleepHistogram_binaryPresence_notDurationWeighted() {
        val dayStart = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = dayStart + 2 * DAYS_MS_TEST
        // Only 1 minute of overlap with hour 6, but the whole hour still counts as "asleep"
        val nap = session(startMs = dayStart + 5 * 3_600_000L + 59 * 60_000L, endMs = dayStart + 6 * 3_600_000L + 1 * 60_000L)

        val histogram = computeSleepHistogram(listOf(nap), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NAP, now, UTC)

        // period is LAST_7_DAYS -> denominator is 7 days, one day has presence in hour 5 and hour 6
        assertTrue(histogram.hourlyPercent[5] > 0f)
        assertTrue(histogram.hourlyPercent[6] > 0f)
    }

    @Test
    fun computeSleepHistogram_lensFiltersByType() {
        val dayStart = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = dayStart + 2 * DAYS_MS_TEST
        val nap = session(id = 1, startMs = dayStart + 15 * 3_600_000L, endMs = dayStart + 16 * 3_600_000L, type = SleepType.NAP)
        val night = session(id = 2, startMs = dayStart + 21 * 3_600_000L, endMs = dayStart + 23 * 3_600_000L, type = SleepType.NIGHT)

        val napOnly = computeSleepHistogram(listOf(nap, night), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NAP, now, UTC)
        assertEquals(0f, napOnly.hourlyPercent[21], 0.01f)
        assertTrue(napOnly.hourlyPercent[15] > 0f)

        val nightOnly = computeSleepHistogram(listOf(nap, night), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NIGHT, now, UTC)
        assertEquals(0f, nightOnly.hourlyPercent[15], 0.01f)
        assertTrue(nightOnly.hourlyPercent[21] > 0f)

        val combined = computeSleepHistogram(listOf(nap, night), SleepStatsPeriod.LAST_7_DAYS, SleepLens.COMBINED, now, UTC)
        assertTrue(combined.hourlyPercent[15] > 0f)
        assertTrue(combined.hourlyPercent[21] > 0f)
    }

    @Test
    fun computeSleepHistogram_openSession_excluded() {
        val now = 1_700_000_000_000L
        val open = session(startMs = now - 1_800_000L, endMs = null)
        val histogram = computeSleepHistogram(listOf(open), SleepStatsPeriod.LAST_7_DAYS, SleepLens.COMBINED, now, UTC)
        assertTrue(histogram.hourlyPercent.all { it == 0f })
    }
```

Add the small local constant this test file needs, right after the `UTC` constant near the top of the file:

```kotlin
private const val DAYS_MS_TEST = 86_400_000L
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.babydatalog.app.utils.SleepStatsTest"`
Expected: FAIL — `Unresolved reference: computeSleepHistogram` / `SleepLens`.

- [ ] **Step 3: Implement**

Append to `android/app/src/main/java/com/babydatalog/app/utils/SleepStats.kt`:

```kotlin

enum class SleepLens { NAP, NIGHT, COMBINED }

/** hourlyPercent has 24 entries, index = local hour-of-day (0..23), value 0..100. */
data class SleepHistogram(val hourlyPercent: List<Float>)

/**
 * For each local hour-of-day, the percentage of days in the period where the
 * baby was recorded asleep (for the given lens) at any point during that hour
 * — binary presence, not duration-weighted (spec §5.3). Day-split per §5.1
 * first, so an overnight session's hours land on the correct calendar day.
 */
fun computeSleepHistogram(
    sessions: List<SleepSession>,
    period: SleepStatsPeriod,
    lens: SleepLens,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): SleepHistogram {
    val closed = sessions.filter { it.endTimeMs != null }
    val startMs = periodStartMs(period, closed, nowMs, zone)

    val slices = closed.flatMap { splitAcrossDays(it, zone) }
        .filter { it.startMs < nowMs && it.endMs > startMs }
        .filter {
            when (lens) {
                SleepLens.COMBINED -> true
                SleepLens.NAP -> it.type == SleepType.NAP
                SleepLens.NIGHT -> it.type == SleepType.NIGHT
            }
        }

    // date -> set of local hours (0..23) the baby was asleep at any point that day
    val hoursByDay = HashMap<LocalDate, MutableSet<Int>>()
    for (slice in slices) {
        var cursorMs = slice.startMs
        while (cursorMs < slice.endMs) {
            val zdt = Instant.ofEpochMilli(cursorMs).atZone(zone)
            hoursByDay.getOrPut(slice.date) { mutableSetOf() }.add(zdt.hour)
            val nextHourMs = zdt.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
            cursorMs = minOf(slice.endMs, nextHourMs)
        }
    }

    val totalDays = ((nowMs - startMs) / DAY_MS.toDouble()).coerceAtLeast(1.0)
    val counts = IntArray(24)
    for (hours in hoursByDay.values) {
        for (h in hours) counts[h]++
    }
    return SleepHistogram((0..23).map { h -> (counts[h] / totalDays * 100.0).toFloat() })
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.babydatalog.app.utils.SleepStatsTest"`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/utils/SleepStats.kt \
        android/app/src/test/java/com/babydatalog/app/utils/SleepStatsTest.kt
git commit -m "feat(sleep): add hourly sleep histogram computation"
```

---

## Task 5: `SleepRepository`

*Implements spec §2.3.*

**Files:**
- Create: `android/app/src/main/java/com/babydatalog/app/data/repository/SleepRepository.kt`

**Interfaces:**
- Consumes: `SleepDao` (Task 1), `BabyDao.getBabyByIdOnce` (existing), `SyncScheduler.requestSyncSoon()` (existing), `syncUuidFor`/`floorToMinute` (existing, `SyncUuidUtils.kt`), `defaultSleepTypeForStart` (Task 2, used by callers — not by the repository itself, which takes `type` as a parameter per spec §2.3).
- Produces: `SleepRepository` with `getSleepSessionsForBaby(babyId): Flow<List<SleepSession>>`, `getSleepById(id): Flow<SleepSession?>`, `getOpenSleepSession(babyId): Flow<SleepSession?>`, `startSleep(babyId: Long, type: SleepType, startTimeMs: Long = now): SleepSession`, `endSleep(session: SleepSession, endTimeMs: Long = now)`, `upsertSleep(session: SleepSession): SleepSession`, `deleteSleep(session: SleepSession)` — Tasks 6, 8, 10 depend on these exact names.

No dedicated automated test for this task: this codebase has no test file for any of the four existing repositories (`FeedingRepository`, `NappyRepository`, `GrowthRepository`, `MilestoneRepository` are all untested — their real logic, e.g. `FeedingRepository.upsertFeeding`'s natural-key `syncUuid` generation, is exercised only through the DAO tests plus manual/UI use). `SleepRepository` follows the same convention; Task 1's `SleepDaoTest` already covers the DAO layer it wraps, and Task 15's end-to-end manual pass exercises this repository's logic through the real app.

- [ ] **Step 1: Implement**

Create `android/app/src/main/java/com/babydatalog/app/data/repository/SleepRepository.kt`:

```kotlin
package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.SleepDao
import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import com.babydatalog.app.data.sync.SyncScheduler
import com.babydatalog.app.utils.floorToMinute
import com.babydatalog.app.utils.syncUuidFor
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepository @Inject constructor(
    private val sleepDao: SleepDao,
    private val babyDao: BabyDao,
    private val syncScheduler: SyncScheduler
) {

    fun getSleepSessionsForBaby(babyId: Long): Flow<List<SleepSession>> =
        sleepDao.getSleepSessionsForBaby(babyId)

    fun getSleepById(id: Long): Flow<SleepSession?> =
        sleepDao.getSleepById(id)

    fun getOpenSleepSession(babyId: Long): Flow<SleepSession?> =
        sleepDao.getOpenSleepSession(babyId)

    private suspend fun newSyncUuid(babyId: Long, startTimeMs: Long): String {
        val babySyncUuid = babyDao.getBabyByIdOnce(babyId)?.syncUuid
        return if (babySyncUuid != null) {
            syncUuidFor("s", babySyncUuid, floorToMinute(startTimeMs))
        } else {
            UUID.randomUUID().toString()
        }
    }

    // Starts a session immediately (endTimeMs = null) so it persists across app
    // restarts and syncs to the other parent's device as "currently asleep"
    // (spec §2.3 / §4.1 — either device can later end it, not just this one).
    suspend fun startSleep(babyId: Long, type: SleepType, startTimeMs: Long = System.currentTimeMillis()): SleepSession {
        val now = System.currentTimeMillis()
        val session = SleepSession(
            syncUuid = newSyncUuid(babyId, startTimeMs),
            babyId = babyId,
            startTimeMs = startTimeMs,
            endTimeMs = null,
            type = type,
            notes = null,
            createdAtMs = now,
            updatedAtMs = now
        )
        val id = sleepDao.insertSleep(session)
        syncScheduler.requestSyncSoon()
        return session.copy(id = id)
    }

    // Ends whatever session is passed in — callable from either device holding
    // the open row, not only the device that started it (spec §2.3).
    suspend fun endSleep(session: SleepSession, endTimeMs: Long = System.currentTimeMillis()) {
        sleepDao.updateSleep(session.copy(endTimeMs = endTimeMs, updatedAtMs = System.currentTimeMillis()))
        syncScheduler.requestSyncSoon()
    }

    // Insert-or-update for the manual entry form — mirrors FeedingRepository.upsertFeeding.
    suspend fun upsertSleep(session: SleepSession): SleepSession {
        val now = System.currentTimeMillis()
        val saved = if (session.id == 0L) {
            val syncUuid = if (session.syncUuid.isBlank()) newSyncUuid(session.babyId, session.startTimeMs) else session.syncUuid
            val toInsert = session.copy(syncUuid = syncUuid, updatedAtMs = now)
            val newId = sleepDao.insertSleep(toInsert)
            toInsert.copy(id = newId)
        } else {
            val toUpdate = session.copy(updatedAtMs = now)
            sleepDao.updateSleep(toUpdate)
            toUpdate
        }
        syncScheduler.requestSyncSoon()
        return saved
    }

    suspend fun deleteSleep(session: SleepSession) {
        val now = System.currentTimeMillis()
        sleepDao.updateSleep(session.copy(deletedAtMs = now, updatedAtMs = now))
        syncScheduler.requestSyncSoon()
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/data/repository/SleepRepository.kt
git commit -m "feat(sleep): add SleepRepository"
```

---

## Task 6: Android sync — `SyncSleep` DTO and `SyncRepository` wiring

*Implements spec §3.3, §3.4.*

**Files:**
- Modify: `android/app/src/main/java/com/babydatalog/app/data/sync/SyncModels.kt`
- Modify: `android/app/src/main/java/com/babydatalog/app/data/sync/SyncRepository.kt`

**Interfaces:**
- Consumes: `SleepSession`, `SleepType` (Task 1), `SleepDao` (Task 1).
- Produces: `SyncSleep` DTO, `SleepSession.toSync()`, `SyncSleep.toEntity()` — no other task in this plan depends on these directly (sync is orthogonal to the UI layer), but they must exist for the app to sync sleep data at all, and Task 15's manual verification exercises them.

No dedicated automated test: this codebase has no test file for `SyncRepository` either (it requires a live server and WorkManager, neither of which any existing test sets up). Verified by compiling and by Task 15's manual sync smoke test.

- [ ] **Step 1: Add the `SyncSleep` DTO to `SyncModels.kt`**

Add the import:

```kotlin
import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
```

Add the DTO (next to `SyncGrowth`):

```kotlin
@Serializable
data class SyncSleep(
    val id: Long, val syncUuid: String, val babyId: Long,
    val startTimeMs: Long, val endTimeMs: Long?, val type: String,
    val notes: String?, val createdAtMs: Long, val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
    val babySyncUuid: String = ""
)
```

Add the entity/DTO converters (next to the `GrowthMeasurement` ones):

```kotlin
fun SleepSession.toSync() = SyncSleep(
    id, syncUuid, babyId, startTimeMs, endTimeMs, type.name,
    notes, createdAtMs, updatedAtMs, deletedAtMs
)
```

```kotlin
fun SyncSleep.toEntity() = SleepSession(
    id, syncUuid, babyId, startTimeMs, endTimeMs,
    SleepType.valueOf(type),
    notes, createdAtMs, updatedAtMs, deletedAtMs
)
```

- [ ] **Step 2: Wire `SleepDao` into `SyncRepository`**

Add the import and constructor parameter:

```kotlin
import com.babydatalog.app.data.database.dao.SleepDao
```

```kotlin
@Singleton
class SyncRepository @Inject constructor(
    private val api: SyncApiClient,
    private val prefs: SyncPreferences,
    private val babyDao: BabyDao,
    private val feedingDao: FeedingDao,
    private val nappyDao: NappyDao,
    private val milestoneDao: MilestoneDao,
    private val growthDao: GrowthDao,
    private val sleepDao: SleepDao
) {
```

- [ ] **Step 3: Add the push block**

In `pushAll`, add a `TablePush` entry after the `growth_measurements` one (inside the `tables = listOf(...)` call):

```kotlin
            TablePush("sleep_sessions", sleepDao.getAllForSync().mapNotNull { s ->
                val uuid = babyUuidMap[s.babyId]
                val dto = s.toSync().copy(babySyncUuid = uuid ?: "")
                if (uuid.isNullOrBlank()) {
                    holdBack("sleep_sessions", s.syncUuid, describeSleep(s.startTimeMs), json.encodeToString(dto))
                } else json.encodeToJsonElement(dto)
            })
```

- [ ] **Step 4: Add the pull block**

In `applyPull`, add after the `growth_measurements` block:

```kotlin
        pull.data["sleep_sessions"]?.jsonArray?.forEach { el ->
            val record = json.decodeFromJsonElement<SyncSleep>(el)
            if (record.babySyncUuid.isBlank()) sawBlankBabyUuid = true
            val byUuid = sleepDao.getByUuid(record.syncUuid)
            val localBabyId = resolveBabyId(record.babySyncUuid, record.babyId) ?: byUuid?.babyId
            if (localBabyId == null) {
                defer("sleep_sessions", record.syncUuid, describeSleep(record.startTimeMs), record.babySyncUuid, el)
                return@forEach
            }
            val entity = record.toEntity().copy(babyId = localBabyId)
            val minute = floorToMinute(record.startTimeMs)
            val existing = byUuid
                ?: sleepDao.getByNaturalKey(localBabyId, minute, minute + MINUTE_MS)
            when {
                existing == null -> sleepDao.insertSleep(entity.copy(id = 0L))
                record.updatedAtMs > existing.updatedAtMs ->
                    sleepDao.updateSleep(entity.copy(id = existing.id))
                existing.syncUuid != record.syncUuid ->
                    sleepDao.updateSleep(existing.copy(syncUuid = record.syncUuid))
            }
        }
```

- [ ] **Step 5: Add the `wipeAndResync` cleanup and `assignDeferredToBaby` branch**

In `wipeAndResync`, add after `growthDao.deleteAll()`:

```kotlin
        sleepDao.deleteAll()
```

In `assignDeferredToBaby`'s `when (record.table)`, add a branch after `"growth_measurements"`:

```kotlin
                "sleep_sessions" -> {
                    val entity = json.decodeFromString<SyncSleep>(record.recordJson)
                        .toEntity().copy(babyId = babyId, updatedAtMs = now)
                    val existing = sleepDao.getByUuid(record.syncUuid)
                    if (existing == null) sleepDao.insertSleep(entity.copy(id = 0L))
                    else sleepDao.updateSleep(entity.copy(id = existing.id))
                }
```

- [ ] **Step 6: Add the description helper**

Next to `describeGrowth`:

```kotlin
    private fun describeSleep(ms: Long) = "Sleep • ${descDateFormat.format(Date(ms))}"
```

- [ ] **Step 7: Verify it compiles**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/data/sync/SyncModels.kt \
        android/app/src/main/java/com/babydatalog/app/data/sync/SyncRepository.kt
git commit -m "feat(sleep): wire sleep_sessions into the Android sync pipeline"
```

---

## Task 7: Web sync — `sleep_sessions` table and route

*Implements spec §3.1, §3.2.*

**Files:**
- Modify: `web/lib/db/schema.ts`
- Modify: `web/app/api/sync/route.ts`

**Interfaces:**
- Produces: a `sleep_sessions` table matching Task 1's Room schema field-for-field, and route support for pushing/pulling it — Task 15's manual sync smoke test depends on this table existing with these exact column names.

No test framework exists in `web/` (`web/package.json` has no test script/dependency) — verified by running the migration script and inspecting the resulting schema directly.

- [ ] **Step 1: Add the table to `runMigrations()`**

In `web/lib/db/schema.ts`, inside the initial `db.exec(...)` block, add after the `growth_measurements` table definition (before the `-- Sync tracking` comment):

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
```

- [ ] **Step 2: Add it to the `DATA_TABLES` backfill loop**

Update the `DATA_TABLES` array (the loop that adds `updatedAtMs`/`deletedAtMs` to pre-existing installs — a no-op on this brand-new table, kept for uniformity with the other five):

```typescript
  const DATA_TABLES = [
    "babies", "feeding_sessions", "nappy_changes", "milestones", "growth_measurements", "sleep_sessions",
  ] as const;
```

- [ ] **Step 3: Add the index**

In the final `db.exec(...)` index block at the bottom of `runMigrations()`, add:

```sql
    CREATE INDEX IF NOT EXISTS idx_sleep_babyId    ON sleep_sessions(babyId);
    CREATE INDEX IF NOT EXISTS idx_sleep_updatedAt ON sleep_sessions(updatedAtMs);
```

- [ ] **Step 4: Register the table in the sync route**

In `web/app/api/sync/route.ts`, add `"sleep_sessions"` to `SYNC_TABLES`:

```typescript
const SYNC_TABLES = [
  "babies",
  "feeding_sessions",
  "nappy_changes",
  "milestones",
  "growth_measurements",
  "sleep_sessions",
] as const;
```

- [ ] **Step 5: Add the `timeCol` branch for natural-key matching**

In `findByNaturalKey`, update the `eventMs` and `timeCol` computation to treat `sleep_sessions` the same as `feeding_sessions` (both key off `startTimeMs`, not `timestampMs`):

```typescript
  const babyId = Number(record.babyId ?? 0);
  const eventMs = Number(
    (table === "feeding_sessions" || table === "sleep_sessions" ? record.startTimeMs : record.timestampMs) ?? 0
  );
  if (!babyId || !eventMs) return undefined;
  const minStart = Math.floor(eventMs / MINUTE_MS) * MINUTE_MS;
  const timeCol = table === "feeding_sessions" || table === "sleep_sessions" ? "startTimeMs" : "timestampMs";
```

No other changes to `route.ts` are needed — `insertRow`, `updateRow`, the `POST` handler's baby-resolution logic, and the `GET` handler's pull loop are all already generic over `SYNC_TABLES`.

- [ ] **Step 6: Verify by running the migration and inspecting the schema**

Run: `cd web && npm run db:migrate`
Expected output: `Migrations complete.`

Then verify the table exists with the right columns:

Run: `cd web && node -e "const db = require('better-sqlite3')('./data/babydatalog.db'); console.log(db.prepare(\"PRAGMA table_info(sleep_sessions)\").all());"`

Expected: prints 10 columns — `id, syncUuid, babyId, startTimeMs, endTimeMs, type, notes, createdAtMs, updatedAtMs, deletedAtMs`.

- [ ] **Step 7: Commit**

```bash
git add web/lib/db/schema.ts web/app/api/sync/route.ts
git commit -m "feat(sleep): add sleep_sessions to the web sync server"
```

---

## Task 8: `SleepViewModel`

*Implements spec §4.1 state management.*

**Files:**
- Create: `android/app/src/main/java/com/babydatalog/app/viewmodel/SleepViewModel.kt`

**Interfaces:**
- Consumes: `SleepRepository` (Task 5), `defaultSleepTypeForStart` (Task 2), `SleepSession`/`SleepType` (Task 1).
- Produces: `SleepFormState`, `SleepViewModel` with `formState: StateFlow<SleepFormState>`, `sleepSessions: StateFlow<List<SleepSession>>`, `currentOpenSession: StateFlow<SleepSession?>`, `setActiveBabyId(id: Long)`, `loadSleep(id: Long)`, `quickStart()`, `quickEnd()`, `updateType(type: SleepType)`, `updateNotes(notes: String)`, `updateStartTime(ms: Long)`, `updateEndTime(ms: Long?)`, `updateStillAsleep(stillAsleep: Boolean)`, `saveSleep()`, `deleteSleep(session: SleepSession)`, `resetForm()` — Task 9 (screens) depends on these exact names.

No dedicated automated test: no `ViewModel` in this codebase has a test file (`FeedingViewModel`, `NappyViewModel`, etc. are all untested — their state-management logic is exercised through the UI). `SleepViewModel` follows the same convention; its one piece of real logic (`defaultSleepTypeForStart`) is already unit-tested in Task 2.

- [ ] **Step 1: Implement**

Create `android/app/src/main/java/com/babydatalog/app/viewmodel/SleepViewModel.kt`:

```kotlin
package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import com.babydatalog.app.data.repository.SleepRepository
import com.babydatalog.app.utils.defaultSleepTypeForStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SleepFormState(
    val id: Long = 0L,
    val syncUuid: String = "",
    val babyId: Long = 0L,
    val startTimeMs: Long = System.currentTimeMillis(),
    val endTimeMs: Long? = null,
    val stillAsleep: Boolean = false,
    val type: SleepType = SleepType.NAP,
    val notes: String = "",
    val createdAtMs: Long = 0L,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val sleepRepository: SleepRepository
) : ViewModel() {

    private val _activeBabyId = MutableStateFlow<Long?>(null)
    private val _formState = MutableStateFlow(SleepFormState())
    val formState: StateFlow<SleepFormState> = _formState.asStateFlow()

    val sleepSessions: StateFlow<List<SleepSession>> = _activeBabyId
        .flatMapLatest { id ->
            if (id != null) sleepRepository.getSleepSessionsForBaby(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentOpenSession: StateFlow<SleepSession?> = _activeBabyId
        .flatMapLatest { id ->
            if (id != null) sleepRepository.getOpenSleepSession(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setActiveBabyId(id: Long) {
        if (_activeBabyId.value != id) {
            _activeBabyId.value = id
            _formState.update { it.copy(babyId = id) }
        }
    }

    fun loadSleep(id: Long) {
        viewModelScope.launch {
            val session = sleepRepository.getSleepById(id).first() ?: return@launch
            _formState.update {
                it.copy(
                    id = session.id,
                    syncUuid = session.syncUuid,
                    babyId = session.babyId,
                    startTimeMs = session.startTimeMs,
                    endTimeMs = session.endTimeMs,
                    stillAsleep = session.endTimeMs == null,
                    type = session.type,
                    notes = session.notes ?: "",
                    createdAtMs = session.createdAtMs,
                    isSaving = false,
                    saveSuccess = false,
                    error = null
                )
            }
        }
    }

    // Primary contextual action — "Went to sleep" (spec §4.1). Persists immediately.
    fun quickStart() {
        val babyId = _activeBabyId.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            sleepRepository.startSleep(babyId, defaultSleepTypeForStart(now), now)
        }
    }

    // Primary contextual action — "Woke up" (spec §4.1). Callable regardless of
    // which device started the open session.
    fun quickEnd() {
        val open = currentOpenSession.value ?: return
        viewModelScope.launch { sleepRepository.endSleep(open) }
    }

    fun updateType(type: SleepType) {
        _formState.update { it.copy(type = type) }
    }

    fun updateNotes(notes: String) {
        _formState.update { it.copy(notes = notes) }
    }

    fun updateStartTime(ms: Long) {
        _formState.update { it.copy(startTimeMs = ms) }
    }

    fun updateEndTime(ms: Long?) {
        _formState.update { it.copy(endTimeMs = ms) }
    }

    fun updateStillAsleep(stillAsleep: Boolean) {
        _formState.update { it.copy(stillAsleep = stillAsleep, endTimeMs = if (stillAsleep) null else it.endTimeMs) }
    }

    fun saveSleep() {
        val state = _formState.value
        val endTime = if (state.stillAsleep) null else state.endTimeMs

        if (endTime != null && endTime <= state.startTimeMs) {
            _formState.update { it.copy(error = "End time must be after start time") }
            return
        }

        _formState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val session = SleepSession(
                    id = state.id,
                    syncUuid = state.syncUuid,
                    babyId = state.babyId,
                    startTimeMs = state.startTimeMs,
                    endTimeMs = endTime,
                    type = state.type,
                    notes = state.notes.ifBlank { null },
                    createdAtMs = if (state.id == 0L) System.currentTimeMillis() else state.createdAtMs
                )
                sleepRepository.upsertSleep(session)
                _formState.update { it.copy(isSaving = false, saveSuccess = true, error = null) }
            } catch (e: Exception) {
                _formState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save sleep session") }
            }
        }
    }

    fun deleteSleep(session: SleepSession) {
        viewModelScope.launch {
            try {
                sleepRepository.deleteSleep(session)
            } catch (e: Exception) {
                _formState.update { it.copy(error = e.message ?: "Failed to delete sleep session") }
            }
        }
    }

    fun resetForm() {
        val babyId = _activeBabyId.value ?: 0L
        val now = System.currentTimeMillis()
        _formState.value = SleepFormState(babyId = babyId, type = defaultSleepTypeForStart(now))
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/viewmodel/SleepViewModel.kt
git commit -m "feat(sleep): add SleepViewModel"
```

---

## Task 9: `SleepListScreen` and `SleepFormScreen`

*Implements spec §4.1.*

**Files:**
- Create: `android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepListScreen.kt`
- Create: `android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepFormScreen.kt`

**Interfaces:**
- Consumes: `SleepViewModel`, `SleepFormState` (Task 8), `SleepSession`/`SleepType` (Task 1), `ConfirmDeleteDialog`, `ToggleChipGroup`, `DateTimePickerRow`, `EmptyStateMessage` (existing shared components), `toDisplayDateTime`, `toDisplayTime`, `formatDuration` (existing `DateUtils.kt`).
- Produces: `@Composable fun SleepListScreen(onNavigateToAdd: () -> Unit, onNavigateToEdit: (Long) -> Unit, onNavigateToStats: () -> Unit, viewModel: SleepViewModel = hiltViewModel())`, `@Composable fun SleepFormScreen(sleepId: Long = 0L, onNavigateBack: () -> Unit, viewModel: SleepViewModel = hiltViewModel())` — Task 11 (nav wiring) depends on these exact signatures.

No automated UI test: no Compose screen in this codebase has one (there's no `androidx.compose.ui.test` dependency in `build.gradle.kts`). Verified manually once Task 11 wires navigation.

- [ ] **Step 1: Implement `SleepListScreen`**

Create `android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepListScreen.kt`:

```kotlin
package com.babydatalog.app.ui.screens.sleep

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import com.babydatalog.app.ui.components.ConfirmDeleteDialog
import com.babydatalog.app.ui.components.EmptyStateMessage
import com.babydatalog.app.utils.formatDuration
import com.babydatalog.app.utils.toDisplayDateTime
import com.babydatalog.app.viewmodel.SleepViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepListScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToStats: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel()
) {
    val sessions by viewModel.sleepSessions.collectAsStateWithLifecycle()
    val openSession by viewModel.currentOpenSession.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<SleepSession?>(null) }

    if (pendingDelete != null) {
        ConfirmDeleteDialog(
            onConfirm = {
                pendingDelete?.let { viewModel.deleteSleep(it) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep") },
                actions = {
                    IconButton(onClick = onNavigateToStats) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Sleep Stats")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add Sleep Session")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SleepToggleCard(
                openSession = openSession,
                onStart = viewModel::quickStart,
                onEnd = viewModel::quickEnd
            )

            if (sessions.isEmpty()) {
                EmptyStateMessage(message = "No sleep sessions recorded yet")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(sessions, key = { it.id }) { session ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) pendingDelete = session
                                false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                        MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.surface,
                                    label = "swipe_bg"
                                )
                                Box(
                                    modifier = Modifier.fillMaxSize().background(color).padding(end = 16.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        ) {
                            SleepListItem(session = session, onClick = { onNavigateToEdit(session.id) })
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SleepToggleCard(
    openSession: SleepSession?,
    onStart: () -> Unit,
    onEnd: () -> Unit
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(openSession?.startTimeMs) {
        val start = openSession?.startTimeMs
        if (start != null) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - start) / 1000L
                delay(1000L)
            }
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (openSession != null) {
                val hours = elapsedSeconds / 3600
                val mins = (elapsedSeconds % 3600) / 60
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Baby is asleep", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (hours > 0) "%dh %02dm".format(hours, mins) else "%dm".format(mins),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onEnd) { Text("Woke Up") }
            } else {
                Text("Baby is awake", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStart) { Text("Went to Sleep") }
            }
        }
    }
}

@Composable
private fun SleepListItem(session: SleepSession, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (session.type == SleepType.NAP) "Nap" else "Night Sleep",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (session.endTimeMs == null) "In progress"
                    else formatDuration((session.endTimeMs - session.startTimeMs) / 60_000f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = session.startTimeMs.toDisplayDateTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 2: Implement `SleepFormScreen`**

Create `android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepFormScreen.kt`:

```kotlin
package com.babydatalog.app.ui.screens.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.data.database.entity.SleepType
import com.babydatalog.app.ui.components.ConfirmDeleteDialog
import com.babydatalog.app.ui.components.DateTimePickerRow
import com.babydatalog.app.ui.components.SectionHeader
import com.babydatalog.app.ui.components.ToggleChipGroup
import com.babydatalog.app.viewmodel.SleepViewModel
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepFormScreen(
    sleepId: Long = 0L,
    onNavigateBack: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(sleepId) {
        if (sleepId > 0L) viewModel.loadSleep(sleepId)
    }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            viewModel.resetForm()
            onNavigateBack()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteSleep(
                    com.babydatalog.app.data.database.entity.SleepSession(
                        id = state.id, syncUuid = state.syncUuid, babyId = state.babyId,
                        startTimeMs = state.startTimeMs, endTimeMs = state.endTimeMs, type = state.type,
                        notes = state.notes.ifBlank { null }, createdAtMs = state.createdAtMs
                    )
                )
                onNavigateBack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    val title = if (sleepId > 0L) "Edit Sleep" else "Add Sleep"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            SectionHeader("Type")
            ToggleChipGroup(
                options = SleepType.entries,
                selected = state.type,
                onSelect = { viewModel.updateType(it) },
                label = { if (it == SleepType.NAP) "Nap" else "Night Sleep" }
            )

            SectionHeader("Start")
            DateTimePickerRow(
                label = "Start Time",
                timestampMs = state.startTimeMs,
                onDateTimeSelected = { viewModel.updateStartTime(it) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.stillAsleep,
                    onCheckedChange = { viewModel.updateStillAsleep(it) }
                )
                Text("Still asleep (no end time yet)")
            }

            if (!state.stillAsleep) {
                SectionHeader("End")
                DateTimePickerRow(
                    label = "End Time",
                    timestampMs = state.endTimeMs ?: state.startTimeMs,
                    onDateTimeSelected = { viewModel.updateEndTime(it) }
                )
            }

            SectionHeader("Notes")
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = { viewModel.saveSleep() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save Sleep Session")
                    }
                }
            }

            if (sleepId > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepListScreen.kt \
        android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepFormScreen.kt
git commit -m "feat(sleep): add SleepListScreen and SleepFormScreen"
```

---

## Task 10: Home screen "currently asleep" card

*Implements spec §4.2.*

**Files:**
- Modify: `android/app/src/main/java/com/babydatalog/app/viewmodel/HomeViewModel.kt`
- Modify: `android/app/src/main/java/com/babydatalog/app/ui/screens/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `SleepRepository` (Task 5), `defaultSleepTypeForStart` (Task 2), `SleepSession` (Task 1).
- Produces: `HomeViewModel.currentSleepSession: StateFlow<SleepSession?>`, `HomeViewModel.quickStartSleep()`, `HomeViewModel.quickEndSleep()` — no other task depends on these (leaf of the dependency graph within Home), but Task 15's manual verification exercises them.

- [ ] **Step 1: Extend `HomeViewModel`**

Add imports:

```kotlin
import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.repository.SleepRepository
import com.babydatalog.app.utils.defaultSleepTypeForStart
import kotlinx.coroutines.launch
```

Add `sleepRepository` to the constructor:

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feedingRepository: FeedingRepository,
    private val nappyRepository: NappyRepository,
    private val sleepRepository: SleepRepository
) : ViewModel() {
```

Add the state flow and actions (after `todayNappyCount`):

```kotlin
    val currentSleepSession: StateFlow<SleepSession?> = _activeBabyId
        .flatMapLatest { id ->
            if (id != null) sleepRepository.getOpenSleepSession(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun quickStartSleep() {
        val babyId = _activeBabyId.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            sleepRepository.startSleep(babyId, defaultSleepTypeForStart(now), now)
        }
    }

    fun quickEndSleep() {
        val open = currentSleepSession.value ?: return
        viewModelScope.launch { sleepRepository.endSleep(open) }
    }
```

- [ ] **Step 2: Add the card to `HomeScreen`**

Add imports:

```kotlin
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
```

Add the state read (after `val latestMeasurement by growthViewModel.latestMeasurement.collectAsStateWithLifecycle()`):

```kotlin
    val currentSleepSession by viewModel.currentSleepSession.collectAsStateWithLifecycle()
```

Add the card as the first `item` inside the `LazyColumn`, right after `item { Spacer(modifier = Modifier.height(4.dp)) }` and before the "Today's stats" `Row` item:

```kotlin
            // Currently-asleep status — new, cross-device live state (spec §4.2)
            item {
                var elapsedSeconds by remember { mutableLongStateOf(0L) }
                LaunchedEffect(currentSleepSession?.startTimeMs) {
                    val start = currentSleepSession?.startTimeMs
                    if (start != null) {
                        while (true) {
                            elapsedSeconds = (System.currentTimeMillis() - start) / 1000L
                            delay(1000L)
                        }
                    }
                }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentSleepSession != null) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Baby is asleep", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                                val hours = elapsedSeconds / 3600
                                val mins = (elapsedSeconds % 3600) / 60
                                Text(
                                    text = if (hours > 0) "%dh %02dm".format(hours, mins) else "%dm".format(mins),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = { viewModel.quickEndSleep() }) { Text("Wake Up") }
                        } else {
                            Text("Baby is awake", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { viewModel.quickStartSleep() }) { Text("Went to Sleep") }
                        }
                    }
                }
            }
```

- [ ] **Step 3: Verify it compiles**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/viewmodel/HomeViewModel.kt \
        android/app/src/main/java/com/babydatalog/app/ui/screens/home/HomeScreen.kt
git commit -m "feat(sleep): show currently-asleep status on Home screen"
```

---

## Task 11: Navigation wiring — Routes, `NavGraph`, bottom nav

*Implements spec §7. End-to-end manual verification of the recording flow happens after this task.*

**Files:**
- Modify: `android/app/src/main/java/com/babydatalog/app/ui/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `SleepListScreen`, `SleepFormScreen` (Task 9), `SleepViewModel` (Task 8).
- Produces: `Routes.SLEEP_LIST`, `Routes.SLEEP_ADD`, `Routes.SLEEP_EDIT`, `Routes.sleepEdit(id: Long)`, `Routes.SLEEP_STATS` — Task 12 depends on `Routes.SLEEP_STATS` existing (it wires the stats screen composable into the route already reserved here).

- [ ] **Step 1: Add route constants**

In the `Routes` object, add after the `GROWTH_EDIT`/`growthEdit` entries:

```kotlin
    const val SLEEP_LIST = "sleep_list"
    const val SLEEP_ADD = "sleep_add"
    const val SLEEP_EDIT = "sleep_edit/{sleepId}"
    const val SLEEP_STATS = "sleep_stats"

    fun sleepEdit(sleepId: Long) = "sleep_edit/$sleepId"
```

- [ ] **Step 2: Add imports**

```kotlin
import androidx.compose.material.icons.filled.Bedtime
import com.babydatalog.app.ui.screens.sleep.SleepFormScreen
import com.babydatalog.app.ui.screens.sleep.SleepListScreen
import com.babydatalog.app.viewmodel.SleepViewModel
```

- [ ] **Step 3: Add the bottom nav item**

In `bottomNavItems`, add after the `GROWTH_LIST` entry:

```kotlin
        BottomNavItem(
            route = Routes.SLEEP_LIST,
            label = "Sleep",
            icon = { Icon(Icons.Filled.Bedtime, contentDescription = "Sleep") }
        ),
```

- [ ] **Step 4: Add the routes to `bottomBarRoutes`**

`SLEEP_LIST` joins the tab roots; `SLEEP_STATS` is a reachable sub-screen (like `SETTINGS`) that keeps the bottom bar visible but isn't its own tab:

```kotlin
    val bottomBarRoutes = setOf(
        Routes.HOME,
        Routes.FEEDING_LIST,
        Routes.NAPPY_LIST,
        Routes.MILESTONE_LIST,
        Routes.GROWTH_LIST,
        Routes.SLEEP_LIST,
        Routes.SUMMARY,
        Routes.SETTINGS,
        Routes.SYNC,
        Routes.SLEEP_STATS
    )
```

- [ ] **Step 5: Add the composable destinations**

Inside `NavHost`, add after the `Routes.GROWTH_EDIT` composable block and before `Routes.SUMMARY`:

```kotlin
            composable(Routes.SLEEP_LIST) {
                val vm: SleepViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                SleepListScreen(
                    onNavigateToAdd = {
                        if (babyUiState.selectedBaby != null) navController.navigate(Routes.SLEEP_ADD)
                        else babyViewModel.startAddBaby()
                    },
                    onNavigateToEdit = { id -> navController.navigate(Routes.sleepEdit(id)) },
                    onNavigateToStats = { navController.navigate(Routes.SLEEP_STATS) }
                )
            }
            composable(Routes.SLEEP_ADD) {
                val vm: SleepViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                SleepFormScreen(
                    sleepId = 0L,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.SLEEP_EDIT,
                arguments = listOf(navArgument("sleepId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sleepId = backStackEntry.arguments?.getLong("sleepId") ?: 0L
                SleepFormScreen(
                    sleepId = sleepId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
```

Task 12 will add the `Routes.SLEEP_STATS` composable block itself, once `SleepStatsScreen` exists.

- [ ] **Step 6: Build and manually verify the recording flow end-to-end**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

Then, with an emulator or device connected (`./gradlew :app:installDebug`), manually verify:
1. A "Sleep" tab with a moon icon appears in the bottom nav.
2. Tapping it shows "Baby is awake" and a "Went to Sleep" button.
3. Tapping "Went to Sleep" flips it to "Baby is asleep" with a live-ticking elapsed timer and a "Wake Up" button — and the same status now shows on the Home tab's card.
4. Tapping "Wake Up" (from either the Sleep tab or Home) ends the session; it now appears in the list below.
5. Tapping the FAB opens the manual-entry form; saving with `type = Night Sleep` and explicit start/end times creates a second list entry.
6. Force-closing and reopening the app while a session is open (step 3) still shows "Baby is asleep" — confirms it persisted, not just in-memory state.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/ui/navigation/NavGraph.kt
git commit -m "feat(sleep): wire Sleep tab into navigation"
```

---

## Task 12: `SleepStatsScreen` — numeric stats

*Implements spec §5.5 (numeric portion) and §5.4.*

**Files:**
- Create: `android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepStatsScreen.kt`
- Create: `android/app/src/main/java/com/babydatalog/app/viewmodel/SleepStatsViewModel.kt`
- Modify: `android/app/src/main/java/com/babydatalog/app/ui/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `SleepRepository` (Task 5), `computeSleepStats`, `SleepStats`, `SleepLensStats`, `SleepStatsPeriod` (Task 3), `Routes.SLEEP_STATS` (Task 11).
- Produces: `SleepStatsViewModel` with `stats: StateFlow<SleepStats>`, `period: StateFlow<SleepStatsPeriod>`, `setActiveBabyId(id: Long)`, `selectPeriod(period: SleepStatsPeriod)`; `@Composable fun SleepStatsScreen(viewModel: SleepStatsViewModel = hiltViewModel())` — Task 13 extends this same screen and viewmodel with the histogram.

- [ ] **Step 1: Implement `SleepStatsViewModel`**

Create `android/app/src/main/java/com/babydatalog/app/viewmodel/SleepStatsViewModel.kt`:

```kotlin
package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.repository.SleepRepository
import com.babydatalog.app.utils.SleepStats
import com.babydatalog.app.utils.SleepStatsPeriod
import com.babydatalog.app.utils.computeSleepStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SleepStatsViewModel @Inject constructor(
    private val sleepRepository: SleepRepository
) : ViewModel() {

    private val _activeBabyId = MutableStateFlow<Long?>(null)
    private val _period = MutableStateFlow(SleepStatsPeriod.LAST_7_DAYS)
    val period: StateFlow<SleepStatsPeriod> = _period

    val stats: StateFlow<SleepStats> = combine(_activeBabyId, _period) { id, period -> id to period }
        .flatMapLatest { (id, period) ->
            if (id == null) flowOf(computeSleepStats(emptyList(), period))
            else sleepRepository.getSleepSessionsForBaby(id).map { sessions -> computeSleepStats(sessions, period) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeSleepStats(emptyList(), SleepStatsPeriod.LAST_7_DAYS))

    fun setActiveBabyId(id: Long) {
        if (_activeBabyId.value != id) _activeBabyId.value = id
    }

    fun selectPeriod(period: SleepStatsPeriod) {
        _period.value = period
    }
}
```

- [ ] **Step 2: Implement `SleepStatsScreen`**

Create `android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepStatsScreen.kt`:

```kotlin
package com.babydatalog.app.ui.screens.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.utils.SleepLensStats
import com.babydatalog.app.utils.SleepStatsPeriod
import com.babydatalog.app.utils.formatDuration
import com.babydatalog.app.viewmodel.SleepStatsViewModel

private fun periodLabel(period: SleepStatsPeriod): String = when (period) {
    SleepStatsPeriod.LAST_7_DAYS -> "Last 7 Days"
    SleepStatsPeriod.LAST_30_DAYS -> "Last 30 Days"
    SleepStatsPeriod.LAST_365_DAYS -> "Last Year"
    SleepStatsPeriod.ALL_TIME -> "All Time"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepStatsScreen(
    viewModel: SleepStatsViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sleep Stats") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepStatsPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = period == p,
                        onClick = { viewModel.selectPeriod(p) },
                        label = { Text(periodLabel(p)) }
                    )
                }
            }

            SleepLensCard(title = "Naps", stats = stats.nap)
            SleepLensCard(title = "Night Sleep", stats = stats.night)
            SleepLensCard(title = "Total (Naps + Night)", stats = stats.combined)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SleepLensCard(title: String, stats: SleepLensStats) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            if (stats.sessionCount != null) {
                SleepStatRow("Sessions", "${stats.sessionCount}")
                SleepStatRow("Sessions / Day", "%.1f".format(stats.avgSessionsPerDay ?: 0f))
                stats.avgSessionDurationMinutes?.let { SleepStatRow("Avg Length", formatDuration(it)) }
            }
            SleepStatRow("Total Time", formatDuration(stats.totalMinutes))
            SleepStatRow("Avg / Day", formatDuration(stats.avgMinutesPerDay))
        }
    }
}

@Composable
private fun SleepStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
```

- [ ] **Step 3: Wire the route in `NavGraph.kt`**

Add the import:

```kotlin
import com.babydatalog.app.ui.screens.sleep.SleepStatsScreen
```

Add the composable, after the `Routes.SLEEP_EDIT` block:

```kotlin
            composable(Routes.SLEEP_STATS) {
                val vm: com.babydatalog.app.viewmodel.SleepStatsViewModel = hiltViewModel()
                LaunchedEffect(babyUiState.selectedBaby?.id) {
                    babyUiState.selectedBaby?.id?.let { vm.setActiveBabyId(it) }
                }
                SleepStatsScreen()
            }
```

- [ ] **Step 4: Build and manually verify**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

Manually: from the Sleep tab, tap the bar-chart icon in the top bar; confirm three cards (Naps / Night Sleep / Total) render with numbers that update when switching the Last 7 Days / Last 30 Days / Last Year / All Time chips, and match what you'd expect from the sessions logged in Task 11's verification pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/viewmodel/SleepStatsViewModel.kt \
        android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepStatsScreen.kt \
        android/app/src/main/java/com/babydatalog/app/ui/navigation/NavGraph.kt
git commit -m "feat(sleep): add SleepStatsScreen numeric stats"
```

---

## Task 13: `SleepStatsScreen` — hourly histogram

*Implements spec §5.3, §5.5 (histogram portion).*

**Files:**
- Modify: `android/app/src/main/java/com/babydatalog/app/viewmodel/SleepStatsViewModel.kt`
- Modify: `android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepStatsScreen.kt`

**Interfaces:**
- Consumes: `computeSleepHistogram`, `SleepHistogram`, `SleepLens` (Task 4).
- Produces: `SleepStatsViewModel.lens: StateFlow<SleepLens>`, `SleepStatsViewModel.histogram: StateFlow<SleepHistogram>`, `SleepStatsViewModel.selectLens(lens: SleepLens)`.

- [ ] **Step 1: Extend `SleepStatsViewModel`**

Add imports:

```kotlin
import com.babydatalog.app.utils.SleepHistogram
import com.babydatalog.app.utils.SleepLens
import com.babydatalog.app.utils.computeSleepHistogram
```

Add the lens state and histogram flow (after `stats`):

```kotlin
    private val _lens = MutableStateFlow(SleepLens.COMBINED)
    val lens: StateFlow<SleepLens> = _lens

    val histogram: StateFlow<SleepHistogram> = combine(_activeBabyId, _period, _lens) { id, period, lens -> Triple(id, period, lens) }
        .flatMapLatest { (id, period, lens) ->
            if (id == null) flowOf(computeSleepHistogram(emptyList(), period, lens))
            else sleepRepository.getSleepSessionsForBaby(id).map { sessions -> computeSleepHistogram(sessions, period, lens) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeSleepHistogram(emptyList(), SleepStatsPeriod.LAST_7_DAYS, SleepLens.COMBINED))

    fun selectLens(lens: SleepLens) {
        _lens.value = lens
    }
```

(`combine` with 3 flows needs the `kotlinx.coroutines.flow.combine` overload that takes a lambda over a `Triple` — this is already imported via the existing `combine` import used by `stats`.)

- [ ] **Step 2: Add the histogram chart to `SleepStatsScreen`**

Add imports:

```kotlin
import androidx.compose.runtime.remember
import com.babydatalog.app.utils.SleepHistogram
import com.babydatalog.app.utils.SleepLens
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
```

Read the new state in `SleepStatsScreen` (alongside `stats`/`period`):

```kotlin
    val lens by viewModel.lens.collectAsStateWithLifecycle()
    val histogram by viewModel.histogram.collectAsStateWithLifecycle()
```

Add the histogram section, after the three `SleepLensCard` calls and before the trailing `Spacer`:

```kotlin
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sleep by Hour of Day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SleepLens.entries.forEach { l ->
                            FilterChip(
                                selected = lens == l,
                                onClick = { viewModel.selectLens(l) },
                                label = {
                                    Text(
                                        when (l) {
                                            SleepLens.NAP -> "Nap"
                                            SleepLens.NIGHT -> "Night"
                                            SleepLens.COMBINED -> "Combined"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SleepHistogramChart(histogram = histogram)
                }
            }
```

Add the chart composable at the bottom of the file:

```kotlin
@Composable
private fun SleepHistogramChart(histogram: SleepHistogram) {
    val entries = remember(histogram) {
        histogram.hourlyPercent.mapIndexed { hour, pct -> entryOf(hour.toFloat(), pct) }
    }
    val producer = remember(histogram) { ChartEntryModelProducer(entries) }

    ProvideChartStyle(m3ChartStyle()) {
        Chart(
            chart = columnChart(),
            chartModelProducer = producer,
            startAxis = rememberStartAxis(
                itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 5),
                valueFormatter = { value, _ -> "${value.toInt()}%" }
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _ -> if (value.toInt() % 2 == 0) "${value.toInt()}h" else "" }
            ),
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
    }
}
```

- [ ] **Step 3: Build and manually verify**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

Manually: on Sleep Stats, confirm a 24-bar chart renders below the three cards, the Nap/Night/Combined chips switch which bars are shown, and bars line up with the hours you logged test sessions for in Task 11/12's verification.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/viewmodel/SleepStatsViewModel.kt \
        android/app/src/main/java/com/babydatalog/app/ui/screens/sleep/SleepStatsScreen.kt
git commit -m "feat(sleep): add hourly sleep histogram chart"
```

---

## Task 14: Summary screen integration

*Implements spec §6.*

**Files:**
- Modify: `android/app/src/main/java/com/babydatalog/app/viewmodel/SummaryViewModel.kt`
- Modify: `android/app/src/main/java/com/babydatalog/app/ui/screens/summary/SummaryScreen.kt`

**Interfaces:**
- Consumes: `SleepRepository` (Task 5), `splitAcrossDays` (Task 2), `SleepSession` (Task 1).
- Produces: `SleepSummaryStats(sessionCount: Int, totalSleepMinutes: Float)`, `SummaryViewModel.sleepStats: StateFlow<SleepSummaryStats>`.

- [ ] **Step 1: Extend `SummaryViewModel`**

Add imports:

```kotlin
import com.babydatalog.app.data.repository.SleepRepository
import com.babydatalog.app.utils.splitAcrossDays
```

Add `sleepRepository` to the constructor:

```kotlin
@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val feedingRepository: FeedingRepository,
    private val nappyRepository: NappyRepository,
    private val growthRepository: GrowthRepository,
    private val sleepRepository: SleepRepository
) : ViewModel() {
```

Add the data class and state flow (after the `weightStats` block, before `setActiveBabyId`):

```kotlin
    // Combined (Nap + Night) totals for the currently-selected SummaryPeriod — the
    // same Today/This Week/This Month tabs the Feeding/Nappy cards above use,
    // not the dedicated Sleep section's own Last-7/30/365-days/All-time filter
    // (spec §6 — this card sits alongside those existing period-scoped numbers).
    data class SleepSummaryStats(val sessionCount: Int = 0, val totalSleepMinutes: Float = 0f)

    val sleepStats: StateFlow<SleepSummaryStats> = combine(_defaultBabyId, _selectedPeriod) { babyId, period -> babyId to period }
        .flatMapLatest { (babyId, period) ->
            if (babyId == null) return@flatMapLatest flowOf(SleepSummaryStats())
            val (startMs, endMs) = periodRange(period)
            sleepRepository.getSleepSessionsForBaby(babyId).map { sessions ->
                val slices = sessions
                    .filter { it.endTimeMs != null }
                    .flatMap { splitAcrossDays(it) }
                    .filter { it.startMs < endMs && it.endMs > startMs }
                SleepSummaryStats(
                    sessionCount = slices.size,
                    totalSleepMinutes = slices.sumOf { (it.endMs - it.startMs) / 60_000.0 }.toFloat()
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SleepSummaryStats())
```

Note: `SleepSummaryStats` is declared as a nested class here rather than in `utils/SleepStats.kt` because it's Summary-screen-specific shaping (bound to the existing `SummaryPeriod`, not `SleepStatsPeriod`) — it has no reuse outside this ViewModel, matching how `SummaryStats` itself is declared directly in this file rather than in a shared utils file.

- [ ] **Step 2: Add the condensed card to `SummaryScreen`**

Add the import:

```kotlin
import com.babydatalog.app.viewmodel.SummaryViewModel.SleepSummaryStats
```

Read the new state (alongside `weightStats`):

```kotlin
    val sleepStats by viewModel.sleepStats.collectAsStateWithLifecycle()
```

Add the card immediately after `WeightGrowthCard(weightStats)`:

```kotlin
            // Sleep summary — condensed; full stats + histogram live on the dedicated Sleep tab
            SleepSummaryCard(sleepStats)
```

Add the composable, next to `WeightGrowthCard`:

```kotlin
@Composable
private fun SleepSummaryCard(sleepStats: SleepSummaryStats) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sleep",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatRow(label = "Sessions", value = "${sleepStats.sessionCount}")
            StatRow(label = "Total Time Asleep", value = formatDuration(sleepStats.totalSleepMinutes))
        }
    }
}
```

- [ ] **Step 3: Build and manually verify**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

Manually: open Summary, confirm a "Sleep" card appears directly below "Weight Growth" showing session count and total time asleep, and that both numbers change correctly when switching Summary's Today/This Week/This Month chips.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/babydatalog/app/viewmodel/SummaryViewModel.kt \
        android/app/src/main/java/com/babydatalog/app/ui/screens/summary/SummaryScreen.kt
git commit -m "feat(sleep): add condensed sleep card to Summary screen"
```

---

## Task 15: End-to-end verification

*Confirms the full spec is met and nothing beyond its explicit Out-of-scope (§8) was built.*

**Files:** none (verification only — code changes only if this step uncovers a bug, in which case fix it in the relevant existing file and note it in the commit message).

- [ ] **Step 1: Full Android test suite**

Run: `cd android && ./gradlew test connectedAndroidTest`
Expected: BUILD SUCCESSFUL — all unit tests (including `SleepStatsTest`) and instrumented tests (including `SleepDaoTest`, alongside the pre-existing `BabyDaoTest`/`FeedingDaoTest`/`NappyDaoTest`) pass.

- [ ] **Step 2: Web build**

Run: `cd web && npm run build`
Expected: build succeeds with no TypeScript errors in `schema.ts` / `route.ts`.

- [ ] **Step 3: Manual cross-device sync smoke test**

1. Start the web sync server locally: `cd web && npm run dev`.
2. On the Android app (emulator or device), go to Settings → Sync, register/pair the device against the local server, following the same flow used for any other data type.
3. Log a sleep session (Task 11's flow), then trigger a manual sync from the Sync screen.
4. Verify the row landed on the server: `cd web && node -e "const db = require('better-sqlite3')('./data/babydatalog.db'); console.log(db.prepare('SELECT id, syncUuid, babyId, startTimeMs, endTimeMs, type FROM sleep_sessions').all());"` — expect one row matching what was logged.
5. Wipe local app data (or use a second emulator/device paired to the same server) and sync again — verify the sleep session pulls back down correctly, including its `type`.

- [ ] **Step 4: Confirm scope boundaries from spec §8 were respected**

Walk the checklist:
- Logging a sleep session under 1 minute long is not blocked or auto-filtered — it just saves (matches every other entry type).
- No push-notification/instant-sync special-casing exists for the "currently asleep" status — it relies on the same debounced + periodic sync as everything else.
- `SummaryPeriod`'s Today/This Week/This Month semantics and the existing Feeding/Nappy charts on Summary are unchanged (only the new Sleep card was added).
- An in-progress (open) session never appears in `SleepStats`/`SleepHistogram`/`SleepSummaryStats` numbers — only the Home/Sleep-tab live cards show it.

- [ ] **Step 5: If any step above surfaced a bug, fix it and commit**

```bash
git add -A
git commit -m "fix(sleep): <describe the specific fix>"
```

(Skip this step entirely if verification passed cleanly — do not create an empty commit.)

---

## Self-Review

**Spec coverage:** §2.1/§2.2/§2.4 → Task 1. §2.3 → Task 5. §3.1–§3.4 → Tasks 6–7. §4.1 → Tasks 8–9, 11. §4.2 → Task 10. §4.3 → Task 2 (`defaultSleepTypeForStart`), consumed in Tasks 5/8/10. §5.1 → Task 2 (`splitAcrossDays`). §5.2 → Task 3. §5.3 → Task 4. §5.4 → Task 2 (`periodStartMs`)/Task 3/Task 4/Task 12. §5.5 → Tasks 12–13. §6 → Task 14. §7 → Task 11. §8 (out of scope) → verified explicitly in Task 15.

**Placeholder scan:** every step above contains real Kotlin/TypeScript, not descriptions of code; every test has real assertions; every "Run" step has a real, copy-pasteable command.

**Type consistency:** `SleepSession`/`SleepType` (Task 1) are used identically in Tasks 2–14. `SleepStatsPeriod`/`DaySlice`/`splitAcrossDays`/`periodStartMs`/`defaultSleepTypeForStart` (Task 2) feed Tasks 3, 4, 5, 8, 10, 14 with matching signatures. `SleepStats`/`SleepLensStats`/`computeSleepStats` (Task 3) and `SleepLens`/`SleepHistogram`/`computeSleepHistogram` (Task 4) are consumed unchanged by Tasks 12–13. `SleepRepository`'s method names (`startSleep`, `endSleep`, `upsertSleep`, `deleteSleep`, `getSleepSessionsForBaby`, `getSleepById`, `getOpenSleepSession`) from Task 5 are used identically by Tasks 6 (indirectly, via `SleepDao` which `SleepRepository` wraps), 8, 10, 12, 14. `Routes.SLEEP_STATS` is reserved in Task 11 and filled in by Task 12, as called out explicitly in both tasks.
