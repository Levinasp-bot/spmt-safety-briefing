package com.example.spmtsafetybriefingapp

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
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
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.google.firebase.firestore.ListenerRegistration
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.compose.ui.unit.TextUnit

class UnduhAbsensiActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ambil data dari Intent
        val intent = intent
        val selectedTerminal = intent.getStringExtra("selectedTerminal") ?: ""
        val selectedShift = intent.getStringExtra("selectedShift") ?: ""
        val selectedDate = intent.getStringExtra("selectedDate") ?: ""
        val totalUsers = intent.getIntExtra("totalUsers", 0)
        val absentUsers = intent.getIntExtra("absentUsers", 0)
        val presentUsers = intent.getIntExtra("presentUsers", 0)
        val briefingId = intent.getStringExtra("briefingId") ?: ""

        val cutiList = intent.getStringArrayListExtra("cutiList")?.toSet() ?: emptySet()
        val sakitList = intent.getStringArrayListExtra("sakitList")?.toSet() ?: emptySet()
        val izinList = intent.getStringArrayListExtra("izinList")?.toSet() ?: emptySet()
        val filteredUsers = intent.getStringArrayListExtra("filteredUsers") ?: emptyList()

        val attendanceListString = intent.getStringArrayListExtra("attendanceList") ?: arrayListOf()
        val attendanceList = attendanceListString.map {
            val parts = it.split(" - ")
            Pair(parts[0], parts.getOrNull(1) ?: "-")
        }

        setContent {
            UnduhPdfAbsensi(
                selectedTerminal = selectedTerminal,
                selectedShift = selectedShift,
                selectedDate = selectedDate,
                totalUsers = totalUsers,
                absentUsers = absentUsers,
                presentUsers = presentUsers,
                briefingId = briefingId,
                cutiList = cutiList.toSet(),   // ✅ Konversi ke Set<String>
                sakitList = sakitList.toSet(), // ✅ Konversi ke Set<String>
                izinList = izinList.toSet(),
                filteredUsers = filteredUsers,
                attendanceList = attendanceList
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun generatePdf(
    activity: ComponentActivity,
    selectedTerminal: String,
    selectedShift: String,
    selectedDate: String,
    totalUsers: Int,
    absentUsers: Int,
    presentUsers: Int,
    briefingId: String,
    cutiList: Set<String>,
    sakitList: Set<String>,
    izinList: Set<String>,
    filteredUsers: List<String>,
    attendanceList: List<Pair<String, String>>
) {
    val pdfComposeView = ComposeView(activity).apply {
        setContent {
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // 🔹 Header Laporan
                Text(
                    text = "Laporan Absensi Safety Briefing",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))

                val tanpaKeteranganCount = filteredUsers.count { user ->
                    user !in cutiList && user !in sakitList && user !in izinList &&
                            attendanceList.none { it.first == user }
                }
                // 🔹 Informasi Terminal, Shift, dan Tanggal
                Column(modifier = Modifier.fillMaxWidth()) {
                    InfoRow("Terminal:", selectedTerminal, fontSize = 10.sp)
                    InfoRow("Shift:", selectedShift, fontSize = 10.sp)
                    InfoRow("Tanggal:", selectedDate, fontSize = 10.sp)
                    InfoRow("Hadir:", presentUsers.toString(), fontSize = 10.sp)
                    InfoRow("Cuti:", cutiList.size.toString(), fontSize = 10.sp)
                    InfoRow("Izin:", izinList.size.toString(), fontSize = 10.sp)
                    InfoRow("Sakit:", sakitList.size.toString(), fontSize = 10.sp)
                    InfoRow("Tanpa Keterangan:", tanpaKeteranganCount.toString(), fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 🔹 Tabel Absensi
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Black)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Gray)
                            .padding(2.dp)
                    ) {
                        TableCell("No", 0.2f, Color.White, true, fontSize = 10.sp)
                        DividerVertikal()
                        TableCell("Nama", 1f, Color.White, true, fontSize = 10.sp)
                        DividerVertikal()
                        TableCell("Waktu", 0.8f, Color.White, true, fontSize = 10.sp)
                        DividerVertikal()
                        TableCell("Keterangan", 1f, Color.White, true, fontSize = 10.sp)
                    }
                    Divider(color = Color.Black, thickness = 1.dp)

                    val sortedUsers = filteredUsers.map { name ->
                        val timestamp = attendanceList.find { it.first == name }?.second
                        Pair(name, timestamp)
                    }.sortedByDescending { it.second } // Urutkan dari terbaru ke terlama

                    sortedUsers.forEachIndexed { index, (name, timestamp) ->
                        val status = when {
                            name in cutiList -> "Cuti"
                            name in sakitList -> "Sakit"
                            name in izinList -> "Izin"
                            timestamp != null -> "Hadir"
                            else -> "Tanpa Keterangan"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        ) {
                            TableCell("${index + 1}", 0.2f, fontSize = 10.sp)
                            DividerVertikal()
                            TableCell(name, 1f, fontSize = 10.sp)
                            DividerVertikal()
                            TableCell(timestamp ?: "-", 0.8f, textColor = Color.Gray, fontSize = 10.sp)
                            DividerVertikal()
                            TableCell(status, 1f, textColor = if (status == "Hadir") Color.Green else Color.Red, fontSize = 10.sp)
                        }
                        Divider(color = Color.LightGray, thickness = 0.8.dp)
                    }
                }
            }
        }
    }

    val dialog = ComponentDialog(activity)
    dialog.setContentView(pdfComposeView)
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.show()

    pdfComposeView.viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                pdfComposeView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                pdfComposeView.measure(
                    View.MeasureSpec.makeMeasureSpec(pdfComposeView.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED) // 🚀 Ambil seluruh tinggi
                )
                pdfComposeView.layout(0, 0, pdfComposeView.measuredWidth, pdfComposeView.measuredHeight)

                val width = pdfComposeView.measuredWidth
                val height = pdfComposeView.measuredHeight

                if (width > 0 && height > 0) {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    pdfComposeView.draw(canvas)

                    saveBitmap(activity, bitmap, filteredUsers.size)
                } else {
                    Toast.makeText(activity, "Gagal mengambil konten", Toast.LENGTH_SHORT).show()
                }

                dialog.dismiss()
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String, fontSize: TextUnit = 10.sp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = label,
            fontSize = fontSize, // ✅ Menggunakan fontSize
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            fontSize = fontSize // ✅ Sama, menggunakan fontSize yang bisa dikustomisasi
        )
    }
}

