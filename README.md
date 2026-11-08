# RobotHub

Aplikasi Android (Kotlin + Jetpack Compose) yang berjalan di HP dan berfungsi sebagai **otak robot**: menerima perintah dari driver (PC/aplikasi lain) lewat TCP, meneruskan perintah ke mikrokontroler STM32 melalui Bluetooth (HC-05 / BLE serial), dan mendeteksi marker ArUco dari kamera HP untuk menentukan tujuan kotak (BOX_A–BOX_D).

## Fitur

- **Kontrol TCP** (port `5000`): driver mengirim karakter perintah satu baris per perintah.
- **Streaming video** (port `5001`): frame JPEG dari kamera dikirim ke driver dengan prefix panjang 4 byte (big-endian).
- **Deteksi ArUco** dengan OpenCV: mendukung kamus 4x4, 5x5, dan 6x6 (utama: `5X5_50`). ID harus stabil selama 3 frame berturut-turut sebelum dianggap valid.
- **Mode MANUAL / AUTO**: perintah gerak manual diabaikan saat mode AUTO (line following di STM32).
- **Watchdog keamanan**: jika driver tidak mengirim apa pun selama 500 ms di mode MANUAL, robot dihentikan (`S`) dan driver diberi tahu `T:LINK_LOST`.
- **Bluetooth STM link**: mencari dan menyambung ke perangkat `HC-05` / `HC-05 LE` secara otomatis, dengan reconnect setiap 3 detik.

## Persyaratan

- Android 7.0 (API 24) atau lebih baru; target SDK 37
- Android Studio versi terbaru dengan dukungan Kotlin Compose
- Perangkat dengan kamera belakang dan Bluetooth BLE
- Robot dengan modul HC-05 / HM-10 / JDY-31 yang terhubung ke STM32
- Dependensi (lihat `build.gradle.kts`): CameraX 1.6.2, OpenCV 5.0.0.1, Jetpack Compose

## Instalasi

```bash
git clone https://github.com/Nazil-h/RobotHub.git
```

1. Buka folder proyek di Android Studio.
2. Tunggu Gradle sync selesai.
3. Hubungkan HP, lalu jalankan **Run** (atau `./gradlew installDebug`).
4. Saat pertama dibuka, izinkan akses **Kamera**, **Bluetooth**, dan **Notifikasi**.

Pastikan modul HC-05 sudah dipasangkan (paired) di pengaturan Bluetooth HP, atau biarkan aplikasi memindai sendiri.

## Penggunaan

1. Nyalakan robot dan pastikan HP berada di jaringan yang sama dengan driver. Disarankan memakai **hotspot Android** (IP biasanya `192.168.43.x`).
2. Jalankan aplikasi. Layar utama menampilkan IP, status listening, status driver, mode, perintah terakhir, dan ID ArUco terakhir.
3. Hubungkan driver ke `IP_HP:5000` untuk kontrol dan `IP_HP:5001` untuk video.

### Protokol perintah (driver → HP)

Setiap perintah dikirim sebagai satu karakter diikuti newline.

| Karakter | Arti |
|---|---|
| `A` | Mode AUTO (line following) |
| `M` | Mode MANUAL |
| `F` / `B` | Maju / mundur |
| `L` / `R` | Kiri / kanan |
| `S` | Stop |
| `U` / `D` | Lengan naik / turun |
| `E` / `Q` | Siku naik / turun |
| `O` / `G` | Grip buka / tutup |
| `H` | Heartbeat |

Perintah gerak dan lengan (`F B L R U D E Q O G`) hanya diteruskan ke STM32 saat mode MANUAL. Perintah lain dicatat sebagai `lastCommand` di status.

### Balasan (HP → driver)

| Pesan | Arti |
|---|---|
| `T:MANUAL` / `T:AUTO` | Mode saat ini |
| `T:OK` | Keepalive, dikirim setiap 300 ms selama driver terhubung |
| `T:LINK_LOST` | Driver diam lebih dari 500 ms di mode MANUAL |
| `I:<id>` | ID ArUco stabil yang terdeteksi |
| `C` | STM32 melaporkan posisi tengah tercapai (otomatis masuk MANUAL) |

Satu sesi driver baru akan menggantikan sesi lama. Setiap sesi dimulai dengan `S` dan mode MANUAL.

### Pemetaan ArUco ke kotak

| ID | Tujuan |
|---|---|
| 0 | BOX_A |
| 1 | BOX_B |
| 2 | BOX_C |
| 3 | BOX_D |

## Struktur Proyek

```
app/src/main/java/com/robothub/
├── MainActivity.kt          # UI Compose & inisialisasi kamera
├── HubViewModel.kt          # Menghubungkan server, video, dan Bluetooth
├── ControlServer.kt         # Server TCP kontrol (port 5000) + watchdog
├── VideoServer.kt           # Server TCP streaming JPEG (port 5001)
├── CommandRouter.kt         # Aturan perintah berdasarkan mode
├── HubState.kt              # Status mode MANUAL / AUTO
├── Protocol.kt              # Konstanta port, timeout, dan karakter perintah
├── StmLink.kt               # Interface pengiriman ke STM32
├── BluetoothStmLink.kt      # Implementasi BLE serial (HC-05)
├── ArucoAnalyzer.kt         # Deteksi ArUco per frame CameraX
├── ArucoConfig.kt           # Konfigurasi kamus dan pemetaan kotak
├── IdStabilizer.kt          # Filter ID stabil (3 frame)
├── NetUtil.kt               # Mengambil IP lokal perangkat
└── ui/theme/                # Tema Compose
```

## Pengujian

Unit test ada di `app/src/test/`:

```bash
./gradlew test
```

Mencakup `CommandRouterTest`, `HubStateTest`, `IdStabilizerTest`, dan `ExampleUnitTest`.

## Catatan

- Saat ini `LogStmLink` hanya mencatat perintah ke log (stub). Untuk robot sungguhan, gunakan `BluetoothStmLink` seperti yang sudah dipasang di `HubViewModel`.
- `VideoServer.kt` merujuk ke `PROTOKOL.md` untuk format frame. File tersebut belum disertakan di repositori ini.

## Lisensi

Tentukan lisensi proyek di sini @RipaldoJR7.

## Kontak

Haqqi / Nazil-h – [github.com/Nazil-h](https://github.com/Nazil-h)
