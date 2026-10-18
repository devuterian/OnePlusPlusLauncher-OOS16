package com.wizpizz.onepluspluslauncher.hook.features

/**
 * Converts between Hangul and the QWERTY keys used to type it on a Dubeolsik keyboard.
 * "네이버" <-> "spdlqj", "쿠팡" <-> "znvkd". Shifted keys collapse to lowercase so
 * "까" and "가" share keys; search is case-insensitive anyway.
 */
internal object KeyboardLayoutUtils {

    private const val HANGUL_BASE = 0xAC00
    private const val HANGUL_END = 0xD7A3
    private const val JUNG_COUNT = 21
    private const val JONG_COUNT = 28

    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private val JONG = listOf(
        "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ",
        "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    )

    private val keyToJamo: Map<Char, Char> = mapOf(
        'q' to 'ㅂ', 'w' to 'ㅈ', 'e' to 'ㄷ', 'r' to 'ㄱ', 't' to 'ㅅ',
        'y' to 'ㅛ', 'u' to 'ㅕ', 'i' to 'ㅑ', 'o' to 'ㅐ', 'p' to 'ㅔ',
        'a' to 'ㅁ', 's' to 'ㄴ', 'd' to 'ㅇ', 'f' to 'ㄹ', 'g' to 'ㅎ',
        'h' to 'ㅗ', 'j' to 'ㅓ', 'k' to 'ㅏ', 'l' to 'ㅣ',
        'z' to 'ㅋ', 'x' to 'ㅌ', 'c' to 'ㅊ', 'v' to 'ㅍ', 'b' to 'ㅠ', 'n' to 'ㅜ', 'm' to 'ㅡ',
        'Q' to 'ㅃ', 'W' to 'ㅉ', 'E' to 'ㄸ', 'R' to 'ㄲ', 'T' to 'ㅆ', 'O' to 'ㅒ', 'P' to 'ㅖ'
    )

    private val jamoToKeys: Map<Char, String> = buildMap {
        keyToJamo.forEach { (key, jamo) -> put(jamo, key.lowercaseChar().toString()) }
        // Doubled consonants are shift + key; keep a single lowercase key.
        put('ㄲ', "r"); put('ㄸ', "e"); put('ㅃ', "q"); put('ㅆ', "t"); put('ㅉ', "w")
        put('ㅘ', "hk"); put('ㅙ', "ho"); put('ㅚ', "hl")
        put('ㅝ', "nj"); put('ㅞ', "np"); put('ㅟ', "nl"); put('ㅢ', "ml")
        put('ㄳ', "rt"); put('ㄵ', "sw"); put('ㄶ', "sg"); put('ㄺ', "fr"); put('ㄻ', "fa")
        put('ㄼ', "fq"); put('ㄽ', "ft"); put('ㄾ', "fx"); put('ㄿ', "fv"); put('ㅀ', "fg")
        put('ㅄ', "qt")
    }

    private val vowelPairs: Map<String, Char> = mapOf(
        "ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ',
        "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ'
    )

    private val finalPairs: Map<String, Char> = mapOf(
        "ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ',
        "ㄹㅂ" to 'ㄼ', "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ'
    )
    private val finalSplit: Map<Char, Pair<Char, Char>> =
        finalPairs.entries.associate { (pair, combined) -> combined to (pair[0] to pair[1]) }

    fun isHangul(ch: Char): Boolean =
        ch.code in HANGUL_BASE..HANGUL_END || ch in 'ㄱ'..'ㅣ'

    fun containsHangul(text: String): Boolean = text.any(::isHangul)

    fun isLatinLetter(ch: Char): Boolean = ch in 'a'..'z' || ch in 'A'..'Z'

    /** Hangul to the lowercase keys that type it. Other characters are lowercased as-is. */
    fun toKeys(text: String): String {
        val builder = StringBuilder(text.length * 3)
        text.forEach { ch ->
            val code = ch.code
            if (code in HANGUL_BASE..HANGUL_END) {
                val offset = code - HANGUL_BASE
                builder.append(jamoToKeys[CHO[offset / (JUNG_COUNT * JONG_COUNT)]])
                builder.append(jamoToKeys[JUNG[(offset / JONG_COUNT) % JUNG_COUNT]])
                val jong = JONG[offset % JONG_COUNT]
                if (jong.isNotEmpty()) builder.append(jamoToKeys[jong[0]])
            } else {
                builder.append(jamoToKeys[ch] ?: ch.lowercaseChar().toString())
            }
        }
        return builder.toString()
    }

    /** Composes Dubeolsik keys into Hangul. "spdlqj" -> "네이버". Non-letters pass through. */
    fun keysToHangul(keys: String): String {
        val out = StringBuilder(keys.length)
        var cho: Char? = null
        var jung: Char? = null
        var jong: Char? = null

        fun flush() {
            val c = cho
            val v = jung
            if (c != null && v != null) {
                val choIndex = CHO.indexOf(c)
                val jungIndex = JUNG.indexOf(v)
                val jongIndex = jong?.let { JONG.indexOf(it.toString()) } ?: 0
                out.append((HANGUL_BASE + (choIndex * JUNG_COUNT + jungIndex) * JONG_COUNT + jongIndex).toChar())
            } else {
                c?.let(out::append)
                v?.let(out::append)
                jong?.let(out::append)
            }
            cho = null; jung = null; jong = null
        }

        keys.forEach { key ->
            val jamo = keyToJamo[key] ?: keyToJamo[key.lowercaseChar()]
            if (jamo == null) {
                flush()
                out.append(key)
                return@forEach
            }
            val isVowel = jamo in JUNG
            if (!isVowel) {
                when {
                    cho != null && jung != null && jong == null && JONG.contains(jamo.toString()) -> jong = jamo
                    jong != null && finalPairs.containsKey("$jong$jamo") -> jong = finalPairs["$jong$jamo"]
                    else -> { flush(); cho = jamo }
                }
            } else {
                val currentJong = jong
                when {
                    currentJong != null -> {
                        val split = finalSplit[currentJong]
                        val nextCho: Char
                        if (split != null) {
                            jong = split.first
                            nextCho = split.second
                        } else {
                            jong = null
                            nextCho = currentJong
                        }
                        flush()
                        cho = nextCho
                        jung = jamo
                    }
                    jung != null && vowelPairs.containsKey("$jung$jamo") -> jung = vowelPairs["$jung$jamo"]
                    jung != null -> { flush(); jung = jamo }
                    else -> jung = jamo
                }
            }
        }
        flush()
        return out.toString()
    }
}
