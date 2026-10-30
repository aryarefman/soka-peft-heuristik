"""
Perbandingan SIMULASI vs REAL-WORLD untuk run Kubernetes Kelompok 8.

Simulasi di sini adalah "kembaran digital" dari run nyata: task, waktu kedatangan, dan
pemetaan task -> VM diambil persis dari hasil/task_{dataset}_{algo}.csv, lalu dieksekusi
ulang dengan aturan CloudSim Plus (CloudletSchedulerSpaceShared):
  - kecepatan PE = rating nominal VM (1.000 / 2.000 MIPS), tanpa overhead apa pun,
  - task tiba tepat di detik kedatangannya (tanpa latensi jaringan),
  - antrean per VM non-preemptive; task di antrean dimulai begitu PE cukup
    (first-fit, seperti moveNextCloudletsFromWaitingToExecList di CloudSim Plus).
Varian kedua (FIFO ketat) meniru antrean worker.py agar efek head-of-line blocking terukur.

Jalankan dari folder realworld/:  python analisis/bandingkan_simulasi.py
Keluaran: hasil/perbandingan_sim_vs_real.csv, hasil/dekomposisi_overhead.csv, grafik/*.png
"""
import csv
import math
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager

ROOT = Path(__file__).resolve().parent.parent
HASIL, GRAFIK = ROOT / "hasil", ROOT / "grafik"
DATASETS, ALGOS = ["gocj", "synthetic"], ["PEFT", "FCFS", "RR"]
NAMA_DS = {"gocj": "GoCJ", "synthetic": "Sintetis"}

VMS = {"vm-1": ("host-1", 1, 1000.0), "vm-2": ("host-1", 2, 1000.0),
       "vm-3": ("host-2", 2, 2000.0), "vm-4": ("host-2", 4, 2000.0)}
HOST_POWER = {"host-1": (93.7, 135.0), "host-2": (140.0, 300.0)}  # sama dengan RealWorldBroker.java

# ------------------------------------------------------------------ gaya grafik
for f in (ROOT.parent / "fonts").glob("Poppins-*.ttf"):
    font_manager.fontManager.addfont(str(f))
plt.rcParams.update({
    "font.family": "Poppins", "font.size": 10, "axes.titlesize": 12, "axes.titleweight": "semibold",
    "axes.edgecolor": "#c9c8c3", "axes.labelcolor": "#52514e", "xtick.color": "#52514e",
    "ytick.color": "#52514e", "axes.spines.top": False, "axes.spines.right": False,
    "axes.grid": True, "axes.grid.axis": "y", "grid.color": "#e8e7e3", "grid.linewidth": 0.8,
    "axes.axisbelow": True, "figure.facecolor": "#fcfcfb", "axes.facecolor": "#fcfcfb",
    "legend.frameon": False, "savefig.dpi": 160, "savefig.bbox": "tight"})
SIM, REAL, FIFO = "#2a78d6", "#eb6834", "#1baf7a"   # slot kategorikal 1-3
INK, MUTED = "#0b0b0b", "#8a8984"


# ------------------------------------------------------------------ data
def load(dataset, algo, folder=HASIL):
    with open(folder / f"task_{dataset}_{algo}.csv", newline="") as fh:
        rows = list(csv.DictReader(fh))
    return [{"id": int(r["TaskId"]), "len": int(r["LengthMI"]), "pes": int(r["Pes"]),
             "arr": float(r["Arrival(s)"]), "vm": r["Vm"], "host": r["Host"],
             "sent": float(r["Sent(s)"]), "wait": float(r["Wait(s)"]),
             "exec": float(r["Exec(s)"]), "done": float(r["Finish(s)"])} for r in rows]


def submit_order(algo, tasks):
    """Urutan submit broker; menentukan urutan antrean bila kedatangan seri."""
    if algo == "PEFT":
        return sorted(tasks, key=lambda t: (t["arr"], -t["len"]))
    return sorted(tasks, key=lambda t: (t["arr"], t["id"]))


def simulate(algo, tasks, strict_fifo=False):
    """Eksekusi ulang ideal per VM (event-driven, space-shared, non-preemptive)."""
    out = {}
    for vm, (_, pes, mips) in VMS.items():
        queue = [t for t in submit_order(algo, tasks) if t["vm"] == vm]
        pending, waiting, running, free, now = list(queue), [], [], pes, 0.0
        while pending or waiting or running:
            nxt = min([p["arr"] for p in pending[:1]] + [r[0] for r in running] or [now])
            now = max(now, nxt)
            for r in [r for r in running if r[0] <= now + 1e-12]:
                running.remove(r)
                free += r[1]["pes"]
            while pending and pending[0]["arr"] <= now + 1e-12:
                waiting.append(pending.pop(0))
            i = 0
            while i < len(waiting):
                t = waiting[i]
                if t["pes"] <= free:
                    waiting.pop(i)
                    free -= t["pes"]
                    fin = now + t["len"] / mips
                    out[t["id"]] = {"wait": now - t["arr"], "exec": t["len"] / mips, "done": fin, "sent": t["arr"]}
                    running.append((fin, t))
                elif strict_fifo:
                    break
                else:
                    i += 1
    return [{**t, **out[t["id"]]} for t in tasks]


def metrics(tasks):
    """Rumus sama persis dengan RealWorldBroker.metrics()."""
    mk = max(t["done"] for t in tasks)
    util = sum(sum(t["exec"] * t["pes"] for t in tasks if t["vm"] == vm) / (pes * mk) * 100
               for vm, (_, pes, _) in VMS.items()) / len(VMS)
    mean = len(tasks) / len(VMS)
    sd = math.sqrt(sum((sum(t["vm"] == vm for t in tasks) - mean) ** 2 for vm in VMS) / len(VMS))
    joule = 0.0
    for host, (pidle, pmax) in HOST_POWER.items():
        host_pes = sum(p for h, p, _ in VMS.values() if h == host)
        busy = min(mk, sum(t["exec"] * t["pes"] for t in tasks if t["host"] == host) / host_pes)
        joule += pmax * busy + pidle * (mk - busy)
    return {"Makespan (s)": mk,
            "Rata-rata turnaround (s)": sum(t["done"] - t["sent"] for t in tasks) / len(tasks),
            "Rata-rata waiting (s)": sum(t["wait"] for t in tasks) / len(tasks),
            "Throughput (task/s)": len(tasks) / mk,
            "Rata-rata utilisasi VM (%)": util,
            "Load balance (SD task/VM)": sd,
            "Estimasi energi (Wh)": joule / 3600}


# ------------------------------------------------------------------ hitung
real, sim, fifo, raw = {}, {}, {}, {}
for ds in DATASETS:
    for al in ALGOS:
        tasks = load(ds, al)
        raw[ds, al] = tasks
        real[ds, al] = metrics(tasks)
        sim[ds, al] = metrics(simulate(al, tasks))
        fifo[ds, al] = metrics(simulate(al, tasks, strict_fifo=True))

with open(HASIL / "perbandingan_sim_vs_real.csv", "w", newline="") as fh:
    w = csv.writer(fh)
    w.writerow(["Dataset", "Algoritma", "Metrik", "Simulasi", "Simulasi_FIFO_ketat", "Real", "Selisih_Real_vs_Sim_%"])
    for ds in DATASETS:
        for al in ALGOS:
            for k in real[ds, al]:
                s, r = sim[ds, al][k], real[ds, al][k]
                w.writerow([ds, al, k, f"{s:.4f}", f"{fifo[ds, al][k]:.4f}", f"{r:.4f}",
                            f"{(r - s) / s * 100:.2f}" if s else ""])

# dekomposisi waktu nyata per task: done = arrival + dispatch + wait + exec + http
decomp = {}
with open(HASIL / "dekomposisi_overhead.csv", "w", newline="") as fh:
    w = csv.writer(fh)
    w.writerow(["Dataset", "Algoritma", "Dispatch_rata2_ms", "HTTP_rata2_ms", "Exec_ideal_total_s",
                "Exec_real_total_s", "Inflasi_exec_%", "Wait_sim_rata2_s", "Wait_real_rata2_s"])
    for ds in DATASETS:
        for al in ALGOS:
            ts, ss = raw[ds, al], simulate(al, raw[ds, al])
            dispatch = sum(t["sent"] - t["arr"] for t in ts) / len(ts) * 1000
            http = sum(t["done"] - t["sent"] - t["wait"] - t["exec"] for t in ts) / len(ts) * 1000
            ideal = sum(t["len"] / VMS[t["vm"]][2] for t in ts)
            ex = sum(t["exec"] for t in ts)
            d = (dispatch, http, ideal, ex, (ex - ideal) / ideal * 100,
                 sum(t["wait"] for t in ss) / len(ss), sum(t["wait"] for t in ts) / len(ts))
            decomp[ds, al] = d
            w.writerow([ds, al] + [f"{v:.3f}" for v in d])


