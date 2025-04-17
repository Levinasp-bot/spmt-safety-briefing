package com.example.spmtsafetybriefingapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.google.firebase.firestore.Source

class LoginActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        setContent {
            LoginScreen(
                onLoginClick = { email, password, onResult ->
                    loginUser(email, password, onResult)
                },
                onRegisterClick = { navigateToRegister() }
            )
        }
    }

    private fun loginUser(nipp: String, password: String, onResult: (Boolean) -> Unit) {
        if (nipp.isBlank() || password.isBlank()) {
            Toast.makeText(this, "NIPP dan password tidak boleh kosong", Toast.LENGTH_SHORT).show()
            onResult(false)
            return
        }

        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()

        db.collection("users").whereEqualTo("noEmployee", nipp.trim()).limit(1).get(Source.SERVER)
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val userDoc = documents.documents[0]
                    val email = userDoc.getString("email") ?: ""

                    if (email.isNotBlank()) {
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener { authResult ->
                                val userId = authResult.user?.uid ?: return@addOnSuccessListener

                                db.collection("users").document(userId).get()
                                    .addOnSuccessListener { document ->
                                        if (document.exists()) {
                                            val isApproved = document.getBoolean("isApproved") ?: false

                                            if (isApproved) {
                                                sharedPreferences.edit().putBoolean("is_logged_in", true).apply()
                                                val intent = Intent(this, HomeActivity::class.java)
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                startActivity(intent)
                                                onResult(true)
                                            } else {
                                                Toast.makeText(this, "Akun Anda belum disetujui oleh Manager Terminal", Toast.LENGTH_LONG).show()
                                                auth.signOut()
                                                onResult(false)
                                            }
                                        } else {
                                            Toast.makeText(this, "Data pengguna tidak ditemukan", Toast.LENGTH_LONG).show()
                                            auth.signOut()
                                            onResult(false)
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Gagal memeriksa status akun: ${e.message}", Toast.LENGTH_LONG).show()
                                        auth.signOut()
                                        onResult(false)
                                    }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Login gagal: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                onResult(false)
                            }
                    } else {
                        Toast.makeText(this, "Email tidak ditemukan untuk NIPP ini", Toast.LENGTH_LONG).show()
                        onResult(false)
                    }
                } else {
                    Toast.makeText(this, "NIPP tidak terdaftar", Toast.LENGTH_LONG).show()
                    onResult(false)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal mengambil data pengguna: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                onResult(false)
            }
    }

    private fun navigateToRegister() {
        startActivity(Intent(this, RegisterActivity::class.java))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginClick: (String, String, (Boolean) -> Unit) -> Unit,
    onRegisterClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val primaryColor = Color(0xFF0E73A7)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Login",
            fontSize = 24.sp,
            color = primaryColor,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("NIPP", color = primaryColor) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = primaryColor
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        var passwordVisible by remember { mutableStateOf(false) }

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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isLoading = true
                onLoginClick(email, password) { success ->
                    isLoading = false
                    if (!success) {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(text = "Login", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onRegisterClick) {
            Text("Belum punya akun? Daftar", color = primaryColor)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LoginScreen(onLoginClick = { _, _, _ -> }, onRegisterClick = {})
}

