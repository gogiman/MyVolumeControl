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
    val dndMutedStreams: Set<VolumeStream> = emptySet(),
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

    fun differencesFrom(actual: SoundSnapshot): List<String> = buildList {
        if (mode != actual.mode) add("Sound mode: requested ${mode.label}, actual ${actual.mode.label}")
        if (vibrateOnCalls != actual.vibrateOnCalls) add("Call vibration was not accepted")
        VolumeStream.entries.forEach { stream ->
            val hiddenByRinger = actual.mode != SoundMode.NORMAL &&
                stream in listOf(VolumeStream.RING, VolumeStream.NOTIFICATION)
            val expected = levels.getValue(stream)
            val observed = actual.levels.getValue(stream)
            // DND returns zero for muted streams, not the stored volume index.
            val hiddenByDnd = stream in actual.dndMutedStreams && observed.value == 0
            if (!hiddenByRinger && !hiddenByDnd &&
                expected.value.coerceIn(observed.min, observed.max) != observed.value) {
                add("${stream.label}: requested ${expected.value}, actual ${observed.value}")
            }
        }
    }

    fun matches(other: SoundSnapshot): Boolean = differencesFrom(other).isEmpty()

    fun dndNotice(): String? = if (dndMutedStreams.isEmpty()) null else
        "Do Not Disturb is muting ${dndMutedStreams.joinToString { it.label }}. " +
            "Android hides their stored levels; these cannot be verified until DND allows them."
}

data class SoundProfile(
    val id: String,
    val name: String,
    val snapshot: SoundSnapshot,
    val pinned: Boolean,
)

fun findActiveProfile(
    profiles: List<SoundProfile>,
    current: SoundSnapshot?,
    selectedId: String?,
): SoundProfile? {
    if (current == null) return null
    return profiles.firstOrNull { it.id == selectedId && it.snapshot.matches(current) }
        ?: profiles.firstOrNull { it.snapshot.matches(current) }
}

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

fun profilesInOrder(profiles: List<SoundProfile>, orderedIds: List<String>): List<SoundProfile> {
    val byId = profiles.associateBy { it.id }
    require(orderedIds.size == profiles.size && orderedIds.toSet() == byId.keys) {
        "The profile list changed. Try reordering it again."
    }
    return orderedIds.map(byId::getValue)
}

fun moveProfile(profiles: List<SoundProfile>, id: String, targetIndex: Int): List<SoundProfile> {
    require(targetIndex in profiles.indices) { "Invalid profile position." }
    val from = profiles.indexOfFirst { it.id == id }
    require(from >= 0) { "This profile no longer exists." }
    if (from == targetIndex) return profiles
    return profiles.toMutableList().apply { add(targetIndex, removeAt(from)) }
}
