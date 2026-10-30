import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Broker real-world Kelompok 8: menjadwalkan task ke VM (pod Kubernetes) sungguhan.
 *
 * Alur:
 *   1. Cek 4 VM aktif            (GET  /info ke tiap VM)
 *   2. Kalibrasi MIPS tiap VM     (jalankan task uji, MIPS per PE = panjang / waktu eksekusi)
 *   3. Siapkan dataset            (sintetis seed 42, atau GoCJ resmi yang diskalakan)
 *   4. Penjadwalan                (PEFT / FCFS / RR menentukan task -> VM)
 *   5. Eksekusi nyata             (task dikirim tepat pada waktu kedatangannya, POST /run)
 *   6. Hitung metrik              (makespan, turnaround, waiting, throughput, utilisasi, energi)
 *
 * Jalankan dari folder realworld/:  java broker/RealWorldBroker.java [ALL|PEFT|FCFS|RR] [synthetic|gocj] [jumlahTask] [folderHasil]
 */
public class RealWorldBroker {

    // ------------------------------------------------------------------ infrastruktur
    static class Vm {
        final String name, host, url;
        final int pes;
        double mips;  // MIPS per PE hasil kalibrasi

        Vm(String name, String host, int port, int pes) {
            this.name = name; this.host = host; this.pes = pes;
            this.url = "http://localhost:" + port;
        }
    }

    /** 1 datacenter, 2 host, 2 VM per host (lihat k8s/vms.yaml). */
    static final List<Vm> VMS = List.of(
            new Vm("vm-1", "host-1", 30001, 1),
            new Vm("vm-2", "host-1", 30002, 2),
            new Vm("vm-3", "host-2", 30003, 2),
            new Vm("vm-4", "host-2", 30004, 4));

    /** Model daya SPECpower host {P_idle, P_max} (W): host-1 = Tipe A, host-2 = Tipe D, sama dengan simulasi. */
    static final Map<String, double[]> HOST_POWER = Map.of(
            "host-1", new double[] {93.7, 135.0},
            "host-2", new double[] {140.0, 300.0});

    static class Task {
        final int id, pes;
        final long length;      // MI per PE
        final double arrival;   // detik sejak eksperimen dimulai
        Vm vm;                  // hasil penjadwalan
        double sent, done, wait, exec;  // hasil eksekusi nyata

        Task(int id, long length, int pes, double arrival) {
            this.id = id; this.length = length; this.pes = pes; this.arrival = arrival;
        }

        Task copy() { return new Task(id, length, pes, arrival); }
    }

    /** Folder keluaran; argumen ke-4 opsional (dipakai batch.sh agar tiap run tidak saling menimpa). */
    static Path outDir = Path.of("hasil");

    static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();

