package com.example.spmtsafetybriefingapp

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Rect
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.firestore.ListenerRegistration

class UnduhPdfActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val briefingId = intent.getStringExtra("briefingId").orEmpty() // ✅ Lebih aman jika null

        setContent {
            UnduhPdfScreen(briefingId) // ✅ Kirim briefingId ke composable
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun generatePdfFromCompose(activity: ComponentActivity, agenda: Agenda_detail?) {
    if (agenda == null) {
        Toast.makeText(activity, "Data agenda tidak tersedia", Toast.LENGTH_SHORT).show()
        return
    }

    val dialog = ComponentDialog(activity)
    val pdfComposeView = ComposeView(activity).apply {
        setContent {
            PdfLayoutScreen(agenda) // Render layout tanpa tombol
        }
    }

    dialog.setContentView(pdfComposeView)
    dialog.window?.setLayout(1080, 1920)
    dialog.show()

    Handler(Looper.getMainLooper()).postDelayed({
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)

        val location = IntArray(2)
        pdfComposeView.getLocationInWindow(location)

        val window = activity.window
        val handler = Handler(Looper.getMainLooper())

        PixelCopy.request(
            window,
            Rect(location[0], location[1], location[0] + pdfComposeView.width, location[1] + pdfComposeView.height),
            bitmap,
            { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    saveBitmapAsPdf(activity, bitmap, agenda)
                } else {
                    Toast.makeText(activity, "Gagal mengambil tangkapan layar", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            },
            handler
        )
    }, 500) // Tunggu 500ms agar rendering selesai
}

fun saveBitmapAsPdf(activity: ComponentActivity, bitmap: Bitmap, agenda: Agenda_detail) {
    val pageHeight = 1800  // Tinggi konten per halaman (sebelum margin)
    val marginTop = 100     // Margin atas untuk halaman kedua dan seterusnya
    val marginBottom = 100  // Margin bawah untuk semua halaman
    val totalHeight = bitmap.height
    val pdfDocument = PdfDocument()

    var yOffset = 0
    var pageNumber = 1

    while (yOffset < totalHeight) {
        val isFirstPage = (pageNumber == 1)
        val heightToCopy = minOf(pageHeight - if (isFirstPage) marginBottom else marginTop, totalHeight - yOffset)

        if (heightToCopy <= 0) break

        val pageBitmap = Bitmap.createBitmap(bitmap, 0, yOffset, bitmap.width, heightToCopy)

        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, pageHeight, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val pdfCanvas = page.canvas

        val yOffsetCanvas = if (isFirstPage) 0f else marginTop.toFloat()
        pdfCanvas.drawBitmap(pageBitmap, 0f, yOffsetCanvas, null)
        pdfDocument.finishPage(page)

        yOffset += heightToCopy
        pageNumber++
    }

    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "SafetyBriefing_${agenda.terminal}_$timeStamp.pdf"

    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val file = File(downloadsDir, fileName)

    pdfDocument.writeTo(FileOutputStream(file))
    pdfDocument.close()

    Toast.makeText(activity, "PDF berhasil disimpan di ${file.absolutePath}", Toast.LENGTH_LONG).show()
}

@Composable
fun PdfLayoutScreen(agenda: Agenda_detail?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, top = 10.dp, end = 28.dp) // Atur padding atas, kanan, kiri
            .background(Color.White)
    ) {
        // 🔹 Header dengan 3 Kolom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min), // ✅ Menyesuaikan tinggi Row dengan kontennya
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🔹 Kolom 1: Logo
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp)
                    .fillMaxHeight()// ✅ Padding lebih kecil agar tidak ada space kosong besar
                    .align(Alignment.CenterVertically), // ✅ Agar sejajar dengan teks lainnya
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_spmt),
                    contentDescription = "Logo SPMT",
                    modifier = Modifier
                        .width(90.dp) // Atur lebar
                        .height(30.dp) // Atur tinggi
                )

            }

            // 🔹 Kolom 2: Judul
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp) // ✅ Padding lebih kecil
                    .align(Alignment.CenterVertically), // ✅ Agar sejajar dengan lainnya
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "PENGENDALIAN PELAKSANAAN SAFETY BRIEFING",
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp) // ✅ Padding lebih kecil
                    .align(Alignment.CenterVertically), // ✅ Agar sejajar dengan lainnya
                verticalArrangement = Arrangement.spacedBy(4.dp) // ✅ Jarak antar teks lebih proporsional
            ) {
                Text("No Dokumen:  ", fontSize = 6.sp, fontWeight = FontWeight.Normal)
                Divider(color = Color.Black, thickness = 1.dp)

                Text("No Revisi: 0", fontSize = 6.sp, fontWeight = FontWeight.Normal)
                Divider(color = Color.Black, thickness = 1.dp)

                Text(
                    "Tanggal: ${
                        agenda?.timestamp?.toDate()?.let {
                            SimpleDateFormat(
                                "dd/MM/yyyy",
                                Locale.getDefault()
                            ).format(it)
                        } ?: "-"
                    }",
                    fontSize = 6.sp, fontWeight = FontWeight.Normal
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min) // Pastikan tinggi semua kolom sejajar
        ) {
            // 🔹 Kolom 1: Hari / Tanggal
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Hari / Tanggal: ${
                        agenda?.timestamp?.toDate()?.let {
                            SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id")).format(it)
                        } ?: "-"
                    }",
                    fontSize = 7.sp
                )
            }

            // 🔹 Kolom 2: Jam
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Jam: ${
                        agenda?.shift?.split(" ")?.drop(2)?.joinToString(" ") ?: "-"
                    }",
                    fontSize = 7.sp
                )
            }

            // 🔹 Kolom 3: Shift
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "${
                        agenda?.shift?.split(" ")?.take(2)?.joinToString(" ") ?: "-"
                    } (${
                        agenda?.group ?: "-"
                    })",
                    fontSize = 7.sp
                )
            }

            // 🔹 Kolom 4: Tempat (Terminal)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Tempat: ${agenda?.tempat ?: "-"}",
                    fontSize = 7.sp
                )
            }
        }
        // 🔹 Row untuk Perwira Briefing & Koordinator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min), // 🔹 Menyamakan tinggi antar kolom
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🔹 Kolom 1: Perwira Briefing
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Perwira Briefing", fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }

            // 🔹 Kolom 2: Nama Perwira
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val text = when (agenda?.terminal) {
                    "Terminal Jamrud" -> "Anton Yudhiana"
                    "Terminal Mirah", "Terminal Nilam" -> "Dimas Wibowo"
                    else -> "Nama Tidak Diketahui" // Jika terminal tidak cocok
                }

                Text(": $text", fontSize = 8.sp)
            }

            // 🔹 Kolom 3: Koordinator
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Koordinator", fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }

            // 🔹 Kolom 4: Nama Koordinator dari agenda
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(": ${agenda?.koordinator ?: "-"}", fontSize = 8.sp)
            }
        }

