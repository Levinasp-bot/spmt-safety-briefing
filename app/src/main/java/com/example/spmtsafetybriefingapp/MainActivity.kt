package com.example.spmtsafetybriefingapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false)

        // Jika sudah login, langsung pindah ke HomeActivity
        if (isLoggedIn) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish() // Menutup MainActivity agar tidak bisa kembali ke login
            return
        }

        setContent {
            val navController = rememberNavController()
            NavHost(navController, startDestination = "login_register") {
                composable("login_register") { LoginRegisterScreen(sharedPreferences) }
                composable("login") { /* Halaman Login */ }
                composable("register") { /* Halaman Register */ }
                composable("forgot_password") { /* Halaman Lupa Password */ }
                composable("home") { /* Halaman Beranda Setelah Login */ }
            }
        }
    }
}
