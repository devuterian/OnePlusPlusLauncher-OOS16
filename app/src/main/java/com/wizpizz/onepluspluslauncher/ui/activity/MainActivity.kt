package com.wizpizz.onepluspluslauncher.ui.activity

import android.content.Intent
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
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
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_INCLUDE_APP_SHORTCUTS_SEARCH
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_LEFT_SWIPE_DISCOVER_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_SWIPE_DOWN_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_USE_FUZZY_SEARCH
import com.wizpizz.onepluspluslauncher.ui.activity.base.BaseActivity
import com.wizpizz.onepluspluslauncher.ui.view.MaterialSwitch
import com.wizpizz.onepluspluslauncher.utils.LocaleUtils

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private companion object {
        private const val TAG = "OPPLauncherUI"
        private const val LAUNCHER_PACKAGE = "com.android.launcher"
        private const val ACTION_RESTART_LAUNCHER =
            "com.wizpizz.onepluspluslauncher.action.RESTART_LAUNCHER"
        private const val GITHUB_URL = "https://github.com/devuterian/OnePlusPlusLauncher-OOS16"
    }

    private val prefs: YukiHookPrefsBridge by lazy { prefs() }

    override fun onCreate() {
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
        setupFeatureToggle(binding.includeAppShortcutsSearchSwitch, PREF_INCLUDE_APP_SHORTCUTS_SEARCH, false)
        setupRestartLauncherButton()
        handleShortcutIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
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
        val success = restartLauncherProcess()
        Toast.makeText(
            this,
            getString(if (success) R.string.restart_launcher_success else R.string.restart_launcher_failed),
            Toast.LENGTH_SHORT
        ).show()
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
            try {
                val process = Runtime.getRuntime().exec(attempt)
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    Log.d(TAG, "runSuCommand success via ${attempt.joinToString(" ")}")
                    return true
                }
                Log.d(TAG, "runSuCommand exit=$exitCode via ${attempt.joinToString(" ")}")
            } catch (e: Throwable) {
                Log.d(TAG, "runSuCommand failed via ${attempt.joinToString(" ")}: ${e.message}")
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