# ------------------------------------------------------------------ grafik
def label_bars(ax, bars, fmt="{:.2f}"):
    for b in bars:
        ax.annotate(fmt.format(b.get_height()), (b.get_x() + b.get_width() / 2, b.get_height()),
                    xytext=(0, 3), textcoords="offset points", ha="center", va="bottom", fontsize=8, color=INK)


def grouped(metric, fname, title, ylabel, fmt="{:.2f}"):
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.2))
    for ax, ds in zip(axes, DATASETS):
        x = range(len(ALGOS))
        wd = 0.38
        b1 = ax.bar([i - wd / 2 - 0.01 for i in x], [sim[ds, a][metric] for a in ALGOS], wd, color=SIM, label="Simulasi (CloudSim ideal)")
        b2 = ax.bar([i + wd / 2 + 0.01 for i in x], [real[ds, a][metric] for a in ALGOS], wd, color=REAL, label="Real-world (Kubernetes)")
        label_bars(ax, b1, fmt)
        label_bars(ax, b2, fmt)
        ax.set_xticks(list(x), ALGOS)
        ax.set_title(f"{NAMA_DS[ds]} · 30 task")
        ax.set_ylabel(ylabel)
        ax.margins(y=0.15)
    h, l = axes[0].get_legend_handles_labels()
    fig.legend(h, l, loc="lower center", ncol=2, fontsize=9, bbox_to_anchor=(0.5, -0.04))
    fig.suptitle(title, fontsize=13, fontweight="semibold", color=INK, x=0.06, ha="left")
    fig.tight_layout()
    fig.savefig(GRAFIK / fname)
    plt.close(fig)


def improvement_chart():
    """Keunggulan PEFT vs FCFS: simulasi vs real, per metrik."""
    keys = [("Makespan (s)", "Makespan"), ("Rata-rata turnaround (s)", "Turnaround"),
            ("Rata-rata waiting (s)", "Waiting"), ("Estimasi energi (Wh)", "Energi")]
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.2), sharey=True)
    for ax, ds in zip(axes, DATASETS):
        imp = lambda src, k: (src[ds, "FCFS"][k] - src[ds, "PEFT"][k]) / src[ds, "FCFS"][k] * 100
        x = range(len(keys))
        wd = 0.38
        b1 = ax.bar([i - wd / 2 - 0.01 for i in x], [imp(sim, k) for k, _ in keys], wd, color=SIM, label="Simulasi")
        b2 = ax.bar([i + wd / 2 + 0.01 for i in x], [imp(real, k) for k, _ in keys], wd, color=REAL, label="Real-world")
        label_bars(ax, b1, "{:.1f}%")
        label_bars(ax, b2, "{:.1f}%")
        ax.axhline(0, color=MUTED, linewidth=0.8)
        ax.set_xticks(list(x), [n for _, n in keys])
        ax.set_title(f"{NAMA_DS[ds]} · 30 task")
        ax.margins(y=0.15)
    axes[0].set_ylabel("Perbaikan PEFT vs FCFS (%), positif = lebih baik")
    axes[0].legend(loc="upper right", fontsize=9)
    fig.suptitle("Seberapa besar keunggulan PEFT bertahan dari simulasi ke dunia nyata?",
                 fontsize=13, fontweight="semibold", color=INK, x=0.06, ha="left")
    fig.tight_layout()
    fig.savefig(GRAFIK / "grafik_keunggulan_peft_sim_vs_real.png")
    plt.close(fig)


def finish_scatter():
    """Waktu selesai tiap task: prediksi simulasi (x) vs ukuran nyata (y)."""
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.6))
    for ax, ds in zip(axes, DATASETS):
        ts = raw[ds, "PEFT"]
        ss = {t["id"]: t for t in simulate("PEFT", ts)}
        xs = [ss[t["id"]]["done"] for t in ts]
        ys = [t["done"] for t in ts]
        lim = max(xs + ys) * 1.05
        ax.plot([0, lim], [0, lim], color=MUTED, linewidth=1, linestyle="--", label="real = simulasi")
        ax.scatter(xs, ys, s=36, color=REAL, edgecolor="#fcfcfb", linewidth=1.5, zorder=3, label="task (PEFT)")
        ax.set_xlim(0, lim)
        ax.set_ylim(0, lim)
        ax.set_xlabel("Waktu selesai di simulasi (s)")
        ax.set_ylabel("Waktu selesai nyata (s)")
        ax.grid(axis="x", color="#e8e7e3", linewidth=0.8)
        ax.set_title(f"{NAMA_DS[ds]} · PEFT")
    axes[0].legend(loc="upper left", fontsize=9)
    fig.suptitle("Waktu selesai per task (PEFT): titik di atas garis = selesai lebih lambat dari simulasi",
                 fontsize=13, fontweight="semibold", color=INK, x=0.06, ha="left")
    fig.tight_layout()
    fig.savefig(GRAFIK / "grafik_waktu_selesai_per_task.png")
    plt.close(fig)


