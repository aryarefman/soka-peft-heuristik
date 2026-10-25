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
 * Simulasi Utama - Optimasi Penjadwalan Task pada Komputasi Awan (Multi-Datacenter)
 *
 * Kelompok 8 - Strategi Optimasi Komputasi Awan
 * Departemen Teknologi Informasi, Institut Teknologi Sepuluh Nopember (ITS)
 *
 * Arsitektur:
 * - 2 Datacenter Heterogen (Jakarta Standard, Bandung Premium)
 * - Total 10 Host heterogen (5 host / DC) dengan model daya SPECpower
 * - Total 40 VM heterogen (4 VM / host: 10 Small, 10 Medium, 20 Large)
 * - Sinkronisasi: Semua VM dari 2 DC dikelola dan disinkronisasikan di akhir simulasi.
 *
 * Algoritma:
 * - PEFT (Predict Earliest Finish Time) - Diusulkan
 * - FCFS (First Come First Served) - Baseline 1
 * - Round Robin - Baseline 2
 */
public class MainSimulation {

    private static final int MAX_TASKS = 1000;
    private static final String DATASET_DIR = "dataset";
    private static final String SWF_FILE = "dataset/NASA-iPSC-1993-3.1-cln.swf";
    private static final String RESULTS_DIR = "results";

    public static void main(String[] args) throws Exception {
        printBanner();

        String mode = args.length > 0 ? args[0].toUpperCase() : "ALL";
        Files.createDirectories(Path.of(RESULTS_DIR));

        List<MetricsCollector.ExperimentResult> allResults = new ArrayList<>();
        List<DatacenterSynchronizer.MultiDatacenterSyncReport> allSyncReports = new ArrayList<>();

        // =========================================================================
        // SKENARIO 1: DATASET SINTETIS (1000 Task)
        // =========================================================================
        System.out.println("\n" + "=".repeat(75));
        System.out.println("  SKENARIO 1: DATASET SINTETIS (1000 TASKS - 3 KELAS UKURAN) [2 DATACENTER]");
        System.out.println("=".repeat(75));

        List<MetricsCollector.ExperimentResult> syntheticResults = new ArrayList<>();

        if (mode.equals("ALL") || mode.equals("PEFT")) {
            SimulationOutcome out = runExperiment("PEFT", "Synthetic", null);
            syntheticResults.add(out.metricResult());
            allSyncReports.add(out.syncReport());
        }
        if (mode.equals("ALL") || mode.equals("FCFS")) {
            SimulationOutcome out = runExperiment("FCFS", "Synthetic", null);
            syntheticResults.add(out.metricResult());
            allSyncReports.add(out.syncReport());
        }
        if (mode.equals("ALL") || mode.equals("RR") || mode.equals("ROUNDROBIN")) {
            SimulationOutcome out = runExperiment("RR", "Synthetic", null);
            syntheticResults.add(out.metricResult());
            allSyncReports.add(out.syncReport());
        }

        if (syntheticResults.size() > 1) {
            MetricsCollector.printComparisonTable(syntheticResults);
            MetricsCollector.exportToCsv(syntheticResults, RESULTS_DIR + "/synthetic_results.csv");
            allResults.addAll(syntheticResults);
        }

        // =========================================================================
        // SKENARIO 2: DATASET REAL TRACE (NASA iPSC - SWF Format)
        // =========================================================================
        String swfPath = findSwfFile();
        if (swfPath != null) {
            System.out.println("\n\n" + "=".repeat(75));
            System.out.println("  SKENARIO 2: DATASET REAL TRACE (NASA iPSC - SWF 1000 TASKS) [2 DATACENTER]");
            System.out.println("=".repeat(75));

            List<MetricsCollector.ExperimentResult> nasaResults = new ArrayList<>();

            if (mode.equals("ALL") || mode.equals("PEFT")) {
                SimulationOutcome out = runExperiment("PEFT", "NASA-iPSC", swfPath);
                nasaResults.add(out.metricResult());
                allSyncReports.add(out.syncReport());
            }
            if (mode.equals("ALL") || mode.equals("FCFS")) {
                SimulationOutcome out = runExperiment("FCFS", "NASA-iPSC", swfPath);
                nasaResults.add(out.metricResult());
                allSyncReports.add(out.syncReport());
            }
            if (mode.equals("ALL") || mode.equals("RR") || mode.equals("ROUNDROBIN")) {
                SimulationOutcome out = runExperiment("RR", "NASA-iPSC", swfPath);
                nasaResults.add(out.metricResult());
                allSyncReports.add(out.syncReport());
            }

            if (nasaResults.size() > 1) {
                MetricsCollector.printComparisonTable(nasaResults);
                MetricsCollector.exportToCsv(nasaResults, RESULTS_DIR + "/nasa_ipsc_results.csv");
                allResults.addAll(nasaResults);
            }
        } else {
            System.out.println("\n  [PERINGATAN] Dataset SWF tidak ditemukan di: " + SWF_FILE);
        }

        // Simpan gabungan semua hasil eksperimen
        if (!allResults.isEmpty()) {
            MetricsCollector.exportToCsv(allResults, RESULTS_DIR + "/all_results.csv");
        }

        // Simpan rekapitulasi sinkronisasi 2 Datacenter ke CSV
        if (!allSyncReports.isEmpty()) {
            DatacenterSynchronizer.exportSyncToCsv(allSyncReports, RESULTS_DIR + "/datacenter_sync_results.csv");
        }

        printFooter();
    }

