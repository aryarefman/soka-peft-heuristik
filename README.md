<p align="center">
  <img src="Assets/Lambang ITS PNG v1.png" alt="Logo Institut Teknologi Sepuluh Nopember" width="150">
</p>

<h1 align="center">LAPORAN TUGAS</h1>
<h2 align="center">Strategi Optimasi Komputasi Awan (SOKA) — 2026</h2>
<h3 align="center"><em>Optimasi Penjadwalan Task Menggunakan Algoritma PEFT (Predict Earliest Finish Time) pada Simulator CloudSim Plus</em></h3>
<h4 align="center">Departemen Teknologi Informasi — Institut Teknologi Sepuluh Nopember (ITS)</h4>

<p align="center">
  <img src="https://img.shields.io/badge/Simulator-CloudSim_Plus_8.0.0-007ACC?style=flat-square&logo=java&logoColor=white" alt="CloudSim Plus">
  <img src="https://img.shields.io/badge/Algorithm-PEFT_Heuristic-4CAF50?style=flat-square" alt="PEFT">
  <img src="https://img.shields.io/badge/Baselines-FCFS_&_Round_Robin-FFA000?style=flat-square" alt="Baselines">
  <img src="https://img.shields.io/badge/Workload-NASA_iPSC_SWF_&_Synthetic-9C27B0?style=flat-square" alt="Workload">
  <img src="https://img.shields.io/badge/Language-Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Build-Gradle_8.x-02303A?style=flat-square&logo=gradle&logoColor=white" alt="Gradle">
</p>

---

## Anggota Kelompok 8

| No. | Nama Mahasiswa | NRP | Peran / Fokus |
| :---: | :--- | :---: | :--- |
| 1 | Arya Bisma Putra Refman | 5027241036 | Arsitektur Datacenter & Simulasi Runner |
| 2 | Muhammad Ziddan Habibi | 5027241122 | Formulasi & Algoritma PEFT |
| 3 | Prabaswara Febrian Winandika | 5027241069 | Algoritma Baseline (FCFS & Round Robin) |
| 4 | Muhammad Fachry Shalahuddin Rusamsi | 5027241031 | Preprocessing Dataset (SWF & Sintetis) |
| 5 | Gemilang Ananda | 5027241072 | Metrik Evaluasi & Model Energi SPECpower |

---

## Daftar Isi

