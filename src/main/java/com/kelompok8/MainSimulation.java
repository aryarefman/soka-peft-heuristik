package com.kelompok8;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;

import java.nio.file.*;
import java.util.*;

/**
 * Simulasi Utama - Optimasi Penjadwalan Task pada Komputasi Awan (Multi-Datacenter)
 *
 * Kelompok 8 - Strategi Optimasi Komputasi Awan
 * Departemen Teknologi Informasi, Institut Teknologi Sepuluh Nopember (ITS)
 *
 * Menjalankan 1.000 task pada dua dataset (Sintetis dan trace asli NASA iPSC/860)
 * untuk tiga algoritma (PEFT, FCFS, Round Robin) di atas infrastruktur
 * yang dipilih lewat {@link Skenario} (3dc atau 2dc).
 *
 * Argumen: [ALL|PEFT|FCFS|RR] [3dc|2dc]
 * Hasil CSV disimpan ke results/{skenario}/.
 */
public class MainSimulation {

    private static final int MAX_TASKS = 1000;

    public static void main(String[] args) throws Exception {
        Skenario skenario = Skenario.dariArgs(args);
        String mode = args.length > 0 && !args[0].toLowerCase().endsWith("dc") ? args[0].toUpperCase() : "ALL";
        String resultsDir = skenario.resultsDir();
        Files.createDirectories(Path.of(resultsDir));

        printBanner(skenario);

        List<String> algorithms = new ArrayList<>();
        if (mode.equals("ALL") || mode.equals("PEFT")) algorithms.add("PEFT");
        if (mode.equals("ALL") || mode.equals("FCFS")) algorithms.add("FCFS");
        if (mode.equals("ALL") || mode.equals("RR") || mode.equals("ROUNDROBIN")) algorithms.add("RR");

        List<MetricsCollector.ExperimentResult> allResults = new ArrayList<>();
        List<DatacenterSynchronizer.MultiDatacenterSyncReport> allSyncReports = new ArrayList<>();

        // DATASET 1: Sintetis (1.000 task)
        printSection("DATASET 1: SINTETIS (1.000 TASK) [" + skenario.ringkasan() + "]");
        List<MetricsCollector.ExperimentResult> syntheticResults = new ArrayList<>();
        for (String algo : algorithms) {
            SimulationOutcome out = runExperiment(skenario, algo, "Synthetic", null);
            syntheticResults.add(out.metricResult());
            allSyncReports.add(out.syncReport());
        }
        if (syntheticResults.size() > 1) {
            MetricsCollector.printComparisonTable(syntheticResults);
            MetricsCollector.exportToCsv(syntheticResults, resultsDir + "/synthetic_results.csv");
        }
        allResults.addAll(syntheticResults);

        // DATASET 2: Trace asli NASA iPSC/860 (1.000 job pertama)
        if (Files.exists(Path.of(SwfParser.NASA_SWF))) {
            printSection("DATASET 2: TRACE NASA iPSC/860 (1.000 JOB) [" + skenario.ringkasan() + "]");
            List<MetricsCollector.ExperimentResult> nasaResults = new ArrayList<>();
            for (String algo : algorithms) {
                SimulationOutcome out = runExperiment(skenario, algo, "NASA-iPSC", SwfParser.NASA_SWF);
                nasaResults.add(out.metricResult());
                allSyncReports.add(out.syncReport());
            }
            if (nasaResults.size() > 1) {
                MetricsCollector.printComparisonTable(nasaResults);
                MetricsCollector.exportToCsv(nasaResults, resultsDir + "/nasa_ipsc_results.csv");
            }
            allResults.addAll(nasaResults);
        } else {
            System.out.println("\n  [PERINGATAN] Dataset SWF tidak ditemukan di: " + SwfParser.NASA_SWF);
        }

        if (!allResults.isEmpty()) {
            MetricsCollector.exportToCsv(allResults, resultsDir + "/all_results.csv");
        }
        if (!allSyncReports.isEmpty()) {
            DatacenterSynchronizer.exportSyncToCsv(allSyncReports, resultsDir + "/datacenter_sync_results.csv");
        }

        System.out.println("\n" + "=".repeat(68));
        System.out.println("  Seluruh eksperimen selesai. Hasil CSV tersimpan di '" + resultsDir + "/'.");
        System.out.println("=".repeat(68));
    }

