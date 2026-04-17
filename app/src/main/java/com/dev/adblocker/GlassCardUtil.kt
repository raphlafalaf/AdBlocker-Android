package com.dev.adblocker

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * Applies a frosted-glass blur to the given view's background on SDK 31+.
 * On older devices it's a no-op — the glass card still looks fine due to
 * its translucent fill and hairline stroke.
 */
object GlassCardUtil {
    fun applyBlur(view: View, radius: Float = 30f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR)
            )
        }
    }

    fun clearBlur(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null)
        }
    }
}
