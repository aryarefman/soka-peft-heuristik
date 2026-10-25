// Membangun docs/Laporan_Hasil_Uji_Coba_Kelompok_8.docx langsung dari CSV di results/.
// Pemakaian (dari root repo): cd tools/laporan && npm install && node build_laporan.js ../.. ../../docs/Laporan_Hasil_Uji_Coba_Kelompok_8.docx
// Setelah itu buka di Word, perbarui daftar isi (F9), lalu ekspor PDF.
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell, WidthType, BorderStyle,
  ShadingType, AlignmentType, HeadingLevel, LevelFormat, Header, Footer, PageNumber, TableOfContents,
  PageBreak, VerticalAlign, TabStopType,
} = require("docx");

const REPO = process.argv[2];
const OUT = process.argv[3];
const R = (...p) => path.join(REPO, ...p);

// ---------------------------------------------------------------- data
function csv(file) {
  const [head, ...rows] = fs.readFileSync(R(file), "utf8").trim().split(/\r?\n/);
  const h = head.split(",");
  return rows.map((l) => { const c = l.split(","); return Object.fromEntries(h.map((k, i) => [k, c[i]])); });
}
const short = (a) => a.startsWith("PEFT") ? "PEFT" : a.startsWith("FCFS") ? "FCFS" : a.startsWith("Round") || a === "RR" ? "RR" : a;
function mainRes(s, f) {
  const o = {};
  for (const r of csv(`results/${s}/${f}.csv`)) o[short(r.Algorithm)] = r;
  return o;
}
function batch(s, f, ds) {
  const o = {};
  for (const r of csv(`results/${s}/${f}.csv`)) if (r.Dataset === ds) (o[+r.TaskCount] ??= {})[r.Algorithm] = r;
  return o;
}
const D = {};
for (const s of ["3dc", "2dc"]) {
  D[s] = {
    syn: mainRes(s, "synthetic_results"),
    nasa: mainRes(s, "nasa_ipsc_results"),
    sync: csv(`results/${s}/datacenter_sync_results.csv`),
    bSyn: batch(s, "batch_100_1000_results", "Synthetic"),
    bNasa: batch(s, "batch_100_1000_results", "NASA-iPSC"),
    bBig: batch(s, "synthetic_1000_10000_results", "Synthetic"),
    bGocj: batch(s, "gocj_100_1000_results", "GoCJ"),
  };
}

// format angka gaya Indonesia
function f(x, d = 2) {
  const s = Number(x).toFixed(d);
  let [i, dec] = s.split(".");
  const neg = i.startsWith("-"); if (neg) i = i.slice(1);
  i = i.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return (neg ? "-" : "") + i + (dec ? "," + dec : "");
}
const pct = (a, b) => (a - b) / b * 100;
const fp = (v) => (v > 0 ? "+" : "") + f(v, 2) + "%";

// ---------------------------------------------------------------- gaya
const FONT = "Poppins", MONO = "Consolas";
const INK = "1F1F1F", MUTE = "555555", HEAD = "E7E9EC", LINE = "9AA0A6";
const W = 9026; // A4, margin 2,54 cm

function runs(text, o = {}) {
  // **tebal**, *miring*, `kode`
  const out = []; const re = /(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`)/g; let last = 0, m;
  const base = { font: FONT, size: o.size || 21, color: o.color || INK, bold: o.bold, italics: o.italics };
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(new TextRun({ ...base, text: text.slice(last, m.index) }));
    const t = m[0];
    if (t.startsWith("**")) out.push(new TextRun({ ...base, text: t.slice(2, -2), bold: true }));
    else if (t.startsWith("`")) out.push(new TextRun({ ...base, text: t.slice(1, -1), font: MONO, size: (o.size || 21) - 2 }));
    else out.push(new TextRun({ ...base, text: t.slice(1, -1), italics: true }));
    last = m.index + t.length;
  }
  if (last < text.length) out.push(new TextRun({ ...base, text: text.slice(last) }));
  return out;
}
const P = (t, o = {}) => new Paragraph({ children: runs(t, o), alignment: o.align ?? (t.includes("`") ? AlignmentType.LEFT : AlignmentType.JUSTIFIED),
  spacing: { after: o.after ?? 140, before: o.before ?? 0, line: 320 }, keepNext: o.keepNext });
const B = (t, lvl = 0) => new Paragraph({ children: runs(t), numbering: { reference: "bul", level: lvl },
  alignment: AlignmentType.JUSTIFIED, spacing: { after: 80, line: 310 } });
let numRef = 0;
const NL = () => `num${numRef++}`;
const N = (t, ref) => new Paragraph({ children: runs(t), numbering: { reference: ref, level: 0 },
  alignment: AlignmentType.JUSTIFIED, spacing: { after: 90, line: 310 } });
const H1 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_1, pageBreakBefore: true, children: [new TextRun(t)] });
const H2 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(t)] });
const H3 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun(t)] });
let eqN = 0;
const EQ = (t) => new Paragraph({
  tabStops: [{ type: TabStopType.CENTER, position: W / 2 }, { type: TabStopType.RIGHT, position: W }],
  spacing: { before: 100, after: 160 },
  children: [new TextRun({ text: "\t" + t, font: "Cambria Math", size: 22, italics: true }),
    new TextRun({ text: `\t(${++eqN})`, font: FONT, size: 20 })],
});
function code(lines, size = 15) {
  return lines.map((l, i) => new Paragraph({
    keepLines: true, keepNext: i < lines.length - 1,
    shading: { type: ShadingType.CLEAR, fill: "F3F4F6", color: "auto" },
    spacing: { before: i === 0 ? 80 : 0, after: i === lines.length - 1 ? 200 : 0, line: 240 },
    children: [new TextRun({ text: l || " ", font: MONO, size, color: INK })],
  }));
}

const BD = { style: BorderStyle.SINGLE, size: 4, color: LINE };
const borders = { top: BD, bottom: BD, left: BD, right: BD };
function cell(t, w, o = {}) {
  return new TableCell({
    width: { size: w, type: WidthType.DXA }, borders,
    shading: o.fill ? { type: ShadingType.CLEAR, fill: o.fill, color: "auto" } : undefined,
    margins: { top: 60, bottom: 60, left: 100, right: 100 }, verticalAlign: VerticalAlign.CENTER,
    columnSpan: o.span,
    children: [new Paragraph({ alignment: o.align ?? AlignmentType.LEFT, spacing: { after: 0, line: 260 },
      children: runs(String(t), { size: o.size || 17, bold: o.bold }) })],
  });
}
let tabN = 0, figN = 0;
const caption = (kind, t) => new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 80, after: 220 },
  keepNext: kind === "Tabel",
  children: [new TextRun({ text: `${kind} ${kind === "Tabel" ? ++tabN : ++figN}. `, font: FONT, size: 18, bold: true }),
    ...runs(t, { size: 18 })] });
