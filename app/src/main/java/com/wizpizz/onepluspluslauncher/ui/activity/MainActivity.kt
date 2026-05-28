package com.wizpizz.onepluspluslauncher.ui.activity


import androidx.core.view.isVisible
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge
import com.wizpizz.onepluspluslauncher.BuildConfig
import com.wizpizz.onepluspluslauncher.R
import com.wizpizz.onepluspluslauncher.databinding.ActivityMainBinding
import com.wizpizz.onepluspluslauncher.ui.activity.base.BaseActivity
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SEARCH_SWIPE
import com.wizpizz.onepluspluslauncher.utils.LocaleUtils
import android.widget.ArrayAdapter
import android.widget.AdapterView
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_ENTER_KEY_LAUNCH
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_GLOBAL_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_SWIPE_DOWN_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_LEFT_SWIPE_DISCOVER_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_LEFT_SWIPE_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_USE_FUZZY_SEARCH
import android.content.Intent
import android.util.Log
import android.widget.Toast

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private companion object {
        private const val TAG = "OPPLauncherUI"
        private const val LAUNCHER_PACKAGE = "com.android.launcher"
    }

    private val prefs: YukiHookPrefsBridge by lazy { prefs() }

    override fun onCreate() {
        refreshModuleStatus()
        binding.mainTextVersion.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)
        binding.mainTextVersion2.text = getString(R.string.supported_launcher_version, BuildConfig.SUPPORTED_LAUNCHER_VERSION)
        
        // Setup language selector
        setupLanguageSpinner()

        // GitHub link
        binding.githubLink.setOnClickListener {
            val url = "https://github.com/zhangbaoshengrio/OnePlusPlusLauncher-OOS16"
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }

        // Setup feature toggles - all enabled by default
        setupFeatureToggle(binding.autoFocusSearchSwipeSwitch, PREF_AUTO_FOCUS_SEARCH_SWIPE)
        setupFeatureToggle(binding.autoFocusSearchRedirectSwitch, PREF_AUTO_FOCUS_SEARCH_REDIRECT)
        setupFeatureToggle(binding.autoFocusSwipeDownRedirectSwitch, PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT)
        setupFeatureToggle(binding.enterKeyLaunchSwitch, PREF_ENTER_KEY_LAUNCH)
        setupFeatureToggle(binding.globalSearchRedirectSwitch, PREF_GLOBAL_SEARCH_REDIRECT)
        setupFeatureToggle(binding.swipeDownSearchRedirectSwitch, PREF_SWIPE_DOWN_SEARCH_REDIRECT)
        setupFeatureToggle(binding.leftSwipeDiscoverRedirectSwitch, PREF_LEFT_SWIPE_DISCOVER_REDIRECT)
        setupFeatureToggle(binding.autoFocusLeftSwipeRedirectSwitch, PREF_AUTO_FOCUS_LEFT_SWIPE_REDIRECT)
        setupFeatureToggle(binding.fuzzySearchSwitchNew, PREF_USE_FUZZY_SEARCH)
        setupRestartLauncherButton()
    }

    /**
     * Setup a feature toggle switch
     * @param switch The MaterialSwitch to setup
     * @param prefKey The preference key to store the value
     * Note: All features default to enabled (true)
     */
    private fun setupFeatureToggle(
        switch: com.wizpizz.onepluspluslauncher.ui.view.MaterialSwitch,
        prefKey: String
    ) {
        switch.isChecked = prefs.getBoolean(prefKey, true)
        switch.setOnCheckedChangeListener { button, isChecked ->
            if (button.isPressed) {
                prefs.native().edit { putBoolean(prefKey, isChecked) }
            }
        }
    }

    private fun setupLanguageSpinner() {
        val entries = resources.getStringArray(R.array.language_entries)
        val values = resources.getStringArray(R.array.language_values)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entries)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.languageSpinner.adapter = adapter

        val uiPrefs = getSharedPreferences(LocaleUtils.PREFS_NAME, MODE_PRIVATE)
        val saved = uiPrefs.getString(LocaleUtils.PREF_UI_LANGUAGE, "") ?: ""
        val initialIndex = values.indexOf(saved).takeIf { it >= 0 } ?: 0
        binding.languageSpinner.setSelection(initialIndex, false)

        binding.languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val newLang = values.getOrNull(position) ?: return
                val current = uiPrefs.getString(LocaleUtils.PREF_UI_LANGUAGE, "") ?: ""
                if (newLang != current) {
                    uiPrefs.edit().putString(LocaleUtils.PREF_UI_LANGUAGE, newLang).apply()
                    recreate()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRestartLauncherButton() {
        binding.restartLauncherButton.setOnClickListener {
            val success = restartLauncherProcess()
            Toast.makeText(
                this,
                getString(if (success) R.string.restart_launcher_success else R.string.restart_launcher_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
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


    /**
     * Refresh module status
     *
     * 刷新模块状态
     */
    private fun refreshModuleStatus() {
        binding.mainLinStatus.setBackgroundResource(
            when {
                YukiHookAPI.Status.isXposedModuleActive -> R.drawable.bg_green_round
                else -> R.drawable.bg_dark_round
            }
        )
        binding.mainImgStatus.setImageResource(
            when {
                YukiHookAPI.Status.isXposedModuleActive -> R.mipmap.ic_success
                else -> R.mipmap.ic_warn
            }
        )
        binding.mainTextStatus.text = getString(
            when {
                YukiHookAPI.Status.isXposedModuleActive -> R.string.module_is_activated
                else -> R.string.module_not_activated
            }
        )
        // Removed mainTextApiWay references as the view no longer exists in the layout
    }
}