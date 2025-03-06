package com.example.spmtsafetybriefingapp

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class FormSafetyBriefingActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FormSafetyBriefingScreen { data -> saveToFirestore(data) }
        }
    }

    @Composable
    fun FormSafetyBriefingScreen(onSaveData: (Map<String, Any>) -> Unit) {
        var terminal by remember { mutableStateOf("") }
        var shift by remember { mutableStateOf("") }
        var koordinator by remember { mutableStateOf("") }
        var groupSecurity by remember { mutableStateOf("") }
        var groupOperational by remember { mutableStateOf("") }
        var agendaList by remember { mutableStateOf(listOf(TextFieldValue(""))) }
        var imageUri by remember { mutableStateOf<Uri?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        val context = LocalContext.current

        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                imageUri = saveImageLocally(bitmap)
            }
        }

        val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { imageUri = it }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Laporan Safety Briefing",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            DropdownMenuInput("Terminal", terminal, { terminal = it }, listOf("Terminal Jamrud", "Terminal Nilam", "Terminal Mirah"))
            DropdownMenuInput("Shift", shift, { shift = it }, listOf("Shift 1 00:00 - 08:00", "Shift 2 08:00 - 16:00", "Shift 3 16:00 - 00:00"))
            DropdownMenuInput("Koordinator", koordinator, { koordinator = it }, listOf("John Doe", "Jane Doe"))
            DropdownMenuInput("Group Security", groupSecurity, { groupSecurity = it }, listOf("Group A", "Group B", "Group C", "Group D"))
            DropdownMenuInput("Group Operational", groupOperational, { groupOperational = it }, listOf("Group A", "Group B", "Group C", "Group D"))

            agendaList.forEachIndexed { index, textFieldValue ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            agendaList = agendaList.toMutableList().apply { this[index] = newValue }
                        },
                        label = { Text("Agenda ${index + 1}") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    if (index == agendaList.size - 1) {
                        IconButton(onClick = { agendaList = agendaList + TextFieldValue("") }) {
                            Icon(painterResource(id = android.R.drawable.ic_input_add), contentDescription = "Tambah Agenda")
                        }
                    }
                }
            }

            imageUri?.let {
                Image(
                    painter = rememberImagePainter(it),
                    contentDescription = "Dokumentasi",
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { cameraLauncher.launch(null) }) {
                    Text("Ambil Foto")
                }
                Button(onClick = { galleryLauncher.launch("image/*") }) {
                    Text("Pilih dari Galeri")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (isLoading) {
                CircularProgressIndicator()
            }
            Button(
                onClick = {
                    isLoading = true
                    val data = mapOf(
                        "terminal" to terminal,
                        "shift" to shift,
                        "koordinator" to koordinator,
                        "groupSecurity" to groupSecurity,
                        "groupOperational" to groupOperational,
                        "agenda" to agendaList.map { it.text },
                        "photoPath" to (imageUri?.toString() ?: ""),
                        "timestamp" to System.currentTimeMillis()
                    )
                    onSaveData(data)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Simpan Data")
            }
        }
    }

    private fun saveImageLocally(bitmap: Bitmap): Uri {
        val file = File(filesDir, "captured_image_${System.currentTimeMillis()}.jpg")
        return try {
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            Uri.fromFile(file)
        } catch (e: IOException) {
            Log.e("SaveImage", "Gagal menyimpan gambar", e)
            Uri.EMPTY
        }
    }

    private fun saveToFirestore(data: Map<String, Any>) {
        val documentRef = firestore.collection("agenda").document() // Buat dokumen baru
        val briefingId = documentRef.id // Ambil nama dokumen sebagai briefingId

        val updatedData = data.toMutableMap()
        updatedData["briefingId"] = briefingId // Tambahkan briefingId ke dalam data
        updatedData["status"] = "aktif" // Tambahkan status dengan nilai "aktif"
        updatedData["timestamp"] = FieldValue.serverTimestamp() // Tambahkan timestamp otomatis dari server

        documentRef.set(updatedData)
            .addOnSuccessListener {
                Toast.makeText(this, "Data tersimpan", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, HomeActivity::class.java)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal menyimpan data", Toast.LENGTH_SHORT).show()
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenuInput(label: String, value: String, onValueChange: (String) -> Unit, options: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(painterResource(id = android.R.drawable.arrow_down_float), contentDescription = "Dropdown Icon")
                }
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFF0E73A7),
                unfocusedBorderColor = Color(0xFF0E73A7)
            )
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
