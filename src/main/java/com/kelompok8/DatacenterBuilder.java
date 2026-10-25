package com.kelompok8;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyBestFit;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRoundRobin;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.*;

/**
 * Builder arsitektur multi-datacenter Kelompok 8.
 *
 * Seluruh spesifikasi (jumlah DC, host, VM, tipe host) diambil dari {@link Skenario},
 * sehingga skenario 3 DC dan 2 DC dibangun oleh kode yang sama:
 *
 * | Skenario | DC | Host/DC | PE/Host | VM/DC | Total          |
 * |----------|----|---------|---------|-------|----------------|
 * | 3dc      | 3  | 8       | 8       | 10    | 24 Host, 30 VM |
 * | 2dc      | 2  | 5       | 16      | 20    | 10 Host, 40 VM |
 *
 * Semua VM dari seluruh DC disubmit ke satu broker global, sehingga broker
 * menjadwalkan task lintas datacenter. Model daya host: linier SPECpower.
 *
 * Kebijakan alokasi VM-ke-host default: Round-Robin (penyerataan), sehingga
 * VM tersebar bergiliran ke seluruh host dan tidak ada host yang menganggur.
 * Penjadwalan task di VM: non-preemptive (CloudletSchedulerSpaceShared).
 */
public class DatacenterBuilder {

    private static final long HOST_STORAGE = 1_000_000; // 1 TB dalam MB
    private static final long HOST_BW = 10_000;         // 10 Gbps dalam Mbps
    private static final long VM_BW = 250;              // 250 Mbps per VM
    private static final long VM_SIZE = 10_000;         // 10 GB storage per VM

    /**
     * Kebijakan alokasi VM ke host fisik:
     * - PENYERATAAN_ROUND_ROBIN : VM dibagikan bergiliran ke seluruh host (tidak ada host menganggur).
     * - BEST_FIT                : VM dipadatkan ke host dengan sisa resource paling sedikit (konsolidasi).
     * - FIRST_FIT               : VM ditempatkan ke host pertama yang kapasitasnya cukup.
     */
    public enum AllocationMode {
        PENYERATAAN_ROUND_ROBIN,
        BEST_FIT,
        FIRST_FIT
    }

    public static MultiDatacenterResult buildMultiple(CloudSimPlus simulation, Skenario skenario) {
        return buildMultiple(simulation, skenario, AllocationMode.PENYERATAAN_ROUND_ROBIN, true);
    }

    /**
     * @param verbose cetak rincian host/VM ke konsol (dimatikan pada batch agar log tidak membengkak)
     */
    public static MultiDatacenterResult buildMultiple(
            CloudSimPlus simulation, Skenario skenario, AllocationMode mode, boolean verbose) {

        List<DatacenterResult> dcResults = new ArrayList<>();
        List<Vm> allVms = new ArrayList<>();
        List<Host> allHosts = new ArrayList<>();
        Map<Vm, Datacenter> vmToDcMap = new IdentityHashMap<>();

        if (verbose) {
            System.out.printf("%n  Membangun %s (skenario %s)...%n", skenario.label, skenario.id);
            System.out.printf("  [Kebijakan Alokasi VM-ke-Host] : %s%n", mode);
            System.out.println("  " + "-".repeat(60));
        }

        for (Skenario.DcSpec spec : skenario.datacenters) {
            if (verbose) System.out.printf("%n  [%s]%n", spec.name());

            List<Host> hostList = createHosts(skenario, spec, verbose);
            List<Vm> vmList = createVms(spec, verbose);

            // Perbandingan kebijakan alokasi VM ke host (catatan revisi dosen):
            // 1. FIRST-FIT (VmAllocationPolicySimple)
            //    Memilih host pertama dalam daftar yang PE dan RAM-nya cukup. Cepat, tetapi VM
            //    menumpuk di host berindeks awal sehingga host di belakang bisa menganggur.
            // 2. BEST-FIT (VmAllocationPolicyBestFit)
            //    Mengevaluasi seluruh host lalu memilih host dengan sisa resource paling sedikit
            //    setelah VM ditempatkan (server consolidation). Hemat host, tetapi sebagian host
            //    dibiarkan kosong.
            // 3. PENYERATAAN (VmAllocationPolicyRoundRobin) - dipakai di proyek ini
            //    VM dibagikan bergiliran ke setiap host sehingga tidak ada host yang menganggur:
            //    - 3dc: 8 host & 10 VM per DC -> 6 host x 1 VM, 2 host x 2 VM
            //    - 2dc: 5 host & 20 VM per DC -> 5 host x 4 VM
            //    Hasil penempatan dapat dibuktikan dengan ./gradlew testAllocation.
            VmAllocationPolicy allocationPolicy = switch (mode) {
                case PENYERATAAN_ROUND_ROBIN -> new VmAllocationPolicyRoundRobin();
                case BEST_FIT -> new VmAllocationPolicyBestFit();
                case FIRST_FIT -> new VmAllocationPolicySimple();
            };

            Datacenter datacenter = new DatacenterSimple(simulation, hostList, allocationPolicy);
            datacenter.setName(spec.name());

            for (Vm vm : vmList) {
                vmToDcMap.put(vm, datacenter);
                vm.setDescription(spec.name() + " [PE:" + spec.vmPes() + ", MIPS:" + spec.vmMips() + "]");
            }

            dcResults.add(new DatacenterResult(datacenter, vmList));
            allVms.addAll(vmList);
            allHosts.addAll(hostList);
        }

        if (verbose) printMultiDatacenterSummary(dcResults, allHosts, allVms);
        return new MultiDatacenterResult(dcResults, allVms, allHosts, vmToDcMap);
    }

