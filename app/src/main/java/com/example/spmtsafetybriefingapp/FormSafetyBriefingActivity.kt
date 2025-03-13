package com.example.spmtsafetybriefingapp

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

class FormSafetyBriefingActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()
    private val cloudinaryUrl = "https://api.cloudinary.com/v1_1/deki7dwe5/image/upload"
    private val apiKey = "735724334454793"
    private val apiSecret = "Cf_XNHVtkyQwvd8NM8BfFx3a0oE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FormSafetyBriefingScreen { rawData, imageBitmap ->
                val data = rawData.toMutableMap() // Konversi ke MutableMap

                if (imageBitmap != null) {
                    uploadImageToCloudinary(imageBitmap) { imageUrl ->
                        data["photoPath"] = imageUrl ?: "" // Pastikan tidak null
                        saveToFirestore(data)
                    }
                } else {
                    saveToFirestore(data)
                }
            }
        }
    }

    @Composable
    fun FormSafetyBriefingScreen(onSaveData: (Map<String, Any>, Bitmap?) -> Unit) {
        var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }

        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            imageBitmap = bitmap
        }

        var terminal by remember { mutableStateOf("") }
        var shift by remember { mutableStateOf("") }
        var koordinator by remember { mutableStateOf("") }
        var group by remember { mutableStateOf("") }
        //var groupOperational by remember { mutableStateOf("") }
        var agendaList by remember { mutableStateOf(listOf(TextFieldValue(""))) }
        var sakitList by remember { mutableStateOf(listOf(TextFieldValue(""))) }
        var cutiList by remember { mutableStateOf(listOf(TextFieldValue(""))) }
        var izinList by remember { mutableStateOf(listOf(TextFieldValue(""))) }
        var tanpaketList by remember { mutableStateOf(listOf(TextFieldValue(""))) }
        var isLoading by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                    imageBitmap = bitmap
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
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
            DropdownMenuInput("Shift", shift, { shift = it }, listOf("Shift 1 08:00 - 16:00", "Shift 2 16:00 - 00:00", "Shift 3 00:00 - 08:00"))
            //DropdownMenuInput("Koordinator", koordinator, { koordinator = it }, listOf("Darman", "Umar Hotob", ))
            DropdownMenuInput("Group", group, { group = it }, listOf("Group A", "Group B", "Group C", "Group D"))
            //DropdownMenuInput("Group Operational", groupOperational, { groupOperational = it }, listOf("Group A", "Group B", "Group C", "Group D"))

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

            sakitList.forEachIndexed { index, textFieldValue ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            sakitList = sakitList.toMutableList().apply { this[index] = newValue }
                        },
                        label = { Text("Nama Pekerja Sakit ${index + 1}") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    if (index == agendaList.size - 1) {
                        IconButton(onClick = { agendaList = agendaList + TextFieldValue("") }) {
                            Icon(painterResource(id = android.R.drawable.ic_input_add), contentDescription = "Tambah Agenda")
                        }
                    }
                }
            }

            cutiList.forEachIndexed { index, textFieldValue ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            cutiList = cutiList.toMutableList().apply { this[index] = newValue }
                        },
                        label = { Text("Nama Pekerja Cuti ${index + 1}") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    if (index == cutiList.size - 1) {
                        IconButton(onClick = { cutiList = cutiList + TextFieldValue("") }) {
                            Icon(painterResource(id = android.R.drawable.ic_input_add), contentDescription = "Tambah Agenda")
                        }
                    }
                }
            }

            izinList.forEachIndexed { index, textFieldValue ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            izinList = izinList.toMutableList().apply { this[index] = newValue }
                        },
                        label = { Text("Nama Pekerja Izin ${index + 1}") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    if (index == izinList.size - 1) {
                        IconButton(onClick = { izinList = izinList + TextFieldValue("") }) {
                            Icon(painterResource(id = android.R.drawable.ic_input_add), contentDescription = "Tambah Agenda")
                        }
                    }
                }
            }

            tanpaketList.forEachIndexed { index, textFieldValue ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            tanpaketList = tanpaketList.toMutableList().apply { this[index] = newValue }
                        },
                        label = { Text("Nama Pekerja Tanpa Keterangan ${index + 1}") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    if (index == tanpaketList.size - 1) {
                        IconButton(onClick = { tanpaketList = tanpaketList + TextFieldValue("") }) {
                            Icon(painterResource(id = android.R.drawable.ic_input_add), contentDescription = "Tambah Agenda")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            imageBitmap?.let { bitmap ->
                Image(
                    painter = remember { BitmapPainter(bitmap.asImageBitmap()) },
                    contentDescription = "Dokumentasi",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                        .background(Color.LightGray)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth(0.34f)
                        .height(40.dp)
                        .testTag("ambil_foto"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
                    shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
                ) {
                    Text("Ambil Foto")
                }
                Button(onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth(0.64f)
                        .height(40.dp)
                        .testTag("pilih_foto"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
                    shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
                ){
                    Text("Pilih dari Galeri")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator()
            }

            var isLoading by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tombol untuk menyimpan data
                Button(
                    onClick = {
                        if (imageBitmap == null) {
                            Toast.makeText(context, "Foto dokumentasi kosong", Toast.LENGTH_SHORT).show()
                        } else {
                            // Set isLoading menjadi true untuk menunjukkan progress bar
                            isLoading = true

                            val data = mapOf(
                                "terminal" to terminal,
                                "shift" to shift,
                                "koordinator" to koordinator,
                                "group" to group,
                                //"groupOperational" to groupOperational,
                                "agenda" to agendaList.map { it.text },
                                "sakit" to sakitList.map { it.text },
                                "izin" to izinList.map { it.text },
                                "cuti" to cutiList.map { it.text },
                                "tanpaKeterangan" to tanpaketList.map { it.text },
                                "timestamp" to System.currentTimeMillis()
                            )

                            onSaveData(data, imageBitmap)

                            isLoading = false
                        }
                    },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("simpan_data"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
                shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
            ) {
                Text("Simpan Data")
            }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }
        }
    }

    fun uploadImageToCloudinary(bitmap: Bitmap, onResult: (String?) -> Unit) {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val imageData = baos.toByteArray()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "image.jpg", RequestBody.create("image/*".toMediaTypeOrNull(), imageData))
            .addFormDataPart("upload_preset", "ml_default") // Pastikan ini sesuai dengan Cloudinary Anda
            .build()

        val request = Request.Builder()
            .url(cloudinaryUrl) // Pastikan cloudinaryUrl benar
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CloudinaryUpload", "Gagal mengunggah gambar: ${e.message}", e)
                e.printStackTrace()
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    Log.e("CloudinaryUpload", "Upload gagal. Response: $responseBody")
                    onResult(null)
                    return
                }

                try {
                    val jsonObject = JSONObject(responseBody)
                    val imageUrl = jsonObject.optString("secure_url", "")

                    if (imageUrl.isNotEmpty()) {
                        Log.d("CloudinaryUpload", "Upload berhasil! URL: $imageUrl")
                        onResult(imageUrl)
                    } else {
                        Log.e("CloudinaryUpload", "Response tidak mengandung secure_url: $responseBody")
                        onResult(null)
                    }
                } catch (e: JSONException) {
                    Log.e("CloudinaryUpload", "Error parsing JSON: ${e.message}", e)
                    onResult(null)
                }
            }
        })
    }

    private fun saveToFirestore(data: MutableMap<String, Any>) {
        val documentRef = firestore.collection("agenda").document()
        data["briefingId"] = documentRef.id
        data["status"] = "aktif"
        data["timestamp"] = FieldValue.serverTimestamp()

        documentRef.set(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Data tersimpan", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
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