// 🔹 Row untuk Keterangan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min) // 🔹 Menyamakan tinggi secara otomatis
                .border(1.dp, Color.Black)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Keterangan:",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(), // 🔹 Memastikan teks melebar ke seluruh kolom
                textAlign = TextAlign.Start // 🔹 Rata kiri
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min) // 🔹 Menyamakan tinggi secara otomatis
                .border(1.dp, Color.Black)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🔹 Kolom 1: Nomor
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "1",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // 🔹 Kolom 2: Informasi Safety Briefing
            Box(
                modifier = Modifier
                    .weight(15f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Safety Briefing wajib dilakukan setiap awal shift",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start
                )
            }
        }

        // 🔹 Row untuk Informasi Safety Briefing
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min) // 🔹 Menyamakan tinggi secara otomatis
                .border(1.dp, Color.Black)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "2",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Box(
                modifier = Modifier
                    .weight(15f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Formulir Safety Briefing ini akan dimonitor oleh Dinas yang membidangi HSSE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start
                )
            }
        }

        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: MATERI YANG DISAMPAIKAN
            Column(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp)
            ) {
                Text(
                    "MATERI YANG DISAMPAIKAN :",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp)) // 🔹 Spasi kecil sebelum daftar

                Text("1. Health", fontSize = 7.sp)
                Text("2. Safety", fontSize = 7.sp)
                Text("3. Security", fontSize = 7.sp)
                Text("4. Environment", fontSize = 7.sp)
                Text("5. Operation", fontSize = 7.sp)
            }

            // 🔹 Kolom 2: Dokumentasi (dibagi menjadi 2 row)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp)
            ) {
                // 🔹 Row 1: Judul DOKUMENTASI
                Text(
                    "DOKUMENTASI:",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )

                // 🔹 Row 2: Menampilkan Gambar
                Box(
                    modifier = Modifier
                        .weight(2f) // ✅ Sesuai dengan perbandingan 1:5
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .border(1.dp, Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    val imageUrl = agenda?.photoPath // 🔹 Ambil photoPath dari Firestore
                    val painter = if (imageUrl != null) {
                        rememberAsyncImagePainter(model = imageUrl) // 🔹 Load image dari URL Firestore
                    } else {
                        painterResource(id = R.drawable.ic_launcher_background) // 🔹 Gambar dummy jika gagal
                    }

                    Image(
                        painter = painter,
                        contentDescription = "Dokumentasi",
                        modifier = Modifier
                            .width(100.dp)
                            .height(40.dp) // 🔹 Sesuaikan tinggi gambar
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                // 🔹 Judul AGENDA BRIEFING
                Text(
                    "AGENDA BRIEFING:",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp)) // 🔹 Spasi kecil sebelum isi agenda

                // 🔹 Menampilkan daftar agenda jika tersedia
                agenda?.agenda?.forEachIndexed { index, item ->
                    Text("${index + 1}. $item", fontSize = 8.sp)
                } ?: Text("- Tidak ada agenda -", fontSize = 7.sp)
            }
        }

        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: Rekap Peserta Safety Briefing
            Box(
                modifier = Modifier
                    .weight(2f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Rekap Peserta Safety Briefing",
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center
                )
            }

            // 🔹 Kolom 2: Hadir
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Hadir", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 3: Sakit
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Sakit", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 4: Cuti
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Cuti", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 5: Izin
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Izin", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 6: Tanpa Keterangan
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Tanpa Keterangan", fontSize = 8.sp, textAlign = TextAlign.Center)
            }
        }

        // 🔹 State untuk menyimpan jumlah pekerja
        var jumlahPekerja by remember { mutableStateOf(0) }
        var jumlahHadir by remember { mutableStateOf(0) }
        var jumlahSakit by remember { mutableStateOf(0) }
        var jumlahCuti by remember { mutableStateOf(0) }
        var jumlahIzin by remember { mutableStateOf(0) }
        var tanpaKeterangan by remember { mutableStateOf(0) }

        var briefingData by remember { mutableStateOf<Map<String, Any>?>(null) }

        LaunchedEffect(agenda) { // 🔹 Jalankan efek hanya jika agenda berubah
            val briefingId = agenda?.briefingId
            if (briefingId == null) {
                Log.e("FirestoreDebug", "briefingId is null")
                return@LaunchedEffect
            }

            Firebase.firestore.collection("agenda")
                .document(briefingId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        Log.d("FirestoreDebug", "Data agenda ditemukan: ${document.data}")
                        briefingData = document.data
                        jumlahIzin = (document["izin"] as? List<*>)?.size ?: 0
                        jumlahSakit = (document["sakit"] as? List<*>)?.size ?: 0
                        jumlahCuti = (document["cuti"] as? List<*>)?.size ?: 0

                        val selectedTerminal = document.getString("terminal") ?: ""
                        val selectedGroup = document.getString("group") ?: ""

                        // 🔹 Ambil total pekerja berdasarkan terminal & group
                        Firebase.firestore.collection("users")
                            .whereEqualTo("terminal", selectedTerminal)
                            .whereEqualTo("group", selectedGroup)
                            .get()
                            .addOnSuccessListener { userSnapshot ->
                                val totalPekerja = userSnapshot.size()
                                Log.d("FirestoreDebug", "Total pekerja di terminal $selectedTerminal dan group $selectedGroup: $totalPekerja")

                                // 🔹 Ambil jumlah pekerja yang hadir
                                Firebase.firestore.collection("agenda")
                                    .document(briefingId)
                                    .collection("attendance")
                                    .get()
                                    .addOnSuccessListener { attendanceSnapshot ->
                                        jumlahPekerja = attendanceSnapshot.size()
                                        Log.d("FirestoreDebug", "Jumlah pekerja hadir: $jumlahPekerja")

                                        // 🔹 Hitung jumlah pekerja tanpa keterangan
                                        tanpaKeterangan = totalPekerja - (jumlahPekerja + jumlahIzin + jumlahSakit + jumlahCuti)
                                        Log.d("FirestoreDebug", "Jumlah pekerja tanpa keterangan: $tanpaKeterangan")
                                    }
                                    .addOnFailureListener { error ->
                                        Log.e("FirestoreDebug", "Gagal mengambil data attendance", error)
                                    }
                            }
                            .addOnFailureListener { error ->
                                Log.e("FirestoreDebug", "Gagal mengambil data users", error)
                            }
                    } else {
                        Log.e("FirestoreDebug", "Dokumen agenda tidak ditemukan")
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("FirestoreDebug", "Gagal mengambil data agenda", error)
                }
        }


