package com.openclaw.vs.service;

import com.openclaw.vs.model.InstalledSkill;
import com.openclaw.vs.repository.InstalledSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawSkillSyncService {

    private final OpenClawInstalledSkillScanner skillScanner;
    private final InstalledSkillRepository skillRepository;

    @Transactional
    public int syncFromDisk() {
        Map<String, OpenClawInstalledSkillScanner.DiskSkill> onDisk = skillScanner.scanBySlug();
        Set<String> seenSlugs = new HashSet<>();
        int changed = 0;

        for (OpenClawInstalledSkillScanner.DiskSkill disk : onDisk.values()) {
            String slugKey = disk.getSlug().toLowerCase(Locale.ROOT);
            seenSlugs.add(slugKey);
            InstalledSkill record = skillRepository.findByMarketSlug(disk.getSlug())
                .or(() -> skillRepository.findByMarketSlug(slugKey))
                .orElse(null);

            if (record == null) {
                skillRepository.save(buildFromDisk(disk));
                changed++;
                continue;
            }

            boolean updated = false;
            if (!"installed".equals(record.getStatus())) {
                record.setStatus("installed");
                updated = true;
            }
            if (disk.getInstallPath() != null && !disk.getInstallPath().equals(record.getInstallPath())) {
                record.setInstallPath(disk.getInstallPath());
                updated = true;
            }
            if (disk.getName() != null && !disk.getName().isBlank()
                && (record.getName() == null || record.getName().isBlank() || record.getName().equals(record.getMarketSlug()))) {
                record.setName(disk.getName());
                updated = true;
            }
            if (disk.getVersion() != null && !disk.getVersion().isBlank()) {
                record.setVersion(disk.getVersion());
                updated = true;
            }
            if (disk.getDescription() != null && !disk.getDescription().equals(record.getDescription())) {
                record.setDescription(disk.getDescription());
                updated = true;
            }
            if (record.getMarketSlug() == null || record.getMarketSlug().isBlank()) {
                record.setMarketSlug(disk.getSlug());
                updated = true;
            }
            if (updated) {
                skillRepository.save(record);
                changed++;
            }
        }

        for (InstalledSkill record : skillRepository.findByStatus("installed")) {
            String slug = record.getMarketSlug();
            if (slug == null || slug.isBlank()) {
                continue;
            }
            if (!seenSlugs.contains(slug.toLowerCase(Locale.ROOT))) {
                record.setStatus("not_installed");
                record.setInstallPath(null);
                skillRepository.save(record);
                changed++;
            }
        }

        if (changed > 0) {
            log.info("Synced OpenClaw skills from disk: {} change(s), {} on disk", changed, onDisk.size());
        }
        return changed;
    }

    private static InstalledSkill buildFromDisk(OpenClawInstalledSkillScanner.DiskSkill disk) {
        return InstalledSkill.builder()
            .id(UUID.randomUUID().toString())
            .name(disk.getName())
            .version(disk.getVersion() != null ? disk.getVersion() : "1.0.0")
            .author("")
            .description(disk.getDescription() != null ? disk.getDescription() : "")
            .source(guessSource(disk.getSlug()))
            .status("installed")
            .installedAt(LocalDateTime.now())
            .rating(0)
            .downloads(0)
            .license("MIT")
            .marketSlug(disk.getSlug())
            .installPath(disk.getInstallPath())
            .build();
    }

    private static String guessSource(String slug) {
        if (slug != null && slug.contains("--")) {
            return "GitHub";
        }
        return "ClawdHub";
    }
}
