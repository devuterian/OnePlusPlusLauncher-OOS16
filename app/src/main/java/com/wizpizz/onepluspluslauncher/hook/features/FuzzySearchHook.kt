package com.wizpizz.onepluspluslauncher.hook.features

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.util.Log
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.LAUNCHER_CLASS
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_INCLUDE_APP_SHORTCUTS_SEARCH
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_SEARCH_HISTORY_RECENCY
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_USE_FUZZY_SEARCH
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG
import java.lang.reflect.Field
import me.xdrop.fuzzywuzzy.FuzzySearch
import kotlin.math.roundToInt


object FuzzySearchHook {

    private const val SEARCH_CONTAINER_CLASS =
        "com.android.launcher3.allapps.search.LauncherTaskbarAppsSearchContainerLayout"
    private const val BASE_ADAPTER_ITEM_CLASS =
        "com.android.launcher3.allapps.BaseAllAppsAdapter\$AdapterItem"
    private const val APP_INFO_CLASS = "com.android.launcher3.model.data.AppInfo"
    private const val WORKSPACE_ITEM_INFO_CLASS = "com.android.launcher3.model.data.WorkspaceItemInfo"
    private const val ARRAY_LIST_CLASS = "java.util.ArrayList"

    private const val PREFIX_MATCH_MULTIPLIER = 1.5
    private const val SUBSTRING_MATCH_MULTIPLIER = 1.3
    private const val SUBSEQUENCE_MATCH_MULTIPLIER = 1.1
    private const val SHORTCUT_MIN_SCORE = 55
    private const val SHORTCUT_CACHE_WINDOW_MS = 10_000L

    // Shared state for SwipeDownSearchRedirectHook coordination
    @Volatile
    var searchContainerInstance: Any? = null
    @Volatile
    var lastRedirectTime = 0L

    // Anti-flash: timestamp when history was injected, blocks reset() for 2s
    @Volatile
    private var historyInjectedTime = 0L
    private const val HISTORY_LOCK_WINDOW_MS = 2000L

    @Volatile
    private var shortcutCacheTime = 0L
    @Volatile
    private var shortcutCache: List<ShortcutInfo> = emptyList()

