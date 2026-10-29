# My Volume Control

A Kotlin / Jetpack Compose Android app for sound profiles with two quiet
notification styles: minimal profile switching, or volume controls with profile chips.

## Open and run

Open this directory in Android Studio, let Gradle sync, and run the `app`
configuration on an Android 9+ device. `local.properties` points at the local SDK;
change it if your SDK is elsewhere. Use a Gradle-compatible JDK in Android Studio.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is `app\build\outputs\apk\debug\app-debug.apk`.

## Everyday use

* Start with Out, Silent and Home, or create your own profiles. The defaults are
  examples captured from the device on first launch; they do not import another app.
* Apply, edit, rename or delete profiles. New profiles start from current volumes.
  Editing is a draft and does not change the phone until Apply is tapped.
  If profiles have identical settings (such as the initial Home and Out profiles),
  the last successfully selected profile remains highlighted in the app and both
  notification styles, even after restarting. Manual changes still show Custom
  when no saved profile matches the actual sound settings.
* Pin up to three profiles using each profile's menu. Enable **Notification
  shortcuts**, and allow notification permission on Android 13+.
* Hold a profile's drag handle and move it up or down to reorder. Drag near the
  top or bottom of the screen to scroll a longer list. Release to save; cancelling
  a drag leaves the previous order unchanged. The profile menu and accessibility
  actions also offer **Move up** and **Move down**.
  Both notification styles show pinned profiles in the saved list order.
  Reordering does not change pin membership, current sound or the selected profile.
* Choose **Notification shortcuts > Notification style**:
  **Minimal** (the default) shows the matching profile (or Custom), sound mode,
  ring level and up to three native profile actions.
  **Volume + profiles** adds an expanded layout with ring-volume minus/plus buttons,
  a thin volume indicator, and rounded profile chips. The active chip is highlighted.
  Expand the notification to see these controls; its collapsed form stays compact.
  The indicator is not a draggable slider: use the minus/plus buttons to adjust.
* Style changes update the existing notification immediately without changing
  sound settings. The choice is saved across app restarts and reboots, including
  when notifications are disabled. Both styles use the same pinned profiles.
* Current volume provides Ring, Notifications, Media, Alarm and Voice call sliders,
  Sound / Vibrate / Silent selection, and optional call vibration.
* Profiles and notification preferences are stored locally. There is no internet
  permission, analytics, account, or notification-listener access.
* **Support development** at the bottom of the main screen has an optional
  **Buy me a coffee** button linking to https://ko-fi.com/gogiman. It opens an
  external browser; Ko-fi handles payments. All app features remain free, and
  the app itself still needs no internet permission.

## Permissions and Android limitations

**Do Not Disturb access** is needed to change modes or cross into/out of silence.
This does not grant access to notification contents. The app does not deliberately
disable existing DND rules. Android 15+ manages app-owned DND behavior differently;
device rules may still prevent a profile from taking effect.
With this access, the silent shortcut channel is allowed to bypass DND so it stays
visible when a Silent profile is applied. Its sound and vibration remain off.
If you customize the channel yourself, Android preserves your channel preferences;
allow it through DND in notification settings if shortcuts disappear in Silent.

**Modify system settings** is optional and used only for Android's legacy
vibrate-on-calls setting. OEMs can ignore this setting or provide richer vibration
options. The app does not request an accessibility service merely to control
accessibility volume; that stream is shown read-only and excluded from profiles.

Android can link Ring and Notification volumes, restrict call volume, impose
headphone safety limits, or use device-specific sound routing. Actual values are
read back after profile application; a partial or blocked application produces
an explicit error, rather than falsely marking a profile as active. Silent does
not mean every sound is muted: saved alarm/media levels are independent.

On some phones (including Pixel Android 12), selecting Silent activates DND and
can mute alarms too, depending on the phone's DND policy. Android then reports zero
instead of the stored volume for those muted streams. The app excludes only these
DND-hidden values from matching and verification and displays a DND notice, rather
than falsely rejecting the profile. Other mismatches still produce an error with
requested and actual values. Hidden levels are checked again once DND unmutes them.
To capture a new profile from current settings, first disable DND so the stored
levels are readable; existing profiles remain editable. The app does not change
your DND policy to allow alarms.

Both styles keep Android's system notification header; the expanded volume layout
uses supported RemoteViews and light/dark chip colors. Android/OEM controls outer
spacing, action visibility and lock-screen behavior. Expand the notification to
access all controls. Volume changes use the same permissions and device limits
as the main screen, and blocked changes display an error. Android 14+ permits
users to dismiss ongoing notifications. Reopen the app to restore one. A boot
receiver restores enabled shortcuts after reboot (after the first unlock); force
stopping the app prevents restoration until it is opened again.

No always-running service is used. The notification's profile actions work even
if the activity is closed, and re-read actual device settings. Changes made
outside the app are reflected while its process is alive; after Android kills
the process, the displayed status may be stale until a shortcut is used or the
app is reopened. The app never automatically changes sound profiles on reboot.
