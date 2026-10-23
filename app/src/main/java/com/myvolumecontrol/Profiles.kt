package com.myvolumecontrol

enum class VolumeStream(val label: String) {
    RING("Ring"),
    NOTIFICATION("Notifications"),
    MEDIA("Media"),
    ALARM("Alarm"),
    CALL("Voice call"),
}

enum class SoundMode(val label: String) {
    NORMAL("Sound"), VIBRATE("Vibrate"), SILENT("Silent"),
}

enum class NotificationStyle(val label: String, val description: String) {
    MINIMAL("Minimal", "Current profile and three native profile shortcuts."),
    VOLUME_AND_PROFILES("Volume + profiles", "Ring controls, a thin volume indicator and profile chips when expanded."),
}

data class VolumeLevel(val value: Int, val min: Int, val max: Int) {
    fun withValue(newValue: Int) = copy(value = newValue.coerceIn(min, max))

    fun adjustedBy(direction: Int): VolumeLevel {
        require(direction == -1 || direction == 1) { "Volume direction must be -1 or 1." }
        return withValue(value + direction)
    }
}

data class SoundSnapshot(
    val levels: Map<VolumeStream, VolumeLevel>,
    val mode: SoundMode,
    val vibrateOnCalls: Boolean,
) {
    fun withMode(newMode: SoundMode): SoundSnapshot {
        val ring = levels.getValue(VolumeStream.RING)
        return copy(
            mode = newMode,
            levels = if (newMode == SoundMode.NORMAL) {
                levels + (VolumeStream.RING to ring.withValue(ring.value.coerceAtLeast(1)))
            } else levels,
        )
    }

    fun matches(other: SoundSnapshot): Boolean =
        mode == other.mode && vibrateOnCalls == other.vibrateOnCalls &&
            VolumeStream.entries.all {
                (mode != SoundMode.NORMAL && it in listOf(VolumeStream.RING, VolumeStream.NOTIFICATION)) ||
                    levels.getValue(it).value == other.levels.getValue(it).value
            }
}

data class SoundProfile(
    val id: String,
    val name: String,
    val snapshot: SoundSnapshot,
    val pinned: Boolean,
)

fun validateProfileName(name: String, profiles: List<SoundProfile>, editingId: String?): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Enter a profile name."
        trimmed.length > 20 -> "Use 20 characters or fewer so the shortcut stays readable."
        profiles.any { it.id != editingId && it.name.equals(trimmed, ignoreCase = true) } ->
            "A profile with this name already exists."
        else -> null
    }
}

fun canPin(profiles: List<SoundProfile>, id: String): Boolean =
    profiles.any { it.id == id && it.pinned } || profiles.count { it.pinned } < 3
