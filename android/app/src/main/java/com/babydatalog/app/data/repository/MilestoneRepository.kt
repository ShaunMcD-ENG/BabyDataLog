package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.MilestoneDao
import com.babydatalog.app.data.database.entity.Milestone
import com.babydatalog.app.data.database.entity.MilestoneCategory
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MilestoneRepository @Inject constructor(
    private val milestoneDao: MilestoneDao
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

    suspend fun deleteMilestone(milestone: Milestone) =
        milestoneDao.deleteMilestone(milestone)

    suspend fun upsertMilestone(milestone: Milestone) {
        val now = System.currentTimeMillis()
        if (milestone.id == 0L) {
            milestoneDao.insertMilestone(
                milestone.copy(
                    syncUuid = if (milestone.syncUuid.isBlank()) UUID.randomUUID().toString() else milestone.syncUuid,
                    updatedAtMs = now
                )
            )
        } else {
            milestoneDao.updateMilestone(milestone.copy(updatedAtMs = now))
        }
    }
}