/** headers, rows, widths; aligns opsional. Judul tabel ditaruh di atas. */
function table(title, headers, rows, widths, o = {}) {
  const sum = widths.reduce((a, b) => a + b, 0);
  const al = o.aligns || headers.map((_, i) => i === 0 ? AlignmentType.LEFT : AlignmentType.CENTER);
  return [caption("Tabel", title), new Table({
    width: { size: sum, type: WidthType.DXA }, columnWidths: widths,
    rows: [
      new TableRow({ tableHeader: true, cantSplit: true,
        children: headers.map((h, i) => cell(h, widths[i], { fill: HEAD, bold: true, align: AlignmentType.CENTER, size: o.size })) }),
      ...rows.map((r) => new TableRow({ cantSplit: true,
        children: r.map((c, i) => cell(c, widths[i], { align: al[i], size: o.size, bold: o.boldFirst && i === 0 })) })),
    ],
  }), new Paragraph({ spacing: { after: 160 }, children: [] })];
}
function figure(rel, t, wPx = 560) {
  const buf = fs.readFileSync(R(rel));
  const w0 = buf.readUInt32BE(16), h0 = buf.readUInt32BE(20);
  const h = Math.round(h0 * wPx / w0);
  return [new Paragraph({ alignment: AlignmentType.CENTER, keepNext: true, spacing: { before: 120, after: 40 },
    children: [new ImageRun({ type: "png", data: buf, transformation: { width: wPx, height: h },
      altText: { title: t, description: t, name: path.basename(rel) } })] }), caption("Gambar", t)];
}

// ---------------------------------------------------------------- tabel hasil
const ALGOS = [["PEFT", "PEFT"], ["FCFS", "FCFS"], ["RR", "Round Robin"]];
function bestMark(vals, d, lower) {
  const txt = vals.map((v) => f(v, d));
  if (lower === null || new Set(txt).size === 1) return txt;
  const best = lower ? Math.min(...vals) : Math.max(...vals);
  return txt.map((t, i) => (Math.abs(vals[i] - best) < 1e-9 ? `**${t}**` : t));
}
function mainTable(s, key, title) {
  const r = D[s][key];
  const spec = [
    ["Task selesai", "TotalTasks", 0, null], ["Makespan (s)", "Makespan(s)", 2, true],
    ["Rata-rata turnaround time (s)", "AvgTurnaround(s)", 2, true], ["Rata-rata waiting time (s)", "AvgWaitingTime(s)", 2, true],
    ["Throughput (task/s)", "Throughput(task/s)", 4, false], ["Rata-rata utilisasi CPU VM (%)", "CpuUtilization(%)", 2, false],
    ["Load balance (SD task/VM)", "LoadBalanceSD", 2, true], ["Energi SPECpower (kWh)", "Energy(kWh)", 3, true],
  ];
  const rows = spec.map(([lbl, col, d, lower]) => {
    const v = ALGOS.map(([k]) => +r[k][col]);
    let vf = pct(v[0], v[1]), vr = pct(v[0], v[2]);
    if (col === "Throughput(task/s)") {
      const mk = ALGOS.map(([k]) => +r[k]["Makespan(s)"]);
      vf = (mk[1] / mk[0] - 1) * 100; vr = (mk[2] / mk[0] - 1) * 100;
    }
    return [lbl, ...bestMark(v, d, lower), fp(vf), fp(vr)];
  });
  return table(title, ["Metrik", "PEFT", "FCFS", "Round Robin", "PEFT vs FCFS", "PEFT vs RR"], rows,
    [2826, 1240, 1240, 1240, 1240, 1240]);
}
function syncTable(s, title) {
  const rows = D[s].sync.map((r) => {
    const [algo, ds] = r.Algorithm.split(" [");
    return [ds.replace("]", "") === "Synthetic" ? "Sintetis" : "NASA", short(algo) === "RR" ? "Round Robin" : short(algo),
      r.Datacenter.replace(/^DC-\d+: /, (m) => m), r.TasksCompleted, f(+r.WorkloadPct, 1) + "%",
      f(+r["DcMakespan(s)"], 2), f(+r["Energy(kWh)"], 3)];
  });
  return table(title, ["Dataset", "Algoritma", "Datacenter", "Task", "Porsi", "Selesai DC (s)", "Energi (kWh)"], rows,
    [1000, 1250, 2576, 800, 900, 1300, 1200], { size: 16 });
}
function batchTable(s, key, cols, title, withDiff = true) {
  const by = D[s][key];
  const heads = ["Task"];
  for (const [lbl] of cols) heads.push(`${lbl} PEFT`, `${lbl} FCFS`, `${lbl} RR`);
  if (withDiff) heads.push("Makespan PEFT vs FCFS");
  const rows = Object.keys(by).map(Number).sort((a, b) => a - b).map((n) => {
    const a = by[n]; const row = [f(n, 0)];
    for (const [, col, d] of cols) row.push(...bestMark(ALGOS.map(([k]) => +a[k][col]), d, true));
    if (withDiff) row.push(fp(pct(+a.PEFT["AvgMakespan(s)"], +a.FCFS["AvgMakespan(s)"])));
    return row;
  });
  const n = heads.length;
  const first = 760, rest = Math.floor((W - first) / (n - 1));
  return table(title, heads, rows, [first, ...Array(n - 1).fill(rest)], { size: 15 });
}
const MK = ["Makespan (s)", "AvgMakespan(s)", 2];
const EN = (d) => ["Energi (kWh)", "AvgEnergy(kWh)", d];

// ---------------------------------------------------------------- angka untuk narasi
const s3 = D["3dc"], s2 = D["2dc"];
const v = (r, k, c) => +r[k][c];
const syn3 = { p: v(s3.syn, "PEFT", "Makespan(s)"), f: v(s3.syn, "FCFS", "Makespan(s)"), r: v(s3.syn, "RR", "Makespan(s)") };
const syn2 = { p: v(s2.syn, "PEFT", "Makespan(s)"), f: v(s2.syn, "FCFS", "Makespan(s)"), r: v(s2.syn, "RR", "Makespan(s)") };
const en3 = { p: v(s3.syn, "PEFT", "Energy(kWh)"), f: v(s3.syn, "FCFS", "Energy(kWh)"), r: v(s3.syn, "RR", "Energy(kWh)") };
const en2 = { p: v(s2.syn, "PEFT", "Energy(kWh)"), f: v(s2.syn, "FCFS", "Energy(kWh)"), r: v(s2.syn, "RR", "Energy(kWh)") };
const na3 = { mk: v(s3.nasa, "PEFT", "Makespan(s)"), tp: v(s3.nasa, "PEFT", "AvgTurnaround(s)"), tf: v(s3.nasa, "FCFS", "AvgTurnaround(s)"),
  wp: v(s3.nasa, "PEFT", "AvgWaitingTime(s)"), wf: v(s3.nasa, "FCFS", "AvgWaitingTime(s)"),
  ep: v(s3.nasa, "PEFT", "Energy(kWh)"), ef: v(s3.nasa, "FCFS", "Energy(kWh)"), lp: v(s3.nasa, "PEFT", "LoadBalanceSD"), lf: v(s3.nasa, "FCFS", "LoadBalanceSD") };
const na2 = { tp: v(s2.nasa, "PEFT", "AvgTurnaround(s)"), tf: v(s2.nasa, "FCFS", "AvgTurnaround(s)"),
  wf: v(s2.nasa, "FCFS", "AvgWaitingTime(s)"), ep: v(s2.nasa, "PEFT", "Energy(kWh)"), ef: v(s2.nasa, "FCFS", "Energy(kWh)") };
