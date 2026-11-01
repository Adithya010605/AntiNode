package dev.antinode.engine

import dev.antinode.core.model.OutputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class EngineMode { AUTO, GLOBAL, PER_APP }

data class SessionInfo(val id: Int, val packageName: String, val attached: Boolean, val hasControl: Boolean)

data class EngineStatus(
    val running: Boolean = false,
    val output: OutputDevice? = null,
    /** True when effects are attached to the global output mix (session 0). */
    val globalActive: Boolean = false,
    val sessions: List<SessionInfo> = emptyList(),
    /** EQ bands Android accepted for the tone stage. */
    val bandCount: Int = 0,
    val processing: Boolean = false,
    val mode: EngineMode = EngineMode.AUTO,
    val problem: String? = null,
)

/** Process-wide engine status so the UI can show what the service is doing. */
object EngineState {
    internal val mutable = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = mutable
}
