"""
Menggambar output terminal asli menjadi "screenshot" jendela Git Bash (MINGW64) untuk README.

Teks tidak diketik ulang: setiap layar disusun dari berkas log asli di hasil/ (atau output perintah yang
dijalankan langsung), lalu diberi prompt, warna, dan bingkai jendela seperti di terminal.

Jalankan dari folder realworld/:  python analisis/render_terminal.py
Keluaran: grafik/terminal/{nama}.png dan {nama}.txt (teks polos yang sama, untuk README)
"""
import re
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "grafik" / "terminal"
FONT = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 28)
BOLD = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 28)
UI = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 24)
CW, LH, PAD, BAR, WRAP = FONT.getlength("M"), 36, 30, 64, 124

BG, FG, DIM = (12, 12, 12), (204, 204, 204), (118, 118, 118)
GREEN, RED, YELLOW, CYAN, MAGENTA, WHITE = (78, 201, 78), (255, 95, 95), (229, 192, 74), (59, 198, 255), (199, 77, 237), (242, 242, 242)
USER, HOST, CWD = "arya4", "DESKTOP-8G8M19I", "~/soka/realworld"

# pewarnaan per kata/pola, diterapkan berurutan pada setiap baris output
RULES = [
    (r"^(=+|-+)$", DIM, False),
    (r"^== .* ==$", CYAN, True),
    (r"^\s*!!.*$", RED, True),
    (r"^\s*\.\. .*$", YELLOW, False),
    (r"\bGAGAL\b", RED, True),
    (r"\bOK\b", GREEN, True),
    (r"^\[\d\].*$", YELLOW, True),
    (r"\b(Ready|Running|condition met|configured|unchanged)\b", GREEN, False),
    (r"^ (REAL-WORLD|PERBANDINGAN|EKSEKUSI|Datacenter).*$", WHITE, True),
    (r"^\s*\[\s*[\d.]+ s\]", CYAN, False),
    (r"^\s*Hasil \w+:$|^\s*Distribusi task .*:$", WHITE, True),
    (r"^### .*$", DIM, False),
    (r"(?<=:)\s+\d{3} MIPS", RED, True),          # hasil kalibrasi di bawah 1.000 MIPS: VM melambat
]


def spans(line):
    """Pecah satu baris menjadi potongan (teks, warna, tebal)."""
    colour = [(FG, False)] * len(line)
    for pat, col, bold in RULES:
        for m in re.finditer(pat, line):
            for i in range(m.start(), m.end()):
                colour[i] = (col, bold)
    out, i = [], 0
    while i < len(line):
        j = i
        while j < len(line) and colour[j] == colour[i]:
            j += 1
        out.append((line[i:j], *colour[i]))
        i = j
    return out


def wrap(line):
    return [line[i:i + WRAP] for i in range(0, max(len(line), 1), WRAP)] or [""]