const g3 = s3.bGocj[1000], g2 = s2.bGocj[1000];
const big3 = s3.bBig[10000], big2 = s2.bBig[10000];
const rng = (by, ds, lo, hi) => {
  const d = Object.keys(by).map(Number).filter((n) => n >= lo && n <= hi)
    .map((n) => -pct(+by[n].PEFT["AvgMakespan(s)"], +by[n].FCFS["AvgMakespan(s)"]));
  return `${f(Math.min(...d), 2)} – ${f(Math.max(...d), 2)}%`;
};
// hitung total konfigurasi & yang PEFT tidak terbaik
let nCfg = 0; const notBest = [];
for (const s of ["3dc", "2dc"]) for (const k of ["bSyn", "bNasa", "bBig", "bGocj"]) for (const n of Object.keys(D[s][k])) {
  if (k === "bBig" && +n === 1000) continue;
  nCfg++; const a = D[s][k][n];
  if (+a.PEFT["AvgMakespan(s)"] > Math.min(+a.FCFS["AvgMakespan(s)"], +a.RR["AvgMakespan(s)"]) + 1e-9)
    notBest.push({ s, n, d: +a.PEFT["AvgMakespan(s)"] - Math.min(+a.FCFS["AvgMakespan(s)"], +a.RR["AvgMakespan(s)"]) });
}
const logExcerpt = (s, from, nLines) => {
  const l = fs.readFileSync(R(`results/${s}/logs/runAll.txt`), "utf8").split(/\r?\n/);
  const i = l.findIndex((x) => x.includes(from));
  return l.slice(i, i + nLines);
};
const lastLines = (file, n) => fs.readFileSync(R(file), "utf8").trimEnd().split(/\r?\n/).filter((x) => x.trim()).slice(-n);

// ---------------------------------------------------------------- isi
const memberTable = new Table({
  width: { size: 6400, type: WidthType.DXA }, columnWidths: [700, 3900, 1800],
  alignment: AlignmentType.CENTER,
  rows: [["No", "Nama", "NRP"], ["1", "Arya Bisma Putra Refman", "5027241036"], ["2", "Muhammad Ziddan Habibi", "5027241122"],
    ["3", "Prabaswara Febrian Winandika", "5027241069"], ["4", "Muhammad Fachry Shalahuddin Rusamsi", "5027241031"],
    ["5", "Gemilang Ananda", "5027241072"]].map((r, i) => new TableRow({ children: r.map((c, j) =>
      cell(c, [700, 3900, 1800][j], { fill: i === 0 ? HEAD : undefined, bold: i === 0, size: 19,
        align: j === 1 ? AlignmentType.LEFT : AlignmentType.CENTER })) })),
});
const center = (t, size, o = {}) => new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: o.before || 0, after: o.after ?? 100, line: 300 },
  children: [new TextRun({ text: t, font: FONT, size, bold: o.bold, color: INK })] });

const logo = fs.readFileSync(R("Assets/Lambang ITS PNG v1.png"));
const cover = [
  center("LAPORAN HASIL UJI COBA", 36, { bold: true, before: 400, after: 160 }),
  center("Optimasi Penjadwalan Task pada Komputasi Awan", 28, { bold: true }),
  center("Evaluasi Algoritma PEFT (Predict Earliest Finish Time) terhadap FCFS dan Round Robin", 22, { after: 40 }),
  center("pada Simulator CloudSim Plus 8.0.0", 22, { after: 500 }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 500 },
    children: [new ImageRun({ type: "png", data: logo, transformation: { width: 260, height: Math.round(260 * 1356 / 2198) } })] }),
  center("Disusun oleh Kelompok 8", 22, { bold: true, after: 160 }),
  memberTable,
  center("Mata Kuliah Strategi Optimasi Komputasi Awan (SOKA)", 21, { before: 700 }),
  center("Departemen Teknologi Informasi", 21),
  center("Institut Teknologi Sepuluh Nopember", 22, { bold: true }),
  center("Surabaya, 2026", 21),
];

const ringkasan = [
  new Paragraph({ heading: HeadingLevel.TITLE, pageBreakBefore: true, children: [new TextRun("Ringkasan Eksekutif")] }),
  P("Laporan ini menyajikan implementasi dan evaluasi algoritma heuristik **PEFT (Predict Earliest Finish Time)** untuk menjadwalkan *bag-of-tasks* independen pada VM heterogen multi-core di simulator **CloudSim Plus 8.0.0**, dibandingkan dengan dua baseline: **First Come First Served (FCFS)** dan **Round Robin (RR)**."),
  P(`Sesuai arahan dosen, simulasi dijalankan pada **dua skenario infrastruktur** dari kode yang sama: skenario \`3dc\` (3 datacenter, 24 host, 30 VM) dan skenario \`2dc\` (2 datacenter, 10 host, 40 VM, tepat 4 VM per host). Evaluasi memakai tiga workload: dataset sintetis terkontrol (100 – 10.000 task), trace asli superkomputer **NASA Ames iPSC/860** dari Parallel Workloads Archive (100 – 1.000 job), dan dataset resmi **GoCJ (Google Cloud Jobs)** (100 – 1.000 job). Setiap konfigurasi batch dijalankan tiga kali.`),
  P("Temuan utama:", { keepNext: true, after: 60 }),
  B(`**Sintetis 1.000 task.** Makespan PEFT ${f(syn3.p)} s pada \`3dc\` (${fp(pct(syn3.p, syn3.f))} terhadap FCFS, ${fp(pct(syn3.p, syn3.r))} terhadap RR) dan ${f(syn2.p)} s pada \`2dc\` (${fp(pct(syn2.p, syn2.f))} terhadap FCFS). Energinya ${fp(pct(en3.p, en3.f))} (\`3dc\`) dan ${fp(pct(en2.p, en2.f))} (\`2dc\`) terhadap FCFS.`),
  B(`**Trace NASA iPSC/860.** Makespan ketiga algoritma sama (${f(na3.mk)} s) karena dibatasi waktu kedatangan job terakhir. PEFT menghapus waktu tunggu (${f(na3.wp)} s vs ${f(na3.wf)} s FCFS pada \`3dc\`) dan memperpendek turnaround time ${f(-pct(na3.tp, na3.tf))}% (\`3dc\`) dan ${f(-pct(na2.tp, na2.tf))}% (\`2dc\`).`),
  B(`**GoCJ 1.000 job.** Makespan PEFT ${f(+g3.PEFT["AvgMakespan(s)"])} s vs FCFS ${f(+g3.FCFS["AvgMakespan(s)"])} s pada \`3dc\` (${fp(pct(+g3.PEFT["AvgMakespan(s)"], +g3.FCFS["AvgMakespan(s)"]))}), dan ${f(+g2.PEFT["AvgMakespan(s)"])} s vs ${f(+g2.FCFS["AvgMakespan(s)"])} s pada \`2dc\`.`),
  B(`**Konsistensi.** Dari ${nCfg} konfigurasi batch, PEFT menghasilkan makespan terpendek atau setara pada ${nCfg - notBest.length} konfigurasi. Seluruh pengulangan menghasilkan nilai identik (simpangan baku antar-run = 0).`),
];

