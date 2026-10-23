package com.myvolumecontrol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences("sound_profiles", Context.MODE_PRIVATE)

    var notificationEnabled: Boolean
        get() = preferences.getBoolean("notification_enabled", false)
        set(value) {
            check(preferences.edit().putBoolean("notification_enabled", value).commit()) {
                "Could not save the notification preference."
            }
        }

    var notificationStyle: NotificationStyle
        get() = NotificationStyle.valueOf(
            preferences.getString("notification_style", NotificationStyle.MINIMAL.name)
                ?: error("The notification style could not be read."),
        )
        set(value) {
            check(preferences.edit().putString("notification_style", value.name).commit()) {
                "Could not save the notification style."
            }
        }

    fun load(): List<SoundProfile>? {
        val text = preferences.getString("profiles", null) ?: return null
        val array = JSONArray(text)
        val profiles = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val volumes = item.getJSONObject("volumes")
            SoundProfile(
                id = item.getString("id"),
                name = item.getString("name"),
                pinned = item.getBoolean("pinned"),
                snapshot = SoundSnapshot(
                    levels = VolumeStream.entries.associateWith { stream ->
                        val level = volumes.getJSONObject(stream.name)
                        val min = level.getInt("min")
                        val max = level.getInt("max")
                        val value = level.getInt("value")
                        require(min >= 0 && max >= min && value in min..max) {
                            "Invalid saved volume for ${stream.label}."
                        }
                        VolumeLevel(value, min, max)
                    },
                    mode = SoundMode.valueOf(item.getString("mode")),
                    vibrateOnCalls = item.getBoolean("vibrate"),
                ),
            )
        }
        require(profiles.map { it.id }.distinct().size == profiles.size)
        require(profiles.count { it.pinned } <= 3)
        return profiles
    }

    fun save(profiles: List<SoundProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            val volumes = JSONObject()
            profile.snapshot.levels.forEach { (stream, level) ->
                volumes.put(stream.name, JSONObject().apply {
                    put("value", level.value)
                    put("min", level.min)
                    put("max", level.max)
                })
            }
            array.put(JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("pinned", profile.pinned)
                put("mode", profile.snapshot.mode.name)
                put("vibrate", profile.snapshot.vibrateOnCalls)
                put("volumes", volumes)
            })
        }
        check(preferences.edit().putString("profiles", array.toString()).commit()) {
            "Could not save sound profiles."
        }
    }
}
