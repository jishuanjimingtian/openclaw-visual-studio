package com.openclaw.vs.service;

import com.openclaw.vs.dto.SkillInstallRequest;
import com.openclaw.vs.dto.SkillInstallResultDto;
import com.openclaw.vs.exception.BadRequestException;
import com.openclaw.vs.exception.NotFoundException;
import com.openclaw.vs.model.InstalledSkill;
import com.openclaw.vs.repository.InstalledSkillRepository;
import com.openclaw.vs.config.SkillMarketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SkillService {

    private final InstalledSkillRepository skillRepository;
    private final OpenClawSkillInstaller openClawSkillInstaller;
    private final SkillLocalizationService skillLocalizationService;
    private final SkillMarketProperties skillMarketProperties;
    private final OpenClawSkillSyncService skillSyncService;

    public Page<InstalledSkill> listSkills(Pageable pageable) {
        skillSyncService.syncFromDisk();
        return skillRepository.findByStatus("installed", pageable);
    }

    public InstalledSkill getSkill(String id) {
        return skillRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Skill", id));
    }

    /**
     * 从市场安装：下载到 OpenClaw 工作区/全局 skills 目录，并登记到本地库。
     */
    public SkillInstallResultDto installFromMarket(SkillInstallRequest request) {
        SkillInstallResultDto diskResult = openClawSkillInstaller.install(request);
        if (!diskResult.isSuccess()) {
            throw new BadRequestException(diskResult.getMessage());
        }

        boolean localize = request.getLocalizeToChinese() != null
            ? request.getLocalizeToChinese()
            : skillMarketProperties.isLocalizeOnInstall();
        if (localize) {
            boolean anyLocalized = false;
            if (diskResult.getInstallPaths() != null) {
                for (String p : diskResult.getInstallPaths()) {
                    if (skillLocalizationService.localizeInstalledSkill(Path.of(p))) {
                        anyLocalized = true;
                    }
                }
            } else if (diskResult.getInstallPath() != null) {
                anyLocalized = skillLocalizationService.localizeInstalledSkill(Path.of(diskResult.getInstallPath()));
            }
            diskResult.setSkillMdLocalized(anyLocalized);
            if (anyLocalized) {
                diskResult.setMessage(diskResult.getMessage() + "；SKILL.md 已译为中文");
            }
        }

        InstalledSkill record = buildRecordFromRequest(request);
        record.setInstallPath(diskResult.getInstallPath());
        record.setStatus("installed");
        record.setInstalledAt(LocalDateTime.now());

        if (record.getMarketSlug() != null && !record.getMarketSlug().isBlank()) {
            Optional<InstalledSkill> existing = skillRepository.findByMarketSlug(record.getMarketSlug().trim());
            if (existing.isPresent()) {
                InstalledSkill e = existing.get();
                applyInstallFields(e, record);
                e.setInstallPath(diskResult.getInstallPath());
                e.setStatus("installed");
                e.setInstalledAt(LocalDateTime.now());
                InstalledSkill saved = skillRepository.save(e);
                diskResult.setSkillId(saved.getId());
                return diskResult;
            }
        }

        record.setId(UUID.randomUUID().toString());
        InstalledSkill saved = skillRepository.save(record);
        diskResult.setSkillId(saved.getId());
        return diskResult;
    }

    public InstalledSkill installSkill(InstalledSkill skill) {
        if (skill.getMarketSlug() != null && !skill.getMarketSlug().isBlank()) {
            SkillInstallRequest req = new SkillInstallRequest();
            req.setSlug(skill.getMarketSlug());
            req.setSource(skill.getSource());
            req.setVersion(skill.getVersion());
            req.setScope("workspace");
            req.setName(skill.getName());
            req.setAuthor(skill.getAuthor());
            req.setDescription(skill.getDescription());
            req.setLicense(skill.getLicense());
            req.setRating(skill.getRating());
            req.setDownloads(skill.getDownloads());
            SkillInstallResultDto result = installFromMarket(req);
            return getSkill(result.getSkillId());
        }
        skill.setId(UUID.randomUUID().toString());
        skill.setStatus("installed");
        skill.setInstalledAt(LocalDateTime.now());
        return skillRepository.save(skill);
    }

    private static InstalledSkill buildRecordFromRequest(SkillInstallRequest request) {
        InstalledSkill skill = new InstalledSkill();
        skill.setName(request.getName() != null ? request.getName() : request.getSlug());
        skill.setVersion(request.getVersion() != null && !request.getVersion().isBlank()
            ? request.getVersion().trim() : "1.0.0");
        skill.setAuthor(request.getAuthor() != null ? request.getAuthor() : "");
        skill.setDescription(request.getDescription() != null ? request.getDescription() : "");
        skill.setSource(request.getSource() != null ? request.getSource() : "ClawdHub");
        skill.setLicense(request.getLicense() != null ? request.getLicense() : "MIT");
        skill.setRating(request.getRating());
        skill.setDownloads(request.getDownloads());
        skill.setMarketSlug(request.getSlug().trim());
        return skill;
    }

    private static void applyInstallFields(InstalledSkill target, InstalledSkill from) {
        if (from.getName() != null) target.setName(from.getName());
        if (from.getVersion() != null) target.setVersion(from.getVersion());
        if (from.getAuthor() != null) target.setAuthor(from.getAuthor());
        if (from.getDescription() != null) target.setDescription(from.getDescription());
        if (from.getSource() != null) target.setSource(from.getSource());
        if (from.getLicense() != null) target.setLicense(from.getLicense());
        if (from.getRating() > 0) target.setRating(from.getRating());
        if (from.getDownloads() > 0) target.setDownloads(from.getDownloads());
        if (from.getInstallPath() != null) target.setInstallPath(from.getInstallPath());
    }

    public InstalledSkill updateSkill(String id, InstalledSkill updated) {
        InstalledSkill existing = getSkill(id);
        if (updated.getName() != null) existing.setName(updated.getName());
        if (updated.getVersion() != null) existing.setVersion(updated.getVersion());
        if (updated.getAuthor() != null) existing.setAuthor(updated.getAuthor());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        if (updated.getRating() >= 0) existing.setRating(updated.getRating());
        if (updated.getDownloads() >= 0) existing.setDownloads(updated.getDownloads());
        if (updated.getLicense() != null) existing.setLicense(updated.getLicense());
        return skillRepository.save(existing);
    }

    public void uninstallSkill(String id) {
        InstalledSkill skill = getSkill(id);
        openClawSkillInstaller.uninstallFromDisk(skill.getMarketSlug(), skill.getInstallPath());
        skill.setStatus("not_installed");
        skill.setInstallPath(null);
        skillRepository.save(skill);
    }

    public void deleteSkill(String id) {
        InstalledSkill skill = skillRepository.findById(id).orElse(null);
        if (skill != null) {
            openClawSkillInstaller.uninstallFromDisk(skill.getMarketSlug(), skill.getInstallPath());
        }
        if (!skillRepository.existsById(id)) {
            throw new NotFoundException("Skill", id);
        }
        skillRepository.deleteById(id);
    }

    public Page<InstalledSkill> searchSkills(String keyword, Pageable pageable) {
        skillSyncService.syncFromDisk();
        if (keyword == null || keyword.isBlank()) {
            return skillRepository.findByStatus("installed", pageable);
        }
        return skillRepository.searchInstalled("installed", keyword.trim(), pageable);
    }

    public int syncFromOpenClaw() {
        return skillSyncService.syncFromDisk();
    }
}
