package com.wizpizz.onepluspluslauncher.hook.features

import me.xdrop.fuzzywuzzy.FuzzySearch

/**
 * Pure ranking logic for app drawer search. Everything per-app is precomputed in [Entry]
 * so a keystroke only does cheap string comparisons.
 *
 * Ranking: match quality bucket (exact > strong > weak), then how often the app is used,
 * then the fine-grained match score.
 */
internal object AppSearchEngine {

    const val SCORE_EXACT = 300
    private const val SCORE_PREFIX = 250
    private const val SCORE_ALIAS_EXACT = 235
    private const val SCORE_WORD_PREFIX = 220
    private const val SCORE_ALIAS_PREFIX = 200
    private const val SCORE_INITIALS = 190
    private const val SCORE_PACKAGE = 170
    private const val SCORE_CONTAINS = 160
    private const val SCORE_TYPO = 120
    private const val CROSS_LAYOUT_PENALTY = 5

    private const val STRONG_THRESHOLD = 180

    private val packageStopWords = setOf(
        "com", "android", "app", "apps", "mobile", "google", "samsung", "oplus", "oneplus",
        "coloros", "heytap", "nhn", "kr", "co", "net", "org", "io", "www", "client", "main"
    )

    val builtInAliases: Map<String, List<String>> = mapOf(
        "맵" to listOf("지도", "maps", "map"),
        "지도" to listOf("지도", "maps", "map"),
        "카톡" to listOf("카카오톡", "kakaotalk"),
        "인스타" to listOf("인스타그램", "instagram"),
        "유튜브" to listOf("youtube", "유튜브"),
        "유튭" to listOf("youtube", "유튜브"),
        "크롬" to listOf("chrome", "크롬"),
        "지메일" to listOf("gmail", "지메일"),
        "메일" to listOf("메일", "gmail", "email"),
        "문자" to listOf("메시지", "messages"),
        "메세지" to listOf("메시지", "messages"),
        "사진" to listOf("사진", "갤러리", "photos", "포토"),
        "갤러리" to listOf("갤러리", "사진", "photos"),
        "알람" to listOf("시계", "clock", "알람"),
        "타이머" to listOf("시계", "clock", "타이머"),
        "전화" to listOf("전화", "phone"),
        "연락처" to listOf("연락처", "contacts"),
        "번역" to listOf("번역", "translate", "파파고"),
        "날씨" to listOf("날씨", "weather"),
        "녹음" to listOf("녹음", "레코더", "recorder"),
        "음악" to listOf("음악", "music", "멜론", "spotify"),
        "플스" to listOf("play스토어", "playstore"),
        "플레이스토어" to listOf("play스토어", "playstore"),
        "배민" to listOf("배달의민족"),
        "넷플" to listOf("넷플릭스", "netflix"),
        "페북" to listOf("페이스북", "facebook"),
        "설정" to listOf("설정", "settings"),
        "달력" to listOf("캘린더", "달력", "calendar"),
        "캘린더" to listOf("캘린더", "달력", "calendar"),
        "메모" to listOf("메모", "notes", "keep"),
        "파일" to listOf("내파일", "files", "파일"),
        "계산기" to listOf("계산기", "calculator"),
        "카메라" to listOf("카메라", "camera")
    )

    class Entry(
        val id: String,
        label: String,
        englishLabel: String?,
        packageName: String
    ) {
        val label: String = label
        val labelNorm = normalize(label)
        val labelKeys = KeyboardLayoutUtils.toKeys(labelNorm)
        val labelIsHangul = KeyboardLayoutUtils.containsHangul(labelNorm)
        val labelWords = words(label)
        val englishNorm: String? = englishLabel?.let(::normalize)?.takeIf { it.isNotEmpty() && it != labelNorm }
        val englishWords: List<String> = if (englishNorm != null && englishLabel != null) words(englishLabel) else emptyList()
        val latinInitials: String = initialsOf(englishWords.ifEmpty { labelWords.filter { w -> w.all { !KeyboardLayoutUtils.isHangul(it) } } })
        val packageTokens: List<String> = packageName.lowercase()
            .split('.', '_')
            .filter { it.length >= 3 && it !in packageStopWords }
    }

    class Alias(val keyNorm: String, val keyKeys: String, val targets: List<String>)

