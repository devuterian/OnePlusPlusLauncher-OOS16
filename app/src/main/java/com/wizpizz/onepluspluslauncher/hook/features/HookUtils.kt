package com.wizpizz.onepluspluslauncher.hook.features

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.toClass

import java.util.ArrayList

object HookUtils {
    
    // Constants
    const val TAG = "OPPLauncherHook"

    // Preference keys
    const val PREF_USE_FUZZY_SEARCH = "use_fuzzy_search"
    const val PREF_AUTO_FOCUS_SEARCH_SWIPE = "auto_focus_search_swipe"
    const val PREF_AUTO_FOCUS_SEARCH_REDIRECT = "auto_focus_search_redirect"
    const val PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT = "auto_focus_swipe_down_redirect"
    const val PREF_ENTER_KEY_LAUNCH = "enter_key_launch"
    const val PREF_GLOBAL_SEARCH_REDIRECT = "global_search_redirect"
    const val PREF_SWIPE_DOWN_SEARCH_REDIRECT = "swipe_down_search_redirect"
    const val PREF_LEFT_SWIPE_DISCOVER_REDIRECT = "left_swipe_discover_redirect"
    const val PREF_AUTO_FOCUS_LEFT_SWIPE_REDIRECT = "auto_focus_left_swipe_redirect"
    const val PREF_SEARCH_HISTORY_RECENCY = "search_history_recency"
    const val PREF_SEARCH_ALIASES = "search_aliases"
    
    // Class names
    const val LAUNCHER_CLASS = "com.android.launcher3.Launcher"
    const val LAUNCHER_STATE_CLASS = "com.android.launcher3.LauncherState"
    const val ANDROID_X_SEARCH_VIEW_CLASS = "androidx.appcompat.widget.SearchView"
    private const val ITEM_INFO_CLASS = "com.android.launcher3.model.data.ItemInfo"
    private const val VIEW_CLASS = "android.view.View"
    private const val INTENT_CLASS = "android.content.Intent"
    
    // Coordination flag to distinguish between swipe and redirect
    @Volatile
    private var isRedirectInProgress = false

    @Volatile
    var drawerOpenTime = 0L
    @Volatile
    var drawerCloseTime = 0L

    /**
     * Mark that a redirect is in progress (used by GlobalSearchRedirectHook)
     */
    fun setRedirectInProgress(inProgress: Boolean) {
        isRedirectInProgress = inProgress
    }
    
    /**
     * Check if a redirect is currently in progress (used by AutoFocusHook)
     */
    fun isRedirectInProgress(): Boolean = isRedirectInProgress
    
    /**
     * Extract Launcher instance from context traversal
     */
    fun getLauncherFromContext(context: Context, appClassLoader: ClassLoader): Any? {
        var currentContext: Context? = context
        val launcher3Class = LAUNCHER_CLASS.toClassOrNull(appClassLoader)
        
        while (currentContext != null) {
            if (currentContext is Activity && launcher3Class?.isInstance(currentContext) == true) {
                return currentContext
            }
            currentContext = if (currentContext is ContextWrapper) {
                currentContext.baseContext
            } else {
                null
            }
        }
        return null
    }
    