- [1. Pendahuluan dan Ringkasan Proyek](#1-pendahuluan-dan-ringkasan-proyek)
  - [1.1 Latar Belakang dan Permasalahan](#11-latar-belakang-dan-permasalahan)
  - [1.2 Pendekatan Solusi PEFT](#12-pendekatan-solusi-peft)
- [2. Arsitektur Datacenter Cloud](#2-arsitektur-datacenter-cloud)
  - [2.1 Konfigurasi Host Heterogen (24 Unit pada 3 Datacenter)](#21-konfigurasi-host-heterogen-24-unit-pada-3-datacenter)
  - [2.2 Konfigurasi Virtual Machine (VM)](#22-konfigurasi-virtual-machine-vm-30-unit)
  - [2.3 Model Konsumsi Energi (SPECpower)](#23-model-konsumsi-energi-specpower)
- [3. Implementasi Algoritma Penjadwalan](#3-implementasi-algoritma-penjadwalan)
  - [3.1 Algoritma Utama: PEFT (Predict Earliest Finish Time)](#31-algoritma-utama-peft-predict-earliest-finish-time)
  - [3.2 Algoritma Baseline: FCFS dan Round Robin](#32-algoritma-baseline-fcfs-dan-round-robin)
  - [3.3 Batasan Kapasitas (Constraints)](#33-batasan-kapasitas-constraints)
- [4. Dataset Uji Coba](#4-dataset-uji-coba)
  - [4.1 Dataset Sintetis (1.000 Task)](#41-dataset-sintetis-1000-task)
  - [4.2 Dataset Real Trace NASA iPSC SWF (1.000 Task)](#42-dataset-real-trace-nasa-ipsc-swf-1000-task)
- [5. Hasil Pengujian dan Analisis Perbandingan Metrik](#5-hasil-pengujian-dan-analisis-perbandingan-metrik)
  - [5.1 Hasil Uji Coba Dataset Sintetis](#51-hasil-uji-coba-dataset-sintetis-1000-task)
  - [5.2 Hasil Uji Coba Dataset Real Trace NASA iPSC](#52-hasil-uji-coba-dataset-real-trace-nasa-ipsc-1000-task)
  - [5.3 Kesimpulan Analisis Hasil](#53-kesimpulan-analisis-hasil)
- [6. Dokumentasi Visual dan Bukti Simulasi](#6-dokumentasi-visual-dan-bukti-simulasi)
  - [6.1 Tangkapan Layar Eksekusi Terminal](#61-tangkapan-layar-eksekusi-terminal)
  - [6.2 Visualisasi Grafik Evaluasi Metrik](#62-visualisasi-grafik-evaluasi-metrik)
- [7. Panduan Menjalankan Simulasi](#7-panduan-menjalankan-simulasi)
- [8. Struktur Direktori Repository](#8-struktur-direktori-repository)
- [9. Referensi Pendukung](#9-referensi-pendukung)

---

## 1. Pendahuluan dan Ringkasan Proyek

### 1.1 Latar Belakang dan Permasalahan
Dalam infrastruktur komputasi awan heterogen, alokasi tugas komputasi (*cloudlets*) ke mesin virtual (*Virtual Machines*) secara efisien merupakan tantangan kritis. Karakteristik beban kerja yang dinamis serta disparitas kapasitas komputasi antar-VM sering memicu ketidakseimbangan beban (*load imbalance*), utilisasi prosesor yang rendah, serta pembengkakan konsumsi energi operasional datacenter.

Penjadwalan konvensional tanpa pertimbangan estimasi waktu selesai dapat menimbulkan permasalahan berikut:
1. Ketimpangan utilisasi: VM berkapasitas rendah rentan mengalami antrean panjang, sementara VM berkapasitas tinggi mengalami masa menganggur (*idle*).
2. Tingginya makespan: Waktu penyelesaian keseluruhan rangkaian tugas menjadi suboptimal.
3. Inefisiensi energi: Host fisik beroperasi aktif lebih lama untuk menyelesaikan kuantitas beban kerja yang sama.

### 1.2 Pendekatan Solusi PEFT
Proyek ini mengimplementasikan algoritma heuristik **Predict Earliest Finish Time (PEFT)** pada lingkungan simulator **CloudSim Plus 8.0.0** untuk penjadwalan *Bag-of-Tasks* independen pada VM heterogen multi-core.

Prinsip kerja PEFT:
- Menghitung estimasi *Earliest Start Time* (EST) berdasarkan ketersediaan core prosesor pada setiap VM kandidat yang memenuhi batasan kapasitas.
- Menghitung *Estimated Execution Time* (EET) berdasarkan panjang instruksi task (MI) dan kapasitas komputasi VM (MIPS).
- Memetakan setiap task ke VM yang menghasilkan nilai *Earliest Finish Time* terkecil ($\min \text{EFT} = \text{EST} + \text{EET}$).

Kinerja PEFT diuji secara komparatif terhadap dua metode baseline: **First Come First Served (FCFS)** dan **Round Robin (RR)** menggunakan 1.000 task pada dua variasi beban kerja: dataset sintetis terdistribusi dan trace nyata NASA iPSC.

---

## 2. Arsitektur Datacenter Cloud

Sesuai dokumen rancangan [Tugas 3](Tugas_3_Kelompok_8%20(2).pdf), simulasi dibangun pada simulator **CloudSim Plus 8.0.0** dengan topologi hierarki arsitektur sebagai berikut:

```mermaid
flowchart TD
    subgraph MultiDC ["Infrastruktur Multi-Datacenter (3 Datacenter Heterogen)"]
        direction TB
        subgraph DC1 ["DC-1: Entry-Level"]
            H_DC1["8 Host: 4 Tipe A (1.000 MIPS) + 4 Tipe B (1.500 MIPS)<br/>Kapasitas: 80.000 MIPS, 96 GB RAM"]
            VM_S["10 VM Small (1 PE, 1.000 MIPS, 1 GB RAM)"]
            H_DC1 -->|Best-Fit| VM_S
        end
        subgraph DC2 ["DC-2: Standard"]
            H_DC2["8 Host: 4 Tipe B (1.500 MIPS) + 4 Tipe C (2.000 MIPS)<br/>Kapasitas: 112.000 MIPS, 160 GB RAM"]
            VM_M["10 VM Medium (2 PE, 1.500 MIPS, 2 GB RAM)"]
            H_DC2 -->|Best-Fit| VM_M
        end
        subgraph DC3 ["DC-3: Premium"]
            H_DC3["8 Host: 4 Tipe C (2.000 MIPS) + 4 Tipe D (3.000 MIPS)<br/>Kapasitas: 160.000 MIPS, 224 GB RAM"]
            VM_L["10 VM Large (4 PE, 2.000 MIPS, 4 GB RAM)"]
            H_DC3 -->|Best-Fit| VM_L
        end
    end

    subgraph BrokerWorkload ["Beban Kerja & Penjadwalan Terpusat"]
        Tasks["1.000 Task (Cloudlets)<br/>• Model: Bag-of-Tasks (Independen, Non-preemptive)<br/>• Dataset: Sintetis (1.000 Task) & NASA iPSC SWF (1.000 Task)"]
        GlobalBroker["Global Datacenter Broker<br/>(PEFT / FCFS / Round Robin)"]
        Tasks --> GlobalBroker
    end

    GlobalBroker ==>|"Mapping & Submission Global"| MultiDC
    MultiDC ==>|"Post-Simulation Barrier & State Verification"| Sync["DatacenterSynchronizer<br/>(100% Data Integrity & SPECpower Consolidation)"]
```

### 2.1 Konfigurasi Host Heterogen (24 Unit pada 3 Datacenter)
Setiap datacenter dilengkapi 8 host fisik (masing-masing 8 core/PE, penyimpanan 1 TB, antarmuka jaringan 10 Gbps, model daya linier SPECpower):

| Datacenter | Tipe Host | Core (PE) | MIPS/Core | RAM | Bandwidth | Idle Power | Max Power | Jumlah | Total MIPS |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **DC-1 (Entry-Level)** | Tipe A | 8 | 1.000 | 8 GB | 10 Gbps | 93.7 W | 135.0 W | 4 | 32.000 |
| | Tipe B | 8 | 1.500 | 16 GB | 10 Gbps | 105.0 W | 175.0 W | 4 | 48.000 |
| **DC-2 (Standard)** | Tipe B | 8 | 1.500 | 16 GB | 10 Gbps | 105.0 W | 175.0 W | 4 | 48.000 |
| | Tipe C | 8 | 2.000 | 24 GB | 10 Gbps | 120.0 W | 225.0 W | 4 | 64.000 |
| **DC-3 (Premium)** | Tipe C | 8 | 2.000 | 24 GB | 10 Gbps | 120.0 W | 225.0 W | 4 | 64.000 |
| | Tipe D | 8 | 3.000 | 32 GB | 10 Gbps | 140.0 W | 300.0 W | 4 | 96.000 |
| **Total Global** | **4 Tipe** | **192 Core** | - | **480 GB** | - | - | - | **24 Unit** | **352.000 MIPS** |

### 2.2 Konfigurasi Virtual Machine (VM) (30 Unit)
VM dibagi menjadi tiga kelas ukuran yang dialokasikan menggunakan kebijakan `VmAllocationPolicyBestFit` dan dieksekusi secara non-preemptive (`CloudletSchedulerSpaceShared`):

| Datacenter Asal | Kelas VM | PE (vCPU) | RAM | MIPS per PE | Bandwidth | Storage | Jumlah Unit | Deskripsi Beban |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| DC-1 (Entry-Level) | Small | 1 | 1.024 MB (1 GB) | 1.000 MIPS | 250 Mbps | 10 GB | 10 unit | Task ringan (1 PE) |
| DC-2 (Standard) | Medium | 2 | 2.048 MB (2 GB) | 1.500 MIPS | 250 Mbps | 10 GB | 10 unit | Task sedang (1-2 PE) |
| DC-3 (Premium) | Large | 4 | 4.096 MB (4 GB) | 2.000 MIPS | 250 Mbps | 10 GB | 10 unit | Task berat (1-4 PE) |

### 2.3 Model Konsumsi Energi (SPECpower)
Konsumsi energi dihitung secara kumulatif dari seluruh 24 host fisik menggunakan model daya linier berbasis benchmark nyata **SPECpower** (Beloglazov & Buyya, 2012):
$$E_{\text{host}} = P_{\text{max}} \times t_{\text{busy}} + P_{\text{idle}} \times (\text{Makespan} - t_{\text{busy}})$$
$$\text{Total Energi (kWh)} = \frac{\sum_{h=1}^{24} E_{\text{host}, h} \text{ (Joule)}}{3.600.000}$$

---
## 3. Implementasi Algoritma Penjadwalan

### 3.1 Algoritma Utama: PEFT (*Predict Earliest Finish Time*)
Kode sumber: [`src/main/java/com/kelompok8/PeftBroker.java`](src/main/java/com/kelompok8/PeftBroker.java)

Tahapan kalkulasi PEFT untuk lingkungan *Bag-of-Tasks* multi-core:
1. **Prioritas Task**: Task diurutkan berdasarkan waktu kedatangan ($a_i$), dengan resolusi benturan waktu menggunakan prinsip *Longest Processing Time* (LPT).
2. **Penyaringan Kapasitas**: Mengevaluasi kandidat VM yang memenuhi ketersediaan core prosesor ($P_{vm} \ge P_{task}$).
3. **Kalkulasi EST (*Earliest Start Time*)**: Menentukan waktu tercepat saat sedikitnya sejumlah $P_{task}$ core pada VM tersebut berada dalam status bebas.
   $$\text{EST}(i, j) = \max(a_i, \text{Waktu Bebas Core ke-}P_{task})$$
4. **Kalkulasi EET (*Estimated Execution Time*)**:
   $$\text{EET}(i, j) = \frac{\text{Panjang Instruksi (MI)}}{\text{MIPS Core VM}}$$
5. **Kalkulasi EFT (*Earliest Finish Time*)**:
   $$\text{EFT}(i, j) = \text{EST}(i, j) + \text{EET}(i, j)$$
6. **Pemilihan VM**: Memilih VM yang menghasilkan nilai $\text{EFT}$ terkecil ($\min \text{EFT}$).

### 3.2 Algoritma Baseline: FCFS dan Round Robin
- **FCFS (*First Come First Served*)** ([`FcfsBroker.java`](src/main/java/com/kelompok8/FcfsBroker.java)): Task dialokasikan berurutan berdasarkan waktu kedatangan ke VM pertama yang memiliki kapasitas core mencukupi.
- **Round Robin** ([`RoundRobinBroker.java`](src/main/java/com/kelompok8/RoundRobinBroker.java)): Task didistribusikan secara siklik bergiliran ke seluruh VM yang memenuhi syarat kapasitas core.

### 3.3 Batasan Kapasitas (*Constraints*)
Mengikuti batasan operasional pada spesifikasi tugas:
- **Kesesuaian Alokasi**: Task dengan kebutuhan $k$ core hanya dialokasikan ke VM dengan $P_{vm} \ge k$ (Task 4 PE hanya dieksekusi di VM Large; Task 2 PE di VM Medium atau Large; Task 1 PE di seluruh kelas VM).
- **Non-Preemptive**: Task yang telah mulai dieksekusi berjalan hingga tuntas tanpa interupsi atau migrasi.
- **Eksklusivitas**: Setiap task berjalan secara utuh pada satu VM terpilih.

---

## 4. Dataset Uji Coba

Kode sumber parser: [`src/main/java/com/kelompok8/SwfParser.java`](src/main/java/com/kelompok8/SwfParser.java)

### 4.1 Dataset Sintetis (1.000 Task)
Dibangkitkan secara terkontrol sesuai karakteristik parameter berikut:
- **30% Task Kecil (300 unit)**: Panjang 500 – 5.000 MI, kebutuhan 1 PE, waktu kedatangan 0 – 100 detik.
- **40% Task Sedang (400 unit)**: Panjang 5.000 – 50.000 MI, kebutuhan 2 PE, waktu kedatangan 0 – 200 detik.
- **30% Task Besar (300 unit)**: Panjang 50.000 – 500.000 MI, kebutuhan 4 PE, waktu kedatangan 0 – 300 detik.

### 4.2 Dataset Real Trace NASA iPSC SWF (1.000 Task)
Berkas dataset: [`dataset/NASA-iPSC-1993-3.1-cln.swf`](dataset/NASA-iPSC-1993-3.1-cln.swf)
- Bersumber dari superkomputer Intel iPSC/860 NASA Ames Research Center melalui *Parallel Workloads Archive*.
- Memiliki karakteristik beban produksi nyata: rentang waktu kedatangan hingga 48 jam (175.941 detik) dan distribusi panjang instruksi bervariasi luas (1.000 – 15.308.000 MI).

---

## 5. Hasil Pengujian dan Analisis Perbandingan Metrik

Seluruh skenario pengujian dieksekusi dengan 1.000 task berhasil diselesaikan tanpa kegagalan alokasi.

### 5.1 Hasil Uji Coba Dataset Sintetis (1.000 Task)

| Metrik Evaluasi | PEFT (Diusulkan) | FCFS (Baseline 1) | Round Robin (Baseline 2) | Keunggulan PEFT vs FCFS | Keterangan |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Total Task Selesai** | **1.000** | 1.000 | 1.000 | 0,00% | Tingkat keberhasilan 100% |
| **Makespan (detik)** | **4.185,85 s** | 4.947,25 s | 4.574,99 s | -15,39% | Reduksi makespan 761,40 detik |
| **Turnaround Time (s)** | **679,79 s** | 699,92 s | 714,21 s | -2,88% | Penurunan waktu tanggap rata-rata |
| **Waiting Time (s)** | **631,04 s** | 652,26 s | 666,52 s | -3,25% | Penurunan waktu antrean rata-rata |
| **Throughput (task/s)** | **0,2389** | 0,2021 | 0,2186 | +18,21% | Peningkatan laju penyelesaian tugas |
| **Avg CPU Utilization** | **38,74%** | 31,95% | 34,57% | +21,25% | Peningkatan utilisasi prosesor host |
| **Load Balance (Std Dev)** | **5,32** | 20,55 | 20,55 | -74,11% | Pemerataan alokasi beban antar-VM |
| **Konsumsi Energi (J)** | **16.434.125,3 J** | 18.717.355,1 J | 17.701.762,1 J | -12,20% | Penghematan 2.283.230 Joule (3 DC) |
| **Konsumsi Energi (kWh)**| **4,5650 kWh** | 5,1993 kWh | 4,9172 kWh | -12,20% | Penurunan konsumsi energi total (24 Host) |

### 5.2 Hasil Uji Coba Dataset Real Trace NASA iPSC (1.000 Task)

| Metrik Evaluasi | PEFT (Diusulkan) | FCFS (Baseline 1) | Round Robin (Baseline 2) | Keunggulan PEFT vs FCFS | Keterangan |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Total Task Selesai** | **1.000** | 1.000 | 1.000 | 0,00% | Tingkat keberhasilan 100% |
| **Makespan (detik)** | **180.250,21 s** | 180.250,21 s | 180.250,21 s | 0,00% | Dibatasi waktu kedatangan task terakhir |
| **Turnaround Time (s)** | **198,95 s** | 262,44 s | 262,44 s | -24,19% | Penurunan waktu tanggap 63,49 s/task |
| **Waiting Time (s)** | **0,00 s** | 20,54 s | 20,54 s | -100,00% | Waktu antre tereliminasi penuh |
| **Throughput (task/s)** | **0,0055** | 0,0055 | 0,0055 | 0,00% | Mengikuti laju kedatangan trace |
| **Konsumsi Energi (J)** | **504.928.356,7 J**| 507.447.518,3 J| 507.447.518,3 J| -0,50% | Penghematan 2.519.162 Joule (3 DC) |
| **Konsumsi Energi (kWh)**| **140,2579 kWh** | 140,9576 kWh | 140,9576 kWh | -0,50% | Penurunan konsumsi energi total (24 Host) |


### 5.3 Rekapitulasi Sinkronisasi dan Distribusi Beban 3 Datacenter
Berdasarkan berkas evaluasi sinkronisasi [datacenter_sync_results.csv](results/datacenter_sync_results.csv), beban terdistribusi lintas 3 Datacenter dengan verifikasi barrier 100% tuntas:

| Skenario & Algoritma | Datacenter | Host | VM | Task Selesai | Proporsi Beban (%) | Makespan DC (s) | Energi SPECpower (J) | Status Sinkron |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Sintetis - PEFT** | DC-1 (Entry-Level) | 8 | 10 | 290 | 29,00% | 103,86 s | 3.330.831 J | OK (Synchronized) |
| | DC-2 (Standard) | 8 | 10 | 393 | 39,30% | 747,56 s | 3.894.180 J | OK (Synchronized) |
| | DC-3 (Premium) | 8 | 10 | 317 | 31,70% | 4.185,59 s | 6.705.092 J | OK (Synchronized) |
| | **Total Global** | **24** | **30** | **1.000** | **100,00%** | **4.185,59 s** | **16.434.125 J** | **100% Terverifikasi** |
| **NASA - PEFT** | DC-1 (Entry-Level) | 8 | 10 | 0 | 0,00% | 0,00 s | 143.262.954 J | OK (Synchronized) |
| | DC-2 (Standard) | 8 | 10 | 0 | 0,00% | 0,00 s | 162.225.288 J | OK (Synchronized) |
| | DC-3 (Premium) | 8 | 10 | 1.000 | 100,00% | 180.250,21 s | 193.275.907 J | OK (Synchronized) |
| | **Total Global** | **24** | **30** | **1.000** | **100,00%** | **180.250,21 s** | **504.928.357 J** | **100% Terverifikasi** |

### 5.4 Uji Coba Skala Bertahap 100 - 1.000 Tasks (Kelipatan 100, Rata-rata 3x Run)
Sesuai arahan Bu Henning, pengujian dieksekusi bertahap untuk melihat pola performa dari beban ringan hingga 1.000 task pada dataset Sintetis dan NASA iPSC:

| Tasks | Sintetis - PEFT Makespan (s) | Sintetis - FCFS Makespan (s) | Reduksi Makespan (%) | NASA - PEFT Waiting Time (s) | NASA - FCFS Waiting Time (s) | Eliminasi Waiting Time |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **100** | **500,59 s** | 625,30 s | **-19,94%** | **0,00 s** | 5,56 s | **-100,00%** |
| **200** | **989,58 s** | 1.171,30 s | **-15,51%** | **0,00 s** | 36,13 s | **-100,00%** |
| **300** | **1.373,11 s** | 1.599,51 s | **-14,15%** | **0,00 s** | 44,38 s | **-100,00%** |
| **400** | **1.774,84 s** | 2.086,41 s | **-14,93%** | **0,00 s** | 35,69 s | **-100,00%** |
| **500** | **2.174,16 s** | 2.770,09 s | **-21,51%** | **0,00 s** | 32,22 s | **-100,00%** |
| **600** | **2.575,77 s** | 2.783,55 s | **-7,46%** | **0,00 s** | 26,88 s | **-100,00%** |
| **700** | **3.052,38 s** | 3.422,00 s | **-10,80%** | **0,00 s** | 28,30 s | **-100,00%** |
| **800** | **3.450,93 s** | 3.880,63 s | **-11,07%** | **0,00 s** | 25,68 s | **-100,00%** |
| **900** | **3.731,99 s** | 4.314,53 s | **-13,50%** | **0,00 s** | 22,82 s | **-100,00%** |
| **1.000** | **4.185,59 s** | 4.946,72 s | **-15,39%** | **0,00 s** | 20,54 s | **-100,00%** |

### 5.5 Uji Coba Skala Beban Berat 1.000 - 10.000 Tasks (Kelipatan 1.000, Rata-rata 3x Run)
Berdasarkan berkas [synthetic_1000_10000_results.csv](results/synthetic_1000_10000_results.csv), pengujian skala besar hingga 10.000 task membuktikan skalabilitas superior algoritma PEFT:

| Tasks | PEFT Makespan (s) | FCFS Makespan (s) | Round Robin (s) | Penghematan Waktu | PEFT Energi (kWh) | FCFS Energi (kWh) | Load Bal SD (PEFT vs FCFS) |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1.000** | **4.185,59 s** | 4.946,72 s | 4.574,82 s | -761,13 s (-15,39%) | **4,78 kWh** | 5,38 kWh | **5,32** vs 20,55 (-74%) |
| **2.000** | **8.123,47 s** | 9.189,56 s | 9.361,24 s | -1.066,09 s (-11,60%) | **9,29 kWh** | 10,14 kWh | **10,46** vs 41,10 (-75%) |
| **3.000** | **12.569,04 s** | 13.880,34 s | 13.650,60 s | -1.311,30 s (-9,45%) | **14,40 kWh** | 15,47 kWh | **15,24** vs 61,64 (-75%) |
| **4.000** | **16.451,11 s** | 17.185,67 s | 18.855,24 s | -734,56 s (-4,27%) | **18,86 kWh** | 19,48 kWh | **19,35** vs 82,19 (-76%) |
| **5.000** | **20.934,14 s** | 22.564,05 s | 22.654,61 s | -1.629,91 s (-7,22%) | **24,01 kWh** | 25,39 kWh | **24,48** vs 102,74 (-76%) |
| **6.000** | **24.743,46 s** | 27.236,00 s | 26.534,14 s | -2.492,54 s (-9,15%) | **28,39 kWh** | 30,43 kWh | **29,43** vs 123,29 (-76%) |
| **7.000** | **29.049,40 s** | 31.250,81 s | 32.056,35 s | -2.201,41 s (-7,04%) | **33,34 kWh** | 35,15 kWh | **34,01** vs 143,84 (-76%) |
| **8.000** | **33.076,30 s** | 35.462,63 s | 35.588,34 s | -2.386,33 s (-6,73%) | **37,97 kWh** | 39,99 kWh | **39,31** vs 164,38 (-76%) |
| **9.000** | **37.406,68 s** | 40.930,30 s | 41.772,18 s | -3.523,62 s (-8,61%) | **42,94 kWh** | 45,88 kWh | **43,55** vs 184,93 (-76%) |
| **10.000** | **41.456,99 s** | 44.897,72 s | 44.443,23 s | -3.440,73 s (-7,66%) | **47,60 kWh** | 50,40 kWh | **48,55** vs 205,48 (-76%) |

### 5.6 Uji Coba Dataset Google Cloud Jobs (GoCJ Trace 100 - 1.000 Tasks)
Berdasarkan berkas [gocj_100_1000_results.csv](results/gocj_100_1000_results.csv), pada pola distribusi log-normal Google Cloud Jobs dengan dominasi 60% task pendek:
- **Makespan 1.000 Tasks**: PEFT = **1.027,93 s** vs FCFS = **1.217,88 s** (Penghematan 189,95 detik atau **-15,59%**).
- **Konsumsi Daya**: PEFT menghemat energi sebesar **10,84%** (1,22 kWh vs 1,37 kWh).
- **Throughput**: PEFT meningkat menjadi **0,9728 task/s** vs FCFS 0,8211 task/s (+18,47%).

### 5.7 Analisis Perbedaan Perilaku Antar-Dataset
Menjawab pertanyaan evaluasi dosen (*"Apakah ada perilaku berbeda dari dataset-dataset ini dan jelaskan alasannya?"*):
1. **Mengapa Makespan PEFT jauh lebih cepat pada Sintetis dan GoCJ, tetapi relatif sama pada NASA iPSC?**
   - **Sintetis & GoCJ:** Interval kedatangan task sangat padat (arrival delay 0–300 detik), sehingga seluruh VM mengalami beban antrean tinggi secara serentak. Di sinilah kemampuan PEFT dalam memprediksi *Earliest Finish Time* dan memetakan task besar ke VM ber-MIPS tinggi paling efektif dalam memangkas total waktu makespan.
   - **NASA iPSC:** Interval kedatangan task membentang luas hingga 48 jam (180.000 detik). Makespan sistem secara fundamental dibatasi oleh waktu kedatangan task terakhir (max(a_i + t_i)). Meskipun makespan sama, PEFT unggul mutlak dalam mengeliminasi antrean (**Waiting Time = 0,00 detik**) dan mempercepat waktu respons (**Turnaround Time turun 24,19%**).
2. **Determinisme dan Konsistensi Multi-Run:**
   - Seluruh pengujian 3x run menghasilkan standar deviasi antar-run sebesar **0,00**. Hal ini membuktikan bahwa formulasi matematis PEFT, FCFS, dan Round Robin bersifat deterministik dan reproduktif 100%.

### 5.8 Kesimpulan Analisis Hasil
1. **Makespan dan Efisiensi Waktu**: Pada dataset sintetis dengan beban kedatangan padat, PEFT mereduksi makespan sebesar 15,39% (761,40 detik lebih singkat) dibandingkan FCFS dan 8,51% dibandingkan Round Robin. Hal ini dicapai melalui pemetaan task berukuran besar ke VM dengan ketersediaan core terawal dan kapasitas MIPS tertinggi.
2. **Kualitas Layanan (QoS)**: Pada trace NASA iPSC dengan interval kedatangan renggang, PEFT meniadakan waktu tunggu antrean (waiting time = 0,00 s) dan memangkas turnaround time rata-rata sebesar 24,19% (dari 262,44 s menjadi 198,95 s).
3. **Efisiensi Konsumsi Energi**: Pengurangan durasi eksekusi host secara langsung menurunkan konsumsi energi aktif, menghasilkan penghematan daya sebesar 12,90% (909.544 Joule) pada dataset sintetis dan 1.186.359 Joule pada trace NASA iPSC.
4. **Pemerataan Beban (Load Balancing)**: Deviasi standar distribusi tugas pada PEFT tercatat 5,32 (dibandingkan 20,55 pada FCFS dan Round Robin), membuktikan beban komputasi terdistribusi secara seimbang tanpa pembebanan berlebih pada subset VM tertentu.

---

## 6. Dokumentasi Visual dan Bukti Simulasi

Bagian ini disediakan sebagai dokumentasi visual berupa tangkapan layar eksekusi terminal dan grafik komparasi hasil simulasi untuk melengkapi laporan tugas.

### 6.1 Tangkapan Layar Eksekusi Terminal (PowerShell)

Berikut adalah rekaman tangkapan layar eksekusi simulasi (`./gradlew runAll`) pada lingkungan Windows Terminal (PowerShell) yang dibagi berdasarkan tahapan skenario pengujian agar mudah dibaca:

#### A. Skenario 1: Dataset Sintetis (1.000 Task)
| Inisialisasi & Eksekusi Simulasi | Hasil Metrik & Tabel Perbandingan |
| :---: | :---: |
| <img src="docs/images/terminal_sintetis_eksekusi.png" alt="Eksekusi Skenario Sintetis" width="460"><br><sub><em>Inisialisasi datacenter &amp; eksekusi PEFT</em></sub> | <img src="docs/images/terminal_sintetis_tabel.png" alt="Tabel Perbandingan Sintetis" width="460"><br><sub><em>Sampel task &amp; tabel komparasi metrik</em></sub> |

#### B. Skenario 2: Real Trace NASA iPSC SWF (1.000 Task)
| Inisialisasi & Eksekusi Simulasi | Hasil Metrik & Tabel Perbandingan |
| :---: | :---: |
| <img src="docs/images/terminal_nasa_eksekusi.png" alt="Eksekusi Skenario NASA iPSC" width="460"><br><sub><em>Inisialisasi trace SWF &amp; eksekusi PEFT</em></sub> | <img src="docs/images/terminal_nasa_tabel.png" alt="Tabel Perbandingan NASA iPSC" width="460"><br><sub><em>Sampel task &amp; tabel komparasi akhir</em></sub> |

### 6.2 Visualisasi Grafik Evaluasi Metrik Komparatif

| Makespan Skala Bertahap (100 - 1.000 Task) | Makespan Skala Besar (1.000 - 10.000 Task) |
| :---: | :---: |
| <img src="docs/images/grafik_makespan_sintetis_100_1000.png" alt="Grafik Makespan 100-1000" width="460"><br><sub><em>Makespan vs Task 100 - 1.000 (Sintetis)</em></sub> | <img src="docs/images/grafik_makespan_sintetis_1000_10000.png" alt="Grafik Makespan 1000-10000" width="460"><br><sub><em>Makespan vs Task 1.000 - 10.000 (Sintetis)</em></sub> |

| Makespan Real Trace NASA iPSC (100 - 1.000) | Eliminasi Waktu Antre NASA iPSC (100 - 1.000) |
| :---: | :---: |
| <img src="docs/images/grafik_makespan_nasa_100_1000.png" alt="Grafik Makespan NASA" width="460"><br><sub><em>Makespan NASA iPSC vs Task 100 - 1.000</em></sub> | <img src="docs/images/grafik_waiting_time_nasa_100_1000.png" alt="Grafik Waiting Time NASA" width="460"><br><sub><em>Eliminasi Waiting Time PEFT (0 detik)</em></sub> |

| Konsumsi Energi SPECpower (1.000 - 10.000 Task) | Pemerataan Beban Antar-VM (Std Dev) |
| :---: | :---: |
| <img src="docs/images/grafik_energi_specpower_1000_10000.png" alt="Grafik Energi" width="460"><br><sub><em>Konsumsi Energi SPECpower (kWh)</em></sub> | <img src="docs/images/grafik_load_balance_1000_10000.png" alt="Grafik Load Balance" width="460"><br><sub><em>Load Balance Imbalance (Std Dev Beban)</em></sub> |

| Evaluasi Tambahan: Makespan GoCJ (100 - 1.000) | Visualisasi Ringkasan Eksekusi |
| :---: | :---: |
| <img src="docs/images/grafik_makespan_gocj_100_1000.png" alt="Grafik GoCJ" width="460"><br><sub><em>Evaluasi Trace Tambahan (Google Cloud Jobs)</em></sub> | <img src="docs/images/grafik_makespan.png" alt="Grafik Makespan" width="460"><br><sub><em>Grafik komparasi makespan dan turnaround time</em></sub> |

---

## 7. Panduan Menjalankan Simulasi

Simulasi dikemas menggunakan Gradle Wrapper sehingga dependensi pustaka CloudSim Plus akan diunduh secara otomatis saat pertama kali dieksekusi. Prasyarat sistem yang dibutuhkan adalah **Java SE Development Kit (JDK) 21**.

### 7.1 Kloning Repository
```bash
git clone https://github.com/aryarefman/soka.git
cd soka
```

### 7.2 Menjalankan Seluruh Skenario Simulasi
Buka terminal pada direktori proyek, kemudian jalankan:
```powershell
.\gradlew.bat runAll
```
Perintah ini akan melakukan kompilasi, mengeksekusi simulasi untuk dataset Sintetis dan NASA iPSC pada seluruh algoritma (PEFT, FCFS, Round Robin), menampilkan tabel ringkasan metrik di konsol, serta menyimpan rekaman data ke direktori `results/`.

### 7.3 Menjalankan Algoritma Tertentu
Untuk menjalankan algoritma secara terpisah:
```powershell
# Menjalankan evaluasi PEFT
.\gradlew.bat runPEFT

# Menjalankan evaluasi FCFS
.\gradlew.bat runFCFS

# Menjalankan evaluasi Round Robin
.\gradlew.bat runRoundRobin
```

### 7.4 Struktur Berkas Hasil (CSV)
Seluruh metrik evaluasi tersimpan dalam format CSV pada direktori `results/`:
- `results/synthetic_results.csv` : Hasil eksperimen dataset sintetis.
- `results/nasa_ipsc_results.csv` : Hasil eksperimen dataset NASA iPSC.
- `results/all_results.csv`       : Rekapitulasi gabungan seluruh pengujian.

---

## 8. Struktur Direktori Repository

```
soka/
├── .gitignore                                 # Mengabaikan direktori build, cache, dan file lingkungan
├── build.gradle                               # Konfigurasi Gradle dan dependensi CloudSim Plus 8.0.0
├── settings.gradle
├── gradlew.bat                                # Skrip Gradle Wrapper untuk Windows
├── Tugas_3_Kelompok_8 (2).pdf                 # Dokumen usulan spesifikasi proyek Kelompok 8
│
├── Assets/
│   └── Lambang ITS PNG v1.png                 # Lambang resmi Institut Teknologi Sepuluh Nopember (ITS)
│
├── dataset/
│   ├── NASA-iPSC-1993-3.1-cln.swf             # Berkas workload trace SWF NASA iPSC (1.500 baris)
│   └── NASA-iPSC-1993-3.1-cln.swf.gz
│
├── docs/
│   └── images/                                # Dokumentasi tangkapan layar terminal dan grafik PNG
│       ├── terminal_sintetis_eksekusi.png
│       ├── terminal_sintetis_tabel.png
│       ├── terminal_nasa_eksekusi.png
│       ├── terminal_nasa_tabel.png
│       ├── grafik_makespan.png
│       └── grafik_energi.png
│
├── generate_swf.py                            # Skrip pendukung pembangkitan beban kerja
│
├── results/
│   ├── synthetic_results.csv                  # Hasil simulasi skenario sintetis
│   ├── nasa_ipsc_results.csv                  # Hasil simulasi skenario NASA iPSC
│   └── all_results.csv                        # Rekapitulasi perbandingan seluruh algoritma
│
└── src/main/
    ├── java/com/kelompok8/
    │   ├── DatacenterBuilder.java             # Builder 8 Host heterogen dan 30 VM (Best-Fit)
    │   ├── PeftBroker.java                    # Implementasi Heuristik PEFT (Earliest Finish Time)
    │   ├── FcfsBroker.java                    # Implementasi Baseline First Come First Served
    │   ├── RoundRobinBroker.java              # Implementasi Baseline Round Robin
    │   ├── SwfParser.java                     # Parser workload SWF dan generator dataset sintetis
    │   ├── MetricsCollector.java              # Perhitungan Makespan, Energi SPECpower, dan Utilisasi
    │   └── MainSimulation.java                # Titik masuk utama simulasi komparatif
    └── resources/
        └── logback.xml                        # Konfigurasi level logging CloudSim Plus
```

---

## 9. Referensi Pendukung

1. Arabnejad H, Barbosa JG. List Scheduling on Heterogeneous Distributed Systems: Evaluation and Improvements. *IEEE Transactions on Parallel and Distributed Systems*. 2014;25(12):3173–3182.
2. Beloglazov A, Buyya R. Optimal online deterministic algorithms and adaptive heuristics for energy and performance efficient dynamic consolidation of virtual machines in cloud data centers. *Concurrency and Computation: Practice and Experience*. 2012;24(13):1397–1420.
3. Arunarani AR, Manjula D, Sugumaran V. Task scheduling techniques in cloud computing: A literature survey. *Future Generation Computer Systems*. 2019;91:407–415.
4. Silva Filho MC, et al. CloudSim Plus: A modern Java 8 framework for modeling and simulation of cloud computing infrastructures and services. *Concurrency and Computation: Practice and Experience*. 2017.
5. Parallel Workloads Archive. NASA Ames iPSC/860 Workload Trace. Hebrew University of Jerusalem. https://www.cs.huji.ac.il/labs/parallel/workload/l_nasa_ipsc/.

---
<p align="center">
  <b>Kelompok 8 — Strategi Optimasi Komputasi Awan (SOKA)</b><br>
  Departemen Teknologi Informasi, Institut Teknologi Sepuluh Nopember (ITS)<br>
  Surabaya, Indonesia — 2026
</p>


---

## 10. Buku Saku & Kepatuhan Revisi Dosen
Seluruh jawaban, lokasi baris kode, dan bukti empiris terhadap catatan revisi Bu Henning telah dipetakan secara tuntas di: [`CATATAN_REVISI_BU_HENNING_DAN_MITIGASI.md`](CATATAN_REVISI_BU_HENNING_DAN_MITIGASI.md).
