package com.openclaw.vs.service;

import com.openclaw.vs.config.SkillMarketProperties;
import com.openclaw.vs.dto.SkillMarketItemDto;
import com.openclaw.vs.dto.SkillMarketPageDto;
import com.openclaw.vs.model.InstalledSkill;
import com.openclaw.vs.repository.InstalledSkillRepository;
import com.openclaw.vs.service.market.ClawHubMarketClient;
import com.openclaw.vs.service.market.GitHubSkillMarketClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkillMarketService {

    private final ClawHubMarketClient clawHubMarketClient;
    private final GitHubSkillMarketClient gitHubSkillMarketClient;
    private final InstalledSkillRepository skillRepository;
    private final SkillMarketProperties properties;
    private final SkillLocalizationService localizationService;
    private final OpenClawInstalledSkillScanner installedSkillScanner;
    private final OpenClawSkillSyncService skillSyncService;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public SkillMarketPageDto browse(String source, String sort, int limit, String cursor, int page) {
        return browse(source, sort, limit, cursor, page, properties.getDefaultLocale(), false);
    }

    public SkillMarketPageDto browse(
        String source, String sort, int limit, String cursor, int page, String locale, boolean useLlm
    ) {
        String cacheKey = "browse:" + locale + ":" + useLlm + ":" + source + ":" + sort + ":" + limit + ":" + cursor + ":" + page;
        SkillMarketPageDto cached = getCached(cacheKey);
        if (cached != null) {
            return finalizePage(cached);
        }

        List<SkillMarketItemDto> items = new ArrayList<>();
        String nextCursor = null;
        String normalizedSource = normalizeSource(source);

        if ("all".equals(normalizedSource) || "clawdhub".equals(normalizedSource)) {
            ClawHubMarketClient.BrowseResult claw = clawHubMarketClient.browse(sort, limit, cursor);
            items.addAll(claw.items());
            nextCursor = claw.nextCursor();
        }
        if ("all".equals(normalizedSource) || "github".equals(normalizedSource)) {
            int ghPage = page > 0 ? page : parseCursorPage(cursor);
            items.addAll(gitHubSkillMarketClient.browse(ghPage, Math.min(limit, 15)));
        }

        if ("all".equals(normalizedSource)) {
            items = mergeAndSort(items, sort);
            if (items.size() > limit) {
                items = items.subList(0, limit);
            }
        }

        applyLocalization(items, locale, useLlm);
        SkillMarketPageDto result = SkillMarketPageDto.builder()
            .items(items)
            .nextCursor(nextCursor)
            .totalCount(items.size())
            .fromCache(false)
            .source(normalizedSource)
            .sort(sort != null ? sort : "downloads")
            .locale(locale)
            .localized(SkillLocalizationService.isChineseLocale(locale))
            .build();
        putCache(cacheKey, result);
        return finalizePage(result);
    }

    public SkillMarketPageDto trending(int limit) {
        return trending(limit, properties.getDefaultLocale(), false);
    }

    public SkillMarketPageDto trending(int limit, String locale, boolean useLlm) {
        return browse("all", "downloads", limit, null, 1, locale, useLlm);
    }

    public SkillMarketPageDto search(String query, String source, int limit, int page) {
        return search(query, source, limit, page, properties.getDefaultLocale(), false);
    }

    public SkillMarketPageDto search(String query, String source, int limit, int page, String locale, boolean useLlm) {
        if (query == null || query.isBlank()) {
            return browse(source, "downloads", limit, null, page, locale, useLlm);
        }
        String cacheKey = "search:" + locale + ":" + useLlm + ":" + source + ":" + query + ":" + limit + ":" + page;
        SkillMarketPageDto cached = getCached(cacheKey);
        if (cached != null) {
            return finalizePage(cached);
        }

        List<SkillMarketItemDto> items = new ArrayList<>();
        String normalizedSource = normalizeSource(source);

        if ("all".equals(normalizedSource) || "clawdhub".equals(normalizedSource)) {
            items.addAll(clawHubMarketClient.search(query, limit));
        }
        if ("all".equals(normalizedSource) || "github".equals(normalizedSource)) {
            String ghQuery = query + " openclaw skill";
            items.addAll(gitHubSkillMarketClient.search(ghQuery, page, Math.min(limit, 15)));
        }

        items = mergeAndSort(items, "downloads");
        if (items.size() > limit) {
            items = items.subList(0, limit);
        }
        applyLocalization(items, locale, useLlm);

        SkillMarketPageDto result = SkillMarketPageDto.builder()
            .items(items)
            .nextCursor(null)
            .totalCount(items.size())
            .fromCache(false)
            .source(normalizedSource)
            .sort("search")
            .locale(locale)
            .localized(SkillLocalizationService.isChineseLocale(locale))
            .build();
        putCache(cacheKey, result);
        return finalizePage(result);
    }

    private SkillMarketPageDto finalizePage(SkillMarketPageDto page) {
        skillSyncService.syncFromDisk();
        if (page.getItems() != null) {
            enrichInstallStatus(page.getItems());
        }
        return page;
    }

    private void applyLocalization(List<SkillMarketItemDto> items, String locale, boolean useLlm) {
        boolean llm = useLlm || properties.isTranslateDescriptions() || properties.isTranslateNames();
        localizationService.localizeItems(items, locale, llm);
    }

    private List<SkillMarketItemDto> enrichInstallStatus(List<SkillMarketItemDto> items) {
        Map<String, InstalledSkill> bySlug = skillRepository.findByStatus("installed").stream()
            .filter(s -> s.getMarketSlug() != null && !s.getMarketSlug().isBlank())
            .collect(Collectors.toMap(
                s -> s.getMarketSlug().toLowerCase(Locale.ROOT),
                s -> s,
                (a, b) -> a
            ));
        Map<String, OpenClawInstalledSkillScanner.DiskSkill> onDisk = installedSkillScanner.scanBySlug();

        for (SkillMarketItemDto item : items) {
            if (item.getSlug() == null) {
                continue;
            }
            String slugKey = item.getSlug().toLowerCase(Locale.ROOT);
            InstalledSkill local = bySlug.get(slugKey);
            OpenClawInstalledSkillScanner.DiskSkill disk = onDisk.get(slugKey);

            if (local == null && disk == null) {
                item.setStatus("not_installed");
                item.setInstalledId(null);
                item.setInstallPath(null);
                continue;
            }

            if (local != null) {
                item.setInstalledId(local.getId());
                item.setInstallPath(firstNonBlank(local.getInstallPath(), disk != null ? disk.getInstallPath() : null));
                if ("installed".equals(local.getStatus())) {
                    boolean sameVersion = item.getVersion() != null
                        && item.getVersion().equals(local.getVersion());
                    item.setStatus(sameVersion ? "installed" : "update_available");
                } else {
                    item.setStatus(disk != null ? "installed" : "not_installed");
                }
                continue;
            }

            item.setStatus("installed");
            item.setInstallPath(disk.getInstallPath());
        }
        return items;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static List<SkillMarketItemDto> mergeAndSort(List<SkillMarketItemDto> items, String sort) {
        Map<String, SkillMarketItemDto> deduped = new java.util.LinkedHashMap<>();
        for (SkillMarketItemDto item : items) {
            String key = item.getSource() + ":" + item.getSlug();
            deduped.merge(key, item, (a, b) -> a.getDownloads() >= b.getDownloads() ? a : b);
        }
        List<SkillMarketItemDto> list = new ArrayList<>(deduped.values());
        Comparator<SkillMarketItemDto> cmp = switch (sort != null ? sort.toLowerCase(Locale.ROOT) : "downloads") {
            case "rating", "stars" -> Comparator.comparingInt(SkillMarketItemDto::getStars).reversed();
            case "name" -> Comparator.comparing(SkillMarketItemDto::getName, String.CASE_INSENSITIVE_ORDER);
            case "updated" -> Comparator.comparing(
                SkillMarketItemDto::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
            );
            default -> Comparator.comparingInt(SkillMarketItemDto::getDownloads).reversed();
        };
        list.sort(cmp);
        return list;
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank() || "all".equalsIgnoreCase(source)) {
            return "all";
        }
        if ("clawdhub".equalsIgnoreCase(source) || "clawhub".equalsIgnoreCase(source)) {
            return "clawdhub";
        }
        if ("github".equalsIgnoreCase(source)) {
            return "github";
        }
        return "all";
    }

    private static int parseCursorPage(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(cursor);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private SkillMarketPageDto getCached(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            cache.remove(key);
            return null;
        }
        SkillMarketPageDto dto = entry.value();
        dto.setFromCache(true);
        return dto;
    }

    private void putCache(String key, SkillMarketPageDto value) {
        cache.put(key, new CacheEntry(
            value,
            Instant.now().plusSeconds(Math.max(60, properties.getCacheSeconds()))
        ));
    }

    private record CacheEntry(SkillMarketPageDto value, Instant expiresAt) {}
}
