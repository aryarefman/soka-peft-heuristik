"""
Worker VM - dijalankan di dalam setiap pod Kubernetes (1 pod = 1 VM).

Tugasnya hanya satu: menerima task dari broker lalu MENGEKSEKUSINYA SUNGGUHAN
(memakai CPU), bukan mensimulasikan.

Endpoint HTTP (port 8080):
  GET  /info -> {"vm": "vm-1", "host": "...", "pes": 1, "mips": 1000}
  POST /run  -> body {"id": 7, "length": 3000, "pes": 2}
                balasan {"id": 7, "vm": "vm-1", "host": "...", "wait": 0.8, "exec": 3.1}

Aturan eksekusi (sama dengan CloudletSchedulerSpaceShared di CloudSim):
  - Task butuh `pes` core. Jika core VM belum cukup, task MENUNGGU (antrean FIFO).
  - Setelah dapat core, task berjalan sampai selesai (non-preemptive).
  - Task dengan `pes` = k dijalankan sebagai k proses paralel, masing-masing
    mengerjakan `length` MI (panjang cloudlet di CloudSim juga per PE).
  - Setiap PE adalah vCPU berkecepatan tetap VM_MIPS (MI/detik), seperti spesifikasi VM
    di CloudSim. Proses PE mengerjakan potongan kecil lalu menunggu bila lebih cepat
    dari ratingnya, sehingga kecepatan VM stabil walau laptop sedang sibuk.
    Jatah CPU fisiknya dijamin oleh requests/limits pod Kubernetes (lihat k8s/vms.yaml).
"""
import json
import os
import threading
import time
from collections import deque
from multiprocessing import Pool
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

VM_NAME = os.environ["VM_NAME"]
VM_PES = int(os.environ["VM_PES"])
NODE_NAME = os.environ.get("NODE_NAME", "?")
VM_MIPS = float(os.environ["VM_MIPS"])   # kecepatan per PE (MI/detik)
# Jumlah iterasi loop untuk 1 MI. 1440 -> 1 core penuh ~ 10.000 MI/detik,
# sehingga PE 1.000 MIPS memakai ~0,1 core dan PE 2.000 MIPS ~0,2 core.
ITER_PER_MI = int(os.environ.get("ITER_PER_MI", "1440"))
CHUNK_MI = 20                            # ukuran potongan kerja (~2 ms CPU)


def burn(length_mi):
    """Beban CPU sungguhan sepanjang `length_mi` MI pada kecepatan maksimum VM_MIPS."""
    start = time.perf_counter()
    done, x = 0, 1
    while done < length_mi:
        step = min(CHUNK_MI, length_mi - done)
        for _ in range(step * ITER_PER_MI):
            x = (x * 1103515245 + 12345) & 0xFFFFFFFF
        done += step
        ahead = done / VM_MIPS - (time.perf_counter() - start)
        if ahead > 0:                    # lebih cepat dari rating vCPU -> tunggu
            time.sleep(ahead)
    return x


class PeGate:
    """Antrean FIFO untuk core (PE) VM: task baru jalan bila gilirannya tiba DAN core cukup."""

    def __init__(self, total):
        self.free = total
        self.queue = deque()
        self.cv = threading.Condition()

    def acquire(self, k):
        ticket = object()
        with self.cv:
            self.queue.append(ticket)
            while self.queue[0] is not ticket or self.free < k:
                self.cv.wait()
            self.queue.popleft()
            self.free -= k
            self.cv.notify_all()

    def release(self, k):
        with self.cv:
            self.free += k
            self.cv.notify_all()


gate = PeGate(VM_PES)
pool = None  # dibuat di main() agar proses anak tidak ikut mewarisi thread server


class Handler(BaseHTTPRequestHandler):
    def _send(self, obj):
        body = json.dumps(obj).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/info":
            self._send({"vm": VM_NAME, "host": NODE_NAME, "pes": VM_PES, "mips": VM_MIPS})
        else:
            self.send_error(404)

    def do_POST(self):
        if self.path != "/run":
            self.send_error(404)
            return
        task = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        pes = int(task["pes"])
        if pes > VM_PES:
            self.send_error(400, f"task butuh {pes} PE, VM hanya {VM_PES} PE")
            return

        received = time.perf_counter()
        gate.acquire(pes)                                   # 1. tunggu core cukup (antrean)
        started = time.perf_counter()
        try:
            jobs = [pool.apply_async(burn, (int(task["length"]),)) for _ in range(pes)]
            for j in jobs:                                  # 2. eksekusi nyata di `pes` core
                j.get()
        finally:
            gate.release(pes)                               # 3. lepas core
        finished = time.perf_counter()

        self._send({"id": task["id"], "vm": VM_NAME, "host": NODE_NAME,
                    "wait": started - received, "exec": finished - started})

    def log_message(self, fmt, *args):
        pass


def main():
    global pool
    pool = Pool(processes=VM_PES)  # 1 proses per PE, semuanya dibuat sejak awal
    print(f"[{VM_NAME}] siap di host {NODE_NAME}: {VM_PES} PE x {VM_MIPS:.0f} MIPS", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()


if __name__ == "__main__":
    main()
