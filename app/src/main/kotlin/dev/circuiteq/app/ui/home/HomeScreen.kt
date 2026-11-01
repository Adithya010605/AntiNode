package dev.circuiteq.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.circuiteq.app.ui.common.Eyebrow
import dev.circuiteq.app.ui.common.TextPrompt
import dev.circuiteq.app.ui.common.pillOutline
import dev.circuiteq.app.ui.common.rememberSwipeState
import dev.circuiteq.app.ui.common.swipeToCycle
import dev.circuiteq.app.ui.editor.EffectSheet
import dev.circuiteq.app.ui.sheets.PresetSheet
import dev.circuiteq.app.ui.sheets.ProfilesSheet
import dev.circuiteq.app.ui.sheets.SettingsSheet
import dev.circuiteq.app.ui.sheets.SetupSheet
import dev.circuiteq.app.ui.theme.Palette
import dev.circuiteq.app.ui.theme.Type
import dev.circuiteq.core.data.EqState
import dev.circuiteq.core.data.Preset
import dev.circuiteq.core.data.Setup
import dev.circuiteq.core.model.EffectId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed interface Sheet {
    data class Effect(val id: EffectId) : Sheet
    data object Profiles : Sheet
    data object Output : Sheet
    data object Settings : Sheet
    data class PresetActions(val preset: Preset) : Sheet
}

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf<ImportOutcome?>(null) }
    var savingPreset by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { toast = it } }
    LaunchedEffect(Unit) { vm.imports.collect { imported = it } }
    LaunchedEffect(toast) { if (toast != null) { delay(1800); toast = null } }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { vm.export(it, asApo = true) }
    }

    Box(Modifier.fillMaxSize().background(Palette.Ground)) {
        val data = ui.data
        val setup = ui.setup
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 18.dp, bottom = 22.dp),
        ) {
            if (data == null || setup == null) {
                Text("Warming up the tubes…", style = Type.Body, modifier = Modifier.padding(top = 40.dp))
                return@Column
            }
            TopBar(
                data = data,
                onProfileStep = { vm.cycleProfile(it) },
                onProfiles = { sheet = Sheet.Profiles },
                onSettings = { sheet = Sheet.Settings },
                onPower = { vm.setPower(!data.power) },
            )
            Spacer(Modifier.height(22.dp))

            val live = ui.live
            ChainBoard(
                chain = setup.chain,
                live = live,
                flowing = live && ui.isConnected && ui.engine.processing,
                outLabel = when {
                    !data.power -> "Out → ${setup.deviceName} · off"
                    setup.bypass -> "Out → ${setup.deviceName} · bypass"
                    else -> "Out → ${setup.deviceName}"
                },
                onToggle = { id ->
                    if (!live) toast = if (!data.power) "Power is off" else "${setup.name} is bypassed — turn effects on below"
                    else vm.toggle(id)
                },
                onEdit = { id ->
                    if (!live) toast = if (!data.power) "Power is off" else "${setup.name} is bypassed — turn effects on below"
                    else sheet = Sheet.Effect(id)
                },
            ) {
                DevicePill(
                    setup = setup,
                    count = data.setups.size,
                    index = data.setups.indexOfFirst { it.id == setup.id },
                    connected = ui.isConnected,
                    format = ui.engine.output?.takeIf { ui.isConnected }?.format,
                    live = live,
                    onStep = { vm.cycleSetup(it) },
                    onOpen = { sheet = Sheet.Output },
                )
            }

            PresetDock(
                data = data,
                setup = setup,
                enabled = live,
                onApply = vm::applyPreset,
                onSave = { savingPreset = "" },
                onActions = { sheet = Sheet.PresetActions(it) },
            )

            Hint(ui, setup, onEnable = { vm.setBypass(false) })
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
        ) {
            Text(
                toast.orEmpty(),
                style = Type.PillName.copy(color = Palette.Ground),
                modifier = Modifier.background(Palette.Beige, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    // ---------- sheets & dialogs ----------
    val data = ui.data
    val setup = ui.setup
    when (val s = sheet) {
        is Sheet.Effect -> if (setup != null) EffectSheet(
            id = s.id,
            chain = setup.chain,
            sourceName = setup.chain.peq.source,
            onCommit = { next -> vm.edit { next } },
            onImport = { importLauncher.launch(arrayOf("text/plain", "application/json", "application/octet-stream")) },
            onExport = { exportLauncher.launch("${setup.name} ParametricEQ.txt") },
            onDismiss = { sheet = null },
        )
        Sheet.Profiles -> if (data != null) ProfilesSheet(
            profiles = data.profiles,
            activeId = data.activeProfile.id,
            onSelect = { id -> vm.cycleProfile(data.profiles.indexOfFirst { it.id == id } - data.profiles.indexOfFirst { it.id == data.activeProfile.id }) },
            onAdd = vm::addProfile,
            onRename = { id, n -> vm.renameProfile(id, n) },
            onDelete = vm::deleteProfile,
            onDismiss = { sheet = null },
        )
        Sheet.Output -> if (setup != null && data != null) SetupSheet(
            setup = setup,
            connected = ui.isConnected,
            format = ui.engine.output?.takeIf { ui.isConnected }?.format,
            canDelete = data.setups.size > 1,
            onSave = { n, art, bypass -> vm.updateSetup(setup.id, n, art, bypass) },
            onAddHeadphones = { n, art -> vm.addHeadphones(n, art) },
            onDelete = { vm.deleteSetup(setup.id) },
            onDismiss = { sheet = null },
        )
        Sheet.Settings -> SettingsSheet(ui.engine, vm.engineMode(), vm::setEngineMode) { sheet = null }
        is Sheet.PresetActions -> if (setup != null) PresetSheet(
            preset = s.preset,
            isCurrent = setup.presetId == s.preset.id,
            modified = setup.modified,
            onRename = { vm.renamePreset(s.preset.id, it) },
            onOverwrite = vm::overwritePreset,
            onDelete = { vm.deletePreset(s.preset.id) },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    imported?.let { result ->
        AlertDialog(
            onDismissRequest = { imported = null },
            containerColor = Palette.Panel,
            title = { Text("Imported", style = Type.SheetTitle) },
            text = {
                Column {
                    Text(result.name, style = Type.DeviceName)
                    Text(result.format, style = Type.Mono, modifier = Modifier.padding(top = 4.dp))
                    result.warnings.forEach { Text("• $it", style = Type.Body, modifier = Modifier.padding(top = 6.dp)) }
                    Text("Applied to ${setup?.name}. Save it as a preset to reuse it.", style = Type.Body, modifier = Modifier.padding(top = 10.dp))
                }
            },
            confirmButton = { TextButton({ savingPreset = result.name; imported = null }) { Text("Save as preset", color = Palette.Beige) } },
            dismissButton = { TextButton({ imported = null }) { Text("Done", color = Palette.Beige2) } },
        )
    }
    savingPreset?.let { initial ->
        TextPrompt("Save preset", initial, "Save", { savingPreset = null }) { vm.savePreset(it); savingPreset = null }
    }
}

@Composable
private fun TopBar(
    data: EqState,
    onProfileStep: (Int) -> Unit,
    onProfiles: () -> Unit,
    onSettings: () -> Unit,
    onPower: () -> Unit,
) {
    val swipe = rememberSwipeState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .height(38.dp)
                .border(1.dp, Palette.Beige4, RoundedCornerShape(50))
                .swipeToCycle(swipe, onStep = onProfileStep)
                .clickable(onClick = onProfiles)
                .semantics { contentDescription = "Profile ${data.activeProfile.name}. Swipe to switch, tap to manage." }
                .padding(start = 4.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp).background(Palette.Beige, CircleShape), contentAlignment = Alignment.Center) {
                Text(data.activeProfile.initial, style = Type.SheetTitle.copy(color = Palette.Ground, fontSize = Type.DeviceName.fontSize))
            }
            Spacer(Modifier.width(9.dp))
            Text(
                data.activeProfile.name, style = Type.PillName.copy(color = Palette.Beige),
                modifier = Modifier.graphicsLayer { translationX = swipe.offset.value * 0.35f; alpha = 1f - minOf(abs(swipe.offset.value) / 260f, 0.5f) },
            )
            Spacer(Modifier.width(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                data.profiles.forEach { p ->
                    Box(Modifier.size(4.dp).background(if (p.id == data.activeProfile.id) Palette.Beige else Palette.Beige4, CircleShape))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        RoundButton(Glyph.KNOB, on = true, label = "Engine settings", onClick = onSettings)
        Spacer(Modifier.width(8.dp))
        RoundButton(Glyph.POWER, on = data.power, label = if (data.power) "Turn all effects off" else "Turn effects on", onClick = onPower)
    }
}

@Composable
private fun RoundButton(glyph: Glyph, on: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).border(1.dp, if (on) Palette.Beige3 else Palette.Beige4, CircleShape).clip(CircleShape).clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, if (on) Palette.Beige else Palette.Beige3)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DevicePill(
    setup: Setup,
    count: Int,
    index: Int,
    connected: Boolean,
    format: String?,
    live: Boolean,
    onStep: (Int) -> Unit,
    onOpen: () -> Unit,
) {
    val swipe = rememberSwipeState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Panel, RoundedCornerShape(50))
            .border(1.5.dp, if (live) Palette.Beige else Palette.Beige3, RoundedCornerShape(50))
            .swipeToCycle(swipe, onStep = onStep)
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "Output ${setup.name}. Swipe to switch, tap to edit." }
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.graphicsLayer {
                translationX = swipe.offset.value * 0.35f
                alpha = 1f - minOf(abs(swipe.offset.value) / 260f, 0.5f)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.height(54.dp).background(Palette.Panel2, RoundedCornerShape(50)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                setup.art.forEachIndexed { i, art ->
                    if (i > 0) Box(Modifier.width(14.dp).height(1.2.dp).background(Palette.Beige3))
                    ArtIcon(art)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(setup.name, style = Type.DeviceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildString {
                    append(setup.deviceName)
                    if (format != null) append(" · $format")
                    if (!connected) append(" · not connected")
                }
                Text(sub, style = Type.Mono.copy(fontSize = Type.Value.fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(count) { i ->
                        Box(Modifier.size(12.dp, 2.dp).background(if (i == index) Palette.Beige else Palette.Beige4, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetDock(
    data: EqState,
    setup: Setup,
    enabled: Boolean,
    onApply: (Long) -> Unit,
    onSave: () -> Unit,
    onActions: (Preset) -> Unit,
) {
    val presets = data.presets
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val current = presets.indexOfFirst { it.id == setup.presetId }
    val latestPresets by rememberUpdatedState(presets)
    val latestSetup by rememberUpdatedState(setup)
    val apply by rememberUpdatedState(onApply)

    Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow("Presets · ${data.activeProfile.name}", Modifier.weight(1f))
        if (setup.modified) Text("● modified", style = Type.Value.copy(color = Palette.Amber))
    }
    Spacer(Modifier.height(10.dp))

    LaunchedEffect(setup.id, current) { if (current >= 0) state.scrollToItem(current) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val centred = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: return@collect
            val p = latestPresets.getOrNull(centred) ?: return@collect
            if (p.id != latestSetup.presetId) apply(p.id)
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().graphicsLayer { alpha = if (enabled) 1f else 0.35f }) {
        val card = 184.dp
        val side = ((maxWidth - card) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = enabled,
        ) {
            itemsIndexed(presets, key = { _, p -> p.id }) { i, p ->
                PresetCard(
                    title = p.name, sub = p.description, current = p.id == setup.presetId,
                    onClick = { if (enabled) scope.launch { state.animateScrollToItem(i) } },
                    onLongClick = { onActions(p) },
                )
            }
            item(key = "save") {
                PresetCard("+ Save current", "Keep this chain as a preset", current = false, onClick = { if (enabled) onSave() }, onLongClick = {})
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(title: String, sub: String, current: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val color = if (current) Palette.Beige else Palette.Beige4
    Column(
        Modifier
            .width(184.dp)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    color, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(w, pathEffect = if (current) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.5.dp.toPx()))),
                )
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(title, style = Type.PillName.copy(color = if (current) Palette.Beige else Palette.Beige3), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, style = Type.Value.copy(color = if (current) Palette.Beige2 else Palette.Beige3), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Hint(ui: HomeUi, setup: Setup, onEnable: () -> Unit) {
    val data = ui.data ?: return
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val style = Type.Value.copy(color = Palette.Beige3, textAlign = TextAlign.Center)
        when {
            !data.power -> Text("All effects are off.\nTap the power button to reconnect the chain.", style = style)
            setup.bypass -> {
                Text("${setup.name} is bypassed — audio is untouched.", style = style)
                Text(
                    "Turn effects on for this output", style = style.copy(color = Palette.Beige),
                    modifier = Modifier.padding(top = 4.dp).clickable(onClick = onEnable),
                )
            }
            ui.engine.problem != null -> Text(ui.engine.problem!!, style = style.copy(color = Palette.Amber))
            !ui.isConnected -> Text("Editing ${setup.name}. It plays when ${setup.deviceName} is connected.", style = style)
            else -> Text("Dot connects an effect · tap a pill to edit\nSwipe the device, presets and profile · hold a preset for options", style = style)
        }
    }
}
