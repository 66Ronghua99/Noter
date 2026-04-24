package com.cory.noter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cory.noter.NoterApplication

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val pendingResult = goAsync()
        val application = context.applicationContext as? NoterApplication
        if (application == null) {
            pendingResult.finish()
            return
        }

        application.reconcileStartupState {
            pendingResult.finish()
        }
    }
}
