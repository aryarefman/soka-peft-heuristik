package com.kelompok8;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;

import java.util.ArrayList;
import java.util.List;

public class TestVmHostAllocation {
    public static void main(String[] args) {
        CloudSimPlus sim = new CloudSimPlus();
        var multiDc = DatacenterBuilder.buildMultiple(sim);
        
        var broker = new DatacenterBrokerSimple(sim);
        broker.setDatacenterMapper((lastDc, vm) -> multiDc.getDatacenterForVm(vm));
        broker.submitVmList(multiDc.allVms());
        
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            cloudlets.add(new CloudletSimple(1000, 1));
        }
        broker.submitCloudletList(cloudlets);
        
        sim.start();
        
        // Print the detailed allocation table
        DatacenterBuilder.printDetailedVmAllocationTable(multiDc);
    }
}
