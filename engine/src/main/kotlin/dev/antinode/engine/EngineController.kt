package dev.antinode.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Entry points the app uses to run and configure the engine. */
object EngineController {
    private const val PREFS = "engine"
    private const val KEY_MODE = "mode"

    fun start(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, EngineService::class.java))
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, EngineService::class.java))
    }

    fun mode(context: Context): EngineMode =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)
            ?.let { runCatching { EngineMode.valueOf(it) }.getOrNull() } ?: EngineMode.AUTO

    fun setMode(context: Context, mode: EngineMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.name).apply()
        ContextCompat.startForegroundService(
            context, Intent(context, EngineService::class.java).putExtra(EngineService.EXTRA_MODE, mode.name),
        )
    }
}
