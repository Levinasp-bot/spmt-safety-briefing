package com.example.spmtsafetybriefingapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class FormSafetyBriefingActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference

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
                val uri = saveImageLocally(bitmap)
                imageUri = uri
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)) {
            Text(
                text = "Laporan Safety Briefing",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0E73A7),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).align(Alignment.CenterHorizontally)
            )

            DropdownMenuInput("Terminal", terminal, { terminal = it }, listOf("Terminal 1", "Terminal 2"))
            DropdownMenuInput("Shift", shift, { shift = it }, listOf("Pagi", "Siang", "Malam"))
            DropdownMenuInput("Koordinator Briefing", koordinator, { koordinator = it }, listOf("John Doe", "Jane Doe"))
            DropdownMenuInput("Group Security", groupSecurity, { groupSecurity = it }, listOf("Alpha", "Bravo"))
            DropdownMenuInput("Group Operational", groupOperational, { groupOperational = it }, listOf("Ops 1", "Ops 2"))

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

            Button(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0E73A7)),
                border = ButtonDefaults.outlinedButtonBorder,
                shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
            ) {
                Text("Ambil Dokumentasi")
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (isLoading) {
                CircularProgressIndicator()
            }
            Button(
                onClick = {
                    isLoading = true
                    val data = hashMapOf(
                        "terminal" to terminal,
                        "shift" to shift,
                        "koordinator" to koordinator,
                        "groupSecurity" to groupSecurity,
                        "groupOperational" to groupOperational,
                        "agenda" to agendaList.map { it.text },
                        "photoPath" to (imageUri?.path ?: ""),
                        "timestamp" to System.currentTimeMillis()
                    )
                    onSaveData(data)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7))
            ) {
                Text("Simpan Data")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FormSafetyBriefingScreen { data -> saveToFirestore(data) }
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
        firestore.collection("agenda").add(data)
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

@Composable
fun FormSafetyBriefingScreen(
    onTakePhoto: () -> Unit,
    onSaveData: (Map<String, Any>) -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
    capturedImage: Bitmap?,
    isPhotoTaken: Boolean
) {
    var terminal by remember { mutableStateOf("") }
    var shift by remember { mutableStateOf("") }
    var koordinator by remember { mutableStateOf("") }
    var groupSecurity by remember { mutableStateOf("") }
    var groupOperational by remember { mutableStateOf("") }
    var agendaList by remember { mutableStateOf(listOf(TextFieldValue(""))) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Laporan Safety Briefing",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0E73A7),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).align(Alignment.CenterHorizontally)
        )

        DropdownMenuInput("Terminal", terminal, { terminal = it }, listOf("Terminal 1", "Terminal 2"))
        DropdownMenuInput("Shift", shift, { shift = it }, listOf("Pagi", "Siang", "Malam"))
        DropdownMenuInput("Koordinator Briefing", koordinator, { koordinator = it }, listOf("John Doe", "Jane Doe"))
        DropdownMenuInput("Group Security", groupSecurity, { groupSecurity = it }, listOf("Alpha", "Bravo"))
        DropdownMenuInput("Group Operational", groupOperational, { groupOperational = it }, listOf("Ops 1", "Ops 2"))

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

        Button(
            onClick = onTakePhoto,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0E73A7))
        ) {
            Text("Ambil Dokumentasi")
        }

        if (capturedImage != null) {
            Image(
                bitmap = capturedImage.asImageBitmap(),
                contentDescription = "Dokumentasi",
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp)
            )
        }

        Button(
            onClick = { onSaveData(mapOf("terminal" to terminal, "shift" to shift, "koordinator" to koordinator, "groupSecurity" to groupSecurity, "groupOperational" to groupOperational, "agenda" to agendaList.map { it.text })) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7))
        ) {
            Text("Selanjutnya")
        }
    }
}
