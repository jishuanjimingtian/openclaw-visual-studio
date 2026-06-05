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

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubSkillMarketClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .build();

    private final SkillMarketProperties properties;

    public List<SkillMarketItemDto> browse(int page, int perPage) {
        return search(properties.getGithubSearchQuery(), page, perPage);
    }

    public List<SkillMarketItemDto> search(String query, int page, int perPage) {
        if (!properties.isGithubEnabled()) {
            return List.of();
        }
        String q = query != null && !query.isBlank() ? query.trim() : properties.getGithubSearchQuery();
        int pageNum = Math.max(page, 1);
        int size = Math.min(Math.max(perPage, 1), 30);
        String url = "https://api.github.com/search/repositories?q="
            + URLEncoder.encode(q, StandardCharsets.UTF_8)
            + "&sort=stars&order=desc&per_page=" + size
            + "&page=" + pageNum;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OpenClaw-Visual-Studio/0.1")
                .GET()
                .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("GitHub search HTTP {}", response.statusCode());
                return List.of();
            }
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<SkillMarketItemDto> out = new ArrayList<>();
            for (JsonNode repo : items) {
                out.add(mapRepo(repo));
            }
            return out;
        } catch (Exception e) {
            log.warn("GitHub skill search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private SkillMarketItemDto mapRepo(JsonNode repo) {
        String fullName = repo.path("full_name").asText("");
        String slug = fullName.replace("/", "--");
        int stars = repo.path("stargazers_count").asInt(0);
        String description = repo.path("description").asText("");
        String license = repo.path("license").path("spdx_id").asText("MIT");
        if (license == null || license.isBlank() || "NOASSERTION".equals(license)) {
            license = "MIT";
        }
        String updated = repo.path("updated_at").asText(null);
        return SkillMarketItemDto.builder()
            .slug(slug)
            .githubRepo(fullName)
            .name(repo.path("name").asText(fullName))
            .version("main")
            .author(repo.path("owner").path("login").asText(""))
            .description(description != null ? description : "")
            .source("GitHub")
            .status("not_installed")
            .rating(ClawHubMarketClient.starsToRating(stars))
            .downloads(stars)
            .stars(stars)
            .license(license)
            .homepageUrl(repo.path("html_url").asText(""))
            .updatedAt(updated)
            .tags(parseTopics(repo.path("topics")))
            .build();
    }

    private static List<String> parseTopics(JsonNode topics) {
        if (!topics.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode t : topics) {
            String v = t.asText(null);
            if (v != null && !v.isBlank()) {
                tags.add(v);
            }
        }
        return tags;
    }
}
