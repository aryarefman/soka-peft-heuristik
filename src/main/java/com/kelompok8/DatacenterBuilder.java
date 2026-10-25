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
 * Builder untuk arsitektur multi-datacenter sesuai desain proyek Kelompok 8.
 *
 * Arsitektur: 2 Datacenter heterogen yang dikelola oleh satu broker terpusat.
 * Setiap datacenter berisi 5 host fisik, dan setiap host menampung tepat 4 VM.
 *
 * DC-1 Jakarta (Standard) : 5 Host Tipe B, tiap host = 2 VM Small + 2 VM Medium  (20 VM)
 * DC-2 Bandung (Premium)  : 3 Host Tipe C + 2 Host Tipe D, tiap host = 4 VM Large (20 VM)
 * Total                   : 2 Datacenter, 10 Host, 40 VM
 *
 * Konfigurasi Host:
 * | Tipe | MIPS/core | Core | RAM (GB) | Bandwidth | Power Idle | Power Max |
 * |------|-----------|------|----------|-----------|------------|-----------|
 * | A    | 1,000     | 8    | 8        | 10 Gbps   |  93.7 W    | 135.0 W   |
 * | B    | 1,500     | 8    | 16       | 10 Gbps   | 105.0 W    | 175.0 W   |
 * | C    | 2,000     | 16   | 24       | 10 Gbps   | 120.0 W    | 225.0 W   |
 * | D    | 3,000     | 16   | 32       | 10 Gbps   | 140.0 W    | 300.0 W   |
 *
 * Konfigurasi VM:
 * | Kelas  | PE | MIPS/PE | RAM     |
 * |--------|----|---------|---------|
 * | Small  | 1  | 1,000   | 1024 MB |
 * | Medium | 2  | 1,500   | 2048 MB |
 * | Large  | 4  | 2,000   | 4096 MB |
 *
 * Sinkronisasi: Semua VM dari kedua datacenter disubmit ke 1 broker tunggal,
 * sehingga broker dapat menjadwalkan task secara global lintas datacenter.
 * Metrik dihimpun bersama dari seluruh host dan VM.
 *
 * Kebijakan Alokasi VM: Penyerataan (Round-Robin), setiap host menampung 4 VM
 * Penjadwalan Task: Non-preemptive (Space-Shared pada VM)
 */
public class DatacenterBuilder {

    private static final long HOST_STORAGE = 1_000_000; // 1 TB in MB
    private static final long HOST_BW = 10_000;         // 10 Gbps in Mbps

    /** Jumlah VM yang ditempatkan pada setiap host. */
    public static final int VMS_PER_HOST = 4;

    // Host Types: {MIPS/core, RAM_GB, jumlah core}
    public static final int[][] HOST_TYPES = {
            {1000,  8,  8},    // Type A: 1000 MIPS/core, 8 GB RAM, 8 core
            {1500, 16,  8},    // Type B: 1500 MIPS/core, 16 GB RAM, 8 core
            {2000, 24, 16},    // Type C: 2000 MIPS/core, 24 GB RAM, 16 core
            {3000, 32, 16},    // Type D: 3000 MIPS/core, 32 GB RAM, 16 core
    };

    private static final String[] HOST_TYPE_NAMES = {"A", "B", "C", "D"};

    // SPECpower benchmark power specs: {idle_power_watts, max_power_watts}
    // References: Beloglazov & Buyya (2012) - HP ProLiant and IBM System benchmarks
    public static final double[][] POWER_SPECS = {
            {93.7,  135.0},  // Type A: HP ProLiant ML110 G5
            {105.0, 175.0},  // Type B: IBM System x3250 M2
            {120.0, 225.0},  // Type C: IBM System x3550 M3
            {140.0, 300.0},  // Type D: High-end enterprise server
    };

    private static final String[] DC_NAMES = {
            "DC-1: Jakarta (Standard)",
            "DC-2: Bandung (Premium)"
    };

    /** Tipe host (indeks HOST_TYPES) untuk masing-masing dari 5 host di setiap datacenter. */
    private static final int[][] DC_HOSTS = {
            {1, 1, 1, 1, 1},   // DC-1: 5 Host Tipe B
            {2, 2, 2, 3, 3},   // DC-2: 3 Host Tipe C + 2 Host Tipe D
    };

