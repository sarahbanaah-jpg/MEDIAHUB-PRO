#!/usr/bin/env python3
from pathlib import Path
import re
import sys

project = Path(sys.argv[1])
java = project / "app/src/main/java/com/mediahub/rebuilt"
layout = project / "app/src/main/res/layout"
drawable = project / "app/src/main/res/drawable"
manifest = project / "app/src/main/AndroidManifest.xml"
gradle = project / "app/build.gradle"

def need(path):
    if not path.is_file():
        raise SystemExit(f"Required Android source is missing: {path}")
    return path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)

# Firebase Cloud Messaging client dependency. The sender remains a trusted Firebase
# Admin SDK / Console operation; no server credentials are embedded in the APK.
g = need(gradle)
if "com.google.firebase:firebase-messaging" not in g:
    needle = "implementation 'com.google.firebase:firebase-analytics'"
    if needle not in g:
        raise SystemExit("Firebase Analytics dependency missing; cannot safely add Messaging.")
    g = g.replace(needle, needle + "\n    implementation 'com.google.firebase:firebase-messaging'", 1)
if "androidx.work:work-runtime" not in g:
    needle = "implementation 'com.google.firebase:firebase-messaging'"
    if needle not in g:
        raise SystemExit("Firebase Messaging dependency missing; cannot safely add update polling.")
    g = g.replace(needle, needle + "\n    implementation 'androidx.work:work-runtime:2.10.5'", 1)
gradle.write_text(g, encoding="utf-8")
properties = project / "gradle.properties"
props = properties.read_text(encoding="utf-8") if properties.is_file() else ""
if re.search(r"(?m)^android\\.useAndroidX\\s*=\\s*false\\s*$", props):
    props = re.sub(r"(?m)^android\\.useAndroidX\\s*=\\s*false\\s*$", "android.useAndroidX=true", props)
elif not re.search(r"(?m)^android\\.useAndroidX\\s*=", props):
    props += "\nandroid.useAndroidX=true\n"
properties.write_text(props, encoding="utf-8")

# Subscribe every installation to the official update topic.
main = java / "MainActivity.java"
s = need(main)
anchor = "requestMediaPermissions(); updateStorageLabel(); refreshNowPlaying(); UpdateManager.checkIfDue(this);"
replacement = """try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                    .subscribeToTopic("mediahub_updates");
        } catch (Exception ignored) { /* FCM can retry after network/service recovery. */ }
        androidx.work.Constraints updateConstraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build();
        androidx.work.PeriodicWorkRequest updateWork = new androidx.work.PeriodicWorkRequest.Builder(
                ReleaseUpdateWorker.class, 12, java.util.concurrent.TimeUnit.HOURS)
                .setConstraints(updateConstraints).build();
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "mediahub_release_update_poll", androidx.work.ExistingPeriodicWorkPolicy.KEEP, updateWork);
        requestMediaPermissions(); updateStorageLabel(); refreshNowPlaying(); UpdateManager.checkIfDue(this);"""
s = replace_once(s, anchor, replacement, "MainActivity update-topic subscription")
main.write_text(s, encoding="utf-8")