const toc = [
  new Paragraph({ heading: HeadingLevel.TITLE, pageBreakBefore: true, children: [new TextRun("Daftar Isi")] }),
  new TableOfContents("Daftar Isi", { hyperlink: true, headingStyleRange: "1-2" }),
];

const nT = NL(), nG = NL();
const bab1 = [
  H1("BAB I  PENDAHULUAN"),
  H2("1.1 Latar Belakang"),
  P("Infrastruktur komputasi awan tersusun atas host fisik dengan generasi prosesor, kapasitas memori, dan konsumsi daya yang berbeda. Di atasnya berjalan VM dengan kapasitas yang juga beragam. Ketika task dialokasikan tanpa memperhitungkan kapasitas VM, sebagian VM lambat menumpuk antrean, sementara VM cepat menganggur. Akibatnya waktu penyelesaian keseluruhan (*makespan*) memanjang dan host fisik menyala lebih lama sehingga energi terbuang pada kondisi idle."),
  P("Penjadwalan task pada sistem heterogen termasuk persoalan NP-complete, sehingga dalam praktik digunakan heuristik. Proyek ini mengevaluasi heuristik PEFT dan membandingkannya dengan dua kebijakan dasar yang umum dipakai: FCFS dan Round Robin."),
  H2("1.2 Rumusan Masalah"),
  N("Seberapa besar PEFT memperpendek makespan, turnaround time, dan waiting time dibanding FCFS dan Round Robin pada infrastruktur cloud heterogen?", nT),
  N("Bagaimana pengaruh penjadwalan terhadap konsumsi energi host berdasarkan model SPECpower?", nT),
  N("Apakah perilaku ketiga algoritma berbeda pada workload sintetis, trace nyata NASA iPSC/860, dan GoCJ, dan apa penyebabnya?", nT),
  N("Bagaimana hasilnya berubah antara skenario 3 datacenter dan 2 datacenter?", nT),
  H2("1.3 Tujuan"),
  N("Mengimplementasikan PEFT, FCFS, dan Round Robin sebagai broker di CloudSim Plus 8.0.0.", nG),
  N("Membangun dua skenario infrastruktur multi-datacenter heterogen (`3dc` dan `2dc`) dengan bukti bahwa tidak ada host yang menganggur.", nG),
  N("Mengukur makespan, turnaround time, waiting time, throughput, utilisasi CPU, load balance, dan energi SPECpower pada skala 100 – 10.000 task.", nG),
  N("Menganalisis perbedaan perilaku algoritma antar-dataset dan antar-skenario.", nG),
  H2("1.4 Batasan"),
  B("**Bag-of-tasks**: task independen tanpa ketergantungan antar-task."),
  B("**Non-preemptive**: task yang sudah berjalan tidak diinterupsi atau dipindahkan (`CloudletSchedulerSpaceShared`)."),
  B("**Kesesuaian kapasitas**: task berkebutuhan *k* PE hanya ditempatkan pada VM dengan jumlah PE ≥ *k*; satu task berjalan utuh pada satu VM."),
  B("**Model energi**: linier SPECpower per host (daya idle dan maksimum dari benchmark), tanpa DVFS dan tanpa mematikan host."),
];

const hostRows3 = [
  ["DC-1: Jakarta (Entry-Level)", "A", "4", "8", "1.000", "8 GB", "93,7 / 135,0"],
  ["", "B", "4", "8", "1.500", "16 GB", "105,0 / 175,0"],
  ["DC-2: Surabaya (Standard)", "B", "4", "8", "1.500", "16 GB", "105,0 / 175,0"],
  ["", "C", "4", "8", "2.000", "24 GB", "120,0 / 225,0"],
  ["DC-3: Bandung (Premium)", "C", "4", "8", "2.000", "24 GB", "120,0 / 225,0"],
  ["", "D", "4", "8", "3.000", "32 GB", "140,0 / 300,0"],
  ["**Total**", "", "**24**", "**192**", "**352.000 MIPS**", "**480 GB**", ""],
];
const hostRows2 = [
  ["DC-1: Jakarta (Standard)", "A", "3", "16", "1.000", "32 GB", "93,7 / 135,0"],
  ["", "B", "2", "16", "1.500", "32 GB", "105,0 / 175,0"],
  ["DC-2: Bandung (Premium)", "C", "3", "16", "2.000", "64 GB", "120,0 / 225,0"],
  ["", "D", "2", "16", "3.000", "64 GB", "140,0 / 300,0"],
  ["**Total**", "", "**10**", "**160**", "**288.000 MIPS**", "**480 GB**", ""],
];
const hostHead = ["Datacenter", "Tipe", "Unit", "PE/host", "MIPS/PE", "RAM/host", "P idle / P max (W)"];
const hostW = [2626, 700, 700, 900, 1500, 1100, 1500];
const vmHead = ["Datacenter", "Kelas VM", "Jumlah", "PE", "MIPS/PE", "RAM", "Bandwidth", "Storage"];
const vmW = [2426, 1000, 900, 700, 1000, 900, 1100, 1000];

const allocTail = (s) => lastLines(`results/${s}/logs/testAllocation.txt`, 1)[0].trim();
const alloc2 = (() => {
  const l = fs.readFileSync(R("results/2dc/logs/testAllocation.txt"), "utf8").split(/\r?\n/);
  const i = l.findIndex((x) => x.includes("BUKTI VERIFIKASI"));
  return l.slice(i + 4).filter((x) => x.trim().startsWith("| DC")).map((x) => {
    const c = x.split("|").map((y) => y.trim()).filter(Boolean);
    return [c[0], c[1], c[2], c[3], c[4]];
  });
})();

