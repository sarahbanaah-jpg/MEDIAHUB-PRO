package com.mediahub.rebuilt.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

/** Lightweight offline IME. GRIMY BOARD does not collect or transmit typed text. */
class GrimyBoardService : InputMethodService() {
    private var caps=false; private var symbols=false; private var mode=0
    private var root:LinearLayout?=null
    private val clipboardItems= mutableListOf<String>()
    private val emojis=listOf("😀","😂","🥹","😍","🥰","😘","😎","😭","😡","🤔","🤫","🤗","🫶","❤️","🔥","✨","💯","🙏","💀","👀","💔","💕","🎉","👍","👏","🥳","😴","🤝","💪","🌹","🖤","😈","😊","😉","🙌","💖","⭐","🌈","🍀","🎧","🎵","💎","🦋","🌸","🚀")
    private val prefs by lazy { getSharedPreferences("grimy_board",Context.MODE_PRIVATE) }
    private val theme get()=prefs.getString("theme","pink")?:"pink"
    private val accent get()=when(theme){"neon"->0xFF00E6BE.toInt();"glass"->0xFFBE96FF.toInt();else->0xFFFF3789.toInt()}
    private val bg get()=if(theme=="amoled") Color.BLACK else if(theme=="neon") 0xFF081216.toInt() else 0xFF0D0C13.toInt()
    private val height get()=prefs.getInt("height",48).coerceIn(40,58)
    override fun onCreateInputView():View { root=LinearLayout(this).apply{orientation=1;setPadding(dp(5),dp(7),dp(5),dp(8));setBackgroundColor(bg)};render();return root!! }
    override fun onStartInputView(info:EditorInfo?,restarting:Boolean){super.onStartInputView(info,restarting);mode=0;caps=false;render()}
    private fun render(){
        val p=root?:return;p.removeAllViews();p.setBackgroundColor(bg)
        val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        head.addView(label("GRIMY BOARD",12f,accent,true),LinearLayout.LayoutParams(0,dp(34),1f))
        head.addView(key("▤"){mode=if(mode==2)0 else 2;render()},LinearLayout.LayoutParams(dp(42),dp(34)))
        head.addView(key("☺"){mode=if(mode==1)0 else 1;render()},LinearLayout.LayoutParams(dp(42),dp(34)))
        head.addView(key("⚙"){startActivity(Intent(this,GrimyBoardSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))},LinearLayout.LayoutParams(dp(42),dp(34)))
        p.addView(head)
        if(mode==1){emojis.chunked(8).forEach{row->val r=LinearLayout(this).apply{gravity=Gravity.CENTER};row.forEach{e->r.addView(key(e){commit(e)},LinearLayout.LayoutParams(0,dp(43),1f).apply{setMargins(2,2,2,2)})};p.addView(r)};p.addView(key("ABC"){mode=0;render()},LinearLayout.LayoutParams(-1,dp(36)));return}
        if(mode==2){p.addView(label("Clipboard is temporary and stays in memory only.",11f,accent,true));if(clipboardItems.isEmpty())p.addView(label("Use Read clipboard to load copied text.",12f,Color.LTGRAY,false));clipboardItems.forEach{v->p.addView(key(v.take(55)){commit(v);mode=0;render()},LinearLayout.LayoutParams(-1,dp(40)).apply{setMargins(0,2,0,2)})};val r=LinearLayout(this);r.addView(key("Read clipboard"){readClipboard()},LinearLayout.LayoutParams(0,dp(38),1f));r.addView(key("Clear"){clipboardItems.clear();render()},LinearLayout.LayoutParams(0,dp(38),1f));r.addView(key("ABC"){mode=0;render()},LinearLayout.LayoutParams(0,dp(38),1f));p.addView(r);return}
        if(prefs.getBoolean("numbers",true))addRow(p,"1234567890",height-4)
        val rows=if(symbols)listOf("@#$%&-*+()","!?:;/=\\[]","<>~^\\|{}") else listOf("qwertyuiop","asdfghjkl","zxcvbnm")
        rows.forEachIndexed{i,s->val r=LinearLayout(this).apply{gravity=Gravity.CENTER};if(i==2)r.addView(key(if(symbols)"=\\<" else "⇧"){if(symbols)symbols=false else caps=!caps;render()},LinearLayout.LayoutParams(dp(42),dp(height)).apply{setMargins(2,2,2,2)});s.forEach{ch->val v=if(caps&&!symbols)ch.uppercase() else ch.toString();r.addView(key(v){commit(v);if(caps){caps=false;render()}},LinearLayout.LayoutParams(0,dp(height),1f).apply{setMargins(2,2,2,2)})};if(i==2)r.addView(key("⌫"){currentInputConnection?.deleteSurroundingText(1,0)},LinearLayout.LayoutParams(dp(42),dp(height)).apply{setMargins(2,2,2,2)});p.addView(r)}
        val bottom=LinearLayout(this).apply{gravity=Gravity.CENTER}
        bottom.addView(key(if(symbols)"ABC" else "?123"){symbols=!symbols;render()},LinearLayout.LayoutParams(dp(55),dp(44)))
        bottom.addView(key(","){commit(",")},LinearLayout.LayoutParams(dp(34),dp(44)))
        bottom.addView(key("English · GRIMY"){commit(" ")},LinearLayout.LayoutParams(0,dp(44),1f).apply{setMargins(3,0,3,0)})
        bottom.addView(key("."){commit(".")},LinearLayout.LayoutParams(dp(34),dp(44)))
        bottom.addView(key("↵"){val a=currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)?:EditorInfo.IME_ACTION_NONE;if(a==EditorInfo.IME_ACTION_NONE||a==EditorInfo.IME_ACTION_UNSPECIFIED)currentInputConnection?.commitText("\n",1) else currentInputConnection?.performEditorAction(a)},LinearLayout.LayoutParams(dp(44),dp(44)))
        p.addView(bottom)
    }
    private fun addRow(p:LinearLayout,s:String,h:Int){val r=LinearLayout(this).apply{gravity=Gravity.CENTER};s.forEach{c->r.addView(key(c.toString()){commit(c.toString())},LinearLayout.LayoutParams(0,dp(h),1f).apply{setMargins(2,1,2,1)})};p.addView(r)}
    private fun readClipboard(){val cm=getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;val v=cm.primaryClip?.takeIf{it.itemCount>0}?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty();if(v.isNotEmpty()&&!clipboardItems.contains(v)){clipboardItems.add(0,v);while(clipboardItems.size>8)clipboardItems.removeAt(clipboardItems.lastIndex)};render()}
    private fun commit(v:String){currentInputConnection?.commitText(v,1);if(prefs.getBoolean("haptics",true))try{val vib=if(android.os.Build.VERSION.SDK_INT>=31)getSystemService(VibratorManager::class.java).defaultVibrator else getSystemService(Context.VIBRATOR_SERVICE) as Vibrator;if(android.os.Build.VERSION.SDK_INT>=26)vib.vibrate(VibrationEffect.createOneShot(12,-1))else @Suppress("DEPRECATION") vib.vibrate(12)}catch(_:Exception){}}
    private fun key(t:String,click:()->Unit)=TextView(this).apply{text=t;gravity=Gravity.CENTER;textSize=if(t.length>2)11f else 19f;setTextColor(Color.WHITE);typeface=Typeface.create("sans-serif-medium",0);background=GradientDrawable().apply{setColor(if(t=="English · GRIMY")accent else 0xFF25222C.toInt());cornerRadius=dp(9).toFloat();setStroke(dp(1),0x22FFFFFF)};isClickable=true;isFocusable=true;setOnClickListener{animate().scaleX(.95f).scaleY(.95f).setDuration(55).withEndAction{scaleX=1f;scaleY=1f}.start();click()}}
    private fun label(t:String,s:Float,c:Int,b:Boolean)=TextView(this).apply{text=t;textSize=s;setTextColor(c);if(b)setTypeface(null,1);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(6),dp(4),dp(6),dp(4))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
