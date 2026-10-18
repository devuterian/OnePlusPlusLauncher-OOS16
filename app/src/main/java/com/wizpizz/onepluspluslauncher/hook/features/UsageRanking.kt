package com.wizpizz.onepluspluslauncher.hook.features

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * Frecency: each launch adds 1, and the total halves every [halfLifeMs].
 * Recent, frequent launches rank highest; apps you stopped using fade out.
 */
internal class UsageRanking(private val halfLifeMs: Long = 14L * 24 * 60 * 60 * 1000) {

    private class Record(val score: Double, val updatedAt: Long)

    private val records = ConcurrentHashMap<String, Record>()

    fun valueOf(id: String, now: Long): Double {
        val record = records[id] ?: return 0.0
        return decayed(record, now)
    }

    fun recordLaunch(id: String, now: Long) {
        records.compute(id) { _, old ->
            Record((old?.let { decayed(it, now) } ?: 0.0) + 1.0, now)
        }
    }

    /** "id\tscore\tupdatedAt" lines. Drops entries that have decayed to almost nothing. */
    fun serialize(now: Long): String {
        val builder = StringBuilder()
        records.forEach { (id, record) ->
            val value = decayed(record, now)
            if (value >= 0.05) builder.append(id).append('\t').append(value).append('\t').append(now).append('\n')
        }
        return builder.toString()
    }

    fun load(text: String?) {
        if (text.isNullOrEmpty()) return
        text.lineSequence().forEach { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@forEach
            val score = parts[1].toDoubleOrNull() ?: return@forEach
            val updatedAt = parts[2].toLongOrNull() ?: return@forEach
            records[parts[0]] = Record(score, updatedAt)
        }
    }

    private fun decayed(record: Record, now: Long): Double {
        val elapsed = (now - record.updatedAt).coerceAtLeast(0L)
        return record.score * 0.5.pow(elapsed.toDouble() / halfLifeMs)
    }
}
