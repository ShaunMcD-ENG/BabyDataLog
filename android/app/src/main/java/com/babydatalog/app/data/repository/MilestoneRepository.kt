package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.MilestoneDao
import com.babydatalog.app.data.database.entity.Milestone
import com.babydatalog.app.data.database.entity.MilestoneCategory
import com.babydatalog.app.utils.floorToMinute
import com.babydatalog.app.utils.syncUuidFor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MilestoneRepository @Inject constructor(
    private val milestoneDao: MilestoneDao,
    private val babyDao: BabyDao
) {

    fun getMilestonesForBaby(babyId: Long): Flow<List<Milestone>> =
        milestoneDao.getMilestonesForBaby(babyId)

    fun getMilestoneById(id: Long): Flow<Milestone?> =
        milestoneDao.getMilestoneById(id)

    fun getMilestonesByCategory(babyId: Long, category: MilestoneCategory): Flow<List<Milestone>> =
        milestoneDao.getMilestonesByCategory(babyId, category)

    suspend fun insertMilestone(milestone: Milestone): Long =
        milestoneDao.insertMilestone(milestone.copy(updatedAtMs = System.currentTimeMillis()))

    suspend fun updateMilestone(milestone: Milestone) =
        milestoneDao.updateMilestone(milestone.copy(updatedAtMs = System.currentTimeMillis()))

    suspend fun deleteMilestone(milestone: Milestone) {
        val now = System.currentTimeMillis()
        milestoneDao.updateMilestone(milestone.copy(deletedAtMs = now, updatedAtMs = now))
    }

    suspend fun upsertMilestone(milestone: Milestone) {
        val now = System.currentTimeMillis()
        if (milestone.id == 0L) {
            val syncUuid = if (milestone.syncUuid.isBlank()) {
                val babySyncUuid = babyDao.getBabyByIdOnce(milestone.babyId)?.syncUuid
                if (babySyncUuid != null) {
                    // Include normalised title so two different milestones at the same minute stay distinct
                    syncUuidFor("m", babySyncUuid, floorToMinute(milestone.timestampMs), milestone.title.trim().lowercase())
                } else {
                    java.util.UUID.randomUUID().toString()
                }
            } else {
                milestone.syncUuid
            }
            milestoneDao.insertMilestone(milestone.copy(syncUuid = syncUuid, updatedAtMs = now))
        } else {
            milestoneDao.updateMilestone(milestone.copy(updatedAtMs = now))
        }
    }
}
