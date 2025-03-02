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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class RegisterActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    @Composable
    fun RegisterScreen(onRegisterClick: (String, String, String, String, String?, String, String, Uri?) -> Unit) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var noEmployee by remember { mutableStateOf("") }

        val terminalOptions = listOf("Terminal Jamrud", "Terminal Nilam", "Terminal Mirah")
        var terminal by remember { mutableStateOf(terminalOptions.first()) }

        val roleOptions = listOf("HSSE", "Koordinator", "Anggota Security", "Anggota Operasional")
        var role by remember { mutableStateOf(roleOptions.first()) }

        val groupOptions = listOf("Group 1", "Group 2", "Group 3")
        var group by remember { mutableStateOf(groupOptions.first()) }

        var imageUri by remember { mutableStateOf<Uri?>(null) }
        var isLoading by remember { mutableStateOf(false) }

        val showGroupDropdown = role in listOf("Koordinator", "Anggota Security", "Anggota Operasional")

        val context = LocalContext.current
        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                val uri = Uri.parse(MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "profile_photo", null))
                imageUri = uri
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Daftar", fontSize = 24.sp, color = Color(0xFF0E73A7))
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = noEmployee, onValueChange = { noEmployee = it }, label = { Text("No Employee") }, modifier = Modifier.fillMaxWidth())

            DropdownMenuComponent("Terminal", terminalOptions, terminal) { terminal = it }
            DropdownMenuComponent("Role", roleOptions, role) { role = it }

            // Hanya tampilkan dropdown Group jika role yang dipilih sesuai
            if (showGroupDropdown) {
                DropdownMenuComponent("Group", groupOptions, group) { group = it }
            }

            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { cameraLauncher.launch(null) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7))) {
                Text(text = "Ambil Foto", color = Color.White)
            }

            imageUri?.let {
                Image(painter = rememberImagePainter(it), contentDescription = "User Image", modifier = Modifier.size(100.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (isLoading) {
                CircularProgressIndicator()
            }
            Button(
                onClick = {
                    isLoading = true
                    val selectedGroup = if (showGroupDropdown) group else null
                    onRegisterClick(email, password, name, role, selectedGroup, noEmployee, terminal, imageUri)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7))
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setContent {
            RegisterScreen { email, password, name, role, group, noEmployee, terminal, imageUri ->
                if (email.isBlank() || password.isBlank() || name.isBlank() || role.isBlank() || noEmployee.isBlank() || terminal.isBlank()) {
                    Toast.makeText(this, "Semua kolom harus diisi!", Toast.LENGTH_SHORT).show()
                    return@RegisterScreen
                }
                registerUser(email, password, name, role, group, noEmployee, terminal, imageUri)
            }
        }
    }

    private fun saveImageLocally(uri: Uri): String {
        val file = File(filesDir, "profile_${System.currentTimeMillis()}.jpg")
        return try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            file.absolutePath
        } catch (e: IOException) {
            Log.e("SaveImage", "Gagal menyimpan gambar", e)
            ""
        }
    }

    private fun registerUser(email: String, password: String, name: String, role: String, group: String?, noEmployee: String, terminal: String, imageUri: Uri?) {
        auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener { authResult ->
            val userId = authResult.user?.uid ?: return@addOnSuccessListener
            val userMap = hashMapOf(
                "userId" to userId,
                "name" to name,
                "email" to email,
                "role" to role,
                "noEmployee" to noEmployee,
                "terminal" to terminal
            )

            if (group != null) {
                userMap["group"] = group
            }

            if (imageUri != null) {
                val imagePath = saveImageLocally(imageUri)
                userMap["imagePath"] = imagePath
            }

            firestore.collection("users").document(userId).set(userMap).addOnSuccessListener {
                Toast.makeText(this, "Registrasi berhasil!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }.addOnFailureListener {
            Log.e("RegisterUser", "Gagal mendaftar", it)
            runOnUiThread { Toast.makeText(this, "Registrasi gagal!", Toast.LENGTH_SHORT).show() }
        }
    }
}
