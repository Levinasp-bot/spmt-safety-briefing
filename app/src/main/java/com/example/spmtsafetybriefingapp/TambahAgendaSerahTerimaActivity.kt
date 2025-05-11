package com.example.spmtsafetybriefingapp
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore

class TambahAgendaSerahTerimaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val agendaId = intent.getStringExtra("briefingId")

        setContent {
            MaterialTheme {
                if (agendaId != null) {
                    SerahTerimaForm(agendaId = agendaId) {
                        finish()
                    }
                } else {
                    Text("Gagal memuat agenda ID")
                }
            }
        }
    }
    @Composable
    fun SerahTerimaForm(agendaId: String, onSuccess: () -> Unit) {
        val firestore = FirebaseFirestore.getInstance()
        var inputList by remember { mutableStateOf(listOf("")) }
        val customColor = Color(0xFF0E73A7)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Tambah Agenda Serah Terima",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                itemsIndexed(inputList) { index, text ->
                    OutlinedTextField(
                        value = text,
                        onValueChange = { newText ->
                            inputList = inputList.toMutableList().also { it[index] = newText }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        label = { Text("Item ${index + 1}") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = customColor,
                            unfocusedBorderColor = customColor,
                            focusedLabelColor = customColor,
                            unfocusedLabelColor = customColor,
                            cursorColor = customColor
                        )
                    )
                }
            }
            TextButton(
                onClick = {
                    inputList = inputList + ""
                },
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text("+ Tambah Item", color = customColor)
            }
            Button(
                onClick = {
                    val cleanedList = inputList.filter { it.isNotBlank() }
                    if (cleanedList.isEmpty()) {
                        Toast.makeText(
                            this@TambahAgendaSerahTerimaActivity,
                            "Harap isi minimal 1 item",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    firestore.collection("agenda").document(agendaId)
                        .update("serahTerima", cleanedList)
                        .addOnSuccessListener {
                            Toast.makeText(
                                this@TambahAgendaSerahTerimaActivity,
                                "Data serah terima berhasil disimpan!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onSuccess()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                this@TambahAgendaSerahTerimaActivity,
                                "Gagal menyimpan data",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColor,
                    contentColor = Color.White
                )
            ) {
                Text("Simpan")
            }
        }
    }
}