# Background fallback: poll GitHub Releases every 12 hours and post a persistent local update alert.
(java / "ReleaseUpdateWorker.java").write_text(r'''package com.mediahub.rebuilt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ReleaseUpdateWorker extends Worker {
    private static final String API =
            "https://api.github.com/repos/sarahbanaah-jpg/MEDIAHUB-PRO/releases/latest";
    private static final String CHANNEL = "mediahub_forced_updates";
    private static final int NOTIFICATION_ID = 3102;

    public ReleaseUpdateWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override public Result doWork() {
        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) return Result.success();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "MEDIAHUB-PRO-Android");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return Result.retry();
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream input = connection.getInputStream()) {
                byte[] chunk = new byte[4096];
                int count;
                while ((count = input.read(chunk)) != -1) buffer.write(chunk, 0, count);
            }
            JSONObject release = new JSONObject(new String(buffer.toByteArray(),
                    java.nio.charset.StandardCharsets.UTF_8));
            String latest = release.optString("tag_name", "");
            PackageInfo installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            String current = installed.versionName == null ? "0" : installed.versionName;
            if (compare(latest, current) <= 0) return Result.success();

            String name = release.optString("name", latest);
            String body = "A required MEDIAHUB-PRO update is available: " + name
                    + ". Tap to download and install the latest version.";
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL,
                        "Required MEDIAHUB-PRO updates", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Important update alerts from MEDIAHUB-PRO");
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                NotificationManager manager = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) manager.createNotificationChannel(channel);
            }
            Intent open = new Intent(context, UpdateActivity.class)
                    .putExtra("force_update", true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent content = PendingIntent.getActivity(context, NOTIFICATION_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
            builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Mandatory MEDIAHUB-PRO update")
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(content)
                    .setCategory(Notification.CATEGORY_SYSTEM)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setDefaults(Notification.DEFAULT_ALL);
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, builder.build());
            return Result.success();
        } catch (Exception error) {
            return Result.retry();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static int compare(String a, String b) {
        try {
            String[] left = a.replaceFirst("^[vV]", "").split("[.-]");
            String[] right = b.replaceFirst("^[vV]", "").split("[.-]");
            for (int i = 0; i < Math.max(left.length, right.length); i++) {
                int x = i < left.length ? Integer.parseInt(left[i].replaceAll("[^0-9].*$", "")) : 0;
                int y = i < right.length ? Integer.parseInt(right[i].replaceAll("[^0-9].*$", "")) : 0;
                if (x != y) return Integer.compare(x, y);
            }
            return 0;
        } catch (Exception ignored) {
            return a.compareToIgnoreCase(b);
        }
    }
}
''', encoding="utf-8")

# Receive data-only FCM update messages and present a persistent, high-priority alert.
(java / "MediaHubMessagingService.java").write_text(r'''package com.mediahub.rebuilt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public final class MediaHubMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL = "mediahub_forced_updates";
    private static final int NOTIFICATION_ID = 3101;

    @Override public void onCreate() {
        super.onCreate();
        subscribeToUpdates();
        createChannel();
    }

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        subscribeToUpdates();
    }

    private void subscribeToUpdates() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic("mediahub_updates");
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        Map<String, String> data = message.getData();
        String title = data.getOrDefault("title", "MEDIAHUB-PRO update available");
        String body = data.getOrDefault("body",
                "A required app update is available. Tap to check and install it.");
        String version = data.getOrDefault("version", "");
        if (!version.isEmpty()) body = body + "  Version " + version;

        Intent open = new Intent(this, UpdateActivity.class);
        open.putExtra("force_update", true);
        open.putExtra("push_version", version);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 3101, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(content)
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true)
                .setOnlyAlertOnce(false)
                .setDefaults(Notification.DEFAULT_ALL);
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Required MEDIAHUB-PRO updates", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Important update alerts from MEDIAHUB-PRO");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableVibration(true);
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
''', encoding="utf-8")

# Register the FCM service.
m = need(manifest)
if ".MediaHubMessagingService" not in m:
    close = "</application>"
    m = replace_once(m, close, """        <service android:name=".MediaHubMessagingService" android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT"/>
            </intent-filter>
        </service>
    </application>""", "FCM service manifest registration")
manifest.write_text(m, encoding="utf-8")

# Make a newer release a blocking update: no Later button and no outside/back dismissal.
updates = java / "UpdateManager.java"
u = need(updates)
u = replace_once(
    u,
    '.setNegativeButton("Later", null)\n                            .setPositiveButton("Download", (dialog, which) ->',
    '.setPositiveButton("Download & install", (dialog, which) ->',
    "mandatory update dialog"
)
u = replace_once(
    u,
    '.setPositiveButton("Download & install", (dialog, which) ->\n                                    download(context, finalApkUrl, releaseName))\n                            .show());',
    '.setPositiveButton("Download & install", (dialog, which) ->\n                                    download(context, finalApkUrl, releaseName))\n                            .setCancelable(false)\n                            .show());',
    "non-dismissible update dialog"
)
updates.write_text(u, encoding="utf-8")

