package com.example.spmtsafetybriefingapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import coil.compose.rememberImagePainter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class DetailSafetyBriefingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val briefingId = intent.getStringExtra("briefingId")

        if (briefingId == null) {
            Log.e("DetailActivity", "ID briefing tidak ditemukan!")
            finish()
            return
        }

        Log.d("DetailActivity", "Menerima briefingId: $briefingId")

        setContent {
            DetailSafetyBriefingScreen(briefingId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSafetyBriefingScreen(briefingId: String) {
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    var briefingData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var jumlahHadir by remember { mutableStateOf(0) }
    var jumlahSakit by remember { mutableStateOf(0) }
    var jumlahCuti by remember { mutableStateOf(0) }
    var jumlahIzin by remember { mutableStateOf(0) }
    var tanpaKeterangan by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    var listenerRegistration by remember { mutableStateOf<ListenerRegistration?>(null) }

    DisposableEffect(briefingId) {
        listenerRegistration = firestore.collection("agenda").document(briefingId)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    Log.e("FirestoreError", "Gagal memuat data", error)
                    return@addSnapshotListener
                }
                if (document != null && document.exists()) {
                    briefingData = document.data
                    jumlahSakit = (document["sakit"] as? List<*>)?.size ?: 0
                    jumlahCuti = (document["cuti"] as? List<*>)?.size ?: 0
                    jumlahIzin = (document["izin"] as? List<*>)?.size ?: 0

                    val selectedTerminal = document.getString("terminal") ?: ""
                    val selectedGroup = document.getString("group") ?: ""

                    Log.d("FirestoreData", "Terminal: $selectedTerminal, Group: $selectedGroup")

                    firestore.collection("users")
                        .whereEqualTo("terminal", selectedTerminal)
                        .whereEqualTo("group", selectedGroup)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            val totalPekerja = snapshot.size() // Jumlah total pekerja sesuai terminal & group

                            // 🔹 Ambil jumlah hadir dari subkoleksi attendance
                            firestore.collection("agenda").document(briefingId).collection("attendance")
                                .get()
                                .addOnSuccessListener { attendanceSnapshot ->
                                    jumlahHadir = attendanceSnapshot.size()

                                    tanpaKeterangan = totalPekerja - (jumlahHadir + jumlahIzin + jumlahSakit + jumlahCuti)
                                    Log.d("totalPekerja", "Jumlah: $totalPekerja")
                                }
                                .addOnFailureListener { error ->
                                    Log.e("FirestoreError", "Gagal mengambil jumlah hadir", error)
                                }
                        }
                        .addOnFailureListener { error ->
                            Log.e("FirestoreError", "Gagal mengambil daftar pekerja", error)
                        }
                }
            }

        onDispose {
            listenerRegistration?.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan Safety Briefing", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            var perwiraBriefing by remember { mutableStateOf("Tidak diketahui") }

            briefingData?.let { data ->
                val terminal = data["terminal"] as? String ?: "Tidak diketahui"
                val tempat = data["tempat"] as? String ?: "Tidak diketahui"

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document("Manager")
                    .get()
                    .addOnSuccessListener { document ->
                        val nama = document.getString(terminal)
                        if (!nama.isNullOrEmpty()) {
                            perwiraBriefing = nama
                        }
                    }
                    .addOnFailureListener {
                        Log.e("Firestore", "Gagal mengambil nama perwira dari Manager", it)
                    }

            CardSection("Informasi Briefing") {
                    DetailItem("Tempat", tempat)
                    DetailItem("Shift", data["shift"] as? String ?: "Tidak diketahui")
                    DetailItem("Perwira Briefing", perwiraBriefing)
                    DetailItem("Koordinator Briefing", data["koordinator"] as? String ?: "Tidak diketahui")
                }

            CardSection("Agenda Briefing") {
                    val agendaList = (data["agenda"] as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
                    if (agendaList.isNotEmpty()) {
                        agendaList.forEachIndexed { index, agenda ->
                            Text(text = "${index + 1}. $agenda", fontSize = 14.sp)
                        }
                    } else {
                        Text("Tidak ada agenda", fontSize = 14.sp, fontWeight = FontWeight.Light)
                    }
                }

                CardSection("Agenda Serah Terima") {
                    val serahTerimaList = (data["serahTerima"] as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
                    if (serahTerimaList.isNotEmpty()) {
                        serahTerimaList.forEachIndexed { index, item ->
                            Text(text = "${index + 1}. $item", fontSize = 14.sp)
                        }
                    } else {
                        Text("Belum ada agenda serah terima", fontSize = 14.sp, fontWeight = FontWeight.Light)
                    }
                }

                CardSection("Jumlah Pekerja (Orang)") {
                    DetailItem("Hadir", jumlahHadir.toString())
                    DetailItem("Sakit", jumlahSakit.toString())
                    DetailItem("Cuti", jumlahCuti.toString())
                    DetailItem("Izin", jumlahIzin.toString())
                    DetailItem("Tanpa Keterangan", tanpaKeterangan.toString())
                }

                CardSection("Foto Dokumentasi") {
                    val fotoUrl = data["photoPath"] as? String
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!fotoUrl.isNullOrEmpty()) {
                            Image(
                                painter = rememberImagePainter(fotoUrl),
                                contentDescription = "Foto Dokumentasi",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_background),
                                contentDescription = "Foto Placeholder",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, EditFormActivity::class.java)
                        intent.putExtra("briefingId", briefingId)
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0E73A7)
                    ),
                    shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp)),
                    border = BorderStroke(1.dp, Color(0xFF0E73A7))
                ) {
                    Text("Edit Agenda", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp)) // Spacer antar tombol

                Button(
                    onClick = {
                        val intent = Intent(context, UnduhPdfActivity::class.java)
                        intent.putExtra("briefingId", briefingId)
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
                    shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
                ) {
                    Text("Unduh PDF", color = Color.White, fontSize = 16.sp)
                }
            } ?: run {
                Text(text = "Memuat data...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


@Composable
fun DetailItem(title: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CardSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E73A7))
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