def gap_breakdown():
    """Dari mana selisih makespan berasal: simulasi first-fit -> FIFO ketat -> real."""
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.2))
    for ax, ds in zip(axes, DATASETS):
        x = range(len(ALGOS))
        wd = 0.26
        k = "Makespan (s)"
        bars = [(sim, SIM, "Simulasi (first-fit, CloudSim)"),
                (fifo, FIFO, "Simulasi FIFO ketat (aturan worker)"),
                (real, REAL, "Real-world")]
        for j, (src, c, lab) in enumerate(bars):
            b = ax.bar([i + (j - 1) * (wd + 0.02) for i in x], [src[ds, a][k] for a in ALGOS], wd, color=c, label=lab)
            label_bars(ax, b)
        ax.set_xticks(list(x), ALGOS)
        ax.set_title(f"{NAMA_DS[ds]} · makespan (s)")
        ax.margins(y=0.15)
    h, l = axes[0].get_legend_handles_labels()
    fig.legend(h, l, loc="lower center", ncol=3, fontsize=9, bbox_to_anchor=(0.5, -0.04))
    fig.suptitle("Memisahkan efek aturan antrean dari overhead eksekusi nyata",
                 fontsize=13, fontweight="semibold", color=INK, x=0.06, ha="left")
    fig.tight_layout()
    fig.savefig(GRAFIK / "grafik_dekomposisi_makespan.png")
    plt.close(fig)


def vm_load():
    """Detik-CPU per VM: simulasi vs real (PEFT & FCFS)."""
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.2))
    vms = list(VMS)
    for ax, ds in zip(axes, DATASETS):
        wd = 0.19
        series = []
        for al in ("PEFT", "FCFS"):
            ts = raw[ds, al]
            series.append((f"{al} simulasi", SIM if al == "PEFT" else "#6250d6",
                           [sum(t["len"] / VMS[v][2] * t["pes"] for t in ts if t["vm"] == v) for v in vms]))
            series.append((f"{al} real", REAL if al == "PEFT" else "#eda100",
                           [sum(t["exec"] * t["pes"] for t in ts if t["vm"] == v) for v in vms]))
        for j, (lab, c, vals) in enumerate(series):
            ax.bar([i + (j - 1.5) * (wd + 0.015) for i in range(len(vms))], vals, wd, color=c, label=lab)
        ax.set_xticks(range(len(vms)), [f"{v}\n{VMS[v][1]} PE · {VMS[v][2]:.0f} MIPS" for v in vms], fontsize=8.5)
        ax.set_title(f"{NAMA_DS[ds]} · beban per VM (detik-CPU)")
    axes[0].legend(loc="upper left", fontsize=8.5, ncol=2)
    fig.suptitle("Beban per VM: host-1 sesuai simulasi, host-2 membengkak saat dipadati",
                 fontsize=13, fontweight="semibold", color=INK, x=0.06, ha="left")
    fig.tight_layout()
    fig.savefig(GRAFIK / "grafik_beban_per_vm.png")
    plt.close(fig)


def completion_curve():
    """Jumlah task selesai terhadap waktu: simulasi (putus-putus) vs real (utuh)."""
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.4))
    for ax, ds in zip(axes, DATASETS):
        for al, c in (("PEFT", SIM), ("FCFS", REAL)):
            ts = raw[ds, al]
            for src, ls, lab in ((simulate(al, ts), "--", "simulasi"), (ts, "-", "real")):
                d = sorted(t["done"] for t in src)
                ax.step([0] + d, list(range(len(d) + 1)), where="post", color=c, linestyle=ls,
                        linewidth=2, label=f"{al} {lab}")
        ax.set_xlabel("Waktu sejak eksperimen dimulai (s)")
        ax.set_ylabel("Task selesai")
        ax.set_title(f"{NAMA_DS[ds]} · 30 task")
        ax.set_xlim(left=0)
        ax.set_ylim(0, 31)
    h, l = axes[0].get_legend_handles_labels()
    fig.legend(h, l, loc="lower center", ncol=4, fontsize=9, bbox_to_anchor=(0.5, -0.05))
    fig.suptitle("Kurva penyelesaian task: semakin cepat naik ke 30, semakin baik",
                 fontsize=13, fontweight="semibold", color=INK, x=0.06, ha="left")
    fig.tight_layout()
    fig.savefig(GRAFIK / "grafik_kurva_penyelesaian.png")
    plt.close(fig)


