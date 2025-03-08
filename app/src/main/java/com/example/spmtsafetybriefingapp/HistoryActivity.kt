package com.example.spmtsafetybriefingapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController() // 🔹 Buat NavController
            HistoryScreen(navController) // 🔹 Kirim ke HistoryScreen
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val agendaList = remember { mutableStateListOf<Agenda>() }
    val firestore = FirebaseFirestore.getInstance()

    firestore.collection("agenda")
        .whereEqualTo("status", "selesai")
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("Firestore", "Error fetching data", error)
                return@addSnapshotListener
            }

            agendaList.clear()
            Log.d("Firestore", "Total agenda ditemukan: ${snapshot?.documents?.size}")

            snapshot?.documents?.forEach { doc ->
                val briefingId = doc.id
                val terminal = doc.getString("terminal") ?: "Tidak diketahui"
                val shift = doc.getString("shift") ?: "Tidak diketahui"
                val timestamp = doc.getTimestamp("timestamp")

                Log.d("Firestore", "Agenda ditemukan -> ID: $briefingId, Terminal: $terminal, Shift: $shift, Timestamp: $timestamp")

                val agenda = Agenda(briefingId, terminal, shift, timestamp)
                agendaList.add(agenda)
            }
        }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = "Riwayat",
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* Handle menu click */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )

            // 🔹 Daftar Riwayat
            if (agendaList.isEmpty()) {
                Text(
                    text = "Belum ada data absensi",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(agendaList) { index, agenda ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable {
                                    val intent = Intent(context, DetailSafetyBriefingActivity::class.java)
                                    intent.putExtra("briefingId", agenda.briefingId)
                                    context.startActivity(intent)
                                }
                                .border(1.dp, Color(0xFF0E73A7), shape = RoundedCornerShape(8.dp)), // 🔹 Stroke biru
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White) // 🔹 Warna putih
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Safety Briefing ${agenda.terminal}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = agenda.timestamp?.toDate()?.let { formatTimestamp(it) } ?: "Unknown",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${agenda.shift}",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


fun formatTimestamp(date: Date): String {
    val format = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return format.format(date)
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val context = LocalContext.current // 🔹 Ambil Context dengan LocalContext.current
    var selectedItem by remember { mutableStateOf(0) }

    NavigationBar(containerColor = Color(0xFF0E73A7)) {
        NavigationBarItem(
            icon = {
                Icon(
                    painterResource(id = if (selectedItem == 0) R.drawable.home_stroke else R.drawable.home_filled),
                    contentDescription = "Beranda"
                )
            },
            label = { Text("Beranda", fontSize = 12.sp, color = Color.White) },
            selected = selectedItem == 0,
            onClick = {
                selectedItem = 0
                context.startActivity(Intent(context, HomeActivity::class.java)) // 🔹 Navigasi ke HomeActivity
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                unselectedIconColor = Color.White,
                selectedTextColor = Color.White,
                unselectedTextColor = Color.White,
                indicatorColor = Color.Transparent
            )
        )

        NavigationBarItem(
            icon = {
                Icon(
                    painterResource(id = if (selectedItem == 1) R.drawable.history_stroke else R.drawable.history_filled),
                    contentDescription = "Riwayat"
                )
            },
            label = { Text("Riwayat", fontSize = 12.sp, color = Color.White) },
            selected = selectedItem == 1,
            onClick = {
                selectedItem = 1
                context.startActivity(Intent(context, HistoryActivity::class.java)) // 🔹 Navigasi ke HistoryActivity
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                unselectedIconColor = Color.White,
                selectedTextColor = Color.White,
                unselectedTextColor = Color.White,
                indicatorColor = Color.Transparent
            )
        )
    }
}

data class Agenda(
    val briefingId: String = "",
    val terminal: String = "Tidak diketahui",
    val shift: String = "Tidak diketahui",
    val timestamp: Timestamp? = null
) {
    fun getFormattedTime(): String {
        return timestamp?.toDate()?.let { date ->
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            sdf.format(date)
        } ?: "Tidak diketahui"
    }
}


