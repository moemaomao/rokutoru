# Rokutoru

**AI-powered Manga Translation Tool** (Kotlin + Compose Desktop)

Rokutoru adalah tool desktop untuk menerjemahkan manga dengan bantuan AI, termasuk dukungan **Vision Model** supaya terjemahan bisa menyesuaikan konteks gambar.

Terinspirasi dari [BallonsTranslator](https://github.com/dmMaze/BallonsTranslator), tapi ditulis ulang dengan Kotlin modern.

---

## Fitur

- [x] UI dark theme (Compose Desktop)
- [x] Load folder manga (jpg/png/webp)
- [x] Page navigation & sidebar
- [x] Zoom halaman dan navigasi
- [x] Pilih area teks manual dan edit teks/terjemahan
- [x] Vision pipeline OpenAI dan Gemini untuk deteksi teks dan terjemahan per halaman
- [x] Ekspor halaman ke PNG dengan teks terjemahan
- [ ] Text Detection (CTD / YOLO)
- [ ] OCR (manga-ocr / Paddle)
- [ ] Inpainting
- [ ] Typesetting lanjutan (font dan penyesuaian posisi)

---

## Requirements

- **JDK 17+**
- **Gradle** (atau pakai Gradle Wrapper)
- Windows / macOS / Linux

---

## Cara Menjalankan

### 1. Clone / Extract project
```bash
cd Rokutoru
```

### 2. Jalankan
```bash
./gradlew run
```

atau di Windows:
```cmd
gradlew.bat run
```

### 3. Build installer (opsional)
```bash
# Windows
./gradlew packageMsi

# macOS
./gradlew packageDmg

# Linux
./gradlew packageDeb
```

Hasil installer ada di:
`build/compose/binaries/main/`

---

## Struktur Project

```
Rokutoru/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/kotlin/com/rokutoru/
│   ├── Main.kt
│   ├── model/
│   │   └── AppState.kt
│   ├── ui/
│   │   └── RokutoruApp.kt
│   └── pipeline/
│       ├── TranslatorPipeline.kt
│       └── PageExporter.kt
└── README.md
```

### Vision, edit teks, dan ekspor

1. Buka **Settings** dan masukkan API key OpenAI dan/atau Gemini. Key hanya disimpan selama aplikasi berjalan; environment variable `OPENAI_API_KEY` dan `GEMINI_API_KEY` (atau `GOOGLE_API_KEY`) juga didukung.
2. Pilih provider **OpenAI** atau **Gemini** di panel kanan, atur bahasa/model (default Gemini: `gemini-3.8-flash`), lalu tekan tombol **Run** untuk mendeteksi teks dan menerjemahkan semua halaman.
3. Untuk koreksi manual, klik **Select text area**, lalu seret kotak pada halaman. Isi teks asli dan terjemahan di panel **Text blocks**.
4. Tekan tombol **Export** dan pilih folder tujuan. Hasil PNG disimpan di subfolder `Rokutoru-Export`.

Run mengirim gambar halaman ke provider terpilih (OpenAI Vision atau Gemini Vision) dan memerlukan koneksi internet serta API key yang valid. Ekspor menutupi area teks dengan kotak putih dan menggambar terjemahan; fitur ini belum melakukan inpainting untuk memulihkan artwork di balik teks asli.

---

---

## Catatan

Deteksi otomatis dan terjemahan mendukung OpenAI Vision dan Gemini Vision. Pemilihan area teks manual dan pengeditan tetap tersedia tanpa API key; inpainting dan restorasi artwork belum tersedia.

---

**Rokutoru** — Translate manga the smart way.