const bab2 = [
  H1("BAB II  ARSITEKTUR INFRASTRUKTUR CLOUD"),
  H2("2.1 Dua Skenario Infrastruktur"),
  P("Kedua skenario didefinisikan pada `Skenario.java` dan dibangun oleh `DatacenterBuilder.java`. Seluruh VM dari semua datacenter disubmit ke **satu broker global**, sehingga broker dapat menempatkan task lintas datacenter. Skenario dipilih saat menjalankan simulasi dengan parameter `-Pskenario=3dc` (default) atau `-Pskenario=2dc`."),
  ...table("Ringkasan dua skenario infrastruktur",
    ["Skenario", "Datacenter", "Host", "VM", "Kapasitas VM", "Penempatan VM"],
    [["`3dc`", "3 (Jakarta, Surabaya, Bandung)", "24 (8/DC)", "30 (10/DC)", "120.000 MIPS", "18 host × 1 VM, 6 host × 2 VM"],
      ["`2dc`", "2 (Jakarta, Bandung)", "10 (5/DC)", "40 (20/DC)", "200.000 MIPS", "10 host × 4 VM"]],
    [900, 2126, 1100, 1100, 1300, 2500]),
  H2("2.2 Skenario 3 Datacenter (3dc)"),
  P("Setiap datacenter berisi 8 host dari dua tipe yang berbeda, masing-masing 8 PE, penyimpanan 1 TB, dan jaringan 10 Gbps. Datacenter dibedakan berdasarkan tingkat kinerja: entry-level di Jakarta, standard di Surabaya, dan premium di Bandung."),
  ...table("Konfigurasi host skenario 3dc", hostHead, hostRows3, hostW),
  ...table("Konfigurasi VM skenario 3dc", vmHead,
    [["DC-1: Jakarta", "Small", "10", "1", "1.000", "1 GB", "250 Mbps", "10 GB"],
      ["DC-2: Surabaya", "Medium", "10", "2", "1.500", "2 GB", "250 Mbps", "10 GB"],
      ["DC-3: Bandung", "Large", "10", "4", "2.000", "4 GB", "250 Mbps", "10 GB"]], vmW),
  ...figure("docs/images/3dc/arsitektur_cloud.png", "Arsitektur skenario 3dc (digambar otomatis dari Skenario.java).", 330),
  H2("2.3 Skenario 2 Datacenter (2dc)"),
  P("Skenario kedua memakai lebih sedikit host fisik yang lebih besar (16 PE per host) dan lebih banyak VM. Setiap datacenter berisi 5 host dan 20 VM, sehingga setiap host menampung tepat 4 VM."),
  ...table("Konfigurasi host skenario 2dc", hostHead, hostRows2, hostW),
  ...table("Konfigurasi VM skenario 2dc", vmHead,
    [["DC-1: Jakarta", "Medium", "20", "2", "1.000", "2 GB", "250 Mbps", "10 GB"],
      ["DC-2: Bandung", "Large", "20", "4", "2.000", "4 GB", "250 Mbps", "10 GB"]], vmW),
  ...figure("docs/images/2dc/arsitektur_cloud.png", "Arsitektur skenario 2dc (digambar otomatis dari Skenario.java).", 330),
  H2("2.4 Penempatan VM ke Host"),
  P("VM ditempatkan ke host dengan kebijakan **penyerataan** (`VmAllocationPolicyRoundRobin`), yaitu VM dibagikan bergiliran ke setiap host. Kebijakan ini dipilih agar tidak ada host yang menganggur. Dua alternatif yang dibandingkan adalah *First-Fit* (`VmAllocationPolicySimple`), yang menempatkan VM di host pertama yang muat sehingga VM menumpuk di host awal, dan *Best-Fit* (`VmAllocationPolicyBestFit`), yang memilih host dengan sisa resource paling sedikit sehingga sebagian host dibiarkan kosong."),
  P(`Hasil verifikasi \`./gradlew testAllocation\`: skenario 3dc menghasilkan "${allocTail("3dc").replace("Status: ", "")}", dan skenario 2dc menghasilkan "${allocTail("2dc").replace("Status: ", "")}". Rincian penempatan skenario 2dc:`),
  ...table("Penempatan VM ke host pada skenario 2dc (output testAllocation)", ["Datacenter", "Host", "Kapasitas", "RAM terpakai", "VM ditempatkan (ID)"],
    alloc2, [2626, 700, 1500, 1700, 2500], { size: 16 }),
  H2("2.5 Model Energi SPECpower"),
  P("Setiap host memakai model daya linier dengan nilai daya idle dan maksimum dari benchmark SPECpower (Beloglazov & Buyya, 2012). Daya idle host berkisar 47 – 69% dari daya maksimum, sehingga lamanya host menyala sangat menentukan total energi. Energi tiap host dihitung dari waktu sibuk dan waktu idle selama makespan:"),
  EQ("E_host = P_max · t_busy + P_idle · (Makespan − t_busy)"),
  EQ("t_busy = min(Makespan, Σ (waktu CPU task × PE task) / PE host)"),
  EQ("E_total (kWh) = Σ E_host (J) / 3.600.000"),
];

const nP = NL();
const bab3 = [
  H1("BAB III  ALGORITMA PENJADWALAN"),
  H2("3.1 PEFT (Predict Earliest Finish Time)"),
  P("PEFT diperkenalkan oleh Arabnejad dan Barbosa (2014) untuk penjadwalan DAG pada sistem heterogen. PEFT memakai *Optimistic Cost Table* (OCT) yang memperkirakan biaya minimum dari sebuah task hingga task terakhir pada graf. Pada model *bag-of-tasks*, task tidak memiliki penerus, sehingga OCT bernilai nol dan pemilihan VM bergantung sepenuhnya pada *Earliest Finish Time* (EFT):"),
  EQ("OCT(t_i, v_j) = 0   untuk setiap task tanpa penerus"),
  P("Implementasi pada `PeftBroker.java` mengikuti langkah berikut:", { keepNext: true, after: 60 }),
  N("**Urutan task**: berdasarkan waktu kedatangan *a_i*; task yang tiba bersamaan diurutkan dari yang terpanjang (*Longest Processing Time*).", nP),
  N("**Filter kapasitas**: hanya VM dengan jumlah PE ≥ kebutuhan PE task yang menjadi kandidat.", nP),
  N("**Hitung EST, EET, dan EFT** untuk setiap kandidat VM *v_j*, dengan broker mencatat waktu bebas setiap core VM.", nP),
  N("**Pilih VM** dengan EFT terkecil; bila seri, dipilih VM dengan MIPS lebih tinggi. Waktu bebas core VM terpilih lalu diperbarui.", nP),
  EQ("EST(t_i, v_j) = max( a_i , waktu bebas core ke-P(t_i) pada v_j )"),
  EQ("EET(t_i, v_j) = L(t_i) / MIPS(v_j)"),
  EQ("EFT(t_i, v_j) = EST(t_i, v_j) + EET(t_i, v_j)"),
  EQ("v* = argmin_{v_j : PE(v_j) ≥ P(t_i)} EFT(t_i, v_j)"),
  P("Kompleksitas waktu penjadwalan adalah O(n · m · p log p) untuk *n* task, *m* VM, dan *p* PE per VM."),
  H2("3.2 First Come First Served (FCFS)"),
  P("`FcfsBroker.java` mengurutkan task berdasarkan waktu kedatangan, lalu membagikannya bergiliran ke VM yang memenuhi kebutuhan PE. Penunjuk giliran disimpan terpisah untuk setiap kebutuhan PE (1, 2, atau 4). FCFS tidak memperhitungkan panjang task maupun kecepatan VM."),
  H2("3.3 Round Robin (RR)"),
  P("`RoundRobinBroker.java` membagikan task bergiliran ke VM yang memenuhi kebutuhan PE sesuai urutan task masuk, tanpa pengurutan, juga dengan penunjuk giliran per kebutuhan PE."),
  P("Karena logika penugasannya sama, FCFS dan Round Robin hanya berbeda pada urutan task. Pada trace NASA, urutan masuk sudah sama dengan urutan kedatangan; pada GoCJ, semua task tiba bersamaan. Pada kedua dataset ini FCFS dan RR menghasilkan jadwal yang identik. Pada dataset sintetis urutan masuknya acak, sehingga hasil keduanya berbeda."),
];

