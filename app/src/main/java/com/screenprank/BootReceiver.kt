package com.screenprank

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("screen_prank", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("boot_start", false)) return
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return
        val service = Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, service)
        else context.startService(service)
    }
}
