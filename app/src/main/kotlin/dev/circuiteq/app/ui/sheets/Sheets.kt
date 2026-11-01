package dev.circuiteq.app.ui.sheets

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.circuiteq.app.ui.common.CircuitSwitch
import dev.circuiteq.app.ui.common.Eyebrow
import dev.circuiteq.app.ui.common.PillButton
import dev.circuiteq.app.ui.common.Segmented
import dev.circuiteq.app.ui.common.TextPrompt
import dev.circuiteq.app.ui.common.circuitFieldColors
import dev.circuiteq.app.ui.common.pillOutline
import dev.circuiteq.app.ui.home.ArtIcon
import dev.circuiteq.app.ui.home.label
import dev.circuiteq.app.ui.theme.Palette
import dev.circuiteq.app.ui.theme.Type
import dev.circuiteq.core.data.Preset
import dev.circuiteq.core.data.Profile
import dev.circuiteq.core.data.Setup
import dev.circuiteq.core.model.Art
import dev.circuiteq.engine.EngineMode
import dev.circuiteq.engine.EngineStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitSheet(title: String, subtitle: String?, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Panel,
        contentColor = Palette.Beige,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 14.dp).size(38.dp, 4.dp).background(Palette.Beige4, RoundedCornerShape(4.dp))) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 20.dp),
        ) {
            Text(title, style = Type.SheetTitle)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = Type.Mono.copy(fontSize = Type.Value.fontSize))
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ListRow(text: String, sub: String?, current: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).height(52.dp)
            .pillOutline(current, if (current) Palette.Beige else Palette.Beige4)
            .clickable(onClick = onClick).padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = Type.PillName.copy(color = if (current) Palette.Beige else Palette.Beige2))
            if (sub != null) Text(sub, style = Type.Value.copy(color = Palette.Beige3))
        }
        trailing()
    }
}

// ---------- profiles ----------

