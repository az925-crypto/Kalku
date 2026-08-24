# Kalku

**Calculator outside. Vault inside. Everything local.**

Kalku adalah aplikasi Android penyimpanan file universal (vault) yang berkedok
kalkulator ilmiah yang berfungsi penuh. Dibangun dengan Kotlin + Jetpack Compose,
100% offline, tanpa akun, tanpa server.

```
fitur lengkap: lihat fitur.txt
package: com.zaaaam.kalku | minSdk 26 | targetSdk 35
```

## Cara pakai

1. Buka aplikasi — tampilan awal adalah kalkulator normal (basic + scientific,
   DEG/RAD, riwayat, copy/paste).
2. Ketik PIN default **`1234`** lalu tekan `=` → vault terbuka.
3. Saat entri pertama, aplikasi memaksa membuat PIN pribadi (4–16 digit).
   PIN disimpan sebagai hash PBKDF2-SHA256 bersalt — tidak pernah plain text.
4. Semua input lain tetap diproses sebagai kalkulasi biasa; percobaan PIN yang
   salah dievaluasi senyap dan **tidak** masuk riwayat.

## Vault

- Lokasi: `/storage/emulated/0/.KalkuVault` (tersembunyi) — file **bertahan
  setelah uninstall/reinstall**. Butuh izin *All files access* (dialog pengaturan
  muncul di Settings saat belum diberikan). Tanpa izin, aplikasi jalan di
  storage privat fallback dengan peringatan.
- Struktur default: `Photos/ Videos/ Audio/ Documents/ Code/ Archives/ Others/`
  plus `.Trash` (recycle bin) dan `.meta`.
- File manager: import (file picker & share sheet dari app lain), export/share,
  folder baru, rename, copy, move, multi-select, ZIP, delete → recycle bin
  (restore / hapus permanen / auto-clean), grid & list view, sort.
- Deteksi tipe file via *magic bytes* + extension — file tanpa/salah extension
  tetap terkategori benar.
- Enkripsi at-rest: semua file terenkripsi AES-256-GCM per-chunk. Dekripsi
  dilakukan saat ditampilkan/saved — file tetap terenkripsi di disk.
  Key derivation: PBKDF2-SHA256 dari PIN user.
- Metadata (nama/path/tag/favorit/ukuran) ada di database Room; isi file tetap
  di disk terenkripsi. **Settings → Rebuild index** membangun ulang index
  bila database hilang/rusak.
- Viewer: galeri foto (zoom/pager), pemutar video & audio (playlist per kategori,
  kecepatan, loop), pembaca PDF (zoom, render per halaman), editor teks/kode
  (undo/redo, find & replace, line numbers, word count), penampil ZIP (list +
  extract).

## Build

Build dijalankan GitHub Actions (`.github/workflows/build.yml`):

- Setiap push/PR: unit test (`testReleaseUnitTest`) + `assembleRelease`.
- APK ter-sign otomatis dari secret repo (`KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), tersedia sebagai artifact.
- Push tag `v*` (mis. `v1.0.0`) → APK dilampirkan ke GitHub Release.

Secara manual:

```bash
gradle assembleRelease          # APK release
gradle testReleaseUnitTest      # unit test core logic
```

## Keamanan & privasi

- Tidak ada izin internet; tidak ada analitik; `allowBackup=false`.
- File vault terenkripsi at-rest (AES-256-GCM per-chunk). Key derivation
  pakai PBKDF2-SHA256 dari PIN. DEK di-wrap ulang saat PIN diubah.
- Auto-lock vault saat di background (dapat diatur: off/1/5/15 menit) + lock manual;
  semua layar vault ter-guard — sesi terkunci selalu kembali ke kalkulator.
- Backoff progresif untuk percobaan PIN salah.
- Catatan desain: vault disimpan di storage publik agar file bertahan lintas
  uninstall. Enkripsi dilakukan di atas file tersebut.

## Roadmap v1.2+ (backlog hasil review)

- Keystore-bound mode (opsional): wrap DEK dengan Android KeyStore agar
  rahasia PIN tidak bisa di-pull dari backup file
- Syntax highlighting editor kode, virtualisasi line-number untuk file raksasa
- Background audio playback (foreground service), subtitle video, PiP
- Archive: extract selektif per-entry ke folder tujuan
- Ekstraksi string resource (i18n), pemisahan LibraryViewModel,
  zip/extract turun ke fs-layer, satukan dialog PIN
- Rate-limit PIN persisten lintas proses; biometric unlock sebagai metode tambahan
- Zeroize key bytes dari memory setelah use (platform-dependent)

## Lisensi

Hak milik pembuatnya — gunakan sesuai kebutuhan pribadi.