fun saveBitmap(activity: ComponentActivity, bitmap: Bitmap, totalRows: Int) {
    val pageWidth = 720
    val pageHeight = 1280
    val topPadding = 50
    val contentHeight = 1650

    val pdfDocument = PdfDocument()

    val maxScaleFactor = minOf(
        pageWidth.toFloat() / bitmap.width.toFloat(),
        contentHeight.toFloat() / bitmap.height.toFloat()
    )

    val scalePercentage = 0.96f
    val finalScaleFactor = pageWidth.toFloat() / bitmap.width.toFloat() * 0.96f

    val scaledWidth = (bitmap.width * finalScaleFactor).toInt()
    val scaledHeight = (bitmap.height * finalScaleFactor).toInt()

    var yOffset = 0
    var pageNumber = 1

    while (yOffset < bitmap.height) {
        val remainingHeight = bitmap.height - yOffset
        val heightToCopy = minOf(remainingHeight, contentHeight)

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val pdfCanvas = page.canvas

        pdfCanvas.drawColor(android.graphics.Color.WHITE)

        val partBitmap = Bitmap.createBitmap(bitmap, 0, yOffset, bitmap.width, heightToCopy)

        val matrix = Matrix().apply {
            postScale(finalScaleFactor, finalScaleFactor)
            postTranslate(
                ((pageWidth - scaledWidth) / 2).toFloat(),
                topPadding.toFloat()
            )
        }

        pdfCanvas.drawBitmap(partBitmap, matrix, null)
        pdfDocument.finishPage(page)

        yOffset += heightToCopy

        // 🔥 **Pastikan semua baris masuk ke halaman berikutnya**
        if (yOffset >= bitmap.height && totalRows > 25) {
            break
        }

        pageNumber++
    }

    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "Laporan_$timeStamp.pdf"

    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val file = File(downloadsDir, fileName)

    pdfDocument.writeTo(FileOutputStream(file))
    pdfDocument.close()

    Toast.makeText(activity, "PDF berhasil disimpan di ${file.absolutePath}", Toast.LENGTH_LONG).show()
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun UnduhPdfAbsensi(
    briefingId: String,
    selectedTerminal: String,
    selectedShift: String,
    selectedDate: String,
    totalUsers: Int,
    absentUsers: Int,
    presentUsers: Int,
    cutiList: Set<String>,
    sakitList: Set<String>,
    izinList: Set<String>,
    filteredUsers: List<String>,
    attendanceList: List<Pair<String, String>>
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    Column(
        modifier = Modifier
            .background(Color.White)
            .fillMaxSize()
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 54.dp)
    ) {
        // 🔹 Judul Laporan Absensi (Tetap di Atas)
        Text(
            text = "Laporan Absensi Safety Briefing",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        val tanpaKeteranganCount = filteredUsers.count { user ->
            user !in cutiList && user !in sakitList && user !in izinList &&
                    attendanceList.none { it.first == user }
        }
        // 🔹 Informasi Terminal, Shift, dan Tanggal
        Column(modifier = Modifier.fillMaxWidth()) {
            InfoRow("Terminal:", selectedTerminal, fontSize = 10.sp)
            InfoRow("Shift:", selectedShift, fontSize = 10.sp)
            InfoRow("Tanggal:", selectedDate, fontSize = 10.sp)
            InfoRow("Hadir:", presentUsers.toString(), fontSize = 10.sp)
            InfoRow("Cuti:", cutiList.size.toString(), fontSize = 10.sp)
            InfoRow("Izin:", izinList.size.toString(), fontSize = 10.sp)
            InfoRow("Sakit:", sakitList.size.toString(), fontSize = 10.sp)
            InfoRow("Tanpa Keterangan:", tanpaKeteranganCount.toString(), fontSize = 10.sp)
        }

        Spacer(modifier = Modifier.height(8.dp)) // 🔹 Tambahkan jarak sebelum tabel

        // 🔹 LazyColumn Harus Ada di Dalam Column, dan Menggunakan weight(1f)
        if (filteredUsers.isEmpty()) {
            Text(
                text = "Belum ada data pengguna",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // 🔥 Memastikan tabel mengambil sisa ruang
                    .padding(2.dp)
            ) {
                // 🔹 Header Tabel
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(Color.White)
                            .padding(vertical = 3.dp)
                    ) {
                        TableCell("No", 0.3f, Color.Black, true)
                        DividerVertikal()
                        TableCell("Nama", 1f, Color.Black, true)
                        DividerVertikal()
                        TableCell("Waktu Kehadiran", 1f, Color.Black, true)
                        DividerVertikal()
                        TableCell("Keterangan", 1f, Color.Black, true)
                    }
                    Divider(color = Color.Black, thickness = 1.dp)
                }

                val sortedUsers = filteredUsers.map { name ->
                    val timestamp = attendanceList.find { it.first == name }?.second
                    Pair(name, timestamp)
                }.sortedByDescending { it.second }

                itemsIndexed(sortedUsers) { index, (name, timestamp) ->
                    val status = when {
                        name in cutiList -> "Cuti"
                        name in sakitList -> "Sakit"
                        name in izinList -> "Izin"
                        timestamp != null -> "Hadir"
                        else -> "Tanpa Keterangan"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .height(IntrinsicSize.Min)
                    ) {
                        TableCell("${index + 1}", 0.3f)
                        DividerVertikal()
                        TableCell(name, 1f)
                        DividerVertikal()
                        TableCell(timestamp ?: "-", 1f, textColor = Color.Gray)
                        DividerVertikal()
                        TableCell(status, 1f, textColor = if (status == "Hadir") Color.Green else Color.Red)
                    }

                    Divider(color = Color.LightGray, thickness = 1.dp)
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp) // Opsional: Padding agar tidak mepet layar
    ) {
        //
        Button(
            onClick = {
                activity?.let { safeActivity ->
                    generatePdf(safeActivity, selectedTerminal, selectedShift, selectedDate, totalUsers, absentUsers, presentUsers, briefingId,
                        cutiList,
                        sakitList,
                        izinList, filteredUsers, attendanceList)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp)
                .align(Alignment.BottomCenter) // Memastikan tombol ada di bawah
                .testTag("unduh_pdf_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E73A7)),
            shape = MaterialTheme.shapes.small.copy(all = CornerSize(8.dp))
        ) {
            Text("Unduh Laporan Absensi", color = Color.White)
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    textColor: Color = Color.Black,
    isHeader: Boolean = false,
    fontSize: TextUnit = 12.sp
) {
    Text(
        text = text,
        fontSize = fontSize, // 🔻 Diperkecil lagi
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        color = textColor,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(weight) // ✅ Gunakan dalam RowScope agar tidak error
            .padding(3.dp) // 🔻 Lebih kecil
    )
}

@Composable
fun DividerVertikal() {
    Box(
        modifier = Modifier
            .fillMaxHeight() // ✅ Pastikan tinggi mengikuti parent Row
            .width(1.dp)
            .background(Color.Black) // ✅ Pakai background agar lebih jelas
    )
}


