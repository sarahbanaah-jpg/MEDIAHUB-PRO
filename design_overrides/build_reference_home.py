from pathlib import Path
import sys

project = Path(sys.argv[1])
res = project / "app/src/main/res"
layout = res / "layout"
main = project / "app/src/main/java/com/mediahub/rebuilt/MainActivity.java"

xml = r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 android:layout_width="match_parent" android:layout_height="match_parent"
 android:orientation="vertical" android:background="#000000">
 <ScrollView android:layout_width="match_parent" android:layout_height="0dp"
  android:layout_weight="1" android:fillViewport="true" android:clipToPadding="false">
  <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
   android:orientation="vertical" android:paddingTop="12dp" android:paddingBottom="18dp">
   <LinearLayout android:layout_width="match_parent" android:layout_height="52dp"
    android:gravity="center_vertical" android:paddingStart="18dp" android:paddingEnd="18dp">
    <TextView android:id="@+id/settingsButton" android:layout_width="42dp" android:layout_height="42dp"
     android:gravity="center" android:text="☰" android:textColor="#FFFFFF" android:textSize="25sp"
     android:background="@drawable/icon_button" android:clickable="true" android:focusable="true"/>
    <TextView android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
     android:gravity="center" android:text="MEDIAHUB PRO" android:textColor="#FFFFFF"
     android:textSize="19sp" android:textStyle="bold"/>
    <TextView android:layout_width="42dp" android:layout_height="42dp" android:gravity="center"
     android:text="⌕" android:textColor="#FFFFFF" android:textSize="28sp"/>
   </LinearLayout>
   <TextView android:id="@+id/summaryText" android:layout_width="match_parent" android:layout_height="wrap_content"
    android:paddingStart="20dp" android:paddingEnd="20dp" android:text="Your media"
    android:textColor="#9A9A9A" android:textSize="12sp"/>
   <TextView android:id="@+id/storageStatus" android:layout_width="match_parent" android:layout_height="1dp"
    android:visibility="gone"/>
   <LinearLayout android:id="@+id/homeContent" android:layout_width="match_parent"
    android:layout_height="wrap_content" android:orientation="vertical" android:paddingTop="8dp"/>
   <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="vertical" android:padding="16dp">
    <TextView android:text="Your library" android:textColor="#FFFFFF" android:textSize="20sp"
     android:textStyle="bold" android:layout_width="match_parent" android:layout_height="wrap_content"/>
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
     android:orientation="horizontal" android:layout_marginTop="12dp">
     <TextView android:id="@+id/musicButton" android:layout_width="0dp" android:layout_height="48dp"
      android:layout_weight="1" android:gravity="center" android:text="♫  Music" android:textColor="#FFFFFF"
      android:background="@drawable/action_card" android:clickable="true"/>
     <TextView android:id="@+id/videoButton" android:layout_width="0dp" android:layout_height="48dp"
      android:layout_weight="1" android:layout_marginStart="8dp" android:gravity="center"
      android:text="▶  Videos" android:textColor="#FFFFFF" android:background="@drawable/action_card" android:clickable="true"/>
     <TextView android:id="@+id/photosButton" android:layout_width="0dp" android:layout_height="48dp"
      android:layout_weight="1" android:layout_marginStart="8dp" android:gravity="center"
      android:text="▧  Photos" android:textColor="#FFFFFF" android:background="@drawable/action_card" android:clickable="true"/>
    </LinearLayout>
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
     android:orientation="horizontal" android:layout_marginTop="8dp">
     <TextView android:id="@+id/playlistsButton" android:layout_width="0dp" android:layout_height="44dp"
      android:layout_weight="1" android:gravity="center" android:text="Playlists" android:textColor="#FFFFFF"
      android:background="@drawable/action_card" android:clickable="true"/>
     <TextView android:id="@+id/favouritesButton" android:layout_width="0dp" android:layout_height="44dp"
      android:layout_weight="1" android:layout_marginStart="8dp" android:gravity="center"
      android:text="♡ Favourites" android:textColor="#FFFFFF" android:background="@drawable/action_card" android:clickable="true"/>
     <TextView android:id="@+id/equalizerButton" android:layout_width="0dp" android:layout_height="44dp"
      android:layout_weight="1" android:layout_marginStart="8dp" android:gravity="center"
      android:text="Equalizer" android:textColor="#FFFFFF" android:background="@drawable/action_card" android:clickable="true"/>
    </LinearLayout>
    <TextView android:id="@+id/recentButton" android:layout_width="match_parent" android:layout_height="1dp"
     android:visibility="gone"/>
    <TextView android:id="@+id/addStorageButton" android:layout_width="match_parent" android:layout_height="1dp"
     android:visibility="gone"/>
    <TextView android:id="@+id/searchButton" android:layout_width="match_parent" android:layout_height="1dp"
     android:visibility="gone"/>
   </LinearLayout>
  </LinearLayout>
 </ScrollView>
 <LinearLayout android:id="@+id/nowPlayingCard" android:layout_width="match_parent"
  android:layout_height="58dp" android:orientation="horizontal" android:gravity="center_vertical"
  android:paddingHorizontal="12dp" android:background="#171717" android:visibility="gone">
  <ImageView android:layout_width="38dp" android:layout_height="38dp" android:src="@drawable/ic_mediahub"/>
  <LinearLayout android:id="@+id/nowPlayingOpen" android:layout_width="0dp" android:layout_height="wrap_content"
   android:layout_weight="1" android:orientation="vertical" android:paddingStart="10dp">
   <TextView android:id="@+id/nowPlayingTitle" android:layout_width="match_parent" android:layout_height="wrap_content"
    android:text="Nothing playing" android:textColor="#FFFFFF" android:textSize="12sp" android:maxLines="1"/>
   <TextView android:id="@+id/nowPlayingArtist" android:layout_width="match_parent" android:layout_height="wrap_content"
    android:text="Choose a track" android:textColor="#AAAAAA" android:textSize="10sp"/>
  </LinearLayout>
  <TextView android:id="@+id/nowPlayingPlay" android:layout_width="48dp" android:layout_height="48dp"
   android:gravity="center" android:text="▶" android:textColor="#FFFFFF" android:textSize="21sp"/>
 </LinearLayout>
 <LinearLayout android:layout_width="match_parent" android:layout_height="66dp"
  android:orientation="horizontal" android:gravity="center" android:background="#111111"
  android:paddingStart="8dp" android:paddingEnd="8dp">
  <TextView android:id="@+id/navHome" android:layout_width="0dp" android:layout_height="58dp"
   android:layout_weight="1" android:gravity="center" android:text="⌂\nHome" android:textColor="#2196F3"
   android:textStyle="bold" android:textSize="13sp"/>
  <TextView android:id="@+id/navNew" android:layout_width="0dp" android:layout_height="58dp"
   android:layout_weight="1" android:gravity="center" android:text="▦\nNew" android:textColor="#FFFFFF" android:textSize="13sp"/>
  <TextView android:id="@+id/navRadio" android:layout_width="0dp" android:layout_height="58dp"
   android:layout_weight="1" android:gravity="center" android:text="◉\nRadio" android:textColor="#FFFFFF" android:textSize="13sp"/>
  <TextView android:id="@+id/navLibrary" android:layout_width="0dp" android:layout_height="58dp"
   android:layout_weight="1" android:gravity="center" android:text="▣\nLibrary" android:textColor="#FFFFFF" android:textSize="13sp"/>
  <TextView android:id="@+id/navSearch" android:layout_width="0dp" android:layout_height="58dp"
   android:layout_weight="1" android:gravity="center" android:text="⌕\nSearch" android:textColor="#FFFFFF" android:textSize="13sp"/>
 </LinearLayout>
