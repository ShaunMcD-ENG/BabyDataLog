package com.babydatalog.app.utils

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

// ─── Long → LocalDateTime ────────────────────────────────────────────────────

fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

// ─── Display formatters ──────────────────────────────────────────────────────

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy")
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm")

fun Long.toDisplayDate(): String =
    toLocalDateTime().format(DATE_FORMATTER)

fun Long.toDisplayTime(): String =
    toLocalDateTime().format(TIME_FORMATTER)

fun Long.toDisplayDateTime(): String =
    toLocalDateTime().format(DATE_TIME_FORMATTER)

// ─── Relative time ───────────────────────────────────────────────────────────

fun Long.toRelativeTime(): String {
    val nowMs = System.currentTimeMillis()
    val diffMs = nowMs - this
    val diffMinutes = diffMs / 60_000L
    val diffHours = diffMs / 3_600_000L
    val diffDays = diffMs / 86_400_000L

    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffHours < 24 -> {
            val remainingMinutes = diffMinutes % 60
            if (remainingMinutes == 0L) "${diffHours}h ago"
            else "${diffHours}h ${remainingMinutes}m ago"
        }
        diffDays == 1L -> "Yesterday"
        diffDays < 7 -> "${diffDays} days ago"
        else -> toDisplayDate()
    }
}

// ─── LocalDateTime → epoch millis ────────────────────────────────────────────

fun LocalDateTime.toEpochMs(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

// ─── Day / week / month boundaries ───────────────────────────────────────────

fun todayStartMs(): Long {
    val zone = ZoneId.systemDefault()
    return LocalDate.now(zone)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}

fun todayEndMs(): Long {
    val zone = ZoneId.systemDefault()
    return LocalDate.now(zone)
        .atTime(23, 59, 59, 999_000_000)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

fun weekStartMs(): Long {
    val zone = ZoneId.systemDefault()
    return LocalDate.now(zone)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}

fun weekEndMs(): Long {
    val zone = ZoneId.systemDefault()
    return LocalDate.now(zone)
        .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        .atTime(23, 59, 59, 999_000_000)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

fun monthStartMs(): Long {
    val zone = ZoneId.systemDefault()
    return LocalDate.now(zone)
        .with(TemporalAdjusters.firstDayOfMonth())
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}

fun monthEndMs(): Long {
    val zone = ZoneId.systemDefault()
    return LocalDate.now(zone)
        .with(TemporalAdjusters.lastDayOfMonth())
        .atTime(23, 59, 59, 999_000_000)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

// ─── Duration formatting ─────────────────────────────────────────────────────

fun formatDuration(minutes: Float?): String {
    if (minutes == null) return "--"
    val totalMinutes = minutes.toLong()
    return if (totalMinutes < 60) {
        "${totalMinutes} min"
    } else {
        val hours = totalMinutes / 60
        val remainingMinutes = totalMinutes % 60
        if (remainingMinutes == 0L) "${hours}h"
        else "${hours}h ${remainingMinutes}m"
    }
}
