package com.example.spmtsafetybriefingapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class PendingApprovalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PendingApprovalScreen()
        }
    }
}

@Composable
fun PendingApprovalScreen() {
    val context = LocalContext.current
    var pendingUsers by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }

    LaunchedEffect(Unit) {
        getPendingUsers { users ->
            pendingUsers = users
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Daftar User Menunggu Persetujuan", style = MaterialTheme.typography.titleLarge)

        LazyColumn {
            items(pendingUsers) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Nama: ${user.getString("name")}")
                        Text(text = "Jabatan: ${user.getString("role")}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { approveUser(context, user.id) }) {
                            Text("Setujui")
                        }
                    }
                }
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

fun approveUser(context: android.content.Context, userId: String) {
    FirebaseFirestore.getInstance().collection("users")
        .document(userId)
        .update("isApproved", true) // Mengubah status menjadi disetujui
        .addOnSuccessListener {
            Toast.makeText(context, "User disetujui!", Toast.LENGTH_SHORT).show()
        }
}
