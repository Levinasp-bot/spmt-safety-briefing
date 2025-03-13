package com.example.spmtsafetybriefingapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.Interpreter
import java.text.SimpleDateFormat
import java.util.Locale

class DashboardActivity : ComponentActivity() {
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestore = FirebaseFirestore.getInstance() // Inisialisasi Firestore

        setContent {
            AttendanceDashboard(firestore) // Kirim Firestore ke fungsi AttendanceDashboard
        }
    }
}


    @Composable
    fun AttendanceDashboard(firestore: FirebaseFirestore) {
        var attendanceList by remember { mutableStateOf(listOf<Pair<String, String>>()) }
        var selectedTerminal by remember { mutableStateOf("Semua") }
        val terminalOptions = listOf("Semua", "Terminal Jamrud", "Terminal Nilam", "Terminal Mirah")

        var totalUsers by remember { mutableStateOf(0) }  // Total user di terminal
        var absentUsers by remember { mutableStateOf(0) } // Total user yang belum absen
        var presentUsers by remember { mutableStateOf(0) } // Jumlah user yang sudah absen

        LaunchedEffect(selectedTerminal) {
            try {
                Log.d("Firestore", "Fetching active agendas for terminal: $selectedTerminal")

                val query = if (selectedTerminal == "Semua") {
                    firestore.collection("agenda").whereEqualTo("status", "aktif")
                } else {
                    firestore.collection("agenda")
                        .whereEqualTo("status", "aktif")
                        .whereEqualTo("terminal", selectedTerminal)
                }

                val activeAgendas = query.get().await()
                Log.d("Firestore", "Total Active Agendas: ${activeAgendas.size()}")

                val allAttendances = mutableListOf<Pair<String, String>>()
                val attendedUsers = mutableSetOf<String>()
                val validRoles = listOf(
                    "Anggota Pengamanan", "Komandan Peleton",
                    "Chief Foreman", "Foreman", "Dispatcher"
                )

                var userCount = 0 // Untuk menghitung totalUsers

                for (agenda in activeAgendas) {
                    val briefingId = agenda.id
                    val group = agenda.getString("group") ?: ""

                    Log.d("Firestore", "Agenda $briefingId -> Group: $group")

                    val attendanceRef = firestore.collection("agenda")
                        .document(briefingId)
                        .collection("attendance")
                        .get()
                        .await()

                    val attendances = attendanceRef.documents.mapNotNull { doc ->
                        val name = doc.getString("userName") ?: "Unknown"
                        attendedUsers.add(name)
                        val timestamp = doc.getTimestamp("timestamp")?.toDate()?.let {
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
                        } ?: "-"
                        name to timestamp
                    }
                    allAttendances.addAll(attendances)

                    // 🔹 Ambil total user yang sesuai dengan filter role dan group
                    val userQuery = if (selectedTerminal == "Semua") {
                        firestore.collection("users").whereIn("role", validRoles).get().await()
                    } else {
                        firestore.collection("users")
                            .whereEqualTo("terminal", selectedTerminal)
                            .whereIn("role", validRoles)
                            .get()
                            .await()
                    }

                    val filteredUsers = userQuery.documents.filter { doc ->
                        val role = doc.getString("role") ?: ""
                        val userGroup = doc.getString("group") ?: ""

                        when (role) {
                            "Anggota Pengamanan", "Komandan Peleton", "Koordinator Operasi Jamrud", "Koordinator Operasi Nilam", "Koordinator Operasi Mirah",
                            "Chief Foreman", "Foreman", "Dispatcher" -> userGroup == group

                            else -> false
                        }
                    }

                    userCount += filteredUsers.size // Tambahkan jumlah user dari agenda ini
                }

                attendanceList = allAttendances
                presentUsers = attendedUsers.size // Simpan jumlah user yang sudah absen
                totalUsers = userCount // Total semua user yang valid
                absentUsers = totalUsers - presentUsers

                Log.d(
                    "Firestore",
                    "Total Users: $totalUsers, Present Users: $presentUsers, Absent Users: $absentUsers"
                )
            } catch (e: Exception) {
                Log.e("Firestore", "Error fetching attendance: ${e.message}")
                e.printStackTrace()
            }
        }


        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Dashboard Absensi",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier
                            .width(115.dp)
                            .height(35.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = selectedTerminal, fontSize = 14.sp, color = Color.Gray)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown Icon",
                                tint = Color.Gray
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.width(119.dp)
                    ) {
                        terminalOptions.forEach { terminal ->
                            DropdownMenuItem(
                                text = { Text(text = terminal, fontSize = 14.sp) },
                                onClick = {
                                    selectedTerminal = terminal
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(50.dp)
                        .background(Color(0xFF4CAF50), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Sudah Absen", color = Color.White, fontSize = 12.sp)
                        Text(
                            text = "$presentUsers",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(50.dp)
                        .background(Color(0xFFD32F2F), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Belum Absen", color = Color.White, fontSize = 12.sp)
                        Text(
                            text = "$absentUsers",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (attendanceList.isEmpty()) {
                Text(
                    text = "Belum ada data absensi",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                // 🔹 Pastikan attendanceList memiliki struktur data yang benar
                val sortedAttendanceList =
                    attendanceList.sortedBy { it.second } // Jika berbentuk Pair<String, String>

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // 🔹 Tabel Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0E73A7))
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "No",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(0.3f).padding(start = 8.dp)
                            )
                            Text(
                                text = "Nama",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )
                            Text(
                                text = "Waktu Kehadiran",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                        }
                    }

                    itemsIndexed(sortedAttendanceList) { index, (name, timestamp) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${index + 1}", // 🔹 Nomor urut otomatis
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(0.3f).padding(start = 8.dp)
                                )
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                                )
                                Text(
                                    text = timestamp,
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                            }
                            Divider(color = Color.LightGray, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }