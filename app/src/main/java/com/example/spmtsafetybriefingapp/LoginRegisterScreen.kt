package com.example.spmtsafetybriefingapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginRegisterScreen(sharedPreferences: SharedPreferences) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_spmt),
            contentDescription = "Pelindo Logo",
            modifier = Modifier
                .width(200.dp)
                .height(100.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { context.startActivity(Intent(context, RegisterActivity::class.java)) },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(8.dp),
            border = ButtonDefaults.outlinedButtonBorder
        ) {
            Text(text = "Daftar", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { context.startActivity(Intent(context, LoginActivity::class.java)) },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0076C0)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "Masuk", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { /* Arahkan ke Forgot Password */ }) {
            Text(
                text = "Lupa password?",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0076C0),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginRegisterScreenPreview() {
    val fakePreferences = object : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getStringSet(key: String?, defValue: MutableSet<String>?): MutableSet<String>? =
            data[key] as? MutableSet<String> ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun getAll(): MutableMap<String, *> = data
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { data[key ?: ""] = value; return this }
            override fun putString(key: String?, value: String?): SharedPreferences.Editor { data[key ?: ""] = value; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { data[key ?: ""] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { data[key ?: ""] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { data[key ?: ""] = value; return this }
            override fun putStringSet(key: String?, value: MutableSet<String>?): SharedPreferences.Editor { data[key ?: ""] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { data.remove(key); return this }
            override fun clear(): SharedPreferences.Editor { data.clear(); return this }
            override fun commit(): Boolean = true
            override fun apply() {}
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    LoginRegisterScreen(sharedPreferences = fakePreferences)
}
