package com.kelompok8;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Kolektor dan reporter metrik evaluasi eksperimen.
 *
 * Metrik yang diukur sesuai dokumen Tugas 3 (Bagian E):
 * 1. Makespan (detik) - waktu penyelesaian seluruh task
 * 2. Total Energy Consumption (Joule dan kWh) - konsumsi energi berdasarkan model SPECpower
 * 3. Resource Utilization (%) - tingkat pemanfaatan CPU VM/Host
 * 4. Throughput (task/detik) - jumlah task selesai per satuan waktu
 * 5. Avg Turnaround Time (detik) - waktu tanggap rata-rata
 * 6. Avg Waiting Time (detik) - waktu tunggu rata-rata
 * 7. Load Balance (Std Dev) - deviasi standar distribusi beban kerja antar VM
 */
public class MetricsCollector {

    public static ExperimentResult collectMetrics(
            String algorithmName,
            List<Cloudlet> finishedCloudlets,
            List<Vm> vmList,
            List<Host> hostList,
            double simulationTime) {

        ExperimentResult result = new ExperimentResult();
        result.algorithm = algorithmName;
        result.totalTasks = finishedCloudlets.size();

        if (finishedCloudlets.isEmpty()) {
            return result;
        }

        // 1. Makespan (detik)
        result.makespan = finishedCloudlets.stream()
                .mapToDouble(Cloudlet::getFinishTime)
                .max().orElse(0.0);

        // 2. Average Turnaround Time: (FinishTime - SubmissionDelay)
        result.avgTurnaroundTime = finishedCloudlets.stream()
                .mapToDouble(c -> Math.max(0.0, c.getFinishTime() - c.getSubmissionDelay()))
                .average().orElse(0.0);

        // 3. Average Waiting Time
        result.avgWaitingTime = finishedCloudlets.stream()
                .mapToDouble(Cloudlet::getWaitingTime)
                .average().orElse(0.0);

        // 4. Throughput: tasks / makespan
        result.throughput = result.makespan > 0 ? (double) result.totalTasks / result.makespan : 0.0;

        // 5. Total Execution Time
        result.totalExecTime = finishedCloudlets.stream()
                .mapToDouble(Cloudlet::getActualCpuTime)
                .sum();

        // 6. Resource Utilization: Rata-rata utilisasi CPU VM (%)
        result.avgCpuUtilization = calculateAvgVmUtilization(finishedCloudlets, vmList, result.makespan);

        // 7. Load Balance: Standard Deviasi beban (MI yang dieksekusi) antar VM
        result.loadBalanceStdDev = calculateLoadBalanceStdDev(finishedCloudlets, vmList);

        // 8. Total Energy Consumption (Joule dan kWh) berdasarkan model SPECpower
        result.totalEnergyJoules = calculateSpecPowerEnergy(finishedCloudlets, hostList, result.makespan);
        result.totalEnergyKWh = result.totalEnergyJoules / 3_600_000.0;

        return result;
    }

    /**
     * Menghitung total konsumsi energi datacenter menggunakan benchmark SPECpower nyata (Section E.1).
     * E_host = P_max * t_busy + P_idle * (Makespan - t_busy)
     */
    private static double calculateSpecPowerEnergy(List<Cloudlet> cloudlets, List<Host> hostList, double makespan) {
        if (makespan <= 0) return 0;

        // Hitung total waktu sibuk CPU per Host
        Map<Host, Double> hostBusyCpuTime = new HashMap<>();
        for (Cloudlet cl : cloudlets) {
            Vm vm = cl.getVm();
            if (vm != null && vm.getHost() != null) {
                Host host = vm.getHost();
                double taskCpuTime = cl.getActualCpuTime() * cl.getPesNumber();
                hostBusyCpuTime.merge(host, taskCpuTime, Double::sum);
            }
        }

        double totalJoules = 0.0;
        for (Host host : hostList) {
            double totalCpuSeconds = hostBusyCpuTime.getOrDefault(host, 0.0);
            int hostPes = (int) host.getPesNumber();
            // Rata-rata waktu host sibuk
            double hostBusyTime = Math.min(makespan, totalCpuSeconds / hostPes);
            double hostIdleTime = Math.max(0.0, makespan - hostBusyTime);

            // Dapatkan daya idle dan max dari PowerModel host
            double pIdle = host.getPowerModel().getPower(0.0);
            double pMax = host.getPowerModel().getPower(1.0);

            double hostEnergy = (pMax * hostBusyTime) + (pIdle * hostIdleTime);
            totalJoules += hostEnergy;
        }

        return totalJoules;
    }