    // -------------------------------------------------------------------------
    // HELPER: Pembuatan Host & VM
    // -------------------------------------------------------------------------

    private static List<Host> createHosts(Skenario skenario, Skenario.DcSpec spec, boolean verbose) {
        List<Host> hostList = new ArrayList<>();
        int hostNumber = 1;
        for (int typeIdx : spec.hostPlan()) {
            int mipsPerCore = skenario.hostTypes[typeIdx][0];
            int ramGB = skenario.hostTypes[typeIdx][1];

            List<Pe> peList = new ArrayList<>();
            for (int pe = 0; pe < skenario.pesPerHost; pe++) {
                peList.add(new PeSimple(mipsPerCore));
            }

            Host host = new HostSimple((long) ramGB * 1024, HOST_BW, HOST_STORAGE, peList)
                    .setVmScheduler(new VmSchedulerTimeShared());
            host.setPowerModel(new PowerModelHostSimple(
                    Skenario.POWER_SPECS[typeIdx][1],   // max power (Watt)
                    Skenario.POWER_SPECS[typeIdx][0]    // idle power (Watt)
            ));
            host.enableUtilizationStats();
            hostList.add(host);

            if (verbose) {
                System.out.printf("    [Host Tipe-%s #%d] %d MIPS/core x %d PE, %d GB RAM%n",
                        Skenario.HOST_TYPE_NAMES[typeIdx], hostNumber, mipsPerCore, skenario.pesPerHost, ramGB);
            }
            hostNumber++;
        }
        return hostList;
    }

