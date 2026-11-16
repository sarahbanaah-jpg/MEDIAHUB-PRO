from pathlib import Path

ROOT = Path("app/src/main/res")
layout = ROOT / "layout"
drawable = ROOT / "drawable"
layout.mkdir(parents=True, exist_ok=True)
drawable.mkdir(parents=True, exist_ok=True)

(layout / "activity_main.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:background="#050507">
 <ScrollView android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:fillViewport="true">
  <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical" android:padding="16dp">
   <LinearLayout android:layout_width="match_parent" android:layout_height="48dp" android:gravity="center_vertical" android:orientation="horizontal">
    <TextView android:text="☰" android:textColor="#FFFFFF" android:textSize="23sp" android:gravity="center" android:layout_width="38dp" android:layout_height="match_parent"/>
    <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:gravity="center" android:orientation="horizontal">
     <TextView android:text="MEDIAHUB " android:textColor="#FFFFFF" android:textSize="20sp" android:textStyle="bold" android:layout_width="wrap_content" android:layout_height="wrap_content"/>
     <TextView android:text="PRO" android:textColor="#FF315F" android:textSize="20sp" android:textStyle="bold" android:layout_width="wrap_content" android:layout_height="wrap_content"/>
    </LinearLayout>
    <TextView android:text="♛" android:textColor="#FFC928" android:textSize="25sp" android:gravity="center" android:layout_width="38dp" android:layout_height="match_parent"/>
    <TextView android:id="@+id/settingsButton" android:text="⚙" android:textColor="#FFFFFF" android:textSize="20sp" android:gravity="center" android:background="@drawable/icon_button" android:layout_width="40dp" android:layout_height="40dp" android:clickable="true" android:focusable="true"/>
   </LinearLayout>
   <LinearLayout android:id="@+id/musicButton" android:layout_width="match_parent" android:layout_height="190dp" android:layout_marginTop="18dp" android:padding="20dp" android:gravity="bottom" android:orientation="vertical" android:background="@drawable/feature_card" android:clickable="true" android:focusable="true">
    <TextView android:text="YOUR ULTIMATE\nMEDIA EXPERIENCE" android:textColor="#FFFFFF" android:textSize="24sp" android:textStyle="bold" android:layout_width="230dp" android:layout_height="wrap_content"/>
    <TextView android:text="Play  •  Watch  •  Enjoy" android:textColor="#E8DDE8" android:textSize="13sp" android:layout_marginTop="8dp" android:layout_width="wrap_content" android:layout_height="wrap_content"/>
    <TextView android:text="Explore Now  ▶" android:textColor="#FFFFFF" android:textStyle="bold" android:textSize="13sp" android:gravity="center" android:background="@drawable/pill_button" android:layout_width="130dp" android:layout_height="40dp" android:layout_marginTop="12dp"/>
   </LinearLayout>
   <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical" android:layout_marginTop="22dp">
    <TextView android:text="Quick Access" android:textColor="#FFFFFF" android:textSize="19sp" android:textStyle="bold" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content"/>
    <TextView android:id="@+id/addStorageButton" android:text="+ Add storage" android:textColor="#FF4771" android:textSize="12sp" android:padding="6dp" android:layout_width="wrap_content" android:layout_height="wrap_content" android:clickable="true" android:focusable="true"/>
   </LinearLayout>
   <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:layout_marginTop="12dp">
    <LinearLayout android:layout_width="0dp" android:layout_height="94dp" android:layout_weight="1" android:orientation="vertical" android:gravity="center" android:background="@drawable/browse_card" android:clickable="true" android:onClick="navMusic"><TextView android:text="♫" android:textColor="#FF3475" android:textSize="27sp" android:layout_width="wrap_content" android:layout_height="wrap_content"/><TextView android:text="Music" android:textColor="#FFFFFF" android:textSize="12sp" android:layout_width="wrap_content" android:layout_height="wrap_content"/></LinearLayout>
    <Space android:layout_width="8dp" android:layout_height="1dp"/>
    <LinearLayout android:id="@+id/videoButton" android:layout_width="0dp" android:layout_height="94dp" android:layout_weight="1" android:orientation="vertical" android:gravity="center" android:background="@drawable/browse_card" android:clickable="true" android:focusable="true"><TextView android:text="▶" android:textColor="#FF3475" android:textSize="27sp" android:layout_width="wrap_content" android:layout_height="wrap_content"/><TextView android:text="Video" android:textColor="#FFFFFF" android:textSize="12sp" android:layout_width="wrap_content" android:layout_height="wrap_content"/></LinearLayout>
    <Space android:layout_width="8dp" android:layout_height="1dp"/>
    <LinearLayout android:id="@+id/photosButton" android:layout_width="0dp" android:layout_height="94dp" android:layout_weight="1" android:orientation="vertical" android:gravity="center" android:background="@drawable/browse_card" android:clickable="true" android:focusable="true"><TextView android:text="▧" android:textColor="#B65CFF" android:textSize="27sp" android:layout_width="wrap_content" android:layout_height="wrap_content"/><TextView android:text="Photos" android:textColor="#FFFFFF" android:textSize="12sp" android:layout_width="wrap_content" android:layout_height="wrap_content"/></LinearLayout>
    <Space android:layout_width="8dp" android:layout_height="1dp"/>
    <LinearLayout android:layout_width="0dp" android:layout_height="94dp" android:layout_weight="1" android:orientation="vertical" android:gravity="center" android:background="@drawable/browse_card" android:clickable="true" android:onClick="navPlaylists"><TextView android:text="▰" android:textColor="#FFB33F" android:textSize="27sp" android:layout_width="wrap_content" android:layout_height="wrap_content"/><TextView android:text="Files" android:textColor="#FFFFFF" android:textSize="12sp" android:layout_width="wrap_content" android:layout_height="wrap_content"/></LinearLayout>
   </LinearLayout>
   <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical" android:layout_marginTop="22dp"><TextView android:text="Your Library" android:textColor="#FFFFFF" android:textSize="19sp" android:textStyle="bold" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content"/><TextView android:text="See All  ›" android:textColor="#C7C1CC" android:textSize="12sp" android:layout_width="wrap_content" android:layout_height="wrap_content" android:onClick="navMusic" android:clickable="true"/></LinearLayout>
   <TextView android:id="@+id/summaryText" android:text="Preparing your collection…" android:textColor="#FFFFFF" android:textSize="15sp" android:textStyle="bold" android:layout_marginTop="9dp" android:layout_width="match_parent" android:layout_height="wrap_content"/>
   <TextView android:id="@+id/storageStatus" android:text="Internal storage" android:textColor="#8F8996" android:textSize="11sp" android:layout_marginTop="3dp" android:layout_width="match_parent" android:layout_height="wrap_content"/>
   <TextView android:text="Recently Played" android:textColor="#FFFFFF" android:textSize="19sp" android:textStyle="bold" android:layout_marginTop="24dp" android:layout_width="match_parent" android:layout_height="wrap_content"/>
   <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:layout_marginTop="11dp">
    <TextView android:id="@+id/recentButton" android:text="◷  Recently played\nOpen your listening history" android:textColor="#FFFFFF" android:textSize="12sp" android:gravity="center_vertical" android:padding="13dp" android:layout_width="0dp" android:layout_height="78dp" android:layout_weight="1" android:background="@drawable/action_card" android:clickable="true" android:focusable="true"/>
    <Space android:layout_width="9dp" android:layout_height="1dp"/>
    <TextView android:id="@+id/favouritesButton" android:text="♡  Favourites\nYour saved media" android:textColor="#FFFFFF" android:textSize="12sp" android:gravity="center_vertical" android:padding="13dp" android:layout_width="0dp" android:layout_height="78dp" android:layout_weight="1" android:background="@drawable/action_card" android:clickable="true" android:focusable="true"/>
   </LinearLayout>
   <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:layout_marginTop="9dp">
    <TextView android:id="@+id/playlistsButton" android:text="▤  Playlists" android:textColor="#FFFFFF" android:textSize="13sp" android:gravity="center" android:layout_width="0dp" android:layout_height="50dp" android:layout_weight="1" android:background="@drawable/action_card" android:clickable="true" android:focusable="true"/>
    <Space android:layout_width="9dp" android:layout_height="1dp"/>
    <TextView android:id="@+id/equalizerButton" android:text="≋  Equalizer" android:textColor="#FFFFFF" android:textSize="13sp" android:gravity="center" android:layout_width="0dp" android:layout_height="50dp" android:layout_weight="1" android:background="@drawable/action_card" android:clickable="true" android:focusable="true"/>
   </LinearLayout>
   <TextView android:id="@+id/searchButton" android:text="⌕   Search your library" android:textColor="#FFFFFF" android:textSize="13sp" android:gravity="center_vertical" android:paddingStart="17dp" android:layout_width="match_parent" android:layout_height="48dp" android:layout_marginTop="10dp" android:background="@drawable/search_field" android:clickable="true" android:focusable="true"/>
  </LinearLayout>
 </ScrollView>
 <LinearLayout android:id="@+id/nowPlayingCard" android:layout_width="match_parent" android:layout_height="66dp" android:orientation="horizontal" android:gravity="center_vertical" android:paddingStart="13dp" android:paddingEnd="13dp" android:background="#21131D" android:visibility="gone">
  <ImageView android:layout_width="43dp" android:layout_height="43dp" android:src="@drawable/ic_mediahub" android:scaleType="centerCrop" android:background="@drawable/album_art_frame" android:clipToOutline="true" android:contentDescription="Current track artwork"/>
  <LinearLayout android:id="@+id/nowPlayingOpen" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:paddingStart="11dp" android:clickable="true"><TextView android:id="@+id/nowPlayingTitle" android:text="Nothing playing" android:textColor="#FFFFFF" android:textSize="12sp" android:textStyle="bold" android:maxLines="1" android:ellipsize="end" android:layout_width="match_parent" android:layout_height="wrap_content"/><TextView android:id="@+id/nowPlayingArtist" android:text="Choose a track" android:textColor="#B7AEB9" android:textSize="10sp" android:maxLines="1" android:layout_width="match_parent" android:layout_height="wrap_content"/></LinearLayout>
  <TextView android:id="@+id/nowPlayingPlay" android:text="Ⅱ" android:textColor="#FF4771" android:textSize="21sp" android:gravity="center" android:layout_width="48dp" android:layout_height="52dp" android:clickable="true" android:focusable="true"/>
 </LinearLayout>
 <LinearLayout android:layout_width="match_parent" android:layout_height="58dp" android:orientation="horizontal" android:gravity="center" android:background="#111014">
  <TextView android:text="⌂\nHome" android:textColor="#FF4771" android:textSize="10sp" android:gravity="center" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"/>
  <TextView android:text="♫\nMusic" android:textColor="#A49AA7" android:textSize="10sp" android:gravity="center" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:onClick="navMusic" android:clickable="true"/>
  <TextView android:text="▤\nPlaylists" android:textColor="#A49AA7" android:textSize="10sp" android:gravity="center" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:onClick="navPlaylists" android:clickable="true"/>
  <TextView android:text="▧\nPhotos" android:textColor="#A49AA7" android:textSize="10sp" android:gravity="center" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:onClick="navPhotos" android:clickable="true"/>
  <TextView android:text="•••\nMore" android:textColor="#A49AA7" android:textSize="10sp" android:gravity="center" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:onClick="navSettings" android:clickable="true"/>
 </LinearLayout>
</LinearLayout>
""", encoding="utf-8")

(drawable / "feature_card.xml").write_text('<shape xmlns:android="http://schemas.android.com/apk/res/android"><corners android:radius="24dp"/><gradient android:angle="0" android:startColor="#650B49" android:centerColor="#321044" android:endColor="#100C18"/><stroke android:width="1dp" android:color="#87345F"/></shape>', encoding="utf-8")
(drawable / "browse_card.xml").write_text('<shape xmlns:android="http://schemas.android.com/apk/res/android"><corners android:radius="18dp"/><gradient android:angle="270" android:startColor="#261421" android:endColor="#171319"/><stroke android:width="1dp" android:color="#402334"/></shape>', encoding="utf-8")
(drawable / "action_card.xml").write_text('<shape xmlns:android="http://schemas.android.com/apk/res/android"><corners android:radius="17dp"/><solid android:color="#1B171E"/><stroke android:width="1dp" android:color="#392633"/></shape>', encoding="utf-8")
print("Applied MEDIAHUB PRO premium dashboard theme.")
