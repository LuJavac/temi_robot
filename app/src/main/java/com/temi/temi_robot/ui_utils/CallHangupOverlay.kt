package com.temi.temi_robot.ui_utils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button

// Bouton "raccrocher" flottant affiché par-dessus l'interface d'appel native
// du Temi (qui recouvre notre app pendant un appel). Nécessite la permission
// SYSTEM_ALERT_WINDOW ("afficher par-dessus les autres applis").
object CallHangupOverlay {

    private var overlayButton: Button? = null

    fun canDraw(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    // Ouvre l'écran de paramètres Android où l'utilisateur accorde la permission
    fun requestPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun show(context: Context, onHangUp: () -> Unit) {
        if (overlayButton != null) return
        if (!canDraw(context)) return

        val appContext = context.applicationContext
        val windowManager =
            appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val button = Button(appContext)
        button.text = "Hang up"
        button.textSize = 24f
        button.setTextColor(Color.WHITE)
        button.setBackgroundColor(Color.parseColor("#CCFF0000"))
        button.setPadding(40, 20, 40, 20)
        button.setOnClickListener { onHangUp() }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 30
        params.y = 120

        windowManager.addView(button, params)
        overlayButton = button
    }

    fun hide(context: Context) {
        val button = overlayButton ?: return
        overlayButton = null
        val windowManager =
            context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            windowManager.removeView(button)
        } catch (_: Exception) {
            // Vue déjà retirée par le système
        }
    }
}
