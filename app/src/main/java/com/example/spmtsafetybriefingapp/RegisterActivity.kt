package com.example.spmtsafetybriefingapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RegisterActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var interpreter: Interpreter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
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
            RegisterScreen { noEmployee, password, name, role, group, terminal, imageUri, faceEmbedding ->
                if (noEmployee.isBlank() || password.isBlank() || name.isBlank() || role.isBlank() ||  terminal.isBlank()) {
                    Toast.makeText(this, "Semua kolom harus diisi!", Toast.LENGTH_SHORT).show()
                    return@RegisterScreen
                }
                registerUser(noEmployee, password, name, role, group, terminal, imageUri, faceEmbedding)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RegisterScreen(onRegisterClick: (String, String, String, String, String?, String, Uri?, List<Float>?) -> Unit) {
        var password by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var noEmployee by remember { mutableStateOf("") }

        val terminalOptions = listOf("Terminal Jamrud", "Terminal Nilam", "Terminal Mirah")
        var terminal by remember { mutableStateOf(terminalOptions.first()) }

        val roleOptions = listOf("Brach Manager", "Deputy Branch Manager Perencanaan dan Pengendalian Operasi", "Manager Operasi Jamrud", "Manager Operasi Nilam Mirah", "HSSE", "Koordinator Lapangan Pengamanan", "Komandan Peleton", "Anggota Pengamanan", "Chief Foreman", "Foreman", "Dispatcher")
        var role by remember { mutableStateOf(roleOptions.first()) }

        val groupOptions = listOf("Group A", "Group B", "Group C", "Group D")
        var group by remember { mutableStateOf(groupOptions.first()) }
        val showGroupDropdown = role in listOf("Komandan Peleton", "Anggota Pengamanan", "Chief Foreman", "Foreman", "Dispatcher")
        var imageUri by remember { mutableStateOf<Uri?>(null) }
        var faceEmbedding by remember { mutableStateOf<List<Float>?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        val primaryColor = Color(0xFF0E73A7)

        val context = LocalContext.current
        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                detectFace(bitmap) { croppedFace ->
                    if (croppedFace != null) {
                        faceEmbedding = getFaceEmbedding(croppedFace)
                        val uri = Uri.parse(MediaStore.Images.Media.insertImage(context.contentResolver, croppedFace, "profile_photo", null))
                        imageUri = uri
                    } else {
                        Toast.makeText(context, "Wajah tidak terdeteksi!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Daftar", fontSize = 24.sp, color = primaryColor)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama", color = primaryColor) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = primaryColor,
                    cursorColor = primaryColor
                )
            )

            OutlinedTextField(
                value = noEmployee,
                onValueChange = { noEmployee = it },
                label = { Text("NIPP", color = primaryColor) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = primaryColor,
                    cursorColor = primaryColor
                )
            )

            DropdownMenuComponent("Terminal", terminalOptions, terminal) { terminal = it }
            DropdownMenuComponent("Jabatan", roleOptions, role) { role = it }

            if (showGroupDropdown) {
                DropdownMenuComponent("Group", groupOptions, group) { group = it }
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = primaryColor) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = primaryColor,
                    cursorColor = primaryColor
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0E73A7)
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(2.dp, Color(0xFF0E73A7))
            ) {
                Text(text = "Ambil Foto", color = Color(0xFF0E73A7))
            }

            Spacer(modifier = Modifier.height(8.dp))

            imageUri?.let {
                Image(painter = rememberImagePainter(it), contentDescription = "User Image", modifier = Modifier.size(100.dp))
            }

            Button(
                onClick = {
                    isLoading = true
                    val selectedGroup = if (showGroupDropdown) group else null
                    onRegisterClick(noEmployee, password, name, role, group, terminal, imageUri, faceEmbedding)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
                shape = RoundedCornerShape(8.dp) // 🔹 Tambahkan rounded corner 8dp
            ) {
                Text(text = if (isLoading) "Mendaftar..." else "Daftar", color = Color.White)
            }
        }
    }

    @Composable
    fun DropdownMenuComponent(label: String, options: List<String>, selectedValue: String, onValueChange: (String) -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                    }
                }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = {
                        onValueChange(option)
                        expanded = false
                    })
                }
            }
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

    private fun registerUser(
        noEmployee: String, // 🔹 Gunakan NIPP sebagai username
        password: String,
        name: String,
        role: String,
        group: String?,
        terminal: String,
        imageUri: Uri?,
        faceEmbedding: List<Float>?
    ) {
        val fakeEmail = "$noEmployee@gmail.com" // 🔹 Konversi NIPP ke format email

        auth.createUserWithEmailAndPassword(fakeEmail, password)
            .addOnSuccessListener { authResult ->
                val userId = authResult.user?.uid ?: return@addOnSuccessListener

                val userMap = hashMapOf(
                    "userId" to userId,
                    "name" to name,
                    "email" to fakeEmail, // 🔹 Simpan email palsu jika dibutuhkan
                    "role" to role,
                    "group" to group,
                    "noEmployee" to noEmployee, // 🔹 Simpan NIPP asli
                    "terminal" to terminal,
                    "faceEmbedding" to faceEmbedding
                )

                FirebaseFirestore.getInstance().collection("users")
                    .document(userId)
                    .set(userMap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Registrasi berhasil!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal menyimpan data: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Registrasi gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

