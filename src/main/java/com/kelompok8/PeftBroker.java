package com.kelompok8;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * Algoritma PEFT (Predict Earliest Finish Time) Broker.
 *
 * Algoritma PEFT adalah algoritma heuristik penjadwalan yang memprediksi
 * waktu penyelesaian paling awal (Earliest Finish Time / EFT) dari setiap task
 * pada kumpulan sumber daya komputasi (VM) yang heterogen.
 *
 * Formulasi Matematis:
 * Untuk task_i dengan panjang L_i (MI), kebutuhan prosesor P_i (PE),
 * dan waktu kedatangan a_i (submission delay):
 *
 * 1. Filter VM yang memenuhi batasan kesesuaian alokasi (Section E.4):
 *      EligibleVMs(task_i) = { vm_j in VM_List | vm_j.getPesNumber() >= P_i }
 *
 * 2. Hitung Earliest Start Time (EST):
 *      EST(task_i, vm_j) = max( a_i, AvailableTime(vm_j, P_i) )
 *    dimana AvailableTime(vm_j, P_i) adalah waktu saat setidaknya P_i core
 *    pada vm_j telah selesai mengeksekusi task-task sebelumnya.
 *
 * 3. Hitung Estimated Execution Time (EET):
 *      EET(task_i, vm_j) = L_i / vm_j.getMips()
 *
 * 4. Hitung Predicted Earliest Finish Time (EFT):
 *      EFT(task_i, vm_j) = EST(task_i, vm_j) + EET(task_i, vm_j)
 *
 * 5. Pilih VM optimal:
 *      vm* = argmin_{vm_j in EligibleVMs(task_i)} EFT(task_i, vm_j)
 *
 * 6. Perbarui jadwal ketersediaan core pada vm* dan bind task ke vm*.
 *
 * Constraint tambahan (tidak boleh ada VM kosong):
 *    Selama masih ada VM eligible yang belum menerima task, kandidat dibatasi pada
 *    VM kosong tersebut (tetap dipilih yang EFT-nya terkecil). Setelah setiap VM
 *    menerima minimal 1 task, pemilihan kembali ke seluruh VM eligible.
 */
public class PeftBroker extends DatacenterBrokerSimple {

    public PeftBroker(final CloudSimPlus simulation) {
        super(simulation);
    }

    @Override
    protected void requestDatacentersToCreateWaitingCloudlets() {
        final List<Cloudlet> waitingList = getCloudletWaitingList();
        final List<Vm> vmList = getVmExecList();

        if (waitingList.isEmpty() || vmList.isEmpty()) {
            super.requestDatacentersToCreateWaitingCloudlets();
            return;
        }

        // Struktur pelacakan ketersediaan setiap PE/core pada masing-masing VM
        // Map: VmId -> double[] (waktu ready setiap core)
        Map<Long, double[]> vmPeReadyTimes = new HashMap<>();
        for (Vm vm : vmList) {
            int numPes = (int) vm.getPesNumber();
            vmPeReadyTimes.put(vm.getId(), new double[numPes]);
        }

        // Prioritas penjadwalan (LPT heuristic dengan arrival time):
        // Urutkan task berdasarkan waktu kedatangan (submission delay).
        // Untuk waktu kedatangan yang sama atau berdekatan, prioritaskan task terpanjang (LPT).
        waitingList.sort(Comparator
                .comparingDouble(Cloudlet::getSubmissionDelay)
                .thenComparing((c1, c2) -> Long.compare(c2.getLength(), c1.getLength())));

        // VM yang sudah menerima minimal 1 task
        Set<Long> usedVmIds = new HashSet<>();

        // Proses penjadwalan PEFT untuk setiap task
        for (Cloudlet cloudlet : waitingList) {
            long taskLength = cloudlet.getLength();
            int taskPes = (int) cloudlet.getPesNumber();
            double arrivalTime = cloudlet.getSubmissionDelay();

            // Constraint VM tidak boleh kosong: jika masih ada VM eligible yang kosong,
            // task hanya boleh dipetakan ke VM kosong tersebut.
            boolean onlyEmptyVms = vmList.stream()
                    .anyMatch(vm -> vm.getPesNumber() >= taskPes && !usedVmIds.contains(vm.getId()));

            Vm bestVm = null;
            double minEft = Double.MAX_VALUE;
            double bestEst = 0.0;

            for (Vm vm : vmList) {
                // Batasan Kesesuaian Alokasi: VM harus memiliki PE yang cukup
                if (vm.getPesNumber() < taskPes) {
                    continue;
                }
                if (onlyEmptyVms && usedVmIds.contains(vm.getId())) {
                    continue;
                }

                double[] peReady = vmPeReadyTimes.get(vm.getId());
                // Salin dan urutkan waktu ketersediaan core
                double[] sortedReady = Arrays.copyOf(peReady, peReady.length);
                Arrays.sort(sortedReady);

                // Core ke-(taskPes - 1) menentukan kapan minimal taskPes core siap bersamaan
                double coreAvailableTime = sortedReady[taskPes - 1];
                double est = Math.max(arrivalTime, coreAvailableTime);
                double eet = (double) taskLength / vm.getMips();
                double eft = est + eet;

                // Cari VM dengan EFT terkecil
                if (eft < minEft) {
                    minEft = eft;
                    bestEst = est;
                    bestVm = vm;
                } else if (Math.abs(eft - minEft) < 1e-6 && bestVm != null) {
                    // Tie-breaker: pilih VM dengan MIPS lebih tinggi (lebih cepat)
                    if (vm.getMips() > bestVm.getMips()) {
                        minEft = eft;
                        bestEst = est;
                        bestVm = vm;
                    }
                }
            }

            if (bestVm != null) {
                cloudlet.setVm(bestVm);
                usedVmIds.add(bestVm.getId());

                // Perbarui ketersediaan core pada VM yang terpilih
                double[] peReady = vmPeReadyTimes.get(bestVm.getId());
                Arrays.sort(peReady);
                for (int p = 0; p < taskPes; p++) {
                    peReady[p] = minEft;
                }
            }
        }

        // Jalankan pengiriman cloudlet ke datacenter
        super.requestDatacentersToCreateWaitingCloudlets();
    }
}
