# Demo Real-World Heuristik: PEFT di Kubernetes

Implementasi nyata algoritma heuristik **PEFT** (beserta pembanding FCFS dan Round Robin) di atas cluster **Kubernetes**. Berbeda dengan simulasi CloudSim di folder induk, di sini task **benar-benar dieksekusi** di VM (pod) dan waktunya diukur dari jam dinding.

## Pemetaan ke syarat tugas

| Syarat dosen | Implementasi | Berkas |
| :--- | :--- | :--- |
| Platform cloud (Proxmox / Kubernetes / AWS / GCP) | Kubernetes (cluster `kind` di Docker Desktop) | `k8s/kind-cluster.yaml` |
| Minimal 1 datacenter | 1 cluster bernama `soka-dc` | `k8s/kind-cluster.yaml` |
| Minimal 2 host | 2 worker node: `host-1` (Standard, maks. 1 CPU) dan `host-2` (Premium, maks. 2 CPU) | `k8s/kind-cluster.yaml`, `setup.sh` |
| Minimal 2 VM per host | 4 pod, 2 per node, dikunci dengan `nodeSelector` | `k8s/vms.yaml` |
| Algoritma heuristik yang dibuat sebelumnya | PEFT dengan rumus EST/EET/EFT yang sama dengan `PeftBroker.java` | `broker/RealWorldBroker.java` |

## Arsitektur

```
Laptop (broker)                 Datacenter: cluster Kubernetes "soka-dc"
┌──────────────────────┐        ┌─────────────────────────────────────────────────┐
│ RealWorldBroker.java │        │ host-1 (Standard)        host-2 (Premium)       │
│  1. cek VM           │  HTTP  │ ┌──────────┐┌──────────┐ ┌──────────┐┌──────────┐│
│  2. kalibrasi MIPS   │──────► │ │ vm-1     ││ vm-2     │ │ vm-3     ││ vm-4     ││
│  3. jadwalkan (PEFT) │ :3000x │ │ 1 PE     ││ 2 PE     │ │ 2 PE     ││ 4 PE     ││
│  4. kirim task       │        │ │ 1000 MIPS││ 1000 MIPS│ │ 2000 MIPS││ 2000 MIPS││
│  5. hitung metrik    │ ◄───── │ └──────────┘└──────────┘ └──────────┘└──────────┘│
└──────────────────────┘ hasil  └─────────────────────────────────────────────────┘
```

| VM | Host | PE | MIPS per PE | CPU request / limit |
| :--- | :--- | :---: | :---: | :---: |
| vm-1 | host-1 Standard | 1 | 1.000 | 100m / 200m |
| vm-2 | host-1 Standard | 2 | 1.000 | 200m / 400m |
| vm-3 | host-2 Premium | 2 | 2.000 | 400m / 800m |
| vm-4 | host-2 Premium | 4 | 2.000 | 800m / 1600m |

Spesifikasi VM sengaja dibuat setara dengan simulasi: VM Standard 1.000 MIPS (seperti VM Small) dan VM Premium 2.000 MIPS (seperti VM Large).

## Alur program

1. **`worker/worker.py`** berjalan di setiap VM. Ia menerima task lewat `POST /run`, menunggu sampai core (PE) cukup (antrean FIFO, non-preemptive), lalu menjalankan beban CPU sungguhan sepanjang `length` MI di `pes` proses paralel. Kecepatan tiap PE dijaga sesuai `VM_MIPS`.
2. **`broker/RealWorldBroker.java`** berjalan di laptop:
   - **Cek VM**: memanggil `GET /info` ke keempat VM.
   - **Kalibrasi**: mengirim task uji 2.000 MI ke setiap VM. MIPS per PE dihitung dari waktu eksekusi nyata, dan nilai inilah yang dipakai PEFT.
   - **Penjadwalan**:
     - PEFT: urutkan task berdasarkan kedatangan (seri → yang terpanjang dulu). Untuk setiap VM yang PE-nya cukup, hitung `EST = max(kedatangan, waktu core ke-P bebas)`, `EET = panjang / MIPS`, dan `EFT = EST + EET`. Pilih VM dengan EFT terkecil.
     - FCFS: task diurutkan berdasarkan kedatangan, lalu dibagikan bergiliran ke VM yang PE-nya cukup.
     - RR: dibagikan bergiliran sesuai urutan task masuk.
   - **Eksekusi**: setiap task dikirim tepat pada waktu kedatangannya ke VM pilihan algoritma.
   - **Metrik**: makespan, turnaround, waiting, throughput, utilisasi VM, load balance, dan estimasi energi SPECpower.

