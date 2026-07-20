package com.codex.edgeshelf.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.codex.edgeshelf.data.ShelfStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = ShelfStore(context).settings.first()
                if (settings.autoStart && settings.enabled && Settings.canDrawOverlays(context)) {
                    EdgeShelfService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