# Open the update check immediately when a user taps a forced-update notification.
update_activity = java / "UpdateActivity.java"
ua = need(update_activity)
anchor = "        refreshHistory();\n        findViewById(R.id.checkUpdateButton).setOnClickListener"
replacement = """        refreshHistory();
        if (getIntent().getBooleanExtra("force_update", false)) {
            progress.setVisibility(android.view.View.VISIBLE);
            status.setText("A required update was announced. Checking the official release…");
            UpdateManager.check(this, message -> {
                status.setText(message);
                progress.setVisibility(android.view.View.GONE);
                refreshHistory();
            });
        }
        findViewById(R.id.checkUpdateButton).setOnClickListener"""
ua = replace_once(ua, anchor, replacement, "forced-update notification launch")
update_activity.write_text(ua, encoding="utf-8")

# MediaSession-backed playback notification for Android lock-screen and headset controls.
playback = java / "PlaybackService.java"
p = need(playback)
p = replace_once(
    p,
    "    private MediaPlayer player;\n",
    "    private MediaPlayer player;\n    private android.media.session.MediaSession mediaSession;\n",
    "MediaSession field"
)
p = replace_once(
    p,
    "    @Override public void onCreate(){super.onCreate();instance=this;createChannel();}\n",
    """    @Override public void onCreate(){super.onCreate();instance=this;createChannel();initMediaSession();}
    private void initMediaSession(){
        mediaSession=new android.media.session.MediaSession(this,"MEDIAHUB-PRO");
        mediaSession.setFlags(android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new android.media.session.MediaSession.Callback(){
            @Override public void onPlay(){dispatchSessionAction("RESUME");}
            @Override public void onPause(){dispatchSessionAction("PAUSE");}
            @Override public void onSkipToNext(){dispatchSessionAction("NEXT");}
            @Override public void onSkipToPrevious(){dispatchSessionAction("PREVIOUS");}
            @Override public void onSeekTo(long position){
                if(player!=null&&prepared) player.seekTo((int)Math.max(0,position));
                updateNotification();
            }
        });
        mediaSession.setActive(true);
    }
    private void dispatchSessionAction(String action){
        Intent command=new Intent(this,PlaybackService.class).setAction(action);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(command);else startService(command);
    }
    private void updateMediaSession(boolean playing){
        if(mediaSession==null)return;
        android.content.SharedPreferences prefs=getSharedPreferences("now_playing",MODE_PRIVATE);
        String title=prefs.getString("title","MEDIAHUB-PRO");
        String artist=prefs.getString("artist","Local music");
        long duration=player!=null&&prepared?player.getDuration():0L;
        long position=player!=null&&prepared?player.getCurrentPosition():0L;
        android.media.MediaMetadata metadata=new android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE,title)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST,artist)
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,duration)
                .build();
        mediaSession.setMetadata(metadata);
        long actions=android.media.session.PlaybackState.ACTION_PLAY
                | android.media.session.PlaybackState.ACTION_PAUSE
                | android.media.session.PlaybackState.ACTION_PLAY_PAUSE
                | android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT
                | android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | android.media.session.PlaybackState.ACTION_SEEK_TO;
        android.media.session.PlaybackState state=new android.media.session.PlaybackState.Builder()
                .setActions(actions)
                .setState(playing?android.media.session.PlaybackState.STATE_PLAYING:
                        android.media.session.PlaybackState.STATE_PAUSED,position,playing?1.0f:0.0f)
                .build();
        mediaSession.setPlaybackState(state);
        mediaSession.setActive(true);
    }
""",
    "MediaSession initialization"
)
p = replace_once(
    p,
    "    private void updateNotification(){String t=getSharedPreferences",
    "    private void updateNotification(){updateMediaSession(player!=null&&player.isPlaying());String t=getSharedPreferences",
    "MediaSession notification-state update"
)
p = replace_once(
    p,
    '        if(Build.VERSION.SDK_INT>=21)b.setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0,1,2));\n',
    """        b.setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_TRANSPORT);
        if(Build.VERSION.SDK_INT>=21){
            Notification.MediaStyle style=new Notification.MediaStyle();
            if(mediaSession!=null)style.setMediaSession(mediaSession.getSessionToken());
            b.setStyle(style.setShowActionsInCompactView(0,1,2));
        }
""",
    "lock-screen MediaStyle session token"
)
p = replace_once(
    p,
    "    @Override public void onDestroy(){releasePlayer();getSharedPreferences",
    "    @Override public void onDestroy(){releasePlayer();if(mediaSession!=null){mediaSession.setActive(false);mediaSession.release();mediaSession=null;}getSharedPreferences",
    "MediaSession cleanup"
)
playback.write_text(p, encoding="utf-8")

