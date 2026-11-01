package com.wizpizz.onepluspluslauncher.hook.features

import android.util.Log
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_LEFT_SWIPE_DISCOVER_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_LEFT_SWIPE_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG

/**
 * Intercepts the left-swipe (Discover/Feed/News) overlay and opens the app drawer instead.
 * Hooks OplusLauncherOverlay.onScrollInteractionBegin().
 * 
 * Features:
 * - Toggle to enable/disable the redirect
 * - Optional auto-focus on search input when redirecting
 */
object LeftSwipeDiscoverRedirectHook {
    private const val OPLUS_LAUNCHER_OVERLAY_CLASS = "com.android.overlay.OplusLauncherOverlay"

    private val hookedOverlayClasses = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Class<*>, Boolean>())

    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            // OOS16 keeps the class name; ColorOS 17 obfuscates it (z9.c in 17.3.12). In both, the
            // overlay is handed to Launcher.setLauncherOverlay(), so hook whatever class arrives there.
            OPLUS_LAUNCHER_OVERLAY_CLASS.toClassOrNull(appClassLoader)?.let { hookOverlayClass(it) }

            val overlayInterface = "com.android.systemui.plugins.shared.LauncherOverlayManager\$LauncherOverlay"
                .toClassOrNull(appClassLoader)
            val setOverlay = overlayInterface?.let {
                findMethodQuietly(HookUtils.LAUNCHER_CLASS.toClassOrNull(appClassLoader), listOf("setLauncherOverlay"), it)
            }
            if (setOverlay == null) {
                Log.e(TAG, "[LeftSwipe] Launcher.setLauncherOverlay not found")
                return@apply
            }
            setOverlay.hook {
                before {
                    val overlay = args.getOrNull(0) ?: return@before
                    hookOverlayClass(overlay.javaClass)
                }
            }
        }
    }

    private fun PackageParam.hookOverlayClass(overlayClass: Class<*>) {
        if (!hookedOverlayClasses.add(overlayClass)) return
        val method = findMethodQuietly(overlayClass) {
            it.name == "onScrollInteractionBegin" && it.parameterTypes.isEmpty() &&
                !java.lang.reflect.Modifier.isAbstract(it.modifiers)
        } ?: run {
            Log.e(TAG, "[LeftSwipe] ${overlayClass.name}.onScrollInteractionBegin not found")
            return
        }
        Log.d(TAG, "[LeftSwipe] Hooking ${overlayClass.name}.onScrollInteractionBegin")
        method.hook {
                before {
                    // Check if left-swipe redirect is enabled
                    val leftSwipeRedirectEnabled = prefs.getBoolean(PREF_LEFT_SWIPE_DISCOVER_REDIRECT, true)
                    if (!leftSwipeRedirectEnabled) {
                        Log.d(TAG, "[LeftSwipe] Feature disabled, allowing original behavior")
                        return@before
                    }
                    
                    Log.d(TAG, "[LeftSwipe] Intercepting left-swipe overlay, redirecting to app drawer")
                    
                    // Mark that we're starting a redirect to prevent AutoFocusHook from triggering
                    HookUtils.setRedirectInProgress(true)
                    
                    // Get the Launcher instance from the overlay
                    val launcherClass = HookUtils.LAUNCHER_CLASS.toClassOrNull(appClassLoader)
                    val launcher = launcherClass?.let { findFieldByType(instance.javaClass, it)?.get(instance) }
                    
                    if (launcher != null) {
                        try {
                            // Try to call showAllAppsFromIntent(true) on the launcher
                            launcher.current().method {
                                name = "showAllAppsFromIntent"
                                param(Boolean::class.java)
                            }.call(true)
                            Log.d(TAG, "[LeftSwipe] Successfully opened app drawer from left swipe")
                            
                            // If redirect was successful and auto focus on redirect is enabled, focus search
                            val autoFocusRedirectEnabled = prefs.getBoolean(PREF_AUTO_FOCUS_LEFT_SWIPE_REDIRECT, true)
                            if (autoFocusRedirectEnabled) {
                                Log.d(TAG, "[LeftSwipe] Auto focus enabled, focusing search input")
                                appClassLoader?.let { HookUtils.focusSearchInput(launcher, it) }
                                // Clear redirect flag after focusing
                                HookUtils.setRedirectInProgress(false)
                            } else {
                                Log.d(TAG, "[LeftSwipe] Auto focus disabled, clearing redirect flag")
                                // Clear redirect flag immediately if not focusing
                                HookUtils.setRedirectInProgress(false)
                            }
                            
                        } catch (e: Throwable) {
                            Log.e(TAG, "[LeftSwipe] Failed to open app drawer: ${e.message}")
                            // Reset flag if redirect failed
                            HookUtils.setRedirectInProgress(false)
                        }
                    } else {
                        Log.e(TAG, "[LeftSwipe] Failed to get launcher instance from overlay")
                        // Reset flag if we couldn't get launcher
                        HookUtils.setRedirectInProgress(false)
                    }
                    
                    // Prevent the overlay from opening
                    result = null
                    return@before
                }
        }
    }
}