package com.wizpizz.onepluspluslauncher.hook.features

import android.util.Log
import android.view.KeyEvent
import android.view.WindowInsets
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.LAUNCHER_CLASS
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.LAUNCHER_STATE_CLASS
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SEARCH_SWIPE
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG

/**
 * Auto-focuses search input when swiping up to enter All Apps state
 */
object SwipeUpAutoFocusHook {

    private const val OPLUS_ALL_APPS_CONTAINER_CLASS =
        "com.android.launcher3.allapps.OplusLauncherAllAppsContainerView"
    private const val EXTENDED_EDIT_TEXT_CLASS = "com.android.launcher3.ExtendedEditText"

    @Volatile
    private var lastSwipeFocusAt = 0L

    @Volatile
    private var searchImeWasVisible = false
    
    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            hookStateSetStart()
            hookScrollUpEnd()
            hookSearchBackKey()
            hookImeHiddenReset()
        }
    }

    private fun PackageParam.hookStateSetStart() {
        val launcherClass = LAUNCHER_CLASS.toClassOrNull(appClassLoader) ?: return
        val launcherStateClass = LAUNCHER_STATE_CLASS.toClassOrNull(appClassLoader) ?: return

        launcherClass.method {
            name = "onStateSetStart"
            param(launcherStateClass, launcherStateClass)
        }?.hook {
            after {
                if (!prefs.getBoolean(PREF_AUTO_FOCUS_SEARCH_SWIPE, true)) return@after
                if (!isAllAppsState(args.getOrNull(1), appClassLoader)) return@after

                val classLoader = appClassLoader ?: return@after
                val launcher = instance
                val view = launcher as? android.view.View
                val now = System.currentTimeMillis()
                if (now - lastSwipeFocusAt < 800L) return@after
                lastSwipeFocusAt = now
                HookUtils.drawerOpenTime = now

                val hostView = (launcher as? android.app.Activity)?.window?.decorView
                hostView?.postDelayed({
                    if (!HookUtils.isRedirectInProgress()) {
                        Log.d(TAG, "[AutoFocus] Entering search mode via onStateSetStart")
                        HookUtils.focusSearchInputOnce(launcher, classLoader)
                    }
                }, 120L)
            }
        } ?: Log.d(TAG, "[AutoFocus] onStateSetStart with two params not found")
    }

    private fun PackageParam.hookScrollUpEnd() {
        OPLUS_ALL_APPS_CONTAINER_CLASS.toClassOrNull(appClassLoader)?.method {
            name = "onScrollUpEnd"
        }?.hook {
            after {
                if (!prefs.getBoolean(PREF_AUTO_FOCUS_SEARCH_SWIPE, true)) return@after
                if (HookUtils.isRedirectInProgress()) {
                    Log.d(TAG, "[AutoFocus] Skipping swipe focus - redirect in progress")
                    return@after
                }

                val now = System.currentTimeMillis()
                val classLoader = appClassLoader ?: return@after
                val view = instance as? android.view.View ?: return@after
                val launcher = HookUtils.getLauncherFromContext(view.context, classLoader) ?: return@after
                val searchModeActive = HookUtils.isSearchModeActive(launcher, classLoader)
                if (now - lastSwipeFocusAt < 800L && searchModeActive) {
                    Log.d(TAG, "[AutoFocus] Skipping duplicate swipe focus")
                    return@after
                }
                if (!searchModeActive) {
                    lastSwipeFocusAt = now
                    HookUtils.drawerOpenTime = now
                    Log.d(TAG, "[AutoFocus] Fallback search mode via onScrollUpEnd")
                    HookUtils.focusSearchInputOnce(launcher, classLoader)
                }
            }
        } ?: Log.d(TAG, "[AutoFocus] onScrollUpEnd not found")
    }

    private fun PackageParam.hookSearchBackKey() {
        EXTENDED_EDIT_TEXT_CLASS.toClassOrNull(appClassLoader)?.method {
            name = "onKeyPreIme"
            param(IntType, KeyEvent::class.java)
        }?.hook {
            before {
                val keyCode = args.getOrNull(0) as? Int ?: return@before
                val event = args.getOrNull(1) as? KeyEvent ?: return@before
                if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) return@before

                val editText = instance as? android.widget.EditText ?: return@before
                val classLoader = appClassLoader ?: return@before
                val launcher = HookUtils.getLauncherFromContext(editText.context, classLoader) ?: return@before
                if (HookUtils.resetSearchModeIfActive(launcher, classLoader)) {
                    Log.d(TAG, "[AutoFocus] Consumed back key and reset search mode")
                    result = true
                }
            }
        } ?: Log.d(TAG, "[AutoFocus] ExtendedEditText.onKeyPreIme not found")
    }

    private fun PackageParam.hookImeHiddenReset() {
        OPLUS_ALL_APPS_CONTAINER_CLASS.toClassOrNull(appClassLoader)?.method {
            name = "dispatchApplyWindowInsets"
            param(WindowInsets::class.java)
        }?.hook {
            after {
                val insets = args.getOrNull(0) as? WindowInsets ?: return@after
                val classLoader = appClassLoader ?: return@after
                val view = instance as? android.view.View ?: return@after
                val launcher = HookUtils.getLauncherFromContext(view.context, classLoader) ?: return@after
                val searchModeActive = HookUtils.isSearchModeActive(launcher, classLoader)
                val imeVisible = insets.isVisible(WindowInsets.Type.ime())

                if (!searchModeActive) {
                    searchImeWasVisible = false
                    return@after
                }

                if (imeVisible) {
                    searchImeWasVisible = true
                    return@after
                }

                val justEnteredSearch = System.currentTimeMillis() - lastSwipeFocusAt < 700L
                if (searchImeWasVisible && !justEnteredSearch) {
                    searchImeWasVisible = false
                    if (HookUtils.resetSearchModeIfActive(launcher, classLoader)) {
                        Log.d(TAG, "[AutoFocus] Reset search mode after IME hidden")
                    }
                }
            }
        } ?: Log.d(TAG, "[AutoFocus] dispatchApplyWindowInsets not found")
    }

    private fun isAllAppsState(targetState: Any?, classLoader: ClassLoader?): Boolean {
        if (targetState == null || classLoader == null) return false
        val launcherStateClass = LAUNCHER_STATE_CLASS.toClassOrNull(classLoader) ?: return false
        val allAppsState = try {
            launcherStateClass.field { name = "ALL_APPS" }.get().any()
        } catch (_: Throwable) {
            null
        }
        return targetState == allAppsState
    }
}
