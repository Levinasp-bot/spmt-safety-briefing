/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

// Create and deploy your first functions
// https://firebase.google.com/docs/functions/get-started

// exports.helloWorld = onRequest((request, response) => {
//   logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

// Inisialisasi Admin SDK Firebase
admin.initializeApp();

// Konfigurasi Nodemailer untuk Gmail (Atau email lainnya)
const transporter = nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: "levinaspa3@gmail.com", // Ganti dengan email kamu
    pass: "Levinasp12.",
  },
});

exports.sendApprovalEmail = functions.firestore
    .document("users/{userId}")
    .onUpdate((change, context) => {
      const before = change.before.data(); // Data sebelumnya
      const after = change.after.data(); // Data setelah update

      // Cek apakah status berubah dari false menjadi true
      if (!before.isApproved && after.isApproved) {
        const userEmail = after.realEmail; // Ambil email pengguna

        if (userEmail) {
          // Opsi email yang akan dikirim
          const mailOptions = {
            from: "levinaspa3@gmail.com", // Email pengirim
            to: userEmail, // Email penerima (dari Firestore)
            subject: "Akun Anda Telah Disetujui!", // Subjek email
            text: "Akun anda telah disetujui oleh Manager Terminal," +
            "silahkan buka aplikasi dan" +
            "Masuk menggunakan NIPP dan Password",
          };

          // Kirim email menggunakan Nodemailer
          return transporter.sendMail(mailOptions)
              .then(() => console.log("Gagal dikirim ke:", userEmail))
              .catch((error) => console.error("Gagal mengirim email:", error));
        }
      }

      return null;
    });
