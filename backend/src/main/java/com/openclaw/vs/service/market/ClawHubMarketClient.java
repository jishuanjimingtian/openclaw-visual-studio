package com.openclaw.vs.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.vs.config.SkillMarketProperties;
import com.openclaw.vs.dto.SkillMarketItemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClawHubMarketClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final SkillMarketProperties properties;

    public record BrowseResult(List<SkillMarketItemDto> items, String nextCursor) {}

    public BrowseResult browse(String sort, int limit, String cursor) {
        if (!properties.isClawhubEnabled()) {
            return new BrowseResult(List.of(), null);
        }
        String apiSort = mapSort(sort);
        String base = properties.getClawhubBaseUrl().replaceAll("/$", "");
        StringBuilder url = new StringBuilder(base)
            .append("/api/v1/skills?limit=")
            .append(Math.min(Math.max(limit, 1), 50))
            .append("&sort=")
            .append(apiSort)
            .append("&nonSuspiciousOnly=true");
        if (cursor != null && !cursor.isBlank() && !"trending".equals(apiSort)) {
            url.append("&cursor=").append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
        }
        return fetchList(url.toString(), "ClawdHub");
    }

    public List<SkillMarketItemDto> search(String query, int limit) {
        if (!properties.isClawhubEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        String base = properties.getClawhubBaseUrl().replaceAll("/$", "");
        String url = base + "/api/v1/search?q="
            + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
            + "&limit=" + Math.min(Math.max(limit, 1), 50);
        try {
            JsonNode root = getJson(url);
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                return List.of();
            }
            List<SkillMarketItemDto> items = new ArrayList<>();
            for (JsonNode node : results) {
                items.add(mapSearchResult(node));
            }
            return items;
        } catch (Exception e) {
            log.warn("ClawHub search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private BrowseResult fetchList(String url, String sourceLabel) {
        try {
            JsonNode root = getJson(url);
            JsonNode itemsNode = root.path("items");
            if (!itemsNode.isArray() || itemsNode.isEmpty()) {
                if (url.contains("sort=trending")) {
                    String fallback = url.replace("sort=trending", "sort=downloads");
                    return fetchList(fallback, sourceLabel);
                }
                return new BrowseResult(List.of(), null);
            }
            List<SkillMarketItemDto> items = new ArrayList<>();
            for (JsonNode node : itemsNode) {
                items.add(mapListItem(node, sourceLabel));
            }
            String next = root.path("nextCursor").asText(null);
            return new BrowseResult(items, next);
        } catch (Exception e) {
            log.warn("ClawHub browse failed ({}): {}", url, e.getMessage());
            return new BrowseResult(List.of(), null);
        }
    }

    private SkillMarketItemDto mapListItem(JsonNode node, String source) {
        String slug = node.path("slug").asText("");
        JsonNode stats = node.path("stats");
        int stars = stats.path("stars").asInt(0);
        int downloads = stats.path("downloads").asInt(0);
        String version = node.path("latestVersion").path("version").asText(
            node.path("tags").path("latest").asText("1.0.0")
        );
        String license = node.path("latestVersion").path("license").asText("MIT");
        if (license == null || license.isBlank() || "null".equals(license)) {
            license = "MIT";
        }
        long updatedMs = node.path("updatedAt").asLong(0);
        return SkillMarketItemDto.builder()
            .slug(slug)
            .name(node.path("displayName").asText(slug))
            .version(version)
            .author("")
            .description(node.path("summary").asText(""))
            .source(source)
            .status("not_installed")
            .rating(starsToRating(stars))
            .downloads(downloads)
            .stars(stars)
            .license(license)
            .homepageUrl("https://clawhub.ai/skills/" + slug)
            .updatedAt(updatedMs > 0 ? Instant.ofEpochMilli(updatedMs).toString() : null)
            .build();
    }

    private SkillMarketItemDto mapSearchResult(JsonNode node) {
        String slug = node.path("slug").asText("");
        String owner = node.path("ownerHandle").asText(
            node.path("owner").path("handle").asText("")
        );
        long updatedMs = node.path("updatedAt").asLong(0);
        return SkillMarketItemDto.builder()
            .slug(slug)
            .name(node.path("displayName").asText(slug))
            .version(node.path("version").asText("1.0.0"))
            .author(owner)
            .description(node.path("summary").asText(""))
            .source("ClawdHub")
            .status("not_installed")
            .rating(4.0)
            .downloads(0)
            .stars(0)
            .license("MIT")
            .homepageUrl("https://clawhub.ai/skills/" + slug)
            .updatedAt(updatedMs > 0 ? Instant.ofEpochMilli(updatedMs).toString() : null)
            .build();
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("User-Agent", "OpenClaw-Visual-Studio/0.1")
            .GET()
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return MAPPER.readTree(response.body());
    }

    static String mapSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "downloads";
        }
        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "trending", "hot" -> "trending";
            case "rating", "stars" -> "stars";
            case "newest", "created" -> "createdAt";
            case "installs" -> "installsCurrent";
            case "updated" -> "updated";
            default -> "downloads";
        };
    }

    static double starsToRating(int stars) {
        if (stars <= 0) {
            return 3.0;
        }
        return Math.min(5.0, 2.0 + Math.log10(stars + 1) * 1.2);
    }
}
