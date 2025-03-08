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

class UnduhPdfActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val briefingId = intent.getStringExtra("briefingId").orEmpty() // ✅ Lebih aman jika null

        setContent {
            UnduhPdfScreen(briefingId) // ✅ Kirim briefingId ke composable
        }
    }
}

fun generatePdfFromCompose(activity: ComponentActivity, agenda: Agenda_detail?) {
    if (agenda == null) {
        Toast.makeText(activity, "Data agenda tidak tersedia", Toast.LENGTH_SHORT).show()
        return
    }

    // 🔹 1. Gunakan ComponentDialog agar memiliki LifecycleOwner yang benar
    val dialog = ComponentDialog(activity)

    // 🔹 2. Gunakan ComposeView di dalam Dialog
    val pdfComposeView = ComposeView(activity).apply {
        setContent {
            PdfLayoutScreen(agenda) // Render layout tanpa tombol
        }
    }

    // 🔹 3. Tambahkan ComposeView ke dalam dialog
    dialog.setContentView(pdfComposeView)
    dialog.window?.setLayout(1080, 1920)

    // 🔹 4. Tampilkan Dialog agar ComposeView ter-attach ke window
    dialog.show()

    Handler(Looper.getMainLooper()).postDelayed({
        // 🔹 5. Ambil screenshot setelah Dialog selesai dirender
        pdfComposeView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        pdfComposeView.layout(0, 0, 1080, 1920)

        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        pdfComposeView.draw(canvas)

        // 🔹 6. Simpan ke PDF
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val pdfCanvas = page.canvas
        pdfCanvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "SafetyBriefing_${agenda.terminal}_$timeStamp.pdf"

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        Toast.makeText(activity, "PDF berhasil disimpan di ${file.absolutePath}", Toast.LENGTH_LONG).show()

        // 🔹 7. Tutup dialog setelah screenshot selesai
        dialog.dismiss()
    }, 500) // Tunggu 500ms agar rendering selesai
}

@Composable
fun PdfLayoutScreen(agenda: Agenda_detail?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
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
                    fontSize = 10.sp,
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
                Text("No Dokumen: ", fontSize = 8.sp, fontWeight = FontWeight.Normal)
                Divider(color = Color.Black, thickness = 1.dp)

                Text("No Revisi: 0", fontSize = 8.sp, fontWeight = FontWeight.Normal)
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
                    fontSize = 8.sp, fontWeight = FontWeight.Normal
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
                    fontSize = 8.sp
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
                    fontSize = 8.sp
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
                    }",
                    fontSize = 8.sp
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
                    "Tempat: ${agenda?.terminal ?: "-"}",
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center
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
                Text(": Anton Yudhiana", fontSize = 8.sp)
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
                            .height(20.dp) // 🔹 Sesuaikan tinggi gambar
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

// 🔹 Mengambil jumlah pekerja dari Firestore (sub-koleksi attendance)
        LaunchedEffect(Unit) {
            val briefingId = agenda?.briefingId ?: return@LaunchedEffect
            Firebase.firestore.collection("agenda")
                .document(briefingId)
                .collection("attendance") // Sub-koleksi attendance
                .get()
                .addOnSuccessListener { documents ->
                    jumlahPekerja = documents.size() // Jumlah user dalam sub-koleksi
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

            // 🔹 Kolom 3: 0
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("0", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 4: 0
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("0", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 5: 0
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("0", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("0", fontSize = 8.sp, textAlign = TextAlign.Center)
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

            // 🔹 Kolom 2: Kosong
            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            )
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
            )
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
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Tanpa Keterangan",
                    fontSize = 8.sp,
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
            )
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
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Tidak Lengkap Atribut",
                    fontSize = 8.sp,
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
            )
        }
    }
}

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
            .padding(10.dp)
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
                    fontSize = 10.sp,
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
                Text("No Dokumen: ", fontSize = 8.sp, fontWeight = FontWeight.Normal)
                Divider(color = Color.Black, thickness = 1.dp)

                Text("No Revisi: 0", fontSize = 8.sp, fontWeight = FontWeight.Normal)
                Divider(color = Color.Black, thickness = 1.dp)

                Text(
                    "Tanggal: ${agenda?.timestamp?.toDate()?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: "-"}",
                    fontSize = 8.sp, fontWeight = FontWeight.Normal
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
                    fontSize = 8.sp
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
                    fontSize = 8.sp
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
                    }",
                    fontSize = 8.sp
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
                    "Tempat: ${agenda?.terminal ?: "-"}",
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center
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
                Text(": Anton Yudhiana", fontSize = 8.sp)
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
                            .height(20.dp) // 🔹 Sesuaikan tinggi gambar
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

        LaunchedEffect(Unit) {
            val briefingId = agenda?.briefingId ?: return@LaunchedEffect
            Firebase.firestore.collection("agenda")
                .document(briefingId)
                .collection("attendance") // Sub-koleksi attendance
                .get()
                .addOnSuccessListener { documents ->
                    jumlahPekerja = documents.size() // Jumlah user dalam sub-koleksi
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

            // 🔹 Kolom 3: 0
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("0", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 4: 0
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("0", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 5: 0
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("0", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            // 🔹 Kolom 6: 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("0", fontSize = 8.sp, textAlign = TextAlign.Center)
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

            // 🔹 Kolom 2: Kosong
            Box(
                modifier = Modifier
                    .weight(4f)
                    .border(1.dp, Color.Black)
                    .fillMaxHeight()
                    .padding(4.dp)
            )
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
            )
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
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Tanpa Keterangan",
                    fontSize = 8.sp,
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
            )
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
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Nama Pekerja Tidak Lengkap Atribut",
                    fontSize = 8.sp,
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
            )
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
    val shift: String = "Tidak diketahui",
    val timestamp: Timestamp? = null,
    val details: String? = "",
    val koordinator: String? = "",
    val photoPath: String? = "",
    val agenda: List<String> = emptyList()
)