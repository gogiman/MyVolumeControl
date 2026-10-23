package com.myvolumecontrol

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.UUID

class VolumeApplication : Application() {
    lateinit var audio: AudioController
        private set
    lateinit var store: ProfileStore
        private set
    lateinit var shortcuts: ProfileNotification
        private set
    var profiles by mutableStateOf<List<SoundProfile>>(emptyList())
        private set
    var current by mutableStateOf<SoundSnapshot?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notificationEnabled by mutableStateOf(false)
        private set
    var notificationStyle by mutableStateOf(NotificationStyle.MINIMAL)
        private set
    private var selectedProfileId by mutableStateOf<String?>(null)
    val activeProfile: SoundProfile?
        get() = findActiveProfile(profiles, current, selectedProfileId)
    private var storageHealthy = true
    private val handler = Handler(Looper.getMainLooper())
    private val refreshTask = Runnable { refresh() }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = scheduleRefresh()
    }
    private val audioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = scheduleRefresh()
    }

    override fun onCreate() {
        super.onCreate()
        audio = AudioController(this)
        store = ProfileStore(this)
        shortcuts = ProfileNotification(this)
        shortcuts.createChannel()
        perform {
            current = audio.snapshot()
            notificationEnabled = store.notificationEnabled
            try {
                profiles = store.load() ?: defaultProfiles(current!!).also { store.save(it) }
            } catch (exception: Exception) {
                storageHealthy = false
                throw IllegalStateException(
                    "Saved profiles could not be read. Your data was not overwritten. Clear app storage to reset.", exception,
                )
            }
            notificationStyle = store.notificationStyle
            selectedProfileId = store.selectedProfileId
        }
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        ContextCompat.registerReceiver(
            this, audioReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION).apply {
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        refresh()
    }

    private fun scheduleRefresh() {
        handler.removeCallbacks(refreshTask)
        handler.postDelayed(refreshTask, 200)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refresh()
    }

    private fun defaultProfiles(snapshot: SoundSnapshot): List<SoundProfile> {
        val out = snapshot.copy(
            mode = SoundMode.NORMAL,
            levels = snapshot.levels.mapValues { (stream, level) ->
                if (stream == VolumeStream.RING || stream == VolumeStream.NOTIFICATION) level.withValue(level.max) else level
            },
        )
        val silent = snapshot.copy(
            mode = SoundMode.SILENT,
            levels = snapshot.levels.mapValues { (stream, level) ->
                if (stream in listOf(VolumeStream.RING, VolumeStream.NOTIFICATION, VolumeStream.MEDIA)) level.withValue(0) else level
            },
        )
        return listOf(
            SoundProfile(UUID.randomUUID().toString(), "Out", out, true),
            SoundProfile(UUID.randomUUID().toString(), "Silent", silent, true),
            SoundProfile(UUID.randomUUID().toString(), "Home", snapshot, true),
        )
    }

    fun refresh() {
        perform {
            current = audio.snapshot()
            shortcuts.update(this)
        }
    }

    fun perform(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (exception: Exception) {
        Log.e("MyVolumeControl", "Operation failed", exception)
        error = exception.message ?: "The operation failed. Please try again."
        false
    }

    fun dismissError() { error = null }

    fun changeVolume(stream: VolumeStream, value: Int) {
        perform { audio.setVolume(stream, value) }
        refresh()
    }

    fun changeMode(mode: SoundMode) {
        perform { audio.setMode(mode) }
        refresh()
    }

    fun adjustRing(direction: Int): Boolean {
        val success = perform {
            val ring = audio.snapshot().levels.getValue(VolumeStream.RING)
            val adjusted = ring.adjustedBy(direction)
            if (adjusted.value != ring.value) audio.setVolume(VolumeStream.RING, adjusted.value)
        }
        refresh()
        return success
    }

    fun changeVibration(enabled: Boolean) {
        perform { audio.setVibrate(enabled) }
        refresh()
    }

    fun applyProfile(id: String): Boolean {
        val success = perform {
            val profile = profiles.firstOrNull { it.id == id }
                ?: error("This profile no longer exists. Open the app to choose another.")
            audio.apply(profile)
            store.selectedProfileId = id
            selectedProfileId = id
        }
        refresh()
        return success
    }

    fun saveProfile(id: String?, name: String, snapshot: SoundSnapshot): Boolean = perform {
        validateProfileName(name, profiles, id)?.let { error(it) }
        check(snapshot.dndMutedStreams.isEmpty()) {
            "Android is hiding some volume levels while Do Not Disturb is active. " +
                "Turn off Do Not Disturb before saving current settings as a new profile. " +
                "Existing profiles can still be edited and applied."
        }
        val profile = SoundProfile(
            id ?: UUID.randomUUID().toString(), name.trim(), snapshot,
            profiles.firstOrNull { it.id == id }?.pinned ?: (profiles.count { it.pinned } < 3),
        )
        updateProfiles(if (id == null) profiles + profile else profiles.map { if (it.id == id) profile else it })
    }

    fun deleteProfile(id: String) { perform { updateProfiles(profiles.filterNot { it.id == id }) } }

    fun togglePin(id: String) {
        perform {
            check(canPin(profiles, id)) { "Android shows up to three notification shortcuts. Unpin another profile first." }
            updateProfiles(profiles.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
        }
    }

    private fun updateProfiles(updated: List<SoundProfile>) {
        check(storageHealthy) { "Profile storage could not be read. Clear app storage to reset it." }
        store.save(updated)
        profiles = updated
        shortcuts.update(this)
    }

    fun enableNotification(enabled: Boolean) {
        perform {
            if (enabled) check(shortcuts.allowed()) { "Allow notifications in Android settings before enabling shortcuts." }
            store.notificationEnabled = enabled
            notificationEnabled = enabled
            shortcuts.update(this)
        }
    }

    fun changeNotificationStyle(style: NotificationStyle) {
        perform {
            store.notificationStyle = style
            notificationStyle = style
            shortcuts.update(this)
        }
    }
}
