package com.kelompok8;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Runner Otomatis Pengujian Batch Berskala (Batch Experiment Suite).
 *
 * Mengakomodasi seluruh catatan revisi dan arahan dari Dosen Pengampu (Bu Henning):
 * 1. Pengujian skala bertahap 100 s.d. 1.000 task (kelipatan 100):
 *    [100, 200, 300, 400, 500, 600, 700, 800, 900, 1000]
 * 2. Pengujian skala beban tinggi 1.000 s.d. 10.000 task (kelipatan 1.000):
 *    [1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000]
 * 3. Protokol multi-run: setiap konfigurasi dieksekusi 3x Run independen,
 *    dan dihitung nilai rata-rata (mean) serta standar deviasinya.
 * 4. Verifikasi determinisme: membuktikan bahwa pada dataset identik,
 *    algoritma deterministik menghasilkan total objective yang konsisten antar-run.
 * 5. Penyimpanan bukti otentik dataset: dataset sintetis yang dibangkitkan
 *    otomatis disimpan ke berkas CSV di direktori 'dataset/'.
 * 6. Ekspor hasil ke CSV di direktori 'results/{skenario}/'.
 *
 * Argumen: [ALL|SCALE_100_1000|SCALE_1000_10000|GOCJ] [3dc|2dc]
 */
public class BatchExperimentSuite {

    private static final String DATASET_DIR = "dataset";

    private static Skenario skenario;
    private static String resultsDir;

    public static void main(String[] args) throws Exception {
        skenario = Skenario.dariArgs(args);
        resultsDir = skenario.resultsDir();
        Files.createDirectories(Path.of(resultsDir));
        Files.createDirectories(Path.of(DATASET_DIR));

        String mode = args.length > 0 && !args[0].toLowerCase().endsWith("dc") ? args[0].toUpperCase() : "ALL";

        System.out.println("====================================================================");
        System.out.println("    SOKA - BATCH EXPERIMENT SUITE (KELOMPOK 8)");
        System.out.println("    Multi-Run (3x) & Multi-Scale Benchmark Suite");
        System.out.println("    Skenario: " + skenario.id + " - " + skenario.ringkasan());
        System.out.println("====================================================================\n");

        if (mode.equals("ALL") || mode.equals("SCALE_100_1000")) {
            runSuite100to1000();
        }

        if (mode.equals("ALL") || mode.equals("SCALE_1000_10000")) {
            runSuite1000to10000();
        }

        if (mode.equals("ALL") || mode.equals("GOCJ")) {
            runSuiteGoCj100to1000();
        }

        System.out.println("\n====================================================================");
        System.out.println("  Semua batch eksperimen telah selesai dieksekusi dan diekspor!     ");
        System.out.println("====================================================================");
    }

    /**
     * SUITE 1: Skala 100 - 1.000 Task (Kelipatan 100)
     * Menguji Sintetis dan NASA iPSC untuk 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000 tasks.
     * Masing-masing 3x Run per algoritma (PEFT, FCFS, Round Robin).
     */
    public static void runSuite100to1000() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  SUITE 1: PENGUJIAN SKALA 100 - 1.000 TASKS (KELIPATAN 100) [3x RUNS]");
        System.out.println("=".repeat(80));

        int[] taskCounts = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
        String[] algorithms = {"PEFT", "FCFS", "RR"};

        List<BatchRunSummary> summaries = new ArrayList<>();

        for (int count : taskCounts) {
            System.out.printf("%n>>> PENGUJIAN WORKLOAD: %,d TASKS <<<%n", count);

            // 1. Sintetis
            List<Cloudlet> synthCloudlets = SwfParser.getOrCreateSyntheticDataset(count, 42L);
            SwfParser.printDatasetCharacteristics(synthCloudlets, "Sintetis " + count + " Task");

            for (String algo : algorithms) {
                BatchRunSummary summary = executeMultiRun(algo, "Synthetic", count, synthCloudlets, 3);
                summaries.add(summary);
            }

            // 2. NASA iPSC (SWF) jika tersedia
            if (Files.exists(Path.of(SwfParser.NASA_SWF))) {
                List<Cloudlet> nasaCloudlets = SwfParser.parse(SwfParser.NASA_SWF, count);
                for (String algo : algorithms) {
                    BatchRunSummary summary = executeMultiRun(algo, "NASA-iPSC", count, nasaCloudlets, 3);
                    summaries.add(summary);
                }
            }
        }

