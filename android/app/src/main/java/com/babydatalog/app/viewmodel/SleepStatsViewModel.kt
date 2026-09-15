package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.repository.SleepRepository
import com.babydatalog.app.utils.SleepHistogram
import com.babydatalog.app.utils.SleepLens
import com.babydatalog.app.utils.SleepStats
import com.babydatalog.app.utils.SleepStatsPeriod
import com.babydatalog.app.utils.computeSleepHistogram
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

    private val _lens = MutableStateFlow(SleepLens.COMBINED)
    val lens: StateFlow<SleepLens> = _lens

    val histogram: StateFlow<SleepHistogram> = combine(_activeBabyId, _period, _lens) { id, period, lens -> Triple(id, period, lens) }
        .flatMapLatest { (id, period, lens) ->
            if (id == null) flowOf(computeSleepHistogram(emptyList(), period, lens))
            else sleepRepository.getSleepSessionsForBaby(id).map { sessions -> computeSleepHistogram(sessions, period, lens) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeSleepHistogram(emptyList(), SleepStatsPeriod.LAST_7_DAYS, SleepLens.COMBINED))

    fun setActiveBabyId(id: Long) {
        if (_activeBabyId.value != id) _activeBabyId.value = id
    }

    fun selectPeriod(period: SleepStatsPeriod) {
        _period.value = period
    }

    fun selectLens(lens: SleepLens) {
        _lens.value = lens
    }
}