    /**
     * Menghitung rata-rata utilisasi CPU seluruh VM (%).
     */
    private static double calculateAvgVmUtilization(List<Cloudlet> cloudlets, List<Vm> vmList, double makespan) {
        if (makespan <= 0 || vmList.isEmpty()) return 0;

        Map<Vm, Double> vmCpuTime = new HashMap<>();
        for (Cloudlet cl : cloudlets) {
            Vm vm = cl.getVm();
            if (vm != null) {
                vmCpuTime.merge(vm, cl.getActualCpuTime(), Double::sum);
            }
        }

        double totalUtilization = 0;
        for (Vm vm : vmList) {
            double busyTime = vmCpuTime.getOrDefault(vm, 0.0);
            double vmUtil = Math.min(100.0, (busyTime / makespan) * 100.0);
            totalUtilization += vmUtil;
        }

        return totalUtilization / vmList.size();
    }

    /**
     * Menghitung Standar Deviasi jumlah task yang dialokasikan antar VM (Load Balancing).
     */
    private static double calculateLoadBalanceStdDev(List<Cloudlet> cloudlets, List<Vm> vmList) {
        if (vmList.isEmpty()) return 0;

        Map<Vm, Long> tasksPerVm = cloudlets.stream()
                .filter(cl -> cl.getVm() != null)
                .collect(Collectors.groupingBy(Cloudlet::getVm, Collectors.counting()));

        double mean = (double) cloudlets.size() / vmList.size();
        double variance = 0;
        for (Vm vm : vmList) {
            long count = tasksPerVm.getOrDefault(vm, 0L);
            variance += Math.pow(count - mean, 2);
        }
        return Math.sqrt(variance / vmList.size());
    }

    /**
     * Mencetak laporan metrik untuk satu algoritma.
     */
    public static void printReport(ExperimentResult result, int totalSubmitted) {
        System.out.println("\n" + "=".repeat(70));
        System.out.printf("  HASIL EKSPERIMEN - %s%n", result.algorithm);
        System.out.println("=".repeat(70));
        System.out.printf("  %-35s : %d / %d tasks%n", "Total Tasks Selesai", result.totalTasks, totalSubmitted);
        System.out.printf("  %-35s : %.4f detik%n", "Makespan", result.makespan);
        System.out.printf("  %-35s : %.4f detik%n", "Avg Turnaround Time", result.avgTurnaroundTime);
        System.out.printf("  %-35s : %.4f detik%n", "Avg Waiting Time", result.avgWaitingTime);
        System.out.printf("  %-35s : %.4f task/detik%n", "Throughput", result.throughput);
        System.out.printf("  %-35s : %.4f %%%n", "Avg CPU Utilization", result.avgCpuUtilization);
        System.out.printf("  %-35s : %.4f%n", "Load Balance (Std Dev)", result.loadBalanceStdDev);
        System.out.printf("  %-35s : %.2f Joule%n", "Total Energy (Joule)", result.totalEnergyJoules);
        System.out.printf("  %-35s : %.6f kWh%n", "Total Energy (kWh)", result.totalEnergyKWh);
        System.out.println("=".repeat(70));
    }

    /**
     * Mencetak tabel perbandingan performa antar algoritma.
     */
    public static void printComparisonTable(List<ExperimentResult> results) {
        System.out.println("\n");
        System.out.println("+" + "-".repeat(90) + "+");
        System.out.println("|" + centerPad("TABEL PERBANDINGAN PERFORMA ALGORITMA", 90) + "|");
        System.out.println("+" + "-".repeat(90) + "+");

        // Header
        System.out.printf("| %-22s | %14s | %14s | %14s | %14s |%n",
                "Metrik Evaluasi", getShortName(results, 0), getShortName(results, 1), getShortName(results, 2), "PEFT vs FCFS");
        System.out.println("+" + "-".repeat(90) + "+");

        ExperimentResult peftResult = results.stream()
                .filter(r -> r.algorithm.contains("PEFT"))
                .findFirst().orElse(results.get(0));
        ExperimentResult fcfsResult = results.stream()
                .filter(r -> r.algorithm.contains("FCFS"))
                .findFirst().orElse(results.get(0));

        printRow("Tasks Selesai", results, r -> (double) r.totalTasks, "%.0f", peftResult.totalTasks, fcfsResult.totalTasks, false);
        printRow("Makespan (detik)", results, r -> r.makespan, "%.2f", peftResult.makespan, fcfsResult.makespan, true);
        printRow("Turnaround Time (s)", results, r -> r.avgTurnaroundTime, "%.2f", peftResult.avgTurnaroundTime, fcfsResult.avgTurnaroundTime, true);
        printRow("Waiting Time (s)", results, r -> r.avgWaitingTime, "%.2f", peftResult.avgWaitingTime, fcfsResult.avgWaitingTime, true);
        printRow("Throughput (task/s)", results, r -> r.throughput, "%.4f", peftResult.throughput, fcfsResult.throughput, false);
        printRow("CPU Utilization (%)", results, r -> r.avgCpuUtilization, "%.2f", peftResult.avgCpuUtilization, fcfsResult.avgCpuUtilization, false);
        printRow("Load Balance (SD)", results, r -> r.loadBalanceStdDev, "%.2f", peftResult.loadBalanceStdDev, fcfsResult.loadBalanceStdDev, true);
        printRow("Energy (Joule)", results, r -> r.totalEnergyJoules, "%.1f", peftResult.totalEnergyJoules, fcfsResult.totalEnergyJoules, true);
        printRow("Energy (kWh)", results, r -> r.totalEnergyKWh, "%.6f", peftResult.totalEnergyKWh, fcfsResult.totalEnergyKWh, true);

        System.out.println("+" + "-".repeat(90) + "+");
        System.out.println("  Catatan: Nilai persentase PEFT vs FCFS bertanda (-) menunjukkan penurunan/efisiensi yang lebih baik.");
    }

