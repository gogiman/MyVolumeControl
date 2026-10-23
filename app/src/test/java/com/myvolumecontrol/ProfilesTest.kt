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
}