    data class FuzzyMatchResult(
        val itemInfo: Any,
        val parentAppInfo: Any?,
        val isShortcut: Boolean,
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
                    val includeShortcuts = try {
                        prefs.getBoolean(PREF_INCLUDE_APP_SHORTCUTS_SEARCH, false)
                    } catch (_: Throwable) {
                        false
                    }

                    try {
                        val sortedResults = performFuzzySearch(instance, sanitizedQuery, includeShortcuts)
                        if (sortedResults.isNotEmpty()) {
                            args[1] = sortedResults
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "[FuzzySearch] Error during fuzzy search: ${e.message}")
                    }
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

    private fun PackageParam.performFuzzySearch(
        containerInstance: Any,
        query: String,
        includeShortcuts: Boolean
    ): ArrayList<Any> {
        val appsList = getAppsListFromContainer(containerInstance) ?: return ArrayList()
        val allAppInfos = getAllAppInfos(appsList) ?: return ArrayList()
        val scoredResults = ArrayList<FuzzyMatchResult>()
        scoredResults.addAll(scoreSearchResults(allAppInfos, query))
        if (includeShortcuts) {
            scoredResults.addAll(scoreShortcutResults(containerInstance, allAppInfos, query))
        }
        return convertToAdapterItems(containerInstance, scoredResults)
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
                        parentAppInfo = it,
                        isShortcut = false,
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

    private fun PackageParam.scoreShortcutResults(
        containerInstance: Any,
        appInfos: List<*>,
        query: String
    ): List<FuzzyMatchResult> {
        val context = (containerInstance as? android.view.View)?.context ?: return emptyList()
        val queryLower = query.lowercase()
        val parentApps = buildParentAppMap(appInfos)
        val scoredResults = ArrayList<FuzzyMatchResult>()

        getShortcutInfos(context).forEach { shortcutInfo ->
            try {
                val parentAppInfo = parentApps[shortcutInfo.`package`] ?: return@forEach
                val shortcutLabel = shortcutInfo.longLabel?.toString()
                    ?: shortcutInfo.shortLabel?.toString()
                    ?: shortcutInfo.id
                val shortcutLabelLower = shortcutLabel.lowercase()
                val matchScore = scoreLabel(shortcutLabelLower, queryLower, shortcutLabel, query)
                if (
                    matchScore.score < SHORTCUT_MIN_SCORE &&
                    matchScore.directPriority == 0 &&
                    matchScore.koreanPriority == 0
                ) {
                    return@forEach
                }

                scoredResults.add(
                    FuzzyMatchResult(
                        itemInfo = shortcutInfo,
                        parentAppInfo = parentAppInfo,
                        isShortcut = true,
                        score = matchScore.score,
                        koreanMatchPriority = matchScore.koreanPriority,
                        directMatchPriority = matchScore.directPriority,
                        appName = shortcutLabel
                    )
                )
            } catch (e: Throwable) {
                Log.d(TAG, "[FuzzySearch] Error scoring shortcut: ${e.message}")
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

    private fun PackageParam.convertToAdapterItems(
        containerInstance: Any,
        scoredResults: List<FuzzyMatchResult>
    ): ArrayList<Any> {
        val sortedResults = scoredResults.sortedWith(
            compareByDescending<FuzzyMatchResult> {
                it.koreanMatchPriority
            }.thenByDescending {
                it.directMatchPriority
            }.thenByDescending { it.score }
        )

        val finalAdapterItems = ArrayList<Any>()
        val context = (containerInstance as? android.view.View)?.context
        val adapterItemClass = BASE_ADAPTER_ITEM_CLASS.toClass(appClassLoader)
        val appInfoClass = APP_INFO_CLASS.toClass(appClassLoader)

        sortedResults.forEach { result ->
            try {
                val adapterItem = if (result.isShortcut && context != null) {
                    createShortcutAdapterItem(context, adapterItemClass, appInfoClass, result)
                } else {
                    createAppAdapterItem(adapterItemClass, appInfoClass, result.itemInfo)
                }

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

    private fun PackageParam.createShortcutAdapterItem(
        context: Context,
        adapterItemClass: Class<*>,
        appInfoClass: Class<*>,
        result: FuzzyMatchResult
    ): Any? {
        val shortcutInfo = result.itemInfo as? ShortcutInfo ?: return null
        val parentAppInfo = result.parentAppInfo ?: return null
        val workspaceItemInfo = createWorkspaceItemInfo(context, shortcutInfo) ?: return null

        val directAdapterItem = try {
            adapterItemClass.method {
                name = "asShortcut"
                param(workspaceItemInfo.javaClass)
                modifiers { isStatic }
            }.get().call(workspaceItemInfo)
        } catch (_: Throwable) {
            null
        } ?: try {
            adapterItemClass.method {
                name = "asDeepShortcut"
                param(workspaceItemInfo.javaClass)
                modifiers { isStatic }
            }.get().call(workspaceItemInfo)
        } catch (_: Throwable) {
            null
        }

        if (directAdapterItem != null) return directAdapterItem

        return createAppAdapterItem(adapterItemClass, appInfoClass, parentAppInfo)?.also { adapterItem ->
            setFieldValue(adapterItem, "itemInfo", workspaceItemInfo)
            setFieldValue(adapterItem, "appInfo", parentAppInfo)
        }
    }

    private fun PackageParam.createWorkspaceItemInfo(
        context: Context,
        shortcutInfo: ShortcutInfo
    ): Any? {
        return try {
            val workspaceItemInfoClass = WORKSPACE_ITEM_INFO_CLASS.toClass(appClassLoader)
            workspaceItemInfoClass.getDeclaredConstructor(ShortcutInfo::class.java, Context::class.java)
                .apply { isAccessible = true }
                .newInstance(shortcutInfo, context)
        } catch (e: Throwable) {
            Log.d(TAG, "[FuzzySearch] Failed to create WorkspaceItemInfo: ${e.message}")
            null
        }
    }

    private fun getShortcutInfos(context: Context): List<ShortcutInfo> {
        val now = System.currentTimeMillis()
        if (now - shortcutCacheTime < SHORTCUT_CACHE_WINDOW_MS) return shortcutCache

        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val shortcuts = ArrayList<ShortcutInfo>()
        val flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST

        launcherApps.profiles.forEach { userHandle ->
            try {
                val query = LauncherApps.ShortcutQuery().apply {
                    setQueryFlags(flags)
                }
                launcherApps.getShortcuts(query, userHandle)?.let { shortcuts.addAll(it) }
            } catch (e: Throwable) {
                Log.d(TAG, "[FuzzySearch] Failed to query shortcuts for $userHandle: ${e.message}")
            }
        }

        return shortcuts.distinctBy { shortcut ->
            "${shortcut.userHandle.hashCode()}:${shortcut.`package`}:${shortcut.id}"
        }.also {
            shortcutCache = it
            shortcutCacheTime = now
        }
    }

    private fun buildParentAppMap(appInfos: List<*>): Map<String, Any> {
        val parentApps = LinkedHashMap<String, Any>()
        appInfos.filterNotNull().forEach { appInfo ->
            val packageName = getAppInfoPackageName(appInfo)
            if (packageName != null && !parentApps.containsKey(packageName)) {
                parentApps[packageName] = appInfo
            }
        }
        return parentApps
    }

    private fun getAppInfoPackageName(appInfo: Any): String? {
        val componentName = getFieldValue(appInfo, "componentName") as? ComponentName
            ?: try {
                appInfo.current().method { name = "getTargetComponent"; superClass(true) }.call() as? ComponentName
            } catch (_: Throwable) {
                null
            }
        return componentName?.packageName
    }

    private fun getFieldValue(instance: Any, fieldName: String): Any? {
        return findField(instance.javaClass, fieldName)?.let { field ->
            field.isAccessible = true
            field.get(instance)
        }
    }

    private fun setFieldValue(instance: Any, fieldName: String, value: Any?) {
        findField(instance.javaClass, fieldName)?.let { field ->
            field.isAccessible = true
            field.set(instance, value)
        }
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
