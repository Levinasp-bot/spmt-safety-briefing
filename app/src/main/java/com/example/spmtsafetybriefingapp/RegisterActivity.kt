package com.example.spmtsafetybriefingapp

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.google.firebase.messaging.FirebaseMessaging
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.tasks.await


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
            RegisterScreen { noEmployee, email, password, name, role, group, branch, terminal, imageUri, faceEmbedding ->
                if (noEmployee.isBlank() || password.isBlank() || name.isBlank() || role.isBlank() ||  terminal.isBlank()) {
                    Toast.makeText(this, "Semua kolom harus diisi!", Toast.LENGTH_SHORT).show()
                    return@RegisterScreen
                }
                registerUser(noEmployee, email, password, name, role, group, branch, terminal, imageUri, faceEmbedding)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RegisterScreen(onRegisterClick: (String, String, String, String, String, String, String?, String, Uri?, List<Float>?) -> Unit) {
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var noEmployee by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        val branchOptions = listOf("Jamrud Nilam Mirah")
        var branch by remember { mutableStateOf(branchOptions.first()) }

        val terminalOptions = listOf("Terminal Jamrud", "Terminal Nilam", "Terminal Mirah")
        var terminal by remember { mutableStateOf(terminalOptions.first()) }

        val roleOptions = listOf("Branch Manager", "Deputy Branch Manager Perencanaan dan Pengendalian Operasi", "Manager Operasi Jamrud", "Manager Operasi Nilam Mirah",
            "Koordinator Operasi Jamrud", "Koordinator Operasi Nilam", "Koordinator Operasi Mirah", "HSSE", "Koordinator Lapangan Pengamanan",
             "Anggota Pengamanan", "Operasional", "Komandan Peleton")
        var role by remember { mutableStateOf(roleOptions.first()) }

        val groupOptions = listOf("Group A", "Group B", "Group C", "Group D")
        var group by remember { mutableStateOf(groupOptions.first()) }
        val showGroupDropdown = role in listOf("Koordinator Operasi Jamrud", "Koordinator Operasi Nilam", "Koordinator Operasi Mirah",
            "Anggota Pengamanan", "Operasional", "Komandan Peleton")
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val uri = saveBitmapToMediaStore(context, croppedFace)
                            imageUri = uri
                        } else {
                            val uri = Uri.parse(MediaStore.Images.Media.insertImage(
                                context.contentResolver, croppedFace, "profile_photo", null
                            ))
                            imageUri = uri
                        }
                    } else {
                        Toast.makeText(context, "Wajah tidak terdeteksi!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
            .verticalScroll(rememberScrollState()),
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

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", color = primaryColor) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = primaryColor,
                    cursorColor = primaryColor
                )
            )

            DropdownMenuComponent("Branch", branchOptions, branch) { branch = it }
            DropdownMenuComponent("Terminal", terminalOptions, terminal) { terminal = it }
            DropdownMenuComponent("Jabatan", roleOptions, role) { role = it }

            if (showGroupDropdown) {
                DropdownMenuComponent("Group", groupOptions, group) { group = it }
            }

            var passwordVisible by remember { mutableStateOf(false) }
            var confirmPasswordVisible by remember { mutableStateOf(false) }
            val passwordsMatch = password == confirmPassword

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = primaryColor) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = primaryColor
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = primaryColor
                        )
                    }
                }
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Konfirmasi Password", color = primaryColor) },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = primaryColor
                ),
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                            tint = primaryColor
                        )
                    }
                },
                isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                supportingText = {
                    if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                        Text("Password tidak cocok", color = Color.Red, fontSize = 12.sp)
                    }
                }
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
                Text(text = "Ambil Foto Wajah", color = Color(0xFF0E73A7))
            }

            Spacer(modifier = Modifier.height(8.dp))

            imageUri?.let {
                Image(painter = rememberImagePainter(it), contentDescription = "User Image", modifier = Modifier.size(100.dp))
            }

            var usersExceptionList by remember { mutableStateOf<List<String>>(emptyList()) }
            var nippUser by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                try {
                    val exceptionDocs = firestore.collection("usersException").get().await()
                    usersExceptionList = exceptionDocs.documents.mapNotNull { it.getString("nipp") }
                } catch (e: Exception) {
                    Log.e("Firestore", "Error fetching usersException: ${e.message}")
                }
            }

            Button(
                onClick = {
                    val isUserException = nippUser in usersExceptionList // Cek apakah NIPP ada di daftar pengecualian

                    // Validasi input kosong
                    if (noEmployee.isBlank() ||
                        email.isBlank() ||
                        password.isBlank() ||
                        confirmPassword.isBlank() ||
                        name.isBlank() ||
                        role.isBlank() ||
                        branch.isBlank() ||
                        terminal.isBlank() ||
                        (showGroupDropdown && group.isBlank())
                    ) {
                        Toast.makeText(context, "Semua field harus diisi", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Validasi password harus sama
                    if (password != confirmPassword) {
                        Toast.makeText(context, "Password dan konfirmasi password tidak cocok", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    if (imageUri == null && !isUserException) {
                        Toast.makeText(context, "Foto wajah belum diambil", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true
                    val selectedGroup = if (showGroupDropdown) group else null
                    onRegisterClick(
                        noEmployee, email, password, name, role, branch, selectedGroup, terminal, imageUri, faceEmbedding
                    )
                },
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
                    Text(text = "Daftar", color = Color.White)
                }
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
        noEmployee: String,
        email: String,
        password: String,
        name: String,
        role: String,
        group: String?,
        branch: String?,
        terminal: String,
        imageUri: Uri?,
        faceEmbedding: List<Float>?
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val userId = authResult.user?.uid ?: return@addOnSuccessListener
                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            Log.e("FCM", "Fetching FCM token failed", task.exception)
                            Toast.makeText(this, "Gagal mendapatkan FCM Token", Toast.LENGTH_SHORT).show()
                            return@addOnCompleteListener
                        }
                        val fcmToken = task.result
                        Log.d("FCM", "FCM Token: $fcmToken")

                        val isManagerOperasi =
                            role == "Manager Operasi Jamrud" || role == "Manager Operasi Nilam Mirah"

                        val userMap = hashMapOf(
                            "userId" to userId,
                            "name" to name,
                            "email" to email,
                            "role" to role,
                            "group" to group,
                            "noEmployee" to noEmployee,

                            "terminal" to terminal,
                            "faceEmbedding" to faceEmbedding,
                            "isApproved" to isManagerOperasi,
                            "fcmToken" to fcmToken
                        )

                        FirebaseFirestore.getInstance().collection("users")
                            .document(userId)
                            .set(userMap)
                            .addOnSuccessListener {
                                val toastMessage = if (isManagerOperasi) {
                                    "Registrasi Berhasil"
                                } else {
                                    "Menunggu persetujuan Manager Terminal."
                                }

                                Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    this,
                                    "Gagal menyimpan data: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Registrasi gagal: ${e.message}", Toast.LENGTH_SHORT).show()
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