    private static List<Vm> createVms(Skenario.DcSpec spec, boolean verbose) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < spec.vmCount(); i++) {
            Vm vm = new VmSimple(spec.vmMips(), spec.vmPes())
                    .setRam(spec.vmRamMB())
                    .setBw(VM_BW)
                    .setSize(VM_SIZE)
                    .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vmList.add(vm);
        }
        if (verbose) {
            System.out.printf("    [VM %s] %d MIPS x %d PE, %d MB RAM x %d unit%n",
                    spec.vmClass(), spec.vmMips(), spec.vmPes(), spec.vmRamMB(), spec.vmCount());
        }
        return vmList;
    }

    // -------------------------------------------------------------------------
    // HELPER: Ringkasan & Verifikasi Alokasi
    // -------------------------------------------------------------------------

    private static void printMultiDatacenterSummary(
            List<DatacenterResult> dcResults, List<Host> allHosts, List<Vm> allVms) {

        System.out.println("\n  +==============================================================================+");
        System.out.println("  |                    RINGKASAN ARSITEKTUR MULTI-DATACENTER                     |");
        System.out.println("  +==============================================================================+");
        for (DatacenterResult dcr : dcResults) {
            List<Host> hosts = dcr.datacenter().getHostList();
            System.out.printf("    %-30s : %2d Host | %2d VM | %,7d MIPS | %6.1f GB RAM%n",
                    dcr.datacenter().getName(), hosts.size(), dcr.vmList().size(), totalMips(hosts), totalRamGb(hosts));
        }
        System.out.println("  +------------------------------------------------------------------------------+");
        System.out.printf("    %-30s : %2d Host | %2d VM | %,7d MIPS | %6.1f GB RAM%n",
                "TOTAL INFRASTRUKTUR", allHosts.size(), allVms.size(), totalMips(allHosts), totalRamGb(allHosts));
        System.out.printf("    [SINKRONISASI] Seluruh VM dari %d DC dikelola 1 broker global%n", dcResults.size());
        System.out.println("  +==============================================================================+\n");
    }

    private static long totalMips(List<Host> hosts) {
        return hosts.stream().mapToLong(h -> h.getPeList().stream().mapToLong(Pe::getCapacity).sum()).sum();
    }

    private static double totalRamGb(List<Host> hosts) {
        return hosts.stream().mapToLong(h -> h.getRam().getCapacity()).sum() / 1024.0;
    }

    /**
     * Mencetak bukti penempatan VM ke setiap host fisik setelah simulasi berjalan,
     * termasuk jumlah host yang menganggur (tanpa VM).
     */
    public static void printDetailedVmAllocationTable(MultiDatacenterResult multiDc) {
        String border = "  +" + "-".repeat(30) + "+" + "-".repeat(10) + "+" + "-".repeat(16) + "+"
                + "-".repeat(18) + "+" + "-".repeat(40) + "+";
        System.out.println("\n  BUKTI VERIFIKASI ALOKASI VM KE HOST FISIK");
        System.out.println(border);
        System.out.printf("  | %-28s | %-8s | %-14s | %-16s | %-38s |%n",
                "Datacenter", "Host", "Kapasitas", "RAM Terpakai", "VM Ditempatkan");
        System.out.println(border);

        int idleHosts = 0;
        Map<Integer, Integer> vmsPerHostHistogram = new TreeMap<>();
        for (DatacenterResult dcr : multiDc.dcResults()) {
            Datacenter dc = dcr.datacenter();
            int hostIdx = 1;
            for (Host h : dc.getHostList()) {
                List<Vm> vms = h.getVmCreatedList();
                long ramUsedMB = vms.stream().mapToLong(v -> v.getRam().getCapacity()).sum();
                String vmSummary = vms.isEmpty() ? "0 VM (menganggur)"
                        : vms.size() + " VM " + vms.stream().map(v -> String.valueOf(v.getId())).toList();
                String hostCap = h.getPesNumber() + " PE x " + (long) h.getPeList().get(0).getCapacity();
                System.out.printf("  | %-28s | #%-7d | %-14s | %6d/%6d MB | %-38s |%n",
                        dc.getName(), hostIdx++, hostCap, ramUsedMB, h.getRam().getCapacity(), vmSummary);
                if (vms.isEmpty()) idleHosts++;
                vmsPerHostHistogram.merge(vms.size(), 1, Integer::sum);
            }
            System.out.println(border);
        }

        StringBuilder pola = new StringBuilder();
        vmsPerHostHistogram.forEach((vmCount, hostCount) ->
                pola.append(pola.isEmpty() ? "" : ", ").append(hostCount).append(" host x ").append(vmCount).append(" VM"));
        System.out.printf("  Status: %d dari %d host aktif, %d host menganggur. Pola penempatan: %s%n%n",
                multiDc.allHosts().size() - idleHosts, multiDc.allHosts().size(), idleHosts, pola);
    }

    // -------------------------------------------------------------------------
    // RECORD TYPES
    // -------------------------------------------------------------------------

    /** Hasil build untuk satu datacenter. */
    public record DatacenterResult(Datacenter datacenter, List<Vm> vmList) {}

    /**
     * Hasil build seluruh datacenter pada satu skenario:
     *   - dcResults : daftar DatacenterResult per DC
     *   - allVms    : gabungan VM seluruh DC (disubmit ke satu broker)
     *   - allHosts  : gabungan host seluruh DC (untuk metrik energi)
     */
    public record MultiDatacenterResult(
            List<DatacenterResult> dcResults,
            List<Vm> allVms,
            List<Host> allHosts,
            Map<Vm, Datacenter> vmToDatacenterMap) {

        /** Mendapatkan Datacenter tempat VM dialokasikan. */
        public Datacenter getDatacenterForVm(Vm vm) {
            return vm == null ? null : vmToDatacenterMap.get(vm);
        }
    }
}