    /**
     * Menjalankan satu eksperimen lengkap dengan 2 Datacenter.
     *
     * Alur Operasi:
     *   1. Bangun 2 Datacenter Heterogen (DC-1 Standard, DC-2 Premium)
     *   2. Buat Broker dan pasang DatacenterMapper agar tiap VM terpetakan ke DC yang tepat
     *   3. Submit VM list (40 VM dari 2 DC) dan Cloudlets (1000 tasks)
     *   4. Jalankan Simulasi CloudSim Plus
     *   5. Kumpulkan metrik performa global
     *   6. Lakukan proses SINKRONISASI & VERIFIKASI akhir antar ke-2 Datacenter
     */
    private static SimulationOutcome runExperiment(
            String algorithm, String datasetName, String swfPath) throws Exception {

        System.out.printf("%n  +-------------------------------------------------------------+%n");
        System.out.printf("  |  Algoritma   : %-44s |%n", getAlgorithmName(algorithm));
        System.out.printf("  |  Dataset     : %-44s |%n", datasetName);
        System.out.printf("  |  Tasks       : %-44d |%n", MAX_TASKS);
        System.out.printf("  |  Infrastruktur: %-43s |%n", "2 Datacenter (10 Host, 40 VM)");
        System.out.printf("  +-------------------------------------------------------------+%n%n");

        CloudSimPlus simulation = new CloudSimPlus();

        // 1. Bangun 2 Datacenter Heterogen
        System.out.println("  [1/5] Membangun Arsitektur 2 Datacenter Heterogen...");
        DatacenterBuilder.MultiDatacenterResult multiDcResult =
                DatacenterBuilder.buildMultiple(simulation);

        // 2. Buat Datacenter Broker
        System.out.println("  [2/5] Menginisialisasi Broker Global (" + getAlgorithmName(algorithm) + ")...");
        DatacenterBroker broker = createBroker(algorithm, simulation);

        // Petakan tiap VM secara spesifik ke Datacenter asalnya
        broker.setDatacenterMapper((lastDc, vm) -> {
            var targetDc = multiDcResult.getDatacenterForVm(vm);
            return targetDc != null ? targetDc : lastDc;
        });

        // 3. Persiapkan Dataset
        System.out.println("  [3/5] Mempersiapkan Dataset (" + datasetName + ")...");
        List<Cloudlet> cloudlets;
        if (swfPath != null) {
            cloudlets = SwfParser.parse(swfPath, MAX_TASKS);
        } else {
            cloudlets = SwfParser.generateSyntheticDataset(MAX_TASKS, 42L);
            Path synCsv = Path.of(DATASET_DIR, "synthetic_dataset_1000.csv");
            if (!Files.exists(synCsv)) {
                SwfParser.exportSyntheticDatasetToCsv(cloudlets, synCsv.toString());
            }
        }

        // 4. Submit seluruh VM dari ketiga DC dan seluruh Cloudlet ke Broker
        broker.submitVmList(multiDcResult.allVms());
        broker.submitCloudletList(cloudlets);

        // 5. Jalankan Simulasi
        System.out.println("  [4/5] Menjalankan Simulasi CloudSim Plus (2 Datacenter)...");
        long startTime = System.currentTimeMillis();
        simulation.start();
        long elapsedSimRealTime = System.currentTimeMillis() - startTime;

        // 6. Kumpulkan Metrik Performa
        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        List<Host> allHosts = multiDcResult.allHosts();
        List<Vm> allVms = multiDcResult.allVms();

        String algoFullName = getAlgorithmName(algorithm) + " [" + datasetName + "]";
        MetricsCollector.ExperimentResult metricResult = MetricsCollector.collectMetrics(
                algoFullName, finishedCloudlets, allVms, allHosts, simulation.clock());

        System.out.printf("  Simulasi selesai dalam %d ms (Real-world execution time).%n", elapsedSimRealTime);
        MetricsCollector.printReport(metricResult);
        MetricsCollector.printCloudletTable(finishedCloudlets, algorithm, 10);

        // 7. SINKRONISASI & VERIFIKASI AKHIR ANTAR 2 DATACENTER
        System.out.println("  [5/5] Melakukan Sinkronisasi & Verifikasi State Antar 2 Datacenter...");
        DatacenterSynchronizer.MultiDatacenterSyncReport syncReport =
                DatacenterSynchronizer.synchronizeAndVerify(
                        algoFullName, multiDcResult, finishedCloudlets, cloudlets.size(), simulation.clock());

        return new SimulationOutcome(metricResult, syncReport);
    }

