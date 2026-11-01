package dev.antinode.engine

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.antinode.core.data.EqRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: turns all effects on or off. */
class PowerTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        s.launch { render(EqRepository.get(this@PowerTileService).isPowerOn()) }
    }

    override fun onStopListening() {
        scope?.cancel(); scope = null
    }

    override fun onClick() {
        val repo = EqRepository.get(this)
        scope?.launch {
            val on = !repo.isPowerOn()
            repo.setPower(on)
            if (on) runCatching { EngineController.start(this@PowerTileService) }
            render(on)
        }
    }

    private fun render(on: Boolean) {
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = if (on) "On" else "Off"
            updateTile()
        }
    }
}
