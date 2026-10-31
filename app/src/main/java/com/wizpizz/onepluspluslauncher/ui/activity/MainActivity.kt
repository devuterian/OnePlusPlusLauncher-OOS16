package com.wizpizz.onepluspluslauncher.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge
import com.wizpizz.onepluspluslauncher.BuildConfig
import com.wizpizz.onepluspluslauncher.R
import com.wizpizz.onepluspluslauncher.databinding.ActivityMainBinding
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_LEFT_SWIPE_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SEARCH_SWIPE
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_ENTER_KEY_LAUNCH
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_GLOBAL_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_LEFT_SWIPE_DISCOVER_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_SEARCH_ALIASES
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_SWIPE_DOWN_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_USE_FUZZY_SEARCH
import com.wizpizz.onepluspluslauncher.utils.LocaleUtils
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private companion object {
        private const val TAG = "OPPLauncherUI"
        private const val LAUNCHER_PACKAGE = "com.android.launcher"
        private const val ACTION_RESTART_LAUNCHER =
            "com.wizpizz.onepluspluslauncher.action.RESTART_LAUNCHER"
        private const val SU_TIMEOUT_SECONDS = 20L  // Magisk prompt auto-answers after 10s
        private val restartInFlight = AtomicBoolean(false)
        private const val GITHUB_URL = "https://github.com/devuterian/OnePlusPlusLauncher-OOS16"
    }

    private val prefs: YukiHookPrefsBridge by lazy { prefs() }
    private lateinit var binding: ActivityMainBinding

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(LocaleUtils.PREFS_NAME, MODE_PRIVATE)
        super.attachBaseContext(LocaleUtils.wrapContext(newBase, prefs.getString(LocaleUtils.PREF_UI_LANGUAGE, "")))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupSystemBars()
        setupToolbar()
        refreshModuleStatus()
        binding.mainTextVersion.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)
        binding.mainTextVersion2.text =
            getString(R.string.supported_launcher_version, BuildConfig.SUPPORTED_LAUNCHER_VERSION)

        setupLanguageDropdown()
        setupFeatureToggle(binding.autoFocusSearchSwipeSwitch, PREF_AUTO_FOCUS_SEARCH_SWIPE)
        setupFeatureToggle(binding.autoFocusSearchRedirectSwitch, PREF_AUTO_FOCUS_SEARCH_REDIRECT)
        setupFeatureToggle(binding.autoFocusSwipeDownRedirectSwitch, PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT)
        setupFeatureToggle(binding.enterKeyLaunchSwitch, PREF_ENTER_KEY_LAUNCH)
        setupFeatureToggle(binding.globalSearchRedirectSwitch, PREF_GLOBAL_SEARCH_REDIRECT)
        setupFeatureToggle(binding.swipeDownSearchRedirectSwitch, PREF_SWIPE_DOWN_SEARCH_REDIRECT)
        setupFeatureToggle(binding.leftSwipeDiscoverRedirectSwitch, PREF_LEFT_SWIPE_DISCOVER_REDIRECT)
        setupFeatureToggle(binding.autoFocusLeftSwipeRedirectSwitch, PREF_AUTO_FOCUS_LEFT_SWIPE_REDIRECT)
        setupFeatureToggle(binding.fuzzySearchSwitchNew, PREF_USE_FUZZY_SEARCH)
        setupSearchAliases()
        setupRestartLauncherButton()
        handleShortcutIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun setupSystemBars() {
        val isLightTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = isLightTheme
            isAppearanceLightNavigationBars = isLightTheme
        }
        val surface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            android.graphics.Color.TRANSPARENT
        )
        window.statusBarColor = surface
        window.navigationBarColor = surface
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = surface
        }
    }

    private fun setupToolbar() {
        binding.topToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.github_link -> {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(GITHUB_URL)))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupFeatureToggle(switch: MaterialSwitch, prefKey: String, defaultValue: Boolean = true) {
        switch.isChecked = prefs.getBoolean(prefKey, defaultValue)
        switch.setOnCheckedChangeListener { button, isChecked ->
            if (button.isPressed) {
                prefs.native().edit { putBoolean(prefKey, isChecked) }
            }
        }
    }

    override fun onPause() {
        saveSearchAliases()
        super.onPause()
    }

    private fun setupSearchAliases() {
        binding.searchAliasesInput.setText(prefs.getString(PREF_SEARCH_ALIASES, ""))
        binding.searchAliasesInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSearchAliases()
        }
    }

    private fun saveSearchAliases() {
        val text = binding.searchAliasesInput.text?.toString()?.trim().orEmpty()
        if (text != prefs.getString(PREF_SEARCH_ALIASES, "")) {
            prefs.native().edit { putString(PREF_SEARCH_ALIASES, text) }
        }
    }

    private fun setupLanguageDropdown() {
        val entries = resources.getStringArray(R.array.language_entries)
        val values = resources.getStringArray(R.array.language_values)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, entries)
        binding.languageDropdown.setAdapter(adapter)

        val uiPrefs = getSharedPreferences(LocaleUtils.PREFS_NAME, MODE_PRIVATE)
        val saved = uiPrefs.getString(LocaleUtils.PREF_UI_LANGUAGE, "") ?: ""
        val initialIndex = values.indexOf(saved).takeIf { it >= 0 } ?: 0
        binding.languageDropdown.setText(entries[initialIndex], false)

        binding.languageDropdown.setOnItemClickListener { _, _, position, _ ->
            val newLang = values.getOrNull(position) ?: return@setOnItemClickListener
            val current = uiPrefs.getString(LocaleUtils.PREF_UI_LANGUAGE, "") ?: ""
            if (newLang != current) {
                uiPrefs.edit().putString(LocaleUtils.PREF_UI_LANGUAGE, newLang).apply()
                recreate()
            }
        }
    }

    private fun setupRestartLauncherButton() {
        binding.restartLauncherButton.setOnClickListener {
            restartLauncherWithToast()
        }
    }

    private fun handleShortcutIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action == ACTION_RESTART_LAUNCHER) {
            binding.root.post { restartLauncherWithToast() }
        }
    }

    private fun restartLauncherWithToast() {
        // su can block on a root prompt; keep it off the UI thread so the app never freezes.
        // The shortcut and a recreated activity bypass the button, so guard across instances too.
        if (!restartInFlight.compareAndSet(false, true)) return
        binding.restartLauncherButton.isEnabled = false
        Thread({
            val success = try {
                restartLauncherProcess()
            } finally {
                restartInFlight.set(false)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                binding.restartLauncherButton.isEnabled = true
                Toast.makeText(
                    this,
                    getString(if (success) R.string.restart_launcher_success else R.string.restart_launcher_failed),
                    if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                ).show()
            }
        }, "SearchUp-restart").start()
    }

    private fun restartLauncherProcess(): Boolean {
        val forceStopOk = runSuCommand("am force-stop $LAUNCHER_PACKAGE")
        val homeOk = launchHomeLauncher()
        Log.d(TAG, "restartLauncherProcess forceStopOk=$forceStopOk homeOk=$homeOk")
        return forceStopOk && homeOk
    }

    private fun runSuCommand(command: String): Boolean {
        val attempts = listOf(
            arrayOf("su", "-c", command),
            arrayOf("su", "0", "sh", "-c", command),
            arrayOf("/system/bin/su", "-c", command),
            arrayOf("/system/xbin/su", "-c", command)
        )

        for (attempt in attempts) {
            var process: Process? = null
            try {
                process = Runtime.getRuntime().exec(attempt)
                if (!process.waitFor(SU_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    // su exists but isn't answering; another su path won't help and the command may still run later.
                    Log.d(TAG, "runSuCommand timed out via ${attempt.joinToString(" ")}")
                    return false
                }
                val exitCode = process.exitValue()
                if (exitCode == 0) {
                    Log.d(TAG, "runSuCommand success via ${attempt.joinToString(" ")}")
                    return true
                }
                Log.d(TAG, "runSuCommand exit=$exitCode via ${attempt.joinToString(" ")}")
            } catch (e: Throwable) {
                Log.d(TAG, "runSuCommand failed via ${attempt.joinToString(" ")}: ${e.message}")
            } finally {
                process?.destroy()
            }
        }
        return false
    }

    private fun launchHomeLauncher(): Boolean {
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "launchHomeLauncher failed: ${e.message}")
            false
        }
    }

    private fun refreshModuleStatus() {
        val active = YukiHookAPI.Status.isXposedModuleActive
        binding.statusCard.setCardBackgroundColor(
            ContextCompat.getColor(
                this,
                if (active) R.color.m3_status_active else R.color.m3_status_inactive
            )
        )
        binding.mainImgStatus.setImageResource(if (active) R.mipmap.ic_success else R.mipmap.ic_warn)
        binding.mainTextStatus.setText(
            if (active) R.string.module_is_activated else R.string.module_not_activated
        )
    }
}
