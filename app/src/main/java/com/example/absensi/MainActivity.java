package com.example.absensi;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private LinearLayout containerKehadiran;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Method untuk menyimpan data user
    public void saveUserData(FirebaseUser user) {
        String uid = user.getUid(); // UID pengguna
        String nim = user.getDisplayName(); // Ambil NIM dari display name user (NIM seharusnya disimpan di sini)

        // Buat map untuk menyimpan data
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("nim", nim);
        userMap.put("nama", "Nama User");  // Ganti dengan nama yang sesuai atau ambil dari input
        userMap.put("jurusan", "Informatika");  // Ganti dengan jurusan sesuai

        // Simpan ke Firestore di koleksi "users" dengan ID document berdasarkan UID
        db.collection("users").document(uid).set(userMap)
                .addOnSuccessListener(aVoid -> Log.d("TAG", "User data saved"))
                .addOnFailureListener(e -> Log.w("TAG", "Error saving user data", e));
    }

    // Method untuk menyimpan data absensi
    public void saveAbsensi(String nim, String status, String jamAbsen) {
        // Gunakan NIM dan tanggal sebagai document ID untuk koleksi absensi
        String tanggal = new SimpleDateFormat("yyyy-MM-dd").format(new Date());  // Ambil tanggal sekarang
        Map<String, Object> absensiMap = new HashMap<>();
        absensiMap.put("status", status);  // Status absensi (misalnya "Hadir" atau "Tidak Hadir")
        absensiMap.put("jamAbsen", jamAbsen);  // Jam saat absen (misalnya "07:58")

        // Simpan data absensi ke Firestore
        db.collection("absensi").document(nim).collection(tanggal)  // Koleksi absensi per NIM dan tanggal
                .add(absensiMap)  // Simpan data absensi
                .addOnSuccessListener(aVoid -> Log.d("TAG", "Absensi saved"))
                .addOnFailureListener(e -> Log.w("TAG", "Error saving absensi", e));
    }

    // Method untuk mengambil data absensi dari Firestore
    public void loadAbsensiData(String nim) {
        String tanggal = new SimpleDateFormat("yyyy-MM-dd").format(new Date());  // Ambil tanggal sekarang

        db.collection("absensi").document(nim).collection(tanggal)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            String status = document.getString("status");
                            String jamAbsen = document.getString("jamAbsen");

                            // Tambahkan data absensi ke dalam containerKehadiran
                            LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                            View cardView = inflater.inflate(R.layout.card_kehadiran, containerKehadiran, false);

                            // Misalnya data QR-nya adalah status dan jam absen
                            ((TextView) cardView.findViewById(R.id.textMatkul)).setText("Matkul Example");  // Bisa diganti sesuai data
                            ((TextView) cardView.findViewById(R.id.textTanggal)).setText(tanggal);
                            ((TextView) cardView.findViewById(R.id.textHari)).setText("Hari Example");  // Bisa diganti sesuai data
                            ((TextView) cardView.findViewById(R.id.textJamAbsen)).setText(jamAbsen);
                            ((TextView) cardView.findViewById(R.id.textStatus)).setText(status);

                            containerKehadiran.addView(cardView);
                        }
                    } else {
                        Log.w("TAG", "Error getting absensi data.", task.getException());
                    }
                });
    }

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    String hasilQR = result.getContents();
                    Toast.makeText(MainActivity.this, "QR Terbaca: " + hasilQR, Toast.LENGTH_SHORT).show();

                    // ⬇️ Tambah card dari layout card_kehadiran.xml
                    LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                    View cardView = inflater.inflate(R.layout.card_kehadiran, containerKehadiran, false);

                    // Contoh QR format: "Bisnis Elektronik|21|Kamis|07:58|09:31|Hadir"
                    String[] data = hasilQR.split("\\|");
                    if (data.length >= 6) {
                        ((TextView) cardView.findViewById(R.id.textMatkul)).setText(data[0]);
                        ((TextView) cardView.findViewById(R.id.textTanggal)).setText(data[1]);
                        ((TextView) cardView.findViewById(R.id.textHari)).setText(data[2]);
                        ((TextView) cardView.findViewById(R.id.textJamAbsen)).setText(data[3]);
                        ((TextView) cardView.findViewById(R.id.textJamJadwal)).setText(data[4]);
                        ((TextView) cardView.findViewById(R.id.textStatus)).setText(data[5]);

                        // Simpan data absensi ke Firestore
                        String nimQR = data[0];  // Misalnya, kita anggap NIM ada di index 0 (ubah sesuai format QR)
                        String status = data[5];  // Status hadir/tidak hadir
                        String jamAbsen = data[3];  // Jam absen

                        // Panggil method saveAbsensi untuk menyimpan data ke Firestore
                        saveAbsensi(nimQR, status, jamAbsen);
                    }

                    containerKehadiran.addView(cardView);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind container
        containerKehadiran = findViewById(R.id.containerKehadiran);

        // Navigasi ke riwayat
        TextView selengkapnya = findViewById(R.id.selengkapnya);
        selengkapnya.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RiwayatKehadiran.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // Scan QR
        LinearLayout btnScan = findViewById(R.id.absenqr);
        btnScan.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Arahkan ke QR Absen");
            options.setBeepEnabled(true);
            options.setOrientationLocked(true);
            options.setCaptureActivity(CaptureActivity.class);
            barcodeLauncher.launch(options);
        });

        // Menyimpan data user ke Firestore setelah login
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            saveUserData(user);  // Simpan data user setelah login
            loadAbsensiData(user.getDisplayName());  // Ambil data absensi berdasarkan NIM
        }
    }
}
