package com.kelompok8;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Pemuat dataset workload menjadi objek Cloudlet CloudSim Plus.
 *
 * 1. NASA iPSC/860 (Standard Workload Format, trace asli Parallel Workloads Archive):
 *    dataset/NASA-iPSC-1993-3.1-cln.swf.gz
 * 2. GoCJ - Google Cloud Jobs (Hussain & Aleem, 2018, Mendeley Data):
 *    dataset/gocj/GoCJ_Dataset_{100..1000}.txt
 * 3. Sintetis terkontrol (dibangkitkan dengan seed tetap, disimpan ke dataset/synthetic_dataset_N.csv)
 *
 * Field SWF yang digunakan sesuai B.4 Desain Dokumen:
 * - Field 1: Job Number
 * - Field 2: Submit Time (detik) -> waktu kedatangan relatif
 * - Field 4: Run Time (detik) -> dikonversi ke Length (MI) berdasarkan MIPS referensi
 * - Field 5: Number of Allocated Processors -> jumlah PE (dibatasi kapasitas VM = 4 PE)
 *
 * Referensi: https://www.cs.huji.ac.il/labs/parallel/workload/swf.html
 */
public class SwfParser {

    /** Trace asli NASA Ames iPSC/860 (versi cleaned, 18.239 job) dari Parallel Workloads Archive. */
    public static final String NASA_SWF = "dataset/NASA-iPSC-1993-3.1-cln.swf.gz";

    /** MIPS referensi untuk konversi runtime ke MI */
    private static final double REFERENCE_MIPS = 1000.0;

    /** Batas maksimum PE per cloudlet (sesuai kapasitas VM terbesar = 4 PE) */
    private static final int MAX_PES_PER_CLOUDLET = 4;

    /** Ukuran file input/output default (dalam bytes) */
    private static final long DEFAULT_FILE_SIZE = 300;
    private static final long DEFAULT_OUTPUT_SIZE = 300;