    /**
     * Focus search input in app drawer.
     * Uses multiple approaches and retries to handle third-party keyboards on OOS16.
     */
    fun focusSearchInput(
        launcherInstance: Any,
        appClassLoader: ClassLoader,
        smoothMode: Boolean = false
    ) {
        if (launcherInstance !is android.content.Context) return

        try {
            // Get AppsView
            var appsView = LAUNCHER_CLASS.toClass(appClassLoader)
                .field { name = "mAppsView" }
                .get(instance = launcherInstance)
                .any()

            if (appsView == null) {
                appsView = launcherInstance.current().method { name = "getAppsView" }.call()
            }

            if (appsView == null) {
                Log.e(TAG, "[AutoFocus] Failed to get AppsView")
                return
            }

            // Get SearchUiManager (launcher internals changed across versions)
            val searchUiManager = resolveSearchUiManager(appsView)

            if (searchUiManager == null) {
                Log.e(TAG, "[AutoFocus] Failed to get SearchUiManager")
                return
            }

            // ColorOS 17: focusing the EditText alone no longer switches the drawer into search
            // mode, and a click in the normal state can start global search instead. Enter the
            // drawer search mode directly first.
            if (!isSearchUiInEditState(searchUiManager)) {
                enterSearchMode(searchUiManager)
            }

            // Try multiple approaches to get and focus search input
            var searchInputFocused = false
            var editTextRef: android.widget.EditText? = null

            // Approach 1: Try getEditText method
            try {
                val editText = searchUiManager.current().method {
                    name = "getEditText"
                    superClass()
                }.call() as? android.widget.EditText

                if (editText != null) {
                    editText.isFocusable = true
                    editText.isFocusableInTouchMode = true
                    editText.showSoftInputOnFocus = true
                    editText.requestFocus()
                    editText.requestFocusFromTouch()
                    editText.performClick()
                    searchInputFocused = true
                    editTextRef = editText
                    Log.d(TAG, "[AutoFocus] Successfully focused search input via getEditText")
                }
            } catch (e: Throwable) {
                Log.d(TAG, "[AutoFocus] getEditText method not available")
            }

            // Approach 2: Search for EditText in searchUiManager view hierarchy
            if (!searchInputFocused && searchUiManager is android.view.ViewGroup) {
                val editText = findEditTextInViewGroup(searchUiManager)
                if (editText != null) {
                    editText.isFocusable = true
                    editText.isFocusableInTouchMode = true
                    editText.showSoftInputOnFocus = true
                    editText.requestFocus()
                    editText.requestFocusFromTouch()
                    editText.performClick()
                    searchInputFocused = true
                    editTextRef = editText
                    Log.d(TAG, "[AutoFocus] Successfully focused search input via view traversal")
                }
            }

            // Approach 3: Search for EditText in AppsView hierarchy (fallback)
            if (!searchInputFocused && appsView is android.view.ViewGroup) {
                val editText = findEditTextInViewGroup(appsView)
                if (editText != null) {
                    editText.isFocusable = true
                    editText.isFocusableInTouchMode = true
                    editText.showSoftInputOnFocus = true
                    editText.requestFocus()
                    editText.requestFocusFromTouch()
                    editText.performClick()
                    searchInputFocused = true
                    editTextRef = editText
                    Log.d(TAG, "[AutoFocus] Successfully focused search input via AppsView traversal")
                }
            }

            if (!searchInputFocused) {
                Log.w(TAG, "[AutoFocus] Could not focus search input - no suitable method found")
            }

            // Show keyboard (immediate)
            try {
                searchUiManager.current().method {
                    name = "showKeyboard"
                    superClass()
                }.call()
            } catch (_: Throwable) {}

            // If we have a real EditText, force IME on it (post to wait for layout)
            try {
                val et = editTextRef
                if (et != null) {
                    et.post {
                        try {
                            val imm = et.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                            imm?.restartInput(et)
                            imm?.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                            Log.d(TAG, "[AutoFocus] showSoftInput on EditText (post)")
                        } catch (_: Throwable) {}
                    }
                    et.postDelayed({
                        try {
                            val imm = et.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                            imm?.restartInput(et)
                            imm?.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                            Log.d(TAG, "[AutoFocus] showSoftInput on EditText (postDelayed=100ms)")
                        } catch (_: Throwable) {}
                    }, 100L)
                }
            } catch (_: Throwable) {}

            // Retry keyboard show after short delays.
            // Use SHOW_FORCED on later retries to override third-party IME suppression by launcher.
            try {
                if (searchUiManager is android.view.View) {
                    val view = searchUiManager
                    val retryMs = if (smoothMode) {
                        listOf(120L, 240L, 360L, 520L, 760L, 1000L)
                    } else {
                        listOf(250L, 400L, 600L, 800L, 1000L)
                    }
                    for (delay in retryMs) {
                        view.postDelayed({
                            try {
                                val editText = editTextRef ?: try {
                                    searchUiManager.current().method {
                                        name = "getEditText"
                                        superClass()
                                    }.call() as? android.widget.EditText
                                } catch (_: Throwable) { null }
                                    ?: if (searchUiManager is android.view.ViewGroup) {
                                        findEditTextInViewGroup(searchUiManager)
                                    } else null
                                    ?: if (appsView is android.view.ViewGroup) {
                                        findEditTextInViewGroup(appsView)
                                    } else null

                                editText?.isFocusable = true
                                editText?.isFocusableInTouchMode = true
                                editText?.showSoftInputOnFocus = true
                                editText?.requestFocus()
                                editText?.requestFocusFromTouch()
                                editText?.performClick()

                                try {
                                    searchUiManager.current().method {
                                        name = "showKeyboard"
                                        superClass()
                                    }.call()
                                } catch (_: Throwable) {}

                                val targetView = (editText ?: view) as android.view.View
                                val imm = targetView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                                if (editText != null) {
                                    imm?.restartInput(editText)
                                }
                                val showFlags = if (delay >= 600L) {
                                    android.view.inputmethod.InputMethodManager.SHOW_FORCED
                                } else {
                                    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
                                }
                                imm?.showSoftInput(targetView, showFlags)

                                Log.d(TAG, "[AutoFocus] Retried showKeyboard (delay=${delay}ms, flags=${showFlags})")
                            } catch (e: Throwable) {
                                Log.d(TAG, "[AutoFocus] Retry showKeyboard failed: ${e.message}")
                            }
                        }, delay)
                    }
                }
            } catch (_: Throwable) {}

        } catch (e: Throwable) {
            Log.e(TAG, "[AutoFocus] Error during focus logic: ${e.message}")
        }
    }

