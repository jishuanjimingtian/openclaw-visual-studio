package com.openclaw.vs.service;

import com.openclaw.vs.dto.SystemMetricsDto;
import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class SystemMetricsService {

    public SystemMetricsDto collect(int sessionCount, long tokenUsageToday) {
        return SystemMetricsDto.builder()
            .cpu(round(readCpuPercent()))
            .memory(round(readMemoryPercent()))
            .disk(round(readDiskPercent()))
            .uptime(ManagementFactory.getRuntimeMXBean().getUptime() / 1000)
            .sessionCount(sessionCount)
            .tokenUsage(tokenUsageToday)
            .build();
    }

    private static double readCpuPercent() {
        try {
            OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
            double load = os.getCpuLoad();
            if (load >= 0) {
                return load * 100.0;
            }
            double avg = os.getSystemLoadAverage();
            int processors = os.getAvailableProcessors();
            if (avg >= 0 && processors > 0) {
                return Math.min(100.0, (avg / processors) * 100.0);
            }
        } catch (Exception e) {
            log.debug("CPU metrics unavailable: {}", e.getMessage());
        }
        return 0;
    }

    private static double readMemoryPercent() {
        try {
            OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
            long total = os.getTotalMemorySize();
            long free = os.getFreeMemorySize();
            if (total > 0) {
                return (total - free) * 100.0 / total;
            }
        } catch (Exception e) {
            log.debug("Memory metrics unavailable: {}", e.getMessage());
        }
        return 0;
    }

    private static double readDiskPercent() {
        try {
            Path home = Path.of(System.getProperty("user.home", "."));
            FileStore store = Files.getFileStore(home);
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            if (total > 0) {
                return (total - usable) * 100.0 / total;
            }
        } catch (IOException e) {
            log.debug("Disk metrics unavailable: {}", e.getMessage());
        }
        return 0;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