    /**
     * Parse file SWF dan konversi menjadi list of Cloudlet.
     *
     * @param filePath path ke file SWF (bisa .swf atau .swf.gz)
     * @param maxTasks jumlah maksimum task yang diambil (0 = semua)
     * @return List of Cloudlet
     */
    public static List<Cloudlet> parse(String filePath, int maxTasks) throws IOException {
        List<Cloudlet> cloudlets = new ArrayList<>();
        BufferedReader reader;

        // Handle gzipped files
        if (filePath.endsWith(".gz")) {
            reader = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(new FileInputStream(filePath))));
        } else {
            reader = Files.newBufferedReader(Path.of(filePath));
        }

        String line;
        int taskId = 0;
        double baseSubmitTime = -1;

        while ((line = reader.readLine()) != null) {
            // Skip comments (lines starting with ;)
            line = line.trim();
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }

            // Parse SWF fields (whitespace-separated)
            String[] fields = line.split("\\s+");
            if (fields.length < 5) continue;

            try {
                // Field 1: Job Number (index 0)
                // Field 2: Submit Time in seconds (index 1)
                double submitTime = Double.parseDouble(fields[1]);

                // Field 4: Run Time in seconds (index 3)
                double runTime = Double.parseDouble(fields[3]);

                // Field 5: Number of Allocated Processors (index 4)
                int numProcs = Integer.parseInt(fields[4]);

                // Preprocessing: skip invalid entries (Section B.4)
                if (runTime <= 0) continue;  // Skip tasks with zero/negative runtime
                if (numProcs <= 0) numProcs = 1;
                if (submitTime < 0) submitTime = 0;

                // Track base submit time for relative timing
                if (baseSubmitTime < 0) {
                    baseSubmitTime = submitTime;
                }

                // Convert runtime to MI (Million Instructions)
                long lengthMI = (long) (runTime * REFERENCE_MIPS);
                if (lengthMI <= 0) lengthMI = 1;

                // Map requested processors to available VM PE tiers: 1, 2, or 4 PEs
                int pesNeeded;
                if (numProcs <= 1) {
                    pesNeeded = 1;
                } else if (numProcs <= 2) {
                    pesNeeded = 2;
                } else {
                    pesNeeded = 4;
                }

                // Relative submission delay
                double submissionDelay = submitTime - baseSubmitTime;

                // Create CloudSim Plus Cloudlet
                Cloudlet cloudlet = new CloudletSimple(lengthMI, pesNeeded)
                        .setFileSize(DEFAULT_FILE_SIZE)
                        .setOutputSize(DEFAULT_OUTPUT_SIZE)
                        .setUtilizationModelCpu(new UtilizationModelFull())
                        .setUtilizationModelRam(new UtilizationModelDynamic(0.05))
                        .setUtilizationModelBw(new UtilizationModelDynamic(0.02));

                if (submissionDelay > 0) {
                    cloudlet.setSubmissionDelay(submissionDelay);
                }

                cloudlets.add(cloudlet);
                taskId++;

                if (maxTasks > 0 && taskId >= maxTasks) {
                    break;
                }

            } catch (NumberFormatException e) {
                // Skip malformed lines
                continue;
            }
        }

        reader.close();

        System.out.printf("  [SWF Parser] Berhasil memuat %d task dari %s%n", cloudlets.size(), filePath);
        System.out.printf("  [SWF Parser] Rentang panjang: %d - %d MI%n",
                cloudlets.stream().mapToLong(Cloudlet::getLength).min().orElse(0),
                cloudlets.stream().mapToLong(Cloudlet::getLength).max().orElse(0));

        return cloudlets;
    }

    /**
     * Generate dataset sintetis dengan distribusi terkontrol sesuai dokumen Tugas 3.
     *
     * Pembagian kelas task:
     * - Kecil (30%):  500 - 5.000 MI, 1 PE, arrival uniform [0, 100] s
     * - Sedang (40%): 5.000 - 50.000 MI, 2 PE, arrival uniform [0, 200] s
     * - Besar (30%):  50.000 - 500.000 MI, 4 PE, arrival uniform [0, 300] s
     *
     * @param numTasks jumlah task yang dibangkitkan (default 1000)
     * @param seed random seed untuk reproduktifitas eksperimen
     * @return List of Cloudlet
     */
    public static List<Cloudlet> generateSyntheticDataset(int numTasks, long seed) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        Random rand = new Random(seed);

        int smallCount = (int) (numTasks * 0.30);
        int mediumCount = (int) (numTasks * 0.40);
        int largeCount = numTasks - smallCount - mediumCount;

        // Generate small tasks (30%): 1 PE
        for (int i = 0; i < smallCount; i++) {
            long length = 500 + rand.nextLong(4500);  // 500-5000 MI
            cloudlets.add(createSyntheticCloudlet(length, 1, rand.nextDouble() * 100));
        }

        // Generate medium tasks (40%): 2 PE
        for (int i = 0; i < mediumCount; i++) {
            long length = 5000 + rand.nextLong(45000);  // 5000-50000 MI
            cloudlets.add(createSyntheticCloudlet(length, 2, rand.nextDouble() * 200));
        }

        // Generate large tasks (30%): 4 PE
        for (int i = 0; i < largeCount; i++) {
            long length = 50000 + rand.nextLong(450000);  // 50000-500000 MI
            cloudlets.add(createSyntheticCloudlet(length, 4, rand.nextDouble() * 300));
        }

        // Acak urutan task agar bercampur secara realistis
        Collections.shuffle(cloudlets, rand);

        // Tetapkan ID berurutan untuk mempermudah pelacakan dan bukti dataset
        for (int i = 0; i < cloudlets.size(); i++) {
            cloudlets.get(i).setId(i);
        }

        System.out.printf("  [Sintetis] Dibangkitkan %d tasks (Kecil=%d, Sedang=%d, Besar=%d)%n",
                numTasks, smallCount, mediumCount, largeCount);

        return cloudlets;
    }

    private static Cloudlet createSyntheticCloudlet(long lengthMI, int pes, double submissionDelay) {
        Cloudlet cloudlet = new CloudletSimple(lengthMI, pes)
                .setFileSize(DEFAULT_FILE_SIZE)
                .setOutputSize(DEFAULT_OUTPUT_SIZE)
                .setUtilizationModelCpu(new UtilizationModelFull())
                .setUtilizationModelRam(new UtilizationModelDynamic(0.05))
                .setUtilizationModelBw(new UtilizationModelDynamic(0.02));

        if (submissionDelay > 0) {
            cloudlet.setSubmissionDelay(submissionDelay);
        }

        return cloudlet;
    }

    /**
     * Memuat dataset dari berkas CSV yang tersimpan sebelumnya.
     */
    public static List<Cloudlet> loadCsvDataset(String filePath) throws IOException {
        List<Cloudlet> cloudlets = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(filePath))) {
            String line = br.readLine(); // Header
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 4) continue;
                long id = Long.parseLong(parts[0]);
                long lengthMI = Long.parseLong(parts[1]);
                int pes = Integer.parseInt(parts[2]);
                double submissionDelay = Double.parseDouble(parts[3]);

                Cloudlet c = createSyntheticCloudlet(lengthMI, pes, submissionDelay);
                c.setId(id);
                cloudlets.add(c);
            }
        }
        return cloudlets;
    }

    /**
     * Membangun atau memuat dataset sintetis terdistribusi terkontrol dan
     * otomatis menyimpannya ke berkas CSV sebagai bukti otentik.
     */
    public static List<Cloudlet> getOrCreateSyntheticDataset(int numTasks, long seed) {
        String filename = "dataset/synthetic_dataset_" + numTasks + ".csv";
        Path path = Path.of(filename);
        if (Files.exists(path)) {
            try {
                return loadCsvDataset(filename);
            } catch (IOException ignored) {}
        }
        List<Cloudlet> cloudlets = generateSyntheticDataset(numTasks, seed);
        try {
            Files.createDirectories(path.getParent());
            exportSyntheticDatasetToCsv(cloudlets, filename);
        } catch (IOException ignored) {}
        return cloudlets;
    }

    /**
     * Memuat berkas resmi GoCJ (Google Cloud Jobs, Hussain & Aleem 2018).
     *
     * Setiap baris berkas berisi panjang satu job dalam MI (rentang 15.000 - 900.000 MI).
     * GoCJ tidak memuat waktu kedatangan maupun kebutuhan prosesor, sehingga diasumsikan:
     * - seluruh job tiba bersamaan pada t = 0 (batch / bag-of-tasks),
     * - setiap job membutuhkan 1 PE.
     *
     * @param numTasks 100, 200, ..., 1000 (sesuai nama berkas GoCJ_Dataset_N.txt)
     */
    public static List<Cloudlet> loadGoCjDataset(int numTasks) throws IOException {
        Path path = Path.of("dataset", "gocj", "GoCJ_Dataset_" + numTasks + ".txt");
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Cloudlet c = createSyntheticCloudlet(Long.parseLong(line), 1, 0);
            c.setId(cloudlets.size());
            cloudlets.add(c);
        }
        return cloudlets;
    }

    /**
     * Menyimpan dataset sintetis/GoCJ ke berkas CSV sebagai bukti otentik dataset yang dibangkitkan.
     * Memenuhi kriteria verifikasi: mendokumentasikan spesifikasi setiap cloudlet (ID, length MI, PE, arrival).
     */
    public static void exportSyntheticDatasetToCsv(List<Cloudlet> cloudlets, String filePath) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("TaskId,LengthMI,PesNumber,SubmissionDelay(s),FileSize(B),OutputSize(B)");
            for (Cloudlet c : cloudlets) {
                writer.printf("%d,%d,%d,%.4f,%d,%d%n",
                        c.getId(), c.getLength(), c.getPesNumber(),
                        c.getSubmissionDelay(), c.getFileSize(), c.getOutputSize());
            }
            System.out.println("  [Dataset Bukti] Dataset berhasil disimpan ke: " + filePath);
        } catch (IOException e) {
            System.err.println("  [Peringatan] Gagal menyimpan dataset: " + e.getMessage());
        }
    }

    /**
     * Mencetak ringkasan profil karakteristik dataset secara komprehensif.
     * Menjawab catatan Bu Henning mengenai:
     * - Berapa persen task pendek, sedang, panjang
     * - Panjang tiap data dan rentang MI
     * - Pola kedatangan task
     */
    public static void printDatasetCharacteristics(List<Cloudlet> cloudlets, String datasetName) {
        if (cloudlets.isEmpty()) return;

        int total = cloudlets.size();
        LongSummaryStatistics len = cloudlets.stream().mapToLong(Cloudlet::getLength).summaryStatistics();
        DoubleSummaryStatistics arr = cloudlets.stream().mapToDouble(Cloudlet::getSubmissionDelay).summaryStatistics();

        System.out.printf("%n  PROFIL DATASET: %s (%,d task)%n", datasetName, total);
        System.out.println("  " + "-".repeat(78));
        for (int pes : new int[] {1, 2, 4}) {
            LongSummaryStatistics cls = cloudlets.stream()
                    .filter(c -> c.getPesNumber() == pes)
                    .mapToLong(Cloudlet::getLength).summaryStatistics();
            if (cls.getCount() == 0) continue;
            System.out.printf("    Task %d PE : %,6d (%5.1f%%) | %,d - %,d MI%n",
                    pes, cls.getCount(), cls.getCount() * 100.0 / total, cls.getMin(), cls.getMax());
        }
        System.out.printf("    Panjang   : min %,d MI | maks %,d MI | rata-rata %,.1f MI%n",
                len.getMin(), len.getMax(), len.getAverage());
        System.out.printf("    Kedatangan: min %,.2f s | maks %,.2f s | rata-rata %,.2f s%n",
                arr.getMin(), arr.getMax(), arr.getAverage());
        System.out.println("  " + "-".repeat(78));
    }
}