    /**
     * Menjalankan satu eksperimen lengkap:
     *   1. Bangun seluruh datacenter sesuai skenario
     *   2. Buat broker dan petakan tiap VM ke DC asalnya
     *   3. Submit seluruh VM dan cloudlet (1.000 task)
     *   4. Jalankan simulasi CloudSim Plus
     *   5. Kumpulkan metrik performa global
     *   6. Sinkronisasi & verifikasi akhir antar-datacenter
     */
    private static SimulationOutcome runExperiment(
            Skenario skenario, String algorithm, String datasetName, String swfPath) throws Exception {

        System.out.printf("%n  +-------------------------------------------------------------+%n");
        System.out.printf("  |  Algoritma    : %-43s |%n", getAlgorithmName(algorithm));
        System.out.printf("  |  Dataset      : %-43s |%n", datasetName);
        System.out.printf("  |  Tasks        : %-43d |%n", MAX_TASKS);
        System.out.printf("  |  Infrastruktur: %-43s |%n", skenario.ringkasan());
        System.out.printf("  +-------------------------------------------------------------+%n%n");

        CloudSimPlus simulation = new CloudSimPlus();

        System.out.println("  [1/5] Membangun " + skenario.label + "...");
        DatacenterBuilder.MultiDatacenterResult multiDcResult = DatacenterBuilder.buildMultiple(simulation, skenario);

        System.out.println("  [2/5] Menginisialisasi broker global (" + getAlgorithmName(algorithm) + ")...");
        DatacenterBroker broker = createBroker(algorithm, simulation);
        broker.setDatacenterMapper((lastDc, vm) -> {
            var targetDc = multiDcResult.getDatacenterForVm(vm);
            return targetDc != null ? targetDc : lastDc;
        });

        System.out.println("  [3/5] Mempersiapkan dataset (" + datasetName + ")...");
        List<Cloudlet> cloudlets;
        if (swfPath != null) {
            cloudlets = SwfParser.parse(swfPath, MAX_TASKS);
        } else {
            // Dataset yang sama persis dengan BatchExperimentSuite (dataset/synthetic_dataset_1000.csv)
            cloudlets = SwfParser.getOrCreateSyntheticDataset(MAX_TASKS, 42L);
        }
        SwfParser.printDatasetCharacteristics(cloudlets, datasetName);

        broker.submitVmList(multiDcResult.allVms());
        broker.submitCloudletList(cloudlets);

        System.out.println("  [4/5] Menjalankan simulasi CloudSim Plus (" + skenario.ringkasan() + ")...");
        long startTime = System.currentTimeMillis();
        simulation.start();
        long elapsedMs = System.currentTimeMillis() - startTime;

        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        String algoFullName = getAlgorithmName(algorithm) + " [" + datasetName + "]";
        MetricsCollector.ExperimentResult metricResult = MetricsCollector.collectMetrics(
                algoFullName, finishedCloudlets, multiDcResult.allVms(), multiDcResult.allHosts(), simulation.clock());

        System.out.printf("  Simulasi selesai dalam %d ms (waktu eksekusi nyata).%n", elapsedMs);
        MetricsCollector.printReport(metricResult, cloudlets.size());
        MetricsCollector.printCloudletTable(finishedCloudlets, algorithm, 10);
        DatacenterBuilder.printDetailedVmAllocationTable(multiDcResult);

        System.out.println("  [5/5] Sinkronisasi & verifikasi state antar-datacenter...");
        DatacenterSynchronizer.MultiDatacenterSyncReport syncReport = DatacenterSynchronizer.synchronizeAndVerify(
                algoFullName, multiDcResult, finishedCloudlets, cloudlets.size(), simulation.clock());

        return new SimulationOutcome(metricResult, syncReport);
    }

    static DatacenterBroker createBroker(String algorithm, CloudSimPlus simulation) {
        return switch (algorithm.toUpperCase()) {
            case "PEFT" -> new PeftBroker(simulation);
            case "FCFS" -> new FcfsBroker(simulation);
            case "RR", "ROUNDROBIN" -> new RoundRobinBroker(simulation);
            default -> throw new IllegalArgumentException("Algoritma tidak dikenal: " + algorithm);
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

    private static void printSection(String title) {
        System.out.println("\n" + "=".repeat(75));
        System.out.println("  " + title);
        System.out.println("=".repeat(75));
    }

    private static void printBanner(Skenario skenario) {
        System.out.println();
        System.out.println("=".repeat(68));
        System.out.println("   OPTIMASI PENJADWALAN TASK PADA KOMPUTASI AWAN");
        System.out.println("   Kelompok 8 - Strategi Optimasi Komputasi Awan, DTI ITS");
        System.out.println();
        System.out.printf("   Skenario            : %s - %s%n", skenario.id, skenario.label);
        for (Skenario.DcSpec dc : skenario.datacenters) {
            System.out.printf("                         - %s: %d host, %d VM %s%n",
                    dc.name(), dc.hostPlan().length, dc.vmCount(), dc.vmClass());
        }
        System.out.printf("   Kapasitas Total     : %d Host, %d VM%n", skenario.totalHosts(), skenario.totalVms());
        System.out.println("   Algoritma Diusulkan : PEFT (Predict Earliest Finish Time)");
        System.out.println("   Algoritma Baseline  : FCFS, Round Robin");
        System.out.println("   Simulator           : CloudSim Plus 8.0.0");
        System.out.println("=".repeat(68));
    }

    public record SimulationOutcome(
            MetricsCollector.ExperimentResult metricResult,
            DatacenterSynchronizer.MultiDatacenterSyncReport syncReport) {}
}
