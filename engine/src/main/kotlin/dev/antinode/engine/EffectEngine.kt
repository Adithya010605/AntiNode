package dev.antinode.engine

import dev.antinode.core.model.Chain

/**
 * Owns every attached session. In global mode effects sit on the output mix (session 0) and per-app sessions
 * are only tracked, so audio is never processed twice. If the phone refuses session 0, per-app sessions
 * announced by players are used instead.
 */
internal class EffectEngine(private var mode: EngineMode) {
    private val sessions = LinkedHashMap<Int, EffectSession>()
    private val known = LinkedHashMap<Int, String>()
    private var chain: Chain? = null
    private var globalOk = false

    fun start() {
        if (mode != EngineMode.PER_APP) {
            val global = EffectSession(GLOBAL, "Global output")
            globalOk = global.create()
            if (globalOk) sessions[GLOBAL] = global
        }
        sync()
    }

    fun setMode(next: EngineMode) {
        if (next == mode) return
        releaseAll()
        mode = next
        start()
    }

    fun open(id: Int, pkg: String) {
        if (id == GLOBAL) return
        known[id] = pkg
        sync()
    }

    fun close(id: Int) {
        known.remove(id)
        sessions.remove(id)?.release()
        publish()
    }

    fun apply(next: Chain?) {
        chain = next
        sessions.values.forEach { it.apply(next) }
        publish()
    }

    fun releaseAll() {
        sessions.values.forEach { it.release() }
        sessions.clear()
        globalOk = false
    }

    private fun usePerApp() = mode == EngineMode.PER_APP || !globalOk

    private fun sync() {
        if (usePerApp()) {
            for ((id, pkg) in known) {
                if (id in sessions) continue
                val s = EffectSession(id, pkg)
                if (s.create()) { sessions[id] = s; s.apply(chain) }
            }
        }
        sessions[GLOBAL]?.apply(chain)
        publish()
    }

    private fun publish() {
        val global = sessions[GLOBAL]
        EngineState.mutable.value = EngineState.mutable.value.copy(
            globalActive = global != null,
            sessions = known.map { (id, pkg) ->
                val s = sessions[id]
                SessionInfo(id, pkg, attached = s != null, hasControl = s?.hasControl == true)
            },
            bandCount = (global ?: sessions.values.firstOrNull())?.bands ?: 0,
            processing = chain != null && sessions.isNotEmpty(),
            mode = mode,
            problem = when {
                sessions.isEmpty() && mode != EngineMode.PER_APP ->
                    "This phone blocks global effects. Play music to attach to the player's session."
                global != null && !global.hasControl -> "Another audio app has taken control of the effects."
                else -> null
            },
        )
    }

    companion object {
        const val GLOBAL = 0
    }
}
