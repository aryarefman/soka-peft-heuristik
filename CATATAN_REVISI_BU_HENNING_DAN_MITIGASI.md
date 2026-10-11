# PANDUAN LENGKAP & BUKTI MITIGASI SELURUH CATATAN REVISI BU HENNING
## Strategi Optimasi Komputasi Awan (SOKA) — Kelompok 8 (2026)

Dokumen ini disusun sebagai **buku saku dan bukti kepatuhan 100% (zero loophole / tanpa celah)** terhadap setiap catatan, pertanyaan, dan instruksi revisi dari Dosen Pengampu (**Bu Henning Titi Ciptaningtyas, S.Kom., M.Kom.** / Tim Dosen SOKA ITS).

---

## DAFTAR ISI DAN PEMETAAN MATRIKS CATATAN

| No | Topik Catatan Bu Henning | Status Mitigasi | Bukti Codebase | Bukti Dokumen & Hasil |
|:--:|:---|:---:|:---|:---|
| 1 | **Implementasi tidak sesuai dengan proposal. Perbaiki proposal.** | **100% TUNTAS** | `DatacenterBuilder.java`<br>`PeftBroker.java` | Proposal `Tugas_3_Kelompok_8_Final_3DC.docx` telah disinkronkan total |
| 2 | **Penentuan tipe dan jumlah VM dan Host langsung dispesifikasikan di proposal.** | **100% TUNTAS** | `DatacenterBuilder.java:52-85` | Tabel 3 & Tabel 4 di Proposal: 24 Host, 30 VM, 3 DC |
| 3 | **Di dokumen tidak ada penjelasan setiap host punya berapa VM. Buktikan jangan sampai ada host yang nganggur.** | **100% TUNTAS** | `DatacenterBuilder.java:124-145`<br>`testAllocation` | Tabel 5 Proposal & Terminal Output: 6 Host x 1 VM, 2 Host x 2 VM (100% Host Aktif) |
| 4 | **Memahami algoritma First-Fit vs Best-Fit vs Penyerataan (alokasi host ditaruh di komen kode).** | **100% TUNTAS** | `DatacenterBuilder.java:125-138` | Komentar detail perbandingan First-Fit, Best-Fit, dan Penyerataan Round-Robin |
| 5 | **Tunjukkan method `createVms()` dalam codebase.** | **100% TUNTAS** | `DatacenterBuilder.java:220-237` | Method `createVmsForDc()` teranotasi dan siap didemokan |
| 6 | **Dataset random tolong habis disintesis disimpan biar ada bukti (panjang tiap data dsb).** | **100% TUNTAS** | `SwfParser.java:231-332` | 31 file CSV tersimpan di `dataset/synthetic_dataset_*.csv` |
| 7 | **Cari tahu task pendek, sedang, dan panjang berapa persen (30% Kecil, 40% Sedang, 30% Besar).** | **100% TUNTAS** | `SwfParser.java:162-184`<br>`SwfParser.java:335-370` | Method `printDatasetCharacteristics()` mencetak persentase & rentang MI |
| 8 | **Uji coba skala 100 s.d. 1.000 task (kelipatan 100) masing-masing 3x run dan diambil rata-rata.** | **100% TUNTAS** | `BatchExperimentSuite.java:52-87` | `results/batch_100_1000_results.csv` & grafik PNG |
| 9 | **Tambahkan dataset sampai 10.000 task (1.000, 2.000, ..., 10.000) masing-masing 3x run.** | **100% TUNTAS** | `BatchExperimentSuite.java:94-118` | `results/synthetic_1000_10000_results.csv` & grafik PNG |
| 10 | **Dataset Riil: NASA iPSC (SWF) vs GoCJ trace** | **100% TUNTAS** | `SwfParser.java:16-45`<br>`dataset/NASA-iPSC-1993-3.1-cln.swf` | NASA iPSC adalah dataset riil utama Kelompok 8 (100–1.000 task), GoCJ disediakan sebagai pembanding tambahan |
| 11 | **Verifikasi running dataset yang sama (hasil objective keseluruhan harus konsisten / deterministik).** | **100% TUNTAS** | `BatchExperimentSuite.java:188-195` | `StdDev Run: 0.00` membuktikan determinisme PEFT |
| 12 | **Spesifikasi regional Data Center Jakarta, Surabaya, Bandung (low, mid, high grade).** | **100% TUNTAS** | `DatacenterBuilder.java:83-87` | DC-1 Jakarta (Low), DC-2 Surabaya (Mid), DC-3 Bandung (High) |
| 13 | **Apakah perilaku performa berbeda antar dataset? Jelaskan alasannya kenapa bisa dan kenapa tidak.** | **100% TUNTAS** | Analisis Ilmiah di Bab V | Analisis Arrival Time Padat (Sintetis) vs Long-Span Trace (NASA iPSC) |
| 14 | **Buat diagram/grafik perbandingan Makespan, Energi, Waiting Time, dan Load Balance.** | **100% TUNTAS** | `GenerateEvaluationCharts.java` | 6 Berkas PNG Resolusi Tinggi di `docs/images/` |

---

## PEMBAHASAN MENDALAM TIAP CATATAN & JAWABAN SAAT DEMO

### 1. "Implementasi tidak sesuai dengan proposal. Perbaiki proposal." & "Penentuan tipe dan jumlah VM dan Host langsung dispesifikasikan di proposal"
* **Masalah Awal:** Di draf awal proposal, konfigurasi host hanya dicantumkan 8 unit (2 A, 2 B, 2 C, 2 D) dan kapasitas RAM VM berbeda dengan implementasi.
* **Solusi & Mitigasi yang Diterapkan:**
  - Dokumen proposal `Tugas_3_Kelompok_8_Final_3DC.docx` telah disinkronkan 100% dengan kode Java.
  - Tabel 3 (Komponen): 3 Datacenter Heterogen Multi-Region, 24 Host Fisik (8 Host/DC), 30 Virtual Machine (10 VM/DC), dan pengujian 100 s.d. 10.000 task.
  - Tabel 4 (Rincian Host): Spesifikasi 24 Host (4 A + 4 B di DC-1; 4 B + 4 C di DC-2; 4 C + 4 D di DC-3) dengan benchmark konsumsi daya resmi **SPECpower**.
  - Tabel 5 (Konfigurasi VM): 10 VM Small (1 PE, 1.000 MIPS, 1.024 MB RAM), 10 VM Medium (2 PE, 1.500 MIPS, 2.048 MB RAM), 10 VM Large (4 PE, 2.000 MIPS, 4.096 MB RAM).
* **Jawaban saat Ditanya:**
  > *"Proposal telah kami perbaiki dan selaraskan penuh dengan implementasi simulator CloudSim Plus. Kami memodelkan arsitektur 3 Datacenter dengan total 24 host fisik dan 30 VM heterogen. Rincian tipe prosesor, alokasi RAM, konsumsi daya SPECpower, serta kebijakan alokasinya sudah tertuang secara presisi pada Tabel 3, 4, dan 5 proposal."*

---

### 2. "Di dokumen tidak ada penjelasan setiap host punya berapa VM. Buktikan pembagian VM apakah ke tiap host dan jangan sampai ada host yang nganggur!"
* **Masalah Awal:** Pada kebijakan bawaan `VmAllocationPolicyBestFit` CloudSim Plus, VM terkonsolidasi hanya pada Host #1 dan #2, sementara Host #3 s.d. #8 kosong (nganggur).
* **Solusi & Mitigasi yang Diterapkan:**
  - Di `DatacenterBuilder.java`, diimplementasikan kebijakan **Penyerataan (Balanced Round-Robin Allocation)**:
    - Di setiap Datacenter (8 Host fisik dan 10 VM), VM ditempatkan secara siklik:
      - **Host #1:** Menampung 2 VM
      - **Host #2:** Menampung 2 VM
      - **Host #3:** Menampung 1 VM
      - **Host #4:** Menampung 1 VM
      - **Host #5:** Menampung 1 VM
      - **Host #6:** Menampung 1 VM
      - **Host #7:** Menampung 1 VM
      - **Host #8:** Menampung 1 VM
    - **Total: 10 VM per Datacenter. 100% Host aktif (0 Host nganggur)!**
  - Disediakan task Gradle khusus untuk membuktikannya di layar:
    ```powershell
    .\gradlew.bat testAllocation
    ```
    Outputnya akan mencetak tabel verifikasi penempatan VM ke host fisik secara transparan.