def render(name, blocks, title=f"MINGW64:/c/Users/{USER}/soka/realworld"):
    """blocks: daftar (perintah atau None, [baris output]); perintah None = lanjutan output tanpa prompt."""
    rows, text = [], []                                   # rows: daftar potongan per baris layar
    for k, (cmd, lines) in enumerate(blocks):
        if cmd is not None:
            if rows:
                rows.append([("", FG, False)])
            rows.append([(USER + "@" + HOST, GREEN, False), (" ", FG, False), ("MINGW64", MAGENTA, False),
                         (" ", FG, False), (CWD, YELLOW, False)])
            for n, part in enumerate(wrap("$ " + cmd)):
                rows.append([(part, WHITE, n == 0 and False)])
            text += [f"{USER}@{HOST} MINGW64 {CWD}", "$ " + cmd]
        for line in lines:
            for part in wrap(line.rstrip()):
                rows.append(spans(part))
            text.append(line.rstrip())
    # prompt kosong di akhir, seperti terminal yang menunggu perintah berikutnya
    rows += [[("", FG, False)], [(USER + "@" + HOST, GREEN, False), (" ", FG, False), ("MINGW64", MAGENTA, False),
                                 (" ", FG, False), (CWD, YELLOW, False)], [("$ ", WHITE, False), ("█", FG, False)]]
    cols = max(sum(len(t) for t, _, _ in r) for r in rows)
    W, H = int(PAD * 2 + cols * CW), BAR + PAD * 2 + LH * len(rows)
    img = Image.new("RGB", (W + 24, H + 24), (236, 236, 236))   # tepi luar terang agar jendela "mengambang"
    win = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(win)
    d.rectangle((0, 0, W, BAR), fill=(32, 32, 32))
    d.text((PAD, BAR / 2), title, font=UI, fill=(220, 220, 220), anchor="lm")
    for i, glyph in enumerate(["—", "☐", "✕"]):
        x = W - 46 - i * 72
        d.text((x, BAR / 2), glyph, font=UI, fill=(220, 220, 220), anchor="mm")
    y = BAR + PAD
    for r in rows:
        x = PAD
        for t, col, b in r:
            d.text((x, y), t, font=BOLD if b else FONT, fill=col)
            x += len(t) * CW
        y += LH
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, W - 1, H - 1), radius=14, fill=255)
    img.paste(win, (12, 12), mask)
    img = img.resize((img.width // 2, img.height // 2), Image.LANCZOS)   # digambar 2x lalu diperkecil: tajam
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / f"{name}.png")
    (OUT / f"{name}.txt").write_text("\n".join(text) + "\n", encoding="utf-8")
    print(f"{name}.png  {img.width}x{img.height}")


def lines_of(path):
    return (ROOT / path).read_text(encoding="utf-8", errors="replace").splitlines()


def between(lines, start, count):
    i = next(n for n, l in enumerate(lines) if l.startswith(start) or l.strip().startswith(start))
    return lines[i:i + count]


# 1. setup.sh
render("01_setup", [("bash setup.sh", lines_of("hasil/logs/setup.txt"))])

# 2. bukti infrastruktur: setiap bagian infrastruktur.txt adalah output satu perintah di batch.sh
CMDS = {
    "kubectl get nodes": "kubectl get nodes -L host,tier",
    "Batas CPU host": "for c in soka-dc-worker soka-dc-worker2; do echo \"$c : $(docker inspect -f '{{.HostConfig.NanoCpus}}' \"$c\" | awk '{print $1/1e9}') CPU\"; done",
    "kubectl get pods": "kubectl get pods -o wide",
    "Spesifikasi VM": "kubectl get pods -l app=soka-vm -o custom-columns='VM:.metadata.name,NODE:.spec.nodeName,PES:.spec.containers[0].env[1].value,MIPS:.spec.containers[0].env[2].value,CPU_REQ:.spec.containers[0].resources.requests.cpu,CPU_LIM:.spec.containers[0].resources.limits.cpu'",
}
blocks, cur = [], None
for line in lines_of("hasil/logs/infrastruktur.txt"):
    if line.startswith("### "):
        key = next(k for k in CMDS if k in line)
        cur = (CMDS[key], []); blocks.append(cur)
    elif cur is not None and line.strip():
        cur[1].append(line)
render("02_infrastruktur", blocks)

# 3. broker: cek VM, kalibrasi, dataset, dan awal eksekusi
log = lines_of("hasil/batch/gocj_30/run2/log.txt")
start_exec = next(n for n, l in enumerate(log) if "EKSEKUSI PEFT" in l)
render("03_broker_awal", [("java broker/RealWorldBroker.java ALL gocj 30", log[:start_exec + 8] + ["  ..."])])

# 4. broker: sebaran task, hasil PEFT, dan tabel perbandingan
dist = between(log, "Distribusi task PEFT", 13)
cmp_i = next(n for n, l in enumerate(log) if "PERBANDINGAN" in l)
render("04_broker_hasil", [(None, ["  ..."] + dist + ["  ..."] + log[cmp_i - 1:])])

# 5. pengecek kecepatan di batch.sh (lolos, dibuang, diulang)
bl = lines_of("hasil/logs/batch_ulang.txt")
pick = [l for l in bl if l.startswith("== ") or "cek kecepatan" in l or l.startswith("   !!")]
guard, seen = [], set()
for l in pick:
    if l in seen:            # baris cek kecepatan tercetak dua kali (tee ke log + layar)
        continue
    seen.add(l); guard.append(l)
    if "gocj | 40 task | run 2" in l:
        break
render("05_pengecek_kecepatan", [("bash batch.sh", guard[:-1] + ["   ..."])])

# 6. kalibrasi saat laptop terganggu
bad = lines_of("hasil/batch_terganggu/gocj_50_run1_batch1/log.txt")[:17]
render("06_kalibrasi_terganggu", [("head -n 17 hasil/batch_terganggu/gocj_50_run1_batch1/log.txt", bad)])

# 7. mode baterai: pengecek menunggu, dan vm-1 tertahan batas jatah CPU-nya
wait = [l for l in lines_of("hasil/logs/batch_ulang_sintetis50_akhir.txt") if "menunggu" in l][:3]
thr = subprocess.run(["kubectl", "exec", "vm-1", "--", "sh", "-c",
                      "cat /sys/fs/cgroup/cpu.max; grep -E 'nr_periods|nr_throttled|throttled_usec' /sys/fs/cgroup/cpu.stat"],
                     capture_output=True, text=True).stdout.splitlines()
render("07_baterai_throttling", [
    ('grep -a "menunggu" hasil/logs/batch_ulang_sintetis50_akhir.txt | head -n 3', wait),
    ("kubectl exec vm-1 -- sh -c 'cat /sys/fs/cgroup/cpu.max; grep -E \"nr_periods|nr_throttled|throttled_usec\" /sys/fs/cgroup/cpu.stat'", thr),
])
