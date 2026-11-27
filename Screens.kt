@file:OptIn(ExperimentalMaterial3Api::class)

package com.amar.toolkit

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FeatureScreen(f: Feature) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(f.desc, style = MaterialTheme.typography.bodyLarge)
        f.need?.let {
            Text("দরকারি অনুমতি: $it", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary)
        }
        f.toggleKey?.let { PrefSwitch(it) }
        when (f.id) {
            "setup" -> SetupScreen()
            "modes" -> ModesScreen()
            "night" -> NightScreen()
            "tiles" -> TilesScreen()
            "perms" -> PermsScreen()
            "info" -> InfoScreen()
            "clock" -> ClockScreen()
            "wifi" -> WifiScreen()
            "focus" -> FocusScreen()
            "diary" -> DiaryScreen()
            "lost" -> LostSetupScreen()
            "mathalarm" -> MathAlarmScreen()
            "voice" -> VoiceScreen()
        }
    }
}

// ---------- ছোট সাহায্যকারী ----------

@Composable
fun PrefSwitch(key: String, label: String = "চালু করো", onChange: (Boolean) -> Unit = {}) {
    val c = LocalContext.current
    var v by remember(key) { mutableStateOf(Prefs.p(c).getBoolean(key, false)) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Switch(checked = v, onCheckedChange = {
                v = it
                Prefs.p(c).edit().putBoolean(key, it).apply()
                MonitorService.ensure(c)
                onChange(it)
            })
        }
    }
}

@Composable
fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(k, Modifier.weight(1f))
        Text(v, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
    HorizontalDivider()
}

private fun Context.open(i: Intent) {
    try { startActivity(i) } catch (_: Exception) {}
}

private fun Context.pkgUri() = Uri.parse("package:$packageName")

private fun pickTime(c: Context, h: Int, m: Int, done: (Int, Int) -> Unit) {
    TimePickerDialog(c, { _, hh, mm -> done(hh, mm) }, h, m, false).show()
}

private fun fmtTime(h: Int, m: Int): String {
    val hh = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.US, "%d:%02d %s", hh, m, if (h < 12) "AM" else "PM")
}

// ---------- ⚙️ অনুমতি সেটআপ ----------

@SuppressLint("BatteryLife")
@Composable
fun SetupScreen() {
    val c = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    fun granted(p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    if (tick < 0) return // tick পড়লে প্রতি সেকেন্ডে অবস্থা নতুন করে দেখায়

    PermRow("🔔 নোটিফিকেশন", Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)) {
        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    PermRow("📞 ফোন কলের অবস্থা (কলে ফ্ল্যাশ)", granted(Manifest.permission.READ_PHONE_STATE)) {
        ask.launch(Manifest.permission.READ_PHONE_STATE)
    }
    PermRow("✉️ SMS গ্রহণ (হারানো ফোন)", granted(Manifest.permission.RECEIVE_SMS)) {
        ask.launch(Manifest.permission.RECEIVE_SMS)
    }
    PermRow("☀️ সিস্টেম সেটিং বদলানো (ব্রাইটনেস, টাইমআউট)", SettingsCtl.canWrite(c)) {
        c.open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, c.pkgUri()))
    }
    PermRow("🌙 Do Not Disturb (পুরো সাইলেন্ট)", SettingsCtl.hasDnd(c)) {
        c.open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }
    val pm = c.getSystemService(PowerManager::class.java)
    PermRow("🔋 পটভূমিতে চলতে দাও", pm.isIgnoringBatteryOptimizations(c.packageName)) {
        c.open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, c.pkgUri()))
    }
    if (Build.VERSION.SDK_INT >= 34) {
        PermRow("🚨 ফুল-স্ক্রিন অ্যালার্ম", Notif.nm(c).canUseFullScreenIntent()) {
            c.open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, c.pkgUri()))
        }
    }
    Text("সব ✅ হলে সব ফিচার ঠিকমতো চলবে। যেটা লাগবে না সেটা বাদ দিতে পারো।",
        style = MaterialTheme.typography.bodySmall)
}

@Composable
fun PermRow(label: String, ok: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (ok) "✅" else "❌", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(label, Modifier.weight(1f))
            if (!ok) FilledTonalButton(onClick = onClick) { Text("দাও") }
        }
    }
}

// ---------- 🎚️ মোড ----------