    /**
     * Gently focuses the drawer search input for swipe-up entry.
     *
     * OOS16 already enters drawer/search mode from OplusLauncherAllAppsContainerView.onScrollUpEnd().
     * For swipe-up, avoid performClick(), SHOW_FORCED, and long retry queues because those can race
     * with the launcher's own IME/back-key state machine.
     */
    fun focusSearchInputOnce(
        launcherInstance: Any,
        appClassLoader: ClassLoader
    ) {
        if (launcherInstance !is android.content.Context) return

        try {
            var appsView = LAUNCHER_CLASS.toClass(appClassLoader)
                .field { name = "mAppsView" }
                .get(instance = launcherInstance)
                .any()

            if (appsView == null) {
                appsView = launcherInstance.current().method { name = "getAppsView" }.call()
            }

            if (appsView == null) {
                Log.e(TAG, "[AutoFocus] Failed to get AppsView")
                return
            }

            val searchUiManager = resolveSearchUiManager(appsView)
            if (searchUiManager == null) {
                Log.e(TAG, "[AutoFocus] Failed to get SearchUiManager")
                return
            }

            val editText = resolveSearchEditText(searchUiManager, appsView)

            if (!isSearchUiInEditState(searchUiManager)) {
                if (enterSearchMode(searchUiManager)) {
                    Log.d(TAG, "[AutoFocus] Entered drawer search mode")
                    return
                }
            }

            if (editText != null && !editText.hasFocus()) {
                editText.isFocusable = true
                editText.isFocusableInTouchMode = true
                editText.showSoftInputOnFocus = true
                editText.requestFocus()
            }

            try {
                searchUiManager.current().method {
                    name = "showKeyboard"
                    superClass()
                }.call()
            } catch (_: Throwable) {}

            val targetView = (editText ?: searchUiManager as? android.view.View) ?: return
            targetView.postDelayed({
                try {
                    val imm = targetView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    if (editText != null && editText.hasWindowFocus()) {
                        imm?.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                    Log.d(TAG, "[AutoFocus] Gentle swipe keyboard request")
                } catch (e: Throwable) {
                    Log.d(TAG, "[AutoFocus] Gentle swipe keyboard request failed: ${e.message}")
                }
            }, 80L)
        } catch (e: Throwable) {
            Log.e(TAG, "[AutoFocus] Error during gentle focus logic: ${e.message}")
        }
    }

    fun isSearchModeActive(
        launcherInstance: Any,
        appClassLoader: ClassLoader
    ): Boolean {
        val searchUiManager = getSearchUiManager(launcherInstance, appClassLoader) ?: return false
        return isSearchUiInEditState(searchUiManager)
    }

    fun resetSearchModeIfActive(
        launcherInstance: Any,
        appClassLoader: ClassLoader
    ): Boolean {
        val searchUiManager = getSearchUiManager(launcherInstance, appClassLoader) ?: return false
        if (!isSearchUiInEditState(searchUiManager)) return false
        return try {
            searchUiManager.current().method {
                name = "resetSearch"
                superClass()
            }.call()
            true
        } catch (e: Throwable) {
            Log.d(TAG, "[AutoFocus] resetSearch failed: ${e.message}")
            false
        }
    }
    
    /**
     * Recursively search for EditText in view hierarchy
     */
    private fun findEditTextInViewGroup(viewGroup: android.view.ViewGroup): android.widget.EditText? {
        for (i in 0 until viewGroup.childCount) {
            when (val child = viewGroup.getChildAt(i)) {
                is android.widget.EditText -> return child
                is android.view.ViewGroup -> {
                    val found = findEditTextInViewGroup(child)
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun getSearchUiManager(launcherInstance: Any, appClassLoader: ClassLoader): Any? {
        return try {
            var appsView = LAUNCHER_CLASS.toClass(appClassLoader)
                .field { name = "mAppsView" }
                .get(instance = launcherInstance)
                .any()

            if (appsView == null) {
                appsView = launcherInstance.current().method { name = "getAppsView" }.call()
            }

            if (appsView == null) null else resolveSearchUiManager(appsView)
        } catch (e: Throwable) {
            Log.d(TAG, "[AutoFocus] getSearchUiManager failed: ${e.message}")
            null
        }
    }

    private fun resolveSearchEditText(searchUiManager: Any, appsView: Any): android.widget.EditText? {
        try {
            val editText = searchUiManager.current().method {
                name = "getEditText"
                superClass()
            }.call() as? android.widget.EditText
            if (editText != null) return editText
        } catch (_: Throwable) {}

        if (searchUiManager is android.view.ViewGroup) {
            val editText = findEditTextInViewGroup(searchUiManager)
            if (editText != null) return editText
        }

        return if (appsView is android.view.ViewGroup) {
            findEditTextInViewGroup(appsView)
        } else {
            null
        }
    }

    private fun isSearchUiInEditState(searchUiManager: Any): Boolean {
        val method = findMethodQuietly(searchUiManager.javaClass, listOf("isInEditState")) ?: return false
        return try {
            method.invoke(searchUiManager) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private fun enterSearchMode(searchUiManager: Any): Boolean {
        // onSearchBarClickInternal enters drawer search directly; onSearchBarClick may start
        // global search (QuickSearchBox / branch search) when the bar is in its normal state.
        return callFirstQuietly(searchUiManager, listOf("onSearchBarClickInternal", "onSearchBarClick"))
    }

    /**
     * Puts the drawer into its search page right after a redirect opened All Apps.
     *
     * OOS16 (onSearchResult(String, ArrayList)) showed the search page when fed a blank query.
     * ColorOS 17 (onSearchResult(ArrayList, String)) ignores results while the query is empty,
     * so enter search mode the way the search bar does instead.
     */
    fun triggerRedirectSearchPage(container: Any?, launcherInstance: Any?, appClassLoader: ClassLoader?): Boolean {
        if (container != null) {
            val legacy = findMethodQuietly(container.javaClass) {
                it.name == "onSearchResult" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == String::class.java &&
                    java.util.ArrayList::class.java.isAssignableFrom(it.parameterTypes[1])
            }
            if (legacy != null) {
                return try {
                    legacy.invoke(container, " ", java.util.ArrayList<Any>())
                    true
                } catch (e: Throwable) {
                    Log.d(TAG, "[Redirect] legacy onSearchResult failed: ${e.cause?.message ?: e.message}")
                    false
                }
            }
        }
        val searchUiManager = when {
            launcherInstance != null && appClassLoader != null -> getSearchUiManager(launcherInstance, appClassLoader)
            else -> null
        } ?: container ?: return false
        if (isSearchUiInEditState(searchUiManager)) return true
        return enterSearchMode(searchUiManager)
    }

    /**
     * Resolve launcher search manager across 16.4.15 and 16.6.5 variants.
     */
    private fun resolveSearchUiManager(appsView: Any): Any? {
        val methodCandidates = listOf(
            "getSearchUiManager",
            "getSearchManager",
            "getSearchContainer"
        )
        for (methodName in methodCandidates) {
            try {
                val result = appsView.current().method {
                    name = methodName
                    superClass()
                }.call()
                if (result != null) {
                    if (methodName != "getSearchUiManager") {
                        Log.d(TAG, "[AutoFocus] Using fallback manager method: $methodName")
                    }
                    return result
                }
            } catch (_: Throwable) {}
        }

        val fieldCandidates = listOf(
            "mSearchUiManager",
            "mSearchManager",
            "mSearchContainer"
        )
        for (fieldName in fieldCandidates) {
            try {
                val result = appsView.javaClass.field {
                    name = fieldName
                    superClass(true)
                }.get(appsView).any()
                if (result != null) {
                    Log.d(TAG, "[AutoFocus] Using fallback manager field: $fieldName")
                    return result
                }
            } catch (_: Throwable) {}
        }
        return null
    }
    
    /**
     * Launch first search result
     */
    fun launchFirstSearchResult(
        launcherInstance: Any,
        searchView: android.view.View,
        appClassLoader: ClassLoader
    ): Boolean {
        return try {
            val appsView = launcherInstance.current().method {
                name = "getAppsView"; superClass()
            }.call()

            // OOS16: Click the first visible app item in the search RecyclerView
            // This ensures we launch exactly what the user sees as the first result
            try {
                val searchRv = appsView?.current()?.method { name = "getActiveSearchRecyclerView"; superClass() }?.call()

                if (searchRv != null && searchRv is android.view.ViewGroup) {
                    val childCount = searchRv.childCount
                    Log.d(TAG, "[LaunchFirst] searchRv childCount=$childCount")

                    if (childCount > 0) {
                        // Find the first child that looks like an app icon (BubbleTextView or clickable view)
                        for (i in 0 until minOf(childCount, 15)) {
                            val child = searchRv.getChildAt(i)
                            if (child == null) continue

                            // If child is a clickable view with a tag (app item), click it
                            if (child.isClickable && child.tag != null) {
                                val tag = child.tag
                                val tagClass = tag.javaClass.name
                                Log.d(TAG, "[LaunchFirst] child[$i] tag class=$tagClass")

                                // Check if tag is an ItemInfo (app info)
                                val itemInfoClass = ITEM_INFO_CLASS.toClass(appClassLoader)
                                if (itemInfoClass.isInstance(tag)) {
                                    val intent = tag.current().method { name = "getIntent"; superClass() }.call() as? android.content.Intent
                                    if (intent != null) {
                                        Log.d(TAG, "[LaunchFirst] Launching via tag intent=$intent")
                                        launcherInstance.current().method {
                                            name = "startActivitySafely"
                                            param(VIEW_CLASS.toClass(appClassLoader),
                                                    INTENT_CLASS.toClass(appClassLoader),
                                                    ITEM_INFO_CLASS.toClass(appClassLoader))
                                            superClass()
                                        }.call(child, intent, tag)
                                        return true
                                    }
                                }
                            }

                            // If child is a ViewGroup (e.g. a row of icons), search inside it
                            if (child is android.view.ViewGroup) {
                                for (j in 0 until child.childCount) {
                                    val subChild = child.getChildAt(j)
                                    if (subChild != null && subChild.isClickable && subChild.tag != null) {
                                        val tag = subChild.tag
                                        val itemInfoClass = ITEM_INFO_CLASS.toClass(appClassLoader)
                                        if (itemInfoClass.isInstance(tag)) {
                                            val intent = tag.current().method { name = "getIntent"; superClass() }.call() as? android.content.Intent
                                            if (intent != null) {
                                                Log.d(TAG, "[LaunchFirst] Launching via subChild tag intent=$intent")
                                                launcherInstance.current().method {
                                                    name = "startActivitySafely"
                                                    param(VIEW_CLASS.toClass(appClassLoader),
                                                            INTENT_CLASS.toClass(appClassLoader),
                                                            ITEM_INFO_CLASS.toClass(appClassLoader))
                                                    superClass()
                                                }.call(subChild, intent, tag)
                                                return true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "[LaunchFirst] searchRv approach failed: ${e.message}")
            }

            // Fallback: Try main AlphabeticalAppsList (older launcher versions)
            val appsList = appsView?.current()?.method {
                name = "getAlphabeticalAppsList"; superClass()
            }?.call() ?: appsView?.current()?.method {
                name = "getAppsList"; superClass()
            }?.call()

            if (appsList != null) {
                var resultItems: List<*>? = null
                try {
                    resultItems = appsList.current().method { name = "getSearchResults"; superClass() }.call() as? List<*>
                } catch (_: Throwable) {}

                if (resultItems != null && resultItems.isNotEmpty()) {
                    val firstAdapterItem = resultItems[0]
                    if (firstAdapterItem != null) {
                        val itemInfoField = firstAdapterItem.javaClass.field {
                            name = "itemInfo"; superClass(true)
                        }
                        val itemInfoObject = itemInfoField.get(firstAdapterItem).any()
                        val itemInfoClass = ITEM_INFO_CLASS.toClass(appClassLoader)
                        val itemInfo = if (itemInfoClass.isInstance(itemInfoObject)) itemInfoObject else null

                        if (itemInfo != null) {
                            val foundIntent = itemInfo.current().method {
                                name = "getIntent"; superClass()
                            }.call() as? android.content.Intent

                            if (foundIntent != null) {
                                launcherInstance.current().method {
                                    name = "startActivitySafely"
                                    param(VIEW_CLASS.toClass(appClassLoader),
                                            INTENT_CLASS.toClass(appClassLoader),
                                            ITEM_INFO_CLASS.toClass(appClassLoader))
                                    superClass()
                                }.call(searchView, foundIntent, itemInfo)
                                return true
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "[LaunchFirst] No launchable item found")
            false
        } catch (e: Throwable) {
            Log.e(TAG, "Error launching first search result: ${e.message}", e)
            false
        }
    }
}

// Extension for safe class resolution
fun String.toClassOrNull(classLoader: ClassLoader?): Class<*>? {
    if (classLoader == null) return null
    return try {
        Class.forName(this, false, classLoader)
    } catch (e: Throwable) {
        null
    }
}

/**
 * Finds a declared method on [start] or its superclasses without logging a failure.
 * Launcher builds rename, reorder and obfuscate members, so callers match by shape
 * instead of letting the hook framework report NoSuchMethodError for every miss.
 */
fun findMethodQuietly(start: Class<*>?, predicate: (java.lang.reflect.Method) -> Boolean): java.lang.reflect.Method? {
    var current = start
    while (current != null && current != Any::class.java) {
        val found = try {
            current.declaredMethods.firstOrNull(predicate)
        } catch (_: Throwable) {
            null
        }
        if (found != null) return found.apply { isAccessible = true }
        current = current.superclass
    }
    return null
}

fun findMethodQuietly(start: Class<*>?, names: List<String>, vararg params: Class<*>): java.lang.reflect.Method? {
    for (name in names) {
        findMethodQuietly(start) { it.name == name && it.parameterTypes.contentEquals(params) }?.let { return it }
    }
    return null
}

/** Calls the first existing no-throw candidate method; returns true when one was invoked. */
fun callFirstQuietly(target: Any, names: List<String>, vararg args: Any?, params: Array<Class<*>> = emptyArray()): Boolean {
    val method = findMethodQuietly(target.javaClass, names, *params) ?: return false
    return try {
        method.invoke(target, *args)
        true
    } catch (e: Throwable) {
        android.util.Log.d(HookUtils.TAG, "${method.name} failed: ${e.cause?.message ?: e.message}")
        false
    }
}

/** Finds the first field on [start] or its superclasses whose type is [type] or a subtype. */
fun findFieldByType(start: Class<*>?, type: Class<*>): java.lang.reflect.Field? {
    var current = start
    while (current != null && current != Any::class.java) {
        current.declaredFields.firstOrNull { type.isAssignableFrom(it.type) }?.let {
            return it.apply { isAccessible = true }
        }
        current = current.superclass
    }
    return null
}
