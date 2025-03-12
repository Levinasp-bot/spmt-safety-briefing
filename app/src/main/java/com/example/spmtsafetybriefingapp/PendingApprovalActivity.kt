package com.example.spmtsafetybriefingapp
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class PendingApprovalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PendingApprovalScreen(this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingApprovalScreen(context: Context) {
    val localContext = LocalContext.current
    var pendingUsers by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }

    LaunchedEffect(Unit) {
        getPendingUsers { users -> pendingUsers = users }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Daftar Pengguna Menunggu Persetujuan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            LazyColumn {
                items(pendingUsers) { user ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.getString("name") ?: "Unknown",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = user.getString("role") ?: "Unknown",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        approveUser(localContext, user.id)
                                        val intent = Intent(context, (context as Activity)::class.java)
                                        context.startActivity(intent)
                                        (context as Activity).finish()
                                              }, // ✅ Kirim context & userId
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF4CAF50), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Setujui",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        deleteUser(localContext, user.id)
                                        val intent = Intent(context, (context as Activity)::class.java)
                                        context.startActivity(intent)
                                        (context as Activity).finish()
                                              }, // ✅ Kirim context & userId
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFE53935), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tolak",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


fun deleteUser(context: Context, userId: String) {
    CoroutineScope(Dispatchers.IO).launch { // 🔄 Jalankan di background thread
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userId).delete()
                .addOnSuccessListener {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "User dihapus!", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun getPendingUsers(onResult: (List<DocumentSnapshot>) -> Unit) {
    FirebaseFirestore.getInstance().collection("users")
        .whereEqualTo("isApproved", false)
        .get()
        .addOnSuccessListener { result ->
            onResult(result.documents)
        }
}

fun approveUser(context: Context, userId: String) {
    CoroutineScope(Dispatchers.IO).launch { // 🔄 Jalankan di background thread
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(userId)
                .update("isApproved", true)
                .addOnSuccessListener {
                    Log.d("Firestore", "User berhasil disetujui")
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Gagal menyetujui user", e)
                }
        } catch (e: Exception) {
            Log.e("Firestore", "Error: ${e.message}")
        }
    }
}


fun getServiceAccountJson(context: Context): InputStream? {
    return context.resources.openRawResource(R.raw.service_account)
}

fun getAccessToken(context: Context): String? {
    val inputStream = getServiceAccountJson(context)
    val googleCredentials = GoogleCredentials.fromStream(inputStream)
        .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
    googleCredentials.refreshIfExpired()
    return googleCredentials.accessToken.tokenValue
}

fun sendFCMNotification(context: Context, token: String, title: String, message: String) {
    val accessToken = getAccessToken(context) ?: return

    val json = JSONObject().apply {
        put("message", JSONObject().apply {
            put("token", token)
            put("notification", JSONObject().apply {
                put("title", title)
                put("body", message)
            })
        })
    }

    val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
    val request = Request.Builder()
        .url("https://fcm.googleapis.com/v1/projects/YOUR_PROJECT_ID/messages:send")
        .addHeader("Authorization", "Bearer $accessToken")
        .addHeader("Content-Type", "application/json")
        .post(body)
        .build()

    val client = OkHttpClient()
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
        }

        override fun onResponse(call: Call, response: Response) {
            println("Response: ${response.body?.string()}")
        }
    })
}