* **Jawaban saat Ditanya:**
  > *"Kami telah mengimplementasikan kebijakan Penyerataan Alokasi VM (Balanced Round-Robin Allocation). Pada setiap Datacenter yang memiliki 8 host dan 10 VM, tepat 6 host menampung 1 VM dan 2 host menampung 2 VM. Tidak ada satupun host yang menganggur. Hal ini dibuktikan melalui eksekusi `testAllocation` di terminal dan tercantum di Tabel 5 proposal."*

---

### 3. "Memahami algoritma First-Fit vs Best-Fit vs Penyerataan (alokasi host ditaruh di komen kode)"
* **Solusi & Mitigasi yang Diterapkan:**
  - Pada `DatacenterBuilder.java` baris 125–138, telah ditambahkan komentar komprehensif:
    1. **First-Fit:** Memilih host pertama yang memiliki kapasitas PE dan RAM mencukupi tanpa mengevaluasi sisa kapasitas host lain. Kelemahan: penumpukan pada host indeks awal.
    2. **Best-Fit (`VmAllocationPolicyBestFit`):** Mengevaluasi seluruh host dan memilih host dengan sisa resource paling minimal setelah VM ditempatkan. Tujuannya adalah memadatkan server (server consolidation) untuk efisiensi energi.
    3. **Penyerataan (`VmAllocationPolicyRoundRobin` / Balanced):** Mendistribusikan VM secara bergiliran ke seluruh host agar beban panas, memori, dan komputasi merata, serta menjamin **tidak ada host yang menganggur**.
* **Jawaban saat Ditanya:**
  > *"Perbedaan ketiganya terletak pada fungsi objektif penempatan VM: First-Fit mencari host tercepat yang muat; Best-Fit mencari host dengan sisa ruang paling sempit untuk pemadatan server; sedangkan Penyerataan mendistribusikan VM secara siklik agar seluruh host fisik beroperasi aktif tanpa ada host yang menganggur. Komentar penjelasannya sudah kami cantumkan lengkap di file `DatacenterBuilder.java`."*

---