    /**
     * Komposisi 4 VM pada setiap host: { vmPes, vmRamMB, vmMips, jumlah per host }.
     * Jumlah per host pada satu datacenter selalu berjumlah VMS_PER_HOST.
     */
    private static final int[][][] DC_VMS_PER_HOST = {
            {   // DC-1: tiap host = 2 Small + 2 Medium
                    {1, 1024, 1000, 2},
                    {2, 2048, 1500, 2},
            },
            {   // DC-2: tiap host = 4 Large
                    {4, 4096, 2000, 4},
            },
    };

    /**
     * Mode Kebijakan Alokasi VM ke Host Fisik:
     * 1. PENYERATAAN (ROUND_ROBIN / BALANCED):
     *    Mendistribusikan VM secara merata bergiliran ke seluruh 5 host fisik di setiap Datacenter.
     *    Hasil: setiap host menampung tepat 4 VM (20 VM per Datacenter).
     *    TIDAK ADA HOST YANG NGANGGUR (100% Host aktif dimanfaatkan).
     *
     * 2. BEST_FIT (CONSOLIDATION):
     *    Memadatkan VM ke host dengan sisa resource paling minimal untuk efisiensi energi.
     *
     * 3. FIRST_FIT:
     *    Memilih host pertama yang memiliki kapasitas mencukupi.
     */
    public enum AllocationMode {
        PENYERATAAN_ROUND_ROBIN,
        BEST_FIT,
        FIRST_FIT
    }

    public static AllocationMode DEFAULT_ALLOCATION_MODE = AllocationMode.PENYERATAAN_ROUND_ROBIN;

    // -------------------------------------------------------------------------
    // API UTAMA: Bangun 2 Datacenter Sekaligus
    // -------------------------------------------------------------------------

    public static MultiDatacenterResult buildMultiple(CloudSimPlus simulation) {
        return buildMultiple(simulation, DEFAULT_ALLOCATION_MODE);
    }

    public static MultiDatacenterResult buildMultiple(CloudSimPlus simulation, AllocationMode mode) {
        List<DatacenterResult> dcResults = new ArrayList<>();
        List<Vm>   allVms   = new ArrayList<>();
        List<Host> allHosts = new ArrayList<>();
        Map<Vm, Datacenter> vmToDcMap = new IdentityHashMap<>();

        System.out.printf("%n  Membangun %d Datacenter Heterogen Multi-Region...%n", DC_NAMES.length);
        System.out.printf("  [Kebijakan Alokasi VM-ke-Host] : %s (Penyerataan: %d VM per Host)%n", mode, VMS_PER_HOST);
        System.out.println("  " + "-".repeat(60));

        for (int dcIdx = 0; dcIdx < DC_NAMES.length; dcIdx++) {
            System.out.printf("%n  [%s]%n", DC_NAMES[dcIdx]);

            List<Host> hostList = createHostsForDc(DC_HOSTS[dcIdx]);
            List<Vm> vmList = createVmsForDc(DC_VMS_PER_HOST[dcIdx], hostList.size(), DC_NAMES[dcIdx]);

            // Pastikan setiap host mampu menampung 4 VM-nya (core, MIPS, RAM).
            validateHostCapacity(hostList, DC_VMS_PER_HOST[dcIdx], DC_NAMES[dcIdx]);

            // Tentukan kebijakan alokasi VM ke Host fisik
            // Penjelasan Ilmiah Kebijakan Alokasi Host:
            // 1. FIRST-FIT:
            //    Memilih host pertama yang ditemukan dalam daftar host yang memiliki kapasitas PE dan RAM mencukupi.
            //    Karakteristik: Komputasi cepat, namun rentan penumpukan pada indeks awal dan fragmentasi resource.
            // 2. BEST-FIT (VmAllocationPolicyBestFit):
            //    Mengevaluasi seluruh host kandidat dan memilih host dengan sisa resource paling minimal setelah VM dialokasikan.
            //    Karakteristik: Memadatkan VM ke sejumlah kecil host (server consolidation) sehingga host lain dapat idle.
            // 3. PENYERATAAN (VmAllocationPolicyRoundRobin / Balanced Allocation):
            //    Mendistribusikan VM secara siklik bergiliran ke seluruh 5 host fisik di setiap Datacenter.
            //    Karakteristik: Memastikan JANGAN SAMPAI ADA HOST YANG NGANGGUR! Dengan 5 host dan 20 VM per Datacenter,
            //    setiap host menampung tepat 4 VM. Seluruh host berpartisipasi aktif,
            //    beban panas dan komputasi tersebar merata, dan pembagian VM ke host tetap 100% konsisten pada tiap run.
            VmAllocationPolicy allocationPolicy = switch (mode) {
                case PENYERATAAN_ROUND_ROBIN -> new VmAllocationPolicyRoundRobin();
                case BEST_FIT -> new VmAllocationPolicyBestFit();
                case FIRST_FIT -> new VmAllocationPolicySimple();
            };

            Datacenter datacenter = new DatacenterSimple(simulation, hostList, allocationPolicy);
            datacenter.setName(DC_NAMES[dcIdx]);

            for (Vm vm : vmList) {
                vmToDcMap.put(vm, datacenter);
                vm.setDescription(DC_NAMES[dcIdx] + " [PE:" + vm.getPesNumber() + ", MIPS:" + (long) vm.getMips() + "]");
            }

            dcResults.add(new DatacenterResult(datacenter, vmList));
            allVms.addAll(vmList);
            allHosts.addAll(hostList);
        }

        printMultiDatacenterSummary(dcResults, allHosts, allVms);
        return new MultiDatacenterResult(dcResults, allVms, allHosts, vmToDcMap);
    }

