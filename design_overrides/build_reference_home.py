from pathlib import Path
import sys

project = Path(sys.argv[1])
main = project / "app/src/main/java/com/mediahub/rebuilt/MainActivity.java"
source = main.read_text(encoding="utf-8")

old = "requestMediaPermissions(); updateStorageLabel(); refreshNowPlaying(); UpdateManager.checkIfDue(this);"
if old not in source:
    raise SystemExit("Expected MainActivity initialization line not found.")
source = source.replace(old, old + "\n        buildVideoReferenceShell();", 1)

methods = r'''
    private android.widget.FrameLayout videoShell;
    private android.widget.LinearLayout videoPage;
    private String videoCurrentTab = "Home";
    private final java.util.concurrent.ExecutorService artworkExecutor =
        java.util.concurrent.Executors.newFixedThreadPool(3);

    private int uiDp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private android.graphics.drawable.GradientDrawable uiShape(int color, int stroke, float radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(uiDp(radius));
        if (stroke != 0) d.setStroke(uiDp(1), stroke);
        return d;
    }
    private android.widget.TextView uiText(String text, float size, int color, boolean bold) {
        android.widget.TextView v = new android.widget.TextView(this);
        v.setText(text); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return v;
    }
    private android.widget.LinearLayout uiColumn() {
        android.widget.LinearLayout l = new android.widget.LinearLayout(this);
        l.setOrientation(android.widget.LinearLayout.VERTICAL); return l;
    }
    private android.widget.LinearLayout uiRow() {
        android.widget.LinearLayout l = new android.widget.LinearLayout(this);
        l.setOrientation(android.widget.LinearLayout.HORIZONTAL); l.setGravity(android.view.Gravity.CENTER_VERTICAL); return l;
    }
    private void buildVideoReferenceShell() {
        videoShell = new android.widget.FrameLayout(this);
        videoShell.setBackgroundColor(android.graphics.Color.BLACK);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(false); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        videoPage = uiColumn();
        videoPage.setPadding(0, uiDp(12), 0, uiDp(132));
        scroll.addView(videoPage, new android.widget.ScrollView.LayoutParams(-1, -2));
        videoShell.addView(scroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.LinearLayout glass = uiRow();
        glass.setGravity(android.view.Gravity.CENTER);
        glass.setPadding(uiDp(5), uiDp(4), uiDp(5), uiDp(4));
        glass.setBackground(uiShape(0xD91E1E23, 0x887F7F86, 48));
        glass.setElevation(uiDp(18));
        android.view.ViewGroup.LayoutParams navParams = new android.widget.FrameLayout.LayoutParams(-1, uiDp(66),
            android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        ((android.widget.FrameLayout.LayoutParams)navParams).setMargins(uiDp(22), 0, uiDp(22), uiDp(17));
        String[] labels = {"Home", "New", "Radio", "Library", "Search"};
        String[] icons = {"⌂", "▦", "◉", "▣", "⌕"};
        for (int i=0;i<labels.length;i++) {
            final String tab = labels[i];
            android.widget.LinearLayout item = uiColumn();
            item.setGravity(android.view.Gravity.CENTER);
            android.widget.TextView icon = uiText(icons[i], 26, -1, true);
            icon.setGravity(android.view.Gravity.CENTER);
            android.widget.TextView label = uiText(labels[i], 10, -1, false);
            label.setGravity(android.view.Gravity.CENTER);
            item.addView(icon, new android.widget.LinearLayout.LayoutParams(-1, uiDp(32)));
            item.addView(label, new android.widget.LinearLayout.LayoutParams(-1, uiDp(18)));
            item.setOnClickListener(v -> showVideoTab(tab));
            glass.addView(item, new android.widget.LinearLayout.LayoutParams(0, -1, 1));
        }
        videoShell.addView(glass, navParams);
        setContentView(videoShell);
        showVideoTab("Home");
    }

    private void showVideoTab(String tab) {
        videoCurrentTab = tab;
        videoPage.removeAllViews();
        switch(tab) {
            case "Home": renderVideoHome(); break;
            case "New": renderVideoNew(); break;
            case "Radio": renderVideoRadio(); break;
            case "Library": renderVideoLibrary(); break;
            case "Search": renderVideoSearch(); break;
        }
    }
    private void pageHeader(String title) {
        android.widget.LinearLayout row = uiRow(); row.setPadding(uiDp(22), uiDp(8), uiDp(18), uiDp(13));
        android.widget.TextView heading = uiText(title, 30, -1, true);
        row.addView(heading, new android.widget.LinearLayout.LayoutParams(0, uiDp(58), 1));
        android.widget.TextView more = uiText("•••", 22, 0xFFFFFFFF, true);
        more.setGravity(android.view.Gravity.CENTER);
        more.setBackground(uiShape(0xFF242428, 0, 40));
        row.addView(more, new android.widget.LinearLayout.LayoutParams(uiDp(48), uiDp(48)));
        more.setOnClickListener(v -> showVideoMenu(v));
        videoPage.addView(row);
    }
    private void sectionTitle(String title) {
        android.widget.TextView t = uiText(title, 20, -1, true);
        android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(-1, -2);
        p.setMargins(uiDp(22), uiDp(24), uiDp(16), uiDp(12)); videoPage.addView(t,p);
    }
    private void renderVideoHome() {
        pageHeader("Home");
        android.widget.LinearLayout chips = uiRow(); chips.setPadding(uiDp(22),0,0,0);
        String[] names={"All","For you","Artists"};
        for(int i=0;i<names.length;i++){
            android.widget.TextView c=uiText(names[i],13,i==0?0xFF000000:-1,true);
            c.setGravity(android.view.Gravity.CENTER); c.setPadding(uiDp(18),0,uiDp(18),0);
            c.setBackground(uiShape(i==0?-1:0xFF28282D,0,30));
            android.widget.LinearLayout.LayoutParams cp=new android.widget.LinearLayout.LayoutParams(-2,uiDp(38)); cp.rightMargin=uiDp(9); chips.addView(c,cp);
            c.setOnClickListener(v->{ for(int j=0;j<chips.getChildCount();j++){android.view.View q=chips.getChildAt(j);q.setBackground(uiShape(q==c?-1:0xFF28282D,0,30));((android.widget.TextView)q).setTextColor(q==c?0xFF000000:-1);} });
        }
        videoPage.addView(chips);
        sectionTitle("Made for you");
        android.widget.HorizontalScrollView madeScroll=new android.widget.HorizontalScrollView(this); madeScroll.setHorizontalScrollBarEnabled(false);
        android.widget.LinearLayout made=uiRow(); made.setPadding(uiDp(22),0,uiDp(18),0);
        addGradientFeature(made,"Your mix","Built from what you love",0xFF9C65E8,0xFFF15DAD,"♫");
        addGradientFeature(made,"Pop Radio","Pop",0xFFF04D88,0xFFFF9A54,"◉");
        madeScroll.addView(made); videoPage.addView(madeScroll);
        java.util.List<MediaItem> music=MediaRepository.query(this,LibraryActivity.KIND_MUSIC);
        java.util.List<MediaItem> videos=MediaRepository.query(this,LibraryActivity.KIND_VIDEO);
        sectionTitle("Recommended stations  ›");
        android.widget.HorizontalScrollView stationsScroll=new android.widget.HorizontalScrollView(this); stationsScroll.setHorizontalScrollBarEnabled(false);
        android.widget.LinearLayout stations=uiRow(); stations.setPadding(uiDp(22),0,uiDp(18),0);
        addStation(stations,"Pop Smoke",0xFFFFE18B,music,0);
        addStation(stations,"Eminem",0xFFD5C1FF,music,1);
        stationsScroll.addView(stations); videoPage.addView(stationsScroll);
        sectionTitle("Quick picks");
        addQuickPicks(music);
        addMediaCarousel("Listen again",music,false);
        addMediaCarousel("Listen together",music,false);
        addMediaCarousel("Music videos for you",videos,true);
        sectionTitle("Covers and remixes");
        addMediaRows(music,4);
        addMediaCarousel("Albums for you",music,false);
        addMediaCarousel("From the community",videos,true);
        if(music.isEmpty() && videos.isEmpty()) emptyHint("Allow media access or add music and videos to see your personal feed here.");
    }
    private void addGradientFeature(android.widget.LinearLayout parent,String title,String subtitle,int a,int b,String glyph){
        android.widget.LinearLayout card=uiColumn(); card.setPadding(uiDp(18),uiDp(16),uiDp(16),uiDp(14));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{a,b}); bg.setCornerRadius(uiDp(22)); card.setBackground(bg);
        android.widget.TextView symbol=uiText(glyph,48,-1,true); symbol.setGravity(android.view.Gravity.CENTER);
        android.widget.TextView name=uiText(title,19,-1,true); android.widget.TextView sub=uiText(subtitle,12,0xFFECECEC,false);
        card.addView(symbol,new android.widget.LinearLayout.LayoutParams(-1,0,1)); card.addView(name); card.addView(sub);
        android.widget.LinearLayout.LayoutParams p=new android.widget.LinearLayout.LayoutParams(uiDp(190),uiDp(200)); p.rightMargin=uiDp(12); parent.addView(card,p);
        card.setOnClickListener(v->openLibrary(LibraryActivity.KIND_MUSIC));
    }
    private void addStation(android.widget.LinearLayout parent,String name,int color,java.util.List<MediaItem> music,int index){
        android.widget.LinearLayout card=uiColumn(); card.setGravity(android.view.Gravity.CENTER); card.setPadding(uiDp(12),uiDp(12),uiDp(12),uiDp(14)); card.setBackground(uiShape(color,0,24));
        android.widget.TextView tag=uiText("◉  RADIO",10,0xFF34313A,true); tag.setGravity(android.view.Gravity.CENTER);
        android.widget.TextView face=uiText(index==0?"●":"◉",58,0xFF33313B,true); face.setGravity(android.view.Gravity.CENTER);
        android.widget.TextView label=uiText(name,17,0xFF25232B,true); label.setGravity(android.view.Gravity.CENTER);
        card.addView(tag,new android.widget.LinearLayout.LayoutParams(-1,uiDp(26))); card.addView(face,new android.widget.LinearLayout.LayoutParams(-1,0,1)); card.addView(label);
        android.widget.LinearLayout.LayoutParams p=new android.widget.LinearLayout.LayoutParams(uiDp(174),uiDp(220)); p.rightMargin=uiDp(12); parent.addView(card,p);
        card.setOnClickListener(v->renderVideoRadio());
    }
    private void addQuickPicks(java.util.List<MediaItem> items){
        int count=Math.min(items.size(),8);
        for(int i=0;i<count;i+=2){
            android.widget.LinearLayout row=uiRow(); row.setPadding(uiDp(22),uiDp(4),uiDp(12),uiDp(4));
            for(int j=i;j<Math.min(i+2,count);j++){
                MediaItem item=items.get(j); android.widget.LinearLayout cell=uiRow();
                android.widget.ImageView art=new android.widget.ImageView(this); art.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP); art.setBackground(uiShape(0xFF25252B,0,9)); loadUiArtwork(item,art,false);
                cell.addView(art,new android.widget.LinearLayout.LayoutParams(uiDp(48),uiDp(48)));
                android.widget.TextView title=uiText(item.title,12,-1,false); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setPadding(uiDp(8),0,uiDp(4),0);
                cell.addView(title,new android.widget.LinearLayout.LayoutParams(0,uiDp(50),1));
                android.widget.LinearLayout.LayoutParams p=new android.widget.LinearLayout.LayoutParams(0,uiDp(58),1); p.rightMargin=uiDp(8); row.addView(cell,p);
                cell.setOnClickListener(v->playUiItem(item,false));
            }
            videoPage.addView(row);
        }
    }
    private void addMediaCarousel(String heading,java.util.List<MediaItem> items,boolean video){
        sectionTitle(heading);
        android.widget.HorizontalScrollView sc=new android.widget.HorizontalScrollView(this); sc.setHorizontalScrollBarEnabled(false);
        android.widget.LinearLayout row=uiRow(); row.setPadding(uiDp(22),0,uiDp(18),0);
        int count=Math.min(items.size(),12);
        for(int i=0;i<count;i++){
            MediaItem item=items.get(i); android.widget.LinearLayout card=uiColumn();
            android.widget.ImageView art=new android.widget.ImageView(this); art.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP); art.setBackground(uiShape(0xFF24242A,0,16)); loadUiArtwork(item,art,video);
            card.addView(art,new android.widget.LinearLayout.LayoutParams(uiDp(170),uiDp(170)));
            android.widget.TextView title=uiText(item.title,13,-1,true); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            android.widget.TextView sub=uiText(item.artist==null||item.artist.isEmpty()?(video?"Video":"On this device"):item.artist,11,0xFF9B9BA2,false); sub.setMaxLines(1);
            android.widget.LinearLayout.LayoutParams tp=new android.widget.LinearLayout.LayoutParams(uiDp(170),-2); tp.topMargin=uiDp(7); card.addView(title,tp); card.addView(sub);
            android.widget.LinearLayout.LayoutParams cp=new android.widget.LinearLayout.LayoutParams(uiDp(170),-2); cp.rightMargin=uiDp(13); row.addView(card,cp);
            card.setOnClickListener(v->playUiItem(item,video));
        }
        if(count==0){ android.widget.TextView e=uiText("Your personal recommendations will appear here as your library grows.",12,0xFF999999,false); e.setPadding(uiDp(22),0,0,uiDp(8)); row.addView(e); }
        sc.addView(row); videoPage.addView(sc);
    }
    private void addMediaRows(java.util.List<MediaItem> items,int max){
        int count=Math.min(items.size(),max);
        for(int i=0;i<count;i++){
            MediaItem item=items.get(i); android.widget.LinearLayout row=uiRow(); row.setPadding(uiDp(22),uiDp(7),uiDp(22),uiDp(7));
            android.widget.ImageView art=new android.widget.ImageView(this); art.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP); art.setBackground(uiShape(0xFF24242A,0,9)); loadUiArtwork(item,art,false);
            row.addView(art,new android.widget.LinearLayout.LayoutParams(uiDp(58),uiDp(58)));
            android.widget.LinearLayout col=uiColumn(); col.setPadding(uiDp(12),0,uiDp(8),0);
            android.widget.TextView title=uiText(item.title,14,-1,false); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            android.widget.TextView sub=uiText(item.artist==null||item.artist.isEmpty()?"On this device":item.artist,12,0xFF99999F,false); sub.setMaxLines(1);
            col.addView(title); col.addView(sub); row.addView(col,new android.widget.LinearLayout.LayoutParams(0,-2,1));
            android.widget.TextView more=uiText("•••",18,0xFF999999,true); row.addView(more,new android.widget.LinearLayout.LayoutParams(uiDp(32),-2));
            videoPage.addView(row); row.setOnClickListener(v->playUiItem(item,false));
        }
    }
    private void loadUiArtwork(MediaItem item,android.widget.ImageView target,boolean video){
        final String key=item.uri.toString(); target.setTag(key);
        artworkExecutor.execute(()->{
            android.graphics.Bitmap bmp=null; android.media.MediaMetadataRetriever r=new android.media.MediaMetadataRetriever();
            try{r.setDataSource(this,item.uri); if(video) bmp=r.getFrameAtTime(1000000L,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC); else {byte[] b=r.getEmbeddedPicture(); if(b!=null)bmp=android.graphics.BitmapFactory.decodeByteArray(b,0,b.length);}}
            catch(Exception ignored){} finally{try{r.release();}catch(Exception ignored){}}
            final android.graphics.Bitmap image=bmp; if(image!=null)runOnUiThread(()->{if(key.equals(target.getTag()))target.setImageBitmap(image);});
        });
    }
    private void playUiItem(MediaItem item,boolean video){
        if(video){Intent p=new Intent(this,VideoPlayerActivity.class);p.setData(item.uri);p.putExtra("title",item.title);startActivity(p);}
        else {Intent p=new Intent(this,PlayerActivity.class);p.putExtra("uri",item.uri.toString());startActivity(p);}
    }
    private void emptyHint(String text){android.widget.TextView t=uiText(text,13,0xFF999999,false);t.setPadding(uiDp(22),uiDp(16),uiDp(22),uiDp(28));videoPage.addView(t);}
    private void renderVideoNew(){pageHeader("New"); sectionTitle("Recently added"); addMediaCarousel("New music",MediaRepository.query(this,0),false); addMediaCarousel("New videos",MediaRepository.query(this,1),true); addMediaCarousel("New photos",MediaRepository.query(this,2),true);}
    private void renderVideoRadio(){pageHeader("Radio"); sectionTitle("Your stations"); emptyHint("Online radio stations are coming soon. Your downloaded music and local library remain available offline."); addMediaCarousel("Play your music instead",MediaRepository.query(this,0),false);}
    private void renderVideoLibrary(){
        pageHeader("Library");
        android.widget.LinearLayout tiles=uiRow(); tiles.setPadding(uiDp(18),uiDp(5),uiDp(18),0);
        addLibraryTile(tiles,"♥","Liked songs","favourites",0xFFAD55CF,0xFFE45FA8);
        addLibraryTile(tiles,"↓","Downloads","downloads",0xFF2BA6D6,0xFF55D1B6);
        videoPage.addView(tiles);
        android.widget.LinearLayout history=uiRow(); history.setPadding(uiDp(22),uiDp(20),uiDp(22),uiDp(12));
        android.widget.TextView clock=uiText("◷",28,0xFF2196F3,true); clock.setGravity(android.view.Gravity.CENTER); clock.setBackground(uiShape(0xFF28282D,0,12)); history.addView(clock,new android.widget.LinearLayout.LayoutParams(uiDp(58),uiDp(58)));
        android.widget.TextView h=uiText("History                                      ›",16,-1,false); h.setPadding(uiDp(14),0,0,0); history.addView(h,new android.widget.LinearLayout.LayoutParams(0,-1,1)); history.setOnClickListener(v->openSaved("recent")); videoPage.addView(history);
        sectionTitle("Playlists"); addLibraryAction("＋","New playlist",()->startActivity(new Intent(this,PlaylistsActivity.class))); addLibraryAction("☷","Import a playlist",()->startActivity(new Intent(this,PlaylistsActivity.class)));
        sectionTitle("Your media"); addLibraryAction("♫","Music",()->openLibrary(0)); addLibraryAction("▶","Videos",()->openLibrary(1)); addLibraryAction("▧","Photos",()->openLibrary(2));
        sectionTitle("Tools"); addLibraryAction("⇄","Offline sharing",()->startActivity(new Intent(this,SharingActivity.class))); addLibraryAction("◉","Equalizer",()->startActivity(new Intent(this,EqualizerActivity.class)));
    }
    private void addLibraryTile(android.widget.LinearLayout row,String icon,String title,String key,int a,int b){
        android.widget.LinearLayout tile=uiColumn(); tile.setPadding(uiDp(16),uiDp(13),uiDp(12),uiDp(13));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{a,b});bg.setCornerRadius(uiDp(18));tile.setBackground(bg);
        tile.addView(uiText(icon,27,-1,true));tile.addView(uiText(title,16,-1,true));tile.addView(uiText(key.equals("favourites")?"Your saved tracks":"Available offline",11,0xFFEDEDED,false));
        android.widget.LinearLayout.LayoutParams p=new android.widget.LinearLayout.LayoutParams(0,uiDp(124),1);p.setMargins(uiDp(4),0,uiDp(5),0);row.addView(tile,p);tile.setOnClickListener(v->openSaved(key));
    }
    private void addLibraryAction(String icon,String title,Runnable action){
        android.widget.LinearLayout row=uiRow();row.setPadding(uiDp(22),uiDp(8),uiDp(22),uiDp(8));
        android.widget.TextView ic=uiText(icon,27,0xFF2196F3,true);ic.setGravity(android.view.Gravity.CENTER);ic.setBackground(uiShape(0xFF29292E,0,12));row.addView(ic,new android.widget.LinearLayout.LayoutParams(uiDp(58),uiDp(58)));
        android.widget.TextView label=uiText(title+"                                      ›",15,-1,false);label.setPadding(uiDp(14),0,0,0);row.addView(label,new android.widget.LinearLayout.LayoutParams(0,-1,1));row.setOnClickListener(v->action.run());videoPage.addView(row);
    }
    private void renderVideoSearch(){
        pageHeader("Search");
        android.widget.EditText input=new android.widget.EditText(this);input.setSingleLine(true);input.setTextColor(-1);input.setHintTextColor(0xFF8B8B91);input.setHint("Songs, artists, albums, playlists…");input.setPadding(uiDp(18),0,uiDp(18),0);input.setBackground(uiShape(0xFF222227,0,28));
        android.widget.LinearLayout.LayoutParams ip=new android.widget.LinearLayout.LayoutParams(-1,uiDp(54));ip.setMargins(uiDp(20),uiDp(6),uiDp(20),uiDp(18));videoPage.addView(input,ip);
        android.widget.LinearLayout results=uiColumn();videoPage.addView(results);
        sectionTitle("Recent searches");
        String[] recent={"Recently played","Artists","Albums","Playlists"};
        for(String q:recent){android.widget.TextView t=uiText("◷     "+q,15,0xFFEAEAEA,false);t.setPadding(uiDp(24),uiDp(12),0,uiDp(12));videoPage.addView(t);}
        input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){results.removeAllViews();String q=s.toString().toLowerCase();if(q.isEmpty())return;java.util.List<MediaItem> all=new java.util.ArrayList<>();all.addAll(MediaRepository.query(MainActivity.this,0));all.addAll(MediaRepository.query(MainActivity.this,1));for(MediaItem item:all){if(item.title.toLowerCase().contains(q)||(item.artist!=null&&item.artist.toLowerCase().contains(q))){android.widget.TextView hit=uiText("♫     "+item.title+"   ·   "+(item.artist==null?"":item.artist),14,-1,false);hit.setPadding(uiDp(22),uiDp(10),uiDp(16),uiDp(10));results.addView(hit);hit.setOnClickListener(v->playUiItem(item,item.mime!=null&&item.mime.startsWith("video/")));}}}public void afterTextChanged(android.text.Editable e){}});
    }
    private void showVideoMenu(android.view.View anchor){
        android.widget.PopupMenu m=new android.widget.PopupMenu(this,anchor);
        for(String s:new String[]{"Account","Settings","Check for updates","Refresh media","Sharing","Equalizer","About MEDIAHUB-PRO"})m.getMenu().add(s);
        m.setOnMenuItemClickListener(item->{String s=item.getTitle().toString();if(s.equals("Account")){new android.app.AlertDialog.Builder(this).setTitle("Account").setMessage("MEDIAHUB-PRO\nAll your media, in one place.\nAll rights reserved by Grimy.").setPositiveButton("OK",null).show();}
            else if(s.equals("Settings"))showSettings();else if(s.equals("Check for updates"))UpdateManager.check(this);else if(s.equals("Refresh media")){showVideoTab(videoCurrentTab);Toast.makeText(this,"Media refreshed.",Toast.LENGTH_SHORT).show();}else if(s.equals("Sharing"))startActivity(new Intent(this,SharingActivity.class));else if(s.equals("Equalizer"))startActivity(new Intent(this,EqualizerActivity.class));else new android.app.AlertDialog.Builder(this).setTitle("MEDIAHUB-PRO").setMessage("All rights reserved by Grimy.").setPositiveButton("OK",null).show();return true;});
        m.show();
    }
'''
marker = "    private void requestMediaPermissions()"
if source.count(marker) != 1:
    raise SystemExit("MainActivity insertion point changed; refusing to patch.")
source = source.replace(marker, methods + "\n" + marker, 1)
main.write_text(source, encoding="utf-8")
print("Applied full video-inspired multi-screen feed, functional local media sections, search, library, new tab, menu, and translucent liquid-glass bottom navigation.")
