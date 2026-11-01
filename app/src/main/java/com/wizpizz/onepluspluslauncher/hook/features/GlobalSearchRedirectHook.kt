package com.wizpizz.onepluspluslauncher.hook.features

import android.util.Log
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.IntentClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_GLOBAL_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG

/**
 * Intercepts global search redirects to third-party apps (like QuickSearchBox)
 * and redirects them to the All Apps search instead.
 * Also provides optional auto-focus when redirecting.
 * 
 * Updated for System Launcher 15.8.17+ which uses IndicatorEntry instead of SearchEntry.
 * Still supports legacy SearchEntry for older versions.
 */
object GlobalSearchRedirectHook {
    
    private const val INDICATOR_ENTRY_CLASS = "com.android.launcher3.search.IndicatorEntry"
    private const val SEARCH_ENTRY_CLASS = "com.android.launcher3.search.SearchEntry"
    private val QUICK_SEARCH_BOX_PACKAGES = setOf(
        "com.oppo.quicksearchbox",   // OxygenOS 16
        "com.heytap.quicksearchbox"  // ColorOS 17
    )
    private val SEARCH_ACTIONS = setOf(
        "android.search.action.GLOBAL_SEARCH",
        "android.search.action.DOCK_SEARCH"
    )

    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            // OOS16: boolean startIndicatorApp(Intent); ColorOS 17: void startIndicatorApp(Intent).
            hookIndicatorEntry(INDICATOR_ENTRY_CLASS, "startIndicatorApp")
            hookIndicatorEntry(SEARCH_ENTRY_CLASS, "startSearchApp")
        }
    }

    private fun PackageParam.hookIndicatorEntry(className: String, methodName: String) {
        val entryClass = className.toClassOrNull(appClassLoader) ?: run {
            Log.d(TAG, "[GlobalSearch] $className not present")
            return
        }
        val method = findMethodQuietly(entryClass, listOf(methodName), android.content.Intent::class.java) ?: run {
            Log.d(TAG, "[GlobalSearch] $className.$methodName not found")
            return
        }
        val returnsBoolean = method.returnType == Boolean::class.javaPrimitiveType
        method.hook {
            before {
                // Check if global search redirect is enabled
                val globalSearchRedirectEnabled = prefs.getBoolean(PREF_GLOBAL_SEARCH_REDIRECT, true)
                if (!globalSearchRedirectEnabled) return@before

                val intentToLaunch = args[0] as? android.content.Intent
                if (!isQuickSearchIntent(intentToLaunch, entryClass)) return@before

                Log.d(TAG, "[GlobalSearch] Intercepting QuickSearchBox launch, redirecting to All Apps")

                // Mark that we're starting a redirect to prevent AutoFocusHook from triggering
                HookUtils.setRedirectInProgress(true)

                if (redirectToAllApps(instance)) {
                    result = if (returnsBoolean) false else null // Prevent original method
                } else {
                    // Reset flag if redirect failed
                    HookUtils.setRedirectInProgress(false)
                }
            }
        }
    }

    /**
     * A null intent means "the entry's default app". On ColorOS 17 that default can be the
     * Breeno assistant instead of search, so only treat null as search when Breeno isn't used.
     */
    private fun isQuickSearchIntent(intent: android.content.Intent?, entryClass: Class<*>): Boolean {
        if (intent == null) {
            val usingBreeno = findMethodQuietly(entryClass) {
                it.name == "isUsingBreenoEntry" && it.parameterTypes.isEmpty() &&
                    java.lang.reflect.Modifier.isStatic(it.modifiers)
            }?.let { runCatching { it.invoke(null) as? Boolean }.getOrNull() } ?: false
            return !usingBreeno
        }
        val pkg = intent.`package` ?: intent.component?.packageName
        return pkg in QUICK_SEARCH_BOX_PACKAGES || intent.action in SEARCH_ACTIONS
    }

    private fun PackageParam.redirectToAllApps(indicatorEntryInstance: Any): Boolean {
        return try {
            val launcherInstance = getLauncherFromIndicatorEntry(indicatorEntryInstance)
            if (launcherInstance == null) {
                Log.e(TAG, "[GlobalSearch] Failed to get launcher instance")
                return false
            }
            
            // Try primary method
            val success = try {
                launcherInstance.current().method { 
                    name = "showAllAppsFromIntent"
                    param(BooleanType) 
                }.call(true)
                Log.d(TAG, "[GlobalSearch] Called showAllAppsFromIntent successfully")
                true
            } catch (e: Throwable) {
                Log.w(TAG, "[GlobalSearch] showAllAppsFromIntent failed, trying TaskbarUtils: ${e.message}")
                
                // Fallback to TaskbarUtils
                try {
                    val launcherContext = launcherInstance as? android.content.Context ?: return false
                    "com.android.launcher3.taskbar.TaskbarUtils".toClass(appClassLoader).method { 
                        name = "showAllApps"
                        param(launcherContext.javaClass)
                        modifiers { isStatic } 
                    }.get().call(launcherContext)
                    Log.d(TAG, "[GlobalSearch] Called TaskbarUtils.showAllApps successfully")
                    true
                } catch (e2: Throwable) {
                    Log.e(TAG, "[GlobalSearch] TaskbarUtils.showAllApps also failed: ${e2.message}")
                    false
                }
            }
            
            // If redirect was successful and auto focus on redirect is enabled, focus search
            if (success) {
                FuzzySearchHook.lastRedirectTime = System.currentTimeMillis()
                val autoFocusRedirectEnabled = prefs.getBoolean(PREF_AUTO_FOCUS_SEARCH_REDIRECT, true)
                if (autoFocusRedirectEnabled) {
                    HookUtils.setRedirectInProgress(true)
                    appClassLoader?.let { classLoader ->
                        try {
                            HookUtils.focusSearchInputOnce(launcherInstance, classLoader)
                        } finally {
                            HookUtils.setRedirectInProgress(false)
                        }
                    } ?: HookUtils.setRedirectInProgress(false)
                } else {
                    // Reset flag immediately if auto focus on redirect is disabled
                    HookUtils.setRedirectInProgress(false)
                    Log.d(TAG, "[GlobalSearch] Auto focus on redirect disabled - reset flag")
                }
            } else {
                // Reset flag if redirect failed
                HookUtils.setRedirectInProgress(false)
                Log.d(TAG, "[GlobalSearch] Redirect failed - reset flag")
            }
            
            return success
        } catch (e: Throwable) {
            Log.e(TAG, "[GlobalSearch] Error redirecting to All Apps: ${e.message}")
            false
        }
    }
    
    private fun getLauncherFromIndicatorEntry(indicatorEntryInstance: Any): Any? {
        return try {
            val launcherClass = HookUtils.LAUNCHER_CLASS.toClassOrNull(indicatorEntryInstance.javaClass.classLoader)
            val field = findFieldByType(indicatorEntryInstance.javaClass, launcherClass ?: return null)
            field?.get(indicatorEntryInstance)
        } catch (e: Exception) {
            Log.e(TAG, "[GlobalSearch] Error getting mLauncher: ${e.message}")
            null
        }
    }
}
