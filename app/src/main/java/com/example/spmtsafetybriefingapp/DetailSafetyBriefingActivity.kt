package com.example.spmtsafetybriefingapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            finish() // Tutup aktivitas jika ID tidak ada
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
    var briefingData by remember { mutableStateOf<Map<String, Any>?>(null) }
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
                    Log.d("FirestoreData", "Data yang diterima: ${document.data}")
                }
            }

        onDispose {
            listenerRegistration?.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan Safety Briefing", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
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
            briefingData?.let { data ->
                val terminal = data["terminal"] as? String ?: "Tidak diketahui"
                val shift = data["shift"] as? String ?: "Tidak diketahui"
                val perwira = data["perwira_briefing"] as? String ?: "Tidak diketahui"
                val koordinator = data["koordinator"] as? String ?: "Tidak diketahui"

                // Menyesuaikan format agenda sebagai list dari Firestore
                val agendaList = when (val agenda = data["agenda"]) {
                    is List<*> -> agenda.mapNotNull { it.toString() } // Pastikan bisa dikonversi ke String
                    else -> emptyList()
                }

                val fotoUrl = data["photoPath"] as? String

                // Menampilkan detail briefing
                DetailItem(title = "Terminal", value = terminal)
                DetailItem(title = "Shift", value = shift)
                DetailItem(title = "Perwira Briefing", value = perwira)
                DetailItem(title = "Koordinator Briefing", value = koordinator)

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Agenda Briefing", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (agendaList.isNotEmpty()) {
                    agendaList.forEachIndexed { index, agenda ->
                        Text(text = "${index + 1}. $agenda", fontSize = 14.sp)
                    }
            } else {
                    Text(text = "Tidak ada agenda", fontSize = 14.sp, fontWeight = FontWeight.Light)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Jumlah pekerja
                Text(text = "Jumlah Pekerja (orang)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                DetailItem(title = "Hadir", value = data["jumlah_hadir"]?.toString() ?: "0")
                DetailItem(title = "Sakit", value = data["jumlah_sakit"]?.toString() ?: "0")
                DetailItem(title = "Cuti", value = data["jumlah_cuti"]?.toString() ?: "0")

                Spacer(modifier = Modifier.height(16.dp))

                // Foto Absensi
                Text(text = "Foto Dokumentasi", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(24.dp))

                // Tombol Unduh PDF
                Button(
                    onClick = { /* Aksi Unduh PDF */ },
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
    Column {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
    }
}
