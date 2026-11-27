@file:OptIn(ExperimentalMaterial3Api::class)

package com.amar.toolkit

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notif.init(this)
        MonitorService.ensure(this)
        setContent { AppTheme { App() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(c)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(c)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** toggleKey থাকলে কার্ডে চালু/বন্ধ দেখায়। */
data class Feature(
    val id: String,
    val emoji: String,
    val title: String,
    val desc: String,
    val toggleKey: String? = null,
    val need: String? = null
)

val FEATURES = listOf(
    Feature("setup", "⚙️", "অনুমতি সেটআপ", "অনেক ফিচারের জন্য কিছু অনুমতি লাগে। এখান থেকে সব একসাথে দিয়ে দাও।"),
    Feature("modes", "🎚️", "এক ট্যাপে মোড", "ক্লাস, ঘুম, গেম বা স্বাভাবিক মোড। এক ট্যাপে রিংটোন, ভলিউম আর ব্রাইটনেস বদলাবে। নিজের প্রোফাইলও সেভ করা যায়।"),
    Feature("night", "🌙", "রাতের অটো সাইলেন্ট", "ঠিক করা সময়ে ফোন নিজে থেকে সাইলেন্ট হবে, সকালে আবার রিং চালু।", need = "পুরো সাইলেন্টের জন্য Do Not Disturb অনুমতি"),
    Feature("tiles", "🔦", "Quick Settings টাইল", "নোটিফিকেশন বারে দুটো নতুন বাটন: টর্চ আর স্ক্রিন টাইমআউট (৩০ সেকেন্ড / ২ মিনিট / ১০ মিনিট)।"),
    Feature("charge80", "🔋", "৮০% চার্জ অ্যালার্ট", "চার্জে থাকা অবস্থায় ৮০% হলেই জানাবে, যাতে ব্যাটারি অনেকদিন ভালো থাকে।", "charge80", "নোটিফিকেশন"),
    Feature("low", "🪫", "ব্যাটারি সেভার প্লাস", "চার্জ ২০%-এর নিচে নামলে ব্রাইটনেস কমিয়ে দেবে আর জানাবে।", "low", "নোটিফিকেশন, সিস্টেম সেটিং বদলানো"),
    Feature("angry", "😤", "রাগী ব্যাটারি", "চার্জ ২০%-এর নিচে নামলে ফোন নিজেই কথা বলে অভিমান করবে!", "angry"),
    Feature("heat", "🌡️", "গরম হলে সতর্কতা", "ব্যাটারি ৪২°C ছাড়ালে সতর্ক করবে।", "heat", "নোটিফিকেশন"),
    Feature("flip", "🙃", "উল্টালে সাইলেন্ট", "টেবিলে স্ক্রিন নিচে করে রাখলে ফোন ভাইব্রেটে যাবে, সোজা করলে আবার রিং।", "flip"),
    Feature("shake", "📳", "ঝাঁকালে টর্চ", "ফোন জোরে দুইবার ঝাঁকালে টর্চ জ্বলবে বা নিভবে।", "shake"),
    Feature("callflash", "💡", "কল এলে ফ্ল্যাশ", "কল এলে ক্যামেরার ফ্ল্যাশ জ্বলবে-নিভবে, সাইলেন্টে থাকলেও বুঝবে।", "callflash", "ফোন কলের অবস্থা"),
    Feature("headphone", "🎧", "হেডফোন লাগালে ভলিউম", "তারের হেডফোন লাগালেই মিউজিক ভলিউম ৪০%-এ সেট হবে, কান বাঁচবে।", "headphone"),
    Feature("perms", "🛡️", "পারমিশন চেকার", "কোন অ্যাপ ক্যামেরা, মাইক, লোকেশন, কন্ট্যাক্ট বা SMS দেখতে পারে। অ্যাপে চাপ দিলে তার সেটিং খুলবে।"),
    Feature("info", "📊", "ফোন ইনফো", "ব্যাটারি, তাপমাত্রা, স্টোরেজ, RAM সব এক জায়গায়, প্রতি ২ সেকেন্ডে আপডেট।"),
    Feature("clock", "⏰", "চার্জিং ঘড়ি", "চার্জে রেখে বড় ঘড়ি আর চার্জের শতাংশ দেখো। এই স্ক্রিনে থাকলে ফোন ঘুমাবে না।"),
    Feature("wifi", "📶", "Wi-Fi পাসওয়ার্ড খাতা", "কোন জায়গার কোন Wi-Fi আর তার পাসওয়ার্ড লিখে রাখো। শুধু তোমার ফোনেই থাকবে।"),
    Feature("focus", "🧘", "ফোন-ছাড়া চ্যালেঞ্জ", "সময় ঠিক করো। সেই সময়ের মধ্যে ফোন আনলক না করলে পয়েন্ট পাবে!"),
    Feature("diary", "📔", "ফোনের ডায়েরি", "ফোন নিজেই হিসাব রাখবে তুমি দিনে কতবার তাকে আনলক করো 😩", "diary"),
    Feature("lost", "🚨", "হারানো ফোন অ্যালার্ম", "অন্য ফোন থেকে গোপন কোড লিখে SMS পাঠালে তোমার ফোন সাইলেন্টেও জোরে বাজবে।", "lost", "SMS গ্রহণ, ফুল-স্ক্রিন অ্যালার্ম"),
    Feature("mathalarm", "🧮", "অঙ্কের অ্যালার্ম", "প্রতিদিনের অ্যালার্ম। বন্ধ করতে হলে ৩টা অঙ্ক মেলাতে হবে, ঘুম পুরো ভাঙবে!", need = "নোটিফিকেশন, ফুল-স্ক্রিন অ্যালার্ম"),
    Feature("voice", "🗣️", "বাংলা ভয়েস কমান্ড", "মাইকে চাপ দিয়ে বলো: \"চুপ\", \"রিং বাজাও\", \"আলো জ্বালাও\", \"আলো বন্ধ\", \"উজ্জ্বল\", \"অন্ধকার\", \"ব্যাটারি\", \"কয়টা বাজে\"।"),
)

@Composable
fun App() {
    var screen by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = screen != null) { screen = null }
    val f = FEATURES.find { it.id == screen }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(f?.let { "${it.emoji} ${it.title}" } ?: "⚡ আমার টুলকিট") },
                navigationIcon = {
                    if (f != null) TextButton(onClick = { screen = null }) { Text("←", fontSize = 24.sp) }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (f == null) Home { screen = it } else FeatureScreen(f)
        }
    }
}

@Composable
fun Home(open: (String) -> Unit) {
    val c = LocalContext.current
    // ফিরে এলে কার্ডের চালু/বন্ধ লেখা নতুন করে পড়ার জন্য
    val p = Prefs.p(c)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Text(
                "${FEATURES.size}টা টুল, সব তোমার Pixel-এর জন্য। প্রথমে ⚙️ অনুমতি সেটআপ করে নাও।",
                Modifier.padding(4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        items(FEATURES, key = { it.id }) { f ->
            Card(onClick = { open(f.id) }, modifier = Modifier.fillMaxWidth().height(130.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(f.emoji, fontSize = 32.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(f.title, fontWeight = FontWeight.Bold, maxLines = 2)
                    f.toggleKey?.let {
                        val on = p.getBoolean(it, false)
                        Text(
                            if (on) "● চালু" else "○ বন্ধ",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
