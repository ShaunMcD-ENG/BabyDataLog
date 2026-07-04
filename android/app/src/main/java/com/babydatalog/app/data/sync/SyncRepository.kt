package com.babydatalog.app.data.sync

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.FeedingDao
import com.babydatalog.app.data.database.dao.GrowthDao
import com.babydatalog.app.data.database.dao.MilestoneDao
import com.babydatalog.app.data.database.dao.NappyDao
import com.babydatalog.app.utils.floorToDay
import com.babydatalog.app.utils.floorToMinute
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
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

        val skipped = try {
            applyPull(pull)
        } catch (e: Exception) {
            return SyncResult.Error("Pull apply failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        if (skipped > 0 || push.heldBack > 0) {
            // Don't advance lastSyncMs: the affected records stay in the next
            // pull window so they can be retried once their baby exists.
            return SyncResult.Error(
                "Sync completed with ${skipped + push.heldBack} record(s) deferred; they will retry next sync"
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
        return sync()
    }

    fun disconnect() = prefs.clear()

    private data class PushOutcome(val error: String?, val heldBack: Int = 0)

    private suspend fun pushAll(serverUrl: String, apiKey: String, deviceId: String): PushOutcome {
        data class TablePush(val name: String, val records: List<kotlinx.serialization.json.JsonElement>)

        // Build id→syncUuid map so child records can include babySyncUuid.
        // The server uses babySyncUuid to resolve the correct server-side babyId,
        // since local auto-increment IDs differ between devices. Records whose
        // baby can't be resolved are held back rather than pushed with a blank
        // uuid, which the server would have to reject.
        val babyUuidMap = babyDao.getAllForSync().associate { it.id to it.syncUuid }
        var heldBack = 0

        fun uuidFor(babyId: Long): String? {
            val uuid = babyUuidMap[babyId]
            if (uuid.isNullOrBlank()) heldBack++
            return if (uuid.isNullOrBlank()) null else uuid
        }

        val tables = listOf(
            TablePush("babies", babyDao.getAllForSync().map { json.encodeToJsonElement(it.toSync()) }),
            TablePush("feeding_sessions", feedingDao.getAllForSync().mapNotNull { f ->
                uuidFor(f.babyId)?.let { json.encodeToJsonElement(f.toSync().copy(babySyncUuid = it)) }
            }),
            TablePush("nappy_changes", nappyDao.getAllForSync().mapNotNull { n ->
                uuidFor(n.babyId)?.let { json.encodeToJsonElement(n.toSync().copy(babySyncUuid = it)) }
            }),
            TablePush("milestones", milestoneDao.getAllForSync().mapNotNull { m ->
                uuidFor(m.babyId)?.let { json.encodeToJsonElement(m.toSync().copy(babySyncUuid = it)) }
            }),
            TablePush("growth_measurements", growthDao.getAllForSync().mapNotNull { g ->
                uuidFor(g.babyId)?.let { json.encodeToJsonElement(g.toSync().copy(babySyncUuid = it)) }
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
     * Returns the number of records that could not be applied (unknown baby).
     */
    private suspend fun applyPull(pull: SyncPullResponse): Int {
        var skipped = 0

        // Babies first so child-record babyId remapping can resolve
        val pulledBabies = pull.data["babies"]?.jsonArray
            ?.map { json.decodeFromJsonElement<SyncBaby>(it) } ?: emptyList()

        for (serverBaby in pulledBabies) {
            val byUuid = babyDao.getByUuid(serverBaby.syncUuid)
            val existing = byUuid ?: run {
                val dayStart = floorToDay(serverBaby.birthDateMs)
                babyDao.getByNaturalKey(serverBaby.name.trim().lowercase(), dayStart, dayStart + DAY_MS)
            }
            when {
                existing == null -> babyDao.insertBaby(serverBaby.toEntity().copy(id = 0L))
                serverBaby.updatedAtMs > existing.updatedAtMs ->
                    babyDao.updateBaby(serverBaby.toEntity().copy(id = existing.id))
                existing.syncUuid != serverBaby.syncUuid ->
                    // Local data is newer but identity must converge on the
                    // server's uuid; the next push then updates the server row.
                    babyDao.updateBaby(existing.copy(syncUuid = serverBaby.syncUuid))
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
                ?: run { skipped++; return@forEach }
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
                ?: run { skipped++; return@forEach }
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
                ?: run { skipped++; return@forEach }
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
                ?: run { skipped++; return@forEach }
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

        return skipped
    }

    private fun generatePairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
