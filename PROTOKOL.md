# Protokol Komunikasi Robot RIVAL ITS 2026

Versi: v1.2, 2026-11-05
Pemilik: programming (HP kontrol dan HP kamera), dikonfirmasi ke elektrikal (STM32)

## 1. Perangkat dan jalur

- **HP Kontrol (driver):** Android, di luar robot. Mengirim perintah, menampilkan video, dan membaca input analog untuk lengan dan gripper.
- **HP Kamera (hub):** Android, dipasang di robot. Menjadi hotspot, menjalankan deteksi ArUco, dan menjembatani ke HC-05.
- **STM32F411 Black Pill + HC-05:** menggerakkan motor dan servo, membaca sensor garis, dan menjalankan line following saat mode AUTO.

Jalur: 
## 2. Port dan format

| Keperluan | Format | Port |
|---|---|---|
| Kontrol dan status | TCP, teks, satu pesan per baris diakhiri `\n` | 5000 |
| Video | TCP, biner | 5001 |

Format video: setiap frame terdiri dari 4 byte panjang (uint32, big-endian) diikuti data JPEG. Panjang maksimum 500.000 byte. Resolusi 320x240, grayscale, sekitar 10 fps.

## 3. Pesan dari HP Kontrol ke HP Kamera

| Pesan | Arti | Catatan |
|---|---|---|
| F | Maju | Hanya diteruskan saat mode MANUAL |
| B | Mundur | Hanya diteruskan saat mode MANUAL |
| L | Putar kiri | Hanya diteruskan saat mode MANUAL |
| R | Putar kanan | Hanya diteruskan saat mode MANUAL |
| S | Berhenti roda | Selalu diteruskan, prioritas tertinggi |
| H | Heartbeat | Dikirim tiap 150 ms selama terhubung |
| A | Masuk mode AUTO | Diteruskan ke STM32 |
| M | Masuk mode MANUAL | Selalu diteruskan |
| U | Lengan naik | Satu langkah per karakter. Hanya saat MANUAL |
| D | Lengan turun | Satu langkah per karakter. Hanya saat MANUAL |
| O | Gripper buka | Satu langkah per karakter. Hanya saat MANUAL |
| G | Gripper tutup (jepit) | Satu langkah per karakter. Hanya saat MANUAL |

Pesan lain diabaikan.

## 4. Pesan dari HP Kamera ke HP Kontrol

| Pesan | Arti |
|---|---|
| T:OK | Keepalive, dikirim tiap 300 ms |
| T:MANUAL | Mode saat ini MANUAL. Dikirim saat sesi dimulai dan setiap mode berubah |
| T:AUTO | Mode saat ini AUTO. Dikirim setiap mode berubah |
| T:LINK_LOST | Driver tidak mengirim apa pun selama 500 ms saat MANUAL. Robot dihentikan |
| I:id | ID ArUco yang sudah stabil (terbaca di 3 frame berturut-turut) |
| C | Robot sudah sampai di titik tengah (diteruskan dari STM32) |

## 5. Pesan dari HP Kamera ke STM32 (UART via HC-05)

Karakter yang sama dengan bagian 3, masing-masing diikuti `\n`. STM32 mengabaikan `\n`.

Selama HP Kontrol terhubung, HP Kamera meneruskan `H` ke STM32 tiap 150 ms.

Yang diteruskan ke STM32: `F`, `B`, `L`, `R`, `S`, `H`, `A`, `M`, `U`, `D`, `O`, `G`.
- Mode MANUAL: menerima gerak roda, lengan, dan gripper.
- Mode AUTO: hanya menerima `S`, `H`, `A`, dan `M`.

## 6. Pesan dari STM32 ke HP Kamera

| Pesan | Arti |
|---|---|
| C\n | Robot sudah di titik tengah. Setelah ini STM32 berada di mode MANUAL. |

## 7. Mode

- Setelah STM32 menyala, mode default adalah MANUAL.
- `A`: STM32 menjalankan line following dari titik start ke titik tengah. Perintah gerak, lengan, dan gripper diabaikan. `A` hanya diterima saat robot di titik start.
- `C` dari STM32: mode berpindah ke MANUAL secara otomatis. HP Kamera mengirim `T:MANUAL`.
- `M`: pindah ke MANUAL kapan saja. Dipakai jika driver perlu mengambil alih.
- Setiap sesi driver dimulai dengan MANUAL. HP Kamera memaksa mode ke MANUAL saat sesi dimulai.

## 8. Kontrol lengan dan gripper (analog)

Lengan dan gripper dikendalikan dengan joystick analog di HP Kontrol, bukan tombol. Pemetaan arah:

| Arah analog | Karakter | Aksi |
|---|---|---|
| Atas | `U` | Lengan naik |
| Bawah | `D` | Lengan turun |
| Kiri | `O` | Gripper buka |
| Kanan | `G` | Gripper jepit (tutup) |

### Aturan pengiriman di HP Kontrol

1. **Deadzone:** nilai analog dengan besar di bawah 0.3 dianggap nol, dan tidak mengirim apa pun.
2. **Satu sumbu per tick:** kalau analog condong ke dua arah, kirim hanya sumbu yang nilainya lebih besar. Hanya satu karakter per tick, supaya STM32 tidak menerima dua perintah servo bersamaan.
3. **Pengulangan:** selama analog melewati deadzone, karakter yang sesuai dikirim berulang setiap **100 ms**. Begitu analog kembali ke tengah, pengiriman berhenti.
4. **Saat dilepas:** tidak mengirim `S`. Servo berhenti karena tidak ada perintah baru, dan tetap pada posisi terakhir.
5. **Mode AUTO atau koneksi putus:** tidak ada perintah yang dikirim. Kontrol analog dinonaktifkan di UI.