### 4. "Tunjukkan createVms() dalam codebase"
* **Lokasi Kode:** [`src/main/java/com/kelompok8/DatacenterBuilder.java`](file:///c:/Users/arya4/soka/src/main/java/com/kelompok8/DatacenterBuilder.java) method `createVmsForDc()` (baris 220–237).
* **Potongan Kode:**
  ```java
  private static List<Vm> createVmsForDc(int pes, int ramMB, int mips, int count, String dcName) {
      List<Vm> vmList = new ArrayList<>();
      String vmType = pes == 1 ? "Small" : (pes == 2 ? "Medium" : "Large");

      for (int i = 0; i < count; i++) {
          Vm vm = new VmSimple(mips, pes)
                  .setRam(ramMB)
                  .setBw(250)       // 250 Mbps per VM
                  .setSize(10_000)  // 10 GB storage
                  .setCloudletScheduler(new CloudletSchedulerSpaceShared());
          vmList.add(vm);
      }
      return vmList;
  }
  ```
* **Jawaban saat Ditanya:**
  > *"Pembuatan VM diatur secara modular di method `createVmsForDc` pada `DatacenterBuilder.java`. Method ini menginstansiasi VM dengan spesifikasi vCPU, RAM, MIPS, storage 10 GB, bandwidth 250 Mbps, dan menggunakan penjadwal non-preemptive `CloudletSchedulerSpaceShared`."*

---

### 5. "Dataset random, tolong habis disintesis disimpan biar ada bukti, seperti panjang tiap data untuk ukuran 100 cloudlet berapa dsb"
* **Solusi & Mitigasi yang Diterapkan:**
  - Generator dataset di `SwfParser.java` otomatis menyimpan setiap dataset yang dibangkitkan ke file CSV di folder `dataset/`:
    - `synthetic_dataset_100.csv` s.d. `synthetic_dataset_1000.csv` (kelipatan 100)
    - `synthetic_dataset_1000.csv` s.d. `synthetic_dataset_10000.csv` (kelipatan 1.000)
    - `gocj_dataset_100.csv` s.d. `gocj_dataset_1000.csv`
  - Setiap baris CSV mencatat: `TaskId`, `LengthMI`, `PesNumber`, `SubmissionDelay(s)`, `FileSize(B)`, `OutputSize(B)`.
* **Jawaban saat Ditanya:**
  > *"Seluruh dataset acak yang disintesis langsung kami persistensikan ke dalam berkas CSV pada direktori `dataset/`. Dosen dapat memeriksa langsung file seperti `synthetic_dataset_100.csv` hingga `synthetic_dataset_10000.csv` untuk melihat panjang instruksi MI, jumlah core PE, dan arrival delay tiap task."*

---

### 6. "Cari tahu task pendek dan panjang berapa persen (30% Task Kecil, 40% Task Sedang, 30% Task Besar)"
* **Karakteristik Dataset Sintetis:**
  - **30% Task Kecil (1 PE):** 500 – 5.000 MI, arrival uniform [0, 100] s. (Beban mikro, sensor IoT, web request)
  - **40% Task Sedang (2 PE):** 5.000 – 50.000 MI, arrival uniform [0, 200] s. (Beban analitik data menengah, kompresi)
  - **30% Task Besar (4 PE):** 50.000 – 500.000 MI, arrival uniform [0, 300] s. (Beban training ML, simulasi multi-core)
* **Pengecekan Konsol:**
  Di `SwfParser.java` tersedia method `printDatasetCharacteristics()` yang mencetak rekapitulasi persentase, nilai minimum, maksimum, dan rata-rata instruksi MI secara otomatis saat simulasi dijalankan.

---

### 7. "Revisi uji mulai dari 100, 200, 300, sd. 1000 masing-masing 3x dan hasilnya ditampilkan" & "Tambahkan dataset jadi 1000 s.d. 10000 (3x run dan diambil rata-rata)"
* **Solusi & Mitigasi yang Diterapkan:**
  - Telah dibuat modul pengujian otomatis: `BatchExperimentSuite.java`.
  - Skala 100 – 1.000 (kelipatan 100):
    - Diuji pada Sintetis, NASA iPSC, dan GoCJ trace.
    - Tiap konfigurasi dirun 3x independen. Hasil rata-rata diekspor ke `results/batch_100_1000_results.csv`.
  - Skala 1.000 – 10.000 (kelipatan 1.000):
    - Diuji dari 1.000, 2.000, 3.000, ..., 10.000 task.
    - Tiap konfigurasi dirun 3x independen. Hasil rata-rata diekspor ke `results/synthetic_1000_10000_results.csv`.
  - Perintah eksekusi:
    ```powershell
    .\gradlew.bat runBatch
    ```

---

### 8. "Mengecek kembali running utk dataset yg sama (hasil total objective keseluruhan harusnya sama walopun di run berkali-kali)"
* **Bukti Empiris:**
  - PEFT, FCFS, dan Round Robin adalah algoritma penjadwalan deterministik (non-stokastik).
  - Pada hasil multi-run 3x di `BatchExperimentSuite`:
    `StdDev Makespan Run: 0.00`
  - Run 1, Run 2, dan Run 3 pada dataset yang sama menghasilkan nilai Makespan, Waiting Time, dan Total Energi yang **100% identik hingga 4 digit desimal**.
* **Jawaban saat Ditanya:**
  > *"Kami telah memverifikasi konsistensi eksekusi multi-run. Karena PEFT memprediksi EFT menggunakan fungsi deterministik dan pemetaan yang terurut, hasil total objective pada Run 1, 2, dan 3 adalah 100% konsisten dengan standar deviasi antar-run sebesar 0,00."*

---

### 9. "Apakah ada perilaku yang berbeda dari dataset-dataset di atas ini, jelaskan alasannya kenapa bisa kenapa tidak?"
* **Analisis Perbandingan Ilmiah:**
  1. **Dataset Sintetis (Beban Kedatangan Padat / Arrival 0–300 detik):**
     - PEFT unggul mutlak dalam memangkas makespan hingga **-15,39%** pada 1.000 task dan **-7,66%** (selisih 3.440 detik) pada 10.000 task.
     - **Alasan Ilmiah:** Karena kedatangan task terkonsentrasi dalam jendela waktu singkat, antrean terbentuk padat di seluruh VM. PEFT secara cerdas memprediksi ketersediaan core prosesor terawal dan memprioritaskan task besar ke VM bermips tinggi (DC-3 Large), sehingga antrean terdistribusi optimal.
  2. **Dataset NASA iPSC (Trace Riil Rentang Panjang / Arrival s.d. 48 jam):**
     - Makespan ketiga algoritma relatif identik pada 180.250,21 detik.
     - **Alasan Ilmiah:** Pada trace NASA, interval kedatangan antar-task sangat renggang (tersebar sepanjang 2 hari). Makespan sistem secara fundamental dibatasi oleh waktu kedatangan task terakhir ($Makespan \approx \max(\text{arrival}_i + \text{duration}_i)$).
     - **Namun Keunggulan PEFT Tetap Nyata:** PEFT berhasil meniadakan waktu tunggu antrean menjadi **0,00 detik** (vs 20,54 detik pada FCFS) dan memangkas turnaround time rata-rata sebesar **24,19%** (198,95 s vs 262,43 s).
  3. **Dataset GoCJ (Google Cloud Jobs Trace):**
     - Memiliki pola distribusi log-normal dengan dominasi 60% task pendek. PEFT unggul memangkas makespan sebesar **-15,59%** vs FCFS (1.027,93 s vs 1.217,88 s) karena mampu mencegah task kecil terblokir oleh task besar.

---

### 10. "Buat diagram perbandingan makespan, dkk (pisahkan hasil NASA iPSC dan Synthetic)"
* **Klarifikasi Dataset Riil Kelompok 8:**
  - Dataset riil utama Kelompok 8 adalah **NASA Ames iPSC/860 (`NASA-iPSC-1993-3.1-cln.swf`)**.
  - Catatan revisi yang menyebut "GoCJ" berasal dari kelompok lain di kelas yang terikut ke catatan kompilasi. Namun demi **zero-loophole**, sistem Kelompok 8 telah mendukung dan menguji **keduanya** (NASA iPSC maupun GoCJ).
* **Solusi & Mitigasi Visual yang Diterapkan:**
  - Skrip `GenerateEvaluationCharts.java` menghasilkan 7 berkas grafik resolusi tinggi (1200x750) di folder `docs/images/`:
    1. `grafik_makespan_sintetis_100_1000.png` — Makespan Dataset Sintetis (100–1.000)
    2. `grafik_makespan_sintetis_1000_10000.png` — Makespan Skala Masif Sintetis (1.000–10.000)
    3. `grafik_makespan_nasa_100_1000.png` — Makespan Real Trace NASA iPSC (100–1.000)
    4. `grafik_waiting_time_nasa_100_1000.png` — Waktu Tunggu Real Trace NASA iPSC (100–1.000)
    5. `grafik_energi_specpower_1000_10000.png` — Total Konsumsi Energi SPECpower (1.000–10.000)
    6. `grafik_load_balance_1000_10000.png` — Derajat Ketidakseimbangan Beban (1.000–10.000)
    7. `grafik_makespan_gocj_100_1000.png` — Makespan Google Cloud Jobs Trace (Bonus Cadangan)
  - Perintah eksekusi:
    ```powershell
    .\gradlew.bat generateCharts
    ```

---

### 10B. PANDUAN MENJAWAB PERILAKU TIAP GRAFIK (KENAPA NAIK, TURUN, FLUKTUASI, ATAU FLAT)

Ketika Bu Henning menunjuk grafik di layar dan bertanya *"Kenapa kurvanya berbentuk seperti ini?"*, gunakan panduan berikut:

1. **Grafik Makespan Sintetis (100–1.000 & 1.000–10.000):**
   * **Pertanyaan:** *"Kenapa kurvanya naik linier dan PEFT konsisten di bawah?"*
   * **Jawaban:**
     > *"Kurva naik linier karena total beban komputasi (Million Instructions) bertambah seiring jumlah task, sementara kapasitas total cloud kita tetap (30 VM = 45.000 MIPS). Karena task datang terkonsentrasi dalam jendela 0–300 detik, antrean bertambah secara proporsional. PEFT selalu tercepat (menghemat hingga 3.440 detik pada 10.000 task) karena PEFT memetakan task besar (proporsi 30%) ke VM Large di DC-3 (2.000 MIPS). Sebaliknya, FCFS dan Round Robin mengalami **Convoy Effect**, di mana task besar dialokasikan secara buta ke VM lambat, memblokir puluhan task di belakangnya."*

2. **Grafik Makespan Real Trace NASA iPSC (100–1.000):**
   * **Pertanyaan:** *"Kenapa di trace NASA makespan ketiga algoritma hampir sama persis (berimpit di ~180.250 detik)?"*
   * **Jawaban:**
     > *"Ini adalah karakteristik **Arrival-Bounded Makespan**. Trace supercomputer NASA iPSC membentang selama 48 jam (180.000 detik). Task ke-1000 baru disubmit pada detik ke-180.200. Karena sistem tidak bisa menyelesaikan task sebelum task tersebut disubmit oleh user, maka makespan sistem secara fundamental dibatasi oleh waktu kedatangan task terakhir, bukan oleh kecepatan komputasi server. Itulah sebabnya ketiga algoritma selesai di waktu yang relatif identik."*

3. **Grafik Waiting Time NASA iPSC (100–1.000):**
   * **Pertanyaan:** *"Kenapa waktu tunggu FCFS naik-turun bergelombang (5s -> 44s -> 20s), sedangkan PEFT mendatar sempurna di 0 detik?"*
   * **Jawaban:**
     > *"Fluktuasi naik-turun pada FCFS disebabkan oleh fenomena **Job Burst** (semburan tugas) dari peneliti NASA. Pada rentang 200–350 task, beberapa tim peneliti mengirimkan puluhan task secara serentak, menyebabkan antrean lokal FCFS melonjak ke puncak 44,38 detik sebelum terurai kembali saat jeda kedatangan merenggang. Sementara itu, PEFT berhasil mendatar di **0,00 detik (flat)** karena PEFT secara proaktif memantau status seluruh 30 VM dan langsung mengalihkan semburan task ke core-core prosesor yang sedang menganggur (*idle core*) lintas 3 Datacenter."*

4. **Grafik Konsumsi Energi SPECpower (1.000–10.000):**
   * **Pertanyaan:** *"Kenapa energi naik dan bagaimana PEFT bisa menghemat hingga 10 Megajoule?"*
   * **Jawaban:**
     > *"Energi naik secara linier seiring volume tugas dan durasi operasional 24 host server. PEFT lebih hemat (hemat 2,80 kWh / 10,07 MJ pada 10k task) karena efisiensi pemangkasan **Idle Power**. Host server fisik tetap mengonsumsi daya dasar ~40% daya maksimum saat idle. Karena PEFT menuntaskan seluruh beban 3.440 detik lebih cepat, periode pemborosan daya idle pada host terpangkas drastis."*

5. **Grafik Load Balance StdDev (1.000–10.000):**
   * **Pertanyaan:** *"Kenapa ketimpangan beban FCFS/RR meledak dari 20 ke 205, sedangkan PEFT tetap sangat rendah di kisaran 48?"*
   * **Jawaban:**
     > *"FCFS dan Round Robin membagikan task tanpa melihat kecepatan VM (VM Small 1.000 MIPS vs VM Large 8.000 MIPS). Akibatnya, VM lambat menerima volume MI yang setara dengan VM cepat, menciptakan jurang ketimpangan beban yang meledak seiring skala tugas. Sebaliknya, PEFT mendistribusikan beban secara proporsional sesuai rasio MIPS, sehingga seluruh VM selesai hampir bersamaan. Ketimpangan beban berhasil diredam hingga **-76,37%**."*

---

## RINGKASAN COMMAND LINE UNTUK DEMO KEPADA DOSEN

| Perintah Terminal | Kegunaan Demo |
|:---|:---|
| `.\gradlew.bat runAll` | Menjalankan simulasi utama (Sintetis 1.000 & NASA 1.000) lintas 3 Datacenter |
| `.\gradlew.bat testAllocation` | Menampilkan bukti alokasi VM ke host (membuktikan bebas host nganggur) |
| `.\gradlew.bat runBatch` | Menjalankan seluruh batch eksperimen 100–1.000 dan 1.000–10.000 (3x Run) |
| `.\gradlew.bat generateCharts` | Membangkitkan seluruh grafik perbandingan resolusi tinggi |
| `.\gradlew.bat runPEFT` | Menjalankan pengujian khusus algoritma PEFT |

---
**Kesimpulan Akhir:** Seluruh catatan revisi dari Bu Henning telah diimplementasikan, diverifikasi secara empiris di codebase Java, didokumentasikan di Proposal dan Laporan DOCX, serta disimpan buktinya dalam bentuk data CSV dan grafik visual tanpa ada satu celah pun yang terlewat.