## Menjalankan

Prasyarat: Docker Desktop menyala (di kiri bawah tertulis *Engine running*), `kind`, `kubectl`, dan Java 21. Jalankan dari Git Bash di folder `realworld/`:

```bash
bash setup.sh                                      # bangun datacenter + 4 VM (aman diulang)
kubectl get nodes -L host,tier                     # tunjukkan 2 host
kubectl get pods -o wide                           # tunjukkan 2 VM per host

java broker/RealWorldBroker.java ALL gocj 30       # ~1 menit: PEFT vs FCFS vs RR, dataset GoCJ
java broker/RealWorldBroker.java ALL synthetic 30  # ~3 menit: dataset sintetis 1/2/4 PE
java broker/RealWorldBroker.java PEFT synthetic 30 # hanya PEFT
```

Argumen: `[ALL|PEFT|FCFS|RR] [synthetic|gocj] [jumlah task]`. Hasil per task dan ringkasan disimpan di `hasil/`.

Perintah lain yang berguna saat demo:

```bash
kubectl logs vm-4                  # log VM
kubectl top pod                    # (butuh metrics-server) pemakaian CPU
kind delete cluster --name soka-dc # hapus datacenter setelah demo
```

## Dataset

- **Sintetis** (seed 42): komposisi sama dengan simulasi, yaitu 30% task 1 PE (500 – 2.000 MI), 40% task 2 PE (2.000 – 6.000 MI), dan 30% task 4 PE (6.000 – 12.000 MI). Kedatangan tersebar 0 – 15 detik. Panjangnya diperkecil dari simulasi agar demo selesai dalam hitungan menit.
- **GoCJ**: 30 job pertama dari berkas resmi `../dataset/gocj/GoCJ_Dataset_100.txt` (Hussain & Aleem, 2018). Panjangnya dibagi 50, semua tiba di t = 0, dan masing-masing butuh 1 PE.

## Hasil uji (30 task, Kubernetes di laptop)

| Dataset | Metrik | PEFT | FCFS | RR |
| :--- | :--- | ---: | ---: | ---: |
| GoCJ | Makespan (s) | **7,61** | 13,83 | 13,85 |
| GoCJ | Rata-rata waiting (s) | **1,75** | 2,79 | 2,38 |
| Sintetis | Makespan (s) | **47,84** | 49,60 | 49,43 |
| Sintetis | Rata-rata waiting (s) | **7,52** | 9,38 | 9,05 |

Angka bisa sedikit berbeda setiap run (± beberapa persen) karena ini eksekusi nyata, dan laptop juga menjalankan proses lain.

## Poin penjelasan saat demo

- **Mengapa selisih makespan di dataset sintetis kecil?** Hanya vm-4 yang punya 4 PE, sehingga kesembilan task 4 PE wajib dijalankan di vm-4 apa pun algoritmanya. vm-4 menjadi bottleneck (sekitar 180 detik-CPU di semua algoritma). Keunggulan PEFT terlihat dari waiting time dan turnaround yang lebih pendek, karena task 1 PE dan 2 PE dialihkan ke VM yang lebih cepat selesai.
- **Mengapa GoCJ menunjukkan selisih besar?** Semua job tiba bersamaan dengan panjang yang sangat bervariasi. FCFS dan RR membagi job sama rata, sehingga job besar ikut masuk ke VM 1.000 MIPS. PEFT menghitung EFT dan menaruh job besar di VM 2.000 MIPS.
- **Mengapa FCFS dan RR hampir sama?** Keduanya membagi task bergiliran per kebutuhan PE. Bedanya hanya urutan: FCFS mengurutkan berdasarkan waktu kedatangan, sedangkan RR memakai urutan task masuk.
- **Dari mana nilai MIPS untuk PEFT?** Dari kalibrasi nyata di langkah 2, bukan angka tetap di kode.
- **Bagaimana kecepatan VM dijaga?** Kubernetes menjamin jatah CPU tiap pod (`requests`/`limits`), lalu worker menjaga setiap PE agar tidak melebihi rating MIPS-nya. Tanpa pengaturan ini, kecepatan VM di satu laptop akan berubah-ubah mengikuti beban laptop.
- **Energi** adalah estimasi dengan model SPECpower yang sama dengan simulasi (host-1 = Tipe A, host-2 = Tipe D), dihitung dari waktu sibuk nyata. Energi tidak diukur dari daya listrik laptop.
