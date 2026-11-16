from pathlib import Path
p=Path("app/src/main/res/layout")
(p/"activity_player.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent" android:background="#050507" android:fillViewport="true">
 <LinearLayout android:orientation="vertical" android:gravity="center_horizontal" android:padding="24dp" android:layout_width="match_parent" android:layout_height="wrap_content">
  <TextView android:text="NOW PLAYING" android:textColor="#FF4771" android:textSize="11sp" android:textStyle="bold" android:letterSpacing="0.14" android:layout_width="wrap_content" android:layout_height="wrap_content"/>
  <ImageView android:layout_width="match_parent" android:layout_height="300dp" android:src="@drawable/ic_mediahub" android:scaleType="centerCrop" android:background="@drawable/album_art_frame" android:clipToOutline="true" android:layout_marginTop="24dp" android:contentDescription="Current album art"/>
  <TextView android:id="@+id/playerTitle" android:text="Nothing playing" android:textColor="#FFFFFF" android:textSize="25sp" android:textStyle="bold" android:gravity="center" android:layout_marginTop="23dp" android:layout_width="match_parent" android:layout_height="wrap_content"/>
  <TextView android:id="@+id/playerArtist" android:text="Choose a track from your library" android:textColor="#B8B0BC" android:textSize="14sp" android:layout_marginTop="6dp" android:layout_width="match_parent" android:gravity="center" android:layout_height="wrap_content"/>
  <TextView android:text="◈  Dolby Atmos  ·  Where supported" android:textColor="#F7B3C7" android:textSize="11sp" android:background="@drawable/action_card" android:padding="9dp" android:layout_marginTop="14dp" android:layout_width="wrap_content" android:layout_height="wrap_content"/>
  <SeekBar android:id="@+id/playerSeek" android:progressTint="#FF3475" android:thumbTint="#FF4771" android:layout_marginTop="20dp" android:layout_width="match_parent" android:layout_height="wrap_content"/>
  <LinearLayout android:orientation="horizontal" android:gravity="center" android:layout_width="match_parent" android:layout_height="wrap_content"><TextView android:id="@+id/playerElapsed" android:text="0:00" android:textColor="#AAA2AE" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content"/><TextView android:id="@+id/playerDuration" android:text="0:00" android:textColor="#AAA2AE" android:gravity="right" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content"/></LinearLayout>
  <LinearLayout android:orientation="horizontal" android:gravity="center" android:layout_marginTop="26dp" android:layout_width="match_parent" android:layout_height="wrap_content">
   <Button android:id="@+id/playerShuffle" android:text="⇄" android:textSize="18sp" android:textColor="#FFFFFF" android:backgroundTint="#242027" android:layout_width="48dp" android:layout_height="52dp"/>
   <Button android:id="@+id/playerPrevious" android:text="|◀" android:textColor="#FFFFFF" android:backgroundTint="#242027" android:layout_width="55dp" android:layout_height="52dp"/>
   <Button android:id="@+id/playerToggle" android:text="▶" android:textSize="20sp" android:textColor="#FFFFFF" android:backgroundTint="#F52E67" android:layout_width="68dp" android:layout_height="68dp"/>
   <Button android:id="@+id/playerNext" android:text="▶|" android:textColor="#FFFFFF" android:backgroundTint="#242027" android:layout_width="55dp" android:layout_height="52dp"/>
   <Button android:id="@+id/playerRepeat" android:text="↻" android:textSize="18sp" android:textColor="#FFFFFF" android:backgroundTint="#242027" android:layout_width="48dp" android:layout_height="52dp"/>
  </LinearLayout>
  <Button android:id="@+id/playerQueue" android:text="♫   LYRICS &amp; UP NEXT" android:layout_marginTop="20dp" android:backgroundTint="#211B25" android:textColor="#FFFFFF" android:layout_width="match_parent" android:layout_height="50dp"/>
 </LinearLayout>
</ScrollView>
""",encoding="utf-8")
(p/"activity_video.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:orientation="vertical" android:background="#000000" android:layout_width="match_parent" android:layout_height="match_parent">
 <LinearLayout android:orientation="horizontal" android:gravity="center_vertical" android:padding="14dp" android:background="#09090D" android:layout_width="match_parent" android:layout_height="52dp"><TextView android:text="‹" android:textColor="#FFFFFF" android:textSize="28sp" android:layout_width="40dp" android:layout_height="wrap_content"/><TextView android:text="MEDIAHUB PRO  •  VIDEO" android:textColor="#FFFFFF" android:textStyle="bold" android:textSize="13sp" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content"/><TextView android:text="⛶" android:textColor="#FFFFFF" android:textSize="20sp" android:layout_width="32dp" android:layout_height="wrap_content"/></LinearLayout>
 <VideoView android:id="@+id/videoView" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"/>
 <LinearLayout android:orientation="horizontal" android:gravity="center" android:padding="10dp" android:background="#111014" android:layout_width="match_parent" android:layout_height="wrap_content">
  <Button android:id="@+id/rewindButton" android:text="−10s" android:textColor="#FFFFFF" android:backgroundTint="#29212B" android:layout_width="wrap_content" android:layout_height="wrap_content"/>
  <Button android:id="@+id/playButton" android:text="▶ / Ⅱ" android:textColor="#FFFFFF" android:backgroundTint="#F52E67" android:layout_width="wrap_content" android:layout_height="wrap_content"/>
  <Button android:id="@+id/forwardButton" android:text="+10s" android:textColor="#FFFFFF" android:backgroundTint="#29212B" android:layout_width="wrap_content" android:layout_height="wrap_content"/>
 </LinearLayout>
</LinearLayout>
""",encoding="utf-8")
print("Applied premium music and video player surfaces.")
