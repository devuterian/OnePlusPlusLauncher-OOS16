package com.wizpizz.onepluspluslauncher.hook.features

import android.util.Log
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.LAUNCHER_CLASS
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_SEARCH_HISTORY_RECENCY
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_USE_FUZZY_SEARCH
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG
import me.xdrop.fuzzywuzzy.FuzzySearch
import kotlin.math.roundToInt


object FuzzySearchHook {

    private const val SEARCH_CONTAINER_CLASS =
        "com.android.launcher3.allapps.search.LauncherTaskbarAppsSearchContainerLayout"
    private const val BASE_ADAPTER_ITEM_CLASS =
        "com.android.launcher3.allapps.BaseAllAppsAdapter\$AdapterItem"
    private const val APP_INFO_CLASS = "com.android.launcher3.model.data.AppInfo"
    private const val ARRAY_LIST_CLASS = "java.util.ArrayList"

    private const val PREFIX_MATCH_MULTIPLIER = 1.5
    private const val SUBSTRING_MATCH_MULTIPLIER = 1.3
    private const val SUBSEQUENCE_MATCH_MULTIPLIER = 1.1

    // Shared state for SwipeDownSearchRedirectHook coordination
    @Volatile
    var searchContainerInstance: Any? = null
    @Volatile
    var lastRedirectTime = 0L

    // Anti-flash: timestamp when history was injected, blocks reset() for 2s
    @Volatile
    private var historyInjectedTime = 0L
    private const val HISTORY_LOCK_WINDOW_MS = 2000L

    data class FuzzyMatchResult(
        val itemInfo: Any,
        val score: Int,
        val koreanMatchPriority: Int,
        val directMatchPriority: Int,
        val appName: String
    )

    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            // Hook onSearchResult for fuzzy search
            SEARCH_CONTAINER_CLASS.toClassOrNull(appClassLoader)?.method {
                name = "onSearchResult"
                param(String::class.java.name, ARRAY_LIST_CLASS)
            }?.hook {
                before {
                    // Cache the container instance for SwipeDownSearchRedirectHook
                    searchContainerInstance = instance

                    val rawQuery = args[0] as? String ?: return@before
                    val sanitizedQuery = sanitizeSearchQuery(rawQuery)
                    if (sanitizedQuery.isBlank()) return@before

                    val useFuzzySearch = try { prefs.getBoolean(PREF_USE_FUZZY_SEARCH, true) } catch (_: Throwable) { true }
                    if (!useFuzzySearch) return@before

                    try {
                        val sortedResults = performFuzzySearch(instance, sanitizedQuery)
                        if (sortedResults.isNotEmpty()) {
                            args[1] = sortedResults
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "[FuzzySearch] Error during fuzzy search: ${e.message}")
                    }
                }
                after {
                    val rawQuery = args[0] as? String ?: return@after
                    if (rawQuery.isNotBlank()) disableSearchResultFade(instance)
                }
            } ?: Log.e(TAG, "[FuzzySearch] Could not find onSearchResult method")

            // Cache container instance on attach (for first-open reliability)
            SEARCH_CONTAINER_CLASS.toClassOrNull(appClassLoader)?.method {
                name = "onAttachedToWindow"
                emptyParam()
            }?.hook {
                after {
                    searchContainerInstance = instance
                    Log.d(TAG, "[FuzzySearch] Cached searchContainerInstance via onAttachedToWindow")
                }
            }

            // Anti-flash: hook showAllAppsFromIntent to synchronously trigger search mode
            // This runs on the SAME frame as the drawer open, before any vsync renders the app grid
            LAUNCHER_CLASS.toClassOrNull(appClassLoader)?.method {
                name = "showAllAppsFromIntent"
                param(BooleanType)
            }?.hook {
                after {
                    val useHistory = try { prefs.getBoolean(PREF_SEARCH_HISTORY_RECENCY, true) } catch (_: Throwable) { true }
                    if (!useHistory) return@after

                    // Only act within 1s of a redirect (not normal swipe-up)
                    if (System.currentTimeMillis() - lastRedirectTime > 1000) return@after

                    val container = searchContainerInstance ?: return@after
                    try {
                        container.current().method {
                            name = "onSearchResult"
                            param(String::class.java, java.util.ArrayList::class.java)
                            superClass(true)
                        }.call(" ", java.util.ArrayList<Any>())
                        historyInjectedTime = System.currentTimeMillis()
                        Log.d(TAG, "[AntiFlash] Synchronous search mode trigger in showAllAppsFromIntent.after")
                    } catch (e: Throwable) {
                        Log.e(TAG, "[AntiFlash] Failed to trigger search mode: ${e.message}")
                    }
                }
            }

