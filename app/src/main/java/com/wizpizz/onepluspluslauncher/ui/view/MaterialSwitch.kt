@file:Suppress("SameParameterValue")

package com.wizpizz.onepluspluslauncher.ui.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.color.MaterialColors
import com.wizpizz.onepluspluslauncher.utils.factory.dp
import top.defaults.drawabletoolbox.DrawableBuilder

class MaterialSwitch(context: Context, attrs: AttributeSet?) : SwitchCompat(context, attrs) {

    private fun trackColors(selected: Int, pressed: Int, normal: Int): ColorStateList {
        val colors = intArrayOf(selected, pressed, normal)
        val states = arrayOfNulls<IntArray>(3)
        states[0] = intArrayOf(android.R.attr.state_checked)
        states[1] = intArrayOf(android.R.attr.state_pressed)
        states[2] = intArrayOf()
        return ColorStateList(states, colors)
    }

    init {
        val primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
        val surfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant)
        val onPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary)

        trackDrawable = DrawableBuilder()
            .rectangle()
            .rounded()
            .solidColor(surfaceVariant)
            .height(20.dp(context))
            .cornerRadius(15.dp(context))
            .build()
        thumbDrawable = DrawableBuilder()
            .rectangle()
            .rounded()
            .solidColor(Color.WHITE)
            .size(20.dp(context), 20.dp(context))
            .cornerRadius(20.dp(context))
            .strokeWidth(8.dp(context))
            .strokeColor(Color.TRANSPARENT)
            .build()
        trackTintList = trackColors(primary, primary, surfaceVariant)
        thumbTintList = ColorStateList.valueOf(onPrimary)
    }
}