const bab4 = [
  H1("BAB IV  METODOLOGI DAN DATASET"),
  H2("4.1 Dataset Sintetis Terkontrol"),
  P("Dataset sintetis dibangkitkan dengan seed tetap (42) untuk 100 – 1.000 task (kelipatan 100) dan 1.000 – 10.000 task (kelipatan 1.000). Setiap dataset disimpan sebagai bukti pada `dataset/synthetic_dataset_N.csv`, dan simulasi utama maupun batch membaca berkas yang sama."),
  ...table("Komposisi dataset sintetis", ["Kelas", "Porsi", "Panjang (MI)", "PE", "Waktu kedatangan"],
    [["Kecil", "30%", "500 – 5.000", "1", "uniform 0 – 100 s"], ["Sedang", "40%", "5.000 – 50.000", "2", "uniform 0 – 200 s"],
      ["Besar", "30%", "50.000 – 500.000", "4", "uniform 0 – 300 s"]], [1500, 1000, 2526, 800, 3200]),
  H2("4.2 Trace NASA Ames iPSC/860"),
  P("Trace asli diambil dari Parallel Workloads Archive (Feitelson dkk., 2014), versi *cleaned* `NASA-iPSC-1993-3.1-cln.swf.gz` yang merekam 18.239 job pada superkomputer Intel iPSC/860 128 node di NASA Ames selama Oktober – Desember 1993 (SHA-256 `5589ae9b…7f29`). Job dikonversi menjadi cloudlet sebagai berikut:"),
  B("*Submit time* (field 2) → waktu kedatangan relatif terhadap job pertama."),
  B("*Run time* (field 4) × 1.000 MIPS → panjang task (MI). Job dengan run time ≤ 0 dilewati."),
  B("Jumlah prosesor (field 5) → 1, 2, atau 4 PE; permintaan lebih dari 2 prosesor dibatasi 4 PE sesuai VM terbesar."),
  ...table("Profil 1.000 job pertama trace NASA iPSC/860", ["Karakteristik", "Nilai"],
    [["Job 1 PE", "321 (32,1%), 1.000 – 1.965.000 MI"], ["Job 2 PE", "90 (9,0%), 5.000 – 5.226.000 MI"],
      ["Job 4 PE", "589 (58,9%), 3.000 – 19.761.000 MI"], ["Rata-rata panjang", "624.381 MI"],
      ["Rentang kedatangan", "0 – 587.055 s (± 6,8 hari)"]], [3000, 6026]),
  H2("4.3 GoCJ (Google Cloud Jobs)"),
  P("GoCJ adalah dataset resmi Hussain dan Aleem (2018) di Mendeley Data yang dibentuk dari karakteristik Google cluster trace. Berkas `GoCJ_Dataset_100.txt` hingga `GoCJ_Dataset_1000.txt` berisi panjang job dalam MI; untuk 1.000 job rentangnya 15.000 – 900.000 MI (median 93.000 MI). Karena GoCJ tidak memuat waktu kedatangan maupun kebutuhan prosesor, diasumsikan semua job tiba pada t = 0 dan masing-masing membutuhkan 1 PE."),
  H2("4.4 Metrik Evaluasi"),
  ...table("Metrik evaluasi", ["Metrik", "Definisi", "Target"],
    [["Makespan", "max waktu selesai task", "Minimum"], ["Turnaround time", "rata-rata (selesai − kedatangan)", "Minimum"],
      ["Waiting time", "rata-rata waktu tunggu sebelum eksekusi", "Minimum"], ["Throughput", "jumlah task / makespan", "Maksimum"],
      ["Utilisasi CPU", "rata-rata (waktu sibuk VM / makespan)", "Maksimum"],
      ["Load balance", "simpangan baku jumlah task per VM", "Deskriptif"], ["Energi", "Persamaan (1) – (3), dalam kWh", "Minimum"]],
    [2000, 5026, 2000]),
  H2("4.5 Protokol Eksperimen"),
  B("**Eksperimen utama** (`./gradlew runAll`): 1.000 task sintetis dan 1.000 job NASA untuk ketiga algoritma, disertai verifikasi sinkronisasi per datacenter."),
  B("**Eksperimen berskala** (`./gradlew runBatch`): sintetis dan NASA 100 – 1.000, sintetis 1.000 – 10.000, serta GoCJ 100 – 1.000. Setiap konfigurasi dijalankan 3 kali dan dirata-rata."),
  B("Kedua eksperimen dijalankan untuk skenario `3dc` dan `2dc`. Seluruh hasil disimpan sebagai CSV di `results/{skenario}/` beserta log terminal di `results/{skenario}/logs/`."),
];

function hasil(s, label, bab) {
  const o = [
    H1(`BAB ${bab}  HASIL SKENARIO ${label}`),
    H2(`${bab === "V" ? 5 : 6}.1 Eksperimen Utama: Sintetis 1.000 Task`),
    ...mainTable(s, "syn", `Hasil dataset sintetis 1.000 task, skenario ${s} (nilai terbaik ditebalkan)`),
    H2(`${bab === "V" ? 5 : 6}.2 Eksperimen Utama: NASA iPSC/860 1.000 Job`),
    ...mainTable(s, "nasa", `Hasil trace NASA iPSC/860 1.000 job, skenario ${s}`),
    H2(`${bab === "V" ? 5 : 6}.3 Distribusi Beban per Datacenter`),
    P(`Modul \`DatacenterSynchronizer\` memverifikasi bahwa seluruh 1.000 task selesai tanpa ada yang hilang atau terduplikasi, lalu merekap beban dan energi tiap datacenter.`),
    ...syncTable(s, `Distribusi task dan energi per datacenter, skenario ${s}`),
    H2(`${bab === "V" ? 5 : 6}.4 Skala 100 – 1.000 Task`),
    ...batchTable(s, "bSyn", [MK, EN(3)], `Dataset sintetis 100 – 1.000 task, skenario ${s} (rata-rata 3 run)`),
    ...batchTable(s, "bNasa", [["Turnaround (s)", "AvgTurnaround(s)", 2], ["Waiting (s)", "AvgWaiting(s)", 2]],
      `Trace NASA iPSC/860 100 – 1.000 job, skenario ${s} (rata-rata 3 run)`, false),
    H2(`${bab === "V" ? 5 : 6}.5 Skala 1.000 – 10.000 Task (Sintetis)`),
    ...batchTable(s, "bBig", [MK, EN(2)], `Dataset sintetis 1.000 – 10.000 task, skenario ${s} (rata-rata 3 run)`),
    H2(`${bab === "V" ? 5 : 6}.6 GoCJ 100 – 1.000 Job`),
    ...batchTable(s, "bGocj", [MK, EN(3)], `Dataset GoCJ 100 – 1.000 job, skenario ${s} (rata-rata 3 run)`),
    H2(`${bab === "V" ? 5 : 6}.7 Grafik`),
  ];
  const charts = [
    ["grafik_makespan_sintetis_100_1000", "Makespan dataset sintetis 100 – 1.000 task"],
    ["grafik_makespan_sintetis_1000_10000", "Makespan dataset sintetis 1.000 – 10.000 task"],
    ["grafik_turnaround_nasa_100_1000", "Turnaround time trace NASA iPSC/860"],
    ["grafik_waiting_time_nasa_100_1000", "Waiting time trace NASA iPSC/860"],
    ["grafik_makespan_gocj_100_1000", "Makespan dataset GoCJ"],
    ["grafik_energi_specpower_1000_10000", "Energi SPECpower dataset sintetis 1.000 – 10.000 task"],
    ["grafik_load_balance_1000_10000", "Load balance dataset sintetis 1.000 – 10.000 task"],
  ];
  for (const [file, t] of charts) o.push(...figure(`docs/images/${s}/${file}.png`, `${t}, skenario ${s}.`, 560));
  return o;
}

