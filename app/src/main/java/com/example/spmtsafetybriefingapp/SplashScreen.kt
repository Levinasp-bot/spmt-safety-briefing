package com.example.spmtsafetybriefingapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.example.spmtsafetybriefingapp.ui.theme.SPMTSafetyBriefingAppTheme
import kotlinx.coroutines.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

class SplashActivity : ComponentActivity() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val currentVersion = BuildConfig.VERSION_NAME // 🔹 Ambil versi dari build.gradle

    private val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION // 🔹 Lokasi tetap diminta, tapi opsional
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES) // 🔥 Wajib untuk lokasi berbasis WiFi/Bluetooth di Android 13+
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE) // 🔹 Opsional untuk Android < 10
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES) // 🔹 Opsional untuk Android 13+
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE) // 🔹 Opsional untuk Android 12 ke bawah
        }
    }.toTypedArray()

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.NEARBY_WIFI_DEVICES] == true

        val storageGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_MEDIA_IMAGES] == true

        checkForUpdate()

        if (!locationGranted) {
            Toast.makeText(this, "Izin lokasi tidak diberikan, beberapa fitur mungkin terbatas", Toast.LENGTH_SHORT).show()
        }

        if (!storageGranted) {
            Toast.makeText(this, "Izin penyimpanan tidak diberikan, beberapa fitur mungkin terbatas", Toast.LENGTH_SHORT).show()
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SPMTSafetyBriefingAppTheme(
                darkTheme = false,      // ✅ Paksa tema terang
                dynamicColor = false    // ✅ Nonaktifkan warna dinamis
            ) {
                SplashScreen()
            }
        }
        checkPermissions()
    }
    private fun checkPermissions() {
        val deniedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isEmpty()) {
            checkForUpdate()
        } else {
            permissionRequest.launch(deniedPermissions.toTypedArray())
        }
    }
    private fun checkForUpdate() {
        val actualVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }

        Log.d("UpdateCheck", "BuildConfig Version: $currentVersion") // 🔹 Cek versi dari BuildConfig
        Log.d("UpdateCheck", "PackageManager Version: $actualVersion") // 🔹 Cek versi dari PackageManager

        firestore.collection("config").document("version").get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val latestVersion = document.getString("latestVersion")
                    val downloadUrl = document.getString("downloadUrl")

                    if (latestVersion != null && downloadUrl != null) {
                        if (isUpdateRequired(actualVersion, latestVersion)) {
                            Log.d("UpdateCheck", "Update Required!")
                            forceUpdate(downloadUrl)
                        } else {
                            Log.d("UpdateCheck", "App is up to date")
                            goToMainActivity()
                        }
                    } else {
                        Log.e("UpdateCheck", "Failed to fetch update info")
                        Toast.makeText(this, "Gagal mendapatkan informasi pembaruan", Toast.LENGTH_LONG).show()
                        goToMainActivity()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("UpdateCheck", "Error checking update: ${e.message}")
                Toast.makeText(this, "Gagal memeriksa pembaruan", Toast.LENGTH_LONG).show()
                goToMainActivity()
            }
    }

    private fun isUpdateRequired(current: String, latest: String): Boolean {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val currentPart = currentParts.getOrElse(i) { 0 }
            val latestPart = latestParts.getOrElse(i) { 0 }

            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }

    private fun forceUpdate(downloadUrl: String) {
        Toast.makeText(this, "Aplikasi perlu diperbarui!", Toast.LENGTH_LONG).show()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
        startActivity(intent)
        finish()
    }

    private fun goToMainActivity() {
        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_pelindo),
            contentDescription = "Logo",
            modifier = Modifier.size(200.dp)
        )
    }
}