    class Query(raw: String) {
        val norm = normalize(raw)
        val keys = KeyboardLayoutUtils.toKeys(norm)
        val isHangul = KeyboardLayoutUtils.containsHangul(norm)
        val isLatin = norm.isNotEmpty() && norm.any(KeyboardLayoutUtils::isLatinLetter) && !isHangul
        /** Latin query composed as Hangul, e.g. "spdlqj" -> "네이버". */
        val asHangul: String? = if (isLatin) KeyboardLayoutUtils.keysToHangul(norm).takeIf { it != norm } else null
    }

    fun buildAliases(userAliasText: String?): List<Alias> {
        val merged = LinkedHashMap<String, MutableList<String>>()
        builtInAliases.forEach { (key, targets) -> merged.getOrPut(normalize(key)) { mutableListOf() }.addAll(targets.map(::normalize)) }
        AppNamePronunciations.readings.forEach { (target, readings) ->
            readings.forEach { reading -> merged.getOrPut(normalize(reading)) { mutableListOf() }.add(normalize(target)) }
        }
        parseUserAliases(userAliasText).forEach { (key, targets) ->
            merged.getOrPut(key) { mutableListOf() }.addAll(targets)
        }
        return merged.map { (key, targets) -> Alias(key, KeyboardLayoutUtils.toKeys(key), targets.distinct()) }
    }

    /** One alias per line: "맵 = 지도, maps". */
    fun parseUserAliases(text: String?): Map<String, List<String>> {
        if (text.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, List<String>>()
        text.lineSequence().forEach { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            val key = normalize(line.substring(0, separator))
            val targets = line.substring(separator + 1).split(',', '，')
                .map(::normalize)
                .filter { it.isNotEmpty() }
            if (key.isNotEmpty() && targets.isNotEmpty()) result[key] = targets
        }
        return result
    }

    fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        text.forEach { ch ->
            if (!ch.isWhitespace() && ch != '\'' && ch != '’' && ch != '-' && ch != '·' && ch != '.') {
                builder.append(ch.lowercaseChar())
            }
        }
        return builder.toString()
    }

    private fun words(text: String): List<String> =
        text.lowercase().split(' ', '-', '_', '·', '.', '(', ')', ':', '/').filter { it.isNotEmpty() }

    private fun initialsOf(words: List<String>): String =
        if (words.size < 2) "" else words.joinToString("") { it.first().toString() }

    /** Returns 0 when the entry does not match. */
    fun score(entry: Entry, query: Query, aliases: List<Alias>): Int {
        val q = query.norm
        if (q.isEmpty()) return 0
        var best = 0

        fun consider(score: Int) {
            if (score > best) best = score
        }

        // Wrong-layout matches on 1-2 keys are guesses ("tw" -> ㅅㅈ -> 사진); keep them below
        // real matches so a frequently used app can't jump ahead of what was actually typed.
        val shortQuery = query.keys.length < 3
        fun crossLayout(score: Int): Int {
            if (score <= 0) return 0
            val penalized = score - CROSS_LAYOUT_PENALTY
            return if (shortQuery) minOf(penalized, STRONG_THRESHOLD - 1) else penalized
        }

        // Same script, typed as-is.
        consider(textScore(entry.labelNorm, entry.labelWords, q, allowContains = true))
        entry.englishNorm?.let { consider(textScore(it, entry.englishWords, q, allowContains = true)) }

        // Hangul label compared key-by-key handles half-typed syllables ("네입" -> 네이버).
        if (query.isHangul && entry.labelIsHangul) {
            consider(keyScore(entry.labelKeys, query.keys, allowContains = query.keys.length >= 3))
        }

        // Typed in the wrong keyboard layout.
        if (query.keys.length >= 2) {
            if (query.isLatin && entry.labelIsHangul) {
                consider(crossLayout(keyScore(entry.labelKeys, query.keys, allowContains = query.keys.length >= 3)))
            }
            if (query.isHangul) {
                consider(crossLayout(textScore(entry.labelNorm, entry.labelWords, query.keys, allowContains = query.keys.length >= 3)))
                entry.englishNorm?.let {
                    consider(crossLayout(textScore(it, entry.englishWords, query.keys, allowContains = query.keys.length >= 3)))
                }
            }
        }

        // Korean initials and sequential syllable matching ("ㅋㅌ", "카톡").
        if (entry.labelIsHangul) {
            if (query.isHangul) consider(koreanScore(entry.label, q))
            query.asHangul?.let { consider(crossLayout(koreanScore(entry.label, it))) }
        }

        // Latin initials: "ytm" -> YouTube Music.
        if (q.length >= 2 && entry.latinInitials.length >= 2 && entry.latinInitials.startsWith(q)) {
            consider(SCORE_INITIALS)
        }

        // Package name tokens: com.coupang.mobile -> "coupang".
        val packageQuery = if (query.isLatin) q else if (query.isHangul) query.keys else q
        if (packageQuery.length >= 3 && entry.packageTokens.any { it.startsWith(packageQuery) }) {
            consider(SCORE_PACKAGE)
        }

        if (best < SCORE_ALIAS_EXACT) consider(aliasScore(entry, query, aliases))

        // Typo tolerance for Latin queries, only when nothing else matched.
        if (best == 0 && query.isLatin && q.length >= 4) {
            val target = entry.englishNorm ?: entry.labelNorm.takeIf { !entry.labelIsHangul }
            if (target != null && FuzzySearch.ratio(target.take(q.length + 2), q) >= 80) {
                consider(SCORE_TYPO)
            }
        }

        return best
    }

