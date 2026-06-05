package com.openclaw.vs.service;

import com.openclaw.vs.dto.*;
import com.openclaw.vs.gateway.GatewayWebSocketClient;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import com.openclaw.vs.model.Conversation;
import com.openclaw.vs.model.DeploymentTask;
import com.openclaw.vs.repository.ConversationRepository;
import com.openclaw.vs.repository.DeploymentTaskRepository;
import com.openclaw.vs.repository.InstalledSkillRepository;
import com.openclaw.vs.repository.MessageRepository;
import com.openclaw.vs.repository.ModelConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ConversationRepository conversationRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final DeploymentTaskRepository deploymentTaskRepository;
    private final InstalledSkillRepository skillRepository;
    private final MessageRepository messageRepository;
    private final SystemMetricsService systemMetricsService;
    private final GatewayService gatewayService;
    private final GatewayWebSocketClient gatewayWebSocketClient;
    private final OpenClawModelConfigService openClawModelConfigService;
    private final OpenClawSessionService openClawSessionService;
    private final OpenClawUsageService openClawUsageService;

    public DashboardOverviewDto getOverview() {
        DashboardStatsDto stats = getStats();
        SystemMetricsDto metrics = getMetrics(true);
        DashboardGatewayDto gateway = buildGatewaySummary();
        OpenClawModelOverview openclaw = openClawModelConfigService.readOverview();

        return DashboardOverviewDto.builder()
            .stats(stats)
            .metrics(metrics)
            .gateway(gateway)
            .openclawConfigPath(openclaw.getConfigPath())
            .primaryModelRef(openclaw.getPrimaryModelRef())
            .recentConversations(getRecentConversations(8))
            .recentDeployments(getRecentDeployments(5))
            .build();
    }

    public DashboardRecentDto getRecent() {
        return DashboardRecentDto.builder()
            .recentConversations(getRecentConversations(8))
            .recentDeployments(getRecentDeployments(5))
            .build();
    }

    public DashboardOpenClawMetaDto getOpenClawMeta() {
        OpenClawModelOverview openclaw = openClawModelConfigService.readOverview();
        return DashboardOpenClawMetaDto.builder()
            .openclawConfigPath(openclaw.getConfigPath())
            .primaryModelRef(openclaw.getPrimaryModelRef())
            .build();
    }

    /** 今日 Token（含 Gateway），可能较慢，建议单独请求 */
    public long getTodayTokenUsage() {
        long local = messageRepository.sumTokensSince(LocalDate.now().atStartOfDay());
        long gateway = openClawUsageService.getTodayTokens();
        return local + gateway;
    }

    public DashboardStatsDto getStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long localConversations = conversationRepository.count();
        long totalMessages = messageRepository.count();
        long messagesToday = messageRepository.countByCreatedAtAfter(startOfDay);

        long totalConversations = localConversations + resolveGatewaySessionCount();
        if (gatewayWebSocketClient.isConnected()) {
            totalMessages += openClawUsageService.getAllTimeGatewayMessages();
            messagesToday += openClawUsageService.getTodayGatewayMessages();
        }

        return DashboardStatsDto.builder()
            .totalConversations(totalConversations)
            .activeModels(modelConfigRepository.findByEnabledTrue().size())
            .totalModels(modelConfigRepository.count())
            .activeDeployments(deploymentTaskRepository.findByStatusOrderByStartTimeDesc("running").size())
            .totalDeployments(deploymentTaskRepository.count())
            .installedSkills(skillRepository.findByStatus("installed").size())
            .totalSkills(skillRepository.count())
            .totalMessages(totalMessages)
            .messagesToday(messagesToday)
            .build();
    }

    public SystemMetricsDto getMetrics() {
        return getMetrics(false);
    }

    public SystemMetricsDto getMetrics(boolean includeGatewayTokens) {
        long sessions = conversationRepository.count() + resolveGatewaySessionCount();
        int sessionCount = sessions > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sessions;
        long tokensToday = messageRepository.sumTokensSince(LocalDate.now().atStartOfDay());
        if (includeGatewayTokens) {
            tokensToday += openClawUsageService.getTodayTokens();
        }
        return systemMetricsService.collect(sessionCount, tokensToday);
    }

    /** Gateway 侧 OpenClaw 会话数（与数据分析页一致） */
    private long resolveGatewaySessionCount() {
        if (!gatewayWebSocketClient.isConnected()) {
            return 0L;
        }
        long gateway = openClawUsageService.getGatewaySessionCount();
        if (gateway > 0) {
            return gateway;
        }
        OpenClawSessionsResult sessions = openClawSessionService.listSessions(500, null, false);
        return sessions.getSessions() != null ? sessions.getSessions().size() : 0L;
    }

    private DashboardGatewayDto buildGatewaySummary() {
        GatewayInfo info = gatewayService.getGatewayStatus();
        boolean wsConnected = gatewayWebSocketClient.isConnected();
        String status = info.getStatus();
        if (wsConnected && ("stopped".equals(status) || status == null)) {
            status = "running";
        }
        return DashboardGatewayDto.builder()
            .status(status)
            .port(info.getPort())
            .endpoint(info.getEndpoint())
            .pid(info.getPid())
            .wsConnected(wsConnected)
            .message(info.getMessage())
            .build();
    }

    private List<DashboardRecentConversationDto> getRecentConversations(int limit) {
        List<DashboardRecentConversationDto> merged = new ArrayList<>(
            conversationRepository.findByArchivedFalseOrderByUpdatedAtDesc().stream()
                .limit(limit)
                .map(this::toRecentConversation)
                .toList());

        if (gatewayWebSocketClient.isConnected()) {
            for (OpenClawSessionDto session : openClawSessionService.listSessions(limit, null, true).getSessions()) {
                DashboardRecentConversationDto gw = toRecentFromOpenClaw(session);
                boolean exists = merged.stream().anyMatch(c -> c.getId().equals(gw.getId()));
                if (!exists) {
                    merged.add(gw);
                }
            }
        }

        return merged.stream()
            .sorted(Comparator
                .comparing(this::hasRecentMessages).reversed()
                .thenComparing(
                    DashboardRecentConversationDto::getUpdatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .limit(limit)
            .toList();
    }

    private boolean hasRecentMessages(DashboardRecentConversationDto conversation) {
        if (conversation.getMessageCount() > 0) {
            return true;
        }
        String preview = conversation.getLastMessagePreview();
        return preview != null && !preview.isBlank();
    }

    private DashboardRecentConversationDto toRecentFromOpenClaw(OpenClawSessionDto session) {
        String id = session.getSessionId() != null && !session.getSessionId().isBlank()
            ? session.getSessionId()
            : session.getKey();
        LocalDateTime updatedAt = null;
        if (session.getUpdatedAt() != null && session.getUpdatedAt() > 0) {
            updatedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(session.getUpdatedAt()),
                ZoneId.systemDefault());
        }
        return DashboardRecentConversationDto.builder()
            .id(id != null ? id : session.getKey())
            .title(session.getTitle() != null ? session.getTitle() : "未命名会话")
            .model(session.getModel() != null ? session.getModel() : "—")
            .updatedAt(updatedAt)
            .messageCount(0)
            .lastMessagePreview(session.getLastMessagePreview())
            .build();
    }

    private List<DashboardRecentDeploymentDto> getRecentDeployments(int limit) {
        return deploymentTaskRepository.findAllByOrderByStartTimeDesc().stream()
            .limit(limit)
            .map(this::toRecentDeployment)
            .toList();
    }

    private DashboardRecentConversationDto toRecentConversation(Conversation c) {
        return DashboardRecentConversationDto.builder()
            .id(c.getId())
            .title(c.getTitle())
            .model(c.getModel())
            .updatedAt(c.getUpdatedAt())
            .messageCount(c.getMessageCount())
            .lastMessagePreview(c.getLastMessagePreview())
            .build();
    }

    private DashboardRecentDeploymentDto toRecentDeployment(DeploymentTask task) {
        String installMethod = "未知";
        Integer port = null;
        String configJson = task.getConfigJson();
        if (configJson != null && !configJson.isBlank()) {
            installMethod = extractJsonString(configJson, "installSource", installMethod);
            String portStr = extractJsonString(configJson, "gatewayPort", null);
            if (portStr != null) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
        }
        return DashboardRecentDeploymentDto.builder()
            .id(task.getId())
            .status(task.getStatus())
            .progress(task.getProgress())
            .startTime(task.getStartTime())
            .installMethod(installMethod)
            .port(port)
            .build();
    }

    private static String extractJsonString(String json, String key, String fallback) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start >= 0) {
            start += marker.length();
            int end = json.indexOf('"', start);
            if (end > start) {
                return json.substring(start, end);
            }
        }
        String numMarker = "\"" + key + "\":";
        int numStart = json.indexOf(numMarker);
        if (numStart >= 0) {
            numStart += numMarker.length();
            int end = json.indexOf(',', numStart);
            if (end < 0) {
                end = json.indexOf('}', numStart);
            }
            if (end > numStart) {
                return json.substring(numStart, end).trim();
            }
        }
        return fallback;
    }

    public boolean isOpenClawConfigPresent() {
        Path path = OpenClawGatewayConfigReader.defaultConfigPath();
        return Files.isRegularFile(path);
    }
}
