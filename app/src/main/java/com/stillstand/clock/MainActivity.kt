package com.stillstand.clock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("stillstand", Context.MODE_PRIVATE) }
    private lateinit var toggle: Button
    private lateinit var status: TextView
    private var face = 0
    private var palette = 0
    private var red = false
    private val faces = listOf("Studio Digital", "Square Automatic", "Editorial", "Quiet Lines", "Red After Dark", "Orbit")
    private val descriptions = listOf("Large, calm numerals", "A precision square-dial movement", "A softer editorial typeface", "Minimal marks, maximum space", "Low-light monochrome red", "A restrained orbital dial")
    private val accents = listOf(0xFFE9E0D1.toInt(), 0xFFA8B9A2.toInt(), 0xFFA8C8DE.toInt())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        face = prefs.getInt("face", 0); palette = prefs.getInt("palette", 0); red = prefs.getBoolean("red", false)
        buildUi()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(26), dp(28), dp(26), dp(32)); setBackgroundColor(0xFF101112.toInt()) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }
        page.addView(label("A QUIETER WAY TO KEEP TIME", 10f, 0xFF9B9B96.toInt(), true))
        page.addView(label("Stillstand", 43f, Color.WHITE, false).apply { typeface=Typeface.create("sans-serif-light",0); setPadding(0,dp(8),0,0) })
        page.addView(label("A little room for time to breathe.", 14f, 0xFF9B9B96.toInt(), false))
        space(page, 24)
        val preview = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; background=rounded(0xFF1B1C1D.toInt(),22); setPadding(dp(18),dp(22),dp(18),dp(22)) }
        preview.addView(label("10:09", 42f, 0xFFE9E0D1.toInt(), false).apply { typeface=Typeface.create("sans-serif-light",0); gravity=Gravity.CENTER })
        preview.addView(label("MONDAY  ·  NOVEMBER 15", 9f, 0xFF9B9B96.toInt(), true).apply { gravity=Gravity.CENTER; letterSpacing=.12f })
        page.addView(preview, LinearLayout.LayoutParams(-1,dp(130)))
        space(page, 18)
        toggle = Button(this).apply { isAllCaps=false; textSize=13f; letterSpacing=.06f; setTextColor(0xFF151515.toInt()); background=rounded(0xFFE9E0D1.toInt(),18); setOnClickListener { toggleService() } }
        page.addView(toggle, LinearLayout.LayoutParams(-1,dp(56)))
        status = label("", 12f, 0xFFAAA9A3.toInt(), false).apply { setPadding(dp(2),dp(10),dp(2),0) }
        page.addView(status)
        space(page,24)
        page.addView(section("WATCH FACES"))
        faces.forEachIndexed { i, name ->
            val item = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(13),dp(16),dp(13)); background=rounded(if(face==i) 0xFF30302E.toInt() else 0xFF202122.toInt(),15); isClickable=true; isFocusable=true }
            item.addView(label(name,15f,Color.WHITE,false))
            item.addView(label(descriptions[i],11f,0xFF999995.toInt(),false).apply { setPadding(0,dp(4),0,0) })
            item.setOnClickListener { face=i; prefs.edit().putInt("face",face).apply(); refreshFaceRows(page) }
            item.tag="face_$i"; val lp=LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(7); page.addView(item,lp)
        }
        space(page,17); page.addView(section("ACCENT PALETTE"))
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf("Warm Ivory","Sage","Arctic Blue").forEachIndexed { i, name ->
            val b=Button(this).apply { text=name; textSize=10f; isAllCaps=false; setTextColor(accents[i]); background=rounded(0xFF242526.toInt(),12); alpha=if(palette==i) 1f else .55f; setOnClickListener { palette=i; prefs.edit().putInt("palette",i).apply(); refreshPalette(row) } }
            val lp=LinearLayout.LayoutParams(0,dp(46),1f); lp.marginEnd=dp(5); row.addView(b,lp); b.tag="palette_$i"
        }
        page.addView(row)
        space(page,12)
        val night=Switch(this).apply { text="Red night mode"; textSize=14f; setTextColor(Color.WHITE); isChecked=red; setOnCheckedChangeListener { _, checked -> red=checked; prefs.edit().putBoolean("red",red).apply() } }
        page.addView(night)
        space(page,22); page.addView(section("THE STANDBY EXPERIENCE"))
        page.addView(label("Turn on Stillstand once. Then leave the app and use your home screen normally. When the phone is held in landscape and stays still for five seconds, the clock appears over your current screen.",13f,0xFFB5B4AE.toInt(),false))
        page.addView(label("Requires the Android 'Display over other apps' permission. A small ongoing notification stays visible while standby detection is enabled. Tap the clock to dismiss it; movement rearms detection.",11f,0xFF777873.toInt(),false).apply { setPadding(0,dp(12),0,0) })
        page.addView(label("Note: Android and some phone makers may pause background services under aggressive battery restrictions. The clock works while the screen is on; it does not replace the lock screen.",11f,0xFF777873.toInt(),false).apply { setPadding(0,dp(10),0,0) })
        setContentView(scroll)
        refreshToggle()
    }

    private fun toggleService() {
        val active = prefs.getBoolean("enabled", false)
        if (active) {
            prefs.edit().putBoolean("enabled", false).apply()
            stopService(Intent(this, StandbyService::class.java)); refreshToggle(); return
        }
        if (!Settings.canDrawOverlays(this)) {
            val intent=Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this,"Allow Stillstand to display over other apps, then turn it on again.",Toast.LENGTH_LONG).show()
            return
        }
        prefs.edit().putBoolean("enabled", true).apply()
        val intent=Intent(this,StandbyService::class.java).setAction(StandbyService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        refreshToggle()
    }

    override fun onResume() { super.onResume(); if (::toggle.isInitialized) refreshToggle() }
    private fun refreshToggle() {
        val active=prefs.getBoolean("enabled",false)
        toggle.text=if(active) "TURN OFF STANDBY" else "TURN ON STANDBY"
        status.text=if(active) "ACTIVE  ·  Background detection is running" else if(!Settings.canDrawOverlays(this)) "Ready when you grant overlay permission" else "OFF  ·  Your clock faces are saved"
    }
    private fun refreshFaceRows(page: LinearLayout) { for(i in faces.indices) page.findViewWithTag<View>("face_$i")?.background=rounded(if(face==i) 0xFF30302E.toInt() else 0xFF202122.toInt(),15) }
    private fun refreshPalette(row: LinearLayout) { for(i in 0..2) row.findViewWithTag<View>("palette_$i")?.alpha=if(palette==i) 1f else .55f }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun label(s:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply { text=s;textSize=size;setTextColor(color);typeface=Typeface.create("sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL);letterSpacing=if(bold).10f else 0f }
    private fun section(s:String)=label(s,10f,0xFF9B9B96.toInt(),true).apply { setPadding(0,0,0,dp(12)) }
    private fun space(parent:LinearLayout,h:Int){parent.addView(View(this),LinearLayout.LayoutParams(1,dp(h)))}
    private fun rounded(color:Int,r:Int)=GradientDrawable().apply { setColor(color);cornerRadius=dp(r).toFloat() }
}