    // ------------------------------------------------------------------ main
    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0].toUpperCase() : "ALL";
        String dataset = args.length > 1 ? args[1].toLowerCase() : "synthetic";
        int n = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        if (args.length > 3) outDir = Path.of(args[3]);

        line('=');
        System.out.println(" REAL-WORLD HEURISTIC SCHEDULING - Kelompok 8 SOKA");
        System.out.println(" Datacenter: cluster Kubernetes 'soka-dc' | 2 host | 4 VM (2 per host)");
        line('=');

        checkVms();
        calibrate();
        List<Task> base = dataset.equals("gocj") ? loadGoCj(n) : syntheticDataset(n);
        printDataset(dataset, base);

        List<String> algos = mode.equals("ALL") ? List.of("PEFT", "FCFS", "RR") : List.of(mode);
        Map<String, Map<String, Double>> summary = new LinkedHashMap<>();
        for (String algo : algos) {
            List<Task> tasks = new ArrayList<>();
            for (Task t : base) tasks.add(t.copy());

            schedule(algo, tasks);
            execute(algo, tasks);
            Map<String, Double> m = metrics(tasks);
            summary.put(algo, m);
            printReport(algo, tasks, m);
            saveTasks(algo, dataset, tasks);
            if (algos.size() > 1) Thread.sleep(3000);  // jeda agar VM kembali idle
        }
        if (summary.size() > 1) printComparison(summary);
        saveSummary(dataset, summary);
    }

    // ------------------------------------------------------------------ 1-2. cek & kalibrasi VM
    static void checkVms() throws Exception {
        System.out.println("\n[1] Memeriksa VM di datacenter:");
        for (Vm vm : VMS) {
            String info = get(vm.url + "/info");
            System.out.printf("    %-5s | %-6s | node %-16s | %d PE | %s%n",
                    vm.name, vm.host, str(info, "host"), vm.pes, vm.url);
        }
    }

    static void calibrate() throws Exception {
        final long calMi = 2000;
        System.out.println("\n[2] Kalibrasi kecepatan VM (task uji " + calMi + " MI di semua PE):");
        for (Vm vm : VMS) {
            String r = post(vm.url + "/run", String.format("{\"id\": -1, \"length\": %d, \"pes\": %d}", calMi, vm.pes));
            vm.mips = calMi / num(r, "exec");
            System.out.printf("    %-5s : %,6.0f MIPS per PE (%d PE)%n", vm.name, vm.mips, vm.pes);
        }
    }

    // ------------------------------------------------------------------ 3. dataset
    /** Sintetis seed 42, komposisi sama dengan simulasi (30% 1 PE, 40% 2 PE, 30% 4 PE), panjang diskalakan untuk demo. */
    static List<Task> syntheticDataset(int n) {
        Random rnd = new Random(42);
        int small = (int) (n * 0.3), medium = (int) (n * 0.4), large = n - small - medium;
        List<Task> list = new ArrayList<>();
        for (int i = 0; i < small; i++) list.add(new Task(0, 500 + rnd.nextInt(1501), 1, rnd.nextDouble() * 5));
        for (int i = 0; i < medium; i++) list.add(new Task(0, 2000 + rnd.nextInt(4001), 2, rnd.nextDouble() * 10));
        for (int i = 0; i < large; i++) list.add(new Task(0, 6000 + rnd.nextInt(6001), 4, rnd.nextDouble() * 15));
        Collections.shuffle(list, rnd);
        List<Task> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Task t = list.get(i);
            out.add(new Task(i, t.length, t.pes, Math.round(t.arrival * 100) / 100.0));
        }
        return out;
    }

    /** GoCJ resmi (Hussain & Aleem, 2018): panjang dibagi 50 agar demo selesai dalam hitungan detik; semua tiba t=0, 1 PE. */
    static List<Task> loadGoCj(int n) throws IOException {
        Path p = Path.of("../dataset/gocj/GoCJ_Dataset_100.txt");
        List<Task> out = new ArrayList<>();
        for (String s : Files.readAllLines(p)) {
            if (s.isBlank()) continue;
            out.add(new Task(out.size(), Long.parseLong(s.trim()) / 50, 1, 0));
            if (out.size() == n) break;
        }
        return out;
    }

    // ------------------------------------------------------------------ 4. penjadwalan
    static void schedule(String algo, List<Task> tasks) {
        switch (algo) {
            case "PEFT" -> peft(tasks);
            case "FCFS" -> {
                List<Task> order = new ArrayList<>(tasks);
                order.sort(Comparator.comparingDouble((Task t) -> t.arrival).thenComparingInt(t -> t.id));
                roundRobinByPe(order);
            }
            case "RR" -> roundRobinByPe(tasks);
            default -> throw new IllegalArgumentException("Algoritma tidak dikenal: " + algo);
        }
    }

    /**
     * PEFT - sama dengan PeftBroker.java di simulasi:
     *   urutkan task (kedatangan, lalu terpanjang dulu), lalu untuk tiap task:
     *   EST = max(kedatangan, waktu core ke-P bebas), EET = panjang / MIPS, EFT = EST + EET,
     *   pilih VM dengan EFT terkecil (seri -> MIPS lebih tinggi), perbarui waktu bebas core.
     */
    static void peft(List<Task> tasks) {
        Map<Vm, double[]> coreReady = new HashMap<>();
        for (Vm vm : VMS) coreReady.put(vm, new double[vm.pes]);

        List<Task> order = new ArrayList<>(tasks);
        order.sort(Comparator.comparingDouble((Task t) -> t.arrival)
                .thenComparing((a, b) -> Long.compare(b.length, a.length)));

        for (Task t : order) {
            Vm best = null;
            double bestEft = Double.MAX_VALUE;
            for (Vm vm : VMS) {
                if (vm.pes < t.pes) continue;                         // batasan kapasitas PE
                double[] ready = coreReady.get(vm).clone();
                Arrays.sort(ready);
                double est = Math.max(t.arrival, ready[t.pes - 1]);   // Earliest Start Time
                double eet = t.length / vm.mips;                      // Estimated Execution Time
                double eft = est + eet;                               // Earliest Finish Time
                if (eft < bestEft - 1e-9 || (Math.abs(eft - bestEft) < 1e-9 && vm.mips > best.mips)) {
                    bestEft = eft;
                    best = vm;
                }
            }
            t.vm = best;
            double[] ready = coreReady.get(best);
            Arrays.sort(ready);
            for (int p = 0; p < t.pes; p++) ready[p] = bestEft;
        }
    }

    /** FCFS & RR (sama dengan FcfsBroker/RoundRobinBroker): bergiliran ke VM yang PE-nya cukup, penunjuk per kebutuhan PE. */
    static void roundRobinByPe(List<Task> order) {
        Map<Integer, Integer> pointer = new HashMap<>();
        for (Task t : order) {
            List<Vm> eligible = VMS.stream().filter(vm -> vm.pes >= t.pes).toList();
            int p = pointer.getOrDefault(t.pes, 0);
            t.vm = eligible.get(p % eligible.size());
            pointer.put(t.pes, p + 1);
        }
    }

    // ------------------------------------------------------------------ 5. eksekusi nyata
    static void execute(String algo, List<Task> tasks) throws InterruptedException {
        System.out.println();
        line('-');
        System.out.printf(" EKSEKUSI %s - %d task dikirim ke VM sesuai waktu kedatangan%n", algo, tasks.size());
        line('-');
        long t0 = System.nanoTime();
        List<java.util.concurrent.Future<Object>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Task t : tasks) {
                futures.add(pool.submit(() -> {
                    sleepUntil(t0, t.arrival);
                    t.sent = seconds(t0);
                    String json = String.format("{\"id\": %d, \"length\": %d, \"pes\": %d}", t.id, t.length, t.pes);
                    String r = post(t.vm.url + "/run", json);
                    t.done = seconds(t0);
                    t.wait = num(r, "wait");
                    t.exec = num(r, "exec");
                    synchronized (System.out) {
                        System.out.printf("  [%6.2f s] task %2d (%d PE, %,6d MI) selesai di %s/%s | tunggu %5.2f s | eksekusi %5.2f s%n",
                                t.done, t.id, t.pes, t.length, t.vm.host, t.vm.name, t.wait, t.exec);
                    }
                    return null;
                }));
            }
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.MINUTES);
        }
        for (var f : futures) {
            try {
                f.get();
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException("Task gagal dieksekusi: " + e.getCause(), e.getCause());
            }
        }
    }

    // ------------------------------------------------------------------ 6. metrik
    static Map<String, Double> metrics(List<Task> tasks) {
        double makespan = tasks.stream().mapToDouble(t -> t.done).max().orElse(0);
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Makespan (s)", makespan);
        m.put("Rata-rata turnaround (s)", tasks.stream().mapToDouble(t -> t.done - t.sent).average().orElse(0));
        m.put("Rata-rata waiting (s)", tasks.stream().mapToDouble(t -> t.wait).average().orElse(0));
        m.put("Throughput (task/s)", tasks.size() / makespan);

        // utilisasi CPU VM = waktu PE sibuk / (PE x makespan)
        double util = 0;
        for (Vm vm : VMS) {
            double busy = tasks.stream().filter(t -> t.vm == vm).mapToDouble(t -> t.exec * t.pes).sum();
            util += busy / (vm.pes * makespan) * 100;
        }
        m.put("Rata-rata utilisasi VM (%)", util / VMS.size());

        // simpangan baku jumlah task per VM
        double mean = (double) tasks.size() / VMS.size(), var = 0;
        for (Vm vm : VMS) var += Math.pow(tasks.stream().filter(t -> t.vm == vm).count() - mean, 2);
        m.put("Load balance (SD task/VM)", Math.sqrt(var / VMS.size()));

        // estimasi energi SPECpower: E = Pmax * t_busy + Pidle * (makespan - t_busy)
        double joule = 0;
        for (var e : HOST_POWER.entrySet()) {
            String host = e.getKey();
            int hostPes = VMS.stream().filter(v -> v.host.equals(host)).mapToInt(v -> v.pes).sum();
            double cpuSec = tasks.stream().filter(t -> t.vm.host.equals(host)).mapToDouble(t -> t.exec * t.pes).sum();
            double busy = Math.min(makespan, cpuSec / hostPes);
            joule += e.getValue()[1] * busy + e.getValue()[0] * (makespan - busy);
        }
        m.put("Estimasi energi (Wh)", joule / 3600);
        return m;
    }

    // ------------------------------------------------------------------ laporan
    static void printDataset(String name, List<Task> tasks) {
        System.out.printf("%n[3] Dataset %s: %d task%n", name, tasks.size());
        for (int pes : new int[] {1, 2, 4}) {
            List<Task> c = tasks.stream().filter(t -> t.pes == pes).toList();
            if (c.isEmpty()) continue;
            System.out.printf("    %d PE : %2d task | %,d - %,d MI%n", pes, c.size(),
                    c.stream().mapToLong(t -> t.length).min().getAsLong(), c.stream().mapToLong(t -> t.length).max().getAsLong());
        }
        System.out.printf("    Kedatangan: 0 - %.2f s%n", tasks.stream().mapToDouble(t -> t.arrival).max().orElse(0));
    }

    static void printReport(String algo, List<Task> tasks, Map<String, Double> m) {
        System.out.printf("%n  Distribusi task %s per VM:%n", algo);
        for (Vm vm : VMS) {
            List<Task> mine = tasks.stream().filter(t -> t.vm == vm).toList();
            System.out.printf("    %-6s/%-5s : %2d task, %6.1f detik-CPU%n", vm.host, vm.name, mine.size(),
                    mine.stream().mapToDouble(t -> t.exec * t.pes).sum());
        }
        System.out.printf("  Hasil %s:%n", algo);
        m.forEach((k, v) -> System.out.printf("    %-28s: %,10.3f%n", k, v));
    }

    static void printComparison(Map<String, Map<String, Double>> s) {
        System.out.println();
        line('=');
        System.out.println(" PERBANDINGAN HASIL EKSEKUSI NYATA");
        line('=');
        System.out.printf(" %-28s", "Metrik");
        s.keySet().forEach(a -> System.out.printf(" | %10s", a));
        System.out.printf(" | %12s%n", "PEFT vs FCFS");
        line('-');
        for (String k : s.values().iterator().next().keySet()) {
            System.out.printf(" %-28s", k);
            s.values().forEach(m -> System.out.printf(" | %,10.3f", m.get(k)));
            if (s.containsKey("PEFT") && s.containsKey("FCFS")) {
                double p = s.get("PEFT").get(k), f = s.get("FCFS").get(k);
                System.out.printf(" | %+11.2f%%", f == 0 ? 0 : (p - f) / f * 100);
            }
            System.out.println();
        }
        line('=');
    }

    static void saveTasks(String algo, String dataset, List<Task> tasks) throws IOException {
        Path out = outDir.resolve("task_" + dataset + "_" + algo + ".csv");
        Files.createDirectories(out.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println("TaskId,LengthMI,Pes,Arrival(s),Vm,Host,Sent(s),Wait(s),Exec(s),Finish(s)");
            for (Task t : tasks) {
                w.printf(Locale.US, "%d,%d,%d,%.2f,%s,%s,%.3f,%.3f,%.3f,%.3f%n", t.id, t.length, t.pes, t.arrival,
                        t.vm.name, t.vm.host, t.sent, t.wait, t.exec, t.done);
            }
        }
    }

    static void saveSummary(String dataset, Map<String, Map<String, Double>> s) throws IOException {
        Path out = outDir.resolve("ringkasan_" + dataset + ".csv");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println("Metrik," + String.join(",", s.keySet()));
            for (String k : s.values().iterator().next().keySet()) {
                StringBuilder sb = new StringBuilder(k);
                s.values().forEach(m -> sb.append(String.format(Locale.US, ",%.4f", m.get(k))));
                w.println(sb);
            }
        }
        System.out.println(" Hasil tersimpan di realworld/" + out.getParent() + "/");
    }

    // ------------------------------------------------------------------ util HTTP & JSON
    static String get(String url) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    static String post(String url, String json) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException(url + " -> HTTP " + r.statusCode());
        return r.body();
    }

    static double num(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\\s*([-0-9.eE]+)").matcher(json);
        if (!m.find()) throw new IllegalStateException("field " + key + " tidak ada: " + json);
        return Double.parseDouble(m.group(1));
    }

    static String str(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "?";
    }

    static double seconds(long t0) {
        return (System.nanoTime() - t0) / 1e9;
    }

    static void sleepUntil(long t0, double offsetSec) throws InterruptedException {
        long ms = (long) (offsetSec * 1000) - (System.nanoTime() - t0) / 1_000_000;
        if (ms > 0) Thread.sleep(ms);
    }

    static void line(char c) {
        System.out.println(String.valueOf(c).repeat(92));
    }
}