@Composable
fun ModesScreen() {
    val c = LocalContext.current
    var msg by remember { mutableStateOf("") }
    val modes = listOf(
        Triple("class", "📚 ক্লাস মোড", "ভাইব্রেট, মিউজিক বন্ধ"),
        Triple("sleep", "😴 ঘুম মোড", "সাইলেন্ট, মিউজিক বন্ধ, ব্রাইটনেস কম"),
        Triple("game", "🎮 গেম মোড", "রিং চালু, মিউজিক ৭০%, ব্রাইটনেস পুরো"),
        Triple("normal", "🙂 স্বাভাবিক", "রিং চালু, মিউজিক ৫০%, অটো ব্রাইটনেস"),
        Triple("custom", "⭐ আমার প্রোফাইল", "যেটা সেভ করে রেখেছো"),
    )
    modes.forEach { (id, title, sub) ->
        Card(onClick = { msg = SettingsCtl.applyProfile(c, id) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    OutlinedButton(onClick = { SettingsCtl.saveCustom(c); msg = "💾 এখনকার রিংটোন, ভলিউম আর ব্রাইটনেস \"আমার প্রোফাইল\"-এ সেভ হলো" },
        modifier = Modifier.fillMaxWidth()) { Text("এখনকার সেটিং সেভ করো") }
    if (msg.isNotEmpty()) Text(msg, fontWeight = FontWeight.Bold)
}

// ---------- 🌙 রাত ----------

@Composable
fun NightScreen() {
    val c = LocalContext.current
    val p = Prefs.p(c)
    var onH by remember { mutableIntStateOf(p.getInt("night_on_h", 23)) }
    var onM by remember { mutableIntStateOf(p.getInt("night_on_m", 0)) }
    var offH by remember { mutableIntStateOf(p.getInt("night_off_h", 7)) }
    var offM by remember { mutableIntStateOf(p.getInt("night_off_m", 0)) }

    PrefSwitch("night") { Scheduler.setNight(c) }
    OutlinedButton(onClick = {
        pickTime(c, onH, onM) { h, m ->
            onH = h; onM = m
            p.edit().putInt("night_on_h", h).putInt("night_on_m", m).apply()
            Scheduler.setNight(c)
        }
    }, Modifier.fillMaxWidth()) { Text("🌙 সাইলেন্ট শুরু: ${fmtTime(onH, onM)}") }
    OutlinedButton(onClick = {
        pickTime(c, offH, offM) { h, m ->
            offH = h; offM = m
            p.edit().putInt("night_off_h", h).putInt("night_off_m", m).apply()
            Scheduler.setNight(c)
        }
    }, Modifier.fillMaxWidth()) { Text("☀️ রিং আবার চালু: ${fmtTime(offH, offM)}") }
    Text("নোট: Android সাধারণ অ্যাপকে ডার্ক মোড বদলাতে দেয় না, তাই সেটা Pixel-এর নিজের \"Dark theme > Schedule\" থেকে চালু করো।",
        style = MaterialTheme.typography.bodySmall)
}

// ---------- 🔦 টাইল ----------

@Composable
fun TilesScreen() {
    val c = LocalContext.current
    Text("কীভাবে যোগ করবে:", fontWeight = FontWeight.Bold)
    Text("১. উপর থেকে দুইবার নিচে টেনে Quick Settings খোলো\n২. নিচের ✏️ পেন্সিল আইকনে চাপো\n৩. \"টর্চ\" আর \"স্ক্রিন টাইমআউট\" টাইল টেনে উপরে আনো")
    Text("স্ক্রিন টাইমআউট টাইলের জন্য ⚙️ সেটআপে \"সিস্টেম সেটিং বদলানো\" অনুমতি লাগবে।",
        style = MaterialTheme.typography.bodySmall)
    Button(onClick = { Torch.toggle(c) }, Modifier.fillMaxWidth()) { Text("🔦 এখনই টর্চ জ্বালাও/নেভাও") }
}

// ---------- 🛡️ পারমিশন চেকার ----------

@Composable
fun PermsScreen() {
    val c = LocalContext.current
    val apps by produceState<List<AppScan.AppPerm>?>(null) {
        value = withContext(Dispatchers.IO) { AppScan.scan(c) }
    }
    val list = apps
    if (list == null) {
        CircularProgressIndicator()
        return
    }
    Text("${list.size}টা অ্যাপ সংবেদনশীল অনুমতি পেয়েছে", fontWeight = FontWeight.Bold)
    list.forEach { a ->
        Card(Modifier.fillMaxWidth().clickable {
            c.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
        }) {
            Column(Modifier.padding(12.dp)) {
                Text(a.name, fontWeight = FontWeight.Bold)
                Text(a.perms.joinToString("  "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------- 📊 ফোন ইনফো ----------

@Composable
fun InfoScreen() {
    val c = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2000); tick++ } }
    val rows = remember(tick) { PhoneInfo.rows(c) }
    rows.forEach { (k, v) -> InfoRow(k, v) }
}

// ---------- ⏰ চার্জিং ঘড়ি ----------

@Composable
fun ClockScreen() {
    val c = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    DisposableEffect(Unit) {
        val w = (c as? Activity)?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val bm = c.getSystemService(BatteryManager::class.java)
    val level = remember(now) { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
    val charging = remember(now) { bm.isCharging }
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(SimpleDateFormat("h:mm", Locale.getDefault()).format(now), fontSize = 96.sp, fontWeight = FontWeight.Light)
        Text(SimpleDateFormat(":ss a", Locale.getDefault()).format(now), fontSize = 28.sp)
        Spacer(Modifier.height(16.dp))
        Text(SimpleDateFormat("EEEE, d MMMM", Locale("bn", "BD")).format(now), fontSize = 20.sp)
        Spacer(Modifier.height(32.dp))
        Text("${if (charging) "⚡" else "🔋"} $level%", fontSize = 48.sp, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = { level / 100f }, modifier = Modifier.fillMaxWidth(0.7f).padding(top = 12.dp))
    }
}

// ---------- 📶 Wi-Fi খাতা ----------

@Composable
fun WifiScreen() {
    val c = LocalContext.current
    val p = Prefs.p(c)
    fun load(): List<Pair<String, String>> {
        val a = JSONArray(p.getString("wifi", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("n") to o.getString("p") } }
    }
    fun save(l: List<Pair<String, String>>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("n", it.first).put("p", it.second)) }
        p.edit().putString("wifi", a.toString()).apply()
    }
    var list by remember { mutableStateOf(load()) }
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(setOf<Int>()) }

    OutlinedTextField(name, { name = it }, label = { Text("জায়গা / Wi-Fi নাম") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(pass, { pass = it }, label = { Text("পাসওয়ার্ড") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Button(onClick = {
        if (name.isNotBlank()) {
            list = list + (name.trim() to pass); save(list); name = ""; pass = ""
        }
    }, Modifier.fillMaxWidth()) { Text("➕ যোগ করো") }

    list.forEachIndexed { i, (n, pw) ->
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("📶 $n", fontWeight = FontWeight.Bold)
                    Text(if (i in shown) pw else "••••••••")
                }
                TextButton(onClick = { shown = if (i in shown) shown - i else shown + i }) {
                    Text(if (i in shown) "লুকাও" else "দেখাও")
                }
                TextButton(onClick = {
                    list = list.filterIndexed { j, _ -> j != i }; save(list); shown = emptySet()
                }) { Text("🗑️") }
            }
        }
    }
}

// ---------- 🧘 ফোন-ছাড়া চ্যালেঞ্জ ----------

@Composable
fun FocusScreen() {
    val c = LocalContext.current
    val p = Prefs.p(c)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var mins by remember { mutableFloatStateOf(15f) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val end = p.getLong("focus_end", 0)
            if (end in 1..now) { // সময় শেষ, আনলক করেনি → জয়
                val len = p.getInt("focus_len", 0)
                p.edit().putLong("focus_end", 0)
                    .putInt("focus_points", p.getInt("focus_points", 0) + len)
                    .putString("focus_result", "🎉 জিতেছো! +$len পয়েন্ট").apply()
            }
            delay(1000)
        }
    }
    val end = remember(now) { p.getLong("focus_end", 0) }
    val result = remember(now) { p.getString("focus_result", "") ?: "" }
    val points = remember(now) { p.getInt("focus_points", 0) }

    Text("🏆 মোট পয়েন্ট: $points", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    if (end > now) {
        val left = (end - now) / 1000
        Text(String.format(Locale.US, "%02d:%02d", left / 60, left % 60), fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Text("এখন ফোন লক করে রেখে দাও। সময় শেষের আগে আনলক করলে হেরে যাবে!")
        OutlinedButton(onClick = {
            p.edit().putLong("focus_end", 0).putString("focus_result", "🏳️ ছেড়ে দিলে").apply()
            now = System.currentTimeMillis()
        }) { Text("ছেড়ে দাও") }
    } else {
        Text("${mins.toInt()} মিনিট", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Slider(value = mins, onValueChange = { mins = it }, valueRange = 5f..120f, steps = 22)
        Button(onClick = {
            p.edit().putLong("focus_end", System.currentTimeMillis() + mins.toLong() * 60_000)
                .putInt("focus_len", mins.toInt()).putString("focus_result", "").apply()
            MonitorService.ensure(c)
            now = System.currentTimeMillis()
        }, Modifier.fillMaxWidth()) { Text("▶️ শুরু করো") }
    }
    if (result.isNotEmpty()) Text(result, fontSize = 18.sp)
}

// ---------- 📔 ডায়েরি ----------

@Composable
fun DiaryScreen() {
    val c = LocalContext.current
    val p = Prefs.p(c)
    val day = 86_400_000L
    val today = p.getInt(Prefs.dayKey(), 0)
    val comment = when {
        today == 0 -> "আজ এখনো আমাকে খোলোনি… নাকি ডায়েরি এইমাত্র চালু করলে? 🤔"
        today < 30 -> "বাহ! আজ তুমি বেশ সংযমী 😇"
        today < 80 -> "আজ তুমি আমাকে $today বার খুলেছো। একটু বিশ্রাম দাও 😮‍💨"
        else -> "$today বার!! আমার স্ক্রিন ক্ষয়ে যাচ্ছে 😩"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("📖 ফোনের ডায়েরি থেকে:", fontWeight = FontWeight.Bold)
            Text("\"$comment\"", fontSize = 18.sp)
        }
    }
    Text("গত ৭ দিন:", fontWeight = FontWeight.Bold)
    val fmt = SimpleDateFormat("EEE, d MMM", Locale("bn", "BD"))
    for (i in 0 until 7) {
        val t = System.currentTimeMillis() - i * day
        InfoRow(if (i == 0) "আজ" else fmt.format(Date(t)), "${p.getInt(Prefs.dayKey(t), 0)} বার")
    }
}

// ---------- 🚨 হারানো ফোন ----------

@Composable
fun LostSetupScreen() {
    val c = LocalContext.current
    val p = Prefs.p(c)
    var code by remember { mutableStateOf(p.getString("lost_code", "") ?: "") }
    OutlinedTextField(code, {
        code = it; p.edit().putString("lost_code", it.trim()).apply()
    }, label = { Text("গোপন কোড (কমপক্ষে ৪ অক্ষর)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("ফোন হারালে অন্য কারো ফোন থেকে তোমার নম্বরে এই কোডটা SMS করো। ফোন সাইলেন্টে থাকলেও পুরো ভলিউমে বাজবে।")
    Text("এমন কোড দাও যা সাধারণ মেসেজে আসে না, যেমন: খুঁজো৭৭৭",
        style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = {
        Notif.ring(c, Notif.ID_LOST, "🚨 পরীক্ষা", "চাপ দিয়ে থামাও", "lost")
    }, Modifier.fillMaxWidth()) { Text("🔊 পরীক্ষা করে দেখো") }
}

// ---------- 🧮 অঙ্কের অ্যালার্ম ----------

@Composable
fun MathAlarmScreen() {
    val c = LocalContext.current
    val p = Prefs.p(c)
    var h by remember { mutableIntStateOf(p.getInt("math_h", 7)) }
    var m by remember { mutableIntStateOf(p.getInt("math_m", 0)) }
    PrefSwitch("math_on", "প্রতিদিন বাজবে") { Scheduler.setMath(c) }
    OutlinedButton(onClick = {
        pickTime(c, h, m) { hh, mm ->
            h = hh; m = mm
            p.edit().putInt("math_h", hh).putInt("math_m", mm).apply()
            Scheduler.setMath(c)
        }
    }, Modifier.fillMaxWidth()) { Text("⏰ সময়: ${fmtTime(h, m)}", fontSize = 20.sp) }
    OutlinedButton(onClick = {
        Notif.ring(c, Notif.ID_MATH, "⏰ পরীক্ষা অ্যালার্ম", "অঙ্ক মিলিয়ে বন্ধ করো", "math")
    }, Modifier.fillMaxWidth()) { Text("🔊 এখনই পরীক্ষা করো") }
}

// ---------- 🗣️ ভয়েস ----------

@Composable
fun VoiceScreen() {
    val c = LocalContext.current
    var heard by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && text != null) {
            heard = text
            result = Voice.handle(c, text)
        }
    }
    Button(onClick = {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "বলো…")
        try { launcher.launch(i) } catch (_: ActivityNotFoundException) {
            result = "এই ফোনে ভয়েস রিকগনিশন পাওয়া যায়নি"
        }
    }, Modifier.fillMaxWidth().height(80.dp)) { Text("🎤 চাপ দিয়ে বলো", fontSize = 22.sp) }
    if (heard.isNotEmpty()) Text("শুনলাম: \"$heard\"")
    if (result.isNotEmpty()) Text(result, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("টিপ: বাংলা ভালো না বুঝলে Google অ্যাপের সেটিং থেকে বাংলা অফলাইন ভাষা ডাউনলোড করে নাও।",
        style = MaterialTheme.typography.bodySmall)
}
