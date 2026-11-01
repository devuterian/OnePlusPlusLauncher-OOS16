package com.wizpizz.onepluspluslauncher.hook.features

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.DecelerateInterpolator
import com.highcapable.yukihookapi.hook.factory.current

import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.param.PackageParam

import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.LAUNCHER_CLASS
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_SEARCH_HISTORY_RECENCY
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_SWIPE_DOWN_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG

/**
 * Hook to redirect swipe down global search to app drawer
 */
object SwipeDownSearchRedirectHook {

    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            findMethodQuietly("com.android.launcher.touch.WorkspacePullDownDetectController".toClassOrNull(appClassLoader)) {
                it.name == "showSearchBar" && it.parameterTypes.size == 5 &&
                    it.returnType == Boolean::class.javaPrimitiveType
            }?.hook {
                before {
                    if (prefs.getBoolean(PREF_SWIPE_DOWN_SEARCH_REDIRECT, true)) {
                        Log.d(TAG, "[SwipeDownSearch] Intercepting showSearchBar")

                        HookUtils.setRedirectInProgress(true)

                        val launcher = args[0]

                        // Activate system blur scrim as full-screen blur cover.
                        // OOS16 exposes blurBackground(int)/detach(); ColorOS 17 obfuscates the
                        // blur scrim controller (d6.f) and needs an explicit attach first, so the
                        // cover is skipped there instead of guessing obfuscated names.
                        val blurController: Any? = launcher?.let { l ->
                            findMethodQuietly(l.javaClass, listOf("getBlurScrimWindowController"))
                                ?.let { runCatching { it.invoke(l) }.getOrNull() }
                        }?.takeIf { findMethodQuietly(it.javaClass, listOf("blurBackground"), Int::class.javaPrimitiveType!!) != null }

                        if (blurController != null) {
                            callFirstQuietly(blurController, listOf("blurBackground"), 100, params = arrayOf(Int::class.javaPrimitiveType!!)) // max blur radius
                            Log.d(TAG, "[SwipeDownSearch] Activated blur scrim (radius=100)")
                        }

                        val success = launcher?.let { openAppDrawer(it) }

                        if (success == true) {
                            FuzzySearchHook.lastRedirectTime = System.currentTimeMillis()
                            HookUtils.drawerOpenTime = System.currentTimeMillis()

                            cleanupPullDownAnimation(instance, launcher)

                            val autoFocusSwipeDownEnabled = prefs.getBoolean(PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT, true)
                            if (autoFocusSwipeDownEnabled) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    try {
                                        HookUtils.setRedirectInProgress(true)
                                        appClassLoader?.let {
                                            HookUtils.focusSearchInput(launcher, it, smoothMode = true)
                                        }
                                        Log.d(TAG, "[SwipeDownSearch] Applied auto focus for swipe-down redirect")
                                    } catch (e: Throwable) {
                                        Log.e(TAG, "[SwipeDownSearch] Auto focus failed: ${e.message}")
                                    } finally {
                                        HookUtils.setRedirectInProgress(false)
                                    }
                                }, 0L)
                            } else {
                                HookUtils.setRedirectInProgress(false)
                            }

                            // T=300ms: animate blur radius 100 → 0 to reveal search UI.
                            if (blurController != null) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    val fadeOut = ValueAnimator.ofFloat(1f, 0f)
                                    fadeOut.duration = 250L
                                    fadeOut.interpolator = DecelerateInterpolator()
                                    fadeOut.addUpdateListener { anim ->
                                        val fraction = anim.animatedValue as Float
                                        try {
                                            val radius = (100 * fraction).toInt()
                                            callFirstQuietly(blurController, listOf("blurBackground"), radius, params = arrayOf(Int::class.javaPrimitiveType!!))
                                        } catch (_: Throwable) {}
                                    }
                                    fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: android.animation.Animator) {
                                            try {
                                                callFirstQuietly(blurController, listOf("blurBackground"), 0, params = arrayOf(Int::class.javaPrimitiveType!!))
                                            } catch (_: Throwable) {}
                                            try {
                                                callFirstQuietly(blurController, listOf("detach"))
                                            } catch (_: Throwable) {}
                                            Log.d(TAG, "[SwipeDownSearch] Blur scrim cleared")
                                        }
                                    })
                                    fadeOut.start()
                                }, 300L)

                                // Safety fallback.
                                Handler(Looper.getMainLooper()).postDelayed({
                                    try {
                                        callFirstQuietly(blurController, listOf("blurBackground"), 0, params = arrayOf(Int::class.javaPrimitiveType!!))
                                    } catch (_: Throwable) {}
                                    try {
                                        callFirstQuietly(blurController, listOf("detach"))
                                    } catch (_: Throwable) {}
                                }, 1500L)
                            }

                            // Trigger history display.
                            if (prefs.getBoolean(PREF_SEARCH_HISTORY_RECENCY, true)) {
                                val triggerHistory: () -> Unit = {
                                    if (HookUtils.triggerRedirectSearchPage(FuzzySearchHook.searchContainerInstance, launcher, appClassLoader)) {
                                        Log.d(TAG, "[SwipeDownSearch] Triggered history display")
                                    } else {
                                        Log.d(TAG, "[SwipeDownSearch] Could not trigger history display")
                                    }
                                }
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (FuzzySearchHook.searchContainerInstance != null) {
                                        triggerHistory()
                                    } else {
                                        Handler(Looper.getMainLooper()).postDelayed(triggerHistory, 450L)
                                    }
                                }, 50L)
                            }

                            result = false
                        } else {
                            Log.d(TAG, "[SwipeDownSearch] Failed to open drawer")
                            // Clear blur on failure.
                            if (blurController != null) {
                                try {
                                    callFirstQuietly(blurController, listOf("blurBackground"), 0, params = arrayOf(Int::class.javaPrimitiveType!!))
                                } catch (_: Throwable) {}
                                try {
                                    callFirstQuietly(blurController, listOf("detach"))
                                } catch (_: Throwable) {}
                            }
                            HookUtils.setRedirectInProgress(false)
                        }
                    }
                }
            } ?: Log.e(TAG, "[SwipeDownSearch] Failed to find showSearchBar method")
        }
    }

    private fun cleanupPullDownAnimation(controllerInstance: Any, launcherInstance: Any) {
        // ColorOS 17 obfuscates the pull-down animator; returning false from showSearchBar
        // already makes the controller cancel the pull (w(false) + pullCancel()), so the named
        // OOS16 cleanup is best effort only.
        try {
            val pullDownAnimator = findMethodQuietly(controllerInstance.javaClass, listOf("getPullDownAnimator"))
                ?.let { runCatching { it.invoke(controllerInstance) }.getOrNull() } ?: return
            if (!callFirstQuietly(pullDownAnimator, listOf("destroyAnimAsHomeGesture", "destroyAnimatorIfNeeded"))) {
                Log.d(TAG, "[SwipeDownSearch] Named pull-down cleanup unavailable; launcher cancels the pull itself")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "[SwipeDownSearch] Cleanup error: ${e.message}")
        }
    }

    private fun PackageParam.openAppDrawer(launcherInstance: Any): Boolean {
        return try {
            val success = try {
                launcherInstance.current().method {
                    name = "showAllAppsFromIntent"
                    param(BooleanType)
                }.call(true)
                true
            } catch (e: Throwable) {
                try {
                    val ctx = launcherInstance as? android.content.Context ?: return false
                    "com.android.launcher3.taskbar.TaskbarUtils".toClass(appClassLoader).method {
                        name = "showAllApps"
                        param(ctx.javaClass)
                        modifiers { isStatic }
                    }.get().call(ctx)
                    true
                } catch (_: Throwable) { false }
            }
            return success
        } catch (_: Throwable) { false }
    }
}
