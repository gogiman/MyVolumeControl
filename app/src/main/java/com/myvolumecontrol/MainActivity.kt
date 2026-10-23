package com.myvolumecontrol

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VolumeApplication
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else if (dark) {
                darkColorScheme(
                    primary = Color(0xFFA1D5BE),
                    secondaryContainer = Color(0xFF2B493D),
                    background = Color(0xFF101613),
                    surface = Color(0xFF101613),
                    surfaceVariant = Color(0xFF26332C),
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF286B50),
                    secondaryContainer = Color(0xFFC4EBD7),
                    background = Color(0xFFF5FAF5),
                    surface = Color(0xFFF5FAF5),
                    surfaceVariant = Color(0xFFE1EBE3),
                )
            }
            MaterialTheme(colorScheme = colors) {
                var permissionVersion by remember { mutableIntStateOf(0) }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            app.refresh()
                            permissionVersion++
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                VolumeScreen(app, permissionVersion, ::openSettings)
            }
        }
    }

    private fun openSettings(action: String) {
        val intent = Intent(action).apply {
            when (action) {
                Settings.ACTION_MANAGE_WRITE_SETTINGS -> data = Uri.parse("package:$packageName")
                Settings.ACTION_APP_NOTIFICATION_SETTINGS -> putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        }
        (application as VolumeApplication).perform { startActivity(intent) }
    }
}