    private fun textScore(text: String, words: List<String>, q: String, allowContains: Boolean): Int = when {
        text == q -> SCORE_EXACT
        text.startsWith(q) -> SCORE_PREFIX
        words.size > 1 && words.any { it.startsWith(q) } -> SCORE_WORD_PREFIX
        allowContains && text.contains(q) -> SCORE_CONTAINS
        else -> 0
    }

    private fun keyScore(textKeys: String, qKeys: String, allowContains: Boolean): Int = when {
        textKeys == qKeys -> SCORE_EXACT
        textKeys.startsWith(qKeys) -> SCORE_PREFIX
        allowContains && textKeys.contains(qKeys) -> SCORE_CONTAINS
        else -> 0
    }

    private fun koreanScore(label: String, query: String): Int {
        val match = KoreanSearchUtils.score(label, query)
        if (!match.isMatch) return 0
        return when {
            match.priority >= 6 -> SCORE_EXACT
            match.priority == 5 -> SCORE_PREFIX
            match.priority == 4 -> SCORE_WORD_PREFIX
            match.priority == 3 -> SCORE_INITIALS
            else -> SCORE_CONTAINS - 10
        }
    }

    private fun aliasScore(entry: Entry, query: Query, aliases: List<Alias>): Int {
        var best = 0
        for (alias in aliases) {
            val aliasScore = when {
                alias.keyNorm == query.norm || alias.keyKeys == query.keys -> SCORE_ALIAS_EXACT
                query.keys.length >= 2 && alias.keyKeys.startsWith(query.keys) -> SCORE_ALIAS_PREFIX
                else -> 0
            }
            if (aliasScore <= best) continue
            if (alias.targets.any { target -> targetMatches(entry, target) }) best = aliasScore
        }
        return best
    }

    private fun targetMatches(entry: Entry, target: String): Boolean {
        // Very short targets ("x") must match the whole label to avoid noise.
        if (target.length <= 2) return entry.labelNorm == target || entry.englishNorm == target
        return entry.labelNorm.contains(target) || entry.englishNorm?.contains(target) == true
    }

    fun bucket(score: Int): Int = when {
        score >= SCORE_EXACT -> 2
        score >= STRONG_THRESHOLD -> 1
        else -> 0
    }

    class Ranked<T>(val item: T, val entry: Entry, val score: Int, val usage: Double)

    fun <T> rank(candidates: List<Pair<T, Entry>>, rawQuery: String, aliases: List<Alias>, usage: (String) -> Double): List<Ranked<T>> {
        val query = Query(rawQuery)
        if (query.norm.isEmpty()) return emptyList()
        // Only aliases whose key matches the query need to be checked against each app.
        val activeAliases = aliases.filter { alias ->
            alias.keyNorm == query.norm || alias.keyKeys == query.keys ||
                (query.keys.length >= 2 && alias.keyKeys.startsWith(query.keys))
        }
        // A single character matches too much mid-word; only keep apps that start with it.
        val minScore = if (query.norm.length == 1) SCORE_WORD_PREFIX else 1
        val matched = ArrayList<Ranked<T>>()
        for ((item, entry) in candidates) {
            val score = score(entry, query, activeAliases)
            if (score >= minScore) matched.add(Ranked(item, entry, score, usage(entry.id)))
        }
        matched.sortWith(
            compareByDescending<Ranked<T>> { bucket(it.score) }
                .thenByDescending { it.usage }
                .thenByDescending { it.score }
                .thenBy { it.entry.labelNorm.length }
                .thenBy { it.entry.labelNorm }
        )
        return matched
    }
}
