package dev.antinode.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the engine after a reboot or an app update, like Easy Effects starting with the session. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching { EngineController.start(context) }
        }
    }
}