// 🔹 Row dengan 6 kolom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: Jumlah Pekerja (orang)
            Box(
                modifier = Modifier
                    .weight(2f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Jumlah Pekerja (orang)",
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center
                )
            }

            // 🔹 Kolom 2: Ambil jumlah pekerja dari sub-koleksi attendance
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahPekerja", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 3: Izin
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahIzin", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 4: Sakit
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahSakit", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 5: Cuti
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahCuti", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 6: Tanpa Keterangan
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$tanpaKeterangan", fontSize = 8.sp, textAlign = TextAlign.Center)
            }
        }

        var namaSakit by remember { mutableStateOf<List<String>>(emptyList()) }
        var namaCuti by remember { mutableStateOf<List<String>>(emptyList()) }
        var namaIzin by remember { mutableStateOf<List<String>>(emptyList()) }
        var namaTanpaKeterangan by remember { mutableStateOf<List<String>>(emptyList()) }
        var namaTidakLengkapAtribut by remember { mutableStateOf<List<String>>(emptyList()) }

        LaunchedEffect(agenda) {
            val briefingId = agenda?.briefingId
            if (briefingId == null) {
                Log.e("FirestoreDebug", "briefingId is null")
                return@LaunchedEffect
            }

            Firebase.firestore.collection("agenda")
                .document(briefingId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        Log.d("FirestoreDebug", "Data agenda ditemukan: ${document.data}")

                        // 🔹 Ambil nama pekerja berdasarkan kategori
                        namaSakit = (document["sakit"] as? List<String>) ?: emptyList()
                        namaCuti = (document["cuti"] as? List<String>) ?: emptyList()
                        namaIzin = (document["izin"] as? List<String>) ?: emptyList()
                        namaTanpaKeterangan = (document["tanpaKeterangan"] as? List<String>) ?: emptyList()
                        namaTidakLengkapAtribut = (document["izin"] as? List<String>) ?: emptyList()
                    } else {
                        Log.e("FirestoreDebug", "Dokumen agenda tidak ditemukan")
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("FirestoreDebug", "Gagal mengambil data agenda", error)
                }
        }


        // 🔹 Nama Pekerja Sakit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Sakit",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }

            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Column {
                    namaSakit.forEach { nama ->
                        Text(nama, fontSize = 7.sp, textAlign = TextAlign.Left)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: Nama Pekerja Sakit
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Cuti",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }
            // 🔹 Kolom 2: Kosong
            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Column {
                    namaCuti.forEach { nama ->
                        Text(nama, fontSize = 7.sp, textAlign = TextAlign.Left)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: Nama Pekerja Sakit
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Izin",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }
            // 🔹 Kolom 2: Kosong
            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Column {
                    namaIzin.forEach { nama ->
                        Text(nama, fontSize = 7.sp, textAlign = TextAlign.Left)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: Nama Pekerja Sakit
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Tanpa Keterangan",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }
            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Column {
                    namaTanpaKeterangan.forEach { nama ->
                        Text(nama, fontSize = 7.sp, textAlign = TextAlign.Left)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: Nama Pekerja Sakit
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Tidak Lengkap Atribut",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }

            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Column {
                    namaTidakLengkapAtribut.forEach { nama ->
                        Text(nama, fontSize = 7.sp, textAlign = TextAlign.Left)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("PERWIRA BRIEFING", fontSize = 7.sp, fontWeight = FontWeight.Normal)
                Spacer(modifier = Modifier.height(40.dp)) // Ruang untuk tanda tangan
                Text(
                    text = when (agenda?.terminal) {
                        "Terminal Jamrud" -> "(Anton Yudhiana)"
                        "Terminal Mirah", "Terminal Nilam" -> "(Dimas Wibowo)"
                        else -> "(Nama Tidak Diketahui)"
                    },
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("KOORDINATOR BRIEFING", fontSize = 7.sp, fontWeight = FontWeight.Normal)
                Spacer(modifier = Modifier.height(40.dp)) // Ruang untuk tanda tangan
                Text(
                    text = "(${agenda?.koordinator ?: "-"})",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun UnduhPdfScreen(briefingId: String) {
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var agenda by remember { mutableStateOf<Agenda_detail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(briefingId) {
        firestore.collection("agenda").document(briefingId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    agenda = document.toObject(Agenda_detail::class.java)
                } else {
                    errorMessage = "Data briefing tidak ditemukan"
                }
                isLoading = false
            }
            .addOnFailureListener {
                errorMessage = "Gagal memuat data"
                isLoading = false
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, top = 10.dp, end = 28.dp) // Atur padding atas, kanan, kiri
            .background(Color.White)
    ) {
        // 🔹 Header dengan 3 Kolom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min), // ✅ Menyesuaikan tinggi Row dengan kontennya
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🔹 Kolom 1: Logo
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp)
                    .fillMaxHeight()// ✅ Padding lebih kecil agar tidak ada space kosong besar
                    .align(Alignment.CenterVertically), // ✅ Agar sejajar dengan teks lainnya
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_spmt),
                    contentDescription = "Logo SPMT",
                    modifier = Modifier
                        .width(90.dp) // Atur lebar
                        .height(30.dp) // Atur tinggi
                )

            }

            // 🔹 Kolom 2: Judul
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp) // ✅ Padding lebih kecil
                    .align(Alignment.CenterVertically), // ✅ Agar sejajar dengan lainnya
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "PENGENDALIAN PELAKSANAAN SAFETY BRIEFING",
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp) // ✅ Padding lebih kecil
                    .align(Alignment.CenterVertically), // ✅ Agar sejajar dengan lainnya
                verticalArrangement = Arrangement.spacedBy(4.dp) // ✅ Jarak antar teks lebih proporsional
            ) {
                Text("No Dokumen: ", fontSize = 6.sp, fontWeight = FontWeight.Normal)
                Divider(color = Color.Black, thickness = 1.dp)

                Text("No Revisi: 0", fontSize = 6.sp, fontWeight = FontWeight.Normal)
                Divider(color = Color.Black, thickness = 1.dp)

                Text(
                    "Tanggal: ${agenda?.timestamp?.toDate()?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: "-"}",
                    fontSize = 6.sp, fontWeight = FontWeight.Normal
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min) // Pastikan tinggi semua kolom sejajar
        ) {
            // 🔹 Kolom 1: Hari / Tanggal
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Hari / Tanggal: ${
                        agenda?.timestamp?.toDate()?.let {
                            SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id")).format(it)
                        } ?: "-"
                    }",
                    fontSize = 7.sp
                )
            }

            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Jam: ${
                        agenda?.shift?.split(" ")?.drop(2)?.joinToString(" ") ?: "-"
                    }",
                    fontSize = 7.sp
                )
            }

            // 🔹 Kolom 3: Shift
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "${
                        agenda?.shift?.split(" ")?.take(2)?.joinToString(" ") ?: "-"
                    } (${
                        agenda?.group ?: "-"
                    })",
                    fontSize = 7.sp
                )
            }

            // 🔹 Kolom 4: Tempat (Terminal)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Tempat: ${agenda?.tempat ?: "-"}",
                    fontSize = 7.sp
                )
            }
        }
        // 🔹 Row untuk Perwira Briefing & Koordinator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min), // 🔹 Menyamakan tinggi antar kolom
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🔹 Kolom 1: Perwira Briefing
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Perwira Briefing", fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }

            // 🔹 Kolom 2: Nama Perwira
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val text = when (agenda?.terminal) {
                    "Terminal Jamrud" -> "Anton Yudhiana"
                    "Terminal Mirah", "Terminal Nilam" -> "Dimas Wibowo"
                    else -> "Nama Tidak Diketahui" // Jika terminal tidak cocok
                }

                Text(": $text", fontSize = 8.sp)
            }

            // 🔹 Kolom 3: Koordinator
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Koordinator", fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }

            // 🔹 Kolom 4: Nama Koordinator dari agenda
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(": ${agenda?.koordinator ?: "-"}", fontSize = 8.sp)
            }
        }

