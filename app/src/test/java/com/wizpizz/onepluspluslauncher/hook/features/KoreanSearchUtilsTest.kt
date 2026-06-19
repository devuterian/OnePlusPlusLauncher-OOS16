package com.wizpizz.onepluspluslauncher.hook.features

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanSearchUtilsTest {

    @Test
    fun consonantQueryMatchesKoreanInitials() {
        val kakaoTalk = KoreanSearchUtils.score("카카오톡", "ㅋㅌ")
        val calculator = KoreanSearchUtils.score("계산기", "ㅋㅌ")

        assertTrue(kakaoTalk.isMatch)
        assertTrue(kakaoTalk.score > calculator.score)
    }

    @Test
    fun partialSyllableMatchesSyllablesWithFinalConsonants() {
        val settings = KoreanSearchUtils.score("설정", "서")

        assertTrue(settings.isMatch)
    }

    @Test
    fun mixedSyllableQueryMatchesSequentially() {
        val kakaoTalk = KoreanSearchUtils.score("카카오톡", "카톡")

        assertTrue(kakaoTalk.isMatch)
    }

    @Test
    fun unrelatedKoreanInitialsDoNotMatch() {
        val kakaoTalk = KoreanSearchUtils.score("카카오톡", "ㅎㄱ")

        assertFalse(kakaoTalk.isMatch)
    }

    @Test
    fun initialSequenceRanksCompactMatchHighly() {
        val jeongseon = KoreanSearchUtils.score("정선군", "ㅈㅅㄱ")

        assertTrue(jeongseon.isMatch)
        assertTrue(jeongseon.priority >= 4)
    }
}