@Composable
fun ProfilesSheet(
    profiles: List<Profile>,
    activeId: Long,
    onSelect: (Long) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    CircuitSheet("Profiles", "Each person keeps their own outputs, presets and setups", onDismiss) {
        profiles.forEach { p ->
            ListRow(p.name, if (p.id == activeId) "Active" else null, p.id == activeId, { onSelect(p.id) }) {
                PillButton("Rename", { renaming = p })
                if (profiles.size > 1) {
                    Spacer(Modifier.width(6.dp))
                    PillButton("Delete", { onDelete(p.id) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PillButton("+ New profile", { adding = true }, solid = true)
    }
    if (adding) TextPrompt("New profile", "", "Create", { adding = false }) { onAdd(it); adding = false }
    renaming?.let { p -> TextPrompt("Rename profile", p.name, "Save", { renaming = null }) { onRename(p.id, it); renaming = null } }
}

// ---------- setup (output + headphones) ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupSheet(
    setup: Setup,
    connected: Boolean,
    format: String?,
    canDelete: Boolean,
    onSave: (String, List<Art>, Boolean) -> Unit,
    onAddHeadphones: (String, List<Art>) -> Unit,
    onAutoEq: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(setup.id) { mutableStateOf(setup.name) }
    var art by remember(setup.id) { mutableStateOf(setup.art) }
    var bypass by remember(setup.id) { mutableStateOf(setup.bypass) }
    var adding by remember { mutableStateOf(false) }
    val sub = buildString {
        append(setup.deviceName)
        if (format != null) append(" · $format")
        append(if (connected) " · connected" else " · not connected")
    }
    CircuitSheet("Output", sub, { onSave(name, art, bypass); onDismiss() }) {
        OutlinedTextField(
            value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") },
            textStyle = TextStyle(fontFamily = Type.Body.fontFamily, fontSize = Type.DeviceName.fontSize),
            colors = circuitFieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Eyebrow("Picture")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Art.entries.forEach { a ->
                val on = a in art
                Row(
                    Modifier.height(44.dp).pillOutline(on, if (on) Palette.Beige else Palette.Beige4)
                        .clickable { art = if (on) (art - a).ifEmpty { art } else (art + a).takeLast(2) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtIcon(a, 28.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(a.label, style = Type.Value.copy(color = if (on) Palette.Beige else Palette.Beige3))
                }
            }
        }
        Text("Pick up to two, e.g. DAC + IEM.", style = Type.Value.copy(color = Palette.Beige3), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Effects on this output", style = Type.PillName.copy(color = Palette.Beige))
                Text(if (bypass) "Off — audio is untouched" else "On", style = Type.Value.copy(color = Palette.Beige3))
            }
            CircuitSwitch(!bypass, { bypass = !it })
        }
        Spacer(Modifier.height(16.dp))
        ListRow("Find EQ in AutoEQ", "Correct these headphones to Harman or another target", false, { onSave(name, art, bypass); onAutoEq() })
        Spacer(Modifier.height(20.dp))
        Eyebrow("Several headphones on one DAC?")
        Text(
            "The phone only sees the DAC, not what's plugged into it. Add each IEM or headphone here and swipe the device pill to switch.",
            style = Type.Body, modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("+ Add headphones", { adding = true })
            if (canDelete) PillButton("Delete", { onDelete(); onDismiss() })
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton("Save", { onSave(name, art, bypass); onDismiss() }, solid = true)
        }
    }
    if (adding) {
        TextPrompt("Headphones on ${setup.deviceName}", "", "Add", { adding = false }) {
            val first = setup.art.firstOrNull { it == Art.DAC }
            onAddHeadphones(it, listOfNotNull(first, Art.IEM))
            adding = false
            onDismiss()
        }
    }
}

// ---------- presets ----------

@Composable
fun PresetSheet(
    preset: Preset,
    isCurrent: Boolean,
    modified: Boolean,
    onRename: (String) -> Unit,
    onOverwrite: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    CircuitSheet(preset.name, preset.description, onDismiss) {
        if (isCurrent && modified) {
            ListRow("Update with current changes", "Saves the chain as it is now into this preset", false, { onOverwrite(); onDismiss() })
        }
        ListRow("Rename", null, false, { renaming = true })
        ListRow("Delete preset", "Outputs using it keep their current settings", false, { onDelete(); onDismiss() })
    }
    if (renaming) TextPrompt("Rename preset", preset.name, "Save", { renaming = false }) { onRename(it); renaming = false; onDismiss() }
}

// ---------- settings / engine status ----------

@Composable
fun SettingsSheet(
    status: EngineStatus,
    mode: EngineMode,
    onMode: (EngineMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    CircuitSheet("Engine", "How Circuit EQ attaches to your audio", onDismiss) {
        Eyebrow("Attach to")
        Spacer(Modifier.height(8.dp))
        Segmented(
            listOf("Global (all apps)", "Per-app sessions"),
            if (mode == EngineMode.PER_APP) 1 else 0,
            { onMode(if (it == 1) EngineMode.PER_APP else EngineMode.AUTO) },
        )
        Text(
            "Global works on most phones and covers every app. If your phone blocks it, use per-app: players like Spotify and Poweramp announce their audio session and get the EQ when they start playing.",
            style = Type.Value.copy(color = Palette.Beige3), modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(18.dp))
        Eyebrow("Status")
        Spacer(Modifier.height(6.dp))
        StatusLine("Engine", if (status.running) "Running" else "Stopped")
        StatusLine("Output", status.output?.let { listOfNotNull(it.name, it.format).joinToString(" · ") } ?: "—")
        StatusLine("Global effects", if (status.globalActive) "Attached" else "Not attached")
        StatusLine("EQ bands", if (status.bandCount > 0) "${status.bandCount}" else "—")
        StatusLine("Processing", if (status.processing) "Yes" else "No")
        status.sessions.forEach { s ->
            StatusLine("Session ${s.id}", "${s.packageName}${if (s.attached) " · attached" else ""}${if (s.attached && !s.hasControl) " · lost control" else ""}")
        }
        status.problem?.let {
            Text(it, style = Type.Body.copy(color = Palette.Amber), modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(18.dp))
        Eyebrow("Keep it running")
        Text(
            "Some phones kill background apps. Allow Circuit EQ to run without battery restrictions so it stays on like Easy Effects.",
            style = Type.Body, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        val pm = context.getSystemService(PowerManager::class.java)
        val exempt = pm.isIgnoringBatteryOptimizations(context.packageName)
        PillButton(
            if (exempt) "Battery: unrestricted ✓" else "Allow unrestricted battery",
            {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                }.onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            },
            enabled = !exempt,
        )

        Spacer(Modifier.height(18.dp))
        Eyebrow("Privacy")
        Text(
            "Circuit EQ has no internet permission. Your audio, presets and files never leave this phone.",
            style = Type.Body, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = Type.Body, modifier = Modifier.width(120.dp))
        Text(value, style = Type.Mono.copy(color = Palette.Beige), modifier = Modifier.weight(1f))
    }
}

@Composable
fun Dot(on: Boolean) {
    Box(Modifier.size(8.dp).then(if (on) Modifier.background(Palette.Beige, CircleShape) else Modifier.border(1.3.dp, Palette.Beige3, CircleShape)))
}
