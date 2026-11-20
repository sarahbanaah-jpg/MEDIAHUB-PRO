package com.mediahub.rebuilt.arcade

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

/** 20 offline game modes with 10 stages each: 200 playable challenge slots. */
class MediaHubGameCenterActivity : AppCompatActivity() {
    private data class Game(val name:String,val category:String,val detail:String,val icon:String)
    private val games = listOf(
        Game("Number Sprint","Brain","Complete number patterns","🔢"),
        Game("Math Rush","Brain","Solve arithmetic challenges","➗"),
        Game("Trivia Quest","Quiz","Offline general-knowledge quiz","🧠"),
        Game("Odd One Out","Brain","Find the number that breaks the rule","🧩"),
        Game("Higher or Lower","Arcade","Predict the next number","📈"),
        Game("Rock Paper Scissors","Arcade","Beat the computer","✊"),
        Game("Color Clash","Brain","Identify the ink colour","🎨"),
        Game("Quick Count","Arcade","Count the symbols","🔍"),
        Game("Sequence Recall","Brain","Remember digit sequences","🧠"),
        Game("Word Scramble","Words","Unscramble hidden words","🔤"),
        Game("Anagram Challenge","Words","Solve letter rearrangements","📝"),
        Game("Number Hunt","Arcade","Tap numbers in ascending order","🎯"),
        Game("Lights Out","Puzzle","Switch off every light","💡"),
        Game("Target Tap","Arcade","Find the requested symbol","🎯"),
        Game("Hangman","Words","Reveal the hidden word","🪢"),
        Game("Tic-Tac-Toe","Puzzle","Play against the computer","❎"),
        Game("Code Breaker","Puzzle","Crack a three-digit code","🔐"),
        Game("Reaction Test","Arcade","Test your reaction speed","⚡"),
        Game("Memory Match","Puzzle","Classic matching-pairs game","🃏"),
        Game("2048","Puzzle","Merge tiles to reach 2048","🔢")
    )
    private val bg=0xFF08080D.toInt()
    private val panel=0xFF171720.toInt()
    private val pink=0xFFFF6B9F.toInt()
    private val muted=0xFFB9B6C3.toInt()
    private val prefs by lazy { getSharedPreferences("mediahub_game_center",MODE_PRIVATE) }
    private val handler=Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);showHub()}

    private fun showHub(query:String="") {
        val scroll=ScrollView(this).apply{setBackgroundColor(bg)}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(34),dp(18),dp(36))}
        scroll.addView(root)
        root.addView(TextView(this).apply{
            text="‹  MEDIAHUB GAME CENTER";textSize=22f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD
            setPadding(0,0,0,dp(8));setOnClickListener{finish()}
        })
        root.addView(TextView(this).apply{
            text="20 game modes  ·  10 stages each  ·  200 offline challenges"
            textSize=13f;setTextColor(muted);setPadding(0,0,0,dp(16))
        })
        val wins=prefs.getInt("wins",0);val played=prefs.getInt("played",0)
        root.addView(TextView(this).apply{
            text="🏆  $wins wins       🎮  $played plays       ⭐  ${badge(wins)}"
            textSize=14f;setTextColor(Color.WHITE);setPadding(dp(15),dp(15),dp(15),dp(15));background=card()
        })
        val day=java.time.LocalDate.now()
        val daily=day.dayOfYear%games.size;val dailyLevel=day.dayOfMonth%10+1
        root.addView(TextView(this).apply{
            text="🌟  DAILY CHALLENGE\n${games[daily].name} · Stage $dailyLevel\nPlay today's challenge  →"
            textSize=16f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD
            setPadding(dp(16),dp(16),dp(16),dp(16));background=card(pink)
            setOnClickListener{startGame(daily,dailyLevel)}
        },LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12);bottomMargin=dp(16)})
        val search=EditText(this).apply{
            hint="Search games";setSingleLine(true);setTextColor(Color.WHITE);setHintTextColor(muted)
            setPadding(dp(14),dp(10),dp(14),dp(10));background=card();setText(query)
        }
        root.addView(search,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(list)
        fun render(q:String){
            list.removeAllViews()
            games.forEachIndexed{index,g->
                if(q.isNotBlank()&&!${g.name+" "+g.category+" "+g.detail}.contains(q,true))return@forEachIndexed
                val item=LinearLayout(this).apply{
                    orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14));background=card()
                    isClickable=true;isFocusable=true;setOnClickListener{chooseLevel(index)}
                    setOnLongClickListener{
                        val next=!prefs.getBoolean("fav_$index",false)
                        prefs.edit().putBoolean("fav_$index",next).apply()
                        Toast.makeText(this@MediaHubGameCenterActivity,if(next)"Added to favourites" else "Removed from favourites",Toast.LENGTH_SHORT).show()
                        render(search.text.toString());true
                    }
                }
                val star=if(prefs.getBoolean("fav_$index",false))" ★" else ""
                item.addView(TextView(this).apply{text="${g.icon}  ${g.name}$star";textSize=18f;setTextColor(pink);typeface=Typeface.DEFAULT_BOLD})
                item.addView(TextView(this).apply{text="${g.category} · 10 stages · ${g.detail}";textSize=12f;setTextColor(muted);setPadding(0,dp(5),0,dp(8))})
                item.addView(TextView(this).apply{text="PLAY →   ·   Long-press to favourite";textSize=11f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD})
                list.addView(item,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})
            }
        }
        render(query)
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){render(s.toString())}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        root.addView(TextView(this).apply{
            text="BADGES\n${badge(wins)}\n${if(wins>=5)"Rising Star unlocked" else "Win 5 games to unlock Rising Star"}\n${if(wins>=20)"Arcade Champion unlocked" else "Win 20 games to unlock Arcade Champion"}"
            textSize=13f;setTextColor(muted);setPadding(0,dp(18),0,0)
        })
        setContentView(scroll)
    }

    private fun chooseLevel(id:Int){
        val stages=(1..10).map{"Stage $it  ${if(prefs.getBoolean("done_${id}_$it",false))"✓ completed" else "•"}"}
        AlertDialog.Builder(this).setTitle(games[id].name).setItems(stages.toTypedArray()){_,which->startGame(id,which+1)}.setNegativeButton("Close",null).show()
    }

    private fun startGame(id:Int,l:Int){
        when(id){
            0->numberSprint(l);1->mathRush(l);2->trivia(l);3->oddOne(l);4->higherLower(l)
            5->rps(l);6->colorClash(l);7->quickCount(l);8->sequence(l);9->wordPuzzle(l,false)
            10->wordPuzzle(l,true);11->numberHunt(l);12->lightsOut(l);13->targetTap(l);14->hangman(l)
            15->ticTacToe(l);16->codeBreaker(l);17->reaction(l)
            18,19->startActivity(Intent(this,MediaHubArcadeActivity::class.java))
        }
    }

    private fun numberSprint(l:Int){
        val step=Random.nextInt(2,l+4);val start=Random.nextInt(1,20);val missing=Random.nextInt(1,5)
        val answer=start+step*missing
        val sequence=(0..5).joinToString("   "){if(it==missing)"?" else (start+step*it).toString()}
        val opts=(listOf(answer)+List(8){answer+Random.nextInt(-4,5)*step}.filter{it>0}).distinct().take(4).shuffled()
        ask("Number Sprint · Stage $l","$sequence\n\nChoose the missing number.",opts.map{it.toString()},answer.toString(),0,l)
    }
    private fun mathRush(l:Int){
        val a=Random.nextInt(2,10+l*3);val b=Random.nextInt(2,8+l*2);val op=listOf("+","−","×").take(if(l<4)2 else 3).random()
        val answer=when(op){"+"->a+b;"−"->kotlin.math.abs(a-b);else->a*b}
        val opts=(listOf(answer)+List(8){(answer+Random.nextInt(-8,9)).coerceAtLeast(0)}).distinct().take(4).shuffled()
        ask("Math Rush · Stage $l","$a $op $b = ?",opts.map{it.toString()},answer.toString(),1,l)
    }
    private fun trivia(l:Int){
        val bank=listOf(
            Triple("Which planet is called the Red Planet?",listOf("Mars","Venus","Jupiter","Mercury"),"Mars"),
            Triple("How many sides has a hexagon?",listOf("5","6","7","8"),"6"),
            Triple("Which is the largest ocean?",listOf("Atlantic","Indian","Pacific","Arctic"),"Pacific"),
            Triple("What is the chemical formula for water?",listOf("CO2","H2O","O2","NaCl"),"H2O"),
            Triple("How many minutes are in two hours?",listOf("60","90","120","180"),"120"),
            Triple("Which gas do plants absorb?",listOf("Oxygen","Nitrogen","Carbon dioxide","Helium"),"Carbon dioxide"),
            Triple("Which continent is Kenya in?",listOf("Asia","Africa","Europe","Oceania"),"Africa"),
            Triple("What is the square root of 81?",listOf("7","8","9","10"),"9"),
            Triple("What is the largest mammal?",listOf("Elephant","Blue whale","Giraffe","Hippo"),"Blue whale"),
            Triple("How many colours are traditionally in a rainbow?",listOf("5","6","7","8"),"7")
        )
        val q=bank.random();ask("Trivia Quest · Stage $l",q.first,q.second.shuffled(),q.third,2,l)
    }
    private fun oddOne(l:Int){
        val base=Random.nextInt(3,20+l);val values=(0..3).map{base+it*2}.toMutableList();val ix=Random.nextInt(4)
        values[ix]+=1
        ask("Odd One Out · Stage $l","Find the number that breaks the even-step pattern.",values.shuffled().map{it.toString()},values[ix].toString(),3,l)
    }
    private fun higherLower(l:Int){
        val a=Random.nextInt(10,80+l*5);val b=Random.nextInt(1,100+l*5);val answer=if(b>=a)"Higher" else "Lower"
        ask("Higher or Lower · Stage $l","Current: $a\nNext: $b\nIs the next number higher or lower?",listOf("Higher","Lower"),answer,4,l)
    }
    private fun rps(l:Int){
        val options=listOf("Rock","Paper","Scissors");val cpu=options.random()
        AlertDialog.Builder(this).setTitle("Rock Paper Scissors · Stage $l").setMessage("Choose your move.")
            .setItems(options.toTypedArray()){_,i->
                val p=options[i];val win=(p=="Rock"&&cpu=="Scissors")||(p=="Paper"&&cpu=="Rock")||(p=="Scissors"&&cpu=="Paper")
                result(5,l,"You: $p · Computer: $cpu",win)
            }.setNegativeButton("Cancel",null).show()
    }
    private fun colorClash(l:Int){
        val colors=listOf("RED","BLUE","GREEN","YELLOW");val ink=colors.random();val word=colors.filter{it!=ink}.random()
        val sample=TextView(this).apply{text=word;textSize=30f;gravity=Gravity.CENTER;setTextColor(when(ink){"RED"->Color.RED;"BLUE"->Color.BLUE;"GREEN"->Color.GREEN;else->Color.YELLOW});setPadding(0,dp(16),0,dp(16))}
        AlertDialog.Builder(this).setTitle("Color Clash · Stage $l").setMessage("Which colour is the INK?").setView(sample)
            .setItems(colors.toTypedArray()){_,i->result(6,l,"Ink colour: $ink",colors[i]==ink)}.setNegativeButton("Cancel",null).show()
    }
    private fun quickCount(l:Int){
        val symbols=listOf("●","◆","★","▲");val target=symbols.random();val count=Random.nextInt(3,6+l)
        val display=(List(count){target}+List(8+l){symbols.filter{it!=target}.random()}).shuffled().joinToString("  ")
        val opts=(listOf(count)+List(8){(count+Random.nextInt(-3,4)).coerceAtLeast(1)}).distinct().take(4).shuffled()
        ask("Quick Count · Stage $l","Count the $target symbols:\n\n$display",opts.map{it.toString()},count.toString(),7,l)
    }
    private fun sequence(l:Int){
        val answer=List((3+l/2).coerceAtMost(8)){Random.nextInt(10)}.joinToString("")
        val shown=TextView(this).apply{text=answer;textSize=30f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);setPadding(0,dp(18),0,dp(18))}
        val d=AlertDialog.Builder(this).setTitle("Memorise the sequence").setMessage("Remember these digits.").setView(shown).setPositiveButton("Ready",null).create()
        d.setOnShowListener{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            d.dismiss();handler.postDelayed({
                val input=EditText(this).apply{hint="Enter digits";setSingleLine(true);inputType=2}
                AlertDialog.Builder(this).setTitle("Sequence Recall · Stage $l").setView(input).setPositiveButton("Check"){_,_->result(8,l,"Sequence: $answer",input.text.toString()==answer)}.setNegativeButton("Cancel",null).show()
            },250)
        }}
        d.show()
    }
    private val words=listOf("MUSIC","CAMERA","PLANET","GARDEN","PUZZLE","ORANGE","POCKET","STREAM","BRIGHT","PLAYER","GALAXY","BUTTON","SILVER","MARKET","JUNGLE","WINDOW","ROCKET","PURPLE","FLOWER","WINTER")
    private fun wordPuzzle(l:Int,anagram:Boolean){
        val word=words.filter{it.length>=if(anagram)5+l/3 else 4}.random()
        var scrambled=word.toList().shuffled().joinToString("");if(scrambled==word)scrambled=word.reversed()
        val input=EditText(this).apply{hint="Your answer";setSingleLine(true)}
        AlertDialog.Builder(this).setTitle("${if(anagram)"Anagram Challenge" else "Word Scramble"} · Stage $l")
            .setMessage("Rearrange these letters:\n\n$scrambled").setView(input)
            .setPositiveButton("Submit"){_,_->result(if(anagram)10 else 9,l,"The word was $word.",input.text.toString().trim().uppercase()==word)}
            .setNegativeButton("Cancel",null).show()
    }
    private fun numberHunt(l:Int){
        val count=(6+l).coerceAtMost(15);val nums=(1..count).shuffled();var next=1
        val d=Dialog(this);val box=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(16),dp(16),dp(16));setBackgroundColor(bg)}
        val status=TextView(this).apply{text="Tap 1 to $count in order";textSize=17f;setTextColor(Color.WHITE)};box.addView(status)
        val grid=GridLayout(this).apply{columnCount=4};box.addView(grid)
        nums.forEach{n->grid.addView(Button(this).apply{
            text=n.toString();isAllCaps=false
            setOnClickListener{if(n==next){isEnabled=false;text="✓";next++;status.text="Next: ${if(next>count)"Complete!" else next}"
                if(next>count){d.dismiss();result(11,l,"Number hunt completed!",true)}
            }else Toast.makeText(this@MediaHubGameCenterActivity,"Find $next next.",Toast.LENGTH_SHORT).show()}
        },GridLayout.LayoutParams().apply{width=dp(66);height=dp(58);setMargins(dp(3),dp(3),dp(3),dp(3))})}
        box.addView(Button(this).apply{text="Close";setOnClickListener{d.dismiss()}});d.setContentView(box);d.show()
    }
    private fun lightsOut(l:Int){
        val state=Array(3){BooleanArray(3){Random.nextBoolean()}};val d=Dialog(this)
        val box=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(16),dp(16),dp(16));setBackgroundColor(bg)}
        box.addView(TextView(this).apply{text="Switch off every light";textSize=17f;setTextColor(Color.WHITE)})
        val grid=GridLayout(this).apply{columnCount=3};box.addView(grid)
        fun redraw(){for(y in 0..2)for(x in 0..2){val b=grid.getChildAt(y*3+x) as Button;b.text=if(state[y][x])"●" else "○";b.setTextColor(if(state[y][x])pink else muted)}}
        for(y in 0..2)for(x in 0..2)grid.addView(Button(this).apply{
            setOnClickListener{
                listOf(y to x,y-1 to x,y+1 to x,y to x-1,y to x+1).forEach{(r,c)->if(r in 0..2&&c in 0..2)state[r][c]=!state[r][c]}
                redraw();if(state.all{row->row.none{it}}){d.dismiss();result(12,l,"All lights are off!",true)}
            }
        },GridLayout.LayoutParams().apply{width=dp(76);height=dp(68);setMargins(dp(3),dp(3),dp(3),dp(3))})
        redraw();box.addView(Button(this).apply{text="Close";setOnClickListener{d.dismiss()}});d.setContentView(box);d.show()
    }
    private fun targetTap(l:Int){
        val target=listOf("★","●","◆","▲","♥").random();val symbols=(listOf(target)+List(8+l){listOf("★","●","◆","▲","♥").filter{it!=target}.random()}).shuffled()
        val d=Dialog(this);val box=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(16),dp(16),dp(16));setBackgroundColor(bg)}
        box.addView(TextView(this).apply{text="Find: $target";textSize=19f;setTextColor(Color.WHITE)})
        val grid=GridLayout(this).apply{columnCount=3};box.addView(grid)
        symbols.forEach{s->grid.addView(Button(this).apply{text=s;textSize=22f;setOnClickListener{
            if(s==target){d.dismiss();result(13,l,"Target found!",true)}else Toast.makeText(this@MediaHubGameCenterActivity,"Try again.",Toast.LENGTH_SHORT).show()
        }},GridLayout.LayoutParams().apply{width=dp(76);height=dp(64);setMargins(dp(3),dp(3),dp(3),dp(3))})}
        box.addView(Button(this).apply{text="Close";setOnClickListener{d.dismiss()}});d.setContentView(box);d.show()
    }
    private fun hangman(l:Int){
        val word=words.filter{it.length>=4+l/3}.random();val guessed=mutableSetOf<Char>();var misses=0
        fun next(){
            val shown=word.map{if(it in guessed)it else '_'}.joinToString(" ")
            if(!shown.contains('_')){result(14,l,"You revealed $word.",true);return}
            if(misses>=6){result(14,l,"Out of attempts. Word: $word",false);return}
            val letters=('A'..'Z').filter{it !in guessed}.map{it.toString()}.toTypedArray()
            AlertDialog.Builder(this).setTitle("Hangman · Stage $l").setMessage("$shown\nWrong guesses: $misses / 6")
                .setItems(letters){_,i->val c=letters[i][0];guessed.add(c);if(c !in word)misses++;next()}
                .setNegativeButton("Give up"){_,_->result(14,l,"Word: $word",false)}.show()
        };next()
    }
    private fun ticTacToe(l:Int){
        val board=Array(9){""};val d=Dialog(this);val box=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(16),dp(16),dp(16));setBackgroundColor(bg)}
        box.addView(TextView(this).apply{text="You are X · Computer is O";setTextColor(Color.WHITE);textSize=16f})
        val grid=GridLayout(this).apply{columnCount=3};box.addView(grid)
        fun won(m:String):Boolean{val lines=listOf(listOf(0,1,2),listOf(3,4,5),listOf(6,7,8),listOf(0,3,6),listOf(1,4,7),listOf(2,5,8),listOf(0,4,8),listOf(2,4,6));return lines.any{line->line.all{board[it]==m}}}
        fun end(msg:String,win:Boolean){d.dismiss();result(15,l,msg,win)}
        fun draw(){for(i in 0..8)(grid.getChildAt(i) as Button).text=board[i].ifBlank{" "}}
        repeat(9){i->grid.addView(Button(this).apply{text=" ";textSize=24f;setOnClickListener{
            if(board[i].isNotEmpty())return@setOnClickListener
            board[i]="X";draw();if(won("X")){end("You beat the computer!",true);return@setOnClickListener}
            val free=board.indices.filter{board[it].isEmpty()};if(free.isEmpty()){end("Draw game.",false);return@setOnClickListener}
            board[free.random()]="O";draw();if(won("O"))end("Computer won this round.",false)else if(board.none{it.isEmpty()})end("Draw game.",false)
        }},GridLayout.LayoutParams().apply{width=dp(82);height=dp(76);setMargins(dp(3),dp(3),dp(3),dp(3))})}
        box.addView(Button(this).apply{text="Close";setOnClickListener{d.dismiss()}});d.setContentView(box);d.show()
    }
    private fun codeBreaker(l:Int){
        val secret=Random.nextInt(100,1000).toString();val input=EditText(this).apply{hint="3-digit code";setSingleLine(true);inputType=2}
        AlertDialog.Builder(this).setTitle("Code Breaker · Stage $l").setMessage("Crack the secret three-digit code in one attempt.").setView(input)
            .setPositiveButton("Check"){_,_->val guess=input.text.toString();result(16,l,"Secret code: $secret",guess==secret)}.setNegativeButton("Cancel",null).show()
    }
    private fun reaction(l:Int){
        val d=Dialog(this);val box=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setPadding(dp(20),dp(24),dp(20),dp(24));setBackgroundColor(bg)}
        val label=TextView(this).apply{text="Tap START, then wait for GO.";textSize=19f;setTextColor(Color.WHITE);gravity=Gravity.CENTER}
        val button=Button(this).apply{text="START"};box.addView(label);box.addView(button)
        var ready=0L;var phase=0
        button.setOnClickListener{
            if(phase==0){phase=1;button.isEnabled=false;button.text="WAIT…";label.text="Wait for the signal."
                handler.postDelayed({if(d.isShowing){phase=2;ready=android.os.SystemClock.elapsedRealtime();button.isEnabled=true;button.text="GO!";label.text="TAP NOW!"}},Random.nextLong(1200,3500))
            }else if(phase==2){val ms=android.os.SystemClock.elapsedRealtime()-ready;d.dismiss();result(17,l,"Reaction time: $ms ms",ms<(1000-l*40))}
        }
        d.setOnDismissListener{handler.removeCallbacksAndMessages(null)}
        box.addView(TextView(this).apply{text="A false start cancels the attempt.";setTextColor(muted);textSize=12f})
        d.setContentView(box);d.show()
    }

    private fun ask(title:String,prompt:String,options:List<String>,answer:String,id:Int,l:Int){
        AlertDialog.Builder(this).setTitle(title).setMessage(prompt).setItems(options.toTypedArray()){_,i->
            val selected=options[i];result(id,l,"Your answer: $selected\nCorrect answer: $answer",selected==answer)
        }.setNegativeButton("Cancel",null).show()
    }
    private fun result(id:Int,l:Int,message:String,win:Boolean){
        prefs.edit().putInt("played",prefs.getInt("played",0)+1)
            .putInt("wins",prefs.getInt("wins",0)+if(win)1 else 0).putBoolean("done_${id}_$l",win).apply()
        AlertDialog.Builder(this).setTitle(if(win)"🏆 Challenge cleared!" else "Round complete")
            .setMessage(message+if(win)"\n\nStage $l completed." else "\n\nTry again to earn this stage badge.")
            .setPositiveButton("Game Center"){_,_->showHub()}.setNeutralButton("Retry"){_,_->startGame(id,l)}.show()
    }
    private fun badge(w:Int)=when{w>=50->"Legend";w>=20->"Arcade Champion";w>=5->"Rising Star";w>0->"First Win";else->"New Player"}
    private fun card(stroke:Int=0x33FFFFFF)=GradientDrawable().apply{setColor(panel);cornerRadius=dp(18).toFloat();setStroke(dp(1),stroke)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