            // Anti-flash: block reset() within 2s of history injection
            // The launcher calls reset() after ALL_APPS animation ends, which would wipe search mode
            SEARCH_CONTAINER_CLASS.toClassOrNull(appClassLoader)?.method {
                name = "reset"
                emptyParam()
            }?.hook {
                before {
                    if (System.currentTimeMillis() - historyInjectedTime < HISTORY_LOCK_WINDOW_MS) {
                        result = null // Block reset
                        Log.d(TAG, "[AntiFlash] Blocked reset() within history lock window")
                    }
                }
            }

            // Anti-flash: block Workspace.requestFocus for 5s after redirect
            // Prevents gray focus box appearing on desktop icons
            "com.android.launcher3.Workspace".toClassOrNull(appClassLoader)?.method {
                name = "requestFocus"
                paramCount(0..2)
            }?.hook {
                before {
                    if (System.currentTimeMillis() - lastRedirectTime < 5000) {
                        result = false
                        Log.d(TAG, "[AntiFlash] Blocked Workspace.requestFocus after redirect")
                    }
                }
            }
        }
    }

    private fun sanitizeSearchQuery(input: String): String {
        if (input.isEmpty()) return input
        val builder = StringBuilder(input.length)
        input.forEach { ch ->
            if (ch != ' ' && ch != '\'') builder.append(ch)
        }
        return builder.toString()
    }

    private fun disableSearchResultFade(containerInstance: Any) {
        val root = containerInstance as? android.view.ViewGroup ?: return
        root.post { forceVisibleWithoutAnimation(root) }
    }

    private fun forceVisibleWithoutAnimation(view: android.view.View) {
        view.clearAnimation()
        view.animate().cancel()
        view.alpha = 1f

        if (view is android.view.ViewGroup) {
            view.layoutAnimation = null
            for (i in 0 until view.childCount) {
                forceVisibleWithoutAnimation(view.getChildAt(i))
            }
        }
    }

    private fun PackageParam.performFuzzySearch(
        containerInstance: Any,
        query: String
    ): ArrayList<Any> {
        val appsList = getAppsListFromContainer(containerInstance) ?: return ArrayList()
        val allAppInfos = getAllAppInfos(appsList) ?: return ArrayList()
        return convertToAdapterItems(scoreSearchResults(allAppInfos, query))
    }

    private fun getAppsListFromContainer(containerInstance: Any): Any? {
        return try {
            val appsViewField =
                containerInstance.javaClass.field { name = "mAppsView"; superClass(true) }
            val appsViewInstance = appsViewField.get(containerInstance).any() ?: return null

            appsViewInstance.current().method { name = "getAlphabeticalAppsList"; superClass() }
                .call()
                ?: appsViewInstance.current().method { name = "getAppsList"; superClass() }.call()
                ?: appsViewInstance.current().method { name = "getApps"; superClass() }.call()
        } catch (e: Throwable) {
            Log.e(TAG, "[FuzzySearch] Failed to get apps list: ${e.message}")
            null
        }
    }

    private fun getAllAppInfos(appsList: Any): List<*>? {
        return try {
            appsList.current().method {
                name = "getApps"
                superClass(true)
            }.call() as? List<*>
        } catch (e: Throwable) {
            try {
                val allAppsStore =
                    appsList.current().method { name = "getAllAppsStore"; superClass(true) }.call()
                allAppsStore?.current()?.method { name = "getApps"; superClass(true) }
                    ?.call() as? List<*>
            } catch (e2: Throwable) {
                Log.e(TAG, "[FuzzySearch] Failed to get app infos: ${e2.message}")
                null
            }
        }
    }

    private fun PackageParam.scoreSearchResults(
        appInfos: List<*>,
        query: String
    ): List<FuzzyMatchResult> {
        val scoredResults = ArrayList<FuzzyMatchResult>()
        val appInfoClass = APP_INFO_CLASS.toClass(appClassLoader)
        val queryLower = query.lowercase()

        appInfos.filterNotNull().forEach { appInfoObj ->
            try {
                if (!appInfoClass.isInstance(appInfoObj)) return@forEach

                val appInfo = appInfoClass.cast(appInfoObj)
                val titleField = appInfo?.javaClass?.field { name = "title"; superClass(true) }
                val appName = titleField?.get(appInfo)?.any()?.toString() ?: ""
                val appNameLower = appName.lowercase()
                val matchScore = scoreLabel(appNameLower, queryLower, appName, query)

                appInfo?.let {
                    FuzzyMatchResult(
                        itemInfo = it,
                        score = matchScore.score,
                        koreanMatchPriority = matchScore.koreanPriority,
                        directMatchPriority = matchScore.directPriority,
                        appName = appName
                    )
                }
                    ?.let { scoredResults.add(it) }
            } catch (e: Throwable) {
                Log.e(TAG, "[FuzzySearch] Error processing app: ${e.message}")
            }
        }

        return scoredResults
    }

    private data class MatchScore(
        val score: Int,
        val koreanPriority: Int,
        val directPriority: Int
    )

    private fun scoreLabel(
        labelLower: String,
        queryLower: String,
        rawLabel: String,
        rawQuery: String
    ): MatchScore {
        val koreanMatch = KoreanSearchUtils.score(rawLabel, rawQuery)
        val directPriority = when {
            queryLower.isEmpty() -> 0
            labelLower == queryLower -> 3
            labelLower.startsWith(queryLower) -> 2
            labelLower.contains(queryLower) -> 1
            else -> 0
        }

        return MatchScore(
            score = calculateMatchScore(labelLower, queryLower, koreanMatch.score),
            koreanPriority = koreanMatch.priority,
            directPriority = directPriority
        )
    }

    private fun calculateMatchScore(labelLower: String, queryLower: String, koreanScore: Int): Int {
        val baseScore = try {
            FuzzySearch.weightedRatio(labelLower, queryLower)
        } catch (t: Throwable) {
            0
        }

        val multiplier = when {
            queryLower.isEmpty() -> 1.0
            labelLower.startsWith(queryLower) -> PREFIX_MATCH_MULTIPLIER
            labelLower.contains(queryLower) -> SUBSTRING_MATCH_MULTIPLIER
            isSubsequence(labelLower, queryLower) -> SUBSEQUENCE_MATCH_MULTIPLIER
            else -> 1.0
        }

        return maxOf((baseScore * multiplier).roundToInt(), koreanScore)
    }

    private fun isSubsequence(text: String, pattern: String): Boolean {
        if (pattern.isEmpty()) return true
        var textIndex = 0
        var patternIndex = 0
        while (textIndex < text.length && patternIndex < pattern.length) {
            if (text[textIndex] == pattern[patternIndex]) {
                patternIndex++
            }
            textIndex++
        }
        return patternIndex == pattern.length
    }

    private fun PackageParam.convertToAdapterItems(scoredResults: List<FuzzyMatchResult>): ArrayList<Any> {
        val sortedResults = scoredResults.sortedWith(
            compareByDescending<FuzzyMatchResult> {
                it.koreanMatchPriority
            }.thenByDescending {
                it.directMatchPriority
            }.thenByDescending { it.score }
        )

        val finalAdapterItems = ArrayList<Any>()
        val adapterItemClass = BASE_ADAPTER_ITEM_CLASS.toClass(appClassLoader)
        val appInfoClass = APP_INFO_CLASS.toClass(appClassLoader)

        sortedResults.forEach { result ->
            try {
                val adapterItem = createAppAdapterItem(adapterItemClass, appInfoClass, result.itemInfo)

                if (adapterItem != null) {
                    finalAdapterItems.add(adapterItem)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "[FuzzySearch] Error converting ${result.appName}: ${e.message}")
            }
        }

        return finalAdapterItems
    }

    private fun createAppAdapterItem(
        adapterItemClass: Class<*>,
        appInfoClass: Class<*>,
        appInfo: Any
    ): Any? {
        return adapterItemClass.method {
            name = "asApp"
            param(appInfoClass)
            modifiers { isStatic }
        }.get().call(appInfo)
    }
}
