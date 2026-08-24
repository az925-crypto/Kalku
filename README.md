<div align="center">

# Kalku

### Calculator outside. Vault inside. Everything local.

Sebuah aplikasi Android penyimpanan file terenkripsi yang menyamar sebagai
kalkulator ilmiah. Semua data tersimpan lokal — tanpa akun, tanpa server,
tanpa kompromi privasi.

</div>

---

## Apa itu Kalku?

Kalku adalah vault file tersembunyi yang tampil sebagai kalkulator biasa.
Dari luar, ini hanyalah kalkulator scientific dengan fitur lengkap.
Di baliknya, tersimpan ruang penyimpanan terenkripsi AES-256-GCM untuk
semua jenis file — foto, video, audio, dokumen, kode, dan lainnya.

**Prinsip desain:**

```
Calculator outside.  → Tampilan kalkulator yang meyakinkan
Vault inside.        → Penyimpanan terenkripsi tersembunyi
Everything local.    → File tetap di perangkat, tidak ada cloud
```

---

## Fitur Utama

### Kalkulator Kamuflase

Kalkulator berfungsi penuh — bukan sekadar tampilan palsu.

- **Basic:** operasi dasar, persentase, desimal, kurung, riwayat
- **Scientific:** sin/cos/tan, log/ln, akar, pangkat, faktorial, mod
- **Konstanta:** pi, e
- **Mode:** DEG / RAD
- **UI:** copy hasil, paste ekspresi, riwayat perhitungan

### Hidden Vault

Ketik PIN lalu tekan `=` → vault terbuka. Input lain tetap diproses
sebagai kalkulasi biasa. Tidak ada jejak percobaan PIN yang salah.

### Enkripsi At-Rest (AES-256-GCM)

Semua file terenkripsi di disk menggunakan AES-256-GCM per-chunk (16 MiB).
Key derivation dari PIN user via PBKDF2-SHA256. File hanya didekripsi
saat ditampilkan atau disimpan — tidak pernah di-cache dalam plaintext
secara permanen.

### File Manager

- Import dari file picker atau share sheet app lain
- Export / share
- Folder baru, rename, copy, move
- Multi-select, sort, search
- Grid & list view
- Delete → Recycle Bin (restore / hapus permanen)

### Viewer & Editor

| Tipe | Fitur |
|------|-------|
| **Foto** | Gallery grid, fullscreen, zoom, swipe, favorite, tag |
| **Video** | Play/pause, seek, speed, fullscreen |
| **Audio** | Playlist per kategori, loop, shuffle, speed, background |
| **PDF** | Render per halaman, zoom, navigasi |
| **Teks/Kode** | Undo/redo, find & replace, line numbers, word count |
| **ZIP** | Lihat isi, extract |

### Smart Detection

Deteksi tipe file via *magic bytes* + extension — file tanpa atau
salah extension tetap terkategori benar.

### Recycle Bin

File yang dihapus masuk ke recycle bin dulu. Bisa dipulihkan atau
dihapus permanen. Auto-clean tersedia.

### Dashboard

Halaman utama vault menampilkan:
- Storage usage per kategori
- Recent files
- Favorites
- Quick actions

---

## Arsitektur

```
com.zaaaam.kalku/
├── calc/           # Kalkulator (CalcViewModel, CalculatorScreen)
├── core/
│   └── crypto/     # AES-256-GCM chunked encryption
├── data/           # Room database, entities, DAOs
├── fs/             # Vault filesystem, cache, migration, trash ops
├── nav/            # Navigation graph
├── security/       # PIN, lock controller, crypto session
├── settings/       # Settings screen
├── ui/             # Theme, shared components
├── vault/          # Dashboard, browse, library screens
└── viewer/         # Photo, video, audio, PDF, text, ZIP viewers
```

**Tech stack:**

| Komponen | Teknologi |
|----------|-----------|
| UI | Jetpack Compose + Material 3 |
| Database | Room (metadata/index) |
| Navigation | Navigation Compose |
| Media | ExoPlayer (Media3) |
| Image | Coil |
| Crypto | AES-256-GCM + PBKDF2-SHA256 |
| Language | Kotlin |
| Build | Gradle KTS + GitHub Actions |

---

## Keamanan

| Layer | Mekanisme |
|-------|-----------|
| **Vault access** | PIN (4–16 digit), hash PBKDF2-SHA256 bersalt |
| **File encryption** | AES-256-GCM per-chunk, DEK wrapped per-PIN |
| **Auto-lock** | Configurable (off / 1 / 5 / 15 menit) + lock manual |
| **Backoff** | Progresif untuk percobaan PIN salah |
| **Isolation** | Tidak ada izin internet, tidak ada analitik, `allowBackup=false` |
| **Persistence** | Vault di storage publik agar file bertahan lintas uninstall |

> **Catatan desain:** Vault disimpan di `/storage/emulated/0/.KalkuVault`
> agar file tetap ada setelah uninstall/reinstall. Enkripsi dilakukan
> di atas file tersebut — isi tidak terbaca tanpa PIN yang benar.

---

## Cara Pakai

1. Install APK
2. Buka aplikasi → tampil kalkulator
3. Ketik PIN default **`1234`** lalu tekan `=`
4. Vault terbuka — buat PIN pribadi saat pertama kali
5. Import file dari picker atau share sheet
6. Lihat, edit, putar file langsung dari dalam vault

> Semua input selain PIN diproses sebagai kalkulasi biasa.
> Tidak ada indikasi bahwa vault ada.

---

## Build & CI

### GitHub Actions (otomatis)

Setiap push/PR ke `main`:
1. Unit test (`testReleaseUnitTest`)
2. Build release APK (`assembleRelease`)
3. APK ter-sign otomatis dari secret repo

Push tag `v*` → APK dilampirkan ke GitHub Release.

### Build manual

```bash
gradle assembleRelease       # APK release
gradle testReleaseUnitTest   # unit test
```

---

## Spesifikasi

| | |
|---|---|
| **Package** | `com.zaaaam.kalku` |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 (Android 15) |
| **Version** | 1.1.0 |
| **Language** | Kotlin 100% |
| **UI** | Jetpack Compose |

---

## Roadmap

### v1.2+

- Keystore-bound mode (opsional) — wrap DEK dengan Android KeyStore
- Syntax highlighting editor kode
- Background audio playback (foreground service)
- Archive: extract selektif per-entry
- Biometric unlock sebagai metode tambahan
- i18n / string resource extraction

### Backlog

- PDF annotation & highlight
- Markdown preview
- Image editor
- File versioning
- Duplicate file detector
- Custom themes

---

## Lisensi

Hak milik pembuatnya — gunakan sesuai kebutuhan pribadi.
