package com.amar.toolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** অঙ্ক মিলিয়ে অ্যালার্ম বন্ধ, বা হারানো ফোনের অ্যালার্ম থামানো। */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val mode = intent.getStringExtra("mode") ?: "math"
        val nid = intent.getIntExtra("nid", Notif.ID_MATH)

        if (mode == "math") {
            // ব্যাক চেপে পালানো যাবে না 😄
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {}
            })
        }
        val done = { Notif.nm(this).cancel(nid); finish() }

        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (mode == "math") MathScreen(done) else LostScreen(done)
                }
            }
        }
    }
}

@Composable
private fun MathScreen(done: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val a = remember(round) { Random.nextInt(11, 30) }
    val b = remember(round) { Random.nextInt(3, 10) }
    val d = remember(round) { Random.nextInt(10, 90) }
    var answer by remember { mutableStateOf("") }
    var solved by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⏰", fontSize = 64.sp)
        Text("ঘুম ভাঙাও! ৩টা অঙ্ক মেলাও", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("মিলেছে: $solved / 3", Modifier.padding(8.dp))
        Spacer(Modifier.height(16.dp))
        Text("$a × $b + $d = ?", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it.filter(Char::isDigit) },
            label = { Text("উত্তর") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            if (answer.toIntOrNull() == a * b + d) {
                solved++
                answer = ""
                msg = "✅ ঠিক!"
                if (solved >= 3) done() else round++
            } else {
                msg = "❌ ভুল, আবার চেষ্টা করো"
                answer = ""
            }
        }) { Text("মিলিয়ে দেখো", fontSize = 18.sp) }
        Text(msg, Modifier.padding(12.dp))
    }
}

@Composable
private fun LostScreen(done: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🚨", fontSize = 80.sp)
        Text("আমি এখানে!", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Button(onClick = done) { Text("পেয়েছি, থামাও", fontSize = 20.sp) }
    }
}