    // -------------------------------------------------------------------------
    // HELPER: Pembuatan Host
    // -------------------------------------------------------------------------

    /**
     * Membuat host untuk sebuah datacenter, satu host untuk setiap entri tipe pada hostTypes.
     */
    private static List<Host> createHostsForDc(int[] hostTypes) {
        List<Host> hostList = new ArrayList<>();

        for (int i = 0; i < hostTypes.length; i++) {
            int typeIdx     = hostTypes[i];
            int mipsPerCore = HOST_TYPES[typeIdx][0];
            int ramGB       = HOST_TYPES[typeIdx][1];
            int numPes      = HOST_TYPES[typeIdx][2];

            List<Pe> peList = new ArrayList<>();
            for (int pe = 0; pe < numPes; pe++) {
                peList.add(new PeSimple(mipsPerCore));
            }

            long ramMB = (long) ramGB * 1024;
            Host host = new HostSimple(ramMB, HOST_BW, HOST_STORAGE, peList)
                    .setVmScheduler(new VmSchedulerTimeShared());

            host.setPowerModel(new PowerModelHostSimple(
                    POWER_SPECS[typeIdx][1],   // max power (Watts)
                    POWER_SPECS[typeIdx][0]    // idle power (Watts)
            ));
            host.enableUtilizationStats();
            hostList.add(host);

            System.out.printf("    [Host #%d Tipe-%s] %d MIPS/core x %d PEs, %d GB RAM%n",
                    i + 1, HOST_TYPE_NAMES[typeIdx], mipsPerCore, numPes, ramGB);
        }
        return hostList;
    }

    /**
     * Memastikan setiap host sanggup menampung komposisi 4 VM-nya. Satu core fisik hanya
     * dipakai satu VM, sehingga jika tidak muat CloudSim akan terus mencoba membuat VM
     * dan simulasi macet. Program dihentikan dengan pesan yang jelas.
     */
    private static void validateHostCapacity(List<Host> hostList, int[][] vmsPerHost, String dcName) {
        int vmCount = Arrays.stream(vmsPerHost).mapToInt(v -> v[3]).sum();
        long pesNeeded = Arrays.stream(vmsPerHost).mapToLong(v -> (long) v[0] * v[3]).sum();
        long ramNeeded = Arrays.stream(vmsPerHost).mapToLong(v -> (long) v[1] * v[3]).sum();
        int maxVmMips  = Arrays.stream(vmsPerHost).mapToInt(v -> v[2]).max().orElse(0);

        if (vmCount != VMS_PER_HOST) {
            throw new IllegalStateException(String.format(
                    "%s: komposisi VM per host berjumlah %d, seharusnya %d.", dcName, vmCount, VMS_PER_HOST));
        }
        for (int i = 0; i < hostList.size(); i++) {
            Host host = hostList.get(i);
            boolean fits = host.getPesNumber() >= pesNeeded
                    && host.getRam().getCapacity() >= ramNeeded
                    && host.getPeList().get(0).getCapacity() >= maxVmMips;
            if (!fits) {
                throw new IllegalStateException(String.format(
                        "%s Host #%d (%d core, %d MB RAM, %d MIPS/core) tidak muat untuk %d VM " +
                        "(butuh %d core, %d MB RAM, %d MIPS/core).",
                        dcName, i + 1, host.getPesNumber(), host.getRam().getCapacity(),
                        (long) host.getPeList().get(0).getCapacity(),
                        VMS_PER_HOST, pesNeeded, ramNeeded, maxVmMips));
            }
        }
    }