    private static void printRow(String metric, List<ExperimentResult> results,
                                 java.util.function.Function<ExperimentResult, Double> getter,
                                 String format, double peftVal, double fcfsVal, boolean lowerIsBetter) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("| %-22s |", metric));

        for (int i = 0; i < Math.min(results.size(), 3); i++) {
            sb.append(String.format(" %14s |", String.format(format, getter.apply(results.get(i)))));
        }

        for (int i = results.size(); i < 3; i++) {
            sb.append(String.format(" %14s |", "N/A"));
        }

        double diff = fcfsVal != 0 ? ((peftVal - fcfsVal) / fcfsVal) * 100.0 : 0;
        String sign = diff > 0 ? "+" : "";
        sb.append(String.format(" %14s |", sign + String.format("%.2f%%", diff)));

        System.out.println(sb);
    }

    private static String getShortName(List<ExperimentResult> results, int index) {
        if (index < results.size()) {
            String name = results.get(index).algorithm;
            if (name.contains("PEFT")) return "PEFT (Usulan)";
            if (name.contains("FCFS")) return "FCFS";
            if (name.contains("Round Robin") || name.contains("RR")) return "Round Robin";
            return name.length() > 14 ? name.substring(0, 14) : name;
        }
        return "N/A";
    }

    private static String centerPad(String text, int width) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + text + " ".repeat(Math.max(0, width - text.length() - padding));
    }

    public static void printCloudletTable(List<Cloudlet> cloudlets, String algorithm, int maxRows) {
        System.out.printf("%n  === Sampel Eksekusi Task (%s) - Top %d ===%n", algorithm, maxRows);
        System.out.printf("  %-10s | %-8s | %-12s | %-12s | %-12s | %-12s%n",
                "CloudletID", "VM ID", "Length (MI)", "Exec Time(s)", "Start Time(s)", "Finish Time(s)");
        System.out.println("  " + "-".repeat(78));

        cloudlets.stream()
                .sorted(Comparator.comparingLong(Cloudlet::getId))
                .limit(maxRows)
                .forEach(cl -> {
                    long vmId = cl.getVm() != null ? cl.getVm().getId() : -1;
                    System.out.printf("  %-10d | %-8d | %-12d | %-12.4f | %-12.4f | %-12.4f%n",
                            cl.getId(), vmId, cl.getLength(),
                            cl.getActualCpuTime(), cl.getExecStartTime(), cl.getFinishTime());
                });

        if (cloudlets.size() > maxRows) {
            System.out.printf("  ... dan %d task lainnya selesai.%n", cloudlets.size() - maxRows);
        }
    }

    public static void exportToCsv(List<ExperimentResult> results, String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Algorithm,TotalTasks,Makespan(s),AvgTurnaround(s),AvgWaitingTime(s)," +
                    "Throughput(task/s),CpuUtilization(%),LoadBalanceSD,Energy(J),Energy(kWh)");

            for (ExperimentResult r : results) {
                writer.printf("%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.6f%n",
                        r.algorithm, r.totalTasks, r.makespan, r.avgTurnaroundTime,
                        r.avgWaitingTime, r.throughput, r.avgCpuUtilization,
                        r.loadBalanceStdDev, r.totalEnergyJoules, r.totalEnergyKWh);
            }
        }
        System.out.printf("  [CSV] Hasil berhasil disimpan ke: %s%n", filename);
    }

    public static class ExperimentResult {
        public String algorithm;
        public int totalTasks;
        public double makespan;
        public double avgTurnaroundTime;
        public double totalExecTime;
        public double throughput;
        public double avgWaitingTime;
        public double avgCpuUtilization;
        public double loadBalanceStdDev;
        public double totalEnergyJoules;
        public double totalEnergyKWh;
    }
}
