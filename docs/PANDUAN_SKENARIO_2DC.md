# Panduan Skenario 2 Datacenter (`2dc`)
**Kelompok 8 · SOKA 2026** · Algoritma PEFT, FCFS, Round Robin · CloudSim Plus 8.0.0

Repository ini menyediakan dua kasus infrastruktur dari kode yang sama. Skenario default adalah `3dc` (3 DC, 24 host, 30 VM). Dokumen ini menjelaskan skenario `2dc` yang diminta dosen sebagai kasus kedua.

---

## 1. Arsitektur

- **2 datacenter heterogen**: DC-1 Jakarta (Standard) dan DC-2 Bandung (Premium).
- **5 host per DC** (total 10 host), masing-masing **16 PE**.
- **20 VM per DC** (total 40 VM), **tepat 4 VM per host**, dan tidak ada host yang menganggur.

Konfigurasi lengkap ada di enum `Skenario.DUA_DC` (`src/main/java/com/kelompok8/Skenario.java`).

### Host

| Datacenter | Unit | Tipe | MIPS/core | PE | RAM | Daya idle / maks |
|---|---|---|---|---|---|---|
| DC-1: Jakarta | Host #1 – #3 | A | 1.000 | 16 | 32 GB | 93,7 W / 135,0 W |
| DC-1: Jakarta | Host #4 – #5 | B | 1.500 | 16 | 32 GB | 105,0 W / 175,0 W |
| DC-2: Bandung | Host #1 – #3 | C | 2.000 | 16 | 64 GB | 120,0 W / 225,0 W |
| DC-2: Bandung | Host #4 – #5 | D | 3.000 | 16 | 64 GB | 140,0 W / 300,0 W |

### VM

| Datacenter | Kelas | Jumlah | PE | RAM | MIPS/PE | Beban per host (4 VM) |
|---|---|---|---|---|---|---|
| DC-1: Jakarta | Medium | 20 | 2 | 2 GB | 1.000 | 8 / 16 PE, 8 / 32 GB |
| DC-2: Bandung | Large | 20 | 4 | 4 GB | 2.000 | 16 / 16 PE, 16 / 64 GB |

VM Large di DC-2 dapat menjalankan task 4 PE dari dataset sintetis dan NASA, sehingga semua task bisa dijadwalkan.

---

## 2. Bukti penempatan VM

Output asli `./gradlew testAllocation -Pskenario=2dc` (lihat `results/2dc/logs/testAllocation.txt`):

```text
  | DC-1: Jakarta (Standard)     | #1       | 16 PE x 1000   |   8192/ 32768 MB | 4 VM [0, 5, 10, 15]                    |
  | DC-1: Jakarta (Standard)     | #2       | 16 PE x 1000   |   8192/ 32768 MB | 4 VM [1, 6, 11, 16]                    |
  | DC-1: Jakarta (Standard)     | #3       | 16 PE x 1000   |   8192/ 32768 MB | 4 VM [2, 7, 12, 17]                    |
  | DC-1: Jakarta (Standard)     | #4       | 16 PE x 1500   |   8192/ 32768 MB | 4 VM [3, 8, 13, 18]                    |
  | DC-1: Jakarta (Standard)     | #5       | 16 PE x 1500   |   8192/ 32768 MB | 4 VM [4, 9, 14, 19]                    |
  | DC-2: Bandung (Premium)      | #1       | 16 PE x 2000   |  16384/ 65536 MB | 4 VM [20, 25, 30, 35]                  |
  | DC-2: Bandung (Premium)      | #2       | 16 PE x 2000   |  16384/ 65536 MB | 4 VM [21, 26, 31, 36]                  |
  | DC-2: Bandung (Premium)      | #3       | 16 PE x 2000   |  16384/ 65536 MB | 4 VM [22, 27, 32, 37]                  |
  | DC-2: Bandung (Premium)      | #4       | 16 PE x 3000   |  16384/ 65536 MB | 4 VM [23, 28, 33, 38]                  |
  | DC-2: Bandung (Premium)      | #5       | 16 PE x 3000   |  16384/ 65536 MB | 4 VM [24, 29, 34, 39]                  |
  Status: 10 dari 10 host aktif, 0 host menganggur. Pola penempatan: 10 host x 4 VM
```

---

## 3. Hasil utama (1.000 task)

| Dataset | Metrik | PEFT | FCFS | Round Robin |
|---|---|---|---|---|
| Sintetis | Makespan (s) | **2.172,86** | 3.094,28 | 2.476,28 |
| Sintetis | Energi (kWh) | **1,225** | 1,529 | 1,335 |
| NASA iPSC/860 | Makespan (s) | 587.101,10 | 587.101,10 | 587.101,10 |
| NASA iPSC/860 | Waiting time (s) | **0,00** | 7,09 | 7,09 |
| NASA iPSC/860 | Turnaround (s) | **312,32** | 332,09 | 332,09 |

Tabel lengkap (termasuk 100 – 10.000 task dan GoCJ) ada di README §6 dan `results/2dc/`.

---

## 4. Perintah untuk demo

```powershell
# Bukti penempatan VM ke host (beberapa detik)
.\gradlew.bat testAllocation -Pskenario=2dc

# Simulasi utama PEFT saja
.\gradlew.bat runPEFT -Pskenario=2dc

# PEFT, FCFS, Round Robin untuk sintetis & NASA
.\gradlew.bat runAll -Pskenario=2dc

# Batch 3x run + grafik
.\gradlew.bat runBatch generateCharts -Pskenario=2dc
```

Tanpa `-Pskenario`, semua perintah berjalan pada skenario `3dc`.
