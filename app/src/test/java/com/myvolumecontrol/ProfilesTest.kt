package com.myvolumecontrol

import org.junit.Assert.*
import org.junit.Test

class ProfilesTest {
    private fun snapshot() = SoundSnapshot(
        VolumeStream.entries.associateWith { VolumeLevel(3, 0, 7) }, SoundMode.NORMAL, false,
    )

    private fun profile(id: String, pinned: Boolean = true) = SoundProfile(id, id, snapshot(), pinned)

    @Test fun `profile names must be nonempty and fit notification shortcuts`() {
        assertNotNull(validateProfileName("   ", emptyList(), null))
        assertNotNull(validateProfileName("x".repeat(21), emptyList(), null))
        assertNull(validateProfileName("x".repeat(20), emptyList(), null))
    }

    @Test fun `names are unique ignoring case and whitespace but can retain their own name`() {
        val profiles = listOf(profile("Home"))
        assertNotNull(validateProfileName(" home ", profiles, null))
        assertNull(validateProfileName(" Home ", profiles, "Home"))
    }

    @Test fun `only three profiles can be pinned but existing shortcuts can be unpinned`() {
        val profiles = listOf(profile("1"), profile("2"), profile("3"), profile("4", false))
        assertFalse(canPin(profiles, "4"))
        assertTrue(canPin(profiles, "2"))
        assertTrue(canPin(profiles.drop(1), "4"))
    }

    @Test fun `custom volume changes invalidate the active profile`() {
        val saved = snapshot()
        val changed = saved.copy(levels = saved.levels + (VolumeStream.MEDIA to VolumeLevel(4, 0, 7)))
        assertTrue(saved.matches(snapshot()))
        assertFalse(saved.matches(changed))
        assertFalse(saved.matches(saved.copy(mode = SoundMode.SILENT)))
        assertFalse(saved.matches(saved.copy(vibrateOnCalls = true)))
    }

    @Test fun `volume adjustments clamp to actual device limits`() {
        assertEquals(1, VolumeLevel(3, 1, 6).withValue(0).value)
        assertEquals(6, VolumeLevel(3, 1, 6).withValue(99).value)
        assertEquals(4, VolumeLevel(3, 1, 6).withValue(4).value)
    }

    @Test fun `silent and vibrate match despite Android hiding stored ring volumes`() {
        for (mode in listOf(SoundMode.SILENT, SoundMode.VIBRATE)) {
            val saved = snapshot().copy(mode = mode)
            val actual = saved.copy(levels = saved.levels.mapValues { (stream, level) ->
                if (stream in listOf(VolumeStream.RING, VolumeStream.NOTIFICATION)) level.withValue(0) else level
            })
            assertTrue(saved.matches(actual))
        }
    }

    @Test fun `switching an edited silent profile to sound restores a nonzero ring level`() {
        val silent = snapshot().copy(
            mode = SoundMode.SILENT,
            levels = snapshot().levels + (VolumeStream.RING to VolumeLevel(0, 0, 7)),
        )
        assertEquals(1, silent.withMode(SoundMode.NORMAL).levels.getValue(VolumeStream.RING).value)
        assertEquals(SoundMode.NORMAL, silent.withMode(SoundMode.NORMAL).mode)
    }

