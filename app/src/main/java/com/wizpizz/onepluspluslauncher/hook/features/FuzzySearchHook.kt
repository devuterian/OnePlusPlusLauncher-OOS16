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
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_SEARCH_ALIASES
import java.lang.reflect.Field
import java.lang.reflect.Method


object FuzzySearchHook {

    private const val SEARCH_CONTAINER_CLASS =
        "com.android.launcher3.allapps.search.LauncherTaskbarAppsSearchContainerLayout"
    private const val BASE_ADAPTER_ITEM_CLASS =
        "com.android.launcher3.allapps.BaseAllAppsAdapter\$AdapterItem"
    private const val APP_INFO_CLASS = "com.android.launcher3.model.data.AppInfo"
    private const val ARRAY_LIST_CLASS = "java.util.ArrayList"

    private const val SEARCH_ALGORITHM_CLASS =
        "com.android.launcher3.allapps.search.OplusDefaultAppSearchAlgorithm"
    private const val DEEP_PROTECTED_APPS_CLASS = "com.android.launcher.filter.DeepProtectedAppsManager"
    private const val ITEM_INFO_CLASS = "com.android.launcher3.model.data.ItemInfo"
    private const val ALIAS_REFRESH_MS = 5000L

    // Shared state for SwipeDownSearchRedirectHook coordination
    @Volatile
    var searchContainerInstance: Any? = null
    @Volatile
    var lastRedirectTime = 0L

    // Anti-flash: timestamp when history was injected, blocks reset() for 2s
    @Volatile
    private var historyInjectedTime = 0L
    private const val HISTORY_LOCK_WINDOW_MS = 2000L

    // Reflection handles, resolved once per process.
    private var titleField: Field? = null
    private var componentField: Field? = null
    private var userField: Field? = null
    private var asAppMethod: Method? = null
    private var algorithmContextField: Field? = null
    private var hiddenAppsGetInstance: Method? = null
    private var hiddenAppsCheck: Method? = null
    private var hiddenAppsLookupFailed = false

    @Volatile private var aliases: List<AppSearchEngine.Alias> = AppSearchEngine.buildAliases(null)
    @Volatile private var aliasText: String? = null
    @Volatile private var aliasCheckedAt = 0L

    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            hookTitleMatchResult()
            hookLaunchTracking()

            // Cache the container for SwipeDownSearchRedirectHook and keep result icons from fading in
            SEARCH_CONTAINER_CLASS.toClassOrNull(appClassLoader)?.method {
                name = "onSearchResult"
                param(String::class.java.name, ARRAY_LIST_CLASS)
            }?.hook {
                before { searchContainerInstance = instance }
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
        }
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

    /**
     * Replaces the launcher's title matcher. It already runs on the launcher's model thread,
     * so ranking never blocks the UI thread.
     */
    private fun PackageParam.hookTitleMatchResult() {
        val algorithmClass = SEARCH_ALGORITHM_CLASS.toClassOrNull(appClassLoader) ?: run {
            Log.e(TAG, "[FuzzySearch] $SEARCH_ALGORITHM_CLASS not found")
            return
        }
        algorithmClass.method {
            name = "getTitleMatchResult"
            param(List::class.java, String::class.java)
        }.hook {
            before {
                val query = args[1] as? String ?: return@before
                if (AppSearchEngine.normalize(query).isEmpty()) return@before
                val useFuzzySearch = try { prefs.getBoolean(PREF_USE_FUZZY_SEARCH, true) } catch (_: Throwable) { true }
                if (!useFuzzySearch) return@before
                val apps = args[0] as? List<*> ?: return@before
                try {
                    val started = System.nanoTime()
                    val results = searchApps(instance, apps, query)
                    // No match: let the launcher's own search try (pinyin, system aliases).
                    if (!results.isNullOrEmpty()) {
                        result = results
                        Log.d(TAG, "[FuzzySearch] query(len=${query.length}) -> ${results.size} results in ${(System.nanoTime() - started) / 1000}us")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "[FuzzySearch] search failed, using launcher search: ${e.message}")
                }
            }
        }
    }

    private fun PackageParam.searchApps(algorithm: Any, apps: List<*>, query: String): ArrayList<Any>? {
        val classLoader = appClassLoader ?: return null
        val context = algorithmContext(algorithm) ?: return null
        LauncherSearchIndex.ensureInitialized(context)
        refreshAliases()
        if (!resolveAppInfoHandles(classLoader)) return null

        val candidates = ArrayList<Pair<Any, AppSearchEngine.Entry>>(apps.size)
        val needEnglish = ArrayList<android.content.ComponentName>()
        for (app in apps) {
            if (app == null) continue
            val component = componentField?.get(app) as? android.content.ComponentName ?: continue
            // If the privacy-lock check is unavailable, fall back to stock search so hidden apps stay hidden.
            val hidden = isHiddenApp(context, app, classLoader) ?: return null
            if (hidden) continue
            val label = (titleField?.get(app) as? CharSequence)?.toString() ?: continue
            val user = userField?.get(app) as? android.os.UserHandle
            val entry = LauncherSearchIndex.entryFor(LauncherSearchIndex.appId(component, user), component, label)
            if (entry.labelIsHangul || label.none(KeyboardLayoutUtils::isLatinLetter)) needEnglish.add(component)
            candidates.add(app to entry)
        }
        LauncherSearchIndex.requestEnglishLabels(needEnglish)
        LauncherSearchIndex.retainOnly(candidates)

        val now = System.currentTimeMillis()
        val ranked = AppSearchEngine.rank(candidates, query, aliases) { id -> LauncherSearchIndex.usage.valueOf(id, now) }
        val asApp = asAppMethod ?: return null
        val output = ArrayList<Any>(ranked.size)
        ranked.forEachIndexed { index, item ->
            asApp.invoke(null, index, "", item.item)?.let(output::add)
        }
        return output
    }