    // -------------------------------------------------------------------------
    // HELPER: Pembuatan VM
    // -------------------------------------------------------------------------

    /**
     * Membuat seluruh VM sebuah datacenter (komposisi per host x jumlah host).
     * VM dibuat berurutan per kelas, sehingga penyerataan round-robin menempatkan
     * komposisi yang sama pada setiap host (mis. 2 Small + 2 Medium per host).
     * Menggunakan CloudletSchedulerSpaceShared (non-preemptive).
     */
    private static List<Vm> createVmsForDc(int[][] vmsPerHost, int hostCount, String dcName) {
        List<Vm> vmList = new ArrayList<>();

        for (int[] spec : vmsPerHost) {
            int pes   = spec[0];
            int ramMB = spec[1];
            int mips  = spec[2];
            int count = spec[3] * hostCount;
            String vmType = pes == 1 ? "Small" : (pes == 2 ? "Medium" : "Large");

            for (int i = 0; i < count; i++) {
                Vm vm = new VmSimple(mips, pes)
                        .setRam(ramMB)
                        .setBw(250)       // 250 Mbps per VM
                        .setSize(10_000)  // 10 GB storage
                        .setCloudletScheduler(new CloudletSchedulerSpaceShared());
                vmList.add(vm);
            }

            System.out.printf("    [VM %s] %d MIPS x %d PE, %d MB RAM x %d unit (%d per host)%n",
                    vmType, mips, pes, ramMB, count, spec[3]);
        }
        return vmList;
    }

    // -------------------------------------------------------------------------
    // HELPER: Ringkasan Info
    // -------------------------------------------------------------------------

    private static void printMultiDatacenterSummary(
            List<DatacenterResult> dcResults, List<Host> allHosts, List<Vm> allVms) {

        System.out.println("\n  +============================================================+");
        System.out.println("  |            RINGKASAN ARSITEKTUR MULTI-DATACENTER           |");
        System.out.println("  +============================================================+");

        for (int i = 0; i < dcResults.size(); i++) {
            DatacenterResult dcr = dcResults.get(i);
            List<Host> hosts = dcr.datacenter().getHostList();
            long totalMips = hosts.stream()
                    .mapToLong(h -> h.getPeList().stream().mapToLong(Pe::getCapacity).sum())
                    .sum();
            long totalRam  = hosts.stream().mapToLong(h -> h.getRam().getCapacity()).sum();
            System.out.printf("    %-24s : %2d Host | %2d VM | %,7d MIPS | %5.1f GB RAM%n",
                    DC_NAMES[i], hosts.size(), dcr.vmList().size(), totalMips, totalRam / 1024.0);
        }

        long grandTotalMips = allHosts.stream()
                .mapToLong(h -> h.getPeList().stream().mapToLong(Pe::getCapacity).sum())
                .sum();
        long grandTotalRam = allHosts.stream().mapToLong(h -> h.getRam().getCapacity()).sum();

        System.out.println("  +------------------------------------------------------------+");
        System.out.printf("    %-24s : %2d Host | %2d VM | %,7d MIPS | %5.1f GB RAM%n",
                "TOTAL INFRASTRUKTUR", allHosts.size(), allVms.size(), grandTotalMips, grandTotalRam / 1024.0);
        System.out.printf("    [SINKRONISASI]           : Seluruh VM dari %d DC dikelola 1 Broker%n", dcResults.size());
        System.out.println("  +============================================================+\n");
    }