GRAFIK.mkdir(exist_ok=True)
completion_curve()
grouped("Makespan (s)", "grafik_makespan_sim_vs_real.png", "Makespan: simulasi vs real-world", "Makespan (s)")
grouped("Rata-rata waiting (s)", "grafik_waiting_sim_vs_real.png", "Rata-rata waiting time: simulasi vs real-world", "Waiting (s)")
grouped("Rata-rata turnaround (s)", "grafik_turnaround_sim_vs_real.png", "Rata-rata turnaround time: simulasi vs real-world", "Turnaround (s)")
grouped("Estimasi energi (Wh)", "grafik_energi_sim_vs_real.png", "Estimasi energi SPECpower: simulasi vs real-world", "Energi (Wh)", "{:.3f}")
improvement_chart()
finish_scatter()
gap_breakdown()
vm_load()

# ------------------------------------------------------------------ batch berskala (hasil/batch/, dari batch.sh)
BATCH = HASIL / "batch"
KEYS = ["Makespan (s)", "Rata-rata turnaround (s)", "Rata-rata waiting (s)", "Throughput (task/s)",
        "Rata-rata utilisasi VM (%)", "Load balance (SD task/VM)", "Estimasi energi (Wh)"]
ALGO_COLOR = {"PEFT": SIM, "FCFS": REAL, "RR": FIFO}


def mean_sd(xs):
    m = sum(xs) / len(xs)
    return m, math.sqrt(sum((x - m) ** 2 for x in xs) / len(xs))


def inflation(tasks, host):
    ts = [t for t in tasks if t["host"] == host]
    ideal = sum(t["len"] / VMS[t["vm"]][2] for t in ts)
    return (sum(t["exec"] for t in ts) - ideal) / ideal * 100 if ideal else 0.0