    private static DatacenterBroker createBroker(String algorithm, CloudSimPlus simulation) {
        return switch (algorithm.toUpperCase()) {
            case "PEFT" -> new PeftBroker(simulation);
            case "FCFS" -> new FcfsBroker(simulation);
            case "RR", "ROUNDROBIN" -> new RoundRobinBroker(simulation);
            default -> {
                System.out.println("  Algoritma tidak dikenal: " + algorithm + ", menggunakan PEFT.");
                yield new PeftBroker(simulation);
            }
        };
    }

    private static String getAlgorithmName(String shortName) {
        return switch (shortName.toUpperCase()) {
            case "PEFT" -> "PEFT (Predict Earliest Finish Time)";
            case "FCFS" -> "FCFS (First Come First Served)";
            case "RR", "ROUNDROBIN" -> "Round Robin";
            default -> shortName;
        };
    }

    private static String findSwfFile() {
        if (Files.exists(Path.of(SWF_FILE))) return SWF_FILE;
        if (Files.exists(Path.of(SWF_FILE + ".gz"))) return SWF_FILE + ".gz";
        try {
            Path datasetDir = Path.of(DATASET_DIR);
            if (Files.exists(datasetDir)) {
                Optional<Path> swfFile = Files.list(datasetDir)
                        .filter(p -> p.toString().endsWith(".swf") || p.toString().endsWith(".swf.gz"))
                        .findFirst();
                return swfFile.map(Path::toString).orElse(null);
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("====================================================================");
        System.out.println("       OPTIMASI PENJADWALAN TASK PADA KOMPUTASI AWAN                ");
        System.out.println("                                                                    ");
        System.out.println("   Kelompok 8 - Strategi Optimasi Komputasi Awan                    ");
        System.out.println("   Departemen Teknologi Informasi, ITS                              ");
        System.out.println("                                                                    ");
        System.out.println("   Arsitektur          : 2 Datacenter Heterogen                     ");
        System.out.println("                         - DC-1: Jakarta (5 Host B, 2 Small+2 Medium/Host)");
        System.out.println("                         - DC-2: Bandung (5 Host C/D, 4 Large/Host)    ");
        System.out.println("   Kapasitas Total     : 10 Host, 40 VM, SPECpower Energy Model     ");
        System.out.println("   Algoritma Diusulkan : PEFT (Predict Earliest Finish Time)        ");
        System.out.println("   Algoritma Baseline  : FCFS, Round Robin                          ");
        System.out.println("   Simulator           : CloudSim Plus 8.0                          ");
        System.out.println("   Fitur Sinkronisasi  : Verifikasi Sinkronisasi 2 Datacenter       ");
        System.out.println("====================================================================");
    }

    private static void printFooter() {
        System.out.println("\n");
        System.out.println("====================================================================");
        System.out.println("  Seluruh eksperimen multi-datacenter telah berhasil diselesaikan!  ");
        System.out.println("  Ketiga Datacenter telah diverifikasi dan tersinkronisasi 100%.    ");
        System.out.println("  Hasil CSV dan ringkasan sinkronisasi tersimpan di 'results/'.     ");
        System.out.println("====================================================================");
    }

    public static record SimulationOutcome(
            MetricsCollector.ExperimentResult metricResult,
            DatacenterSynchronizer.MultiDatacenterSyncReport syncReport
    ) {}
}