    @Test fun `notification volume steps clamp at both limits`() {
        assertEquals(0, VolumeLevel(0, 0, 7).adjustedBy(-1).value)
        assertEquals(7, VolumeLevel(7, 0, 7).adjustedBy(1).value)
        assertEquals(3, VolumeLevel(4, 0, 7).adjustedBy(-1).value)
        assertEquals(5, VolumeLevel(4, 0, 7).adjustedBy(1).value)
        assertEquals(2, VolumeLevel(2, 2, 7).adjustedBy(-1).value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid notification volume direction is rejected`() {
        VolumeLevel(3, 0, 7).adjustedBy(0)
    }

    @Test fun `notification style names round trip for persistence`() {
        NotificationStyle.entries.forEach { style ->
            assertEquals(style, NotificationStyle.valueOf(style.name))
        }
        assertEquals(NotificationStyle.MINIMAL, NotificationStyle.entries.first())
    }

    @Test fun `Silent matches when DND hides the configured alarm level on Pixel`() {
        val requested = snapshot().copy(mode = SoundMode.SILENT)
        val actual = requested.copy(
            levels = requested.levels + (VolumeStream.ALARM to VolumeLevel(0, 1, 7)),
            dndMutedStreams = setOf(VolumeStream.ALARM),
        )
        assertTrue(requested.matches(actual))
        assertNotNull(actual.dndNotice())
        assertNull(requested.dndNotice())
    }

    @Test fun `muted level without DND remains a rejected setting`() {
        val requested = snapshot().copy(mode = SoundMode.SILENT)
        val actual = requested.copy(
            levels = requested.levels + (VolumeStream.ALARM to VolumeLevel(0, 1, 7)),
        )
        assertFalse(requested.matches(actual))
        assertEquals(listOf("Alarm: requested 3, actual 0"), requested.differencesFrom(actual))
    }

    @Test fun `DND cannot hide wrong mode vibration or audible stream levels`() {
        val requested = snapshot().copy(mode = SoundMode.SILENT)
        val actual = requested.copy(
            mode = SoundMode.NORMAL,
            vibrateOnCalls = true,
            levels = requested.levels +
                (VolumeStream.ALARM to VolumeLevel(0, 1, 7)) +
                (VolumeStream.MEDIA to VolumeLevel(1, 0, 7)),
            dndMutedStreams = setOf(VolumeStream.ALARM),
        )
        val differences = requested.differencesFrom(actual)
        assertEquals(3, differences.size)
        assertTrue(differences.any { it.startsWith("Media:") })
        assertFalse(requested.matches(actual))
    }

    @Test fun `unmuted alarm is verified again after leaving Silent`() {
        val requested = snapshot()
        val wrong = requested.copy(
            levels = requested.levels + (VolumeStream.ALARM to VolumeLevel(4, 1, 7)),
        )
        assertFalse(requested.matches(wrong))
        assertTrue(requested.matches(snapshot()))
    }

    @Test fun `DND marked stream with nonzero readback is still verified`() {
        val requested = snapshot()
        val wrong = requested.copy(
            levels = requested.levels + (VolumeStream.ALARM to VolumeLevel(4, 1, 7)),
            dndMutedStreams = setOf(VolumeStream.ALARM),
        )
        assertFalse(requested.matches(wrong))
    }

    @Test fun `readback comparison uses same device limits as application`() {
        val requested = snapshot().copy(
            levels = snapshot().levels + (VolumeStream.MEDIA to VolumeLevel(15, 0, 25)),
        )
        val actual = snapshot().copy(
            levels = snapshot().levels + (VolumeStream.MEDIA to VolumeLevel(7, 0, 7)),
        )
        assertTrue(requested.matches(actual))
    }

    @Test fun `selected Home wins over identical Out settings`() {
        val profiles = listOf(profile("Out"), profile("Home"))
        assertEquals("Home", findActiveProfile(profiles, snapshot(), "Home")?.id)
        assertEquals("Out", findActiveProfile(profiles, snapshot(), "Out")?.id)
    }

    @Test fun `selected profile is not active when actual sound differs`() {
        val profiles = listOf(profile("Out"), profile("Home"))
        val custom = snapshot().copy(mode = SoundMode.VIBRATE)
        assertNull(findActiveProfile(profiles, custom, "Home"))
        assertNull(findActiveProfile(profiles, null, "Home"))
    }

    @Test fun `deleted or nonmatching selected profile falls back to actual matching profile`() {
        val home = profile("Home").copy(snapshot = snapshot().copy(mode = SoundMode.SILENT))
        val profiles = listOf(profile("Out"), home)
        assertEquals("Out", findActiveProfile(profiles, snapshot(), "Home")?.id)
        assertEquals("Out", findActiveProfile(profiles, snapshot(), "deleted")?.id)
        assertEquals("Out", findActiveProfile(profiles, snapshot(), null)?.id)
    }

    @Test fun `dragging last profile to first preserves settings and pins`() {
        val profiles = listOf(profile("Out"), profile("Silent"), profile("Home"))
        val moved = moveProfile(profiles, "Home", 0)
        assertEquals(listOf("Home", "Out", "Silent"), moved.map { it.id })
        assertEquals(profiles.toSet(), moved.toSet())
        assertEquals("Home", findActiveProfile(moved, snapshot(), "Home")?.id)
    }

    @Test fun `dragging first profile to last works in both directions`() {
        val profiles = listOf(profile("Out"), profile("Silent"), profile("Home"))
        val moved = moveProfile(profiles, "Out", 2)
        assertEquals(listOf("Silent", "Home", "Out"), moved.map { it.id })
        assertEquals(profiles, moveProfile(moved, "Out", 0))
        assertSame(profiles, moveProfile(profiles, "Out", 0))
    }

    @Test fun `notification buttons follow pinned subsequence of saved order`() {
        val profiles = listOf(profile("Out"), profile("Other", false), profile("Silent"), profile("Home"))
        val ordered = profilesInOrder(profiles, listOf("Home", "Other", "Out", "Silent"))
        assertEquals(listOf("Home", "Out", "Silent"), ordered.filter { it.pinned }.map { it.id })
        assertEquals(ordered, profilesInOrder(profiles, ordered.map { it.id }))
        assertEquals(1, ordered.count { !it.pinned })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reorder cannot silently drop profiles`() {
        profilesInOrder(listOf(profile("Out"), profile("Home")), listOf("Home"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reorder rejects duplicate IDs`() {
        profilesInOrder(listOf(profile("Out"), profile("Home")), listOf("Out", "Out"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reorder rejects deleted or unknown IDs`() {
        profilesInOrder(listOf(profile("Out"), profile("Home")), listOf("Out", "Unknown"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `moving outside list bounds is rejected`() {
        moveProfile(listOf(profile("Home")), "Home", -1)
    }
}
