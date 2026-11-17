# Mizanku - Aplikasi Android (kasir + printer thermal)

Aplikasi ini membungkus Mizanku menjadi aplikasi Android asli. Printer thermal
(mis. Rongta RPP02N) dihubungkan lewat Bluetooth klasik (SPP), jadi tidak lagi
terbatas pada Web Bluetooth browser.

## Cara 1 - Tanpa Android Studio (lewat GitHub Actions, gratis)
1. Buat repositori baru di github.com (boleh private).
2. Unggah SEMUA isi folder ini, termasuk folder tersembunyi `.github`.
   Cara paling aman lewat komputer:
   git init && git add . && git commit -m "Mizanku" && git branch -M main
   git remote add origin <alamat-repo> && git push -u origin main
3. Buka tab Actions > "Build APK" > Run workflow (atau otomatis berjalan saat push).
4. Tunggu 5-10 menit, buka proses yang selesai, unduh artifact "Mizanku-APK".
5. Ekstrak zip, kirim `app-debug.apk` ke HP, lalu pasang
   (izinkan "Pasang dari sumber tidak dikenal" bila diminta).

## Cara 2 - Android Studio
1. Buka folder ini di Android Studio (Hedgehog atau lebih baru), tunggu Gradle sync.
2. Agar bisa dipakai tanpa internet, buat CSS lokal sekali dengan Node.js:
   npx tailwindcss@3.4.17 -i tailwind/input.css -o app/src/main/assets/tailwind.css --content "app/src/main/assets/index.html" --minify
   (Bila dilewati, tampilan tetap jalan tetapi butuh internet saat aplikasi dibuka.)
3. Build > Build Bundle(s)/APK(s) > Build APK(s).

## Memasangkan printer RPP02N
1. Nyalakan printer.
2. Di HP: Pengaturan > Bluetooth > cari "RPP02N" > Pasangkan (PIN 0000 atau 1234).
3. Buka Mizanku > tombol "Bluetooth Printer" di header > "Cari & Hubungkan Printer".
4. Izinkan akses Bluetooth, pilih RPP02N, lalu tekan "Cetak Tes".
Printer dipilih sekali saja dan diingat; koneksi disambung ulang otomatis saat mencetak.

## Catatan
- Data tersimpan di dalam aplikasi, terpisah dari data di browser/artefak.
  Pindahkan data lewat Cadangkan Data (di browser lama) dan Pulihkan Data (di aplikasi).
- Menghapus aplikasi menghapus datanya. Cadangkan secara berkala.
- Tombol "Kirim ke Word" dan "Cadangkan Data" membuka menu bagikan Android.
- APK yang dihasilkan berjenis debug: cukup untuk dipasang dan dipakai sendiri.
