package com.example.spmtsafetybriefingapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.provider.MediaStore
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.sqrt

class HomeActivity : ComponentActivity() {
    private lateinit var firestore: FirebaseFirestore
    private var interpreter: Interpreter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestore = FirebaseFirestore.getInstance()

        try {
            val tfliteModel = loadModelFile()
            interpreter = Interpreter(tfliteModel)
            Log.d("TFLite", "Model berhasil dimuat.")
        } catch (e: Exception) {
            Log.e("TFLite", "Gagal memuat model", e)
            Toast.makeText(this, "Gagal memuat model", Toast.LENGTH_LONG).show()
            finish()
        }
        setContent {
            HomeScreen {
                val intent = Intent(this, FormSafetyBriefingActivity::class.java)
                startActivity(intent)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HomeScreen(onAddBriefingClick: () -> Unit) {
        val context = LocalContext.current
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        var userName by remember { mutableStateOf("User") }
        var activeAgenda by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
        val scaffoldState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val coroutineScope = rememberCoroutineScope()
        var userRole by remember { mutableStateOf("") }
        var isRefreshing by remember { mutableStateOf(false) } // 🔹 State untuk Refresh

        fun fetchActiveAgenda() {
            isRefreshing = true
            coroutineScope.launch {
                try {
                    val user = auth.currentUser
                    if (user != null) {
                        val userDoc = firestore.collection("users").document(user.uid).get().await()
                        userRole = userDoc.getString("role") ?: ""
                        userName = userDoc.getString("name") ?: "User"
                        val userTerminal = userDoc.getString("terminal") ?: ""
                        val userGroup = userDoc.getString("group") ?: ""

                        val allowedRoles = listOf(
                            "Brach Manager", "Deputy Branch Manager Perencanaan dan Pengendalian Operasi",
                            "Manager Operasi Jamrud", "Manager Operasi Nilam Mirah", "HSSE",
                            "Koordinator Lapangan Pengamanan", "Komandan Peleton", "Chief Foreman"
                        )
                        val operationalRoles = listOf("Foreman", "Dispatcher")

                        val agendaQuery = when {
                            userRole in allowedRoles -> {
                                var query = firestore.collection("agenda").whereEqualTo("status", "aktif")
                                when {
                                    userRole == "Anggota Pengamanan" -> {
                                        query = query.whereEqualTo("terminal", userTerminal)
                                            .whereEqualTo("groupSecurity", userGroup)
                                    }
                                    userRole in operationalRoles -> {
                                        query = query.whereEqualTo("terminal", userTerminal)
                                            .whereEqualTo("groupOperational", userGroup)
                                    }
                                }
                                query
                            }
                            else -> null
                        }

                        if (agendaQuery != null) {
                            val agendaSnapshot = agendaQuery.get().await()
                            activeAgenda = agendaSnapshot.documents.mapNotNull { document ->
                                document.data?.toMutableMap()?.apply { put("id", document.id) }
                            }
                        } else {
                            activeAgenda = emptyList()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Firestore", "Error fetching agendas: ${e.message}")
                } finally {
                    isRefreshing = false // 🔹 Hentikan Refresh
                }
            }
        }

        // Panggil fetchActiveAgenda() saat pertama kali dibuka
        LaunchedEffect(Unit) {
            fetchActiveAgenda()
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
                        title = {
                            Text(
                                text = "Halo, $userName",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { scaffoldState.open() } }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.menu),
                                    contentDescription = "Menu",
                                    tint = Color.Black
                                )
                            }
                        }
                    )
                },
                bottomBar = { BottomNavigationBar() }
            ) { paddingValues ->
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing), // 🔹 Tambahkan SwipeRefresh
                    onRefresh = { fetchActiveAgenda() } // 🔹 Panggil fungsi untuk refresh
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Agenda Safety Briefing",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp),
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (activeAgenda.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(activeAgenda) { agenda ->
                                    Box(modifier = Modifier.width(360.dp)) {
                                        SafetyBriefingCard(agenda)
                                    }
                                }
                            }
                        } else {
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

                        Spacer(modifier = Modifier.height(16.dp))

                        val allowedRoles = setOf(
                            "Brach Manager", "Deputy Branch Manager Perencanaan dan Pengendalian Operasi",
                            "Manager Operasi Jamrud", "Manager Operasi Nilam Mirah", "HSSE",
                            "Koordinator Lapangan Pengamanan", "Komandan Peleton", "Chief Foreman"
                        )

                        if (userRole in allowedRoles && activeAgenda.isNotEmpty()) {
                            activeAgenda.forEach { agenda ->
                                val briefingId = agenda["id"]?.toString() ?: ""
                                if (briefingId.isNotEmpty()) {
                                    AttendanceDashboard(firestore)
                                } else {
                                    Log.e("Debug", "Briefing ID is empty")
                                }
                            }
                        }
                    }
                }
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

                for (agenda in activeAgendas) {
                    val briefingId = agenda.id
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
                }

                attendanceList = allAttendances
                presentUsers = attendedUsers.size // Simpan jumlah user yang sudah absen

                // 🔹 Ambil Total User di Terminal Tersebut
                if (selectedTerminal != "Semua") {
                    val usersQuery = firestore.collection("users")
                        .whereEqualTo("terminal", selectedTerminal)
                        .get()
                        .await()

                    val usersInTerminal = usersQuery.documents.mapNotNull { it.getString("userName") }
                    totalUsers = usersInTerminal.size
                    absentUsers = usersInTerminal.count { it !in attendedUsers }
                } else {
                    totalUsers = 0
                    absentUsers = 0
                }

                Log.d("Firestore", "Total Users: $totalUsers, Present Users: $presentUsers, Absent Users: $absentUsers")
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
                        Text(text = "$presentUsers", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        Text(text = "$absentUsers", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                val sortedAttendanceList = attendanceList.sortedBy { it.second } // Jika berbentuk Pair<String, String>

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

                    // 🔹 Menampilkan Data Absensi dengan Nomor Urut
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

    @Composable
    fun SafetyBriefingCard(agenda: Map<String, Any>) {
        val context = LocalContext.current
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()

        val terminal = agenda["terminal"] as? String ?: "Tidak diketahui"
        val shift = agenda["shift"] as? String ?: "Tidak diketahui"
        val time = (agenda["timestamp"] as? Timestamp)?.toDate()?.let { date ->
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            sdf.format(date)
        } ?: "Tidak diketahui"
        val participants = agenda["participants"] as? List<String> ?: emptyList()
        val briefingId = agenda["id"] as? String ?: ""

        var maxParticipants by remember { mutableStateOf(0) }
        val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
        var shouldRefresh by remember { mutableStateOf(false) }
        var briefingData by remember { mutableStateOf<Map<String, Any>?>(null) }
        var listenerRegistration by remember { mutableStateOf<ListenerRegistration?>(null) }
        var faceEmbedding by remember { mutableStateOf<List<Float>?>(null) }
        val photoUri = remember { mutableStateOf<Uri?>(null) }
        val userId = auth.currentUser?.uid ?: ""
        var userName by remember { mutableStateOf("User") }
        var imageUri by remember { mutableStateOf<Uri?>(null) }
        var participantsCount by remember { mutableStateOf(0) }

        LaunchedEffect(briefingId) {
            if (briefingId.isNotEmpty()) {
                val attendanceSnapshot = firestore.collection("agenda")
                    .document(briefingId)
                    .collection("attendance")
                    .get()
                    .await()

                participantsCount = attendanceSnapshot.size()
            }
        }

        LaunchedEffect(terminal) {
            if (terminal.isNotEmpty() && terminal != "Tidak diketahui") {
                val usersSnapshot = firestore.collection("users")
                    .whereEqualTo("terminal", terminal)
                    .get()
                    .await()

                maxParticipants = usersSnapshot.size() // 🔹 Total user di terminal ini
            }
        }

        LaunchedEffect(userId) {
            if (userId.isNotEmpty()) {
                val userDoc = firestore.collection("users").document(userId).get().await()
                userName = userDoc.getString("name") ?: "User"
            }
        }

        val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                photoUri.value?.let { uri ->
                    val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    detectFace(bitmap) { croppedFace ->
                        if (croppedFace != null) {
                            val embedding = getFaceEmbedding(croppedFace) // Ambil face embedding
                            val embeddingList = embedding?.toList() ?: emptyList() // Konversi ke List<Float>

                            val newUri = Uri.parse(MediaStore.Images.Media.insertImage(
                                context.contentResolver, croppedFace, "profile_photo", null
                            ))
                            imageUri = newUri

                            if (userId.isNotEmpty()) {
                                val attendanceRef = firestore.collection("agenda").document(briefingId)
                                    .collection("attendance").document() // Buat dokumen baru

                                val attendanceData = mapOf(
                                    "userId" to userId,
                                    "userName" to userName,
                                    "timestamp" to FieldValue.serverTimestamp(),
                                    "photoUri" to newUri.toString(),
                                    "faceEmbedding" to embeddingList
                                )

                                attendanceRef.set(attendanceData)
                                    .addOnSuccessListener {
                                        Log.d("Firestore", "Data absensi berhasil disimpan")
                                        compareFaceEmbeddings(userId, briefingId, attendanceRef.id, context)
                                    }
                            }
                        } else {
                            Toast.makeText(context, "Wajah tidak terdeteksi!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.dp, Color.Gray, shape = MaterialTheme.shapes.medium), // Stroke abu-abu
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Safety Briefing $terminal", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = shift, fontSize = 14.sp, color = Color.Gray)
                Text(text = time, fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

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
                        text = "$participantsCount/$maxParticipants",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                var showInvalidLocation by remember { mutableStateOf(false) }

                var hasAttended by remember { mutableStateOf(false) }

                LaunchedEffect(userId, briefingId) {
                    if (userId.isNotEmpty() && briefingId.isNotEmpty()) {
                        val attendanceSnapshot = firestore.collection("agenda")
                            .document(briefingId)
                            .collection("attendance")
                            .whereEqualTo("userId", userId)
                            .get()
                            .await()

                        hasAttended = !attendanceSnapshot.isEmpty
                    }
                }

                Button(
                    onClick = {
                        if (!hasAttended) {
                            Log.d("AbsensiButton", "Button Absensi ditekan")

                            fetchUserLocation(context, fusedLocationClient) { location ->
                                if (location != null) {
                                    validateLocation(firestore, location) { isValid ->
                                        Log.d("AbsensiButton", "Status lokasi valid: $isValid")

                                        if (isValid) {
                                            val file = File(context.getExternalFilesDir(null), "photo.jpg")
                                            val uri = FileProvider.getUriForFile(context, "com.example.spmtsafetybriefingapp.fileprovider", file)
                                            photoUri.value = uri
                                            takePictureLauncher.launch(uri)
                                            showInvalidLocation = false
                                        } else {
                                            Log.d("GeoFence", "Lokasi tidak valid, tidak bisa melakukan absensi.")
                                            showInvalidLocation = true
                                        }
                                    }
                                } else {
                                    Log.e("AbsensiButton", "Gagal mendapatkan lokasi pengguna.")
                                    showInvalidLocation = true
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasAttended) Color.Gray else Color(0xFF4CAF50)
                    ),
                    enabled = !hasAttended,
                    shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
                ) {
                    Text(if (hasAttended) "Sudah Absen" else "Absensi")
                }

                if (showInvalidLocation) {
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { showInvalidLocation = false }) {
                                Text("Tutup", color = Color.White)
                            }
                        }
                    ) {
                        Text("Waktu dan Lokasi tidak sesuai!", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        firestore.collection("agenda").document(briefingId)
                            .update("status", "selesai")
                            .addOnSuccessListener {
                                Log.d("FirestoreUpdate", "Status berhasil diperbarui menjadi selesai")
                                shouldRefresh = true
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

                LaunchedEffect(shouldRefresh) {
                    if (shouldRefresh) {
                        shouldRefresh = false
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

                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, DetailSafetyBriefingActivity::class.java)
                        intent.putExtra("briefingId", briefingId)
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

    fun calculateCosineSimilarity(embedding1: List<Double>, embedding2: List<Double>): Double {
        val dotProduct = embedding1.zip(embedding2).sumOf { it.first * it.second }
        val magnitude1 = sqrt(embedding1.sumOf { it * it })
        val magnitude2 = sqrt(embedding2.sumOf { it * it })

        return if (magnitude1 == 0.0 || magnitude2 == 0.0) 0.0 else dotProduct / (magnitude1 * magnitude2)
    }

    fun compareFaceEmbeddings(userId: String, briefingId: String, attendanceId: String, context: Context) {
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val userEmbedding = userDoc.get("faceEmbedding") as? List<Double>

                if (userEmbedding != null) {
                    firestore.collection("agenda").document(briefingId)
                        .collection("attendance").document(attendanceId).get()
                        .addOnSuccessListener { attendanceDoc ->
                            val attendanceEmbedding = attendanceDoc.get("faceEmbedding") as? List<Double>

                            if (attendanceEmbedding != null) {
                                val similarity = calculateCosineSimilarity(userEmbedding, attendanceEmbedding)

                                // Log nilai akurasi kecocokan wajah
                                Log.d("FaceRecognition", "Akurasi wajah: ${similarity * 100}%")

                                if (similarity >= 0.6) {
                                    Toast.makeText(context, "Absensi berhasil! Akurasi: ${(similarity * 100).toInt()}%", Toast.LENGTH_SHORT).show()

                                    // Arahkan ke halaman AbsensiResultActivity dengan briefingId
                                    val intent = Intent(context, AbsensiResultActivity::class.java).apply {
                                        putExtra("briefingId", briefingId)
                                    }
                                    context.startActivity(intent)

                                } else {
                                    // Hapus data absensi jika wajah tidak cocok
                                    firestore.collection("agenda").document(briefingId)
                                        .collection("attendance").document(attendanceId)
                                        .delete()
                                        .addOnSuccessListener {
                                            Log.d("FaceRecognition", "Data attendance $attendanceId dihapus karena gagal absensi.")
                                        }
                                        .addOnFailureListener {
                                            Log.e("FaceRecognition", "Gagal menghapus data attendance $attendanceId.")
                                        }

                                    // Arahkan ke halaman gagal absensi
                                    val intent = Intent(context, GagalAbsensiActivity::class.java)
                                    context.startActivity(intent)

                                    Toast.makeText(context, "Gagal absensi! Akurasi: ${(similarity * 100).toInt()}%", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Face embedding tidak ditemukan di attendance!", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(context, "Face embedding tidak ditemukan di users!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Gagal mengambil data!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun detectFace(bitmap: Bitmap, onResult: (Bitmap?) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()

        val detector = FaceDetection.getClient(options)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0] // Ambil wajah pertama
                    val croppedFace = cropFace(bitmap, face) // Pangkas wajah
                    onResult(croppedFace)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    private fun cropFace(bitmap: Bitmap, face: Face): Bitmap {
        val boundingBox = face.boundingBox
        val x = boundingBox.left.coerceAtLeast(0)
        val y = boundingBox.top.coerceAtLeast(0)
        val width = boundingBox.width().coerceAtMost(bitmap.width - x)
        val height = boundingBox.height().coerceAtMost(bitmap.height - y)

        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    private fun getFaceEmbedding(faceBitmap: Bitmap): List<Float> {
        val inputBuffer = convertBitmapToByteBuffer(faceBitmap)
        val embeddingSize = 192

        val outputArray = Array(1) { FloatArray(embeddingSize) }

        if (interpreter == null) {
            throw IllegalStateException("Interpreter belum diinisialisasi")
        }

        if (inputBuffer.capacity() == 0) {
            throw RuntimeException("Input buffer kosong!")
        }

        interpreter!!.run(inputBuffer, outputArray)

        Log.d("TFLite", "Output buffer ukuran: ${outputArray[0].size} float values")

        return outputArray[0].toList()
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = 112 // Pastikan ukuran input sesuai dengan model
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        Log.d("ByteBuffer", "ByteBuffer kapasitas: ${byteBuffer.capacity()} bytes")

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in intValues) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        Log.d("ByteBuffer", "ByteBuffer setelah diisi: ${byteBuffer.position()} bytes")
        return byteBuffer
    }

    private fun loadModelFile(): ByteBuffer {
        val assetFileDescriptor = assets.openFd("mobile_face_net.tflite")
        val inputStream = assetFileDescriptor.createInputStream()
        val byteArray = inputStream.readBytes()
        inputStream.close()

        val buffer = ByteBuffer.allocateDirect(byteArray.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(byteArray)
        buffer.rewind()

        return buffer

    }

    @SuppressLint("MissingPermission")
    fun fetchUserLocation(context: Context, fusedLocationClient: FusedLocationProviderClient, callback: (Location?) -> Unit) {
        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location ->
            if (location != null) {
                Log.d("Location", "Lokasi diperoleh: ${location.latitude}, ${location.longitude}")
                callback(location)
            } else {
                Log.e("Location", "Gagal mendapatkan lokasi terbaru.")
                callback(null)
            }
        }.addOnFailureListener {
            Log.e("Location", "Error mendapatkan lokasi", it)
            callback(null)
        }
    }

    fun validateLocation(firestore: FirebaseFirestore, userLocation: Location?, callback: (Boolean) -> Unit) {
        firestore.collection("agenda").whereEqualTo("status", "aktif").limit(1).get()
            .addOnSuccessListener { agendaSnapshot ->
                if (!agendaSnapshot.isEmpty) {
                    val agendaDoc = agendaSnapshot.documents[0]
                    val terminalName = agendaDoc.getString("terminal") ?: return@addOnSuccessListener callback(false)

                    firestore.collection("geoFence").document(terminalName).get()
                        .addOnSuccessListener { geoFenceDoc ->
                            val geoPoint = geoFenceDoc.getGeoPoint("location")
                            geoPoint?.let {
                                val targetLocation = Location("").apply {
                                    latitude = it.latitude
                                    longitude = it.longitude
                                }
                                val distance = userLocation?.distanceTo(targetLocation) ?: Float.MAX_VALUE
                                Log.d("GeoFence", "Jarak ke lokasi absen: $distance meter")
                                callback(distance <= 100.0)
                            } ?: callback(false)
                        }
                        .addOnFailureListener {
                            Log.e("GeoFence", "Gagal mengambil lokasi dari Firestore", it)
                            callback(false)
                        }
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener {
                Log.e("GeoFence", "Gagal mengambil agenda aktif", it)
                callback(false)
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
                    auth.signOut()
                    onCloseDrawer()

                    val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                    sharedPreferences.edit().putBoolean("is_logged_in", false).apply()

                    val intent = Intent(context, LoginRegisterActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)

                    if (context is HomeActivity) {
                        context.finish()
                    }
                }
            )
        }
    }

    @Composable
    fun BottomNavigationBar() {
        val context = LocalContext.current
        var selectedItem by remember { mutableStateOf(0) }

        NavigationBar(
            containerColor = Color(0xFF0E73A7) // Mengubah warna latar belakang
        ) {
            // 🔹 Item Beranda
            NavigationBarItem(
                icon = {
                    Icon(
                        painterResource(id = if (selectedItem == 0) R.drawable.home_filled else R.drawable.home_stroke),
                        contentDescription = "Beranda"
                    )
                },
                label = {
                    Text(
                        "Beranda",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                },
                selected = selectedItem == 0,
                onClick = {
                    selectedItem = 0
                    context.startActivity(Intent(context, HomeActivity::class.java))
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    unselectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedTextColor = Color.White,
                    indicatorColor = Color.Transparent
                )
            )

            // 🔹 Item Riwayat
            NavigationBarItem(
                icon = {
                    Icon(
                        painterResource(id = if (selectedItem == 1) R.drawable.history_filled else R.drawable.history_stroke),
                        contentDescription = "Riwayat"
                    )
                },
                label = {
                    Text(
                        "Riwayat",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                },
                selected = selectedItem == 1,
                onClick = {
                    selectedItem = 1
                    context.startActivity(Intent(context, HistoryActivity::class.java))
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
}