const nK = NL();
const lb3 = s3.bBig;
const bab7 = [
  H1("BAB VII  PEMBAHASAN"),
  H2("7.1 Perbedaan Perilaku Antar-Dataset"),
  P(`**Sintetis: kedatangan padat (0 – 300 s), beban terbatas komputasi.** Seluruh VM mengantre sekaligus, sehingga pilihan VM sangat menentukan. PEFT menempatkan setiap task di VM yang paling cepat menyelesaikannya, sedangkan FCFS dan RR membagi rata tanpa melihat kecepatan VM. Keunggulan makespan PEFT terhadap FCFS adalah ${rng(s3.bSyn, "", 100, 1000)} (\`3dc\`) dan ${rng(s2.bSyn, "", 100, 1000)} (\`2dc\`) pada 100 – 1.000 task, lalu ${rng(s3.bBig, "", 2000, 10000)} (\`3dc\`) dan ${rng(s2.bBig, "", 2000, 10000)} (\`2dc\`) pada 2.000 – 10.000 task. Selisih relatif mengecil pada skala besar karena semua VM jenuh, tetapi selisih absolutnya terus bertambah: pada 10.000 task, ${f(+big3.FCFS["AvgMakespan(s)"] - +big3.PEFT["AvgMakespan(s)"])} s (\`3dc\`) dan ${f(+big2.FCFS["AvgMakespan(s)"] - +big2.PEFT["AvgMakespan(s)"])} s (\`2dc\`).`),
  P(`**NASA iPSC/860: kedatangan renggang (± 6,8 hari), beban terbatas waktu kedatangan.** Makespan ditentukan oleh job terakhir yang tiba pada detik ke-587.055, sehingga ketiga algoritma selesai pada waktu yang hampir sama. Utilisasi CPU di bawah 2%, sehingga energi didominasi daya idle dan selisihnya hanya ${f(-pct(na3.ep, na3.ef))}% (\`3dc\`) dan ${f(-pct(na2.ep, na2.ef))}% (\`2dc\`). Keunggulan PEFT terlihat pada kualitas layanan: waiting time selalu 0 karena setiap job dikirim ke core yang sudah bebas, dan turnaround time lebih pendek karena job diarahkan ke VM 2.000 MIPS.`),
  P(`**GoCJ: semua job tiba bersamaan dengan variasi panjang besar (15.000 – 900.000 MI).** FCFS dan RR menempatkan job besar di VM lambat dan membentuk antrean panjang, sedangkan PEFT memilih VM yang selesai paling awal. Pada 100 – 400 job (\`3dc\`) dan 100 – 700 job (\`2dc\`), makespan PEFT tertahan di 450,21 s. Angka ini adalah durasi job terbesar (900.000 MI ÷ 2.000 MIPS), yaitu batas bawah yang tidak dapat dilampaui algoritma apa pun pada VM tersebut.`),
  H2("7.2 Perbandingan Skenario 3dc dan 2dc"),
  P(`Pada sintetis 1.000 task, makespan PEFT turun dari ${f(syn3.p)} s (\`3dc\`) menjadi ${f(syn2.p)} s (\`2dc\`), dan energinya dari ${f(en3.p, 3)} kWh menjadi ${f(en2.p, 3)} kWh. Skenario \`2dc\` memiliki kapasitas VM lebih besar (200.000 vs 120.000 MIPS, dengan 20 VM Large dibanding 10), sementara host fisiknya lebih sedikit (10 vs 24), sehingga daya idle yang terbuang jauh lebih kecil.`),
  P(`Pada NASA, hasil PEFT (makespan, turnaround, dan waiting time) identik di kedua skenario. Penyebabnya, PEFT selalu memakai VM Large 2.000 MIPS yang spesifikasinya sama di kedua skenario. Yang berbeda hanya energi (${f(na3.ep, 1)} vs ${f(na2.ep, 1)} kWh), karena jumlah host yang menyala berbeda.`),
  H2("7.3 Interpretasi Load Balance"),
  P(`Metrik load balance adalah simpangan baku jumlah task per VM. FCFS dan RR membagi task per kelas kebutuhan PE: task 4 PE hanya ke VM Large, task 2 PE ke VM Medium dan Large, dan task 1 PE ke semua VM. Akibatnya, pada sintetis \`3dc\` VM Large menerima jauh lebih banyak task (SD ${f(+lb3[1000].FCFS.AvgLoadBalanceStdDev)} pada 1.000 task, ${f(+lb3[10000].FCFS.AvgLoadBalanceStdDev)} pada 10.000 task), sedangkan PEFT mengalihkan task kecil ke VM Small/Medium yang lebih dulu bebas (SD ${f(+lb3[1000].PEFT.AvgLoadBalanceStdDev)} dan ${f(+lb3[10000].PEFT.AvgLoadBalanceStdDev)}). Sebaliknya, pada NASA (SD ${f(na3.lp)} vs ${f(na3.lf)}), GoCJ, dan sintetis \`2dc\`, PEFT justru lebih tidak merata karena sengaja menumpuk task di VM tercepat. Jadi metrik ini menggambarkan pola distribusi task, bukan efisiensi.`),
  H2("7.4 Konsistensi dan Reprodusibilitas"),
  P(`Ketiga algoritma deterministik dan semua dataset dibaca dari berkas yang sama, sehingga tiga kali pengulangan menghasilkan nilai identik (simpangan baku antar-run = 0) di semua konfigurasi. Dari ${nCfg} konfigurasi batch, PEFT menghasilkan makespan terpendek atau setara pada ${nCfg - notBest.length} konfigurasi. ${notBest.length ? `Pengecualiannya adalah NASA ${notBest[0].n} job pada kedua skenario, di mana PEFT lebih lama ${f(notBest[0].d)} s karena makespan ditentukan oleh job terakhir yang tiba.` : ""}`),
];

