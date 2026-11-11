package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.database.entity.FeedingSession
import com.babydatalog.app.data.database.entity.NappyChange
import com.babydatalog.app.data.repository.FeedingRepository
import com.babydatalog.app.data.repository.NappyRepository
import com.babydatalog.app.data.repository.SleepRepository
import com.babydatalog.app.utils.CurrentSleepStatus
import com.babydatalog.app.utils.SleepAwakeSummary
import com.babydatalog.app.utils.computeLast24HourSleepAwake
import com.babydatalog.app.utils.currentSleepStatus
import com.babydatalog.app.utils.defaultSleepTypeForStart
import com.babydatalog.app.utils.todayEndMs
import com.babydatalog.app.utils.todayStartMs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feedingRepository: FeedingRepository,
    private val nappyRepository: NappyRepository,
    private val sleepRepository: SleepRepository
) : ViewModel() {

    // babyId is now driven by BabyViewModel via HomeScreen's LaunchedEffect
    private val _activeBabyId = MutableStateFlow<Long?>(null)

    val lastFeeding: StateFlow<FeedingSession?> = _activeBabyId
        .flatMapLatest { id ->
            if (id != null) feedingRepository.getLastFeeding(id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastNappy: StateFlow<NappyChange?> = _activeBabyId
        .flatMapLatest { id ->
            if (id != null) nappyRepository.getLastNappy(id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayFeedingCount: StateFlow<Int> = _activeBabyId
        .flatMapLatest { id ->
            if (id != null) {
                feedingRepository.getFeedingsInRange(id, todayStartMs(), todayEndMs())
                    .map { it.size }
            } else {
                flowOf(0)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayNappyCount: StateFlow<Int> = _activeBabyId
        .flatMapLatest { id ->
            if (id != null) {
                nappyRepository.getNappiesInRange(id, todayStartMs(), todayEndMs())
                    .map { it.size }
            } else {
                flowOf(0)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val sleepSessions = _activeBabyId
        .flatMapLatest { id ->
            if (id != null) sleepRepository.getSleepSessionsForBaby(id) else flowOf(emptyList())
        }

    // Driven by the most recently started session only (see currentSleepStatus), so an
    // older unfinished or mis-dated entry can't hijack the live asleep/awake timer.
    val sleepStatus: StateFlow<CurrentSleepStatus> = sleepSessions
        .map { currentSleepStatus(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CurrentSleepStatus(null, null))

    val last24HourSummary: StateFlow<SleepAwakeSummary> = sleepSessions
        .map { computeLast24HourSleepAwake(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeLast24HourSleepAwake(emptyList()))

    fun quickStartSleep() {
        val babyId = _activeBabyId.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            sleepRepository.startSleep(babyId, defaultSleepTypeForStart(now), now)
        }
    }

    fun quickEndSleep() {
        val open = sleepStatus.value.openSession ?: return
        viewModelScope.launch { sleepRepository.endSleep(open) }
    }

    fun setActiveBabyId(id: Long) {
        if (_activeBabyId.value != id) {
            _activeBabyId.value = id
        }
    }
}
