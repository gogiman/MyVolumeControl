package com.myvolumecontrol

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings

class AudioController(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    val policyGranted: Boolean get() = notifications.isNotificationPolicyAccessGranted
    val canWriteSettings: Boolean get() = Settings.System.canWrite(context)
    val fixedVolume: Boolean get() = audio.isVolumeFixed

    private fun streamId(stream: VolumeStream): Int = when (stream) {
        VolumeStream.RING -> AudioManager.STREAM_RING
        VolumeStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        VolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
        VolumeStream.ALARM -> AudioManager.STREAM_ALARM
        VolumeStream.CALL -> AudioManager.STREAM_VOICE_CALL
    }

    fun snapshot() = SoundSnapshot(
        levels = VolumeStream.entries.associateWith { stream ->
            val id = streamId(stream)
            VolumeLevel(
                audio.getStreamVolume(id),
                audio.getStreamMinVolume(id),
                audio.getStreamMaxVolume(id),
            )
        },
        mode = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> SoundMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> SoundMode.VIBRATE
            else -> SoundMode.NORMAL
        },
        vibrateOnCalls = Settings.System.getInt(
            context.contentResolver, Settings.System.VIBRATE_WHEN_RINGING, 0,
        ) != 0,
    )

    fun accessibilityLevel() = VolumeLevel(
        audio.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY),
        audio.getStreamMinVolume(AudioManager.STREAM_ACCESSIBILITY),
        audio.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY),
    )

    fun setVolume(stream: VolumeStream, value: Int) {
        check(!fixedVolume) { "This device has fixed volume and does not allow volume changes." }
        val before = snapshot()
        if (stream == VolumeStream.RING && (value == 0 || before.mode != SoundMode.NORMAL)) {
            check(policyGranted) { "Allow Do Not Disturb access to change silent or ringing mode." }
            audio.ringerMode = if (value == 0) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
        }
        val level = before.levels.getValue(stream)
        audio.setStreamVolume(streamId(stream), value.coerceIn(level.min, level.max), 0)
        check(snapshot().levels.getValue(stream).value == value.coerceIn(level.min, level.max)) {
            "Android did not accept this volume. Check Do Not Disturb and the connected audio device."
        }
    }

    fun setMode(mode: SoundMode) {
        check(policyGranted) { "Allow Do Not Disturb access to switch sound modes." }
        audio.ringerMode = when (mode) {
            SoundMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            SoundMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            SoundMode.SILENT -> AudioManager.RINGER_MODE_SILENT
        }
        check(snapshot().mode == mode) { "Android did not accept this sound mode." }
    }

    fun setVibrate(enabled: Boolean) {
        check(canWriteSettings) { "Allow Modify system settings to change vibration on calls." }
        check(Settings.System.putInt(
            context.contentResolver, Settings.System.VIBRATE_WHEN_RINGING, if (enabled) 1 else 0,
        )) { "Android did not save the call-vibration setting." }
        check(snapshot().vibrateOnCalls == enabled) { "Android did not accept the call-vibration setting." }
    }

    fun apply(profile: SoundProfile) {
        val before = snapshot()
        val target = profile.snapshot
        check(!fixedVolume) { "This device does not allow volume changes." }
        check(policyGranted || (
            target.mode == before.mode && target.levels.getValue(VolumeStream.RING).value > 0
        )) { "Open My Volume Control and allow Do Not Disturb access before switching this profile." }
        check(target.vibrateOnCalls == before.vibrateOnCalls || canWriteSettings) {
            "Open My Volume Control and allow Modify system settings for this profile's call vibration."
        }
        if (target.mode == SoundMode.NORMAL && before.mode != SoundMode.NORMAL) setMode(SoundMode.NORMAL)
        // Ring is last: some devices link notification and ring volume.
        val order = listOf(VolumeStream.MEDIA, VolumeStream.ALARM, VolumeStream.CALL,
            VolumeStream.NOTIFICATION, VolumeStream.RING)
        order.forEach { stream ->
            val current = snapshot().levels.getValue(stream)
            val value = target.levels.getValue(stream).value.coerceIn(current.min, current.max)
            audio.setStreamVolume(streamId(stream), value, 0)
        }
        if (snapshot().mode != target.mode) setMode(target.mode)
        if (snapshot().vibrateOnCalls != target.vibrateOnCalls) setVibrate(target.vibrateOnCalls)
        check(target.matches(snapshot())) {
            "Some settings were not accepted. Android may link Ring and Notifications, restrict call volume, or block changes during Do Not Disturb. Review the current levels before saving the profile again."
        }
    }
}