    private fun PackageParam.refreshAliases() {
        val now = System.currentTimeMillis()
        if (now - aliasCheckedAt < ALIAS_REFRESH_MS) return
        aliasCheckedAt = now
        val text = try { prefs.getString(PREF_SEARCH_ALIASES, "") } catch (_: Throwable) { "" }
        if (text != aliasText) {
            aliasText = text
            aliases = AppSearchEngine.buildAliases(text)
        }
    }

    private fun algorithmContext(algorithm: Any): android.content.Context? {
        val field = algorithmContextField ?: findField(algorithm.javaClass, "mContext")?.also { algorithmContextField = it }
        return field?.get(algorithm) as? android.content.Context
    }

    private fun resolveAppInfoHandles(classLoader: ClassLoader): Boolean {
        if (asAppMethod != null) return true
        return try {
            val appInfoClass = APP_INFO_CLASS.toClassOrNull(classLoader) ?: return false
            titleField = appInfoClass.getField("title")
            componentField = appInfoClass.getField("componentName")
            userField = appInfoClass.getField("user")
            asAppMethod = BASE_ADAPTER_ITEM_CLASS.toClassOrNull(classLoader)
                ?.getMethod("asApp", Int::class.javaPrimitiveType, String::class.java, appInfoClass)
            asAppMethod != null
        } catch (e: Throwable) {
            Log.e(TAG, "[FuzzySearch] AppInfo fields not found: ${e.message}")
            false
        }
    }

    /**
     * Apps hidden with the system privacy lock must stay out of search, like stock search.
     * Returns null when the check is unavailable.
     */
    private fun isHiddenApp(context: android.content.Context, appInfo: Any, classLoader: ClassLoader): Boolean? {
        if (hiddenAppsLookupFailed) return null
        return try {
            if (hiddenAppsCheck == null) {
                val managerClass = DEEP_PROTECTED_APPS_CLASS.toClassOrNull(classLoader)
                    ?: throw ClassNotFoundException(DEEP_PROTECTED_APPS_CLASS)
                hiddenAppsGetInstance = managerClass.getMethod("getInstance", android.content.Context::class.java)
                hiddenAppsCheck = managerClass.methods.first {
                    it.name == "isAppHiddenEntryAndSupportPrivacyLock" && it.parameterTypes.size == 1
                }
            }
            val manager = hiddenAppsGetInstance?.invoke(null, context)
            hiddenAppsCheck?.invoke(manager, appInfo) as? Boolean
        } catch (e: NoSuchMethodException) {
            hiddenAppsLookupFailed = true
            Log.e(TAG, "[FuzzySearch] hidden-app check unavailable, using launcher search: ${e.message}")
            null
        } catch (e: NoSuchElementException) {
            hiddenAppsLookupFailed = true
            Log.e(TAG, "[FuzzySearch] hidden-app check unavailable, using launcher search: ${e.message}")
            null
        } catch (e: Throwable) {
            Log.e(TAG, "[FuzzySearch] hidden-app check failed: ${e.message}")
            null
        }
    }

    /** Counts launches (home, drawer, search) for frequency ranking. */
    private fun PackageParam.hookLaunchTracking() {
        val itemInfoClass = ITEM_INFO_CLASS.toClassOrNull(appClassLoader) ?: return
        LAUNCHER_CLASS.toClassOrNull(appClassLoader)?.method {
            name = "startActivitySafely"
            param(android.view.View::class.java, android.content.Intent::class.java, itemInfoClass)
        }?.hook {
            before {
                try {
                    val intent = args[1] as? android.content.Intent
                    val itemInfo = args[2]
                    val component = (itemInfo?.let {
                        it.javaClass.getMethod("getTargetComponent").invoke(it) as? android.content.ComponentName
                    }) ?: intent?.component ?: return@before
                    val user = itemInfo?.let { findField(it.javaClass, "user")?.get(it) as? android.os.UserHandle }
                    (instance as? android.content.Context)?.let(LauncherSearchIndex::ensureInitialized)
                    LauncherSearchIndex.recordLaunch(LauncherSearchIndex.appId(component, user))
                } catch (e: Throwable) {
                    Log.d(TAG, "[FuzzySearch] launch tracking skipped: ${e.message}")
                }
            }
        } ?: Log.e(TAG, "[FuzzySearch] startActivitySafely not found")
    }

    private fun findField(start: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = start
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
