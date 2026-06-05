package com.openclaw.vs.service;

import com.openclaw.vs.dto.AnalyticsOverviewDto;
import com.openclaw.vs.dto.AnalyticsSourceBreakdownDto;
import com.openclaw.vs.dto.AnalyticsSourceBreakdownDto.SourceBreakdownRowDto;
import com.openclaw.vs.dto.DailyTokenUsageDto;
import com.openclaw.vs.dto.ModelUsageDto;
import com.openclaw.vs.dto.MessageTrendDto;
import com.openclaw.vs.dto.OpenClawSessionDto;
import com.openclaw.vs.dto.OpenClawSessionsResult;
import com.openclaw.vs.dto.TopSessionUsageDto;
import com.openclaw.vs.repository.MessageRepository;
import com.openclaw.vs.repository.ConversationRepository;
import com.openclaw.vs.repository.ModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final DateTimeFormatter ISO_DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final OpenClawUsageService openClawUsageService;
    private final OpenClawSessionService openClawSessionService;

    public AnalyticsOverviewDto getOverview() {
        return getOverview(false);
    }

    public AnalyticsOverviewDto getOverview(boolean includeGatewayTokens) {
        long totalMessages = messageRepository.count();
        long totalConversations = conversationRepository.count();
        long localTokens = messageRepository.sumTokensSince(LocalDateTime.of(2000, 1, 1, 0, 0));
        long gatewayTokens = includeGatewayTokens ? openClawUsageService.getAllTimeGatewayTokens() : 0L;
        long totalTokens = localTokens + gatewayTokens;
        long todayMessages = messageRepository.countByCreatedAtAfter(LocalDate.now().atStartOfDay());
        long todayLocalTokens = messageRepository.sumTokensSince(LocalDate.now().atStartOfDay());
        long todayGatewayTokens = includeGatewayTokens ? openClawUsageService.getTodayTokens() : 0L;
        long todayTokens = todayLocalTokens + todayGatewayTokens;

        if (includeGatewayTokens && openClawUsageService.isGatewayConnected()) {
            totalMessages += openClawUsageService.getAllTimeGatewayMessages();
            totalConversations += openClawUsageService.getGatewaySessionCount();
            todayMessages += openClawUsageService.getTodayGatewayMessages();
        }

        return AnalyticsOverviewDto.builder()
            .totalMessages(totalMessages)
            .totalConversations(totalConversations)
            .totalTokens(totalTokens)
            .todayMessages(todayMessages)
            .todayTokens(todayTokens)
            .activeModels(modelConfigRepository.findByEnabledTrue().size())
            .build();
    }

    public AnalyticsOverviewDto getGatewayTokenSummary() {
        long todayLocalTokens = messageRepository.sumTokensSince(LocalDate.now().atStartOfDay());
        long localTokens = messageRepository.sumTokensSince(LocalDateTime.of(2000, 1, 1, 0, 0));
        long localMessages = messageRepository.count();
        long localConversations = conversationRepository.count();
        long todayLocalMessages = messageRepository.countByCreatedAtAfter(LocalDate.now().atStartOfDay());

        long gatewayTokens = openClawUsageService.getAllTimeGatewayTokens();
        long todayGatewayTokens = openClawUsageService.getTodayTokens();
        long gatewayMessages = 0L;
        long todayGatewayMessages = 0L;
        long gatewaySessions = 0L;

        if (openClawUsageService.isGatewayConnected()) {
            gatewayMessages = openClawUsageService.getAllTimeGatewayMessages();
            todayGatewayMessages = openClawUsageService.getTodayGatewayMessages();
            gatewaySessions = openClawUsageService.getGatewaySessionCount();

            if (gatewayMessages == 0L) {
                gatewayMessages = openClawUsageService.getDailyMessageMap(90).values().stream()
                    .mapToLong(Long::longValue).sum();
            }
            if (todayGatewayMessages == 0L) {
                String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                todayGatewayMessages = openClawUsageService.getDailyMessageMap(14).getOrDefault(today, 0L);
            }
            if (gatewaySessions == 0L) {
                OpenClawSessionsResult sessions = openClawSessionService.listSessions(500, null, false);
                gatewaySessions = sessions.getSessions().size();
            }
        }

        return AnalyticsOverviewDto.builder()
            .totalTokens(localTokens + gatewayTokens)
            .todayTokens(todayLocalTokens + todayGatewayTokens)
            .totalMessages(localMessages + gatewayMessages)
            .todayMessages(todayLocalMessages + todayGatewayMessages)
            .totalConversations(localConversations + gatewaySessions)
            .activeModels(modelConfigRepository.findByEnabledTrue().size())
            .build();
    }

    public List<DailyTokenUsageDto> getDailyTokenUsage(int days) {
        return getDailyTokenUsage(days, true);
    }

    public List<DailyTokenUsageDto> getDailyTokenUsage(int days, boolean includeGateway) {
        int effectiveDays = Math.min(Math.max(days, 1), 90);
        Map<String, Long> gatewayDaily = includeGateway
            ? openClawUsageService.getDailyTokenMap(effectiveDays)
            : Collections.emptyMap();
        Map<String, Long> gatewayDailyMessages = includeGateway
            ? openClawUsageService.getDailyMessageMap(effectiveDays)
            : Collections.emptyMap();
        List<DailyTokenUsageDto> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = effectiveDays - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            long localTokens = messageRepository.sumTokensBetween(start, end);
            long gatewayTokens = gatewayDaily.getOrDefault(dateStr, 0L);
            long msgCount = messageRepository.countByCreatedAtBetween(start, end)
                + gatewayDailyMessages.getOrDefault(dateStr, 0L);
            result.add(DailyTokenUsageDto.builder()
                .date(dateStr)
                .tokens(localTokens + gatewayTokens)
                .localTokens(localTokens)
                .gatewayTokens(gatewayTokens)
                .messageCount(msgCount)
                .build());
        }
        return result;
    }

    public List<ModelUsageDto> getModelUsage() {
        Map<String, ModelAgg> aggregates = new LinkedHashMap<>();

        for (Object[] row : messageRepository.aggregateUsageByModel()) {
            String model = (String) row[0];
            Long sessions = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            Long tokens = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            Long messages = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            if (model != null && !model.isBlank()) {
                aggregates.computeIfAbsent(model, ModelAgg::new).mergeLocal(sessions, tokens, messages);
            }
        }

        for (Object[] row : conversationRepository.countByModel()) {
            String model = (String) row[0];
            Long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            if (model != null && !model.isBlank() && count != null && count > 0) {
                ModelAgg agg = aggregates.computeIfAbsent(model, ModelAgg::new);
                if (agg.sessionCount == 0) {
                    agg.sessionCount = count;
                }
            }
        }

        if (openClawUsageService.isGatewayConnected()) {
            Map<String, long[]> gatewayByModel = openClawUsageService.aggregateGatewayUsageByModel();
            for (Map.Entry<String, long[]> entry : gatewayByModel.entrySet()) {
                long[] g = entry.getValue();
                aggregates.computeIfAbsent(entry.getKey(), ModelAgg::new)
                    .mergeGateway(g[0], g[1], g[2]);
            }
            if (gatewayByModel.isEmpty()) {
                OpenClawSessionsResult gatewaySessions = openClawSessionService.listSessions(80, null, false);
                String defaultModel = gatewaySessions.getDefaultModel();
                for (OpenClawSessionDto session : gatewaySessions.getSessions()) {
                    String model = session.getModel();
                    if (model == null || model.isBlank()) {
                        model = defaultModel;
                    }
                    if (model != null && !model.isBlank()) {
                        long tokens = session.getTotalTokens() != null ? session.getTotalTokens().longValue() : 0L;
                        aggregates.computeIfAbsent(model, ModelAgg::new).mergeGateway(1, tokens, 0);
                    }
                }
            }
        }

        long totalSessions = aggregates.values().stream().mapToLong(a -> a.sessionCount).sum();
        long totalTokens = aggregates.values().stream().mapToLong(a -> a.totalTokens).sum();
        if (totalSessions == 0 && totalTokens == 0) {
            return List.of();
        }

        return aggregates.entrySet().stream()
            .map(entry -> {
                ModelAgg agg = entry.getValue();
                double sessionPct = totalSessions > 0
                    ? Math.round(agg.sessionCount * 1000.0 / totalSessions) / 10.0 : 0.0;
                double tokenPct = totalTokens > 0
                    ? Math.round(agg.totalTokens * 1000.0 / totalTokens) / 10.0 : 0.0;
                return ModelUsageDto.builder()
                    .model(entry.getKey())
                    .sessionCount(agg.sessionCount)
                    .percentage(sessionPct)
                    .totalTokens(agg.totalTokens)
                    .messageCount(agg.messageCount)
                    .tokenPercentage(tokenPct)
                    .build();
            })
            .sorted((a, b) -> Long.compare(b.getTotalTokens(), a.getTotalTokens()))
            .toList();
    }

    public List<TopSessionUsageDto> getTopSessions(int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);
        List<TopSessionUsageDto> merged = new ArrayList<>();

        for (Object[] row : messageRepository.topConversationsByTokens(PageRequest.of(0, effectiveLimit))) {
            String id = (String) row[0];
            String title = (String) row[1];
            String model = (String) row[2];
            long tokens = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            long messages = row[4] != null ? ((Number) row[4]).longValue() : 0L;
            LocalDateTime updatedAt = row[5] instanceof LocalDateTime dt ? dt : null;
            merged.add(TopSessionUsageDto.builder()
                .id(id)
                .title(title != null ? title : "未命名会话")
                .model(model != null ? model : "—")
                .totalTokens(tokens)
                .messageCount(messages)
                .updatedAt(updatedAt != null ? updatedAt.format(ISO_DT) : null)
                .source("local")
                .inputTokens(0L)
                .outputTokens(0L)
                .totalCost(null)
                .build());
        }

        if (openClawUsageService.isGatewayConnected()) {
            merged.addAll(openClawUsageService.getGatewayTopSessions(effectiveLimit));
        }

        return merged.stream()
            .sorted((a, b) -> Long.compare(b.getTotalTokens(), a.getTotalTokens()))
            .limit(effectiveLimit)
            .toList();
    }

    public AnalyticsSourceBreakdownDto getSourceBreakdown() {
        long localConversations = conversationRepository.count();
        long localMessages = messageRepository.count();
        long localTokens = messageRepository.sumTokensSince(LocalDateTime.of(2000, 1, 1, 0, 0));
        long todayLocalMessages = messageRepository.countByCreatedAtAfter(LocalDate.now().atStartOfDay());
        long todayLocalTokens = messageRepository.sumTokensSince(LocalDate.now().atStartOfDay());

        boolean connected = openClawUsageService.isGatewayConnected();
        long gatewayConversations = 0L;
        long gatewayMessages = 0L;
        long gatewayTokens = 0L;
        long todayGatewayMessages = 0L;
        long todayGatewayTokens = 0L;

        if (connected) {
            gatewayTokens = openClawUsageService.getAllTimeGatewayTokens();
            todayGatewayTokens = openClawUsageService.getTodayTokens();
            gatewayMessages = openClawUsageService.getAllTimeGatewayMessages();
            todayGatewayMessages = openClawUsageService.getTodayGatewayMessages();
            gatewayConversations = openClawUsageService.getGatewaySessionCount();
            if (gatewayMessages == 0L) {
                gatewayMessages = openClawUsageService.getDailyMessageMap(90).values().stream()
                    .mapToLong(Long::longValue).sum();
            }
            if (todayGatewayMessages == 0L) {
                String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                todayGatewayMessages = openClawUsageService.getDailyMessageMap(14).getOrDefault(today, 0L);
            }
            if (gatewayConversations == 0L) {
                OpenClawSessionsResult sessions = openClawSessionService.listSessions(500, null, false);
                gatewayConversations = sessions.getSessions().size();
            }
        }

        List<SourceBreakdownRowDto> rows = List.of(
            row("conversations", "会话数", localConversations, gatewayConversations),
            row("messages", "消息数", localMessages, gatewayMessages),
            row("tokens", "总 Token", localTokens, gatewayTokens),
            row("todayMessages", "今日消息", todayLocalMessages, todayGatewayMessages),
            row("todayTokens", "今日 Token", todayLocalTokens, todayGatewayTokens)
        );

        return AnalyticsSourceBreakdownDto.builder()
            .gatewayConnected(connected)
            .rows(rows)
            .build();
    }

    private static SourceBreakdownRowDto row(String metric, String label, long local, long gateway) {
        return SourceBreakdownRowDto.builder()
            .metric(metric)
            .label(label)
            .local(local)
            .gateway(gateway)
            .total(local + gateway)
            .build();
    }

    public List<MessageTrendDto> getMessageTrend() {
        return getDailyTokenUsage(7, true).stream()
            .map(d -> MessageTrendDto.builder()
                .date(d.getDate())
                .messageCount(d.getMessageCount())
                .build())
            .toList();
    }

    private static final class ModelAgg {
        long sessionCount;
        long totalTokens;
        long messageCount;

        ModelAgg(String model) {
            // model key only
        }

        void mergeLocal(long sessions, long tokens, long messages) {
            this.sessionCount += sessions;
            this.totalTokens += tokens;
            this.messageCount += messages;
        }

        void mergeGateway(long sessions, long tokens, long messages) {
            this.sessionCount += sessions;
            this.totalTokens += tokens;
            this.messageCount += messages;
        }
    }
}
