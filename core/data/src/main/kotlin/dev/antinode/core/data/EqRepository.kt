package dev.antinode.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dev.antinode.core.formats.NativeFormat
import dev.antinode.core.model.Art
import dev.antinode.core.model.Chain
import dev.antinode.core.model.OutputDevice
import dev.antinode.core.model.OutputKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

class EqRepository private constructor(
    private val dao: EqDao,
    private val settings: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** Everything the UI and the engine need. Null until the first profile exists. */
    val state: StateFlow<EqState?> = combine(dao.profiles(), dao.setups(), dao.presets(), settings.data) { p, s, pr, prefs ->
        val activeId = prefs[ACTIVE_PROFILE]
        val active = p.firstOrNull { it.id == activeId } ?: p.firstOrNull() ?: return@combine null
        val setups = s.filter { it.profileId == active.id }.map { it.toModel() }
        EqState(
            profiles = p.map { Profile(it.id, it.name) },
            activeProfile = Profile(active.id, active.name),
            setups = setups,
            presets = pr.filter { it.profileId == active.id }.map { it.toModel() },
            selectedSetup = setups.firstOrNull { it.id == active.selectedSetupId } ?: setups.maxByOrNull { it.lastUsedAt },
            power = prefs[POWER] ?: true,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun ensureSeeded() = mutex.withLock {
        if (dao.profilesNow().isEmpty()) {
            val id = dao.insert(ProfileEntity(name = "Me"))
            seedProfile(id)
            settings.edit { it[ACTIVE_PROFILE] = id }
        }
    }

    // ---------- profiles ----------

    suspend fun addProfile(name: String): Long = mutex.withLock {
        val order = dao.profilesNow().size
        val id = dao.insert(ProfileEntity(name = name.trim().ifEmpty { "Profile ${order + 1}" }, sortOrder = order))
        seedProfile(id)
        settings.edit { it[ACTIVE_PROFILE] = id }
        id
    }

    suspend fun renameProfile(id: Long, name: String) = mutex.withLock {
        dao.profile(id)?.let { dao.update(it.copy(name = name.trim().ifEmpty { it.name })) }
    }

    /** Deletes a profile with its setups and presets. The last profile can't be deleted. */
    suspend fun deleteProfile(id: Long): Boolean = mutex.withLock {
        val all = dao.profilesNow()
        if (all.size <= 1) return@withLock false
        dao.deleteSetupsOf(id); dao.deletePresetsOf(id); dao.deleteProfile(id)
        settings.edit { prefs -> if (prefs[ACTIVE_PROFILE] == id) prefs[ACTIVE_PROFILE] = all.first { it.id != id }.id }
        true
    }

    suspend fun cycleProfile(delta: Int) {
        val st = state.value ?: return
        val i = st.profiles.indexOfFirst { it.id == st.activeProfile.id }
        val next = st.profiles[Math.floorMod(i + delta, st.profiles.size)]
        settings.edit { it[ACTIVE_PROFILE] = next.id }
    }

    suspend fun setPower(on: Boolean) { settings.edit { it[POWER] = on } }

    suspend fun isPowerOn(): Boolean = settings.data.first()[POWER] ?: true

    // ---------- setups ----------

    suspend fun selectSetup(id: Long) = mutex.withLock {
        val s = dao.setup(id) ?: return@withLock
        dao.update(s.copy(lastUsedAt = System.currentTimeMillis()))
        dao.profile(s.profileId)?.let { dao.update(it.copy(selectedSetupId = id)) }
    }

    suspend fun cycleSetup(delta: Int) {
        val st = state.value ?: return
        if (st.setups.isEmpty()) return
        val i = st.setups.indexOfFirst { it.id == st.selectedSetup?.id }.coerceAtLeast(0)
        selectSetup(st.setups[Math.floorMod(i + delta, st.setups.size)].id)
    }

    suspend fun updateChain(setupId: Long, transform: (Chain) -> Chain) = mutex.withLock {
        val s = dao.setup(setupId) ?: return@withLock
        val next = transform(NativeFormat.decodeChain(s.chainJson))
        dao.update(s.copy(chainJson = NativeFormat.encodeChain(next), modified = true))
    }

    suspend fun updateSetup(id: Long, name: String, art: List<Art>, bypass: Boolean) = mutex.withLock {
        val s = dao.setup(id) ?: return@withLock
        dao.update(s.copy(name = name.trim().ifEmpty { s.name }, art = art.joinToString(",") { it.name }, bypass = bypass))
    }

    suspend fun setBypass(id: Long, bypass: Boolean) = mutex.withLock {
        dao.setup(id)?.let { dao.update(it.copy(bypass = bypass)) }
    }

    /** Adds another headphone for the same output, e.g. a second IEM on the same DAC, and selects it. */
    suspend fun addSetupLike(id: Long, name: String, art: List<Art>): Long {
        val id2 = mutex.withLock {
            val s = dao.setup(id) ?: return@withLock -1L
            dao.insert(
                s.copy(
                    id = 0, name = name.trim().ifEmpty { "New headphones" }, art = art.joinToString(",") { it.name },
                    bypass = false, chainJson = NativeFormat.encodeChain(Chain()), presetId = null, modified = false,
                    sortOrder = dao.setupsNow(s.profileId).size,
                ),
            )
        }
        if (id2 > 0) selectSetup(id2)
        return id2
    }

    suspend fun deleteSetup(id: Long): Boolean = mutex.withLock {
        val s = dao.setup(id) ?: return@withLock false
        if (dao.setupsNow(s.profileId).size <= 1) return@withLock false
        dao.deleteSetup(id)
        true
    }

    /**
     * Called by the engine when media output changes. Creates a setup the first time a device is seen and
     * makes the device's most recently used setup the selected one.
     */
    suspend fun onOutputConnected(device: OutputDevice): Long? = mutex.withLock {
        val profileId = settings.data.first()[ACTIVE_PROFILE] ?: dao.profilesNow().firstOrNull()?.id ?: return@withLock null
        val existing = dao.setupsNow(profileId).filter { it.deviceKey == device.key }
        val target = existing.maxByOrNull { it.lastUsedAt } ?: run {
            val flat = dao.presetsNow(profileId).firstOrNull()
            val entity = SetupEntity(
                profileId = profileId,
                name = device.name,
                deviceKey = device.key,
                deviceKind = device.kind.name,
                deviceName = device.name,
                art = defaultArt(device.kind).joinToString(",") { it.name },
                bypass = device.kind == OutputKind.SPEAKER,
                presetId = flat?.id,
                chainJson = NativeFormat.encodeChain(if (device.kind == OutputKind.SPEAKER) Chain.FLAT else Chain()),
                sortOrder = dao.setupsNow(profileId).size,
                lastUsedAt = System.currentTimeMillis(),
            )
            entity.copy(id = dao.insert(entity))
        }
        dao.profile(profileId)?.let { dao.update(it.copy(selectedSetupId = target.id)) }
        target.id
    }

    // ---------- presets ----------

    suspend fun applyPreset(setupId: Long, presetId: Long) = mutex.withLock {
        val s = dao.setup(setupId) ?: return@withLock
        val p = dao.preset(presetId) ?: return@withLock
        dao.update(s.copy(chainJson = p.chainJson, presetId = p.id, modified = false))
    }

    suspend fun savePreset(setupId: Long, name: String, description: String, chain: Chain): Long = mutex.withLock {
        val s = dao.setup(setupId) ?: return@withLock -1L
        val id = dao.insert(
            PresetEntity(
                profileId = s.profileId, name = name.trim().ifEmpty { "Preset" }, description = description,
                chainJson = NativeFormat.encodeChain(chain), sortOrder = dao.presetsNow(s.profileId).size,
            ),
        )
        dao.update(s.copy(chainJson = NativeFormat.encodeChain(chain), presetId = id, modified = false))
        id
    }

    /** Overwrites the setup's preset with the setup's current chain. */
    suspend fun overwritePreset(setupId: Long) = mutex.withLock {
        val s = dao.setup(setupId) ?: return@withLock
        val p = s.presetId?.let { dao.preset(it) } ?: return@withLock
        dao.update(p.copy(chainJson = s.chainJson))
        dao.update(s.copy(modified = false))
    }

    suspend fun renamePreset(id: Long, name: String) = mutex.withLock {
        dao.preset(id)?.let { dao.update(it.copy(name = name.trim().ifEmpty { it.name })) }
    }

    suspend fun deletePreset(id: Long) = mutex.withLock { dao.deletePreset(id) }

    // ---------- internals ----------

    private suspend fun seedProfile(profileId: Long) {
        var flatId: Long? = null
        DefaultPresets.all.forEachIndexed { i, seed ->
            val id = dao.insert(
                PresetEntity(
                    profileId = profileId, name = seed.name, description = seed.description,
                    chainJson = NativeFormat.encodeChain(seed.chain), sortOrder = i,
                ),
            )
            if (i == 0) flatId = id
        }
        val speaker = dao.insert(
            SetupEntity(
                profileId = profileId, name = "Phone speaker", deviceKey = SPEAKER_KEY,
                deviceKind = OutputKind.SPEAKER.name, deviceName = "Built-in speaker",
                art = Art.SPEAKER.name, bypass = true, presetId = flatId,
                chainJson = NativeFormat.encodeChain(Chain.FLAT), lastUsedAt = System.currentTimeMillis(),
            ),
        )
        dao.profile(profileId)?.let { dao.update(it.copy(selectedSetupId = speaker)) }
    }

    private fun SetupEntity.toModel() = Setup(
        id = id, profileId = profileId, name = name, deviceKey = deviceKey,
        deviceKind = runCatching { OutputKind.valueOf(deviceKind) }.getOrDefault(OutputKind.OTHER),
        deviceName = deviceName,
        art = art.split(',').mapNotNull { a -> runCatching { Art.valueOf(a.trim()) }.getOrNull() },
        bypass = bypass, presetId = presetId, chain = NativeFormat.decodeChain(chainJson),
        modified = modified, lastUsedAt = lastUsedAt,
    )

    private fun PresetEntity.toModel() =
        Preset(id = id, profileId = profileId, name = name, description = description, chain = NativeFormat.decodeChain(chainJson))

    companion object {
        const val SPEAKER_KEY = "SPEAKER"
        private val ACTIVE_PROFILE = longPreferencesKey("active_profile")
        private val POWER = booleanPreferencesKey("power")

        fun defaultArt(kind: OutputKind): List<Art> = when (kind) {
            OutputKind.USB -> listOf(Art.DAC, Art.IEM)
            OutputKind.WIRED -> listOf(Art.WIRED)
            OutputKind.BLUETOOTH -> listOf(Art.BUDS)
            OutputKind.SPEAKER -> listOf(Art.SPEAKER)
            OutputKind.HDMI -> listOf(Art.MONITORS)
            OutputKind.OTHER -> listOf(Art.IEM)
        }

        @Volatile private var instance: EqRepository? = null

        fun get(context: Context): EqRepository = instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                val db = Room.databaseBuilder(app, EqDatabase::class.java, "antinode.db").build()
                EqRepository(db.dao(), app.settingsStore, CoroutineScope(SupervisorJob() + Dispatchers.IO))
                    .also { instance = it }
            }
        }
    }
}
