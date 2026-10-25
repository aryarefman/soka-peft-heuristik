# Catatan Revisi Dosen dan Tindak Lanjutnya
### Strategi Optimasi Komputasi Awan (SOKA) · Kelompok 8 · 2026

Dokumen ini memetakan setiap catatan revisi dari dosen pengampu (Bu Henning Titi Ciptaningtyas, S.Kom., M.Kom.) ke kode dan hasil di repository. Semua angka diambil dari CSV di `results/3dc/` dan `results/2dc/`. Lokasi kode dirujuk dengan nama class/method (bukan nomor baris) agar tetap valid saat kode berubah.

---

## Ringkasan

| No | Catatan | Tindak lanjut | Bukti |
|:--:|:---|:---|:---|
| 1 | Implementasi tidak sesuai proposal | Spesifikasi infrastruktur dipusatkan di `Skenario.java` | README §2 |
| 2 | Tipe & jumlah VM/host harus dispesifikasikan | Tabel host & VM per skenario | README §2.1, §2.2 |
| 3 | Buktikan tiap host punya VM, tidak ada host menganggur | Alokasi round-robin + `testAllocation` | `results/*/logs/testAllocation.txt` |
| 4 | Pahami First-Fit vs Best-Fit vs Penyerataan, tulis di komentar | Komentar di `DatacenterBuilder.buildMultiple()` | kode |
| 5 | Tunjukkan `createVms()` | `DatacenterBuilder.createVms()` | kode |
| 6 | Dataset random harus disimpan sebagai bukti | `dataset/synthetic_dataset_*.csv` | 19 berkas CSV |
| 7 | Persentase task pendek/sedang/panjang | `SwfParser.printDatasetCharacteristics()` | log `runAll.txt` |
| 8 | Uji 100 – 1.000 task, 3× run, rata-rata | `BatchExperimentSuite` | `batch_100_1000_results.csv` |
| 9 | Tambah 1.000 – 10.000 task, 3× run | `BatchExperimentSuite` | `synthetic_1000_10000_results.csv` |
| 10 | Dataset riil | Trace asli NASA iPSC/860 + GoCJ resmi | README §4 |
| 11 | Hasil harus konsisten jika di-run berulang | Simpangan baku antar-run = 0 di semua konfigurasi | kolom `StdDevAcrossRuns` |
| 12 | Spesifikasi regional DC (low/mid/high grade) | Jakarta / Surabaya / Bandung | `Skenario.java` |
| 13 | Jelaskan perbedaan perilaku antar-dataset | Analisis berbasis data | README §7.1 |
| 14 | Buat grafik perbandingan | `GenerateEvaluationCharts` | `docs/images/{3dc,2dc}/` |
| + | Sediakan dua kasus infrastruktur (3 DC dan 2 DC) | `-Pskenario=3dc` / `-Pskenario=2dc` | `docs/PANDUAN_SKENARIO_2DC.md` |

---

## 1–2. Spesifikasi infrastruktur sesuai proposal

Seluruh spesifikasi ada di satu tempat, yaitu `src/main/java/com/kelompok8/Skenario.java`, dan dibangun oleh `DatacenterBuilder.buildMultiple()`.

| Skenario | DC | Host | VM |
| :--- | :--- | :--- | :--- |
| `3dc` | DC-1 Jakarta (Entry-Level), DC-2 Surabaya (Standard), DC-3 Bandung (Premium) | 24 (8/DC, 8 PE) | 30: 10 Small (1 PE, 1.000 MIPS, 1 GB), 10 Medium (2 PE, 1.500 MIPS, 2 GB), 10 Large (4 PE, 2.000 MIPS, 4 GB) |
| `2dc` | DC-1 Jakarta (Standard), DC-2 Bandung (Premium) | 10 (5/DC, 16 PE) | 40: 20 Medium (2 PE, 1.000 MIPS, 2 GB), 20 Large (4 PE, 2.000 MIPS, 4 GB) |

> **Jawaban singkat:** "Semua tipe dan jumlah host/VM didefinisikan di `Skenario.java`, dan tabel di README dibuat dari konfigurasi yang sama. Diagram arsitektur juga digambar otomatis dari file itu (`./gradlew renderArsitektur`), jadi dokumen dan kode tidak bisa berbeda."

## 3. Tidak ada host yang menganggur

VM ditempatkan dengan `VmAllocationPolicyRoundRobin`. Output asli `./gradlew testAllocation`:

