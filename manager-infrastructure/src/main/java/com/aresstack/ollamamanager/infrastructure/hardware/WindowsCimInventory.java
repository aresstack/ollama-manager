package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuVendor;
import com.aresstack.ollamamanager.infrastructure.json.Json;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads CPU, RAM and video controllers through PowerShell CIM (no {@code wmic}, no admin rights).
 * VRAM comes from the display class registry key ({@code HardwareInformation.qwMemorySize}) because
 * {@code Win32_VideoController.AdapterRAM} is a 32-bit value capped at 4 GiB.
 */
public final class WindowsCimInventory {

    static final String SCRIPT = "$ErrorActionPreference='Stop';[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
        + "$v=@(Get-CimInstance Win32_VideoController | Select-Object Name,AdapterCompatibility,DriverVersion,"
        + "AdapterRAM,PNPDeviceID);"
        + "$r=@(Get-ItemProperty 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Class\\"
        + "{4d36e968-e325-11ce-bfc1-08002be10318}\\0*' -ErrorAction SilentlyContinue | "
        + "Select-Object MatchingDeviceId,'HardwareInformation.qwMemorySize');"
        + "$p=Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name;"
        + "$c=Get-CimInstance Win32_ComputerSystem;"
        + "[pscustomobject]@{gpus=$v;registry=$r;cpu=$p;memory=$c.TotalPhysicalMemory}"
        + " | ConvertTo-Json -Depth 4 -Compress";

    private static final long ADAPTER_RAM_CAP = 0xFFF00000L;
    private static final Pattern VENDOR_ID = Pattern.compile("VEN_([0-9A-F]{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMD_DEDICATED = Pattern.compile(
        "\\b(rx\\s*\\d{3,4}|r[579]\\s*\\d{3}|radeon\\s+(pro|vii|r9)|vega\\s*(56|64)|instinct|firepro)\\b");
    private static final Pattern INTEL_DEDICATED = Pattern.compile("\\barc\\s*(\\(tm\\)\\s*)?(pro\\s*)?[ab]\\d{3}");

    private final CommandRunner runner;
    private final long timeoutMillis;

    public WindowsCimInventory(CommandRunner runner, long timeoutMillis) {
        this.runner = runner;
        this.timeoutMillis = timeoutMillis;
    }

    /** Return the parsed inventory, or {@code null} if PowerShell is unavailable or its output unusable. */
    public Inventory read(CancellationToken cancellation) throws OperationException {
        String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(Charset.forName("UTF-16LE")));
        CommandResult result = runner.run(Arrays.asList("powershell.exe", "-NoProfile", "-NonInteractive",
            "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded), timeoutMillis, cancellation);
        return result.isSuccess() ? parse(result.stdout()) : null;
    }

    static Inventory parse(String json) {
        Object root;
        try {
            root = Json.parse(json.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!(root instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) root;
        List<Map<?, ?>> registry = objects(map.get("registry"));
        List<GpuDescriptor> gpus = new ArrayList<GpuDescriptor>();
        for (Map<?, ?> controller : objects(map.get("gpus"))) {
            GpuDescriptor gpu = toGpu(controller, registry);
            if (gpu != null) {
                gpus.add(gpu);
            }
        }
        Object cpu = map.get("cpu");
        String cpuName = cpu instanceof String && !((String) cpu).trim().isEmpty() ? ((String) cpu).trim() : null;
        Long memory = positiveLong(map.get("memory"));
        return new Inventory(cpuName, memory, gpus);
    }

    private static GpuDescriptor toGpu(Map<?, ?> controller, List<Map<?, ?>> registry) {
        String name = string(controller.get("Name"));
        if (name == null) {
            return null;
        }
        String pnp = string(controller.get("PNPDeviceID"));
        GpuVendor vendor = vendor(pnp, string(controller.get("AdapterCompatibility")));
        if (pnp != null && !pnp.toUpperCase(Locale.ROOT).startsWith("PCI\\")) {
            // Skip virtual adapters such as Microsoft Basic Display or remote display drivers.
            return null;
        }
        Long vram = registryMemory(pnp, registry);
        if (vram == null) {
            Long adapterRam = positiveLong(controller.get("AdapterRAM"));
            vram = adapterRam != null && adapterRam < ADAPTER_RAM_CAP ? adapterRam : null;
        }
        return new GpuDescriptor(vendor, name, vram, string(controller.get("DriverVersion")),
            isDedicated(vendor, name));
    }

    static boolean isDedicated(GpuVendor vendor, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        switch (vendor) {
            case NVIDIA:
                return true;
            case AMD:
                return AMD_DEDICATED.matcher(lower).find();
            case INTEL:
                return INTEL_DEDICATED.matcher(lower).find();
            default:
                return false;
        }
    }

    private static GpuVendor vendor(String pnp, String compatibility) {
        if (pnp != null) {
            Matcher matcher = VENDOR_ID.matcher(pnp);
            if (matcher.find()) {
                String id = matcher.group(1).toUpperCase(Locale.ROOT);
                if (id.equals("10DE")) {
                    return GpuVendor.NVIDIA;
                }
                if (id.equals("1002") || id.equals("1022")) {
                    return GpuVendor.AMD;
                }
                if (id.equals("8086")) {
                    return GpuVendor.INTEL;
                }
            }
        }
        String lower = compatibility == null ? "" : compatibility.toLowerCase(Locale.ROOT);
        if (lower.contains("nvidia")) {
            return GpuVendor.NVIDIA;
        }
        if (lower.contains("advanced micro devices") || lower.contains("amd") || lower.contains("ati ")) {
            return GpuVendor.AMD;
        }
        return lower.contains("intel") ? GpuVendor.INTEL : GpuVendor.UNKNOWN;
    }

    private static Long registryMemory(String pnp, List<Map<?, ?>> registry) {
        if (pnp == null) {
            return null;
        }
        String device = pnp.toLowerCase(Locale.ROOT);
        for (Map<?, ?> entry : registry) {
            String matching = string(entry.get("MatchingDeviceId"));
            if (matching != null && device.startsWith(matching.toLowerCase(Locale.ROOT))) {
                Long size = positiveLong(entry.get("HardwareInformation.qwMemorySize"));
                if (size != null) {
                    return size;
                }
            }
        }
        return null;
    }

    private static List<Map<?, ?>> objects(Object value) {
        List<Map<?, ?>> result = new ArrayList<Map<?, ?>>();
        if (value instanceof Map) {
            result.add((Map<?, ?>) value);
        } else if (value instanceof List) {
            for (Object element : (List<?>) value) {
                if (element instanceof Map) {
                    result.add((Map<?, ?>) element);
                }
            }
        }
        return result;
    }

    private static String string(Object value) {
        return value instanceof String && !((String) value).trim().isEmpty() ? ((String) value).trim() : null;
    }

    private static Long positiveLong(Object value) {
        if (value instanceof Long && (Long) value > 0) {
            return (Long) value;
        }
        return null;
    }

    /** Parsed CIM view; unknown values are {@code null}. */
    public static final class Inventory {

        private final String cpuName;
        private final Long totalMemoryBytes;
        private final List<GpuDescriptor> gpus;

        Inventory(String cpuName, Long totalMemoryBytes, List<GpuDescriptor> gpus) {
            this.cpuName = cpuName;
            this.totalMemoryBytes = totalMemoryBytes;
            this.gpus = Collections.unmodifiableList(gpus);
        }

        public String cpuName() {
            return cpuName;
        }

        public Long totalMemoryBytes() {
            return totalMemoryBytes;
        }

        public List<GpuDescriptor> gpus() {
            return gpus;
        }
    }
}
