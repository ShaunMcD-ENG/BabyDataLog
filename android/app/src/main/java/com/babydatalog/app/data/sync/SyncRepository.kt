package com.babydatalog.app.data.sync

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.FeedingDao
import com.babydatalog.app.data.database.dao.GrowthDao
import com.babydatalog.app.data.database.dao.MilestoneDao
import com.babydatalog.app.data.database.dao.NappyDao
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

        val pushError = pushAll(serverUrl, apiKey, deviceId)
        if (pushError != null) return SyncResult.Error(pushError)

        val pullResult = api.pull(serverUrl, apiKey, prefs.lastSyncMs)
        if (pullResult.error != null) return SyncResult.Error("Pull failed: ${pullResult.error}")

        pullResult.data?.let { applyPull(it) }
        prefs.lastSyncMs = System.currentTimeMillis()
        return SyncResult.Success
    }

    fun disconnect() = prefs.clear()

    private suspend fun pushAll(serverUrl: String, apiKey: String, deviceId: String): String? {
        data class TablePush(val name: String, val records: List<kotlinx.serialization.json.JsonElement>)

        val tables = listOf(
            TablePush("babies", babyDao.getAllForSync().map { json.encodeToJsonElement(it.toSync()) }),
            TablePush("feeding_sessions", feedingDao.getAllForSync().map { json.encodeToJsonElement(it.toSync()) }),
            TablePush("nappy_changes", nappyDao.getAllForSync().map { json.encodeToJsonElement(it.toSync()) }),
            TablePush("milestones", milestoneDao.getAllForSync().map { json.encodeToJsonElement(it.toSync()) }),
            TablePush("growth_measurements", growthDao.getAllForSync().map { json.encodeToJsonElement(it.toSync()) })
        )
        for (table in tables) {
            if (table.records.isEmpty()) continue
            val r = api.push(serverUrl, apiKey, deviceId, table.name, JsonArray(table.records))
            if (r.error != null) return "Failed to push ${table.name}: ${r.error}"
        }
        return null
    }

    private suspend fun applyPull(pull: SyncPullResponse) {
        // Babies first so child-record babyId remapping can resolve
        val pulledBabies = pull.data["babies"]?.jsonArray
            ?.map { json.decodeFromJsonElement<SyncBaby>(it) } ?: emptyList()

        for (serverBaby in pulledBabies) {
            val existing = babyDao.getByUuid(serverBaby.syncUuid)
            if (existing != null) {
                if (serverBaby.updatedAtMs > existing.updatedAtMs) {
                    babyDao.updateBaby(serverBaby.toEntity().copy(id = existing.id))
                }
            } else {
                babyDao.insertBaby(serverBaby.toEntity().copy(id = 0L))
            }
        }

        // Map server baby ids → local baby ids for FK remapping
        val babyIdMap = pulledBabies.mapNotNull { serverBaby ->
            val local = babyDao.getByUuid(serverBaby.syncUuid) ?: return@mapNotNull null
            serverBaby.id to local.id
        }.toMap()

        pull.data["feeding_sessions"]?.jsonArray?.forEach { el ->
            val record = json.decodeFromJsonElement<SyncFeeding>(el)
            val entity = record.toEntity().copy(babyId = babyIdMap[record.babyId] ?: record.babyId)
            val existing = feedingDao.getByUuid(record.syncUuid)
            when {
                existing == null -> feedingDao.insertFeeding(entity.copy(id = 0L))
                record.updatedAtMs > existing.updatedAtMs ->
                    feedingDao.updateFeeding(entity.copy(id = existing.id))
            }
        }

        pull.data["nappy_changes"]?.jsonArray?.forEach { el ->
            val record = json.decodeFromJsonElement<SyncNappy>(el)
            val entity = record.toEntity().copy(babyId = babyIdMap[record.babyId] ?: record.babyId)
            val existing = nappyDao.getByUuid(record.syncUuid)
            when {
                existing == null -> nappyDao.insertNappy(entity.copy(id = 0L))
                record.updatedAtMs > existing.updatedAtMs ->
                    nappyDao.updateNappy(entity.copy(id = existing.id))
            }
        }

        pull.data["milestones"]?.jsonArray?.forEach { el ->
            val record = json.decodeFromJsonElement<SyncMilestone>(el)
            val entity = record.toEntity().copy(babyId = babyIdMap[record.babyId] ?: record.babyId)
            val existing = milestoneDao.getByUuid(record.syncUuid)
            when {
                existing == null -> milestoneDao.insertMilestone(entity.copy(id = 0L))
                record.updatedAtMs > existing.updatedAtMs ->
                    milestoneDao.updateMilestone(entity.copy(id = existing.id))
            }
        }

        pull.data["growth_measurements"]?.jsonArray?.forEach { el ->
            val record = json.decodeFromJsonElement<SyncGrowth>(el)
            val entity = record.toEntity().copy(babyId = babyIdMap[record.babyId] ?: record.babyId)
            val existing = growthDao.getByUuid(record.syncUuid)
            when {
                existing == null -> growthDao.insertMeasurement(entity.copy(id = 0L))
                record.updatedAtMs > existing.updatedAtMs ->
                    growthDao.updateMeasurement(entity.copy(id = existing.id))
            }
        }
    }

    private fun generatePairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