# Liquid-glass visual language: translucent layered surfaces, bright edge highlights and soft pink tint.
(drawable / "glass_surface.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:type="linear" android:angle="315"
        android:startColor="#38FFFFFF" android:centerColor="#1EFFFFFF" android:endColor="#18FF477A"/>
    <corners android:radius="24dp"/>
    <stroke android:width="1dp" android:color="#66FFFFFF"/>
</shape>
''', encoding="utf-8")
(drawable / "glass_background.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:type="linear" android:angle="315"
        android:startColor="#17131D" android:centerColor="#09090D" android:endColor="#10131B"/>
</shape>
''', encoding="utf-8")
(drawable / "feature_card.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:type="linear" android:angle="315"
        android:startColor="#3BFFFFFF" android:centerColor="#211D202A" android:endColor="#22FF477A"/>
    <corners android:radius="24dp"/>
    <stroke android:width="1dp" android:color="#77FFFFFF"/>
</shape>
''', encoding="utf-8")
(drawable / "browse_card.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:type="linear" android:angle="315"
        android:startColor="#35FFFFFF" android:centerColor="#201C1D27" android:endColor="#17151B"/>
    <corners android:radius="20dp"/>
    <stroke android:width="1dp" android:color="#55FFFFFF"/>
</shape>
''', encoding="utf-8")
(drawable / "icon_button.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:type="linear" android:angle="315"
        android:startColor="#42FFFFFF" android:endColor="#1D24212B"/>
    <corners android:radius="22dp"/>
    <stroke android:width="1dp" android:color="#66FFFFFF"/>
</shape>
''', encoding="utf-8")

# Apply the glass surfaces to the home, player and library canvases/cards.
home = layout / "activity_main.xml"
h = need(home)
h = replace_once(h, 'android:orientation="vertical" android:background="#09090B">',
                 'android:orientation="vertical" android:background="@drawable/glass_background">',
                 "home glass background")
h = h.replace('android:background="@drawable/feature_card"\n                android:padding="15dp"',
              'android:background="@drawable/feature_card" android:elevation="8dp"\n                android:padding="15dp"', 1)
h = h.replace('android:background="@drawable/browse_card"\n                    android:clickable="true"',
              'android:background="@drawable/browse_card" android:elevation="6dp"\n                    android:clickable="true"')
home.write_text(h, encoding="utf-8")

library_layout = layout / "activity_library.xml"
if library_layout.is_file():
    ll = library_layout.read_text(encoding="utf-8")
    ll = ll.replace('android:background="#09090B"', 'android:background="@drawable/glass_background"')
    library_layout.write_text(ll, encoding="utf-8")

player_layout = layout / "activity_player.xml"
if player_layout.is_file():
    pl = player_layout.read_text(encoding="utf-8")
    pl = pl.replace('android:background="#09090B"', 'android:background="@drawable/glass_background"', 1)
    player_layout.write_text(pl, encoding="utf-8")

# Preserve the glass appearance in the app bars while keeping system bars dark and readable.
styles = project / "app/src/main/res/values/styles.xml"
if styles.is_file():
    st = styles.read_text(encoding="utf-8")
    st = st.replace('<item name="android:statusBarColor">#09090B</item>',
                    '<item name="android:statusBarColor">#101018</item>')
    st = st.replace('<item name="android:navigationBarColor">#09090B</item>',
                    '<item name="android:navigationBarColor">#101018</item>')
    styles.write_text(st, encoding="utf-8")

print("Applied v3.1 features: FCM update-topic receiver, mandatory in-app update prompt, MediaSession lock-screen controls, and liquid-glass surfaces.")
