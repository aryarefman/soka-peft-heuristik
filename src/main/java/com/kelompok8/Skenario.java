package com.kelompok8;

import java.util.List;
import java.util.Locale;

/**
 * Skenario infrastruktur simulasi yang dapat dipilih saat runtime.
 *
 * Dosen meminta dua kasus arsitektur yang dapat dijalankan dari kode yang sama:
 *
 * 1. {@link #TIGA_DC} (default) - 3 Datacenter heterogen, 8 host per DC (24 host), 10 VM per DC (30 VM).
 * 2. {@link #DUA_DC}            - 2 Datacenter heterogen, 5 host per DC (10 host), 20 VM per DC (40 VM),
 *                                 tepat 4 VM per host.
 *
 * Cara memilih skenario (urutan prioritas):
 *   - argumen program "3dc" / "2dc"           : ./gradlew run --args="ALL 2dc"
 *   - system property -Dskenario=2dc          : ./gradlew runAll -Pskenario=2dc
 *   - default                                 : 3dc
 */
public enum Skenario {

    TIGA_DC("3dc", "3 Datacenter Heterogen", 8,
            new int[][] {
                    {1000, 8},    // Tipe A: 1.000 MIPS/core, 8 GB RAM
                    {1500, 16},   // Tipe B: 1.500 MIPS/core, 16 GB RAM
                    {2000, 24},   // Tipe C: 2.000 MIPS/core, 24 GB RAM
                    {3000, 32},   // Tipe D: 3.000 MIPS/core, 32 GB RAM
            },
            List.of(
                    new DcSpec("DC-1: Jakarta (Entry-Level)", new int[] {0, 0, 0, 0, 1, 1, 1, 1}, 1, 1024, 1000, 10),
                    new DcSpec("DC-2: Surabaya (Standard)",   new int[] {1, 1, 1, 1, 2, 2, 2, 2}, 2, 2048, 1500, 10),
                    new DcSpec("DC-3: Bandung (Premium)",     new int[] {2, 2, 2, 2, 3, 3, 3, 3}, 4, 4096, 2000, 10))),

    DUA_DC("2dc", "2 Datacenter Heterogen", 16,
            new int[][] {
                    {1000, 32},   // Tipe A: 1.000 MIPS/core, 32 GB RAM
                    {1500, 32},   // Tipe B: 1.500 MIPS/core, 32 GB RAM
                    {2000, 64},   // Tipe C: 2.000 MIPS/core, 64 GB RAM
                    {3000, 64},   // Tipe D: 3.000 MIPS/core, 64 GB RAM
            },
            List.of(
                    new DcSpec("DC-1: Jakarta (Standard)", new int[] {0, 0, 0, 1, 1}, 2, 2048, 1000, 20),
                    new DcSpec("DC-2: Bandung (Premium)",  new int[] {2, 2, 2, 3, 3}, 4, 4096, 2000, 20)));

    /** SPECpower {idle W, max W} per tipe host (Beloglazov & Buyya, 2012), sama untuk kedua skenario. */
    public static final double[][] POWER_SPECS = {
            {93.7,  135.0},  // Tipe A: HP ProLiant ML110 G5
            {105.0, 175.0},  // Tipe B: IBM System x3250 M2
            {120.0, 225.0},  // Tipe C: IBM System x3550 M3
            {140.0, 300.0},  // Tipe D: High-end enterprise server
    };

    public static final String[] HOST_TYPE_NAMES = {"A", "B", "C", "D"};

    /**
     * Spesifikasi satu datacenter.
     *
     * @param hostPlan indeks tipe host untuk setiap unit host (panjang array = jumlah host di DC)
     */
    public record DcSpec(String name, int[] hostPlan, int vmPes, int vmRamMB, int vmMips, int vmCount) {
        public String vmClass() {
            return vmPes == 1 ? "Small" : (vmPes == 2 ? "Medium" : "Large");
        }
    }

    public final String id;
    public final String label;
    public final int pesPerHost;
    public final int[][] hostTypes;
    public final List<DcSpec> datacenters;

    Skenario(String id, String label, int pesPerHost, int[][] hostTypes, List<DcSpec> datacenters) {
        this.id = id;
        this.label = label;
        this.pesPerHost = pesPerHost;
        this.hostTypes = hostTypes;
        this.datacenters = datacenters;
    }

    public int totalHosts() {
        return datacenters.stream().mapToInt(dc -> dc.hostPlan().length).sum();
    }

    public int totalVms() {
        return datacenters.stream().mapToInt(DcSpec::vmCount).sum();
    }

    /** Ringkasan singkat, mis. "3 Datacenter (24 Host, 30 VM)". */
    public String ringkasan() {
        return String.format("%d Datacenter (%d Host, %d VM)", datacenters.size(), totalHosts(), totalVms());
    }

    /** Direktori hasil CSV khusus skenario ini, mis. "results/3dc". */
    public String resultsDir() {
        return "results/" + id;
    }

    /** Direktori grafik khusus skenario ini, mis. "docs/images/3dc". */
    public String imagesDir() {
        return "docs/images/" + id;
    }

    public static Skenario dariId(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        for (Skenario s : values()) {
            if (s.id.equals(v)) return s;
        }
        throw new IllegalArgumentException("Skenario tidak dikenal: " + value + " (pilihan: 3dc, 2dc)");
    }

    /** Membaca skenario dari argumen program, lalu system property "skenario", lalu default 3dc. */
    public static Skenario dariArgs(String[] args) {
        for (String arg : args) {
            String v = arg.toLowerCase(Locale.ROOT).replace("--skenario=", "");
            if (v.equals("3dc") || v.equals("2dc")) return dariId(v);
        }
        String prop = System.getProperty("skenario");
        return (prop == null || prop.isBlank()) ? TIGA_DC : dariId(prop);
    }
}