const bab8 = [
  H1("BAB VIII  KESIMPULAN DAN SARAN"),
  H2("8.1 Kesimpulan"),
  N(`PEFT memberikan makespan terpendek atau setara pada ${nCfg - notBest.length} dari ${nCfg} konfigurasi uji. Keunggulan terbesar terjadi pada beban padat: sintetis 1.000 task (${fp(pct(syn3.p, syn3.f))} pada \`3dc\`, ${fp(pct(syn2.p, syn2.f))} pada \`2dc\` terhadap FCFS) dan GoCJ 1.000 job (${fp(pct(+g3.PEFT["AvgMakespan(s)"], +g3.FCFS["AvgMakespan(s)"]))} dan ${fp(pct(+g2.PEFT["AvgMakespan(s)"], +g2.FCFS["AvgMakespan(s)"]))}).`, nK),
  N(`Pada trace nyata yang renggang (NASA iPSC/860), makespan ditentukan oleh waktu kedatangan. Keunggulan PEFT ada pada kualitas layanan: waiting time 0 dan turnaround time ${f(-pct(na3.tp, na3.tf))}% (\`3dc\`) serta ${f(-pct(na2.tp, na2.tf))}% (\`2dc\`) lebih pendek dari FCFS.`, nK),
  N(`Penghematan energi PEFT berasal dari makespan yang lebih pendek, karena host lebih cepat selesai dan waktu idle berkurang: ${fp(pct(en3.p, en3.f))} (\`3dc\`) dan ${fp(pct(en2.p, en2.f))} (\`2dc\`) pada sintetis 1.000 task.`, nK),
  N("Skenario `2dc` lebih efisien daripada `3dc` untuk workload yang sama karena kapasitas VM-nya lebih besar dengan jumlah host yang lebih sedikit.", nK),
  N("FCFS dan Round Robin pada implementasi ini hanya berbeda urutan task, sehingga hasil keduanya identik ketika urutan masuk sama dengan urutan kedatangan (NASA) atau semua task tiba bersamaan (GoCJ).", nK),
  H2("8.2 Saran Pengembangan"),
  B("Menguji PEFT pada workload berbentuk DAG agar komponen OCT benar-benar berperan."),
  B("Menambahkan baseline yang sadar kapasitas, seperti Min-Min atau Max-Min, agar pembanding tidak hanya kebijakan tanpa informasi kapasitas VM."),
  B("Menambahkan pemodelan DVFS atau mematikan host yang idle untuk mengurangi dominasi daya idle pada workload renggang seperti NASA."),
];

const lampiran = [
  H1("LAMPIRAN  CUPLIKAN OUTPUT TERMINAL"),
  P("Cuplikan berikut disalin dari log eksekusi asli di `results/{skenario}/logs/`. Angka pada log sama dengan angka pada tabel di laporan ini."),
  H3("A. Tabel perbandingan sintetis 1.000 task, skenario 3dc (./gradlew runAll)"),
  ...code(logExcerpt("3dc", "TABEL PERBANDINGAN", 15)),
  H3("B. Laporan sinkronisasi PEFT sintetis, skenario 2dc (./gradlew runAll -Pskenario=2dc)"),
  ...code(logExcerpt("2dc", "LAPORAN SINKRONISASI", 15)),
  H3("C. Profil dataset NASA iPSC/860 (1.000 job pertama)"),
  ...code(logExcerpt("3dc", "PROFIL DATASET: NASA", 8)),
];

const refs = [
  "Arabnejad, H., & Barbosa, J. G. (2014). List scheduling algorithm for heterogeneous systems by an optimistic cost table. IEEE Transactions on Parallel and Distributed Systems, 25(3), 682–694.",
  "Topcuoglu, H., Hariri, S., & Wu, M.-Y. (2002). Performance-effective and low-complexity task scheduling for heterogeneous computing. IEEE Transactions on Parallel and Distributed Systems, 13(3), 260–274.",
  "Silva Filho, M. C., Oliveira, R. L., Monteiro, C. C., Inácio, P. R. M., & Freire, M. M. (2017). CloudSim Plus: A cloud computing simulation framework pursuing software engineering principles for improved modularity, extensibility and correctness. IFIP/IEEE Symposium on Integrated Network and Service Management (IM), 400–406.",
  "Beloglazov, A., & Buyya, R. (2012). Optimal online deterministic algorithms and adaptive heuristics for energy and performance efficient dynamic consolidation of virtual machines in cloud data centers. Concurrency and Computation: Practice and Experience, 24(13), 1397–1420.",
  "Feitelson, D. G., Tsafrir, D., & Krakov, D. (2014). Experience with using the Parallel Workloads Archive. Journal of Parallel and Distributed Computing, 74(10), 2967–2982.",
  "Hussain, A., & Aleem, M. (2018). GoCJ: Google Cloud Jobs dataset for distributed and cloud computing infrastructures. Data, 3(4), 38.",
  "Arunarani, A. R., Manjula, D., & Sugumaran, V. (2019). Task scheduling techniques in cloud computing: A literature survey. Future Generation Computer Systems, 91, 407–415.",
  "Abraham, O. L., Ngadi, M. A., Sharif, J. B. M., & Sidik, M. K. M. (2025). Multi-objective optimization techniques in cloud task scheduling: A systematic literature review. IEEE Access, 13, 12255–12291.",
];
const pustaka = [
  H1("DAFTAR PUSTAKA"),
  ...refs.map((r) => new Paragraph({ children: runs(r, { size: 20 }), spacing: { after: 140, line: 300 },
    indent: { left: 567, hanging: 567 }, alignment: AlignmentType.LEFT })),
];

// ---------------------------------------------------------------- dokumen
const numberings = [{ reference: "bul", levels: [
  { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 400, hanging: 260 } } } },
  { level: 1, format: LevelFormat.BULLET, text: "–", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 800, hanging: 260 } } } }] }];
for (let i = 0; i < numRef; i++) numberings.push({ reference: `num${i}`, levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.",
  alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 400, hanging: 340 } }, run: { font: FONT } } }] });

const header = new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT,
  border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 4 } },
  children: [new TextRun({ text: "Laporan Hasil Uji Coba · Kelompok 8 · SOKA 2026", font: FONT, size: 16, color: MUTE })] })] });
const footer = new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
  children: [new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 18 })] })] });
const page = { page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, bottom: 1300, left: 1440, right: 1440, header: 700, footer: 600 } } };

const doc = new Document({
  creator: "Kelompok 8 SOKA", title: "Laporan Hasil Uji Coba Kelompok 8",
  styles: {
    default: { document: { run: { font: FONT, size: 21, color: INK } } },
    paragraphStyles: [
      { id: "Title", name: "Title", basedOn: "Normal", next: "Normal", run: { font: FONT, size: 30, bold: true },
        paragraph: { alignment: AlignmentType.CENTER, spacing: { after: 300 } } },
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FONT, size: 28, bold: true }, paragraph: { spacing: { after: 280 }, outlineLevel: 0, keepNext: true,
          border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: INK, space: 6 } } } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FONT, size: 24, bold: true }, paragraph: { spacing: { before: 300, after: 140 }, outlineLevel: 1, keepNext: true } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FONT, size: 21, bold: true }, paragraph: { spacing: { before: 220, after: 100 }, outlineLevel: 2, keepNext: true } },
    ],
  },
  numbering: { config: numberings },
  sections: [
    { properties: { ...page, titlePage: true }, headers: { default: header, first: new Header({ children: [] }) },
      footers: { default: footer, first: new Footer({ children: [] }) },
      children: [...cover, ...ringkasan, ...toc, ...bab1, ...bab2, ...bab3, ...bab4,
        ...hasil("3dc", "3 DATACENTER (3dc)", "V"), ...hasil("2dc", "2 DATACENTER (2dc)", "VI"),
        ...bab7, ...bab8, ...lampiran, ...pustaka] },
  ],
});
Packer.toBuffer(doc).then((b) => { fs.writeFileSync(OUT, b); console.log("wrote", OUT, "tables", tabN, "figures", figN, "cfg", nCfg, notBest); });