// 🔹 Row untuk Keterangan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min) // 🔹 Menyamakan tinggi secara otomatis
                .border(1.dp, Color.Black)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Keterangan:",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(), // 🔹 Memastikan teks melebar ke seluruh kolom
                textAlign = TextAlign.Start // 🔹 Rata kiri
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min) // 🔹 Menyamakan tinggi secara otomatis
                .border(1.dp, Color.Black)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🔹 Kolom 1: Nomor
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "1",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // 🔹 Kolom 2: Informasi Safety Briefing
            Box(
                modifier = Modifier
                    .weight(15f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Safety Briefing wajib dilakukan setiap awal shift",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start
                )
            }
        }

        // 🔹 Row untuk Informasi Safety Briefing
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min) // 🔹 Menyamakan tinggi secara otomatis
                .border(1.dp, Color.Black)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "2",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Box(
                modifier = Modifier
                    .weight(15f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Formulir Safety Briefing ini akan dimonitor oleh Dinas yang membidangi HSSE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start
                )
            }
        }

        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: MATERI YANG DISAMPAIKAN
            Column(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp)
            ) {
                Text(
                    "MATERI YANG DISAMPAIKAN :",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp)) // 🔹 Spasi kecil sebelum daftar

                Text("1. Health", fontSize = 7.sp)
                Text("2. Safety", fontSize = 7.sp)
                Text("3. Security", fontSize = 7.sp)
                Text("4. Environment", fontSize = 7.sp)
                Text("5. Operation", fontSize = 7.sp)
            }

            // 🔹 Kolom 2: Dokumentasi (dibagi menjadi 2 row)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp)
            ) {
                // 🔹 Row 1: Judul DOKUMENTASI
                Text(
                    "DOKUMENTASI:",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )

                // 🔹 Row 2: Menampilkan Gambar
                Box(
                    modifier = Modifier
                        .weight(2f) // ✅ Sesuai dengan perbandingan 1:5
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .border(1.dp, Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    val imageUrl = agenda?.photoPath // 🔹 Ambil photoPath dari Firestore
                    val painter = if (imageUrl != null) {
                        rememberAsyncImagePainter(model = imageUrl) // 🔹 Load image dari URL Firestore
                    } else {
                        painterResource(id = R.drawable.ic_launcher_background) // 🔹 Gambar dummy jika gagal
                    }

                    Image(
                        painter = painter,
                        contentDescription = "Dokumentasi",
                        modifier = Modifier
                            .width(100.dp)
                            .height(40.dp) // 🔹 Sesuaikan tinggi gambar
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                // 🔹 Judul AGENDA BRIEFING
                Text(
                    "AGENDA BRIEFING:",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp)) // 🔹 Spasi kecil sebelum isi agenda

                // 🔹 Menampilkan daftar agenda jika tersedia
                agenda?.agenda?.forEachIndexed { index, item ->
                    Text("${index + 1}. $item", fontSize = 8.sp)
                } ?: Text("- Tidak ada agenda -", fontSize = 7.sp)
            }
        }

        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: Rekap Peserta Safety Briefing
            Box(
                modifier = Modifier
                    .weight(2f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Rekap Peserta Safety Briefing",
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center
                )
            }

            // 🔹 Kolom 2: Hadir
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Hadir", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 3: Sakit
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Sakit", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Cuti", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Izin", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Tanpa Keterangan", fontSize = 8.sp, textAlign = TextAlign.Center)
            }
        }

        var jumlahPekerja by remember { mutableStateOf(0) }
        var jumlahHadir by remember { mutableStateOf(0) }
        var jumlahSakit by remember { mutableStateOf(0) }
        var jumlahCuti by remember { mutableStateOf(0) }
        var jumlahIzin by remember { mutableStateOf(0) }
        var tanpaKeterangan by remember { mutableStateOf(0) }

        var briefingData by remember { mutableStateOf<Map<String, Any>?>(null) }

        LaunchedEffect(agenda) {
            val briefingId = agenda?.briefingId
            if (briefingId == null) {
                Log.e("FirestoreDebug", "briefingId is null")
                return@LaunchedEffect
            }

            Firebase.firestore.collection("agenda")
                .document(briefingId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        Log.d("FirestoreDebug", "Data agenda ditemukan: ${document.data}")
                        briefingData = document.data
                        jumlahIzin = (document["izin"] as? List<*>)?.size ?: 0
                        jumlahSakit = (document["sakit"] as? List<*>)?.size ?: 0
                        jumlahCuti = (document["cuti"] as? List<*>)?.size ?: 0

                        val selectedTerminal = document.getString("terminal") ?: ""
                        val selectedGroup = document.getString("group") ?: ""

                        // 🔹 Ambil total pekerja berdasarkan terminal & group
                        Firebase.firestore.collection("users")
                            .whereEqualTo("terminal", selectedTerminal)
                            .whereEqualTo("group", selectedGroup)
                            .get()
                            .addOnSuccessListener { userSnapshot ->
                                val totalPekerja = userSnapshot.size()
                                Log.d("FirestoreDebug", "Total pekerja di terminal $selectedTerminal dan group $selectedGroup: $totalPekerja")

                                // 🔹 Ambil jumlah pekerja yang hadir
                                Firebase.firestore.collection("agenda")
                                    .document(briefingId)
                                    .collection("attendance")
                                    .get()
                                    .addOnSuccessListener { attendanceSnapshot ->
                                        jumlahPekerja = attendanceSnapshot.size()
                                        Log.d("FirestoreDebug", "Jumlah pekerja hadir: $jumlahPekerja")

                                        // 🔹 Hitung jumlah pekerja tanpa keterangan
                                        tanpaKeterangan = totalPekerja - (jumlahPekerja + jumlahIzin + jumlahSakit + jumlahCuti)
                                        Log.d("FirestoreDebug", "Jumlah pekerja tanpa keterangan: $tanpaKeterangan")
                                    }
                                    .addOnFailureListener { error ->
                                        Log.e("FirestoreDebug", "Gagal mengambil data attendance", error)
                                    }
                            }
                            .addOnFailureListener { error ->
                                Log.e("FirestoreDebug", "Gagal mengambil data users", error)
                            }
                    } else {
                        Log.e("FirestoreDebug", "Dokumen agenda tidak ditemukan")
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("FirestoreDebug", "Gagal mengambil data agenda", error)
                }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(2f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Jumlah Pekerja (orang)",
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center
                )
            }

            // 🔹 Kolom 2: Ambil jumlah pekerja dari sub-koleksi attendance
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahPekerja", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 3: Izin
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahIzin", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 4: Sakit
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahSakit", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 5: Cuti
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahCuti", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 6: Tanpa Keterangan
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$tanpaKeterangan", fontSize = 8.sp, textAlign = TextAlign.Center)
            }
        }


        var namaSakit by remember { mutableStateOf<List<String>>(emptyList()) }
        var namaCuti by remember { mutableStateOf<List<String>>(emptyList()) }
        var namaIzin by remember { mutableStateOf<List<String>>(emptyList()) }
        var namaTanpaKeterangan by remember { mutableStateOf<List<String>>(emptyList()) }
        var namaTidakLengkapAtribut by remember { mutableStateOf<List<String>>(emptyList()) }

        LaunchedEffect(agenda) {
            val briefingId = agenda?.briefingId
            if (briefingId == null) {
                Log.e("FirestoreDebug", "briefingId is null")
                return@LaunchedEffect
            }

            Firebase.firestore.collection("agenda")
                .document(briefingId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        Log.d("FirestoreDebug", "Data agenda ditemukan: ${document.data}")

                        // 🔹 Ambil nama pekerja berdasarkan kategori
                        namaSakit = (document["sakit"] as? List<String>) ?: emptyList()
                        namaCuti = (document["cuti"] as? List<String>) ?: emptyList()
                        namaIzin = (document["izin"] as? List<String>) ?: emptyList()
                        namaTanpaKeterangan = (document["tanpaKeterangan"] as? List<String>) ?: emptyList()
                        namaTidakLengkapAtribut = (document["tlatribut"] as? List<String>) ?: emptyList()
                    } else {
                        Log.e("FirestoreDebug", "Dokumen agenda tidak ditemukan")
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("FirestoreDebug", "Gagal mengambil data agenda", error)
                }
        }


        // 🔹 Nama Pekerja Sakit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Sakit",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }

            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Column {
                    namaSakit.forEach { nama ->
                        Text(nama, fontSize = 7.sp, textAlign = TextAlign.Left)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: Nama Pekerja Sakit
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Cuti",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }
            // 🔹 Kolom 2: Kosong
            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Column {
                    namaCuti.forEach { nama ->
                        Text(nama, fontSize = 7.sp, textAlign = TextAlign.Left)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: Nama Pekerja Sakit
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Izin",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }
            // 🔹 Kolom 2: Kosong
            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Column {
                    namaIzin.forEach { nama ->
                        Text(nama, fontSize = 7.sp, textAlign = TextAlign.Left)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: Nama Pekerja Sakit
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Tanpa Keterangan",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }
            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Column {
                    namaTanpaKeterangan.forEach { nama ->
                        Text(nama, fontSize = 7.sp, textAlign = TextAlign.Left)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, Color.Black)
                .padding(4.dp)
        ) {
            // 🔹 Kolom 1: Nama Pekerja Sakit
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Tidak Lengkap Atribut",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }

            // 🔹 Kolom 2: Kosong
            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Column {
                    namaTidakLengkapAtribut.forEach { nama ->
                        Text(nama, fontSize = 7.sp, textAlign = TextAlign.Left)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("PERWIRA BRIEFING", fontSize = 7.sp, fontWeight = FontWeight.Normal)
                Spacer(modifier = Modifier.height(40.dp)) // Ruang untuk tanda tangan
                Text(
                    text = when (agenda?.terminal) {
                        "Terminal Jamrud" -> "(Anton Yudhiana)"
                        "Terminal Mirah", "Terminal Nilam" -> "(Dimas Wibowo)"
                        else -> "(Nama Tidak Diketahui)"
                    },
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("KOORDINATOR BRIEFING", fontSize = 7.sp, fontWeight = FontWeight.Normal)
                Spacer(modifier = Modifier.height(40.dp)) // Ruang untuk tanda tangan
                Text(
                    text = "(${agenda?.koordinator ?: "-"})",
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                activity?.let { generatePdfFromCompose(it, agenda) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("unduh_pdf_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
            shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
        ) {
            Text("Unduh PDF", color = Color.White)
        }
    }
}

data class Agenda_detail(
    val briefingId: String = "",
    val terminal: String = "Tidak diketahui",
    val tempat: String = "Tidak diketahui",
    val group: String = "Tidak diketahui",
    val shift: String = "Tidak diketahui",
    val timestamp: Timestamp? = null,
    val details: String? = "",
    val koordinator: String? = "",
    val photoPath: String? = "",
    val agenda: List<String> = emptyList(),
    val tanpaKeterangan: List<String> = emptyList(),
    val izin: List<String> = emptyList(),
    val cuti: List<String> = emptyList(),
    val sakit: List<String> = emptyList(),
    val groupSecurity: String? = "",
    val groupOperational: String? = ""
)