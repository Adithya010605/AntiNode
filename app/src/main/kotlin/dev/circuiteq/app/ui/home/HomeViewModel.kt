package dev.circuiteq.app.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.circuiteq.core.data.EqRepository
import dev.circuiteq.core.data.EqState
import dev.circuiteq.core.data.Setup
import dev.circuiteq.core.formats.ApoFormat
import dev.circuiteq.core.formats.ImportException
import dev.circuiteq.core.formats.Importer
import dev.circuiteq.core.formats.NativeFormat
import dev.circuiteq.core.model.Art
import dev.circuiteq.core.model.Chain
import dev.circuiteq.core.model.EffectId
import dev.circuiteq.engine.EngineController
import dev.circuiteq.engine.EngineMode
import dev.circuiteq.engine.EngineState
import dev.circuiteq.engine.EngineStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUi(val data: EqState?, val engine: EngineStatus) {
    val setup: Setup? get() = data?.selectedSetup

    /** The chain is live when power is on and the selected setup isn't bypassed. */
    val live: Boolean get() = data?.power == true && setup?.bypass == false

    /** The selected setup is what the engine is applying right now. */
    val isConnected: Boolean get() = setup != null && engine.output?.key == setup?.deviceKey
}

/** Result of an import, shown so the user can name and save it. */
data class ImportOutcome(val name: String, val format: String, val warnings: List<String>)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = EqRepository.get(app)

    val ui: StateFlow<HomeUi> = combine(repo.state, EngineState.status) { d, e -> HomeUi(d, e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUi(null, EngineStatus()))

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _imports = Channel<ImportOutcome>(Channel.BUFFERED)
    val imports = _imports.receiveAsFlow()

    init {
        viewModelScope.launch { repo.ensureSeeded() }
    }

    private val setupId get() = ui.value.setup?.id

    private fun say(text: String) { _messages.trySend(text) }

    // ---------- chain ----------

    fun toggle(id: EffectId) = edit { it.toggle(id) }

    fun edit(transform: (Chain) -> Chain) {
        val id = setupId ?: return
        viewModelScope.launch { repo.updateChain(id, transform) }
    }

    // ---------- navigation-ish ----------

    fun cycleSetup(delta: Int) = viewModelScope.launch { repo.cycleSetup(delta) }
    fun cycleProfile(delta: Int) = viewModelScope.launch { repo.cycleProfile(delta) }
    fun selectSetup(id: Long) = viewModelScope.launch { repo.selectSetup(id) }

    fun setPower(on: Boolean) = viewModelScope.launch {
        repo.setPower(on)
        if (on) EngineController.start(getApplication())
        say(if (on) "Effects on" else "All effects off — bypass")
    }

    // ---------- presets ----------

    fun applyPreset(presetId: Long) {
        val id = setupId ?: return
        viewModelScope.launch { repo.applyPreset(id, presetId) }
    }

    fun savePreset(name: String) {
        val s = ui.value.setup ?: return
        viewModelScope.launch {
            repo.savePreset(s.id, name, describe(s.chain), s.chain)
            say("Saved preset “$name”")
        }
    }

    fun overwritePreset() {
        val id = setupId ?: return
        viewModelScope.launch { repo.overwritePreset(id); say("Preset updated") }
    }

    fun renamePreset(id: Long, name: String) = viewModelScope.launch { repo.renamePreset(id, name) }
    fun deletePreset(id: Long) = viewModelScope.launch { repo.deletePreset(id); say("Preset deleted") }

    // ---------- profiles ----------

    fun addProfile(name: String) = viewModelScope.launch { repo.addProfile(name); say("Profile added") }
    fun renameProfile(id: Long, name: String) = viewModelScope.launch { repo.renameProfile(id, name) }
    fun deleteProfile(id: Long) = viewModelScope.launch {
        if (!repo.deleteProfile(id)) say("Keep at least one profile")
    }

    // ---------- setups ----------

    fun updateSetup(id: Long, name: String, art: List<Art>, bypass: Boolean) =
        viewModelScope.launch { repo.updateSetup(id, name, art, bypass) }

    fun setBypass(bypass: Boolean) {
        val id = setupId ?: return
        viewModelScope.launch { repo.setBypass(id, bypass) }
    }

    fun addHeadphones(name: String, art: List<Art>) {
        val id = setupId ?: return
        viewModelScope.launch { repo.addSetupLike(id, name, art); say("Added $name") }
    }

    fun deleteSetup(id: Long) = viewModelScope.launch {
        if (!repo.deleteSetup(id)) say("Keep at least one output")
    }

    // ---------- engine ----------

    fun setEngineMode(mode: EngineMode) = EngineController.setMode(getApplication(), mode)
    fun engineMode() = EngineController.mode(getApplication())

    // ---------- AutoEQ ----------

    fun applyAutoEq(a: dev.circuiteq.app.ui.autoeq.AutoEqApply) {
        val s = ui.value.setup ?: return
        viewModelScope.launch {
            repo.updateChain(s.id) { c ->
                c.copy(
                    enabled = c.enabled + EffectId.PEQ + EffectId.PREAMP + EffectId.LIMITER,
                    preamp = c.preamp.copy(gainDb = a.preampDb),
                    peq = dev.circuiteq.core.model.ParametricEq(a.filters, source = "AutoEQ · ${a.headphone} · ${a.label}"),
                )
            }
            if (a.renameOutput) repo.updateSetup(s.id, a.headphone, s.art, s.bypass)
            _imports.trySend(ImportOutcome("${a.headphone} · ${a.label}", "AutoEQ", emptyList()))
        }
    }

    // ---------- import / export ----------

    fun import(uri: Uri) {
        viewModelScope.launch {
            val id = setupId ?: repo.state.filterNotNull().first().selectedSetup?.id ?: return@launch
            val app = getApplication<Application>()
            val name = displayName(uri)
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: throw ImportException("Couldn't open the file")
                Importer.read(text, name)
            }
            result.onSuccess { imported ->
                repo.updateChain(id) { current -> merge(current, imported.chain) }
                _imports.trySend(ImportOutcome(imported.name ?: name ?: "Imported", imported.format, imported.warnings))
            }.onFailure { say(it.message ?: "Import failed") }
        }
    }

    fun export(uri: Uri, asApo: Boolean) {
        val s = ui.value.setup ?: return
        viewModelScope.launch {
            val text = if (asApo) ApoFormat.write(s.chain) else NativeFormat.write(s.name, s.chain)
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                }
            }.isSuccess
            say(if (ok) "Exported" else "Export failed")
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        /** Imported EQ and dynamics replace the current ones; effects the file doesn't mention are kept. */
        fun merge(current: Chain, imported: Chain): Chain {
            val touched = imported.enabled
            return current.copy(
                enabled = current.enabled + touched,
                preamp = if (EffectId.PREAMP in touched) imported.preamp else current.preamp,
                peq = if (EffectId.PEQ in touched) imported.peq else current.peq,
                compressor = if (EffectId.COMPRESSOR in touched) imported.compressor else current.compressor,
                limiter = if (EffectId.LIMITER in touched) imported.limiter else current.limiter,
            )
        }

        fun describe(chain: Chain): String {
            val names = EffectId.entries.filter { chain.isOn(it) }.map { it.title }
            return if (names.isEmpty()) "Everything disconnected" else names.joinToString(" · ")
        }
    }
}
