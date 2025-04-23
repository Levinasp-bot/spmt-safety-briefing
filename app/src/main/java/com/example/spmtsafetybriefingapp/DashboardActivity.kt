package com.example.spmtsafetybriefingapp

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role.Companion.Button
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardActivity : ComponentActivity() {
    private lateinit var firestore: FirebaseFirestore

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestore = FirebaseFirestore.getInstance()

        setContent {
            AttendanceDashboard(firestore, this) // Tambahkan activity
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "UnrememberedMutableState")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AttendanceDashboard(firestore: FirebaseFirestore, activity: ComponentActivity) {
    val context = LocalContext.current
    var agenda by remember { mutableStateOf<Agenda_detail?>(null) }
    val activity = context as? ComponentActivity
    var attendanceList by rememberSaveable { mutableStateOf(listOf<Pair<String, String>>()) }
    var selectedTerminal by rememberSaveable { mutableStateOf("Semua Terminal") }
    var selectedShift by rememberSaveable { mutableStateOf("Semua Shift") }
    val terminalOptions = mutableStateListOf("Semua Terminal")

    val currentUser = FirebaseAuth.getInstance().currentUser
    val uid = currentUser?.uid


    uid?.let { userId ->
        FirebaseFirestore.getInstance().collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val branchName = document.getString("branch") ?: return@addOnSuccessListener
                    Log.d("TerminalFetch", "✅ Branch user: $branchName")

                    FirebaseFirestore.getInstance().collection("terminal")
                        .document(branchName)
                        .get()
                        .addOnSuccessListener { terminalDoc ->
                            if (terminalDoc.exists()) {
                                Log.d("TerminalFetch", "✅ Dokumen terminal ditemukan: ${terminalDoc.data}")

                                // Ambil langsung list terminal dari field 'terminal'
                                val terminalList = terminalDoc.get("terminal") as? List<*>
                                terminalList?.forEach { terminalName ->
                                    terminalName?.toString()?.let { terminalOptions.add(it) }
                                }

                                // Menambahkan "Semua Terminal" di awal daftar terminal
                                Log.d("TerminalFetch", "✅ Terminal options final: $terminalOptions")
                            } else {
                                Log.e("TerminalFetch", "❌ Dokumen terminal '$branchName' tidak ditemukan")
                            }
                        }
                        .addOnFailureListener {
                            Log.e("TerminalFetch", "❌ Gagal ambil dokumen terminal: ${it.message}")
                        }

                } else {
                    Log.e("TerminalFetch", "❌ Dokumen user tidak ditemukan")
                }
            }
            .addOnFailureListener {
                Log.e("TerminalFetch", "❌ Gagal ambil data user: ${it.message}")
            }
    } ?: Log.e("TerminalFetch", "❌ User belum login")

    var selectedDate by rememberSaveable {
        mutableStateOf(
            SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.getDefault()
            ).format(Date())
        )
    }
    var datePickerDialogVisible by remember { mutableStateOf(false) }
    var totalUsers by rememberSaveable { mutableStateOf(0) }
    var absentUsers by rememberSaveable { mutableStateOf(0) }
    var presentUsers by rememberSaveable { mutableStateOf(0) }
    var cutiList by rememberSaveable { mutableStateOf(setOf<String>()) }
    var sakitList by rememberSaveable { mutableStateOf(setOf<String>()) }
    var izinList by rememberSaveable { mutableStateOf(setOf<String>()) }
    var filteredUsers by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var briefingId by rememberSaveable { mutableStateOf<String?>(null) }
    val scaffoldState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val attendanceCache = remember { mutableStateMapOf<String, CachedResult>() }

    LaunchedEffect(selectedShift.hashCode()) {
        Log.d(
            "Firestore",
            "🚀 LaunchedEffect Triggered! Shift: $selectedShift (Hash: ${selectedShift.hashCode()})"
        )
    }
    SideEffect {
        Log.d("Firestore", "🔄 SideEffect Triggered! Current selectedShift: $selectedShift")
    }

    LaunchedEffect(selectedDate) {
        try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val selectedDateStart = sdf.parse(selectedDate) ?: return@LaunchedEffect
            val calendar = Calendar.getInstance().apply {
                time = selectedDateStart
                add(Calendar.DAY_OF_MONTH, 1)
            }
            val selectedDateEnd = calendar.time

            val startTimestamp = Timestamp(selectedDateStart)
            val endTimestamp = Timestamp(selectedDateEnd)

            val agendaDocs = firestore.collection("agenda")
                .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
                .whereLessThan("timestamp", endTimestamp)
                .get()
                .await()

            if (agendaDocs.isEmpty) {
                Log.d("Firestore", "❌ Tidak ada agenda ditemukan pada $selectedDate")
                cutiList = emptySet()
                sakitList = emptySet()
                izinList = emptySet()
                return@LaunchedEffect
            }

            cutiList = agendaDocs.documents
                .flatMap { it.get("cuti") as? List<String> ?: emptyList() }
                .toSet()

            sakitList = agendaDocs.documents
                .flatMap { it.get("sakit") as? List<String> ?: emptyList() }
                .toSet()

            izinList = agendaDocs.documents
                .flatMap { it.get("izin") as? List<String> ?: emptyList() }
                .toSet()

            Log.d("Firestore", "✅ Agenda ditemukan: ${agendaDocs.size()}")
            Log.d("Firestore", "📌 Cuti: $cutiList")
            Log.d("Firestore", "📌 Sakit: $sakitList")
            Log.d("Firestore", "📌 Izin: $izinList")

        } catch (e: Exception) {
            Log.e("Firestore", "❗Gagal mengambil data agenda: ${e.message}", e)
        }
    }

    LaunchedEffect(key1 = selectedTerminal, key2 = selectedDate, key3 = selectedShift) {
        val cacheKey = "$selectedTerminal|$selectedDate|$selectedShift"
        val cached = attendanceCache[cacheKey]

        if (cached != null) {
            Log.d("Firestore", "✅ Using cached data for $cacheKey")
            attendanceList = cached.attendanceList
            presentUsers = cached.presentUsers
            totalUsers = cached.totalUsers
            absentUsers = cached.absentUsers
            filteredUsers = cached.filteredUsers
            return@LaunchedEffect
        }

        Log.d("Firestore", "🚀 LaunchedEffect Triggered! Shift: $selectedShift")
        try {
            Log.d(
                "Firestore",
                "Fetching agendas for terminal: $selectedTerminal on date: $selectedDate with shift: $selectedShift"
            )

            attendanceList = emptyList()
            presentUsers = 0
            totalUsers = 0
            absentUsers = 0
            filteredUsers = emptyList()

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val selectedDateStart = sdf.parse(selectedDate) ?: return@LaunchedEffect
            val calendar = Calendar.getInstance().apply {
                time = selectedDateStart
                add(Calendar.DAY_OF_MONTH, 1)
            }
            val selectedDateEnd = calendar.time

            val startTimestamp = Timestamp(selectedDateStart)
            val endTimestamp = Timestamp(selectedDateEnd)

            var query = firestore.collection("agenda")
                .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
                .whereLessThan("timestamp", endTimestamp)

            // Jika shift dipilih, tambahkan filter shift
            if (!selectedShift.isNullOrEmpty() && selectedShift != "Semua Shift") {
                query = query.whereEqualTo("shift", selectedShift)
            } else {
                Log.d("Firestore", "No shift filter applied, fetching all shifts")
            }

// Jika terminal spesifik dipilih, tambahkan filter terminal
            if (!selectedTerminal.isNullOrEmpty() && selectedTerminal != "Semua Terminal") {
                query = query.whereEqualTo("terminal", selectedTerminal)
            } else {
                Log.d("Firestore", "No terminal filter applied, fetching all terminals")
            }

            val allAgendas = query.get().await().documents

            if (allAgendas.isEmpty()) {
                Log.d("Firestore", "⚠ No matching agendas found for shift: $selectedShift and terminal: $selectedTerminal")
                return@LaunchedEffect
            }


            allAgendas.forEach { agenda ->
                Log.d(
                    "Firestore",
                    "Agenda Found: ID=${agenda.id}, Terminal=${agenda.getString("terminal")}, Shift=${
                        agenda.getString("shift")
                    }"
                )
            }

            val terminalsFromAgenda = allAgendas.mapNotNull { it.getString("terminal") }.distinct()
            val groupsFromAgenda = allAgendas.mapNotNull { it.getString("group") }.distinct()
            Log.d("Firestore", "Terminals found in agenda: $terminalsFromAgenda")
            Log.d("Firestore", "Groups found in agenda: $groupsFromAgenda")

            val validTerminals =
                if (selectedTerminal == "Semua Terminal") terminalsFromAgenda else listOf(
                    selectedTerminal
                )
            if (!validTerminals.any { it in terminalsFromAgenda }) {
                Log.d("Firestore", "Selected terminal ($selectedTerminal) is not in the agenda")
                return@LaunchedEffect
            }

            val terminalBatches = validTerminals.chunked(10)
            val attendedUsers = mutableSetOf<String>()
            val allAttendances = mutableListOf<Pair<String, String>>()

            allAgendas.forEach { agenda ->
                val briefingId = agenda.id
                val agendaTerminal = agenda.getString("terminal") ?: ""

                if (agendaTerminal !in validTerminals) {
                    Log.d("Firestore", "Skipping agenda for terminal: $agendaTerminal")
                    return@forEach
                }

                val attendanceRef = firestore.collection("agenda")
                    .document(briefingId)
                    .collection("attendance")
                    .get()
                    .await()

                val attendances = attendanceRef.documents.mapNotNull { doc ->
                    val name = doc.getString("userName") ?: "Unknown"
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()?.let {
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it) // 🔥 Format tanggal & jam
                    } ?: "-"

                    attendedUsers.add(name)
                    name to timestamp
                }
                allAttendances.addAll(attendances)
            }

            val terminalChunks = validTerminals.chunked(10)  // Maksimal 10 per batch
            val groupChunks = groupsFromAgenda.chunked(10)  // Maksimal 10 per batch

            val userDocs = try {
                firestore.collection("users")
                    .limit(1000) // Tambahkan limit lebih besar dari jumlah user yang ada
                    .get()
                    .await()
                    .documents
            } catch (e: FirebaseFirestoreException) {
                Log.e("Firestore", "Error fetching users: ${e.message}")
                emptyList()
            }

            val groupsByTerminal = allAgendas.groupBy(
                { it.getString("terminal") ?: "" },  // Kunci: Terminal
                { it.getString("group") ?: "" }      // Nilai: Group
            ).mapValues { it.value.distinct() }  // Hilangkan duplikasi dalam setiap terminal

            val allUsers = userDocs.filter { doc ->
                val userTerminal = doc.getString("terminal") ?: ""
                val userGroup = doc.getString("group") ?: ""
                val role = doc.getString("role") ?: ""

                val validGroups = groupsByTerminal[userTerminal] ?: emptyList()

                val isKoordinator = role.startsWith("Koordinator")
                val isAllowedRole = role in listOf(
                    "Anggota Pengamanan",
                    "Operasional",
                    "Komandan Peleton"
                ) || isKoordinator

                userTerminal in validTerminals &&
                        userGroup in validGroups &&
                        isAllowedRole
            }

            filteredUsers = allUsers.filter { doc ->
                val userTerminal = doc.getString("terminal") ?: ""
                val userGroup = doc.getString("group") ?: ""

                val terminalMatch = userTerminal in validTerminals
                val groupMatch = userGroup in groupsFromAgenda

                val isValid = terminalMatch && groupMatch
                if (isValid) Log.d(
                    "Firestore",
                    "User Valid: ID=${doc.id}, Name=${doc.getString("name")}, Terminal=$userTerminal, Group=$userGroup"
                )

                isValid
            }.map { it.getString("name") ?: "" }

            totalUsers = filteredUsers.size
            presentUsers =
                attendedUsers.count { it in filteredUsers }  // 🔹 Hanya hitung user yang valid
            absentUsers = totalUsers - presentUsers

            attendanceList = allAttendances

            Log.d(
                "Firestore",
                "Total Users: $totalUsers, Present Users: $presentUsers, Absent Users: $absentUsers"
            )

            attendanceCache[cacheKey] = CachedResult(
                attendanceList = allAttendances,
                presentUsers = presentUsers,
                totalUsers = totalUsers,
                absentUsers = absentUsers,
                filteredUsers = filteredUsers
            )

        } catch (e: Exception) {
            Log.e("Firestore", "Error fetching attendance: ${e.message}")
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
            bottomBar = { BottomNavigationBarDashboard() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp) ,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Dashboard Absensi",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                var expandedTerminal by remember { mutableStateOf(false) }
                var expandedShift by remember { mutableStateOf(false) }

                val shiftOptions = listOf(
                    "Semua Shift",
                    "Shift 1 08:00 - 16:00",
                    "Shift 2 16:00 - 00:00",
                    "Shift 3 00:00 - 08:00"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(0.4f)) {
                        OutlinedButton(
                            onClick = { expandedTerminal = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            border = ButtonDefaults.outlinedButtonBorder,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = selectedTerminal, fontSize = 12.sp, color = Color.Gray)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown Icon",
                                    tint = Color.Gray
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedTerminal,
                            onDismissRequest = { expandedTerminal = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            terminalOptions.forEach { terminal ->
                                DropdownMenuItem(
                                    text = { Text(text = terminal, fontSize = 12.sp) },
                                    onClick = {
                                        selectedTerminal = terminal
                                        expandedTerminal = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(0.4f)) {
                        OutlinedButton(
                            onClick = { expandedShift = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            border = ButtonDefaults.outlinedButtonBorder,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = selectedShift, fontSize = 12.sp, color = Color.Gray)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown Icon",
                                    tint = Color.Gray
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedShift,
                            onDismissRequest = { expandedShift = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            shiftOptions.forEach { shift ->
                                DropdownMenuItem(
                                    text = { Text(text = shift, fontSize = 12.sp) },
                                    onClick = {
                                        if (selectedShift != shift) {
                                            selectedShift = ""
                                            selectedShift = shift
                                        } else {
                                            Log.d(
                                                "Firestore",
                                                "❌ State NOT changing, still: $selectedShift"
                                            )
                                        }
                                        expandedShift = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { datePickerDialogVisible = true },
                        modifier = Modifier
                            .weight(0.18f)
                            .height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Select Date",
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                if (datePickerDialogVisible) {
                    val context = LocalContext.current
                    val calendar = Calendar.getInstance()
                    val datePicker = DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            selectedDate =
                                String.format("%02d/%02d/%d", dayOfMonth, month + 1, year)
                            datePickerDialogVisible = false
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                    datePicker.show()
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
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

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
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

                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.weight(1f))
                {
                    if (filteredUsers.isEmpty()) {
                        Text(
                            text = "Belum ada data pengguna",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom=125.dp)) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0E73A7))
                                        .padding(vertical = 6.dp), // Lebih kecil dari 8.dp
                                    horizontalArrangement = Arrangement.Center // Center semua item dalam row
                                ) {
                                    Text(
                                        text = "No",
                                        color = Color.White,
                                        fontSize = 13.sp, // Font lebih kecil
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .weight(0.3f)
                                            .padding(horizontal = 4.dp)
                                            .align(Alignment.CenterVertically)
                                    )
                                    Text(
                                        text = "Nama",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                            .align(Alignment.CenterVertically)
                                    )
                                    Text(
                                        text = "Waktu Kehadiran",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                            .align(Alignment.CenterVertically)
                                    )
                                    Text(
                                        text = "Keterangan",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                            .align(Alignment.CenterVertically)
                                    )
                                }
                            }

                            val sortedUsers = filteredUsers.map { name ->
                                val timestamp = attendanceList.find { it.first == name }?.second
                                Pair(name, timestamp)
                            }.sortedByDescending { it.second }

                            itemsIndexed(sortedUsers) { index, (name, timestamp) ->
                                val status = when {
                                    name in cutiList -> "Cuti"
                                    name in sakitList -> "Sakit"
                                    name in izinList -> "Izin"
                                    timestamp != null -> "Hadir"
                                    else -> "Tanpa Keterangan"
                                }

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
                                            text = "${index + 1}",
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(0.2f).padding(start = 8.dp)
                                        )
                                        Text(
                                            text = name,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                                        )
                                        Text(
                                            text = timestamp ?: "-",
                                            fontSize = 12.sp, // Ukuran font lebih kecil
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center, // Pusatkan teks
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                                .align(Alignment.CenterVertically) // Pusatkan vertikal
                                        )
                                        if (status == "Hadir") {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Hadir",
                                                tint = Color.Green,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                        } else {
                                            Text(
                                                text = status,
                                                fontSize = 14.sp,
                                                color = Color.Red,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                        }
                                    }
                                    Divider(color = Color.LightGray, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp) // Opsional: Padding agar tidak mepet layar
            ) {
                 //
                Button(
                    onClick = {
                        activity?.let {
                            val intent = Intent(it, UnduhAbsensiActivity::class.java).apply {
                                putExtra("selectedTerminal", selectedTerminal)
                                putExtra("selectedShift", selectedShift)
                                putExtra("selectedDate", selectedDate)
                                putExtra("totalUsers", totalUsers)
                                putExtra("absentUsers", absentUsers)
                                putExtra("presentUsers", presentUsers)
                                putStringArrayListExtra("cutiList", ArrayList(cutiList))
                                putStringArrayListExtra("sakitList", ArrayList(sakitList))
                                putStringArrayListExtra("izinList", ArrayList(izinList))
                                putStringArrayListExtra("filteredUsers", ArrayList(filteredUsers))
                                putExtra("briefingId", briefingId)
                                val attendanceListString = attendanceList.map { "${it.first} - ${it.second}" } as ArrayList<String>
                                putStringArrayListExtra("attendanceList", attendanceListString)
                            }
                            it.startActivity(intent)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp)
                        .align(Alignment.BottomCenter) // Memastikan tombol ada di bawah
                        .testTag("unduh_pdf_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
                    shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
                ) {
                    Text("Unduh Laporan Absensi", color = Color.White)
                }
            }
        }
    }
}

data class CachedResult(
    val attendanceList: List<Pair<String, String>>,
    val presentUsers: Int,
    val totalUsers: Int,
    val absentUsers: Int,
    val filteredUsers: List<String>
)


@Composable
fun BottomNavigationBarDashboard() {
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf(0) }

    NavigationBar(
        containerColor = Color(0xFF0E73A7) // Mengubah warna latar belakang
    ) {
        // 🔹 Item Beranda
        NavigationBarItem(
            icon = {
                Icon(
                    painterResource(id = if (selectedItem == 0) R.drawable.home_stroke else R.drawable.home_filled),
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

        // 🔹 Item Dashboard (Menu Tengah)
        NavigationBarItem(
            icon = {
                Icon(
                    painterResource(id = if (selectedItem == 1) R.drawable.dashboard_stroke else R.drawable.dashboard_filled),
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

        // 🔹 Item Riwayat
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
