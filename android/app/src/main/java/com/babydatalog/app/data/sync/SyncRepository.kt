package com.babydatalog.app.data.sync

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.FeedingDao
import com.babydatalog.app.data.database.dao.GrowthDao
import com.babydatalog.app.data.database.dao.MilestoneDao
import com.babydatalog.app.data.database.dao.NappyDao
import com.babydatalog.app.data.database.entity.Baby
import com.babydatalog.app.utils.floorToDay
import com.babydatalog.app.utils.floorToMinute
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncResult {
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
}

private const val MINUTE_MS = 60_000L
private const val DAY_MS = 86_400_000L

@Singleton
class SyncRepository @Inject constructor(
    private val api: SyncApiClient,
    private val prefs: SyncPreferences,
    private val babyDao: BabyDao,
    private val feedingDao: FeedingDao,
    private val nappyDao: NappyDao,
    private val milestoneDao: MilestoneDao,
    private val growthDao: GrowthDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    val isConnected: Boolean get() = prefs.apiKey != null

    suspend fun registerDevice(serverUrl: String, deviceName: String): SyncResult {
        val deviceId = UUID.randomUUID().toString()
        val pairingCode = generatePairingCode()
        val result = api.registerDevice(serverUrl, deviceId, deviceName, pairingCode)
        return if (result.error != null) {
            SyncResult.Error(result.error)
        } else {
            prefs.serverUrl = serverUrl
            prefs.deviceId = deviceId
            prefs.deviceName = deviceName
            prefs.pairingCode = pairingCode
            prefs.apiKey = null
            prefs.lastSyncMs = 0L
            SyncResult.Success
        }
    }

    suspend fun pollApproval(): PollResponse? {
        val serverUrl = prefs.serverUrl ?: return null
        val deviceId = prefs.deviceId ?: return null
        val pairingCode = prefs.pairingCode ?: return null
        val result = api.pollStatus(serverUrl, deviceId, pairingCode)
        if (result.error != null) return PollResponse("pending")
        if (result.data?.status == "approved" && result.data.apiKey != null) {
            prefs.apiKey = result.data.apiKey
        }
        return result.data
    }

    suspend fun sync(): SyncResult {
        val serverUrl = prefs.serverUrl ?: return SyncResult.Error("Not connected to a server")
        val deviceId = prefs.deviceId ?: return SyncResult.Error("No device ID")
        val apiKey = prefs.apiKey ?: return SyncResult.Error("Device not yet approved")

        val push = pushAll(serverUrl, apiKey, deviceId)
        if (push.error != null) return SyncResult.Error(push.error)

        val pullResult = api.pull(serverUrl, apiKey, prefs.lastSyncMs)
        if (pullResult.error != null) return SyncResult.Error("Pull failed: ${pullResult.error}")
        val pull = pullResult.data ?: return SyncResult.Error("Pull failed: empty response")

        val pullDeferred = try {
            applyPull(pull)
        } catch (e: Exception) {
            return SyncResult.Error("Pull apply failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        val deferred = push.heldBack + pullDeferred
        saveDeferred(deferred)

        if (deferred.isNotEmpty()) {
            // Don't advance lastSyncMs: the affected records stay in the next
            // pull window so they can be retried once their baby exists.
            return SyncResult.Error(
                "Sync completed with ${deferred.size} record(s) deferred — see the list below to fix them"
            )
        }

        // Use the server's clock so pull windows aren't shifted by phone clock skew.
        prefs.lastSyncMs = pull.syncedAtMs
        return SyncResult.Success
    }

    suspend fun wipeAndResync(): SyncResult {
        babyDao.deleteAll()
        feedingDao.deleteAll()
        nappyDao.deleteAll()
        milestoneDao.deleteAll()
        growthDao.deleteAll()
        prefs.lastSyncMs = 0L
        prefs.deferredRecordsJson = null
        return sync()
    }

    fun disconnect() = prefs.clear()

    private data class PushOutcome(val error: String?, val heldBack: List<DeferredRecord> = emptyList())

    private suspend fun pushAll(serverUrl: String, apiKey: String, deviceId: String): PushOutcome {
        data class TablePush(val name: String, val records: List<JsonElement>)

        // Build id→syncUuid map so child records can include babySyncUuid.
        // The server uses babySyncUuid to resolve the correct server-side babyId,
        // since local auto-increment IDs differ between devices. Records whose
        // baby can't be resolved are held back rather than pushed with a blank
        // uuid, which the server would have to reject.
        val babyUuidMap = babyDao.getAllForSync().associate { it.id to it.syncUuid }
        val heldBack = mutableListOf<DeferredRecord>()

        fun holdBack(table: String, syncUuid: String, description: String, recordJson: String): Nothing? {
            heldBack += DeferredRecord(
                table = table,
                syncUuid = syncUuid,
                description = description,
                reason = "Can't push: this record's baby has no sync identity on this device",
                recordJson = recordJson
            )
            return null
        }

        val tables = listOf(
            TablePush("babies", babyDao.getAllForSync().map { json.encodeToJsonElement(it.toSync()) }),
            TablePush("feeding_sessions", feedingDao.getAllForSync().mapNotNull { f ->
                val uuid = babyUuidMap[f.babyId]
                val dto = f.toSync().copy(babySyncUuid = uuid ?: "")
                if (uuid.isNullOrBlank()) {
                    holdBack("feeding_sessions", f.syncUuid, describeFeeding(f.startTimeMs), json.encodeToString(dto))
                } else json.encodeToJsonElement(dto)
            }),
            TablePush("nappy_changes", nappyDao.getAllForSync().mapNotNull { n ->
                val uuid = babyUuidMap[n.babyId]
                val dto = n.toSync().copy(babySyncUuid = uuid ?: "")
                if (uuid.isNullOrBlank()) {
                    holdBack("nappy_changes", n.syncUuid, describeNappy(n.type.name, n.timestampMs), json.encodeToString(dto))
                } else json.encodeToJsonElement(dto)
            }),
            TablePush("milestones", milestoneDao.getAllForSync().mapNotNull { m ->
                val uuid = babyUuidMap[m.babyId]
                val dto = m.toSync().copy(babySyncUuid = uuid ?: "")
                if (uuid.isNullOrBlank()) {
                    holdBack("milestones", m.syncUuid, describeMilestone(m.title, m.timestampMs), json.encodeToString(dto))
                } else json.encodeToJsonElement(dto)
            }),
            TablePush("growth_measurements", growthDao.getAllForSync().mapNotNull { g ->
                val uuid = babyUuidMap[g.babyId]
                val dto = g.toSync().copy(babySyncUuid = uuid ?: "")
                if (uuid.isNullOrBlank()) {
                    holdBack("growth_measurements", g.syncUuid, describeGrowth(g.timestampMs), json.encodeToString(dto))
                } else json.encodeToJsonElement(dto)
            })
        )
        for (table in tables) {
            if (table.records.isEmpty()) continue
            val r = api.push(serverUrl, apiKey, deviceId, table.name, JsonArray(table.records))
            if (r.error != null) return PushOutcome("Failed to push ${table.name}: ${r.error}", heldBack)
        }
        return PushOutcome(error = null, heldBack = heldBack)
    }

    /**
     * Applies a pull from the server (the master database).
     *
     * Records are matched by syncUuid first; if unknown, by natural key
     * (baby + event minute, same scheme as syncUuidFor) so an event logged
     * on two devices under different uuids merges into one row. On a natural
     * key match the local row adopts the server's syncUuid, and data is
     * merged last-write-wins by updatedAtMs.
     *
     * Returns the records that could not be applied (unknown baby).
     */
    private suspend fun applyPull(pull: SyncPullResponse): List<DeferredRecord> {
        val deferred = mutableListOf<DeferredRecord>()

        fun defer(table: String, syncUuid: String, description: String, babyUuid: String, el: JsonElement) {
            deferred += DeferredRecord(
                table = table,
                syncUuid = syncUuid,
                description = description,
                reason = "This record's baby (server id ${babyUuid.take(8)}…) isn't on this device",
                recordJson = el.toString()
            )
        }

        // Babies first so child-record babyId remapping can resolve.
        // Two passes: exact uuid matches claim their local rows first, then
        // natural-key adoption runs only against unclaimed live rows, with
        // live server babies given priority over tombstones. This stops a
        // soft-deleted duplicate on the server from stealing a live local
        // baby's identity (which orphans all of its child records).
        val pulledBabies = pull.data["babies"]?.jsonArray
            ?.map { json.decodeFromJsonElement<SyncBaby>(it) } ?: emptyList()

        val claimedLocalIds = mutableSetOf<Long>()
        val unmatched = mutableListOf<SyncBaby>()

        for (serverBaby in pulledBabies) {
            val existing = babyDao.getByUuid(serverBaby.syncUuid)
            if (existing == null) {
                unmatched += serverBaby
                continue
            }
            claimedLocalIds += existing.id
            if (serverBaby.updatedAtMs > existing.updatedAtMs) {
                babyDao.updateBaby(serverBaby.toEntity().copy(id = existing.id))
            }
        }

        for (serverBaby in unmatched.sortedBy { if (it.deletedAtMs == null) 0 else 1 }) {
            val dayStart = floorToDay(serverBaby.birthDateMs)
            val candidate = babyDao
                .getByNaturalKey(serverBaby.name.trim().lowercase(), dayStart, dayStart + DAY_MS)
                ?.takeIf { it.id !in claimedLocalIds }
            when {
                candidate == null -> babyDao.insertBaby(serverBaby.toEntity().copy(id = 0L))
                serverBaby.updatedAtMs > candidate.updatedAtMs -> {
                    babyDao.updateBaby(serverBaby.toEntity().copy(id = candidate.id))
                    claimedLocalIds += candidate.id
                }
                else -> {
                    // Local data is newer but identity must converge on the
                    // server's uuid; the next push then updates the server row.
                    babyDao.updateBaby(candidate.copy(syncUuid = serverBaby.syncUuid))
                    claimedLocalIds += candidate.id
                }
            }
        }

        // Resolve a pulled child record's local babyId. Prefers babySyncUuid
        // (server attaches it on pull); falls back to the server-id map for
        // older servers. Returns null when the baby is unknown locally.
        val serverIdMap = pulledBabies.mapNotNull { serverBaby ->
            val local = babyDao.getByUuid(serverBaby.syncUuid) ?: return@mapNotNull null
            serverBaby.id to local.id
        }.toMap()

        suspend fun resolveBabyId(babySyncUuid: String, serverBabyId: Long): Long? {
            if (babySyncUuid.isNotBlank()) {
                babyDao.getByUuid(babySyncUuid)?.let { return it.id }
            }
            return serverIdMap[serverBabyId]
        }

        pull.data["feeding_sessions"]?.jsonArray?.forEach { el ->
            val record = json.decodeFromJsonElement<SyncFeeding>(el)
            val localBabyId = resolveBabyId(record.babySyncUuid, record.babyId)
                ?: run {
                    defer("feeding_sessions", record.syncUuid, describeFeeding(record.startTimeMs), record.babySyncUuid, el)
                    return@forEach
                }
            val entity = record.toEntity().copy(babyId = localBabyId)
            val minute = floorToMinute(record.startTimeMs)
            val existing = feedingDao.getByUuid(record.syncUuid)
                ?: feedingDao.getByNaturalKey(localBabyId, minute, minute + MINUTE_MS)
            when {
                existing == null -> feedingDao.insertFeeding(entity.copy(id = 0L))
                record.updatedAtMs > existing.updatedAtMs ->
                    feedingDao.updateFeeding(entity.copy(id = existing.id))
                existing.syncUuid != record.syncUuid ->
                    feedingDao.updateFeeding(existing.copy(syncUuid = record.syncUuid))
            }
        }

        pull.data["nappy_changes"]?.jsonArray?.forEach { el ->
            val record = json.decodeFromJsonElement<SyncNappy>(el)
            val localBabyId = resolveBabyId(record.babySyncUuid, record.babyId)
                ?: run {
                    defer("nappy_changes", record.syncUuid, describeNappy(record.type, record.timestampMs), record.babySyncUuid, el)
                    return@forEach
                }
            val entity = record.toEntity().copy(babyId = localBabyId)
            val minute = floorToMinute(record.timestampMs)
            val existing = nappyDao.getByUuid(record.syncUuid)
                ?: nappyDao.getByNaturalKey(localBabyId, minute, minute + MINUTE_MS)
            when {
                existing == null -> nappyDao.insertNappy(entity.copy(id = 0L))
                record.updatedAtMs > existing.updatedAtMs ->
                    nappyDao.updateNappy(entity.copy(id = existing.id))
                existing.syncUuid != record.syncUuid ->
                    nappyDao.updateNappy(existing.copy(syncUuid = record.syncUuid))
            }
        }

        pull.data["milestones"]?.jsonArray?.forEach { el ->
            val record = json.decodeFromJsonElement<SyncMilestone>(el)
            val localBabyId = resolveBabyId(record.babySyncUuid, record.babyId)
                ?: run {
                    defer("milestones", record.syncUuid, describeMilestone(record.title, record.timestampMs), record.babySyncUuid, el)
                    return@forEach
                }
            val entity = record.toEntity().copy(babyId = localBabyId)
            val minute = floorToMinute(record.timestampMs)
            val existing = milestoneDao.getByUuid(record.syncUuid)
                ?: milestoneDao.getByNaturalKey(
                    localBabyId, minute, minute + MINUTE_MS, record.title.trim().lowercase()
                )
            when {
                existing == null -> milestoneDao.insertMilestone(entity.copy(id = 0L))
                record.updatedAtMs > existing.updatedAtMs ->
                    milestoneDao.updateMilestone(entity.copy(id = existing.id))
                existing.syncUuid != record.syncUuid ->
                    milestoneDao.updateMilestone(existing.copy(syncUuid = record.syncUuid))
            }
        }

        pull.data["growth_measurements"]?.jsonArray?.forEach { el ->
            val record = json.decodeFromJsonElement<SyncGrowth>(el)
            val localBabyId = resolveBabyId(record.babySyncUuid, record.babyId)
                ?: run {
                    defer("growth_measurements", record.syncUuid, describeGrowth(record.timestampMs), record.babySyncUuid, el)
                    return@forEach
                }
            val entity = record.toEntity().copy(babyId = localBabyId)
            val minute = floorToMinute(record.timestampMs)
            val existing = growthDao.getByUuid(record.syncUuid)
                ?: growthDao.getByNaturalKey(localBabyId, minute, minute + MINUTE_MS)
            when {
                existing == null -> growthDao.insertMeasurement(entity.copy(id = 0L))
                record.updatedAtMs > existing.updatedAtMs ->
                    growthDao.updateMeasurement(entity.copy(id = existing.id))
                existing.syncUuid != record.syncUuid ->
                    growthDao.updateMeasurement(existing.copy(syncUuid = record.syncUuid))
            }
        }

        return deferred
    }

    // --- Deferred record management ---

    fun deferredRecords(): List<DeferredRecord> {
        val raw = prefs.deferredRecordsJson ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveDeferred(records: List<DeferredRecord>) {
        prefs.deferredRecordsJson = if (records.isEmpty()) null else json.encodeToString(records)
    }

    fun dismissDeferred(record: DeferredRecord) {
        saveDeferred(deferredRecords().filterNot { it.syncUuid == record.syncUuid && it.table == record.table })
    }

    suspend fun localBabies(): List<Baby> =
        babyDao.getAllForSync().filter { it.deletedAtMs == null }

    /**
     * Fixes a deferred record by attaching it to the chosen local baby.
     * The record is upserted locally (keeping its server syncUuid) with a
     * fresh updatedAtMs so the next push relinks it on the server too.
     */
    suspend fun assignDeferredToBaby(record: DeferredRecord, babyId: Long): SyncResult {
        val now = System.currentTimeMillis()
        try {
            when (record.table) {
                "feeding_sessions" -> {
                    val entity = json.decodeFromString<SyncFeeding>(record.recordJson)
                        .toEntity().copy(babyId = babyId, updatedAtMs = now)
                    val existing = feedingDao.getByUuid(record.syncUuid)
                    if (existing == null) feedingDao.insertFeeding(entity.copy(id = 0L))
                    else feedingDao.updateFeeding(entity.copy(id = existing.id))
                }
                "nappy_changes" -> {
                    val entity = json.decodeFromString<SyncNappy>(record.recordJson)
                        .toEntity().copy(babyId = babyId, updatedAtMs = now)
                    val existing = nappyDao.getByUuid(record.syncUuid)
                    if (existing == null) nappyDao.insertNappy(entity.copy(id = 0L))
                    else nappyDao.updateNappy(entity.copy(id = existing.id))
                }
                "milestones" -> {
                    val entity = json.decodeFromString<SyncMilestone>(record.recordJson)
                        .toEntity().copy(babyId = babyId, updatedAtMs = now)
                    val existing = milestoneDao.getByUuid(record.syncUuid)
                    if (existing == null) milestoneDao.insertMilestone(entity.copy(id = 0L))
                    else milestoneDao.updateMilestone(entity.copy(id = existing.id))
                }
                "growth_measurements" -> {
                    val entity = json.decodeFromString<SyncGrowth>(record.recordJson)
                        .toEntity().copy(babyId = babyId, updatedAtMs = now)
                    val existing = growthDao.getByUuid(record.syncUuid)
                    if (existing == null) growthDao.insertMeasurement(entity.copy(id = 0L))
                    else growthDao.updateMeasurement(entity.copy(id = existing.id))
                }
                else -> return SyncResult.Error("Unknown table ${record.table}")
            }
        } catch (e: Exception) {
            return SyncResult.Error("Couldn't fix record: ${e.message}")
        }
        dismissDeferred(record)
        return SyncResult.Success
    }

    // --- Description helpers for the deferred list ---

    private val descDateFormat = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
    private fun describeFeeding(ms: Long) = "Feeding • ${descDateFormat.format(Date(ms))}"
    private fun describeNappy(type: String, ms: Long) = "Nappy ($type) • ${descDateFormat.format(Date(ms))}"
    private fun describeMilestone(title: String, ms: Long) = "Milestone \"$title\" • ${descDateFormat.format(Date(ms))}"
    private fun describeGrowth(ms: Long) = "Growth • ${descDateFormat.format(Date(ms))}"

    private fun generatePairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
