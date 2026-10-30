package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.hardware.GpuStatistics;
import com.aresstack.ollamamanager.application.hardware.GpuStatisticsSource;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuVendor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Queries {@code nvidia-smi} in CSV mode. Missing tool, non-zero exit or malformed output yields an empty
 * result so callers fall back to the OS view; only timeouts and cancellation propagate.
 */
public final class NvidiaSmi implements GpuStatisticsSource {

    static final String INVENTORY_QUERY = "--query-gpu=name,memory.total,driver_version";
    static final String STATISTICS_QUERY = "--query-gpu=index,name,utilization.gpu,memory.used,memory.total";
    private static final long MIB = 1024L * 1024L;

    private final CommandRunner runner;
    private final List<String> executables;
    private final long timeoutMillis;

    public NvidiaSmi(CommandRunner runner, List<String> executables, long timeoutMillis) {
        this.runner = runner;
        this.executables = Collections.unmodifiableList(new ArrayList<String>(executables));
        this.timeoutMillis = timeoutMillis;
    }

    /** Return the default lookup order: PATH, then the locations used by NVIDIA drivers on Windows. */
    public static List<String> defaultExecutables(boolean windows) {
        if (!windows) {
            return Collections.singletonList("nvidia-smi");
        }
        String systemRoot = System.getenv("SystemRoot");
        String programFiles = System.getenv("ProgramFiles");
        List<String> candidates = new ArrayList<String>();
        candidates.add("nvidia-smi");
        candidates.add((systemRoot == null ? "C:\\Windows" : systemRoot) + "\\System32\\nvidia-smi.exe");
        candidates.add((programFiles == null ? "C:\\Program Files" : programFiles)
            + "\\NVIDIA Corporation\\NVSMI\\nvidia-smi.exe");
        return candidates;
    }

    /** Return detected NVIDIA GPUs, or an empty list if nvidia-smi is unavailable or unusable. */
    public List<GpuDescriptor> inventory(CancellationToken cancellation) throws OperationException {
        String output = query(INVENTORY_QUERY, cancellation);
        return output == null ? Collections.<GpuDescriptor>emptyList() : parseInventory(output);
    }

    @Override
    public List<GpuStatistics> read(CancellationToken cancellation) throws OperationException {
        String output;
        try {
            output = query(STATISTICS_QUERY, cancellation);
        } catch (OperationCancelledException e) {
            throw e;
        } catch (OperationException e) {
            return Collections.emptyList();
        }
        return output == null ? Collections.<GpuStatistics>emptyList() : parseStatistics(output);
    }

    private String query(String fields, CancellationToken cancellation) throws OperationException {
        for (String executable : executables) {
            CommandResult result = runner.run(
                Arrays.asList(executable, fields, "--format=csv,noheader,nounits"), timeoutMillis, cancellation);
            if (!result.isFound()) {
                continue;
            }
            return result.isSuccess() ? result.stdout() : null;
        }
        return null;
    }

    /** Parse inventory CSV; any malformed line discards the whole output. */
    static List<GpuDescriptor> parseInventory(String output) {
        List<GpuDescriptor> gpus = new ArrayList<GpuDescriptor>();
        for (String line : lines(output)) {
            String[] fields = splitFromRight(line, 3);
            if (fields == null || fields[0].isEmpty()) {
                return Collections.emptyList();
            }
            Long vram = mebibytes(fields[1]);
            if (vram == null && !isNotAvailable(fields[1])) {
                return Collections.emptyList();
            }
            String driver = isNotAvailable(fields[2]) || fields[2].isEmpty() ? null : fields[2];
            gpus.add(new GpuDescriptor(GpuVendor.NVIDIA, fields[0], vram, driver, true));
        }
        return gpus;
    }

    static List<GpuStatistics> parseStatistics(String output) {
        List<GpuStatistics> stats = new ArrayList<GpuStatistics>();
        for (String line : lines(output)) {
            String[] head = line.split(",", 2);
            String[] tail = head.length == 2 ? splitFromRight(head[1], 4) : null;
            Integer index = integer(head[0]);
            if (tail == null || index == null || tail[0].isEmpty()) {
                return Collections.emptyList();
            }
            stats.add(new GpuStatistics(index, tail[0], integer(tail[1]), mebibytes(tail[2]), mebibytes(tail[3])));
        }
        return stats;
    }

    private static List<String> lines(String output) {
        List<String> lines = new ArrayList<String>();
        for (String line : output.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    /** Split into {@code count} fields where only the first may contain commas (GPU names can). */
    private static String[] splitFromRight(String line, int count) {
        String[] parts = line.split(",", -1);
        if (parts.length < count) {
            return null;
        }
        String[] fields = new String[count];
        int tail = parts.length - (count - 1);
        StringBuilder first = new StringBuilder();
        for (int i = 0; i < tail; i++) {
            first.append(i == 0 ? "" : ",").append(parts[i]);
        }
        fields[0] = first.toString().trim();
        for (int i = 1; i < count; i++) {
            fields[i] = parts[tail + i - 1].trim();
        }
        return fields;
    }

    private static boolean isNotAvailable(String value) {
        return value.startsWith("[") || value.equalsIgnoreCase("N/A");
    }

    private static Long mebibytes(String value) {
        Integer parsed = integer(value);
        return parsed == null || parsed < 0 ? null : Long.valueOf(parsed * MIB);
    }

    private static Integer integer(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
