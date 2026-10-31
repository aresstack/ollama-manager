package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuVendor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WindowsCimInventoryTest {

    @Test
    public void parsesDedicatedAmdWithRegistryVram() {
        WindowsCimInventory.Inventory inventory = WindowsCimInventory.parse("{\"gpus\":[{\"Name\":"
            + "\"AMD Radeon RX 7900 XTX\",\"AdapterCompatibility\":\"Advanced Micro Devices, Inc.\","
            + "\"DriverVersion\":\"31.0.24027.1012\",\"AdapterRAM\":4293918720,"
            + "\"PNPDeviceID\":\"PCI\\\\VEN_1002&DEV_744C&SUBSYS_0E3B1002&REV_C8\\\\6&1\"}],"
            + "\"registry\":[{\"MatchingDeviceId\":\"pci\\\\ven_1002&dev_744c\","
            + "\"HardwareInformation.qwMemorySize\":25753026560}],"
            + "\"cpu\":\"AMD Ryzen 9 7950X 16-Core Processor\",\"memory\":68719476736}");
        GpuDescriptor gpu = inventory.gpus().get(0);
        assertEquals(GpuVendor.AMD, gpu.vendor());
        assertTrue(gpu.isDedicated());
        assertEquals(Long.valueOf(25753026560L), gpu.vramBytes());
        assertEquals("AMD Ryzen 9 7950X 16-Core Processor", inventory.cpuName());
        assertEquals(Long.valueOf(68719476736L), inventory.totalMemoryBytes());
    }

    @Test
    public void treatsCappedAdapterRamAsUnknownAndSingleObjectAsList() {
        WindowsCimInventory.Inventory inventory = WindowsCimInventory.parse("{\"gpus\":{\"Name\":"
            + "\"AMD Radeon RX 9070 XT\",\"AdapterCompatibility\":\"Advanced Micro Devices, Inc.\","
            + "\"DriverVersion\":\"32.0.1\",\"AdapterRAM\":4293918720,\"PNPDeviceID\":\"PCI\\\\VEN_1002&DEV_7550\"},"
            + "\"registry\":[],\"cpu\":null,\"memory\":null}");
        assertNull(inventory.gpus().get(0).vramBytes());
        assertNull(inventory.cpuName());
        assertNull(inventory.totalMemoryBytes());
    }

    @Test
    public void classifiesIntegratedGraphicsAndSkipsVirtualAdapters() {
        WindowsCimInventory.Inventory inventory = WindowsCimInventory.parse("{\"gpus\":["
            + "{\"Name\":\"Intel(R) UHD Graphics 770\",\"AdapterCompatibility\":\"Intel Corporation\","
            + "\"DriverVersion\":\"31.0.101.4502\",\"AdapterRAM\":2147483648,\"PNPDeviceID\":\"PCI\\\\VEN_8086&DEV_A780\"},"
            + "{\"Name\":\"AMD Radeon(TM) Graphics\",\"AdapterCompatibility\":\"Advanced Micro Devices, Inc.\","
            + "\"DriverVersion\":\"31.0.1\",\"AdapterRAM\":536870912,\"PNPDeviceID\":\"PCI\\\\VEN_1002&DEV_164E\"},"
            + "{\"Name\":\"Microsoft Remote Display Adapter\",\"AdapterCompatibility\":\"Microsoft\","
            + "\"PNPDeviceID\":\"SWD\\\\REMOTEDISPLAYENUM\\\\RDPIDD\"}],"
            + "\"registry\":[],\"cpu\":\"Intel(R) Core(TM) i7-14700K\",\"memory\":34359738368}");
        assertEquals(2, inventory.gpus().size());
        assertFalse(inventory.gpus().get(0).isDedicated());
        assertFalse(inventory.gpus().get(1).isDedicated());
        assertEquals(Long.valueOf(2147483648L), inventory.gpus().get(0).vramBytes());
    }

    @Test
    public void recognisesIntelArcAsDedicated() {
        assertTrue(WindowsCimInventory.isDedicated(GpuVendor.INTEL, "Intel(R) Arc(TM) A770 Graphics"));
        assertTrue(WindowsCimInventory.isDedicated(GpuVendor.AMD, "Radeon RX 580 Series"));
        assertFalse(WindowsCimInventory.isDedicated(GpuVendor.UNKNOWN, "Something"));
    }

    @Test
    public void malformedOutputGivesNull() {
        assertNull(WindowsCimInventory.parse("Get-CimInstance : Access denied"));
        assertNull(WindowsCimInventory.parse("[1,2]"));
    }
}