        exportBatchSummariesToCsv(summaries, resultsDir + "/batch_100_1000_results.csv");
        printComparisonSummaryTable(summaries, "RINGKASAN SKALA 100 - 1.000 TASKS (RATA-RATA 3x RUN)");
    }

    /**
     * SUITE 2: Skala Beban Berat 1.000 - 10.000 Task (Kelipatan 1.000)
     * Menguji Sintetis untuk 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000 tasks.
     * Masing-masing 3x Run per algoritma (PEFT, FCFS, Round Robin).
     */
    public static void runSuite1000to10000() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  SUITE 2: PENGUJIAN SKALA BESAR 1.000 - 10.000 TASKS (KELIPATAN 1.000) [3x RUNS]");
        System.out.println("=".repeat(80));

        int[] taskCounts = {1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000};
        String[] algorithms = {"PEFT", "FCFS", "RR"};

        List<BatchRunSummary> summaries = new ArrayList<>();

        for (int count : taskCounts) {
            System.out.printf("%n>>> PENGUJIAN WORKLOAD SINTETIS SKALA BESAR: %,d TASKS <<<%n", count);

            List<Cloudlet> synthCloudlets = SwfParser.getOrCreateSyntheticDataset(count, 42L);
            SwfParser.printDatasetCharacteristics(synthCloudlets, "Sintetis " + count + " Task");

            for (String algo : algorithms) {
                BatchRunSummary summary = executeMultiRun(algo, "Synthetic", count, synthCloudlets, 3);
                summaries.add(summary);
            }
        }

        exportBatchSummariesToCsv(summaries, resultsDir + "/synthetic_1000_10000_results.csv");
        printComparisonSummaryTable(summaries, "RINGKASAN SKALA BESAR 1.000 - 10.000 TASKS (RATA-RATA 3x RUN)");
    }

    /**
     * SUITE 3: Google Cloud Jobs (GoCJ) 100 - 1.000 Task
     */
    public static void runSuiteGoCj100to1000() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  SUITE 3: PENGUJIAN DATASET GoCJ (GOOGLE CLOUD JOBS) 100 - 1.000 TASKS [3x RUNS]");
        System.out.println("=".repeat(80));

        int[] taskCounts = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
        String[] algorithms = {"PEFT", "FCFS", "RR"};

        List<BatchRunSummary> summaries = new ArrayList<>();

        for (int count : taskCounts) {
            List<Cloudlet> gocjCloudlets = SwfParser.loadGoCjDataset(count);
            SwfParser.printDatasetCharacteristics(gocjCloudlets, "GoCJ " + count + " Task");

            for (String algo : algorithms) {
                BatchRunSummary summary = executeMultiRun(algo, "GoCJ", count, gocjCloudlets, 3);
                summaries.add(summary);
            }
        }

        exportBatchSummariesToCsv(summaries, resultsDir + "/gocj_100_1000_results.csv");
        printComparisonSummaryTable(summaries, "RINGKASAN GoCJ 100 - 1.000 TASKS (RATA-RATA 3x RUN)");
    }

    /**
     * Mengeksekusi satu konfigurasi sebanyak numRuns (default 3x) dan menghitung nilai rata-rata.
     */
    private static BatchRunSummary executeMultiRun(
            String algorithm, String datasetType, int taskCount, List<Cloudlet> baseCloudlets, int numRuns) {

        List<MetricsCollector.ExperimentResult> runResults = new ArrayList<>();

        for (int run = 1; run <= numRuns; run++) {
            CloudSimPlus simulation = new CloudSimPlus();
            var multiDc = DatacenterBuilder.buildMultiple(
                    simulation, skenario, DatacenterBuilder.AllocationMode.PENYERATAAN_ROUND_ROBIN, false);

            DatacenterBroker broker = switch (algorithm.toUpperCase()) {
                case "PEFT" -> new PeftBroker(simulation);
                case "FCFS" -> new FcfsBroker(simulation);
                case "RR", "ROUNDROBIN" -> new RoundRobinBroker(simulation);
                default -> new PeftBroker(simulation);
            };

            broker.setDatacenterMapper((lastDc, vm) -> {
                var targetDc = multiDc.getDatacenterForVm(vm);
                return targetDc != null ? targetDc : lastDc;
            });

            // Gandakan cloudlet untuk setiap run agar state bersih
            List<Cloudlet> cloudlets = cloneCloudlets(baseCloudlets);

            broker.submitVmList(multiDc.allVms());
            broker.submitCloudletList(cloudlets);

            simulation.start();

            List<Cloudlet> finished = broker.getCloudletFinishedList();
            String runLabel = String.format("%s [%s %d] Run-%d", algorithm, datasetType, taskCount, run);
            MetricsCollector.ExperimentResult res = MetricsCollector.collectMetrics(
                    runLabel, finished, multiDc.allVms(), multiDc.allHosts(), simulation.clock());

            runResults.add(res);
        }

        // Hitung rata-rata dan deviasi antar run
        BatchRunSummary summary = new BatchRunSummary(algorithm, datasetType, taskCount, numRuns, runResults);
        System.out.printf("    %-4s | %-10s | %,5d tasks | Avg Makespan: %,8.2f s | Avg Energy: %,10.0f J (%.4f kWh) | StdDev Run: %.2f%n",
                algorithm, datasetType, taskCount, summary.avgMakespan, summary.avgEnergyJoules, summary.avgEnergyKWh, summary.stdDevMakespanAcrossRuns);

        return summary;
    }

    private static List<Cloudlet> cloneCloudlets(List<Cloudlet> original) {
        List<Cloudlet> clones = new ArrayList<>(original.size());
        for (Cloudlet c : original) {
            Cloudlet clone = new org.cloudsimplus.cloudlets.CloudletSimple(c.getLength(), c.getPesNumber())
                    .setFileSize(c.getFileSize())
                    .setOutputSize(c.getOutputSize())
                    .setUtilizationModelCpu(new org.cloudsimplus.utilizationmodels.UtilizationModelFull())
                    .setUtilizationModelRam(new org.cloudsimplus.utilizationmodels.UtilizationModelDynamic(0.05))
                    .setUtilizationModelBw(new org.cloudsimplus.utilizationmodels.UtilizationModelDynamic(0.02));
            if (c.getSubmissionDelay() > 0) {
                clone.setSubmissionDelay(c.getSubmissionDelay());
            }
            clone.setId(c.getId());
            clones.add(clone);
        }
        return clones;
    }

    // -------------------------------------------------------------------------
    // RECORD SUMMARY
    // -------------------------------------------------------------------------

    public static class BatchRunSummary {
        public String algorithm;
        public String datasetType;
        public int taskCount;
        public int numRuns;

        public double avgMakespan;
        public double avgTurnaround;
        public double avgWaiting;
        public double avgThroughput;
        public double avgCpuUtil;
        public double avgEnergyJoules;
        public double avgEnergyKWh;
        public double avgLoadBalanceStdDev;

        public double stdDevMakespanAcrossRuns;

        public BatchRunSummary(String algorithm, String datasetType, int taskCount, int numRuns,
                               List<MetricsCollector.ExperimentResult> runs) {
            this.algorithm = algorithm;
            this.datasetType = datasetType;
            this.taskCount = taskCount;
            this.numRuns = numRuns;

            this.avgMakespan = runs.stream().mapToDouble(r -> r.makespan).average().orElse(0.0);
            this.avgTurnaround = runs.stream().mapToDouble(r -> r.avgTurnaroundTime).average().orElse(0.0);
            this.avgWaiting = runs.stream().mapToDouble(r -> r.avgWaitingTime).average().orElse(0.0);
            this.avgThroughput = runs.stream().mapToDouble(r -> r.throughput).average().orElse(0.0);
            this.avgCpuUtil = runs.stream().mapToDouble(r -> r.avgCpuUtilization).average().orElse(0.0);
            this.avgEnergyJoules = runs.stream().mapToDouble(r -> r.totalEnergyJoules).average().orElse(0.0);
            this.avgEnergyKWh = runs.stream().mapToDouble(r -> r.totalEnergyKWh).average().orElse(0.0);
            this.avgLoadBalanceStdDev = runs.stream().mapToDouble(r -> r.loadBalanceStdDev).average().orElse(0.0);

            double variance = runs.stream().mapToDouble(r -> Math.pow(r.makespan - avgMakespan, 2)).average().orElse(0.0);
            this.stdDevMakespanAcrossRuns = Math.sqrt(variance);
        }
    }

    private static void exportBatchSummariesToCsv(List<BatchRunSummary> summaries, String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("Algorithm,Dataset,TaskCount,NumRuns,AvgMakespan(s),AvgTurnaround(s),AvgWaiting(s),AvgThroughput(task/s),AvgCpuUtil(%),AvgEnergy(J),AvgEnergy(kWh),AvgLoadBalanceStdDev,StdDevAcrossRuns");
            for (BatchRunSummary s : summaries) {
                pw.printf("%s,%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%.2f,%.2f,%.6f,%.2f,%.4f%n",
                        s.algorithm, s.datasetType, s.taskCount, s.numRuns,
                        s.avgMakespan, s.avgTurnaround, s.avgWaiting, s.avgThroughput,
                        s.avgCpuUtil, s.avgEnergyJoules, s.avgEnergyKWh, s.avgLoadBalanceStdDev,
                        s.stdDevMakespanAcrossRuns);
            }
            System.out.println("\n  [CSV Export] Berhasil mengekspor ringkasan batch ke: " + filePath);
        } catch (IOException e) {
            System.err.println("  [Error Export] " + e.getMessage());
        }
    }

    private static void printComparisonSummaryTable(List<BatchRunSummary> summaries, String title) {
        System.out.println("\n+================================================================================================================================+");
        System.out.printf("| %-126s |%n", title);
        System.out.println("+================================================================================================================================+");
        System.out.printf("| %-6s | %-10s | %-10s | %-14s | %-14s | %-12s | %-12s | %-14s | %-14s |%n",
                "Algo", "Dataset", "Tasks", "Makespan (s)", "Turnaround (s)", "Waiting (s)", "Throughput", "Energi (kWh)", "Load Bal (SD)");
        System.out.println("+--------+------------+------------+----------------+----------------+--------------+--------------+----------------+----------------+");

        for (BatchRunSummary s : summaries) {
            System.out.printf("| %-6s | %-10s | %,10d | %,14.2f | %,14.2f | %,12.2f | %12.4f | %14.4f | %14.2f |%n",
                    s.algorithm, s.datasetType, s.taskCount,
                    s.avgMakespan, s.avgTurnaround, s.avgWaiting, s.avgThroughput,
                    s.avgEnergyKWh, s.avgLoadBalanceStdDev);
        }
        System.out.println("+================================================================================================================================+\n");
    }
}