- `3dc`: `Status: 24 dari 24 host aktif, 0 host menganggur. Pola penempatan: 18 host x 1 VM, 6 host x 2 VM` (per DC: 8 host, 10 VM, sehingga host #1 dan #2 menampung 2 VM dan host lainnya 1 VM)
- `2dc`: `Status: 10 dari 10 host aktif, 0 host menganggur. Pola penempatan: 10 host x 4 VM`

> **Jawaban singkat:** "Dengan alokasi round-robin, VM dibagikan bergiliran ke setiap host. Di skenario 3 DC, 10 VM dibagi ke 8 host, jadi 2 host berisi 2 VM dan 6 host berisi 1 VM. Di skenario 2 DC, 20 VM dibagi ke 5 host, jadi tepat 4 VM per host. Tabel `testAllocation` menunjukkan 0 host menganggur."

## 4. First-Fit vs Best-Fit vs Penyerataan

Komentar perbandingan ada di `DatacenterBuilder.buildMultiple()`, tepat di atas pemilihan `VmAllocationPolicy`:

| Kebijakan | Class CloudSim Plus | Cara kerja | Dampak |
| :--- | :--- | :--- | :--- |
| First-Fit | `VmAllocationPolicySimple` | Host pertama yang PE & RAM-nya cukup | VM menumpuk di host awal |
| Best-Fit | `VmAllocationPolicyBestFit` | Host dengan sisa resource paling sedikit | Konsolidasi, sebagian host kosong |
| Penyerataan | `VmAllocationPolicyRoundRobin` | Bergiliran ke setiap host | Semua host aktif (dipakai di proyek ini) |

## 5. `createVms()`

`DatacenterBuilder.createVms(Skenario.DcSpec spec, boolean verbose)` membuat `vmCount` VM dengan PE, MIPS, dan RAM sesuai spesifikasi DC, bandwidth 250 Mbps, storage 10 GB, dan `CloudletSchedulerSpaceShared` (non-preemptive).

## 6–7. Dataset tersimpan & proporsi task

- Dataset sintetis seed 42 disimpan di `dataset/synthetic_dataset_{100..1000, 2000..10000}.csv` (kolom `TaskId, LengthMI, PesNumber, SubmissionDelay(s), FileSize(B), OutputSize(B)`). Eksperimen utama dan batch membaca berkas yang sama.
- Proporsi sintetis: **30% kecil** (1 PE, 500 – 5.000 MI), **40% sedang** (2 PE, 5.000 – 50.000 MI), **30% besar** (4 PE, 50.000 – 500.000 MI).
- `SwfParser.printDatasetCharacteristics()` menghitung proporsi dan rentang langsung dari data. Untuk 1.000 job NASA: 32,1% 1 PE, 9,0% 2 PE, 58,9% 4 PE, panjang 1.000 – 19.761.000 MI.

## 8–9, 11. Pengujian berskala & konsistensi

- `./gradlew runBatch` menjalankan 100 – 1.000 task (sintetis & NASA), 1.000 – 10.000 task (sintetis), dan GoCJ 100 – 1.000, masing-masing **3 run**.
- Kolom `StdDevAcrossRuns` bernilai **0,0000 di semua baris** untuk kedua skenario, sehingga hasilnya deterministik.

> **Jawaban singkat:** "PEFT, FCFS, dan RR tidak memakai bilangan acak, dan datasetnya dibaca dari berkas yang sama. Karena itu 3 run menghasilkan nilai yang identik; simpangan bakunya 0 di semua konfigurasi."

## 10. Dataset riil

- **NASA Ames iPSC/860**: trace asli versi *cleaned* dari Parallel Workloads Archive (`dataset/NASA-iPSC-1993-3.1-cln.swf.gz`, 18.239 job, SHA-256 `5589ae9b…7f29`).
- **GoCJ**: berkas resmi Hussain & Aleem (2018), `dataset/gocj/GoCJ_Dataset_{100..1000}.txt`. GoCJ hanya memuat panjang job (MI), jadi diasumsikan semua job tiba pada t = 0 dan masing-masing butuh 1 PE.

## 12. Spesifikasi regional

- `3dc`: Jakarta (entry-level, host A/B), Surabaya (mid-grade, host B/C), Bandung (high-grade, host C/D).
- `2dc`: Jakarta (standard, host A/B), Bandung (premium, host C/D).

## 13. Perilaku berbeda antar-dataset (angka skenario `3dc`, 1.000 task)

| Dataset | Pola kedatangan | Makespan PEFT vs FCFS | Yang menonjol |
| :--- | :--- | :--- | :--- |
| Sintetis | Padat, 0 – 300 s | 4.185,59 vs 4.946,72 s (−15,39%) | Beban terbatas komputasi; EFT menentukan |
| NASA iPSC/860 | Renggang, 6,8 hari | 587.101,10 s (sama) | Waiting 0 vs 20,08 s; turnaround −8,88% |
| GoCJ | Semua di t = 0 | 1.091,83 vs 5.707,22 s (−80,87%) | Job besar di VM lambat memblokir antrean FCFS/RR |

> **Jawaban singkat:**
> - "Pada **sintetis**, semua task datang dalam 5 menit sehingga semua VM antre. PEFT memilih VM yang paling cepat menyelesaikan task, sedangkan FCFS/RR membagi rata tanpa melihat kecepatan VM, jadi makespan PEFT lebih pendek."
> - "Pada **NASA**, 1.000 job tersebar selama ±6,8 hari dan VM hampir selalu menganggur (utilisasi < 2%). Makespan ditentukan oleh job terakhir yang tiba di detik ke-587.055, sehingga ketiga algoritma selesai bersamaan. Keunggulan PEFT ada pada waiting time 0, karena setiap job langsung masuk ke VM yang sedang bebas."
> - "Pada **GoCJ**, semua job tiba bersamaan dan panjangnya sangat bervariasi. Di 100 – 400 job, makespan PEFT tertahan di 450,21 s, yaitu durasi job terbesar (900.000 MI ÷ 2.000 MIPS), batas bawah yang tidak bisa dilampaui."

## 14. Grafik

`./gradlew generateCharts -Pskenario={3dc|2dc}` menghasilkan 8 grafik per skenario di `docs/images/{skenario}/`: makespan sintetis (100 – 1.000 dan 1.000 – 10.000), makespan, turnaround, dan waiting time NASA, makespan GoCJ, energi, serta load balance.

Panduan membaca grafik:

1. **Makespan sintetis naik hampir linier.** Total MI bertambah sebanding jumlah task, sedangkan kapasitas VM tetap. Selisih PEFT vs FCFS (`3dc`) 7,5 – 21,5% pada 100 – 1.000 task, lalu 4,3 – 11,6% pada 2.000 – 10.000 task karena semua VM jenuh.
2. **Makespan NASA berimpit.** Penyebabnya batas waktu kedatangan, seperti dijelaskan di atas.
3. **Waiting time NASA FCFS/RR naik-turun (3,67 – 24,31 s), PEFT selalu 0.** Nilai rata-rata FCFS/RR bergantung pada berapa banyak job yang kebetulan tiba saat VM gilirannya masih sibuk, sehingga berubah mengikuti pola kedatangan trace. PEFT memeriksa waktu bebas setiap core, jadi job selalu dikirim ke core yang sudah bebas.
4. **Energi sintetis naik linier.** Makespan lebih panjang berarti host menyala lebih lama. Pada 10.000 task (`3dc`), PEFT 47,61 kWh vs FCFS 50,40 kWh (−5,55%). Daya idle host tetap besar (47 – 69% dari daya maksimum, tergantung tipe), jadi memperpendek makespan langsung mengurangi energi.
5. **Load balance (simpangan baku jumlah task per VM).** FCFS/RR membagi per kelas PE: task 4 PE hanya ke 10 VM Large, task 2 PE ke 20 VM, dan task 1 PE ke 30 VM, sehingga VM Large menerima jauh lebih banyak task (SD 20,55 → 205,48 pada 1.000 → 10.000 task). PEFT mengalihkan task kecil ke VM Small/Medium yang lebih dulu bebas (SD 5,32 → 48,51). Namun pada NASA dan GoCJ, SD PEFT justru lebih tinggi karena PEFT menumpuk job di VM tercepat. Metrik ini menggambarkan pola distribusi task, bukan efisiensi.

---

## Perintah untuk demo

| Perintah | Kegunaan |
|:---|:---|
| `.\gradlew.bat runAll` | Simulasi utama (sintetis & NASA, 1.000 task), skenario 3 DC |
| `.\gradlew.bat runAll -Pskenario=2dc` | Sama, skenario 2 DC |
| `.\gradlew.bat testAllocation -Pskenario=2dc` | Bukti penempatan VM ke host |
| `.\gradlew.bat runBatch` | Batch 100 – 1.000, 1.000 – 10.000, GoCJ (3× run) |
| `.\gradlew.bat generateCharts` | Grafik dari CSV batch |
| `.\gradlew.bat runPEFT` | Hanya algoritma PEFT |
