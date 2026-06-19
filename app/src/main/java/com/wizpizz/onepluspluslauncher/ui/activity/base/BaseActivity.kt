@file:Suppress("MemberVisibilityCanBePrivate", "UNCHECKED_CAST")

package com.wizpizz.onepluspluslauncher.ui.activity.base

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.viewbinding.ViewBinding
import com.google.android.material.color.MaterialColors
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.LayoutInflaterClass
import com.wizpizz.onepluspluslauncher.utils.factory.isNotSystemInDarkMode

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        val prefs = newBase.getSharedPreferences(
            com.wizpizz.onepluspluslauncher.utils.LocaleUtils.PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
        val lang = prefs.getString(com.wizpizz.onepluspluslauncher.utils.LocaleUtils.PREF_UI_LANGUAGE, "")
        val wrapped = com.wizpizz.onepluspluslauncher.utils.LocaleUtils.wrapContext(newBase, lang)
        super.attachBaseContext(wrapped)
    }

    lateinit var binding: VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = current().generic()?.argument()?.method {
            name = "inflate"
            param(LayoutInflaterClass)
        }?.get()?.invoke<VB>(layoutInflater) ?: error("binding failed")
        setContentView(binding.root)
        supportActionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = isNotSystemInDarkMode
            isAppearanceLightNavigationBars = isNotSystemInDarkMode
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
        onCreate()
    }

    abstract fun onCreate()
}