def batch_analysis():
    """Agregasi run per skala: real (rata-rata, SD) vs kembaran simulasi tiap run."""
    agg = {}
    for ds in DATASETS:
        for folder in sorted(BATCH.glob(f"{ds}_*"), key=lambda p: int(p.name.split("_")[1])):
            n = int(folder.name.split("_")[1])
            runs = sorted(r for r in folder.glob("run*") if (r / f"ringkasan_{ds}.csv").exists())
            if not runs:
                continue
            for al in ALGOS:
                rs = [load(ds, al, r) for r in runs]
                rm = [metrics(t) for t in rs]
                sm = [metrics(simulate(al, t)) for t in rs]
                fm = [metrics(simulate(al, t, strict_fifo=True)) for t in rs]
                agg[ds, n, al] = {
                    "runs": len(runs),
                    "real": {k: mean_sd([m[k] for m in rm]) for k in KEYS},
                    "sim": {k: mean_sd([m[k] for m in sm]) for k in KEYS},
                    "fifo": {k: mean_sd([m[k] for m in fm]) for k in KEYS},
                    "infl1": mean_sd([inflation(t, "host-1") for t in rs]),
                    "infl2": mean_sd([inflation(t, "host-2") for t in rs]),
                    "cpu2": mean_sd([sum(x["exec"] * x["pes"] for x in t if x["host"] == "host-2") for t in rs]),
                    "http": mean_sd([sum(x["done"] - x["sent"] - x["wait"] - x["exec"] for x in t) / len(t) * 1000
                                     for t in rs]),
                }
    if not agg:
        return None
    with open(HASIL / "batch_sim_vs_real.csv", "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["Dataset", "Task", "Algoritma", "Run", "Metrik", "Sim_mean", "SimFIFO_mean", "Real_mean",
                    "Real_SD", "Selisih_%"])
        for (ds, n, al), a in sorted(agg.items()):
            for k in KEYS:
                sm, rm = a["sim"][k][0], a["real"][k]
                w.writerow([ds, n, al, a["runs"], k, f"{sm:.4f}", f"{a['fifo'][k][0]:.4f}", f"{rm[0]:.4f}",
                            f"{rm[1]:.4f}", f"{(rm[0] - sm) / sm * 100:.2f}" if sm else ""])
    with open(HASIL / "batch_host_inflasi.csv", "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["Dataset", "Task", "Algoritma", "Inflasi_host1_%", "Inflasi_host2_%", "CPUdetik_host2", "HTTP_ms"])
        for (ds, n, al), a in sorted(agg.items()):
            w.writerow([ds, n, al] + [f"{a[c][0]:.2f}" for c in ("infl1", "infl2", "cpu2", "http")])
    return agg


def batch_small_multiples(agg, key, fname, title, ylabel):
    """Baris = dataset, kolom = algoritma; tiap panel simulasi (putus-putus) vs real (utuh, +/- SD)."""
    fig, axes = plt.subplots(2, 3, figsize=(12, 6.8), sharex="row")
    for i, ds in enumerate(DATASETS):
        ns = sorted({n for d, n, _ in agg if d == ds})
        for j, al in enumerate(ALGOS):
            ax = axes[i][j]
            ax.plot(ns, [agg[ds, n, al]["sim"][key][0] for n in ns], color=SIM, linestyle="--", linewidth=2,
                    marker="o", markersize=5, label="Simulasi")
            ax.errorbar(ns, [agg[ds, n, al]["real"][key][0] for n in ns],
                        yerr=[agg[ds, n, al]["real"][key][1] for n in ns], color=REAL, linewidth=2, marker="o",
                        markersize=5, capsize=3, label="Real-world (rata-rata ± SD)")
            ax.set_title(f"{NAMA_DS[ds]} · {al}", fontsize=10.5)
            ax.set_xticks(ns)
            ax.set_ylim(bottom=0)
            if j == 0:
                ax.set_ylabel(ylabel)
            ax.set_xlabel("Jumlah task")
    h, l = axes[0][0].get_legend_handles_labels()
    fig.legend(h, l, loc="lower center", ncol=2, fontsize=9, bbox_to_anchor=(0.5, -0.03))
    fig.suptitle(title, fontsize=13, fontweight="semibold", color=INK, x=0.06, ha="left")
    fig.tight_layout()
    fig.savefig(GRAFIK / fname)
    plt.close(fig)


def batch_lines(agg, value, fname, title, ylabel):
    """Satu garis per algoritma terhadap jumlah task, panel per dataset."""
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.3))
    for ax, ds in zip(axes, DATASETS):
        ns = sorted({n for d, n, _ in agg if d == ds})
        for al in ALGOS:
            ax.plot(ns, [value(agg[ds, n, al]) for n in ns], color=ALGO_COLOR[al], linewidth=2, marker="o",
                    markersize=5, label=al)
        ax.axhline(0, color=MUTED, linewidth=0.8)
        ax.set_xticks(ns)
        ax.set_xlabel("Jumlah task")
        ax.set_title(NAMA_DS[ds])
    axes[0].set_ylabel(ylabel)
    h, l = axes[0].get_legend_handles_labels()
    fig.legend(h, l, loc="lower center", ncol=3, fontsize=9, bbox_to_anchor=(0.5, -0.05))
    fig.suptitle(title, fontsize=13, fontweight="semibold", color=INK, x=0.06, ha="left")
    fig.tight_layout()
    fig.savefig(GRAFIK / fname)
    plt.close(fig)


def improvement(a_peft, a_fcfs, src, key):
    f_, p_ = a_fcfs[src][key][0], a_peft[src][key][0]
    return (f_ - p_) / f_ * 100


