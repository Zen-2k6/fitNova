package uk.ncc.fitNova.ui

import android.app.Activity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import uk.ncc.fitNova.R

fun applyBlackSystemBars(activity: Activity) {
    activity.window.statusBarColor = ContextCompat.getColor(activity, R.color.black)
    activity.window.navigationBarColor = ContextCompat.getColor(activity, R.color.black)
    WindowCompat.getInsetsController(activity.window, activity.window.decorView)?.let { controller ->
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }
}
