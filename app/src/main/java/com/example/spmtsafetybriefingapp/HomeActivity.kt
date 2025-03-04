package com.example.spmtsafetybriefingapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.shape.CornerSize
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

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
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    var userName by remember { mutableStateOf("User") }
    var activeAgenda by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    val scaffoldState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var isLocationValid by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchUserLocation(context, fusedLocationClient) { location ->
                userLocation = location
                validateLocation(firestore, location) { isValid ->
                    isLocationValid = isValid
                    isLoading = false
                }
            }
        } else {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            fetchUserLocation(context, fusedLocationClient) { location ->
                userLocation = location
                validateLocation(firestore, location) { isValid ->
                    Log.d("GeoFence", "Validasi lokasi: $isValid")
                    isLocationValid = isValid
                    isLoading = false
                }
            }
        } else {
            permissionLauncher.launch(permission)
        }
    }

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

    LaunchedEffect(Unit) {
        try {
            val querySnapshot = firestore.collection("agenda")
                .whereEqualTo("status", "aktif")
                .limit(3)
                .get()
                .await()

            if (!querySnapshot.isEmpty) {
                activeAgenda = querySnapshot.documents.map { document ->
                    document.data?.toMutableMap()?.apply { put("id", document.id) } ?: emptyMap()
                }
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
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Halo, $userName",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp) // Memberikan jarak antar kartu
                    ) {
                        items(activeAgenda) { agenda ->
                            Box(
                                modifier = Modifier
                                    .width(360.dp)
                            ) {
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

                if (activeAgenda.isNotEmpty()) {
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

@Composable
fun AttendanceDashboard(firestore: FirebaseFirestore) {
    var attendanceList by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var selectedTerminal by remember { mutableStateOf("Semua") }
    val terminalOptions = listOf("Semua", "Terminal Jamrud", "Terminal Nilam", "Terminal Mirah")

    LaunchedEffect(selectedTerminal) {
        try {
            Log.d("Firestore", "Fetching active agendas for terminal: $selectedTerminal")

            val query = firestore.collection("agenda").whereEqualTo("status", "aktif")
            if (selectedTerminal != "Semua") {
                query.whereEqualTo("terminal", selectedTerminal)
            }

            val activeAgendas = query.get().await()
            Log.d("Firestore", "Total Active Agendas: ${activeAgendas.size()}")

            val allAttendances = mutableListOf<Pair<String, String>>()

            for (agenda in activeAgendas) {
                val briefingId = agenda.id
                val attendanceRef = firestore.collection("agenda")
                    .document(briefingId)
                    .collection("attendance")
                    .get()
                    .await()

                val attendances = attendanceRef.documents.mapNotNull { doc ->
                    val name = doc.getString("userName") ?: "Unknown"
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()?.let {
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
                    } ?: "-"
                    name to timestamp
                }
                allAttendances.addAll(attendances)
            }

            attendanceList = allAttendances
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

        Spacer(modifier = Modifier.height(12.dp))

        // 🔹 Tabel Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E73A7))
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Nama",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            Text(
                text = "Timestamp",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
        }

        // 🔹 Tabel Data Absensi
        if (attendanceList.isEmpty()) {
            Text(
                text = "Belum ada data absensi",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(attendanceList) { (name, timestamp) ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
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

    val maxParticipants = 30
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    val isLocationValid by remember { mutableStateOf(false) }
    var shouldRefresh by remember { mutableStateOf(false) }
    var briefingData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var listenerRegistration by remember { mutableStateOf<ListenerRegistration?>(null) }

    val photoUri = remember { mutableStateOf<Uri?>(null) }
    val userId = auth.currentUser?.uid ?: ""
    var userName by remember { mutableStateOf("User") }

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

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            val userDoc = firestore.collection("users").document(userId).get().await()
            userName = userDoc.getString("name") ?: "User"
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri.value?.let { uri ->
                if (userId.isNotEmpty()) {
                    val attendanceData = mapOf(
                        "userId" to userId,
                        "userName" to userName,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "photoUri" to uri.toString()
                    )
                    firestore.collection("agenda").document(briefingId)
                        .collection("attendance").add(attendanceData)
                        .addOnSuccessListener {
                            val intent = Intent(context, AbsensiResultActivity::class.java).apply {
                                putExtra("briefingId", briefingId)
                            }
                            context.startActivity(intent)
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
                    Text("Lokasi tidak sesuai!", color = Color.White)
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
    var selectedItem by remember { mutableStateOf(0) }
    NavigationBar (
        containerColor = Color(0xFF0E73A7) // Mengubah warna latar belakang
    ){
        NavigationBarItem(
            icon = {
                Icon(
                    painterResource(id = if (selectedItem == 0) R.drawable.home_filled else R.drawable.home_stroke),
                    contentDescription = "Beranda"
                )
            },
            label = { Text(
                "Beranda",
                fontSize = 12.sp,
                color = Color.White  )},
            selected = selectedItem == 0,
            onClick = { selectedItem = 0 },
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
                    painterResource(id = if (selectedItem == 0) R.drawable.history_stroke else R.drawable.history_filled),
                    contentDescription = "Riwayat"
                )
            },
            label = { Text(
                "Riwayat",
                fontSize = 12.sp,
                color = Color.White  )},
            selected = selectedItem == 0,
            onClick = { selectedItem = 0 },
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
