package com.babydatalog.app.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits the current time immediately, then once a minute. Combined with a StateFlow using
 * SharingStarted.WhileSubscribed, it only ticks while a screen is collecting — i.e. on screen.
 */
fun minuteTicker(periodMs: Long = 60_000L): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(periodMs)
    }
}