def batch_advantage(agg):
    """Perbaikan makespan & waiting PEFT vs FCFS terhadap skala: simulasi vs real."""
    fig, axes = plt.subplots(2, 2, figsize=(11, 7), sharex="col")
    for col, ds in enumerate(DATASETS):
        ns = sorted({n for d, n, _ in agg if d == ds})
        for row, (key, nama) in enumerate((("Makespan (s)", "makespan"), ("Rata-rata waiting (s)", "waiting"))):
            ax = axes[row][col]
            for src, c, ls, lab in (("sim", SIM, "--", "Simulasi"), ("real", REAL, "-", "Real-world")):
                ax.plot(ns, [improvement(agg[ds, n, "PEFT"], agg[ds, n, "FCFS"], src, key) for n in ns], color=c,
                        linestyle=ls, linewidth=2, marker="o", markersize=5, label=lab)
            ax.axhline(0, color=MUTED, linewidth=0.8)
            ax.set_xticks(ns)
            ax.set_title(f"{NAMA_DS[ds]} · {nama}", fontsize=10.5)
            if col == 0:
                ax.set_ylabel("Perbaikan PEFT vs FCFS (%)")
            if row == 1:
                ax.set_xlabel("Jumlah task")
    h, l = axes[0][0].get_legend_handles_labels()
    fig.legend(h, l, loc="lower center", ncol=2, fontsize=9, bbox_to_anchor=(0.5, -0.03))
    fig.suptitle("Keunggulan PEFT terhadap FCFS di setiap skala: simulasi vs real-world",
                 fontsize=13, fontweight="semibold", color=INK, x=0.06, ha="left")
    fig.tight_layout()
    fig.savefig(GRAFIK / "grafik_batch_keunggulan_peft.png")
    plt.close(fig)


def batch_contention(agg):
    """Inflasi waktu eksekusi host-1 dan host-2 terhadap total kerja di host-2."""
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.3))
    for ax, ds in zip(axes, DATASETS):
        for al in ALGOS:
            pts = sorted((a["cpu2"][0], a["infl2"][0], a["infl1"][0])
                         for (d, n, x), a in agg.items() if d == ds and x == al)
            ax.plot([p[0] for p in pts], [p[1] for p in pts], color=ALGO_COLOR[al], linewidth=2, marker="o",
                    markersize=6, label=f"{al} · host-2")
            ax.plot([p[0] for p in pts], [p[2] for p in pts], color=ALGO_COLOR[al], linewidth=1.2, linestyle=":",
                    marker="s", markersize=4, label=f"{al} · host-1")
        ax.axhline(0, color=MUTED, linewidth=0.8)
        ax.set_xlabel("Total kerja di host-2 (detik-CPU)")
        ax.set_title(NAMA_DS[ds])
    axes[0].set_ylabel("Eksekusi lebih lambat dari rating (%)")
    h, l = axes[0].get_legend_handles_labels()
    fig.legend(h, l, loc="lower center", ncol=6, fontsize=8.5, bbox_to_anchor=(0.5, -0.05))
    fig.suptitle("Kontensi CPU: semakin padat host-2, semakin lambat VM-nya",
                 fontsize=13, fontweight="semibold", color=INK, x=0.06, ha="left")
    fig.tight_layout()
    fig.savefig(GRAFIK / "grafik_batch_kontensi_host.png")
    plt.close(fig)


def fmt(v, d=2):
    """Format angka Indonesia: 1.234,56"""
    return f"{v:,.{d}f}".replace(",", "X").replace(".", ",").replace("X", ".")


