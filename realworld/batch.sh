#!/usr/bin/env bash
# Eksperimen real-world berskala (jalankan dari folder realworld/ setelah setup.sh):
#   2 dataset x 5 skala (10-50 task) x 3 run, masing-masing PEFT, FCFS, RR.
# Setiap run disimpan di hasil/batch/{dataset}_{n}/run{r}/ beserta log terminalnya.
# Perkiraan durasi: ~1 jam. Hindari memakai laptop untuk pekerjaan berat selama batch berjalan.
set -euo pipefail
cd "$(dirname "$0")"

SCALES="${SCALES:-10 20 30 40 50}"

# Penjaga kebersihan data: kirim task uji 2.000 MI ke tiap VM sendirian. Bila ada VM yang berjalan di bawah
# 95% rating-nya, laptop sedang dipakai program lain dan hasil run tidak bisa dipercaya.
speed_check() {
  local ok=OK line=""
  for v in "1 1000" "2 1000" "3 2000" "4 2000"; do
    set -- $v
    local ex mips
    ex=$(curl -s -m 30 -X POST "http://localhost:3000$1/run" -H 'Content-Type: application/json' \
         -d '{"id": -2, "length": 2000, "pes": 1}' | sed -E 's/.*"exec": ?([0-9.eE-]+).*/\1/' || true)
    mips=$(awk -v e="$ex" 'BEGIN { if (e > 0) printf "%.0f", 2000 / e; else print 0 }')
    line="$line vm-$1=$mips"
    awk -v m="$mips" -v r="$2" 'BEGIN { exit !(m >= 0.95 * r) }' || ok=GAGAL
  done
  echo "$ok$line"
}
RUNS="${RUNS:-3}"
mkdir -p hasil/logs hasil/batch

echo "== Bukti infrastruktur -> hasil/logs/infrastruktur.txt =="
{
  echo "### kubectl get nodes -L host,tier"
  kubectl get nodes -L host,tier
  echo
  echo "### Batas CPU host (docker inspect NanoCpus / 1e9)"
  for c in soka-dc-worker soka-dc-worker2; do
    echo "$c : $(docker inspect -f '{{.HostConfig.NanoCpus}}' "$c" | awk '{print $1/1e9}') CPU"
  done
  echo
  echo "### kubectl get pods -o wide"
  kubectl get pods -o wide
  echo
  echo "### Spesifikasi VM (env & resources tiap pod)"
  kubectl get pods -l app=soka-vm -o custom-columns='VM:.metadata.name,NODE:.spec.nodeName,PES:.spec.containers[0].env[1].value,MIPS:.spec.containers[0].env[2].value,CPU_REQ:.spec.containers[0].resources.requests.cpu,CPU_LIM:.spec.containers[0].resources.limits.cpu'
} | tee hasil/logs/infrastruktur.txt

for ds in gocj synthetic; do
  for n in $SCALES; do
    for r in $(seq 1 "$RUNS"); do
      out="hasil/batch/${ds}_${n}/run${r}"
      if [ -f "$out/ringkasan_${ds}.csv" ]; then echo "-- lewati $out (sudah ada)"; continue; fi
      for try in 1 2 3 4 5; do
        mkdir -p "$out"
        echo "== $ds | $n task | run $r | percobaan $try =="
        pre=$(speed_check)
        for wait in $(seq 1 30); do       # tunggu laptop tenang dulu (maks. 30 menit) agar run tidak sia-sia
          [[ "$pre" == OK* ]] && break
          echo "   .. menunggu laptop tenang ($pre), cek lagi 60 s"
          sleep 60; pre=$(speed_check)
        done
        if [[ "$pre" != OK* ]]; then echo "   !! laptop tidak kunjung tenang (mis. tidak dicolok charger): run dilewati"; rm -rf "$out"; break; fi
        java broker/RealWorldBroker.java ALL "$ds" "$n" "$out" > "$out/log.txt"
        post=$(speed_check)
        echo "   cek kecepatan sebelum: $pre | sesudah: $post" | tee -a "$out/log.txt"
        if [[ "$pre" == OK* && "$post" == OK* ]]; then tail -n 12 "$out/log.txt"; break; fi
        echo "   !! laptop sedang sibuk (VM di bawah 95% rating): run dibuang, coba lagi 60 s"
        mkdir -p hasil/batch_terganggu
        mv "$out" "hasil/batch_terganggu/${ds}_${n}_run${r}_coba${try}_$(date +%H%M%S)"
        sleep 60
      done
      sleep 5   # jeda agar VM kembali idle
    done
  done
done
echo "== Batch selesai. Lanjutkan: python analisis/bandingkan_simulasi.py =="
