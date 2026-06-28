package com.babydatalog.app.utils

import java.util.UUID

/** Floor a millisecond timestamp to the containing minute boundary. */
fun floorToMinute(ms: Long): Long = ms / 60_000L * 60_000L

/** Floor a millisecond timestamp to the containing day boundary (UTC). */
fun floorToDay(ms: Long): Long = ms / 86_400_000L * 86_400_000L

/**
 * Deterministic UUID derived from natural business-key parts.
 *
 * Both phones independently logging the same real-world event produce the same
 * UUID, so the server's last-write-wins logic can deduplicate them rather than
 * inserting two separate records.
 *
 * Uses UUID.nameUUIDFromBytes (RFC 4122 v3 / MD5) which is stable across JVM versions.
 */
fun syncUuidFor(vararg parts: Any): String =
    UUID.nameUUIDFromBytes(parts.joinToString(separator = ":").toByteArray(Charsets.UTF_8))
        .toString()
