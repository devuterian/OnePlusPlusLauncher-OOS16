package com.wizpizz.onepluspluslauncher.hook.features

import kotlin.math.max

internal object KoreanSearchUtils {

    private const val HANGUL_BASE = 0xAC00
    private const val HANGUL_END = 0xD7A3
    private const val JONGSUNG_COUNT = 28
    private const val INITIAL_BLOCK_SIZE = 588

    private val initialConsonantToSyllable = mapOf(
        'ㄱ' to '가'.code,
        'ㄲ' to '까'.code,
        'ㄴ' to '나'.code,
        'ㄷ' to '다'.code,
        'ㄸ' to '따'.code,
        'ㄹ' to '라'.code,
        'ㅁ' to '마'.code,
        'ㅂ' to '바'.code,
        'ㅃ' to '빠'.code,
        'ㅅ' to '사'.code,
        'ㅆ' to '싸'.code,
        'ㅇ' to '아'.code,
        'ㅈ' to '자'.code,
        'ㅉ' to '짜'.code,
        'ㅊ' to '차'.code,
        'ㅋ' to '카'.code,
        'ㅌ' to '타'.code,
        'ㅍ' to '파'.code,
        'ㅎ' to '하'.code
    )

    private val initialConsonants = initialConsonantToSyllable.keys.toList()

    data class Match(
        val isMatch: Boolean,
        val priority: Int,
        val score: Int
    )

    fun score(target: String, query: String): Match {
        if (target.isEmpty() || query.isEmpty()) return Match(false, 0, 0)

        val targetLower = target.lowercase()
        val queryLower = query.lowercase()
        var priority = 0
        var score = 0

        when {
            targetLower == queryLower -> {
                priority = 6
                score = 320
            }
            targetLower.startsWith(queryLower) -> {
                priority = 5
                score = 285
            }
            targetLower.contains(queryLower) -> {
                priority = 4
                score = 255
            }
        }

        if (!query.any(::isKoreanSearchChar)) {
            return Match(score > 0, priority, score)
        }

        if (query.all { initialConsonantToSyllable.containsKey(it) }) {
            val initials = extractInitials(target)
            when {
                initials == query -> {
                    priority = max(priority, 5)
                    score = max(score, 300)
                }
                initials.startsWith(query) -> {
                    priority = max(priority, 4)
                    score = max(score, 275)
                }
                initials.contains(query) -> {
                    priority = max(priority, 3)
                    score = max(score, 245)
                }
            }
        }

        val positions = findSequentialMatchPositions(target, query)
        if (positions != null) {
            val first = positions.first()
            val span = positions.last() - first + 1
            val gaps = span - positions.size
            val startBonus = max(0, 44 - first * 4)
            val compactBonus = max(0, 86 - gaps * 12)
            val sequentialScore = 160 + startBonus + compactBonus
            val sequentialPriority = when {
                first == 0 && gaps == 0 -> 4
                first == 0 -> 3
                else -> 2
            }
            priority = max(priority, sequentialPriority)
            score = max(score, sequentialScore)
        }

        return Match(score > 0, priority, score)
    }

    private fun findSequentialMatchPositions(target: String, query: String): IntArray? {
        val positions = IntArray(query.length)
        var targetIndex = 0

        for ((queryIndex, queryChar) in query.withIndex()) {
            var foundAt = -1
            while (targetIndex < target.length) {
                if (charMatches(target[targetIndex], queryChar)) {
                    foundAt = targetIndex
                    targetIndex++
                    break
                }
                targetIndex++
            }
            if (foundAt == -1) return null
            positions[queryIndex] = foundAt
        }

        return positions
    }

    private fun charMatches(targetChar: Char, queryChar: Char): Boolean {
        if (targetChar.lowercaseChar() == queryChar.lowercaseChar()) return true

        val queryCode = queryChar.code
        if (queryCode in HANGUL_BASE..HANGUL_END) {
            val relativeCode = queryCode - HANGUL_BASE
            if (relativeCode % JONGSUNG_COUNT > 0) return false

            val begin = (relativeCode / JONGSUNG_COUNT) * JONGSUNG_COUNT + HANGUL_BASE
            val end = begin + JONGSUNG_COUNT - 1
            return targetChar.code in begin..end
        }

        val initialBegin = initialConsonantToSyllable[queryChar] ?: return false
        val initialEnd = initialBegin + INITIAL_BLOCK_SIZE - 1
        return targetChar.code in initialBegin..initialEnd
    }

    private fun extractInitials(text: String): String {
        val builder = StringBuilder(text.length)
        text.forEach { ch ->
            val code = ch.code
            if (code in HANGUL_BASE..HANGUL_END) {
                val initialIndex = (code - HANGUL_BASE) / INITIAL_BLOCK_SIZE
                builder.append(initialConsonants[initialIndex])
            } else {
                builder.append(ch.lowercaseChar())
            }
        }
        return builder.toString()
    }

    private fun isKoreanSearchChar(ch: Char): Boolean {
        return ch.code in HANGUL_BASE..HANGUL_END || initialConsonantToSyllable.containsKey(ch)
    }
}