def batch_tables(agg):
    """Tabel Markdown siap tempel ke README."""
    out = []
    for ds in DATASETS:
        ns = sorted({n for d, n, _ in agg if d == ds})
        out.append(f"\n### {NAMA_DS[ds]} · real-world lengkap (rata-rata {agg[ds, ns[0], 'PEFT']['runs']} run)\n")
        out.append("| Task | Algoritma | Makespan (s) | Turnaround (s) | Waiting (s) | Throughput (task/s) | Utilisasi VM (%) | SD task/VM | Energi (Wh) |")
        out.append("| ---: | :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
        for n in ns:
            for a in ALGOS:
                r = agg[ds, n, a]["real"]
                out.append(f"| {n} | {a} | " + " | ".join(
                    f"{fmt(r[k][0], 3 if k.startswith(('Throughput', 'Estimasi')) else 2)} ± {fmt(r[k][1], 3 if k.startswith(('Throughput', 'Estimasi')) else 2)}"
                    if k in ("Makespan (s)", "Rata-rata waiting (s)") else
                    fmt(r[k][0], 3 if k.startswith(('Throughput', 'Estimasi')) else 2) for k in KEYS) + " |")
        for key, d in (("Makespan (s)", 2), ("Rata-rata waiting (s)", 2), ("Rata-rata turnaround (s)", 2),
                       ("Estimasi energi (Wh)", 3)):
            out.append(f"\n### {NAMA_DS[ds]} · {key}: simulasi vs real\n")
            out.append("| Task | " + " | ".join(f"{a} sim | {a} real | Selisih" for a in ALGOS) + " |")
            out.append("| ---: | " + " | ".join("---: | ---: | ---:" for _ in ALGOS) + " |")
            for n in ns:
                cells = []
                for a in ALGOS:
                    s_, r = agg[ds, n, a]["sim"][key][0], agg[ds, n, a]["real"][key][0]
                    cells += [fmt(s_, d), fmt(r, d), f"{'+' if r >= s_ else ''}{fmt((r - s_) / s_ * 100)}%"]
                out.append(f"| {n} | " + " | ".join(cells) + " |")
        out.append(f"\n### {NAMA_DS[ds]} · perbaikan PEFT vs FCFS\n")
        out.append("| Task | Makespan sim | Makespan real | Waiting sim | Waiting real | Energi sim | Energi real |")
        out.append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
        for n in ns:
            cells = [f"{fmt(improvement(agg[ds, n, 'PEFT'], agg[ds, n, 'FCFS'], src, key), 1)}%"
                     for key in ("Makespan (s)", "Rata-rata waiting (s)", "Estimasi energi (Wh)") for src in ("sim", "real")]
            out.append(f"| {n} | " + " | ".join(cells) + " |")
        out.append(f"\n### {NAMA_DS[ds]} · kontensi & overhead\n")
        out.append("| Task | Algoritma | Kerja host-2 (detik-CPU) | Inflasi eksekusi host-1 | Inflasi eksekusi host-2 | Overhead HTTP (ms) | Waiting sim FIFO ketat (s) |")
        out.append("| ---: | :--- | ---: | ---: | ---: | ---: | ---: |")
        for n in ns:
            for a in ALGOS:
                g = agg[ds, n, a]
                out.append(f"| {n} | {a} | {fmt(g['cpu2'][0], 1)} | {fmt(g['infl1'][0], 1)}% | {fmt(g['infl2'][0], 1)}% | "
                           f"{fmt(g['http'][0], 0)} | {fmt(g['fifo']['Rata-rata waiting (s)'][0])} |")
    (HASIL / "batch_tabel.md").write_text("\n".join(out), encoding="utf-8")


agg = batch_analysis()
if agg:
    batch_small_multiples(agg, "Makespan (s)", "grafik_batch_makespan.png",
                          "Makespan terhadap jumlah task: simulasi vs real-world", "Makespan (s)")
    batch_small_multiples(agg, "Rata-rata waiting (s)", "grafik_batch_waiting.png",
                          "Waiting time terhadap jumlah task: simulasi vs real-world", "Waiting (s)")
    batch_small_multiples(agg, "Rata-rata turnaround (s)", "grafik_batch_turnaround.png",
                          "Turnaround time terhadap jumlah task: simulasi vs real-world", "Turnaround (s)")
    batch_small_multiples(agg, "Estimasi energi (Wh)", "grafik_batch_energi.png",
                          "Estimasi energi terhadap jumlah task: simulasi vs real-world", "Energi (Wh)")
    batch_lines(agg, lambda a: (a["real"]["Makespan (s)"][0] - a["sim"]["Makespan (s)"][0]) / a["sim"]["Makespan (s)"][0] * 100,
                "grafik_batch_selisih_makespan.png", "Seberapa jauh makespan nyata meleset dari simulasi?",
                "Makespan real vs simulasi (%)")
    batch_advantage(agg)
    batch_contention(agg)
    batch_tables(agg)
    print(f"Batch: {len(agg)} konfigurasi dianalisis -> hasil/batch_sim_vs_real.csv, hasil/batch_tabel.md")

# ------------------------------------------------------------------ ringkasan konsol
for ds in DATASETS:
    print(f"\n== {NAMA_DS[ds]} ==")
    for al in ALGOS:
        print(f"  {al:4s}", "  ".join(f"{k.split(' (')[0][:14]}: sim {sim[ds, al][k]:8.3f} | fifo {fifo[ds, al][k]:8.3f} | real {real[ds, al][k]:8.3f}"
                                      for k in ("Makespan (s)", "Rata-rata waiting (s)", "Rata-rata turnaround (s)", "Estimasi energi (Wh)", "Rata-rata utilisasi VM (%)")))
        d = decomp[ds, al]
        print(f"       dispatch {d[0]:.1f} ms | http {d[1]:.1f} ms | exec ideal {d[2]:.2f}s real {d[3]:.2f}s (+{d[4]:.2f}%)")
print(f"\nGrafik tersimpan di {GRAFIK}")
