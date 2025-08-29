package com.example.spmtsafetybriefingapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.sqrt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.tasks.await
import java.util.Date



class HomeActivity : ComponentActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var interpreter: Interpreter? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

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
            var userName by remember { mutableStateOf("") }
            var userRole by remember { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                fetchUserData { name, role ->
                    userName = name
                    userRole = role
                    isLoading = false
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                HomeScreen(
                    onAddBriefingClick = {
                        val intent = Intent(this@HomeActivity, FormSafetyBriefingActivity::class.java)
                        startActivity(intent)
                    },
                    navigateToPendingApproval = {
                        val intent = Intent(this@HomeActivity, PendingApprovalActivity::class.java)
                        startActivity(intent)
                    },
                    userName = userName,
                    userRole = userRole
                )
            }
        }
    }

    private fun fetchUserData(onResult: (String, String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name") ?: "User"
                    val role = document.getString("role") ?: "Unknown Role"
                    onResult(name, role)
                } else {
                    onResult("User", "Unknown Role")
                }
            }
            .addOnFailureListener {
                onResult("User", "Unknown Role")
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HomeScreen(onAddBriefingClick: () -> Unit, navigateToPendingApproval: () -> Unit, userName: String, userRole: String) {
        var pendingCount by remember { mutableStateOf(0) }
        val context = LocalContext.current
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        var userName by remember { mutableStateOf("User") }
        var activeAgenda by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
        val scaffoldState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val coroutineScope = rememberCoroutineScope()
        var userRole by remember { mutableStateOf("") }
        var isRefreshing by remember { mutableStateOf(false) } // 🔹 State untuk Refresh
        var userGroup by remember { mutableStateOf("") }
        var userTerminal by remember { mutableStateOf("") }
        var userBranch by remember { mutableStateOf("") }
        var totalBriefing by remember { mutableStateOf(0) }
        var shift1Count by remember { mutableStateOf(0) }
        var shift2Count by remember { mutableStateOf(0) }
        var shift3Count by remember { mutableStateOf(0) }
        var sudahMembuatAgendaHariIni by remember { mutableStateOf(false) }

        val allowedNames = listOf(
            "Eko Mardiyanto",
            "Nanang Iswahyudi",
            "Irawan",
            "Agus Dwi Susanto",
            "Mohammad Samsul Syamsuri",
            "Achmad Zakaria",
            "Moh.Hasan Arif",
            "Suwito Mulyo",
            "Agus Tribusono",
            "Hari Murtiantoro",
            "Sugiono",
            "Herwanto"
        )

        val allowedKoor = listOf("Koordinator Operasi") // ✅ ubah jadi list

        val isAllowedKoor = allowedKoor.any { userRole.startsWith(it) }

        LaunchedEffect(Unit) {
            getPendingUserCount { count ->
                pendingCount = count
            }
        }

        fun fetchActiveAgenda() {
            isRefreshing = true
            coroutineScope.launch {
                try {
                    val user = auth.currentUser
                    if (user != null) {
                        val userDoc = firestore.collection("users").document(user.uid).get().await()
                        userRole = userDoc.getString("role") ?: ""
                        userName = userDoc.getString("name") ?: "User"
                        userBranch = userDoc.getString("branch") ?: ""
                        userTerminal = userDoc.getString("terminal") ?: ""
                        userGroup = userDoc.getString("group") ?: ""

                        val today = LocalDate.now()
                        val zoneId = ZoneId.systemDefault()
                        val startOfDay = today.atStartOfDay(zoneId).toInstant()
                        val endOfDay = today.atTime(LocalTime.MAX).atZone(zoneId).toInstant()
                        val timestampStart = Timestamp(Date.from(startOfDay))
                        val timestampEnd = Timestamp(Date.from(endOfDay))

                        Log.d("AgendaDebug", "userName: '$userName'")
                        Log.d("AgendaDebug", "timestampStart: $timestampStart")
                        Log.d("AgendaDebug", "timestampEnd: $timestampEnd")

                        // ✅ Cek apakah sudah membuat agenda hari ini
                        try {
                            val cekAgendaSnapshot = firestore.collection("agenda")
                                .whereGreaterThanOrEqualTo("timestamp", timestampStart)
                                .whereLessThanOrEqualTo("timestamp", timestampEnd)
                                .whereEqualTo("koordinator", userName.trim())
                                .get()
                                .await()

                            Log.d("AgendaDebug", "Snapshot diterima. Jumlah dokumen: ${cekAgendaSnapshot.size()}")

                            if (cekAgendaSnapshot.isEmpty) {
                                Log.d("AgendaDebug", "Tidak ada agenda ditemukan.")
                            } else {
                                cekAgendaSnapshot.documents.forEachIndexed { i, doc ->
                                    val id = doc.id
                                    val time = doc.getTimestamp("timestamp")?.toDate()
                                    val koor = doc.getString("koordinator")
                                    Log.d("AgendaDebug", "[$i] ID: $id | timestamp: $time | koordinator: '$koor'")
                                }
                            }

                            sudahMembuatAgendaHariIni = cekAgendaSnapshot.documents.isNotEmpty()

                        } catch (e: Exception) {
                            Log.e("AgendaDebug", "Gagal ambil agenda hari ini: ${e.message}", e)
                        }

                        // 🔎 Query agenda aktif berdasarkan role
                        val allowedRoles = listOf(
                            "Branch Manager", "Deputy Branch Manager Perencanaan dan Pengendalian Operasi",
                            "Manager Operasi", "HSSE"
                        )
                        val userRoles = listOf(
                            "Anggota Pengamanan", "Operasional", "Komandan Peleton",
                            "Koordinator Lapangan Pengamanan", "Koordinator Operasi"
                        )

                        val isAllowedRoles = allowedRoles.any { userRole.startsWith(it) }
                        val isUserRoles = userRoles.any { userRole.startsWith(it) }

                        var agendaQuery: Query? = null

                        if (isAllowedRoles || isUserRoles) {
                            agendaQuery = firestore.collection("agenda")
                                .whereEqualTo("status", "aktif")

                            if (isUserRoles) {
                                agendaQuery = agendaQuery
                                    .whereEqualTo("terminal", userTerminal)
                                    .whereEqualTo("group", userGroup)

                                Log.d("Firestore", "Filtered by terminal: $userTerminal, group: $userGroup")
                            }
                        } else {
                            Log.d("Firestore", "User role $userRole tidak diizinkan melihat agenda aktif.")
                        }

                        if (agendaQuery != null) {
                            val agendaSnapshot = agendaQuery.get().await()

                            activeAgenda = agendaSnapshot.documents.mapNotNull { doc ->
                                doc.data?.toMutableMap()?.apply { put("id", doc.id) }
                            }

                            val agendaList = activeAgenda // untuk keperluan rekap shift
                            totalBriefing = agendaList.size

                            shift1Count = agendaList.count { (it["shift"] as? String)?.contains("Shift 1") == true }
                            shift2Count = agendaList.count { (it["shift"] as? String)?.contains("Shift 2") == true }
                            shift3Count = agendaList.count { (it["shift"] as? String)?.contains("Shift 3") == true }

                        } else {
                            activeAgenda = emptyList()
                            totalBriefing = 0
                            shift1Count = 0
                            shift2Count = 0
                            shift3Count = 0
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Firestore", "Error fetching agendas: ${e.message}", e)
                } finally {
                    isRefreshing = false
                }
            }
        }

        LaunchedEffect(Unit) {
            fetchActiveAgenda()
        }

        LaunchedEffect(pendingCount) {
            if (pendingCount > 0) {
                sendNotification(
                    context,
                    title = "Menunggu Persetujuan",
                    message = "Terdapat $pendingCount pengguna menunggu persetujuan anda."
                )
            }
        }

        ModalNavigationDrawer(
            drawerState = scaffoldState,
            drawerContent = {
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                ) {
                    DrawerContent(
                        onCloseDrawer = { coroutineScope.launch { scaffoldState.close() } },
                        userName = userName,
                        branch = userBranch,
                        terminal = userTerminal,
                        group = userGroup
                    )
                }
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
                        },
                        actions = {
                            if (userRole.startsWith("Manager Operasi")) {
                                Box {
                                    IconButton(onClick = { navigateToPendingApproval() }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.notif),
                                            contentDescription = "Pending Approval"
                                        )
                                    }
                                    if (pendingCount > 0) {
                                        Badge(
                                            modifier = Modifier.align(Alignment.TopEnd),
                                            content = { Text(text = pendingCount.toString()) }
                                        )
                                    }
                                }
                            }
                        }
                    )
                },
                bottomBar = { BottomNavigationBar() }
            ) { paddingValues ->
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing),
                    onRefresh = { fetchActiveAgenda() }
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(paddingValues)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ){
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

                                    if ((isAllowedKoor || userName in allowedNames) && !sudahMembuatAgendaHariIni) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_action_name),
                                            contentDescription = "Tambah Briefing",
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clickable { onAddBriefingClick() }
                                        )
                                    } else if (sudahMembuatAgendaHariIni && (isAllowedKoor || userName in allowedNames)) {
                                        Text(
                                            text = "Anda sudah membuat agenda safety briefing",
                                            color = Color.Gray,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Text(text = "Tidak ada safety briefing aktif", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
                        var startDate by remember { mutableStateOf(LocalDate.now().minusDays(7)) }
                        var endDate by remember { mutableStateOf(LocalDate.now()) }

                        var showDateRangeDialog by remember { mutableStateOf(false) }
                        var attendanceShift1 by remember { mutableStateOf(0) }
                        var attendanceShift2 by remember { mutableStateOf(0) }
                        var attendanceShift3 by remember { mutableStateOf(0) }

                        if (showDateRangeDialog) {
                            DateRangePickerDialog(
                                onDismissRequest = { showDateRangeDialog = false },
                                onDateRangeSelected = { startMillis, endMillis ->
                                    startDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                                    endDate = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                                    showDateRangeDialog = false
                                }
                            )
                        }

                        var filteredAgenda by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

                        val allAgenda = remember { mutableStateListOf<Map<String, Any>>() }

                        LaunchedEffect(Unit) {
                            val snapshot = FirebaseFirestore.getInstance()
                                .collection("agenda")
                                .get()
                                .await()

                            allAgenda.clear()
                            allAgenda.addAll(snapshot.documents.mapNotNull { it.data })
                            Log.d("FilterAgenda", "All agenda fetched: ${allAgenda.size}")
                        }

                        fun parseShiftNumber(shiftValue: Any?): Int? {
                            val shiftString = shiftValue as? String ?: return null
                            return shiftString.split(" ").getOrNull(1)?.toIntOrNull()
                        }

                        fun filterAgendaByDate() {
                            filteredAgenda = allAgenda.filter { agenda ->
                                val timestampMillis = when (val ts = agenda["timestamp"]) {
                                    is com.google.firebase.Timestamp -> ts.toDate().time
                                    is Long -> ts
                                    else -> null
                                }

                                val shift = agenda["shift"]

                                if (timestampMillis != null) {
                                    val zonedDateTime = Instant.ofEpochMilli(timestampMillis)
                                        .atZone(ZoneId.systemDefault())

                                    // Geser tanggal ke hari berikutnya jika jam >= 23
                                    val adjustedDate = if (zonedDateTime.hour >= 23) {
                                        zonedDateTime.toLocalDate().plusDays(1)
                                    } else {
                                        zonedDateTime.toLocalDate()
                                    }

                                    // Bandingkan apakah tanggal hasil penyesuaian masuk dalam range
                                    val isInRange = !adjustedDate.isBefore(startDate) && !adjustedDate.isAfter(endDate)

                                    val shiftNumber = parseShiftNumber(shift)

                                    isInRange
                                } else {
                                    false
                                }
                            }
                        }

                        totalBriefing = filteredAgenda.size
                        shift1Count = filteredAgenda.count { parseShiftNumber(it["shift"]) == 1 }
                        shift2Count = filteredAgenda.count { parseShiftNumber(it["shift"]) == 2 }
                        shift3Count = filteredAgenda.count { parseShiftNumber(it["shift"]) == 3 }

                        LaunchedEffect(startDate, endDate, allAgenda.toList()) {
                            if (allAgenda.isNotEmpty()) {
                                filterAgendaByDate()

                                var shift1 = 0
                                var shift2 = 0
                                var shift3 = 0

                                filteredAgenda.forEach { agenda ->
                                    val docId = agenda["briefingId"] as? String
                                    if (docId == null) {
                                        Log.w("AttendanceDebug", "⚠️ briefingId tidak ditemukan atau null → agenda: $agenda")
                                        return@forEach
                                    }

                                    val shiftRaw = agenda["shift"]
                                    val shift = parseShiftNumber(shiftRaw)
                                    if (shift == null) {
                                        Log.w("AttendanceDebug", "⚠️ Gagal parse shift → shift raw: $shiftRaw")
                                        return@forEach
                                    }

                                    try {
                                        val attendanceSnapshot = FirebaseFirestore.getInstance()
                                            .collection("agenda")
                                            .document(docId)
                                            .collection("attendance")
                                            .get()
                                            .await()

                                        when (shift) {
                                            1 -> shift1 += attendanceSnapshot.size()
                                            2 -> shift2 += attendanceSnapshot.size()
                                            3 -> shift3 += attendanceSnapshot.size()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AttendanceDebug", "❌ Gagal mengambil attendance untuk docId: $docId", e)
                                    }
                                }

                                attendanceShift1 = shift1
                                attendanceShift2 = shift2
                                attendanceShift3 = shift3
                            } else {
                                Log.w("AttendanceDebug", "⚠️ allAgenda kosong, tidak ada data untuk diproses")
                            }
                        }


                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "Rekap Pelaksanaan",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            OutlinedButton(
                                onClick = { showDateRangeDialog = true },
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .align(Alignment.Start),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF0E73A7)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF0E73A7))
                            ) {
                                Text(
                                    text = "Periode: ${dateFormatter.format(startDate)} - ${dateFormatter.format(endDate)}",
                                    color = Color(0xFF0E73A7)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                RekapCard(
                                    title = "Total Briefing",
                                    value = filteredAgenda.size.toString(),
                                    modifier = Modifier.weight(1f)
                                )
                                RekapCard(
                                    title = "Shift 1",
                                    value = filteredAgenda.count { parseShiftNumber(it["shift"]) == 1 }.toString(),
                                    modifier = Modifier.weight(1f)
                                )
                                RekapCard(
                                    title = "Shift 2",
                                    value = filteredAgenda.count { parseShiftNumber(it["shift"]) == 2 }.toString(),
                                    modifier = Modifier.weight(1f)
                                )
                                RekapCard(
                                    title = "Shift 3",
                                    value = filteredAgenda.count { parseShiftNumber(it["shift"]) == 3 }.toString(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            AttendanceBarChart(
                                attendanceShift1 = attendanceShift1,
                                attendanceShift2 = attendanceShift2,
                                attendanceShift3 = attendanceShift3
                            )
                        }
                    }
                    }
                }
            }
        }

    fun getPendingUserCount(onResult: (Int) -> Unit) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("isApproved", false)
            .get()
            .addOnSuccessListener { result ->
                onResult(result.size())
            }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DateRangePickerDialog(
        onDismissRequest: () -> Unit,
        onDateRangeSelected: (Long, Long) -> Unit
    ) {
        val datePickerState = rememberDateRangePickerState()

        // Fungsi untuk memeriksa apakah kedua tanggal sudah terisi
        val startDate = datePickerState.selectedStartDateMillis
        val endDate = datePickerState.selectedEndDateMillis

        if (startDate != null && endDate != null) {
            // Menutup dialog secara otomatis setelah kedua tanggal terisi
            onDateRangeSelected(startDate, endDate)
            onDismissRequest() // Menutup dialog
        }

        DatePickerDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(onClick = {
                    if (startDate != null && endDate != null) {
                        onDateRangeSelected(startDate, endDate)
                        onDismissRequest() // Menutup dialog
                    }
                }) {
                    Text("Pilih")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text("Batal")
                }
            }
        ) {
            DateRangePicker(
                state = datePickerState,
                modifier = Modifier.padding(horizontal = 8.dp) // Padding kiri-kanan yang lebih sedikit
            )
            if (startDate != null && endDate != null) {
                Text(
                    text = "Tanggal Mulai: ${LocalDate.ofInstant(Instant.ofEpochMilli(startDate), ZoneId.systemDefault())} - Tanggal Akhir: ${LocalDate.ofInstant(Instant.ofEpochMilli(endDate), ZoneId.systemDefault())}",
                    fontSize = 7.sp, // Ukuran font lebih kecil
                    modifier = Modifier.padding(2.dp)
                )
            }
        }
    }

    fun fetchShiftFromAgenda(agendaId: String, onResult: (String?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("agenda").document(agendaId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val shift = document.getString("shift")
                    onResult(shift)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    data class ShiftSchedule(
        val shiftName: String = "",
        val attendanceStartHour: Int = 0,
        val attendanceStartMinute: Int = 0,
        val attendanceEndHour: Int = 0,
        val attendanceEndMinute: Int = 0
    )

    suspend fun isAttendanceAllowed(shift: String): Boolean {
        val db = FirebaseFirestore.getInstance()
        val currentTime = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
        val hour = currentTime.get(Calendar.HOUR_OF_DAY)
        val minute = currentTime.get(Calendar.MINUTE)
        val timeNow = hour * 60 + minute

        return try {
            val querySnapshot = db.collection("shifts")
                .whereEqualTo("shiftName", shift) // Cocokkan shift agenda dengan shiftName di Firestore
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                Log.e("isAttendanceAllowed", "Shift '$shift' tidak ditemukan di Firestore.")
                return false
            }

            val snapshot = querySnapshot.documents[0] // Ambil dokumen pertama yang cocok
            val shiftData = snapshot.toObject(ShiftSchedule::class.java)

            if (shiftData != null) {
                val attendanceStart = shiftData.attendanceStartHour * 60 + shiftData.attendanceStartMinute
                val attendanceEnd = shiftData.attendanceEndHour * 60 + shiftData.attendanceEndMinute

                Log.d(
                    "isAttendanceAllowed",
                    "Shift '${shiftData.shiftName}' ditemukan. Waktu absensi: $attendanceStart - $attendanceEnd, Waktu sekarang: $timeNow"
                )

                return timeNow in attendanceStart..attendanceEnd
            } else {
                Log.e("isAttendanceAllowed", "Data shift tidak valid atau tidak bisa dikonversi.")
                false
            }
        } catch (e: Exception) {
            Log.e("isAttendanceAllowed", "Gagal mengambil shift dari Firestore: ${e.message}")
            false
        }
    }

    fun getUserTerminal(firestore: FirebaseFirestore, userId: String, callback: (String?) -> Unit) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val userTerminal = userDoc.getString("terminal")
                callback(userTerminal) // Kirim terminal pengguna melalui callback
            }
            .addOnFailureListener {
                Log.e("Firestore", "Gagal mendapatkan terminal pengguna", it)
                callback(null) // Jika gagal, kirim null
            }
    }

    @Composable
    fun SafetyBriefingCard(agenda: Map<String, Any>) {
        val context = LocalContext.current
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val terminal = agenda["terminal"] as? String ?: "Tidak diketahui"
        val tempat = agenda["tempat"] as? String ?: "Tidak diketahui"
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
        var userRole = remember { mutableStateOf("") }


        val allowedNames = listOf(
            "Eko Mardiyanto",
            "Nanang Iswahyudi",
            "Irawan",
            "Agus Dwi Susanto",
            "Mohammad Samsul Syamsuri",
            "Achmad Zakaria",
            "Moh.Hasan Arif",
            "Suwito Mulyo",
            "Agus Tribusono",
            "Hari Murtiantoro",
            "Sugiono",
            "Herwanto"
        )

        val allowedKoor = userRole.value.startsWith("Koordinator Operasi")

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

        LaunchedEffect(terminal, userId) {
            if (terminal.isNotEmpty() && terminal != "Tidak diketahui" && userId.isNotEmpty()) {
                try {
                    val userDoc = firestore.collection("users").document(userId).get().await()
                    userRole.value = userDoc.getString("role") ?: ""
                    val userGroup = userDoc.getString("group") ?: ""

                    Log.d("Firestore", "User Login: ID=$userId, Role=$userRole, Group=$userGroup")

                    val agendaDoc = firestore.collection("agenda").document(briefingId).get().await()
                    val agendaGroup = agendaDoc.getString("group") ?: ""

                    Log.d("Firestore", "Agenda Group -> $agendaGroup")

                    val validRoles = listOf(
                        "Anggota Pengamanan", "Operasional", "Komandan Peleton", "Koordinator Operasi"
                    )

                    val isValidRoles = validRoles.any { userRole.value.startsWith(it) }

                    val usersSnapshot = firestore.collection("users")
                        .whereEqualTo("terminal", terminal)
                        .get().await()

                    Log.d("Firestore", "Total Users di Terminal [$terminal]: ${usersSnapshot.size()}")

                    val filteredUsers = usersSnapshot.documents.filter { doc ->
                        val role = doc.getString("role") ?: ""
                        val group = doc.getString("group") ?: ""

                        Log.d("Firestore", "Cek User: ID=${doc.id}, Role=$role, Group=$group, UserGroup=$userGroup")

                        val isValid = validRoles.any { role.startsWith(it) } && group == agendaGroup

                        if (isValid) {
                            Log.d("Firestore", "User Valid: ID=${doc.id}, Role=$role, Group=$group")
                        } else {
                            Log.d("Firestore", "User Tidak Valid: ID=${doc.id}, Role=$role, Group=$group")
                        }

                        isValid
                    }

                    maxParticipants = filteredUsers.size
                    Log.d("Firestore", "Total Participants yang Valid: $maxParticipants")
                } catch (e: Exception) {
                    Log.e("Firestore", "Gagal mengambil data pengguna: ${e.localizedMessage}", e)
                }
            }
        }

        LaunchedEffect(userId) {
            if (userId.isNotEmpty()) {
                val userDoc = firestore.collection("users").document(userId).get().await()
                userName = userDoc.getString("name") ?: "User"
            }
        }

        val coroutineScope = rememberCoroutineScope()

        val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                detectFace(bitmap) { croppedFace ->
                    if (croppedFace != null) {
                        val newUri = saveBitmapToMediaStore(context, croppedFace)
                        imageUri = newUri

                        val embedding = getFaceEmbedding(croppedFace)
                        val embeddingList = embedding?.toList() ?: emptyList()

                        val currentUser = FirebaseAuth.getInstance().currentUser
                        val userId = currentUser?.uid.orEmpty()

                        if (userId.isNotEmpty()) {
                            coroutineScope.launch {
                                try {
                                    val userDoc = firestore.collection("users").document(userId).get().await()
                                    val userName = userDoc.getString("name") ?: "Unknown"

                                    val attendanceRef = firestore.collection("agenda").document(briefingId)
                                        .collection("attendance").document()

                                    val attendanceData = mapOf(
                                        "userId" to userId,
                                        "userName" to userName,
                                        "timestamp" to FieldValue.serverTimestamp(),
                                        "photoUri" to newUri.toString(),
                                        "faceEmbedding" to embeddingList
                                    )

                                    attendanceRef.set(attendanceData)
                                        .addOnSuccessListener {
                                            Log.d("Firestore", "✅ Data absensi berhasil disimpan")
                                            compareFaceEmbeddings(userId, briefingId, attendanceRef.id, context)
                                        }
                                        .addOnFailureListener {
                                            Log.e("Firestore", "❌ Gagal menyimpan absensi", it)
                                        }
                                } catch (e: Exception) {
                                    Log.e("Firestore", "❌ Error saat mengambil nama user atau menyimpan data", e)
                                    Toast.makeText(context, "Gagal menyimpan data absensi", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "User belum login!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Wajah tidak terdeteksi!", Toast.LENGTH_SHORT).show()
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
                val context = LocalContext.current

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Safety Briefing $terminal",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f) // Agar teks memenuhi sisa ruang
                    )
                    if (allowedKoor || userName in allowedNames) {
                        IconButton(
                            onClick = {
                                val intent = Intent(context, EditFormActivity::class.java).apply {
                                    putExtra(
                                        "briefingId",
                                        briefingId
                                    ) // Mengirim briefingId ke activity EditFormActivity
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.edit),
                                contentDescription = "Edit",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

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
                var showInvalidTime by remember { mutableStateOf(false) }

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

                var userRole by remember { mutableStateOf("") }

                LaunchedEffect(userId) {
                    if (userId.isNotEmpty()) {
                        try {
                            val userDoc = firestore.collection("users").document(userId).get().await()
                            userRole = userDoc.getString("role") ?: ""
                            Log.d("Firestore", "User role: $userRole")
                        } catch (e: Exception) {
                            Log.e("Firestore", "Gagal mengambil data user", e)
                        }
                    }
                }

                var selectedShift by remember { mutableStateOf<String?>(null) }
                val agendaId = agenda["id"]?.toString() ?: ""

                LaunchedEffect(agendaId) {
                    if (agendaId != null) {
                        fetchShiftFromAgenda(agendaId) { shift ->
                            selectedShift = shift
                        }
                    }
                }

                val allowedRoles = listOf(
                    "Deputy Branch Manager Perencanaan dan Pengendalian Operasi",
                    "Manager Operasi",
                    "HSSE"
                )

                val isBranchManager = userRole == "Branch Manager"

                val isAllowed = allowedRoles.any { role ->
                    if (role == "Manager Operasi") {
                        userRole.startsWith(role)
                    } else {
                        userRole == role
                    }
                }

                if (!isAllowed || isBranchManager) {
                    var isLoading by remember { mutableStateOf(false) }

                    Button(
                        onClick = {
                            if (!hasAttended && !isLoading) {
                                isLoading = true
                                Log.d("AbsensiButton", "Button Absensi ditekan")

                                selectedShift?.let {
                                    (context as? LifecycleOwner)?.lifecycleScope?.launch {
                                        val isAllowed = isAttendanceAllowed(selectedShift!!)
                                        if (isAllowed) {
                                            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch

                                            getUserTerminal(firestore, userId) { userTerminal ->
                                                if (userTerminal != null) {
                                                    fetchUserLocation(context, fusedLocationClient) { location ->
                                                        if (location != null) {
                                                            validateLocation(
                                                                firestore,
                                                                location,
                                                                userTerminal,
                                                                tempat,
                                                                userRole
                                                            ) { isValid ->
                                                                Log.d("AbsensiButton", "Status lokasi valid: $isValid")

                                                                if (isValid) {
                                                                    val file = File(
                                                                        context.getExternalFilesDir(null),
                                                                        "photo.jpg"
                                                                    )
                                                                    val uri = FileProvider.getUriForFile(
                                                                        context,
                                                                        "com.example.spmtsafetybriefingapp.fileprovider",
                                                                        file
                                                                    )
                                                                    photoUri.value = uri
                                                                    takePictureLauncher.launch(null)
                                                                    showInvalidLocation = false
                                                                } else {
                                                                    showInvalidLocation = true
                                                                }
                                                                isLoading = false
                                                            }
                                                        } else {
                                                            Log.e("AbsensiButton", "Gagal mendapatkan lokasi pengguna.")
                                                            showInvalidLocation = true
                                                            isLoading = false
                                                        }
                                                    }
                                                } else {
                                                    Log.e("AbsensiButton", "Gagal mendapatkan terminal pengguna.")
                                                    showInvalidLocation = true
                                                    isLoading = false
                                                }
                                            }
                                        } else {
                                            Log.e("AbsensiButton", "Waktu absensi tidak sesuai dengan shift.")
                                            showInvalidTime = true
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasAttended || isLoading) Color.Gray else Color(0xFF4CAF50)
                        ),
                        enabled = !hasAttended && !isLoading,
                        shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(text = if (hasAttended) "Sudah Absen" else "Absensi", color = Color.White)
                        }
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

                    if (showInvalidTime) {
                        Snackbar(
                            modifier = Modifier.padding(16.dp),
                            action = {
                                TextButton(onClick = { showInvalidTime = false }) {
                                    Text("Tutup", color = Color.White)
                                }
                            }
                        ) {
                            Text("Waktu absensi tidak sesuai dengan shift!", color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (allowedKoor) {

                    Button(
                        onClick = {
                            val intent = Intent(context, TambahAgendaSerahTerimaActivity::class.java).apply {
                                putExtra("briefingId", briefingId) // 👈 kirim agenda ID
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
                        shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
                    ) {
                        Text("Tambah Agenda Serah Terima", color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (allowedKoor || userName in allowedNames) {
                    Button(
                        onClick = {
                            firestore.collection("agenda").document(briefingId)
                                .update("status", "selesai")
                                .addOnSuccessListener {
                                    Log.d(
                                        "FirestoreUpdate",
                                        "Status berhasil diperbarui menjadi selesai"
                                    )

                                    val intent = (context as? Activity)?.intent
                                    context.startActivity(intent)
                                    (context as? Activity)?.finish()
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

    @Composable
    fun RekapCard(title: String, value: String, modifier: Modifier = Modifier) {
        Card(
            modifier = modifier
                .padding(horizontal = 4.dp)
                .height(100.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = Color(0xFF0E73A7), // Ganti dengan biru utama aplikasimu
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF0E73A7), // Warna biru juga
                        fontSize = 12.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

    }

    @Composable
    fun AttendanceBarChart(
        attendanceShift1: Int,
        attendanceShift2: Int,
        attendanceShift3: Int
    ) {
        val maxAttendance = listOf(attendanceShift1, attendanceShift2, attendanceShift3).maxOrNull() ?: 0
        val barColor = Color(0xFF0E73A7) // Warna biru yang kamu minta

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Jumlah Kehadiran per Shift",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp), // tambahkan tinggi sedikit
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                SimpleBar("Shift 1", attendanceShift1, maxAttendance, barColor)
                SimpleBar("Shift 2", attendanceShift2, maxAttendance, barColor)
                SimpleBar("Shift 3", attendanceShift3, maxAttendance, barColor)
            }
        }
    }

    @Composable
    fun SimpleBar(label: String, value: Int, max: Int, color: Color) {
        val barHeightRatio = if (max == 0) 0f else value.toFloat() / max.toFloat()

        Column(
            modifier = Modifier
                .width(50.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // Nilai angka di atas batang
            Text(
                "$value",
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((120 * barHeightRatio).dp) // tinggi batang proporsional
                    .background(color, shape = RoundedCornerShape(4.dp))
            )
            Text(
                label,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
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

                                Log.d("FaceRecognition", "Akurasi wajah: ${similarity * 100}%")

                                if (similarity >= 0.4) {
                                    Toast.makeText(context, "Absensi berhasil!", Toast.LENGTH_SHORT).show()

                                    val intent = Intent(context, AbsensiResultActivity::class.java).apply {
                                        putExtra("briefingId", briefingId)
                                    }
                                    context.startActivity(intent)

                                } else {
                                    firestore.collection("agenda").document(briefingId)
                                        .collection("attendance").document(attendanceId)
                                        .delete()
                                        .addOnSuccessListener {
                                            Log.d("FaceRecognition", "Data attendance $attendanceId dihapus karena gagal absensi.")
                                        }
                                        .addOnFailureListener {
                                            Log.e("FaceRecognition", "Gagal menghapus data attendance $attendanceId.")
                                        }

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

    fun sendNotification(context: Context, title: String, message: String) {
        val channelId = "approval_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Approval Notification"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.notif) // ganti dengan icon yang kamu punya
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            with(NotificationManagerCompat.from(context)) {
                notify(101, builder.build())
            }
        } else {
            Log.w("Notif", "Permission not granted to show notifications.")
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

    fun validateLocation(
        firestore: FirebaseFirestore,
        userLocation: Location?,
        userTerminal: String,
        tempat: String,
        userRole: String,
        callback: (Boolean) -> Unit
    ) {
        if (userRole == "Branch Manager") {
            Log.d("GeoFence", "Branch Manager - melewati validasi lokasi")
            return callback(true)
        }
        Log.d("GeoFence", "Tempat Safety Briefing: $tempat")

        val allowedTerminals = when (userTerminal) {
            "Terminal Jamrud" -> listOf("Terminal Jamrud")
            "Terminal Mirah", "Terminal Nilam" -> listOf("Terminal Mirah", "Terminal Nilam")
            else -> emptyList()
        }

        if (!allowedTerminals.contains(tempat)) {
            Log.w("GeoFence", "Terminal user ($userTerminal) tidak diizinkan absen di $tempat")
            return callback(false)
        }

        firestore.collection("geoFence").document(tempat).get()
            .addOnSuccessListener { geoFenceDoc ->
                val geoPoint = geoFenceDoc.getGeoPoint("location")
                if (geoPoint != null) {
                    val latitude = geoPoint.latitude
                    val longitude = geoPoint.longitude

                    Log.d("GeoFence", "Koordinat lokasi dari Firestore: Latitude = $latitude, Longitude = $longitude")

                    val targetLocation = Location("").apply {
                        this.latitude = latitude
                        this.longitude = longitude
                    }

                    val distance = userLocation?.distanceTo(targetLocation) ?: Float.MAX_VALUE

                    // 🔹 Log jarak pengguna ke lokasi absen
                    Log.d("GeoFence", "Jarak pengguna ke lokasi absen: $distance meter")

                    callback(distance <= 150.0) // ✅ Izinkan absen jika dalam radius 150 meter
                } else {
                    Log.e("GeoFence", "GeoPoint tidak ditemukan dalam dokumen Firestore untuk terminal: $tempat")
                    callback(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e("GeoFence", "Gagal mengambil lokasi dari Firestore: ${e.message}", e)
                callback(false)
            }
    }

    @Composable
    fun DrawerContent(
        onCloseDrawer: () -> Unit,
        userName: String = "Nama Pengguna",
        branch: String = "Branch",
        terminal: String = "Terminal",
        group: String? = null
    ) {
        val context = LocalContext.current
        val auth = FirebaseAuth.getInstance()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header / User Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "User Icon",
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = userName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Branch: $branch", fontSize = 14.sp)
                        Text(text = "Terminal: $terminal", fontSize = 14.sp)
                        group?.let {
                            Text(text = "Group: $it", fontSize = 14.sp)
                        }
                    }
                }

                Divider(color = Color.LightGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(24.dp))

                // Tambahkan menu tambahan di sini
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Divider(color = Color.LightGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
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
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Logout",
                        tint = Color.Red
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Logout", color = Color.Red, fontSize = 16.sp)
                }
            }
        }
    }

    @Composable
    fun BottomNavigationBar() {
        val context = LocalContext.current
        var selectedItem by remember { mutableStateOf(0) }

        NavigationBar(
            containerColor = Color(0xFF0E73A7) // Mengubah warna latar belakang
        ) {
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

            NavigationBarItem(
                icon = {
                    Icon(
                        painterResource(id = if (selectedItem == 1) R.drawable.dashboard_filled else R.drawable.dashboard_stroke),
                        contentDescription = "Dashboard"
                    )
                },
                label = {
                    Text(
                        "Dashboard",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                },
                selected = selectedItem == 1,
                onClick = {
                    selectedItem = 1
                    context.startActivity(Intent(context, DashboardActivity::class.java))
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
                        painterResource(id = if (selectedItem == 2) R.drawable.history_filled else R.drawable.history_stroke),
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
                selected = selectedItem == 2,
                onClick = {
                    selectedItem = 2
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

    private fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap): Uri? {
        val filename = "profile_photo_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SafetyBriefing")
        }

        val contentResolver = context.contentResolver
        val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        imageUri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
        }
        return imageUri
    }
}