    /**
     * Mencetak bukti verifikasi alokasi VM ke setiap host fisik untuk membuktikan:
     * 1. Setiap host menampung tepat 4 VM (TIDAK ADA HOST YANG NGANGGUR).
     * 2. Kebijakan penyerataan (balanced) terbukti berjalan deterministik dan konsisten.
     * 3. Rincian beban core dan RAM per host tercatat transparan.
     */
    public static void printDetailedVmAllocationTable(MultiDatacenterResult multiDc) {
        System.out.println("\n  +==================================================================================================+");
        System.out.println("  |       BUKTI VERIFIKASI ALOKASI VM KE HOST FISIK (PENYERATAAN - TIDAK ADA HOST NGANGGUR)         |");
        System.out.println("  +==================================================================================================+");
        System.out.printf("  | %-26s | %-10s | %-16s | %-14s | %-26s |%n",
                "Datacenter", "Host Unit", "Kapasitas Host", "RAM Terpakai", "VM Ditempatkan");
        System.out.println("  +----------------------------+------------+------------------+----------------+----------------------------+");
        boolean allHostsHaveFourVms = true;
        for (DatacenterResult dcr : multiDc.dcResults()) {
            Datacenter dc = dcr.datacenter();
            int hostIdx = 1;
            for (Host h : dc.getHostList()) {
                List<Vm> vms = h.getVmCreatedList();
                if (vms.size() != VMS_PER_HOST) allHostsHaveFourVms = false;
                long ramUsedMB = vms.stream().mapToLong(v -> v.getRam().getCapacity()).sum();
                long ramTotalMB = h.getRam().getCapacity();
                String vmSummary = vms.isEmpty() ? "[NGANGGUR 0 VM]" : (vms.size() + " VM (IDs:" + vms.stream().map(v -> String.valueOf(v.getId())).toList() + ")");
                long mipsPerCore = (long) h.getPeList().get(0).getCapacity();
                String hostCap = h.getPesNumber() + " PE x " + mipsPerCore + "M";
                System.out.printf("  | %-26s | Host #%-4d | %-16s | %5d/%5d MB | %-26s |%n",
                        dc.getName(), hostIdx++, hostCap, ramUsedMB, ramTotalMB, vmSummary);
            }
            System.out.println("  +----------------------------+------------+------------------+----------------+----------------------------+");
        }
        System.out.printf("  | Status Verifikasi: %-78s |%n", allHostsHaveFourVms
                ? "100% Host Aktif Berimbang (setiap host menampung " + VMS_PER_HOST + " VM)"
                : "[WARN] Ada host yang tidak menampung " + VMS_PER_HOST + " VM");
        System.out.println("  +==================================================================================================+\n");
    }

    // RECORD TYPES
    // -------------------------------------------------------------------------

    /** Hasil build untuk satu datacenter. */
    public static record DatacenterResult(Datacenter datacenter, List<Vm> vmList) {}

    /**
     * Hasil build untuk seluruh datacenter sekaligus.
     * Menyediakan akses ke:
     *   - dcResults : daftar DatacenterResult per DC (untuk info per-DC)
     *   - allVms    : gabungan semua VM dari seluruh DC (disubmit ke satu broker)
     *   - allHosts  : gabungan semua host dari seluruh DC (untuk metrik energi)
     */
    public static record MultiDatacenterResult(
            List<DatacenterResult> dcResults,
            List<Vm>              allVms,
            List<Host>            allHosts,
            Map<Vm, Datacenter>   vmToDatacenterMap
    ) {
        /** Host dari datacenter ke-dcIdx (0-based). */
        public List<Host> hostsOf(int dcIdx) {
            return dcResults.get(dcIdx).datacenter().getHostList();
        }

        /** Mendapatkan Datacenter tempat VM dialokasikan. */
        public Datacenter getDatacenterForVm(Vm vm) {
            if (vm == null) return null;
            if (vmToDatacenterMap != null && vmToDatacenterMap.containsKey(vm)) {
                return vmToDatacenterMap.get(vm);
            }
            for (DatacenterResult dcr : dcResults) {
                for (Vm v : dcr.vmList()) {
                    if (v == vm) {
                        return dcr.datacenter();
                    }
                }
            }
            return null;
        }

        /** Mendapatkan nama Datacenter tempat VM dialokasikan. */
        public String getDatacenterNameForVm(Vm vm) {
            Datacenter dc = getDatacenterForVm(vm);
            return dc != null ? dc.getName() : "Unknown-DC";
        }
    }
}