### Aturan di HP Kamera (hub)

- Meneruskan `U`, `D`, `O`, `G` ke STM32 hanya saat MANUAL, tanpa perubahan.
- Tidak mengubah frekuensi pengiriman. Setiap karakter langsung diteruskan.

### Aturan di STM32

- Satu karakter `U`, `D`, `O`, atau `G` menggerakkan servo sebesar satu **langkah kecil** yang sudah dikalibrasi. Langkah ini harus cukup kecil, karena karakter bisa datang setiap 100 ms selama analog ditahan.
- Servo tidak boleh bergerak tanpa perintah baru. Setelah langkah terakhir, posisi dipertahankan.

## 9. Servo

| Servo | Fungsi | Pin | Timer |
|---|---|---|---|
| Servo 1 | Lengan (naik/turun) | PA8 | TIM1_CH1 |
| Servo 2 | Gripper (buka/tutup) | PB10 | TIM2_CH3 |

Tabel kalibrasi (diisi elektrikal):

| Parameter | Nilai | Keterangan |
|---|---|---|
| Lengan, sudut atas | TBD | |
| Lengan, sudut bawah | TBD | |
| Lengan, langkah per karakter | TBD | Harus cukup kecil untuk pengulangan 100 ms |
| Gripper, sudut buka | TBD | |
| Gripper, sudut tutup (jepit) | TBD | |
| Gripper, langkah per karakter | TBD | Harus cukup kecil untuk pengulangan 100 ms |

Servo tidak boleh diuji dengan beban sebelum batas sudut dikalibrasi.

## 10. Keselamatan dan watchdog

| Pihak | Kondisi | Tindakan |
|---|---|---|
| HP Kontrol | Tidak ada pesan dari HP Kamera selama 1500 ms | Status putus, tombol dan analog nonaktif |
| HP Kontrol | Tombol gerak dilepas, atau koneksi diputus | Kirim `S` (untuk roda) |
| HP Kamera | Tidak ada `H` dari HP Kontrol selama 500 ms saat MANUAL | Kirim `S` ke STM32, kirim `T:LINK_LOST` |
| STM32 | Tidak ada karakter selama 400 ms saat MANUAL | `motor_stop()`. Servo tetap pada posisi terakhir |
| STM32 | Mode AUTO | Watchdog tidak berlaku. Robot tetap line following. |

Aturan setelah reconnect: setiap sesi dimulai dengan `S`. Perintah yang masih antre dari sesi sebelumnya dibuang.

Karena lengan dan gripper dikirim berulang, koneksi yang putus langsung menghentikan gerakan lengan dan gripper, karena tidak ada lagi karakter yang datang.

## 11. Contoh alur

1. HP Kontrol terhubung ke HP Kamera. HP Kamera mengirim `S` ke STM32, lalu `T:MANUAL`.
2. Driver menekan tombol `F`. HP Kontrol mengirim `F`, HP Kamera meneruskan ke STM32. Saat dilepas, HP Kontrol mengirim `S`.
3. Driver mendorong analog ke atas. HP Kontrol mengirim `U` tiap 100 ms, lengan naik bertahap. Saat analog dilepas, pengiriman berhenti.
4. Driver mendorong analog ke kanan untuk menjepit. HP Kontrol mengirim `G` tiap 100 ms sampai gripper tertutup.
5. Driver menekan `A`. STM32 line following ke tengah, lalu mengirim `C`. HP Kamera meneruskan `C` dan mengirim `T:MANUAL`.
6. HP Kamera mendeteksi ArUco ID 2 yang stabil, lalu mengirim `I:2` ke HP Kontrol.

## 12. Perubahan yang harus dikerjakan

**Firmware STM32 (elektrikal):**
- Parsing `A`, `M`, `U`, `D`, `O`, `G`.
- Langkah servo per karakter harus kecil, dan dikalibrasi sebelum uji beban.
- Kirim `C\n` saat titik tengah tercapai.
- Watchdog 400 ms hanya di mode MANUAL.

**HP Kamera (hub):**
- `CommandRouter` meneruskan `U`, `D`, `O`, `G` hanya saat MANUAL.
- Kirim `T:MANUAL` saat sesi dimulai, dan `T:AUTO` atau `T:MANUAL` saat mode berubah.

**HP Kontrol (driver):**
- Konversi analog ke karakter dengan deadzone 0.3 dan satu sumbu per tick.
- Kirim karakter berulang setiap 100 ms selama analog ditahan, dan berhenti saat dilepas.
- Nonaktifkan analog saat mode AUTO atau saat tidak terhubung.

## 13. Riwayat versi

- **v1.2 (2026-11-05):** kontrol lengan dan gripper diubah dari tombol ke analog. Pengiriman berulang tiap 100 ms selama analog ditahan, dengan deadzone dan aturan satu sumbu per tick.
- **v1.1 (2026-11-05):** tambah perintah lengan dan gripper (U, D, O, G), tabel servo, dan tabel kalibrasi.
- **v1 (2026-11-04):** protokol awal.