@Composable
private fun VolumeScreen(app: VolumeApplication, permissionVersion: Int, openSettings: (String) -> Unit) {
    var editing by remember { mutableStateOf<SoundProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SoundProfile?>(null) }
    var showLevels by remember { mutableStateOf(true) }
    val snapshot = app.current
    val active = app.profiles.firstOrNull { snapshot != null && it.snapshot.matches(snapshot) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.enableNotification(true)
        else app.perform { error("Notification permission was denied. Enable it in Notification settings to use shortcuts.") }
    }
    val policyGranted = remember(permissionVersion) { app.audio.policyGranted }
    val canWrite = remember(permissionVersion) { app.audio.canWriteSettings }
    val notificationsAllowed = remember(permissionVersion, app.notificationEnabled) { app.shortcuts.allowed() }

    Scaffold { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Outlined.VolumeUp, null, Modifier.padding(14.dp).size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("My Volume Control", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Less noise. One tap.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CURRENT SOUND", style = MaterialTheme.typography.labelMedium)
                    Text(active?.name ?: "Custom", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    snapshot?.let {
                        val ring = it.levels.getValue(VolumeStream.RING)
                        Text("${it.mode.label}  \u00b7  Ring ${ring.value}/${ring.max}",
                            style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("Switch below, or use your notification shortcuts.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sound profiles", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { adding = true }, enabled = snapshot != null) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
                Text("Pin up to three profiles for one-tap notification access.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (app.profiles.isEmpty()) {
                    Text("No saved profiles yet. Tap New to save your current sound settings.")
                }
                app.profiles.forEach { profile ->
                    ProfileCard(
                        profile, active?.id == profile.id,
                        onApply = { app.applyProfile(profile.id) },
                        onPin = { app.togglePin(profile.id) },
                        onEdit = { editing = profile },
                        onDelete = { deleting = profile },
                    )
                }
            }

            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsNone, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Notification shortcuts", style = MaterialTheme.typography.titleMedium)
                            Text("Quiet, compact, always within reach", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = app.notificationEnabled,
                            modifier = Modifier.semantics { contentDescription = "Notification shortcuts" },
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else app.enableNotification(enabled)
                            },
                        )
                    }
                    if (app.notificationEnabled && !notificationsAllowed) {
                        Text("Notifications are blocked by Android.", color = MaterialTheme.colorScheme.error)
                    }
                    HorizontalDivider()
                    Text("Notification style", style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.selectableGroup()) {
                        NotificationStyle.entries.forEach { style ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(
                                        selected = app.notificationStyle == style,
                                        role = Role.RadioButton,
                                        onClick = { app.changeNotificationStyle(style) },
                                    )
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = app.notificationStyle == style, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(style.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(style.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Text("Style changes apply immediately and are saved. Android controls the outer layout; expand the notification to reveal all controls.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) }) {
                        Text("Notification settings")
                    }
                }
            }

            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Tune, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Current volume", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showLevels = !showLevels }) { Text(if (showLevels) "Hide" else "Show") }
                    }
                    if (showLevels && snapshot != null) {
                        SoundModePicker(snapshot.mode, app::changeMode)
                        VolumeStream.entries.forEach { stream ->
                            VolumeSlider(stream.label, snapshot.levels.getValue(stream), !app.audio.fixedVolume) {
                                app.changeVolume(stream, it)
                            }
                        }
                        val accessibility = app.audio.accessibilityLevel()
                        Row(Modifier.fillMaxWidth()) {
                            Text("Accessibility", modifier = Modifier.weight(1f))
                            Text("${accessibility.value}/${accessibility.max}")
                        }
                        Text("Managed by Android or your accessibility service. It is not changed by profiles.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Vibrate on calls")
                                Text(if (canWrite) "Device support varies" else "Requires Modify system settings",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(snapshot.vibrateOnCalls, onCheckedChange = app::changeVibration, enabled = canWrite,
                                modifier = Modifier.semantics { contentDescription = "Vibrate on calls" })
                        }
                        TextButton(onClick = { adding = true }) { Text("Save current settings as a profile") }
                    }
                }
            }

            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Android permissions", style = MaterialTheme.typography.titleMedium)
                    PermissionRow("Sound mode access",
                        "Required to switch Silent, Vibrate and Sound. The app never reads notifications.",
                        policyGranted) { openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
                    HorizontalDivider()
                    PermissionRow("Call vibration",
                        "Optional. Allows changing the system's vibrate-on-calls setting.",
                        canWrite) { openSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS) }
                    Text("Silent profiles keep alarms as configured. Calls and notifications can be linked by your phone. Accessibility volume and existing Do Not Disturb rules remain under Android's control.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Private by design. No internet permission. No background audio service.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }

    if ((adding || editing != null) && snapshot != null) {
        ProfileEditor(
            profile = editing,
            initial = editing?.snapshot ?: snapshot,
            profiles = app.profiles,
            canChangeVibration = canWrite,
            onDismiss = { adding = false; editing = null },
            onSave = { name, settings ->
                if (app.saveProfile(editing?.id, name, settings)) { adding = false; editing = null }
            },
        )
    }
    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("This also removes its notification shortcut. Your current volume will not change.") },
            confirmButton = { TextButton(onClick = { app.deleteProfile(profile.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    app.error?.let { message ->
        AlertDialog(
            onDismissRequest = app::dismissError,
            title = { Text("Couldn't complete the change") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = app::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun ProfileCard(profile: SoundProfile, active: Boolean, onApply: () -> Unit,
    onPin: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(
        containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    )) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (profile.pinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Outlined.PushPin, "Pinned to notification", Modifier.size(14.dp))
                    }
                }
                Text(if (active) "Active now" else profile.snapshot.mode.label,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onApply) {
                if (active) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp))
                Text(if (active) "Active" else "Apply")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Options for ${profile.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit profile") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(if (profile.pinned) "Unpin shortcut" else "Pin shortcut") },
                        leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                        onClick = { menu = false; onPin() })
                    DropdownMenuItem(text = { Text("Delete profile") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, granted: Boolean, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpen) { Text(if (granted) "Granted" else "Allow") }
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundModePicker(mode: SoundMode, onSelect: (SoundMode) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SoundMode.entries.forEach { item ->
            FilterChip(selected = item == mode, onClick = { onSelect(item) }, label = { Text(item.label) })
        }
    }
}

@Composable
private fun VolumeSlider(label: String, level: VolumeLevel, enabled: Boolean = true, onCommit: (Int) -> Unit) {
    var position by remember(level) { mutableFloatStateOf(level.value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${position.roundToInt()}/${level.max}", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            modifier = Modifier.semantics { contentDescription = "$label volume" },
            value = position.coerceIn(level.min.toFloat(), level.max.toFloat()),
            onValueChange = { position = it },
            onValueChangeFinished = { onCommit(position.roundToInt()) },
            valueRange = level.min.toFloat()..maxOf(level.max, level.min + 1).toFloat(),
            steps = (level.max - level.min - 1).coerceAtLeast(0),
            enabled = enabled && level.max > level.min,
        )
    }
}

@Composable
private fun ProfileEditor(profile: SoundProfile?, initial: SoundSnapshot, profiles: List<SoundProfile>,
    canChangeVibration: Boolean, onDismiss: () -> Unit, onSave: (String, SoundSnapshot) -> Unit) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name ?: "") }
    var draft by remember(profile?.id) { mutableStateOf(initial) }
    var attempted by remember { mutableStateOf(false) }
    val nameError = validateProfileName(name, profiles, profile?.id)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 760.dp),
            shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (profile == null) "New profile" else "Edit profile",
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close profile editor") }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, onValueChange = { name = it }, label = { Text("Profile name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        isError = attempted && nameError != null,
                        supportingText = { Text(if (attempted && nameError != null) nameError else "Up to 20 characters") })
                    SoundModePicker(draft.mode) { draft = draft.withMode(it) }
                    VolumeStream.entries.forEach { stream ->
                        VolumeSlider(stream.label, draft.levels.getValue(stream),
                            enabled = draft.mode == SoundMode.NORMAL ||
                                stream !in listOf(VolumeStream.RING, VolumeStream.NOTIFICATION)) { value ->
                            draft = draft.copy(
                                levels = draft.levels + (stream to draft.levels.getValue(stream).withValue(value)),
                                mode = if (stream == VolumeStream.RING && value == 0) SoundMode.SILENT else draft.mode,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vibrate on calls", modifier = Modifier.weight(1f))
                        Switch(draft.vibrateOnCalls,
                            onCheckedChange = { draft = draft.copy(vibrateOnCalls = it) },
                            modifier = Modifier.semantics { contentDescription = "Profile vibration on calls" },
                            enabled = canChangeVibration)
                    }
                    Text("Editing does not change your phone until you apply the profile. Silent and Vibrate modes can force Ring and Notifications to zero on some phones.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        draft = initial
                        if (profile == null) name = ""
                    }) { Text("Reset changes") }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { attempted = true; if (nameError == null) onSave(name, draft) }) { Text("Save profile") }
                }
            }
        }
    }
}
