package com.example.spmtsafetybriefingapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
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
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.TypedValue
import androidx.annotation.RequiresApi
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UnduhPdfActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val briefingId = intent.getStringExtra("briefingId").orEmpty() // ✅ Lebih aman jika null

        setContent {
            UnduhPdfScreen(briefingId)
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

suspend fun getBitmapFromUrl(context: Context, url: String): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            (result as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }
}

fun saveBitmapAsPdf(activity: ComponentActivity, bitmap: Bitmap, agenda: Agenda_detail) {
    val pageHeight = 1800
    val pageWidth = bitmap.width
    val pdfDocument = PdfDocument()

    var pageNumber = 1

    // 🔹 Halaman Pertama: Konten Utama
    val firstPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
    val firstPage = pdfDocument.startPage(firstPageInfo)
    firstPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
    pdfDocument.finishPage(firstPage)

    // 🔹 Halaman Kedua: Tanda Tangan & Barcode
    pageNumber++
    val secondPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
    val secondPage = pdfDocument.startPage(secondPageInfo)
    val canvas = secondPage.canvas

    // 🔹 Konversi dp dan sp ke pixel
    val metrics = activity.resources.displayMetrics
    val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 7f, metrics) // 7sp ke px
    val barcodeSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40f, metrics).toInt() // 40dp ke px

    // 🔹 Pengaturan teks
    val paint = Paint().apply {
        textSize = textSizePx
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.CENTER // ✅ Teks di tengah
    }

    // 🔹 Posisi dasar
    val centerXPerwira = pageWidth * 0.3f // Center untuk perwira
    val centerXKoordinator = pageWidth * 0.7f // Center untuk koordinator
    val sameYPosition = 150f // 🔥 Perwira & Koordinator sejajar
    val barcodeOffsetY = 20f  // Jarak barcode dari teks
    val spacingAboveName = barcodeSizePx + 50f // ✅ Jarak keterangan ke nama lebih besar dari barcode
    var imageUrl: String? = null
    val firestore = FirebaseFirestore.getInstance()

    val fieldName = agenda.terminal?.split(" ")?.getOrNull(1)

    Log.d("FirestoreDebug", "Field Name: $fieldName")

    fieldName?.let { field ->
        // Menentukan role perwira berdasarkan terminal (contoh: "Manager Operasi Jamrud")
        val terminalName = agenda.terminal
        firestore.collection("users")
            .document("Manager")
            .get()
            .addOnSuccessListener { documentSnapshot ->
                val perwiraName = documentSnapshot.getString(terminalName) ?: "Nama Tidak Diketahui"

                Log.d("FirestoreDebug", "Perwira untuk $terminalName adalah $perwiraName")

                firestore.collection("image").document("ttd_manager").get()
                    .addOnSuccessListener { doc ->
                        Log.d("FirestoreDebug", "Dokumen berhasil diambil dari Firestore")
                        val imageUrl = doc.getString(field)

                        Log.d("FirestoreDebug", "imageUrl: $imageUrl")

                        // Lanjutkan jika URL ditemukan
                        imageUrl?.let { imageLink ->
                            CoroutineScope(Dispatchers.IO).launch {
                                // Unduh gambar dari URL
                                val imageBitmap = getBitmapFromUrl(activity, imageLink)

                                Log.d("FirestoreDebug", "imageBitmap: $imageBitmap")

                                // Pastikan gambar sudah tersedia sebelum menggambar di canvas
                                withContext(Dispatchers.Main) {
                                    val canvas = secondPage.canvas // Pastikan bitmap untuk canvas sudah ada

                                    // 🟢 PERWIRA BRIEFING (LURUS DENGAN KOORDINATOR)
                                    canvas.drawText(
                                        "PERWIRA BRIEFING",
                                        centerXPerwira,
                                        sameYPosition,
                                        paint
                                    )

                                    // Gambar barcode di canvas
                                    imageBitmap?.let { image ->
                                        Log.d("FirestoreDebug", "Gambar ditemukan, menggambar di canvas")
                                        val scaledImage = Bitmap.createScaledBitmap(
                                            image,
                                            barcodeSizePx,
                                            barcodeSizePx,
                                            false
                                        )
                                        canvas.drawBitmap(
                                            scaledImage,
                                            centerXPerwira - barcodeSizePx / 2,
                                            sameYPosition + barcodeOffsetY,
                                            null
                                        ) // Barcode tetap center
                                    } ?: Log.d("FirestoreDebug", "Gambar tidak ditemukan")

                                    // Menambahkan nama perwira ke canvas
                                    canvas.drawText(
                                        "($perwiraName)",
                                        centerXPerwira,
                                        sameYPosition + spacingAboveName,
                                        paint
                                    )

                                    // Koordinator briefing
                                    canvas.drawText(
                                        "KOORDINATOR BRIEFING",
                                        centerXKoordinator,
                                        sameYPosition,
                                        paint
                                    )

                                    // Ambil barcode koordinator
                                    val groupSuffix = agenda.group?.lastOrNull()?.toString() ?: ""
                                    val koorField = fieldName + groupSuffix // Misal: "Jamrud" + "A" = "JamrudA"

                                    firestore.collection("image").document("ttd_koor").get()
                                        .addOnSuccessListener { barcodeDoc ->
                                            val koorImageUrl = barcodeDoc.getString(koorField)

                                            CoroutineScope(Dispatchers.IO).launch {
                                                val koorBitmap = getBitmapFromUrl(activity, koorImageUrl.toString())

                                                withContext(Dispatchers.Main) {
                                                    koorBitmap?.let { image ->
                                                        val scaledImage = Bitmap.createScaledBitmap(
                                                            image,
                                                            barcodeSizePx,
                                                            barcodeSizePx,
                                                            false
                                                        )
                                                        canvas.drawBitmap(
                                                            scaledImage,
                                                            centerXKoordinator - barcodeSizePx / 2,
                                                            sameYPosition + barcodeOffsetY,
                                                            null
                                                        )
                                                    }

                                                    canvas.drawText(
                                                        "(${agenda.koordinator ?: "-"})",
                                                        centerXKoordinator,
                                                        sameYPosition + spacingAboveName,
                                                        paint
                                                    )

                                                    // ✅ Selesaikan halaman dan simpan PDF
                                                    pdfDocument.finishPage(secondPage)

                                                    val timeStamp = SimpleDateFormat(
                                                        "yyyyMMdd_HHmmss",
                                                        Locale.getDefault()
                                                    ).format(Date())
                                                    val fileName =
                                                        "SafetyBriefing_${agenda.terminal}_$timeStamp.pdf"
                                                    val downloadsDir =
                                                        Environment.getExternalStoragePublicDirectory(
                                                            Environment.DIRECTORY_DOWNLOADS
                                                        )
                                                    val file = File(downloadsDir, fileName)

                                                    pdfDocument.writeTo(FileOutputStream(file))
                                                    pdfDocument.close()

                                                    Toast.makeText(
                                                        activity,
                                                        "PDF berhasil disimpan di ${file.absolutePath}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                        .addOnFailureListener {
                                            Log.e("FirestoreDebug", "Gagal mengambil barcode koordinator: ${it.message}")
                                        }
                                }
                            }
                        } ?: Log.d("FirestoreDebug", "imageUrl kosong")
                    }
                    .addOnFailureListener {
                        Toast.makeText(activity, "Gagal ambil gambar dari Firestore", Toast.LENGTH_SHORT).show()
                        Log.d("FirestoreDebug", "Gagal mengambil dokumen dari Firestore: ${it.message}")
                    }
            }
            .addOnFailureListener {
                Log.e("FirestoreDebug", "Gagal mengambil perwira: ${it.message}")
            }
    } ?: Log.d("FirestoreDebug", "fieldName kosong")

}

@Composable
fun PdfLayoutScreen(agenda: Agenda_detail?) {
    Column(
        modifier = Modifier
            .background(Color.White)
            .fillMaxSize()
            .padding(start = 28.dp, top = 10.dp, end = 28.dp) // Atur padding atas, kanan, kiri
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

            var namaPerwira by remember { mutableStateOf("Nama Tidak Diketahui") }

            LaunchedEffect(agenda?.terminal) {
                val firestore = FirebaseFirestore.getInstance()
                val terminalName = agenda?.terminal

                firestore.collection("users").document("Manager")
                    .get()
                    .addOnSuccessListener { document ->
                        val name = document.getString(terminalName.toString())
                        if (!name.isNullOrEmpty()) {
                            namaPerwira = name
                        }
                    }
                    .addOnFailureListener {
                        Log.e("Firestore", "Gagal mengambil nama dari Manager", it)
                    }
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
                val text = namaPerwira
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

                        Firebase.firestore.collection("users")
                            .whereEqualTo("terminal", selectedTerminal)
                            .whereEqualTo("group", selectedGroup)
                            .get()
                            .addOnSuccessListener { userSnapshot ->
                                val totalPekerja = userSnapshot.size()
                                Log.d("FirestoreDebug", "Total pekerja di terminal $selectedTerminal dan group $selectedGroup: $totalPekerja")

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

            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahPekerja", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahIzin", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahSakit", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$jumlahCuti", fontSize = 8.sp, textAlign = TextAlign.Center)
            }

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
                        briefingData = document.data
                        jumlahIzin = (document["izin"] as? List<*>)?.size ?: 0
                        jumlahSakit = (document["sakit"] as? List<*>)?.size ?: 0
                        jumlahCuti = (document["cuti"] as? List<*>)?.size ?: 0

                        val selectedTerminal = document.getString("terminal") ?: ""
                        val selectedGroup = document.getString("group") ?: ""

                        Firebase.firestore.collection("users")
                            .whereEqualTo("terminal", selectedTerminal)
                            .whereEqualTo("group", selectedGroup)
                            .get()
                            .addOnSuccessListener { userSnapshot ->
                                val semuaNamaPekerja =
                                    userSnapshot.documents.mapNotNull { it.getString("name") }
                                Log.d(
                                    "FirestoreDebug",
                                    "Total pekerja terdaftar: ${semuaNamaPekerja.size}"
                                )
                                semuaNamaPekerja.forEach {
                                    Log.d("FirestoreDebug", "Nama Pekerja Terdaftar: $it")
                                }

                                Firebase.firestore.collection("agenda")
                                    .document(briefingId)
                                    .collection("attendance")
                                    .get()
                                    .addOnSuccessListener { attendanceSnapshot ->
                                        val namaHadir =
                                            attendanceSnapshot.documents.mapNotNull { it.getString("userName") }
                                        jumlahPekerja = namaHadir.size
                                        Log.d(
                                            "FirestoreDebug",
                                            "Jumlah pekerja hadir: $jumlahPekerja"
                                        )
                                        namaHadir.forEach {
                                            Log.d("FirestoreDebug", "Nama Hadir: $it")
                                        }

                                        val namaDenganKeterangan =
                                            (namaHadir + namaSakit + namaCuti + namaIzin + namaTidakLengkapAtribut).map {
                                                it.trim().lowercase()
                                            }
                                        Log.d(
                                            "FirestoreDebug",
                                            "Nama dengan keterangan: $namaDenganKeterangan"
                                        )

                                        val hasilNamaTanpaKeterangan =
                                            semuaNamaPekerja.filter { nama ->
                                                val cleanNama = nama.trim().lowercase()
                                                cleanNama !in namaHadir.map {
                                                    it.trim().lowercase()
                                                } &&
                                                        cleanNama !in namaDenganKeterangan
                                            }

                                        namaTanpaKeterangan = hasilNamaTanpaKeterangan
                                        Log.d(
                                            "FirestoreDebug",
                                            "Nama tanpa keterangan (${namaTanpaKeterangan.size}): $namaTanpaKeterangan"
                                        )
                                    }
                                    .addOnFailureListener { error ->
                                        Log.e(
                                            "FirestoreDebug",
                                            "Gagal mengambil data attendance",
                                            error
                                        )
                                    }
                            }
                            .addOnFailureListener { error ->
                                Log.e("FirestoreDebug", "Gagal mengambil data users", error)
                            }
                    }
                }
        }

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

                        namaSakit = (document["sakit"] as? List<String>) ?: emptyList()
                        namaCuti = (document["cuti"] as? List<String>) ?: emptyList()
                        namaIzin = (document["izin"] as? List<String>) ?: emptyList()
                        namaTidakLengkapAtribut = (document["izin"] as? List<String>) ?: emptyList()
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
                val namaTanpaKeteranganFormatted = namaTanpaKeterangan.joinToString(", ") { nama ->
                    nama.split(" ").take(2).joinToString(" ")
                }

                Text(
                    text = namaTanpaKeteranganFormatted,
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }
        }

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

        Spacer(modifier = Modifier.height(150.dp))

        val context = LocalContext.current
        var perwiraBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var koorBitmap by remember { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(agenda?.terminal, agenda?.group) {
            val firestore = FirebaseFirestore.getInstance()

            val perwiraField = agenda?.terminal?.substringAfter("Terminal ")?.replace(" ", "") ?: ""
            firestore.collection("image").document("ttd_manager").get()
                .addOnSuccessListener { document ->
                    val url = document.getString(perwiraField)
                    url?.let {
                        CoroutineScope(Dispatchers.IO).launch {
                            val bitmap = getBitmapFromUrl(context, it)
                            withContext(Dispatchers.Main) {
                                perwiraBitmap = bitmap
                            }
                        }
                    }
                }

            // Koordinator
            val koorField = perwiraField + (agenda?.group?.lastOrNull()?.toString() ?: "")
            firestore.collection("image").document("ttd_koor").get()
                .addOnSuccessListener { document ->
                    val url = document.getString(koorField)
                    url?.let {
                        CoroutineScope(Dispatchers.IO).launch {
                            val bitmap = getBitmapFromUrl(context, it)
                            withContext(Dispatchers.Main) {
                                koorBitmap = bitmap
                            }
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
            // === PERWIRA BRIEFING ===
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PERWIRA BRIEFING", fontSize = 7.sp, fontWeight = FontWeight.Normal)
                Spacer(modifier = Modifier.height(5.dp))

                perwiraBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Barcode tanda tangan perwira",
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                val context = LocalContext.current
                val firestore = FirebaseFirestore.getInstance()
                var namaPerwira by remember { mutableStateOf("(Nama Tidak Diketahui)") }

                LaunchedEffect(agenda?.terminal) {
                    agenda?.terminal?.let { terminal ->
                        // Ambil nama terminal yang terakhir (misal "Terminal Jamrud" -> "Jamrud")
                        val terminalName = terminal.substringAfterLast(" ").trim()
                        val role = "Manager Operasi $terminalName"

                        firestore.collection("users")
                            .whereEqualTo("role", role)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                val name = querySnapshot.documents.firstOrNull()?.getString("nama")
                                if (!name.isNullOrEmpty()) {
                                    namaPerwira = "($name)"
                                }
                            }
                    }
                }

                Text(
                    text = namaPerwira,
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center
                )
            }

            // === KOORDINATOR BRIEFING ===
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("KOORDINATOR BRIEFING", fontSize = 7.sp, fontWeight = FontWeight.Normal)
                Spacer(modifier = Modifier.height(5.dp))

                koorBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Barcode tanda tangan koordinator",
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))
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
            .background(Color.White)
            .fillMaxSize()
            .padding(start = 28.dp, top = 10.dp, end = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                        .width(90.dp)
                        .height(30.dp)
                )

            }
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
                    .padding(4.dp)
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(4.dp) 
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

            var namaPerwira by remember { mutableStateOf("Nama Tidak Diketahui") }

            LaunchedEffect(agenda?.terminal) {
                val firestore = FirebaseFirestore.getInstance()
                val terminalName = agenda?.terminal

                firestore.collection("users").document("Manager")
                    .get()
                    .addOnSuccessListener { document ->
                        val name = document.getString(terminalName.toString())
                        if (!name.isNullOrEmpty()) {
                            namaPerwira = name
                        }
                    }
                    .addOnFailureListener {
                        Log.e("Firestore", "Gagal mengambil nama dari Manager", it)
                    }
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
                val text = namaPerwira
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black)
                    .padding(4.dp)
            ) {

                Text(
                    "DOKUMENTASI:",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .weight(2f)
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
                        briefingData = document.data
                        jumlahIzin = (document["izin"] as? List<*>)?.size ?: 0
                        jumlahSakit = (document["sakit"] as? List<*>)?.size ?: 0
                        jumlahCuti = (document["cuti"] as? List<*>)?.size ?: 0

                        val selectedTerminal = document.getString("terminal") ?: ""
                        val selectedGroup = document.getString("group") ?: ""

                        Firebase.firestore.collection("users")
                            .whereEqualTo("terminal", selectedTerminal)
                            .whereEqualTo("group", selectedGroup)
                            .get()
                            .addOnSuccessListener { userSnapshot ->
                                val semuaNamaPekerja =
                                    userSnapshot.documents.mapNotNull { it.getString("name") }
                                Log.d(
                                    "FirestoreDebug",
                                    "Total pekerja terdaftar: ${semuaNamaPekerja.size}"
                                )
                                semuaNamaPekerja.forEach {
                                    Log.d("FirestoreDebug", "Nama Pekerja Terdaftar: $it")
                                }

                                Firebase.firestore.collection("agenda")
                                    .document(briefingId)
                                    .collection("attendance")
                                    .get()
                                    .addOnSuccessListener { attendanceSnapshot ->
                                        val namaHadir =
                                            attendanceSnapshot.documents.mapNotNull { it.getString("userName") }
                                        jumlahPekerja = namaHadir.size
                                        Log.d(
                                            "FirestoreDebug",
                                            "Jumlah pekerja hadir: $jumlahPekerja"
                                        )
                                        namaHadir.forEach {
                                            Log.d("FirestoreDebug", "Nama Hadir: $it")
                                        }

                                        val namaDenganKeterangan =
                                            (namaHadir + namaSakit + namaCuti + namaIzin + namaTidakLengkapAtribut).map {
                                                it.trim().lowercase()
                                            }
                                        Log.d(
                                            "FirestoreDebug",
                                            "Nama dengan keterangan: $namaDenganKeterangan"
                                        )

                                        val hasilNamaTanpaKeterangan =
                                            semuaNamaPekerja.filter { nama ->
                                                val cleanNama = nama.trim().lowercase()
                                                cleanNama !in namaHadir.map {
                                                    it.trim().lowercase()
                                                } &&
                                                        cleanNama !in namaDenganKeterangan
                                            }

                                        namaTanpaKeterangan = hasilNamaTanpaKeterangan
                                        Log.d(
                                            "FirestoreDebug",
                                            "Nama tanpa keterangan (${namaTanpaKeterangan.size}): $namaTanpaKeterangan"
                                        )
                                    }
                                    .addOnFailureListener { error ->
                                        Log.e(
                                            "FirestoreDebug",
                                            "Gagal mengambil data attendance",
                                            error
                                        )
                                    }
                            }
                            .addOnFailureListener { error ->
                                Log.e("FirestoreDebug", "Gagal mengambil data users", error)
                            }
                    }
                }
        }

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
                val namaTanpaKeteranganFormatted = namaTanpaKeterangan.joinToString(", ") { nama ->
                    nama.split(" ").take(2).joinToString(" ")
                }

                Text(
                    text = namaTanpaKeteranganFormatted,
                    fontSize = 7.sp,
                    textAlign = TextAlign.Left
                )
            }
        }

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

        Spacer(modifier = Modifier.height(150.dp))

        val context = LocalContext.current
        var perwiraBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var koorBitmap by remember { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(agenda?.terminal, agenda?.group) {
            val firestore = FirebaseFirestore.getInstance()
            val terminalName = agenda?.terminal?.substringAfter("Terminal ")?.replace(" ", "")

            if (!terminalName.isNullOrEmpty()) {
                firestore.collection("image").document("ttd_manager").get()
                    .addOnSuccessListener { document ->
                        val url = document.getString(terminalName)
                        url?.let {
                            CoroutineScope(Dispatchers.IO).launch {
                                val bitmap = getBitmapFromUrl(context, it)
                                withContext(Dispatchers.Main) {
                                    perwiraBitmap = bitmap
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("FirestoreDebug", "Gagal mengambil data tanda tangan", e)
                    }
            } else {
                Log.e("FirestoreDebug", "Terminal tidak valid atau kosong")
            }

            val koorField = terminalName + (agenda?.group?.lastOrNull()?.toString() ?: "")
            firestore.collection("image").document("ttd_koor").get()
                .addOnSuccessListener { document ->
                    val url = document.getString(koorField)
                    url?.let {
                        CoroutineScope(Dispatchers.IO).launch {
                            val bitmap = getBitmapFromUrl(context, it)
                            withContext(Dispatchers.Main) {
                                koorBitmap = bitmap
                            }
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
            // === PERWIRA BRIEFING ===
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PERWIRA BRIEFING", fontSize = 7.sp, fontWeight = FontWeight.Normal)
                Spacer(modifier = Modifier.height(5.dp))

                perwiraBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Barcode tanda tangan perwira",
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                var namaPerwira by remember { mutableStateOf("(Nama Tidak Diketahui)") }
                val firestore = FirebaseFirestore.getInstance()

                LaunchedEffect(agenda?.terminal) {
                    val terminalName = agenda?.terminal

                    firestore.collection("users").document("Manager")
                        .get()
                        .addOnSuccessListener { document ->
                            val name = document.getString(terminalName.toString())
                            if (!name.isNullOrEmpty()) {
                                namaPerwira = "($name)"
                            } else {
                                namaPerwira = "(Nama Tidak Diketahui)"
                            }
                        }
                        .addOnFailureListener {
                            Log.e("Firestore", "Gagal mengambil nama perwira", it)
                            namaPerwira = "(Error Mengambil Nama)"
                        }
                }

                Text(
                    text = namaPerwira,
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center
                )
            }

            // === KOORDINATOR BRIEFING ===
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("KOORDINATOR BRIEFING", fontSize = 7.sp, fontWeight = FontWeight.Normal)
                Spacer(modifier = Modifier.height(5.dp))

                koorBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Barcode tanda tangan koordinator",
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))
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