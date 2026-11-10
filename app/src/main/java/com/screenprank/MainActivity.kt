package com.screenprank

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("screen_prank", MODE_PRIVATE) }
    private lateinit var statusText: TextView
    private var pendingStart = false
    private val creatures = listOf(
        "זבוב" to 0, "עכביש" to 1, "מקק" to 2, "נמלה" to 3,
        "פרת משה רבנו" to 4, "יתוש" to 5, "דבורה" to 6,
        "עקרב" to 7, "לטאה" to 8, "נחש" to 9,
        "עכבר" to 10, "שבלול" to 11, "צפרדע" to 12, "תולעת" to 13
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(14, 15, 20)
        window.navigationBarColor = Color.rgb(14, 15, 20)
        buildScreen()
        if (!prefs.getBoolean("intro_seen", false)) showIntro()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) refreshStatus()
        if (pendingStart && overlayAllowed()) {
            pendingStart = false
            startEffects()
        }
    }

    private fun overlayAllowed(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(14, 15, 20))
            isFillViewport = true
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(26), dp(20), dp(30))
        }
        scroll.addView(page)
        setContentView(scroll)

        page.addView(text("מסך מופרע", 32, 0xFFF2F4F8.toInt(), true))
        page.addView(text("קצת כאוס על המסך. בלי נזק, בלי חסימות.", 14, 0xFF9AA0B4.toInt(), false))
        statusText = text("בודק מצב…", 14, 0xFFB6FF3B.toInt(), true)
        statusText.setPadding(0, dp(14), 0, dp(14))
        page.addView(statusText)

        val mainButton = button("הפעל את הבלגן", 0xFFB6FF3B.toInt(), 0xFF10130B.toInt())
        mainButton.textSize = 18f
        mainButton.minHeight = dp(58)
        mainButton.setOnClickListener {
            if (OverlayService.active) stopEffects() else requestAndStart()
        }
        page.addView(mainButton, matchWidth())

        val stopAllButton = button("■  כבה הכל – הסר את כל היצורים והאפקטים", 0xFFB3263E.toInt(), Color.WHITE)
        stopAllButton.textSize = 15f
        stopAllButton.minHeight = dp(52)
        stopAllButton.setOnClickListener {
            stopEffects()
            Toast.makeText(this, "כל היצורים והאפקטים כובו", Toast.LENGTH_SHORT).show()
        }
        page.addView(stopAllButton, matchWidth())

        val creaturesCard = card("היצורים על המסך", "בחר יצור. הכמות קובעת כמה יטיילו יחד.")
        page.addView(creaturesCard)
        val firstRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val secondRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        creatures.forEachIndexed { index, pair ->
            val chip = Button(this).apply {
                text = pair.first
                textSize = 11f
                isAllCaps = false
                minHeight = dp(38)
                val chosen = selectedCreatures().contains(pair.second)
                setTextColor(if (chosen) 0xFF10130B.toInt() else 0xFFF2F4F8.toInt())
                background = rounded(if (chosen) 0xFFB6FF3B.toInt() else 0xFF292C38.toInt(), 18)
                setPadding(dp(5), 0, dp(5), 0)
                setOnClickListener {
                    val chosen = selectedCreatures().toMutableSet()
                    if (!chosen.add(pair.second)) chosen.remove(pair.second)
                    if (chosen.isEmpty()) chosen.add(pair.second)
                    prefs.edit().putString("selected_creatures", chosen.sorted().joinToString(","))
                        .putInt("creature_type", pair.second).apply()
                    notifyOverlay()
                    Toast.makeText(this@MainActivity, "בחירת היצורים עודכנה", Toast.LENGTH_SHORT).show()
                    buildScreen()
                }
            }
            val target = if (index < 7) firstRow else secondRow
            target.addView(chip, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginStart = dp(2); marginEnd = dp(2); bottomMargin = dp(5)
            })
        }
        creaturesCard.addView(firstRow)
        creaturesCard.addView(secondRow)

        page.addView(rangeCard("כמות יצורים", "count", 1, 30, 6, ""))
        page.addView(rangeCard("גודל היצורים", "size", 16, 200, 74, " dp"))
        page.addView(rangeCard("מהירות תנועה", "speed", 1, 10, 4, "/10"))
        page.addView(rangeCard("משך הפעלה בדקות (0 = ללא הגבלה)", "duration_minutes", 0, 180, 0, " דקות"))

        val motion = card("מגע ותנועה", "הגדר איך היצורים מתנהגים על המסך.")
        page.addView(motion)
        addToggle(motion, "ריאליזם מלא", "realistic", true, "ציור מפורט עם הצללה, ברק ופרטים.")
        addToggle(motion, "גודל אקראי לכל יצור", "random_size", true, null)
        addToggle(motion, "צליל בעת הפעלה", "sound_enabled", false, "צליל קצר שנוצר במכשיר, ללא קובצי שמע.")
        val pathButton = button("סגנון תנועה: ${pathLabel()}", 0xFF292C38.toInt(), Color.WHITE)
        pathButton.setOnClickListener {
            prefs.edit().putInt("path_mode", (prefs.getInt("path_mode", 0) + 1) % 4).apply()
            notifyOverlay()
            buildScreen()
        }
        motion.addView(pathButton, matchWidth())
        addToggle(motion, "עצירה בניעור הטלפון", "shake_stop", false, null)
        addToggle(motion, "הפעלה מחדש אחרי אתחול", "boot_start", false, "כבוי כברירת מחדל. דורש הרשאת הצגה מעל אפליקציות.")
        addToggle(motion, "כפתורי שליטה בהתראה", "notification_actions", true, "התראת שירות קבועה נדרשת באנדרואיד. אפשר להסתיר את הכפתורים, אך לא להסיר את ההתראה.")

        val extras = card("אפקטים נוספים", "האפקטים מצוירים מקומית מעל המסך ואינם משנים קבצים או תוכן.")
        page.addView(extras)
        val crack = button("🕸️  שברי זכוכית – מראה מציאותי", 0xFFFF3D81.toInt(), Color.WHITE)
        crack.setOnClickListener { sendEffect(OverlayService.ACTION_CRACK) }
        extras.addView(crack, matchWidth())
        val dirt = button("🟤  בוץ ולכלוך – כתמים", 0xFF252A36.toInt(), Color.WHITE)
        dirt.setOnClickListener { sendEffect(OverlayService.ACTION_DIRT) }
        extras.addView(dirt, matchWidth())
        val steam = button("💨  אדים על המסך", 0xFF3A5268.toInt(), Color.WHITE)
        steam.setOnClickListener { sendEffect(OverlayService.ACTION_STEAM) }
        extras.addView(steam, matchWidth())
        val fingerprint = button("👆  טביעות אצבע", 0xFF46505B.toInt(), Color.WHITE)
        fingerprint.setOnClickListener { sendEffect(OverlayService.ACTION_FINGERPRINT) }
        extras.addView(fingerprint, matchWidth())
        val coffee = button("☕  כתמי קפה", 0xFF67452F.toInt(), Color.WHITE)
        coffee.setOnClickListener { sendEffect(OverlayService.ACTION_COFFEE) }
        extras.addView(coffee, matchWidth())
        val ink = button("🖋️  כתמי דיו", 0xFF243B70.toInt(), Color.WHITE)
        ink.setOnClickListener { sendEffect(OverlayService.ACTION_INK) }
        extras.addView(ink, matchWidth())
        val snow = button("❄️  פתיתי שלג", 0xFF426D83.toInt(), Color.WHITE)
        snow.setOnClickListener { sendEffect(OverlayService.ACTION_SNOW) }
        extras.addView(snow, matchWidth())
        val sound = button("השמע צליל לדוגמה", 0xFF292C38.toInt(), Color.WHITE)
        sound.setOnClickListener { sendEffect(OverlayService.ACTION_SOUND) }
        extras.addView(sound, matchWidth())
        val clear = button("נקה את כל האפקטים", 0xFF22E4FF.toInt(), 0xFF071317.toInt())
        clear.setOnClickListener { sendEffect(OverlayService.ACTION_CLEAR) }
        extras.addView(clear, matchWidth())

        val profiles = card("פרופילים מהירים", "טעינה בלחיצה של שילוב הגדרות.")
        page.addView(profiles)
        listOf("הפתעה לחבר", "משרד", "אימה").forEachIndexed { index, name ->
            val b = button(name, 0xFF292C38.toInt(), Color.WHITE)
            b.setOnClickListener { applyProfile(index) }
            profiles.addView(b, matchWidth())
        }
        val saveProfile = button("שמור את ההגדרות כפרופיל אישי", 0xFF3B4658.toInt(), Color.WHITE)
        saveProfile.setOnClickListener { saveCustomProfile() }
        profiles.addView(saveProfile, matchWidth())
        val loadProfile = button("טען פרופיל אישי", 0xFF3B4658.toInt(), Color.WHITE)
        loadProfile.setOnClickListener { loadCustomProfile() }
        profiles.addView(loadProfile, matchWidth())

        val foot = text("הערה: האפליקציה אינה מציגה הודעות מערכת מזויפות ואינה מדמה מסכי נעילה או שירותים פיננסיים. להפסקה מיידית לחץ על הכפתור באפליקציה או על עצור בהתראה.", 12, 0xFF9AA0B4.toInt(), false)
        foot.setPadding(dp(4), dp(18), dp(4), 0)
        page.addView(foot)
        refreshStatus()
    }

    private fun requestAndStart() {
        if (!overlayAllowed()) {
            pendingStart = true
            AlertDialog.Builder(this)
                .setTitle("נדרשת הרשאת הצגה מעל אפליקציות")
                .setMessage("כדי שהחרקים יוכלו לטייל מעל אפליקציות אחרות, צריך לאשר את ההרשאה במסך ההגדרות שייפתח.")
                .setNegativeButton("לא עכשיו", null)
                .setPositiveButton("פתיחת הגדרות") { _, _ ->
                    val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(i)
                }.show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 41)
        }
        startEffects()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) startEffects()
    }

    private fun startEffects() {
        if (!overlayAllowed()) return
        val intent = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(this, intent) else startService(intent)
        refreshStatus()
    }

    private fun stopEffects() {
        startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        refreshStatus()
    }

    private fun sendEffect(action: String) {
        if (!OverlayService.active) {
            Toast.makeText(this, "קודם הפעל את האפקטים", Toast.LENGTH_SHORT).show()
            return
        }
        startService(Intent(this, OverlayService::class.java).setAction(action))
    }

    private fun notifyOverlay() {
        if (OverlayService.active) startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_UPDATE))
    }

    private fun saveCustomProfile() {
        val editor = prefs.edit()
        listOf("count", "size", "speed", "creature_type", "path_mode", "duration_minutes").forEach { key ->
            editor.putInt("custom_$key", prefs.getInt(key, when (key) {
                "count" -> 6
                "size" -> 74
                "speed" -> 4
                else -> 0
            }))
        }
        listOf("realistic", "random_size", "sound_enabled", "shake_stop").forEach { key ->
            editor.putBoolean("custom_$key", prefs.getBoolean(key, key == "realistic" || key == "random_size"))
        }
        editor.putString("custom_selected_creatures", prefs.getString("selected_creatures", "0"))
        editor.putBoolean("custom_profile_saved", true).apply()
        Toast.makeText(this, "הפרופיל האישי נשמר", Toast.LENGTH_SHORT).show()
    }

    private fun loadCustomProfile() {
        if (!prefs.getBoolean("custom_profile_saved", false)) {
            Toast.makeText(this, "עדיין לא שמרת פרופיל אישי", Toast.LENGTH_SHORT).show()
            return
        }
        val editor = prefs.edit()
        listOf("count", "size", "speed", "creature_type", "path_mode", "duration_minutes").forEach { key ->
            val fallback = when (key) {
                "count" -> 6
                "size" -> 74
                "speed" -> 4
                else -> 0
            }
            editor.putInt(key, prefs.getInt("custom_$key", fallback))
        }
        listOf("realistic", "random_size", "sound_enabled", "shake_stop").forEach { key ->
            editor.putBoolean(key, prefs.getBoolean("custom_$key", false))
        }
        editor.putString("selected_creatures", prefs.getString("custom_selected_creatures", "0"))
        editor.apply()
        notifyOverlay()
        buildScreen()
        Toast.makeText(this, "הפרופיל האישי נטען", Toast.LENGTH_SHORT).show()
    }

    private fun selectedCreatures(): Set<Int> {
        val saved = prefs.getString("selected_creatures", "")?.split(",")
            ?.mapNotNull { it.toIntOrNull() }?.filter { it in 0..13 }?.toSet().orEmpty()
        return if (saved.isEmpty()) setOf(prefs.getInt("creature_type", 0).coerceIn(0, 13)) else saved
    }

    private fun pathLabel(): String = when (prefs.getInt("path_mode", 0)) {
        1 -> "גלים אופקיים"
        2 -> "קפיצות מהקצוות"
        3 -> "תנועה אנכית"
        else -> "שיטוט חופשי"
    }

    private fun applyProfile(index: Int) {
        val values = when (index) {
            0 -> Triple(12, 110, 7)
            1 -> Triple(4, 46, 2)
            else -> Triple(18, 135, 5)
        }
        prefs.edit()
            .putInt("count", values.first)
            .putInt("size", values.second)
            .putInt("speed", values.third)
            .putBoolean("realistic", true)
            .apply()
        notifyOverlay()
        Toast.makeText(this, "הפרופיל " + listOf("הפתעה לחבר", "משרד", "אימה")[index] + " נטען", Toast.LENGTH_SHORT).show()
        buildScreen()
    }

    private fun refreshStatus() {
        if (!::statusText.isInitialized) return
        statusText.text = if (OverlayService.active) "● האפקטים פעילים" else "○ האפקטים כבויים"
    }

    private fun showIntro() {
        val pages = listOf(
            "האפליקציה מציגה יצורים מצוירים ואפקטים הומוריסטיים מעל המסך. היא אינה משנה את התוכן שמתחת.",
            "אפשר לבחור כמה סוגי יצורים יחד, לשנות גודל ומהירות, לבחור מסלול תנועה ולהפעיל אפקטים נוספים.",
            "ההרשאה להצגה מעל אפליקציות נדרשת להפעלה. אפשר לעצור בכל רגע מהאפליקציה, מההתראה או באמצעות ניעור אם הפעלת אפשרות זו."
        )
        fun showPage(index: Int) {
            AlertDialog.Builder(this)
                .setTitle("ברוכים הבאים · ${index + 1}/3")
                .setMessage(pages[index])
                .setCancelable(false)
                .setPositiveButton(if (index == 2) "מתחילים" else "הבא") { _, _ ->
                    if (index < 2) showPage(index + 1)
                    else prefs.edit().putBoolean("intro_seen", true).apply()
                }
                .setNegativeButton(if (index == 0) "דלג" else "חזור") { _, _ ->
                    if (index == 0) prefs.edit().putBoolean("intro_seen", true).apply()
                    else showPage(index - 1)
                }
                .show()
        }
        showPage(0)
    }

    private fun rangeCard(title: String, key: String, min: Int, max: Int, default: Int, suffix: String): LinearLayout {
        val box = card(title, "הזז את המחוון כדי לשנות את הערך.")
        val value = text("", 13, 0xFFB6FF3B.toInt(), true)
        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = prefs.getInt(key, default).coerceIn(min, max) - min
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFB6FF3B.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFB6FF3B.toInt())
        }
        fun update(p: Int) {
            val actual = min + p
            value.text = actual.toString() + suffix
            prefs.edit().putInt(key, actual).apply()
            notifyOverlay()
        }
        update(seek.progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) { update(progress) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(value)
        box.addView(seek)
        return box
    }

    private fun addToggle(parent: LinearLayout, title: String, key: String, default: Boolean, note: String?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val toggle = Switch(this).apply {
            text = title
            textSize = 15f
            setTextColor(0xFFF2F4F8.toInt())
            isChecked = prefs.getBoolean(key, default)
            thumbTintList = android.content.res.ColorStateList.valueOf(if (isChecked) 0xFFB6FF3B.toInt() else 0xFF9AA0B4.toInt())
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                notifyOverlay()
                thumbTintList = android.content.res.ColorStateList.valueOf(if (checked) 0xFFB6FF3B.toInt() else 0xFF9AA0B4.toInt())
            }
        }
        row.addView(toggle)
        if (!note.isNullOrBlank()) row.addView(text(note, 11, 0xFF9AA0B4.toInt(), false))
        parent.addView(row)
    }

    private fun card(title: String, subtitle: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(0xFF1A1C25.toInt(), 20)
        }
        box.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(15) }
        box.addView(text(title, 18, 0xFFF2F4F8.toInt(), true))
        val desc = text(subtitle, 12, 0xFF9AA0B4.toInt(), false)
        desc.setPadding(0, dp(3), 0, dp(10))
        box.addView(desc)
        return box
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            gravity = Gravity.RIGHT
            typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

    private fun button(title: String, backgroundColor: Int, textColor: Int): Button =
        Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            setTextColor(textColor)
            background = rounded(backgroundColor, 16)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            minHeight = dp(46)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) }
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun matchWidth() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
