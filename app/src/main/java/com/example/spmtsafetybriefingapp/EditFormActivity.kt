package com.example.spmtsafetybriefingapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.io.File
import java.io.IOException
import android.content.Context


class EditFormActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()
    private val cloudinaryUrl = "https://api.cloudinary.com/v1_1/deki7dwe5/image/upload"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val briefingId = intent.getStringExtra("briefingId") ?: ""
        Log.d("Firestore", "briefingId yang diterima: $briefingId")

        setContent {
            Log.d("Firestore", "Masuk ke setContent") // 🔍 Debugging
            var isLoading by remember { mutableStateOf(false) }
            val context = LocalContext.current

            FormSafetyBriefingScreen(briefingId) { rawData, imageBitmap ->
                Log.d("Firestore", "FormSafetyBriefingScreen callback dijalankan")
                val data = rawData.toMutableMap()
                isLoading = true

                if (imageBitmap != null) {
                    Log.d("Firestore", "Gambar tersedia, mengupload ke Cloudinary...")
                    uploadImageToCloudinary(imageBitmap) { imageUrl ->
                        Log.d("Firestore", "Gambar diupload, imageUrl: $imageUrl")
                        data["photoPath"] = imageUrl ?: "" // Simpan URL ke data
                        saveToFirestore(briefingId, data) { success ->
                            handleFirestoreResult(context, success)
                            isLoading = false
                        }
                    }
                } else {
                    Log.d("Firestore", "Tidak ada gambar, langsung menyimpan ke Firestore")
                    saveToFirestore(briefingId, data) { success ->
                        handleFirestoreResult(context, success)
                        isLoading = false
                    }
                }
            }
        }
    }

    private fun handleFirestoreResult(context: Context, success: Boolean) {
        if (success) {
            Log.d("Firestore", "Data tersimpan di Firestore")
            Toast.makeText(context, "Data tersimpan", Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(context, HomeActivity::class.java))
        } else {
            Log.e("Firestore", "Gagal menyimpan data")
            Toast.makeText(context, "Gagal menyimpan data", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun FormSafetyBriefingScreen(
        briefingId: String, onSaveData: (Map<String, Any>, Bitmap?) -> Unit
    ) {
        var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }

        val cameraLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
                imageBitmap = bitmap
            }
        var selectedSakit by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedCuti by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedIzin by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedAtribut by remember { mutableStateOf<List<String>>(emptyList()) }
        var agendaList by remember { mutableStateOf(listOf(TextFieldValue(""))) }
        var terminal by remember { mutableStateOf("") }
        var tempat by remember { mutableStateOf("") }
        var shift by remember { mutableStateOf("") }
        var group by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var photoPath by remember { mutableStateOf<String?>(null) }

        val context = LocalContext.current
        val galleryLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                        imageBitmap = bitmap
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

        val allUsers = remember { mutableStateListOf<User>() }

        LaunchedEffect(Unit) {
            firestore.collection("users").get()
                .addOnSuccessListener { result ->
                    allUsers.clear()
                    for (document in result) {
                        val user = User(
                            name = document.getString("name") ?: "",
                            terminal = document.getString("terminal") ?: "",
                            group = document.getString("group") ?: ""
                        )
                        allUsers.add(user)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Gagal mengambil data users: ${e.message}")
                }
        }

        LaunchedEffect(briefingId) {
            if (briefingId.isNotEmpty()) {
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("agenda").document(briefingId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            terminal = document.getString("terminal") ?: ""
                            tempat = document.getString("tempat") ?: ""
                            shift = document.getString("shift") ?: ""
                            group = document.getString("group") ?: ""
                            selectedSakit = (document.get("sakit") as? List<String>) ?: emptyList()
                            selectedCuti = (document.get("cuti") as? List<String>) ?: emptyList()
                            selectedIzin = (document.get("izin") as? List<String>) ?: emptyList()
                            selectedAtribut =
                                (document.get("tlatribut") as? List<String>) ?: emptyList()
                            agendaList =
                                (document.get("agenda") as? List<String>)?.map { TextFieldValue(it) }
                                    ?: listOf(TextFieldValue(""))
                            photoPath = document.getString("photoPath")

                            Log.d("Firestore", "Data briefing berhasil diambil: $briefingId")
                        } else {
                            Log.e("Firestore", "Briefing tidak ditemukan.")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Gagal mengambil briefing: ${e.message}")
                    }
            }
        }

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val userId = currentUser.uid
            val db = FirebaseFirestore.getInstance()

            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        terminal = document.getString("terminal") ?: "Tidak Diketahui"
                        group = document.getString("group") ?: "Tidak Diketahui"

                        println("Terminal: $terminal, Group: $group")
                    } else {
                        println("Data user tidak ditemukan di Firestore.")
                    }
                }
                .addOnFailureListener { e ->
                    println("Gagal mengambil data user: ${e.message}")
                }
        } else {
            println("Tidak ada user yang login!")
        }

        val filteredUsers = allUsers.filter { user ->
            user.terminal == terminal && user.group == group
        }.map { it.name }

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

            DropdownMenu(
                "Tempat Briefing",
                tempat,
                { tempat = it },
                listOf("Terminal Jamrud", "Terminal Nilam", "Terminal Mirah")
            )
            DropdownMenu(
                "Shift",
                shift,
                { shift = it },
                listOf("Shift 1 08:00 - 16:00", "Shift 2 16:00 - 00:00", "Shift 3 00:00 - 08:00")
            )
            DropdownMenu(
                "Group",
                group,
                { group = it },
                listOf("Group A", "Group B", "Group C", "Group D")
            )

            Column {
                Text("Nama Pekerja Sakit")
                selectedSakit.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedSakit = selectedSakit - name }) {
                            Icon(
                                painterResource(id = android.R.drawable.ic_delete),
                                contentDescription = "Hapus"
                            )
                        }
                    }
                }
                Row {
                    DropdownMenu(
                        label = "Pilih Pekerja",
                        value = "",
                        onValueChange = { newValue ->
                            if (newValue.isNotEmpty() && !selectedSakit.contains(newValue)) {
                                selectedSakit = selectedSakit + newValue
                            }
                        },
                        options = filteredUsers
                    )
                    IconButton(onClick = {
                        if (selectedSakit.isNotEmpty()) {
                            selectedSakit = selectedSakit + ""
                        }
                    }) {
                        Icon(
                            painterResource(id = android.R.drawable.ic_input_add),
                            contentDescription = "Tambah"
                        )
                    }
                }
            }

            Column {
                Text("Nama Pekerja Cuti")
                selectedCuti.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedCuti = selectedCuti - name }) {
                            Icon(
                                painterResource(id = android.R.drawable.ic_delete),
                                contentDescription = "Hapus"
                            )
                        }
                    }
                }
                Row {
                    DropdownMenu(
                        label = "Pilih Pekerja",
                        value = "",
                        onValueChange = { newValue ->
                            if (newValue.isNotEmpty() && !selectedCuti.contains(newValue)) {
                                selectedCuti = selectedCuti + newValue
                            }
                        },
                        options = filteredUsers
                    )
                    IconButton(onClick = {
                        if (selectedCuti.isNotEmpty()) {
                            selectedCuti = selectedCuti + ""
                        }
                    }) {
                        Icon(
                            painterResource(id = android.R.drawable.ic_input_add),
                            contentDescription = "Tambah"
                        )
                    }
                }
            }

            Column {
                Text("Nama Pekerja Izin")
                selectedIzin.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedIzin = selectedIzin - name }) {
                            Icon(
                                painterResource(id = android.R.drawable.ic_delete),
                                contentDescription = "Hapus"
                            )
                        }
                    }
                }
                Row {
                    DropdownMenu(
                        label = "Pilih Pekerja",
                        value = "",
                        onValueChange = { newValue ->
                            if (newValue.isNotEmpty() && !selectedIzin.contains(newValue)) {
                                selectedIzin = selectedIzin + newValue
                            }
                        },
                        options = filteredUsers
                    )
                    IconButton(onClick = {
                        if (selectedIzin.isNotEmpty()) {
                            selectedIzin = selectedIzin + ""
                        }
                    }) {
                        Icon(
                            painterResource(id = android.R.drawable.ic_input_add),
                            contentDescription = "Tambah"
                        )
                    }
                }
            }

            Column {
                Text("Nama Pekerja Tidak Lengkap Atribut")
                selectedAtribut.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedAtribut = selectedAtribut - name }) {
                            Icon(
                                painterResource(id = android.R.drawable.ic_delete),
                                contentDescription = "Hapus"
                            )
                        }
                    }
                }
                Row {
                    DropdownMenu(
                        label = "Pilih Pekerja",
                        value = "",
                        onValueChange = { newValue ->
                            if (newValue.isNotEmpty() && !selectedAtribut.contains(newValue)) {
                                selectedAtribut = selectedAtribut + newValue
                            }
                        },
                        options = filteredUsers
                    )
                    IconButton(onClick = {
                        if (selectedAtribut.isNotEmpty()) {
                            selectedAtribut = selectedAtribut + ""
                        }
                    }) {
                        Icon(
                            painterResource(id = android.R.drawable.ic_input_add),
                            contentDescription = "Tambah"
                        )
                    }
                }
            }

            agendaList.forEachIndexed { index, textFieldValue ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            Icon(
                                painterResource(id = android.R.drawable.ic_input_add),
                                contentDescription = "Tambah Agenda"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            imageBitmap?.let { bitmap ->
                Image(
                    painter = rememberAsyncImagePainter(photoPath),
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

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
                    shape = RoundedCornerShape(8.dp)
                )
                {
                    Text("Ambil Foto")
                }
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
                    shape = RoundedCornerShape(8.dp)
                )
                {
                    Text("Pilih dari Galeri")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isLoading = true

                    val koordinator = when (terminal) {
                        "Terminal Jamrud" -> when (group) {
                            "Group A" -> "Rahmad Arif Nurcahyo"
                            "Group B" -> "M.Yusuf"
                            "Group C" -> "Darman"
                            "Group D" -> "Umar Hotob"
                            else -> "Tidak diketahui"
                        }

                        "Terminal Mirah" -> when (group) {
                            "Group A" -> "Asrul Edong"
                            "Group B" -> "Judik Agus Setiawan"
                            "Group C" -> "Yuli Kurinuda Pristiawan"
                            "Group D" -> "Anwar Sanusi"
                            else -> "Tidak diketahui"
                        }

                        "Terminal Nilam" -> when (group) {
                            "Group A" -> "Novem Afrianto"
                            "Group B" -> "Rudi Hariyono"
                            "Group C" -> "Hendra Surya"
                            "Group D" -> "Sugeng Setiawan"
                            else -> "Tidak diketahui"
                        }

                        else -> "Tidak diketahui"
                    }

                    val data = mapOf(
                        "terminal" to terminal,
                        "tempat" to tempat,
                        "shift" to shift,
                        "group" to group,
                        "koordinator" to koordinator,
                        "sakit" to selectedSakit,
                        "cuti" to selectedCuti,
                        "izin" to selectedIzin,
                        "tlatribut" to selectedAtribut,
                        "agenda" to agendaList.map { it.text },
                        "timestamp" to System.currentTimeMillis()
                    )

                    // Gunakan coroutine untuk menjalankan proses penyimpanan di background thread
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            onSaveData(data, imageBitmap)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Menyimpan Agenda...", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Gagal menyimpan data: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                isLoading = true
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Simpan Data")
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
            .addFormDataPart(
                "file",
                "image.jpg",
                RequestBody.create("image/*".toMediaTypeOrNull(), imageData)
            )
            .addFormDataPart(
                "upload_preset",
                "ml_default"
            ) // Pastikan ini sesuai dengan Cloudinary Anda
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
                        Log.e(
                            "CloudinaryUpload",
                            "Response tidak mengandung secure_url: $responseBody"
                        )
                        onResult(null)
                    }
                } catch (e: JSONException) {
                    Log.e("CloudinaryUpload", "Error parsing JSON: ${e.message}", e)
                    onResult(null)
                }
            }
        })
    }

    private fun saveToFirestore(
        briefingId: String,
        data: MutableMap<String, Any>,
        onResult: (Boolean) -> Unit
    ) {
        try {
            val documentRef = FirebaseFirestore.getInstance().collection("agenda").document(briefingId)

            Log.d("Firestore", "Menyimpan ke Firestore: $data")

            data["status"] = "aktif"
            data["timestamp"] = FieldValue.serverTimestamp()

            documentRef.set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("Firestore", "Data berhasil diperbarui: $data")
                    onResult(true)
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Gagal menyimpan data", e)
                    onResult(false)
                }
        } catch (e: Exception) {
            Log.e("Firestore", "Error saat menyimpan data: ${e.message}", e)
            onResult(false)
        }
    }
}

    @OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenu(label: String, value: String, onValueChange: (String) -> Unit, options: List<String>) {
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

data class UserEdit(
    val name: String,
    val terminal: String,
    val group: String
)