</LinearLayout>
'''
(layout / "activity_main.xml").write_text(xml, encoding="utf-8")

source = main.read_text(encoding="utf-8")
needle = "requestMediaPermissions(); updateStorageLabel(); refreshNowPlaying(); UpdateManager.checkIfDue(this);"
replacement = needle + "\n        setupReferenceBottomNavigation(); populateReferenceHome();"
if needle not in source:
    raise SystemExit("MainActivity initialization marker not found; refusing partial UI patch.")
source = source.replace(needle, replacement, 1)

methods = r'''
    private void setupReferenceBottomNavigation() {
        findViewById(R.id.navHome).setOnClickListener(v -> {});
        findViewById(R.id.navNew).setOnClickListener(v -> openSaved("recent"));
        findViewById(R.id.navRadio).setOnClickListener(v ->
            new android.app.AlertDialog.Builder(this).setTitle("Radio")
                .setMessage("Online Radio is coming soon. Your local music library is ready to play offline.")
                .setPositiveButton("OK", null).show());
        findViewById(R.id.navLibrary).setOnClickListener(v -> openLibrary(LibraryActivity.KIND_MUSIC));
        findViewById(R.id.navSearch).setOnClickListener(v -> openLibrary(LibraryActivity.KIND_MUSIC));
    }

    private void populateReferenceHome() {
        android.widget.LinearLayout root = findViewById(R.id.homeContent);
        root.removeAllViews();
        addReferenceCarousel(root, "Listen together", MediaRepository.query(this, LibraryActivity.KIND_MUSIC), true);
        addReferenceCarousel(root, "Forgotten favorites", MediaRepository.query(this, LibraryActivity.KIND_VIDEO), false);
        addReferenceLongList(root, "Long listens", MediaRepository.query(this, LibraryActivity.KIND_MUSIC));
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private void addReferenceCarousel(android.widget.LinearLayout root, String heading,
            java.util.List<MediaItem> items, boolean music) {
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText(heading); title.setTextColor(android.graphics.Color.WHITE);
        title.setTextSize(20); title.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams hp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(dp(20), dp(22), dp(12), dp(12)); root.addView(title, hp);
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setPadding(dp(20), 0, dp(20), 0);
        int count = Math.min(items == null ? 0 : items.size(), 12);
        for (int i = 0; i < count; i++) {
            MediaItem item = items.get(i);
            android.widget.LinearLayout card = new android.widget.LinearLayout(this);
            card.setOrientation(android.widget.LinearLayout.VERTICAL);
            card.setFocusable(true); card.setClickable(true);
            android.widget.ImageView art = new android.widget.ImageView(this);
            art.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            art.setBackgroundColor(0xFF202020);
            android.widget.LinearLayout.LayoutParams ap = new android.widget.LinearLayout.LayoutParams(dp(190), dp(190));
            card.addView(art, ap);
            loadReferenceArtwork(item, art, !music);
            android.widget.TextView name = new android.widget.TextView(this);
            name.setText(item.title == null ? "Untitled" : item.title);
            name.setTextColor(android.graphics.Color.WHITE); name.setTextSize(14);
            name.setTypeface(null, android.graphics.Typeface.BOLD); name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            android.widget.LinearLayout.LayoutParams np = new android.widget.LinearLayout.LayoutParams(dp(190), -2);
            np.topMargin = dp(9); card.addView(name, np);
            android.widget.TextView sub = new android.widget.TextView(this);
            sub.setText(item.artist == null || item.artist.trim().isEmpty() ? (music ? "On this device" : "Video") : item.artist);
            sub.setTextColor(0xFF999999); sub.setTextSize(12); sub.setMaxLines(1);
            card.addView(sub, new android.widget.LinearLayout.LayoutParams(dp(190), -2));
            card.setOnClickListener(v -> {
                if (music) { Intent p = new Intent(this, PlayerActivity.class); p.putExtra("uri", item.uri.toString()); startActivity(p); }
                else { Intent p = new Intent(this, VideoPlayerActivity.class); p.setData(item.uri); p.putExtra("title", item.title); startActivity(p); }
            });
            android.widget.LinearLayout.LayoutParams cp = new android.widget.LinearLayout.LayoutParams(dp(190), -2);
            cp.rightMargin = dp(16); row.addView(card, cp);
        }
        if (count == 0) {
            android.widget.TextView empty = new android.widget.TextView(this);
            empty.setText("Your media will appear here when indexed.");
            empty.setTextColor(0xFF999999); empty.setTextSize(13); row.addView(empty);
        }
        scroll.addView(row); root.addView(scroll, new android.widget.LinearLayout.LayoutParams(-1, -2));
    }

    private void addReferenceLongList(android.widget.LinearLayout root, String heading, java.util.List<MediaItem> items) {
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText(heading); title.setTextColor(android.graphics.Color.WHITE);
        title.setTextSize(20); title.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams hp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(dp(20), dp(28), dp(12), dp(12)); root.addView(title, hp);
        int count = Math.min(items == null ? 0 : items.size(), 10);
        for (int i = 0; i < count; i++) {
            MediaItem item = items.get(i);
            android.widget.LinearLayout line = new android.widget.LinearLayout(this);
            line.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            line.setGravity(android.view.Gravity.CENTER_VERTICAL);
            line.setPadding(dp(20), dp(7), dp(20), dp(7));
            android.widget.ImageView art = new android.widget.ImageView(this);
            art.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            art.setBackgroundColor(0xFF242424);
            line.addView(art, new android.widget.LinearLayout.LayoutParams(dp(62), dp(62)));
            loadReferenceArtwork(item, art, false);
            android.widget.LinearLayout textCol = new android.widget.LinearLayout(this);
            textCol.setOrientation(android.widget.LinearLayout.VERTICAL);
            textCol.setPadding(dp(12), 0, dp(8), 0);
            android.widget.TextView name = new android.widget.TextView(this);
            name.setText(item.title == null ? "Untitled" : item.title);
            name.setTextColor(-1); name.setTextSize(14); name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textCol.addView(name);
            android.widget.TextView sub = new android.widget.TextView(this);
            sub.setText(item.artist == null ? "On this device" : item.artist);
            sub.setTextColor(0xFF999999); sub.setTextSize(12); sub.setMaxLines(1);
            textCol.addView(sub);
            line.addView(textCol, new android.widget.LinearLayout.LayoutParams(0, -2, 1));
            android.widget.TextView more = new android.widget.TextView(this);
            more.setText("•••"); more.setTextColor(0xFFAAAAAA); more.setTextSize(18);
            line.addView(more, new android.widget.LinearLayout.LayoutParams(dp(35), dp(48)));
            line.setOnClickListener(v -> { Intent p = new Intent(this, PlayerActivity.class); p.putExtra("uri", item.uri.toString()); startActivity(p); });
            root.addView(line, new android.widget.LinearLayout.LayoutParams(-1, -2));
        }
        if (count == 0) {
            android.widget.TextView empty = new android.widget.TextView(this);
            empty.setText("Play a few tracks and your long listens will appear here.");
            empty.setTextColor(0xFF999999); empty.setTextSize(13);
            android.widget.LinearLayout.LayoutParams ep = new android.widget.LinearLayout.LayoutParams(-1, -2);
            ep.setMargins(dp(20), 0, dp(20), dp(12)); root.addView(empty, ep);
        }
    }

    private void loadReferenceArtwork(MediaItem item, android.widget.ImageView target, boolean video) {
        final String key = item.uri.toString();
        target.setTag(key);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            android.graphics.Bitmap bitmap = null;
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, item.uri);
                if (video) bitmap = retriever.getFrameAtTime(1000000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                else {
                    byte[] picture = retriever.getEmbeddedPicture();
                    if (picture != null) bitmap = android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.length);
                }
            } catch (Exception ignored) {
            } finally { try { retriever.release(); } catch (Exception ignored) {} }
            final android.graphics.Bitmap result = bitmap;
            if (result != null) runOnUiThread(() -> {
                if (key.equals(target.getTag())) target.setImageBitmap(result);
            });
            executor.shutdown();
        });
    }
'''
marker = "    private void requestMediaPermissions()"
if source.count(marker) != 1:
    raise SystemExit("Could not safely locate MainActivity method insertion point.")
source = source.replace(marker, methods + "\n" + marker, 1)
main.write_text(source, encoding="utf-8")
print("Applied screenshot-inspired black home feed, live media carousels, artwork extraction, long-list rows and five-item bottom navigation.")
