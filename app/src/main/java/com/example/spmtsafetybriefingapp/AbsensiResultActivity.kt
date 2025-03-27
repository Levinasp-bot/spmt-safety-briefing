package com.example.spmtsafetybriefingapp

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.rememberAsyncImagePainter
import coil.compose.rememberImagePainter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text

class AbsensiResultActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val briefingId = intent.getStringExtra("briefingId") ?: ""

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            AbsensiResultScreen(briefingId, fusedLocationClient)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun AbsensiResultScreen(briefingId: String, fusedLocationClient: FusedLocationProviderClient) {
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    var userName by remember { mutableStateOf("User") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var timestamp by remember { mutableStateOf(0L) }
    var location by remember { mutableStateOf("Not Available") }

    LaunchedEffect(briefingId) {
        Log.d("AbsensiResult", "Fetching data for briefingId: $briefingId")
        if (briefingId.isNotEmpty()) {
            try {
                val attendanceRef = firestore.collection("agenda")
                    .document(briefingId)
                    .collection("attendance")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                if (!attendanceRef.isEmpty) {
                    val latestAttendance = attendanceRef.documents[0]
                    userName = latestAttendance.getString("userName") ?: "Unknown"
                    photoUri = latestAttendance.getString("photoUri")?.toUri()
                    val firestoreTimestamp = latestAttendance.getTimestamp("timestamp")
                    timestamp = firestoreTimestamp?.toDate()?.time ?: 0L
                    Log.d("AbsensiResult", "Fetched Data - Name: $userName, Timestamp: $timestamp, Location: $location")
                } else {
                    Log.w("AbsensiResult", "No attendance data found")
                }
            } catch (e: Exception) {
                Log.e("AbsensiResult", "Error fetching attendance data", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
            if (loc != null) {
                location = "${loc.latitude}, ${loc.longitude}"
                Log.d("AbsensiResult", "User Location: $location")
            } else {
                Log.w("AbsensiResult", "Failed to get user location")
            }
        }
    }

    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Jakarta")
    }
    val formattedDate = if (timestamp > 0) dateFormat.format(Date(timestamp)) else "Unknown"

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Absensi Berhasil!",
                fontSize = 24.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            photoUri?.let {
                Image(
                    painter = rememberAsyncImagePainter(it),
                    contentDescription = "Hasil Foto Absensi",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(text = "Nama: $userName", fontSize = 18.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = formattedDate, fontSize = 16.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Lokasi: $location", fontSize = 16.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val intent = Intent(context, HomeActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7))
            ) {
                Text(text = "Selesai", fontSize = 18.sp, color = Color.White)
            }
        }
    }
}
