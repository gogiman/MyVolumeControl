package com.myvolumecontrol

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class ProfileNotification(private val context: Context) {
    companion object {
        const val CHANNEL = "profile_shortcuts"
        const val ID = 100
        const val APPLY = "com.myvolumecontrol.APPLY_PROFILE"
        const val PROFILE_ID = "profile_id"
        const val ADJUST_RING = "com.myvolumecontrol.ADJUST_RING"
        const val DIRECTION = "direction"
    }

    fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notification_channel_description)
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(manager.isNotificationPolicyAccessGranted)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
        )
    }

    fun allowed(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    }

    @SuppressLint("MissingPermission")
    fun update(app: VolumeApplication) {
        createChannel()
        val manager = NotificationManagerCompat.from(context)
        if (!app.notificationEnabled || !allowed()) {
            manager.cancel(ID)
            return
        }
        val snapshot = app.current ?: return
        val active = app.activeProfile
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val ring = snapshot.levels.getValue(VolumeStream.RING)
        val title = active?.name ?: "Custom"
        val dndNotice = snapshot.dndNotice()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_volume)
            .setContentTitle(title)
            .setContentText("${snapshot.mode.label}  \u00b7  Ring ${ring.value}/${ring.max}")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setColor(0xFF7EBAA3.toInt())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (dndNotice != null) {
            builder.setContentText("${snapshot.mode.label} \u00b7 Some levels muted by DND")
        }
        val pinned = app.profiles.filter { it.pinned }.take(3)
        when (app.notificationStyle) {
            NotificationStyle.MINIMAL -> pinned.forEach { profile ->
                val label = if (active?.id == profile.id) "\u2713 ${profile.name}" else profile.name
                builder.addAction(R.drawable.ic_volume, label, profileIntent(profile))
            }
            NotificationStyle.VOLUME_AND_PROFILES -> builder
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(volumeView(app, ring, pinned, active?.id, open, dndNotice))
        }
        if (pinned.isEmpty()) builder.setContentText("Open the app to pin up to three profiles")
        manager.notify(ID, builder.build())
    }

    private fun profileIntent(profile: SoundProfile): PendingIntent {
        val intent = Intent(context, ProfileReceiver::class.java).apply {
            action = APPLY
            data = android.net.Uri.Builder().scheme("myvolumecontrol").authority("profile").appendPath(profile.id).build()
            putExtra(PROFILE_ID, profile.id)
        }
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun volumeIntent(direction: Int): PendingIntent {
        val intent = Intent(context, ProfileReceiver::class.java).apply {
            action = ADJUST_RING
            putExtra(DIRECTION, direction)
        }
        return PendingIntent.getBroadcast(context, direction, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun volumeView(app: VolumeApplication, ring: VolumeLevel, pinned: List<SoundProfile>,
        activeId: String?, open: PendingIntent, dndNotice: String?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_volume_profiles)
        views.setTextViewText(R.id.ring_level, context.getString(R.string.notification_level, ring.value, ring.max))
        views.setProgressBar(R.id.ring_progress, ring.max, ring.value, false)
        listOf(R.id.ring_down to -1, R.id.ring_up to 1).forEach { (id, direction) ->
            val enabled = !app.audio.fixedVolume && ring.adjustedBy(direction).value != ring.value
            views.setBoolean(id, "setEnabled", enabled)
            views.setFloat(id, "setAlpha", if (enabled) 1f else 0.38f)
            views.setOnClickPendingIntent(id, volumeIntent(direction))
        }
        val chipIds = listOf(R.id.profile_one, R.id.profile_two, R.id.profile_three)
        chipIds.forEachIndexed { index, id ->
            val profile = pinned.getOrNull(index)
            views.setViewVisibility(id, if (profile == null) View.GONE else View.VISIBLE)
            if (profile != null) {
                val selected = profile.id == activeId
                views.setTextViewText(id, if (selected) "\u2713 ${profile.name}" else profile.name)
                views.setInt(id, "setBackgroundResource",
                    if (selected) R.drawable.notification_chip_active else R.drawable.notification_chip)
                views.setTextColor(id, ContextCompat.getColor(context,
                    if (selected) R.color.notification_active_text else R.color.notification_text))
                views.setContentDescription(id, context.getString(
                    if (selected) R.string.notification_active_profile else R.string.notification_apply_profile, profile.name))
                views.setOnClickPendingIntent(id, profileIntent(profile))
            }
        }
        views.setViewVisibility(R.id.profile_shortcuts, if (pinned.isEmpty()) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.empty_profiles, if (pinned.isEmpty()) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(R.id.empty_profiles, open)
        views.setViewVisibility(R.id.dnd_notice, if (dndNotice == null) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.dnd_notice, context.getString(R.string.notification_dnd_muted))
        return views
    }
}

class ProfileReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VolumeApplication
        val success = when (intent.action) {
            ProfileNotification.APPLY -> {
                val id = intent.getStringExtra(ProfileNotification.PROFILE_ID)
                if (id == null) app.perform {
                    error("The profile shortcut is invalid. Open the app to restore shortcuts.")
                } else app.applyProfile(id)
            }
            ProfileNotification.ADJUST_RING -> app.adjustRing(intent.getIntExtra(ProfileNotification.DIRECTION, 0))
            else -> return
        }
        if (success) {
            app.dismissError()
            NotificationManagerCompat.from(context).cancel(101)
            return
        }
        val message = app.error ?: "Could not change the sound settings."
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (app.shortcuts.allowed()) {
            NotificationManagerCompat.from(context).notify(101,
                NotificationCompat.Builder(context, ProfileNotification.CHANNEL)
                    .setSmallIcon(R.drawable.ic_volume)
                    .setContentTitle(if (intent.action == ProfileNotification.APPLY) "Profile not fully applied" else "Volume not changed")
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build())
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            (context.applicationContext as VolumeApplication).refresh()
        }
    }
}
