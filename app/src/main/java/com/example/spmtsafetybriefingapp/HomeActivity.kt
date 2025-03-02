package com.example.spmtsafetybriefingapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeScreen {
                val intent = Intent(this, FormSafetyBriefingActivity::class.java)
                startActivity(intent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onAddBriefingClick: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    var userName by remember { mutableStateOf("User") }
    var activeAgenda by remember { mutableStateOf<Map<String, Any>?>(null) }
    val scaffoldState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // 🔹 Mengambil nama pengguna
    LaunchedEffect(auth.currentUser?.uid) {
        auth.currentUser?.uid?.let { uid ->
            try {
                val document = firestore.collection("users").document(uid).get().await()
                userName = document.getString("name") ?: "User"
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 🔹 Mengambil agenda dengan status "aktif"
    LaunchedEffect(Unit) {
        try {
            val querySnapshot = firestore.collection("agenda")
                .whereEqualTo("status", "aktif")
                .limit(1)
                .get()
                .await()

            if (!querySnapshot.isEmpty) {
                activeAgenda = querySnapshot.documents[0].data?.toMutableMap()
                (activeAgenda as MutableMap<String, Any>?)?.put("id", querySnapshot.documents[0].id) // Simpan ID dokumen
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    ModalNavigationDrawer(
        drawerState = scaffoldState,
        drawerContent = {
            DrawerContent(onCloseDrawer = { coroutineScope.launch { scaffoldState.close() } })
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { scaffoldState.open() } }) {
                            Icon(
                                painter = painterResource(id = R.drawable.menu),
                                contentDescription = "Menu",
                                tint = Color.Black
                            )
                        }
                    },
                    actions = {
                        if (activeAgenda == null) {
                            IconButton(onClick = onAddBriefingClick) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_action_name),
                                    contentDescription = "Tambah Briefing"
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = { BottomNavigationBar() }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(8.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Halo, $userName", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Agenda Safety Briefing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (activeAgenda != null) {
                    SafetyBriefingCard(activeAgenda!!)
                } else {
                    // Jika tidak ada agenda aktif, tampilkan ikon +
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_action_name),
                                contentDescription = "Tambah Briefing",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clickable { onAddBriefingClick() }
                            )
                            Text(text = "Tidak ada safety briefing aktif", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Riwayat Safety Briefing", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Lihat Lainnya", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { /* Aksi lihat lainnya */ })
                }
            }
        }
    }
}

@Composable
fun SafetyBriefingCard(agenda: Map<String, Any>) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val terminal = agenda["terminal"] as? String ?: "Tidak diketahui"
    val date = agenda["tanggal"] as? String ?: "Tidak diketahui"
    val time = agenda["waktu"] as? String ?: "Tidak diketahui"
    val participants = agenda["participants"] as? List<String> ?: emptyList()
    val briefingId = agenda["id"] as? String ?: "" // Ambil ID agenda
    val maxParticipants = 30
    var shouldRefresh by remember { mutableStateOf(false) }
    var briefingData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var listenerRegistration by remember { mutableStateOf<ListenerRegistration?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Safety Briefing $terminal",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = date, fontSize = 14.sp, color = Color.Gray)
            Text(text = time, fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))

            // Jumlah peserta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.user),
                    contentDescription = "Peserta",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${participants.size}/$maxParticipants",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tombol-tombol
            Button(
                onClick = { /* Aksi absen */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
            ) {
                Text("Absensi")
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    firestore.collection("agenda").document(briefingId)
                        .update("status", "selesai")
                        .addOnSuccessListener {
                            Log.d("FirestoreUpdate", "Status berhasil diperbarui menjadi selesai")
                            shouldRefresh = true // Trigger refresh
                        }
                        .addOnFailureListener { e ->
                            Log.e("FirestoreUpdate", "Gagal memperbarui status", e)
                        }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
            ) {
                Text("Akhiri Kegiatan")
            }

// Auto-refresh saat shouldRefresh berubah menjadi true
            LaunchedEffect(shouldRefresh) {
                if (shouldRefresh) {
                    shouldRefresh = false
                    // Refresh data dari Firestore
                    listenerRegistration?.remove()
                    listenerRegistration = firestore.collection("agenda").document(briefingId)
                        .addSnapshotListener { document, error ->
                            if (error == null && document != null && document.exists()) {
                                briefingData = document.data
                            }
                        }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tombol Lihat Detail
            OutlinedButton(
                onClick = {
                    val intent = Intent(context, DetailSafetyBriefingActivity::class.java)
                    intent.putExtra("briefingId", briefingId) // Kirim ID briefing
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
            ) {
                Text("Lihat Detail", color = Color(0xFF0E73A7))
            }
        }
    }
}




@Composable
fun DrawerContent(onCloseDrawer: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = "Menu", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Beranda", fontSize = 16.sp, modifier = Modifier.clickable { onCloseDrawer() })
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Riwayat", fontSize = 16.sp, modifier = Modifier.clickable { onCloseDrawer() })
        Spacer(modifier = Modifier.height(8.dp))

        // Logout button
        Text(
            text = "Logout",
            fontSize = 16.sp,
            color = Color.Red,
            modifier = Modifier.clickable {
                auth.signOut() // Logout dari Firebase
                onCloseDrawer() // Tutup drawer

                // Simpan status logout di SharedPreferences
                val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                sharedPreferences.edit().putBoolean("is_logged_in", false).apply()

                // Arahkan ke LoginRegisterActivity
                val intent = Intent(context, LoginRegisterActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)

                // Pastikan HomeActivity selesai setelah pindah
                if (context is HomeActivity) {
                    context.finish()
                }
            }
        )
    }
}

@Composable
fun BottomNavigationBar() {
    NavigationBar(containerColor = Color(0xFF1976D2)) {
        NavigationBarItem(
            icon = { Icon(painterResource(id = R.drawable.home), contentDescription = "Beranda") },
            label = { Text("Beranda") },
            selected = true,
            onClick = {}
        )
        NavigationBarItem(
            icon = { Icon(painterResource(id = R.drawable.history), contentDescription = "Riwayat") },
            label = { Text("Riwayat") },
            selected = false,
            onClick = {}
        )
    }
}
