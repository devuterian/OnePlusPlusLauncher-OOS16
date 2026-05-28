package com.wizpizz.onepluspluslauncher.hook.features

import android.util.Log
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.LAUNCHER_CLASS
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.LAUNCHER_STATE_CLASS
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SEARCH_SWIPE
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG

/**
 * Auto-focuses search input when swiping up to enter All Apps state
 */
object SwipeUpAutoFocusHook {
    
    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            hookStateSetStart()
            hookStateTransition("onStateSetEnd")
        }
    }

    private fun PackageParam.hookStateSetStart() {
        val launcherClass = LAUNCHER_CLASS.toClassOrNull(appClassLoader) ?: return
        val launcherStateClass = LAUNCHER_STATE_CLASS.toClassOrNull(appClassLoader) ?: return

        launcherClass.method {
            name = "onStateSetStart"
            param(launcherStateClass)
        }?.hook {
            after {
                val autoFocusSwipeEnabled = prefs.getBoolean(PREF_AUTO_FOCUS_SEARCH_SWIPE, true)
                handleStateTransition(args.lastOrNull(), instance, appClassLoader, "onStateSetStart/1", autoFocusSwipeEnabled)
            }
        }

        launcherClass.method {
            name = "onStateSetStart"
            param(launcherStateClass, launcherStateClass)
        }?.hook {
            after {
                val autoFocusSwipeEnabled = prefs.getBoolean(PREF_AUTO_FOCUS_SEARCH_SWIPE, true)
                handleStateTransition(args.lastOrNull(), instance, appClassLoader, "onStateSetStart/2", autoFocusSwipeEnabled)
            }
        } ?: Log.d(TAG, "[AutoFocus] onStateSetStart with two params not found")
    }

    private fun PackageParam.hookStateTransition(methodName: String) {
        LAUNCHER_CLASS.toClassOrNull(appClassLoader)?.method {
            name = methodName
            param(LAUNCHER_STATE_CLASS.toClassOrNull(appClassLoader) ?: return@method)
        }?.hook {
            after {
                val autoFocusSwipeEnabled = prefs.getBoolean(PREF_AUTO_FOCUS_SEARCH_SWIPE, true)
                handleStateTransition(args.lastOrNull(), instance, appClassLoader, methodName, autoFocusSwipeEnabled)
            }
        } ?: Log.d(TAG, "[AutoFocus] $methodName not found")
    }

    private fun handleStateTransition(
        targetState: Any?,
        launcherInstance: Any,
        classLoader: ClassLoader?,
        source: String,
        autoFocusSwipeEnabled: Boolean
    ) {
        if (targetState == null || classLoader == null) return

        if (!autoFocusSwipeEnabled) return

        if (HookUtils.isRedirectInProgress()) {
            Log.d(TAG, "[AutoFocus] Skipping swipe focus - redirect in progress")
            return
        }

        val launcherStateClass = LAUNCHER_STATE_CLASS.toClassOrNull(classLoader) ?: return
        val allAppsState = try {
            launcherStateClass.field { name = "ALL_APPS" }.get().any()
        } catch (_: Throwable) {
            null
        } ?: return

        if (targetState == allAppsState) {
            HookUtils.drawerOpenTime = System.currentTimeMillis()
            Log.d(TAG, "[AutoFocus] Focusing search input via $source")
            HookUtils.focusSearchInput(launcherInstance, classLoader)
        } else {
            HookUtils.drawerCloseTime = System.currentTimeMillis()
        }
    }
} 