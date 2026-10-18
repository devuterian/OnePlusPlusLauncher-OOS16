package com.wizpizz.onepluspluslauncher.hook.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchEngineTest {

    private val naver = AppSearchEngine.Entry("naver", "네이버", "NAVER", "com.nhn.android.search")
    private val naverMap = AppSearchEngine.Entry("nmap", "네이버지도", "NAVER Map", "com.nhn.android.nmap")
    private val naverPay = AppSearchEngine.Entry("npay", "네이버페이", "Naver Pay", "com.naverfin.payapp")
    private val coupang = AppSearchEngine.Entry("coupang", "쿠팡", "Coupang", "com.coupang.mobile")
    private val coupangEats = AppSearchEngine.Entry("eats", "쿠팡이츠", "Coupang Eats", "com.coupang.mobile.eats")
    private val googleMaps = AppSearchEngine.Entry("gmaps", "지도", "Maps", "com.google.android.apps.maps")
    private val kakaoTalk = AppSearchEngine.Entry("kakao", "카카오톡", "KakaoTalk", "com.kakao.talk")
    private val calculator = AppSearchEngine.Entry("calc", "계산기", "Calculator", "com.oplus.calculator")
    private val twitch = AppSearchEngine.Entry("twitch", "Twitch", null, "tv.twitch.android.app")
    private val photos = AppSearchEngine.Entry("photos", "사진", "Photos", "com.oneplus.gallery")
    private val youtubeMusic = AppSearchEngine.Entry("ytm", "YouTube Music", null, "com.google.android.apps.youtube.music")

    private val all = listOf(naver, naverMap, naverPay, coupang, coupangEats, googleMaps, kakaoTalk, calculator, youtubeMusic, twitch, photos)
    private val aliases = AppSearchEngine.buildAliases(null)

    private fun search(query: String, usage: Map<String, Double> = emptyMap()): List<String> =
        AppSearchEngine.rank(all.map { it.id to it }, query, aliases) { usage[it] ?: 0.0 }.map { it.item }

    @Test
    fun keyboardConversionRoundTrips() {
        assertEquals("spdlqj", KeyboardLayoutUtils.toKeys("네이버"))
        assertEquals("znvkd", KeyboardLayoutUtils.toKeys("쿠팡"))
        assertEquals("네이버", KeyboardLayoutUtils.keysToHangul("spdlqj"))
        assertEquals("안녕", KeyboardLayoutUtils.keysToHangul("dkssud"))
        assertEquals("왕", KeyboardLayoutUtils.keysToHangul("dhkd"))
        assertEquals("닭", KeyboardLayoutUtils.keysToHangul("ekfr"))
        assertEquals("달기", KeyboardLayoutUtils.keysToHangul("ekfrl"))
        assertEquals("coupang", KeyboardLayoutUtils.toKeys("채ㅕㅔ뭏"))
    }

    @Test
    fun englishKeysTypedForKoreanLabel() {
        assertEquals("naver", search("spdlqj").first())
        assertEquals("naver", search("spdlq").first())
    }

    @Test
    fun englishNameFindsKoreanLabel() {
        assertEquals("coupang", search("coupang").first())
        assertEquals("coupang", search("coup").first())
    }

    @Test
    fun englishTypedWithKoreanKeyboard() {
        assertEquals("coupang", search("채ㅕㅔ뭏").first())
    }

    @Test
    fun mapAliasFindsMaps() {
        val results = search("맵")
        assertTrue(results.contains("gmaps"))
        assertTrue(results.contains("nmap"))
        assertFalse(results.contains("calc"))
    }

    @Test
    fun halfTypedSyllableMatches() {
        assertEquals("naver", search("네입").first())
    }

    @Test
    fun onlyMatchingAppsAreReturned() {
        val results = search("쿠팡")
        assertEquals(setOf("coupang", "eats"), results.toSet())
    }

    @Test
    fun frequentAppRisesButExactMatchStaysFirst() {
        val usage = mapOf("nmap" to 20.0, "naver" to 1.0)
        assertEquals("nmap", search("네", usage).first())
        assertEquals("naver", search("네이버", usage).first())
    }

    @Test
    fun koreanInitialsStillWork() {
        assertEquals("kakao", search("ㅋㅋㅇㅌ").first())
    }

    @Test
    fun latinInitialsMatch() {
        assertEquals("ytm", search("ym").first())
    }

    @Test
    fun userAliasesParse() {
        val parsed = AppSearchEngine.parseUserAliases("엔페이 = 네이버페이, naver pay\n잘못된줄\n")
        assertEquals(listOf("네이버페이", "naverpay"), parsed["엔페이"])
        val custom = AppSearchEngine.buildAliases("엔페이 = 네이버페이")
        val results = AppSearchEngine.rank(all.map { it.id to it }, "엔페이", custom) { 0.0 }.map { it.item }
        assertEquals("npay", results.first())
    }

    @Test
    fun singleCharacterOnlyMatchesStarts() {
        val results = search("ㅇ")
        assertFalse(results.contains("kakao"))
        val initial = search("ㄴ")
        assertTrue(initial.containsAll(listOf("naver", "nmap", "npay")))
        assertFalse(search("a").contains("calc"))
    }

    @Test
    fun shortWrongLayoutGuessDoesNotBeatRealPrefix() {
        assertEquals("twitch", search("tw", mapOf("photos" to 50.0)).first())
    }

    @Test
    fun koreanReadingFindsLatinNames() {
        val apps = listOf(
            AppSearchEngine.Entry("gpt", "ChatGPT", null, "com.openai.chatgpt"),
            AppSearchEngine.Entry("nagram", "Nagram XF", null, "fork.risin42.nagramx"),
            AppSearchEngine.Entry("yt", "YouTube", null, "com.google.android.youtube"),
            AppSearchEngine.Entry("amaran", "amaran", null, "com.sidus.link.amaran"),
            AppSearchEngine.Entry("gmail", "Gmail", null, "com.google.android.gm"),
            AppSearchEngine.Entry("payco", "PAYCO", null, "com.nhnent.payapp")
        )
        fun first(q: String) = AppSearchEngine.rank(apps.map { it.id to it }, q, aliases) { 0.0 }.first().item
        assertEquals("gpt", first("챗지피티"))
        assertEquals("gpt", first("챗지"))
        assertEquals("nagram", first("나그램"))
        assertEquals("nagram", first("텔레그램"))
        assertEquals("yt", first("유투브"))
        assertEquals("amaran", first("아마란"))
        assertEquals("gmail", first("지메일"))
        assertEquals("payco", first("페이코"))
    }

    @Test
    fun usageDecaysOverTime() {
        val usage = UsageRanking(halfLifeMs = 1000)
        usage.recordLaunch("a", 0)
        usage.recordLaunch("a", 0)
        assertEquals(1.0, usage.valueOf("a", 1000), 0.001)
        val restored = UsageRanking(halfLifeMs = 1000)
        restored.load(usage.serialize(1000))
        assertEquals(1.0, restored.valueOf("a", 1000), 0.001)
    }
}
