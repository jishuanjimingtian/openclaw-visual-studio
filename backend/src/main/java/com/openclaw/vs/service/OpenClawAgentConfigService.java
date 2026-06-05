package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.dto.AgentConfigOverviewDto;
import com.openclaw.vs.dto.AgentRoleDto;
import com.openclaw.vs.dto.BootstrapSyncResultDto;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import com.openclaw.vs.util.OpenClawConfigJsonWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads and writes Agent roles from {@code ~/.openclaw/openclaw.json}:
 * {@code agents.list[]} entries and {@code agents.defaults} fallback.
 */
@Slf4j
@Service
public class OpenClawAgentConfigService {

    public static final String DEFAULT_AGENT_ID = "main";
    public static final String DEFAULTS_ROLE_ID = "__defaults__";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double FALLBACK_TEMPERATURE = 0.7;
    private static final int FALLBACK_MAX_TOKENS = 2048;

    private final OpenClawModelConfigService modelConfigService;

    public OpenClawAgentConfigService(OpenClawModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    public Path getConfigPath() {
        return modelConfigService.getConfigPath();
    }

    public AgentConfigOverviewDto readOverview() {
        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            return AgentConfigOverviewDto.builder()
                .configPath(configPath.toString())
                .configExists(false)
                .build();
        }

        try {
            JsonNode root = MAPPER.readTree(configPath.toFile());
            JsonNode defaults = root.path("agents").path("defaults");
            JsonNode modelNode = defaults.path("model");
            String primary = readPrimaryModelRef(modelNode, defaults);
            List<String> fallbacks = readFallbackRefs(modelNode);

            String workspace = defaults.path("workspace").asText(null);
            Integer timeoutSeconds = defaults.has("timeoutSeconds")
                ? defaults.path("timeoutSeconds").asInt()
                : null;
            String toolsProfile = root.path("tools").path("profile").asText(null);

            PromptContext prompt = resolvePromptContext(defaults, null, workspace);
            double temperature = readTemperature(defaults, null, primary);
            int maxTokens = readMaxTokens(defaults, null, primary);

            return AgentConfigOverviewDto.builder()
                .configPath(configPath.toString())
                .configExists(true)
                .workspace(workspace)
                .timeoutSeconds(timeoutSeconds)
                .primaryModelRef(primary)
                .fallbackModelRefs(fallbacks)
                .toolsProfile(toolsProfile)
                .systemPrompt(prompt.systemPrompt())
                .promptSource(prompt.promptSource())
                .bootstrapFile(prompt.bootstrapFile())
                .bootstrapLineCount(prompt.bootstrapLineCount())
                .bootstrapPreview(prompt.bootstrapPreview())
                .defaultTemperature(temperature)
                .defaultMaxTokens(maxTokens)
                .temperatureFromConfig(hasParamDouble(defaults, null, "temperature")
                    || hasModelParamDouble(defaults, primary, "temperature"))
                .maxTokensFromConfig(hasParamInt(defaults, null, "maxTokens")
                    || hasModelParamInt(defaults, primary, "maxTokens"))
                .build();
        } catch (Exception e) {
            log.warn("Failed to read OpenClaw agent overview: {}", e.getMessage());
            return AgentConfigOverviewDto.builder()
                .configPath(configPath.toString())
                .configExists(true)
                .build();
        }
    }

    public List<AgentRoleDto> listRoles() {
        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            return List.of();
        }

        try {
            JsonNode root = MAPPER.readTree(configPath.toFile());
            JsonNode agents = root.path("agents");
            JsonNode defaults = agents.path("defaults");
            JsonNode list = agents.path("list");

            List<AgentRoleDto> roles = new ArrayList<>();
            Set<String> configIds = new HashSet<>();

            if (list.isArray() && list.size() > 0) {
                for (JsonNode agent : list) {
                    String id = agent.path("id").asText(null);
                    if (id != null && !id.isBlank()) {
                        configIds.add(id);
                    }
                    roles.add(mapAgentEntry(agent, defaults));
                }
                roles.add(mapDefaultsAsRole(defaults).toBuilder()
                    .name("全局默认")
                    .defaultAgent(false)
                    .build());
            } else {
                List<String> diskAgentIds = discoverAgentIdsFromDisk(configPath);
                if (diskAgentIds.isEmpty()) {
                    roles.add(mapDefaultsAsRole(defaults));
                } else {
                    for (int i = 0; i < diskAgentIds.size(); i++) {
                        String agentId = diskAgentIds.get(i);
                        boolean asDefault = DEFAULT_AGENT_ID.equals(agentId)
                            || (i == 0 && !diskAgentIds.contains(DEFAULT_AGENT_ID));
                        roles.add(mapDiscoveredAgent(agentId, defaults, asDefault));
                    }
                }
            }

            if (!configIds.isEmpty()) {
                for (String diskId : discoverAgentIdsFromDisk(configPath)) {
                    if (!configIds.contains(diskId)) {
                        roles.add(mapDiscoveredAgent(diskId, defaults, false));
                    }
                }
            }

            roles.sort(Comparator
                .comparing(AgentRoleDto::isDefaultAgent).reversed()
                .thenComparing(AgentRoleDto::isBuiltin).reversed()
                .thenComparing(AgentRoleDto::getName));
            return roles;
        } catch (Exception e) {
            log.warn("Failed to list OpenClaw agent roles: {}", e.getMessage());
            return List.of();
        }
    }

    public AgentRoleDto getRole(String id) {
        return listRoles().stream()
            .filter(r -> r.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("角色不存在: " + id));
    }

    public AgentRoleDto saveRole(AgentRoleDto dto) throws Exception {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }

        Path configPath = getConfigPath();
        ObjectNode root;
        if (Files.isRegularFile(configPath)) {
            root = (ObjectNode) MAPPER.readTree(configPath.toFile());
        } else {
            root = MAPPER.createObjectNode();
            Files.createDirectories(configPath.getParent());
        }

        ObjectNode agents = ensureObject(root, "agents");
        ObjectNode defaults = ensureObject(agents, "defaults");
        JsonNode listNode = agents.get("list");
        ArrayNode list = listNode instanceof ArrayNode array ? array : null;
        boolean listEmpty = list == null || list.isEmpty();

        String roleId = dto.getId();
        boolean editingDefaults = DEFAULTS_ROLE_ID.equals(roleId)
            || (listEmpty && (roleId == null || roleId.isBlank() || DEFAULT_AGENT_ID.equals(roleId)));

        if (editingDefaults) {
            applyRoleToDefaults(defaults, dto);
            roleId = DEFAULTS_ROLE_ID;
        } else if (roleId == null || roleId.isBlank()) {
            list = ensureArray(agents, "list");
            roleId = ensureUniqueAgentId(list, slugify(dto.getName()));
            ObjectNode agentNode = MAPPER.createObjectNode();
            agentNode.put("id", roleId);
            applyRoleToAgent(agentNode, dto, defaults);
            list.add(agentNode);
        } else {
            list = ensureArray(agents, "list");
            final String existingId = roleId;
            ObjectNode agentNode = findAgentById(list, existingId)
                .orElseThrow(() -> new NoSuchElementException("角色不存在: " + existingId));
            applyRoleToAgent(agentNode, dto, defaults);
        }

        if (dto.getDefaultModel() != null && !dto.getDefaultModel().isBlank()) {
            OpenClawModelConfigService.applyModelRegistration(root, dto.getDefaultModel().trim());
        }

        writeConfig(root, configPath);
        log.info("OpenClaw agent role saved: id={}, name={}", roleId, dto.getName());

        return getRole(roleId);
    }

    public void deleteRole(String id) throws Exception {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("角色 id 不能为空");
        }
        if (DEFAULTS_ROLE_ID.equals(id) || DEFAULT_AGENT_ID.equals(id)) {
            throw new IllegalArgumentException("全局默认配置不可删除");
        }

        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            throw new NoSuchElementException("角色不存在: " + id);
        }

        ObjectNode root = (ObjectNode) MAPPER.readTree(configPath.toFile());
        ObjectNode agents = ensureObject(root, "agents");
        ArrayNode list = agents.path("list") instanceof ArrayNode array ? array : null;

        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("全局默认配置不可删除");
        }

        AgentRoleDto existing = getRole(id);
        if (existing.isDefaultAgent() || DEFAULTS_ROLE_ID.equals(id)) {
            throw new IllegalArgumentException("默认 Agent 不可删除");
        }

        boolean removed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (id.equals(list.get(i).path("id").asText(null))) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) {
            throw new NoSuchElementException("角色不存在: " + id);
        }

        writeConfig(root, configPath);
        log.info("OpenClaw agent role deleted: id={}", id);
    }

    /**
     * 将工作区 {@code AGENTS.md} 全文写入 {@code agents.defaults.systemPrompt}（或指定 agent）。
     * 便于在配置中心统一管理；OpenClaw 运行时仍会加载 AGENTS.md bootstrap。
     */
    public BootstrapSyncResultDto syncBootstrapPromptToConfig(String roleId) throws Exception {
        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalStateException("OpenClaw 配置文件不存在: " + configPath);
        }

        ObjectNode root = (ObjectNode) MAPPER.readTree(configPath.toFile());
        ObjectNode agents = ensureObject(root, "agents");
        ObjectNode defaults = ensureObject(agents, "defaults");

        String workspace = defaults.path("workspace").asText(null);
        if (workspace == null || workspace.isBlank()) {
            throw new IllegalStateException("未配置 agents.defaults.workspace，无法定位 AGENTS.md");
        }

        Path agentsMd = Path.of(workspace.trim()).resolve("AGENTS.md");
        if (!Files.isRegularFile(agentsMd)) {
            throw new IllegalStateException("工作区中未找到 AGENTS.md: " + agentsMd);
        }

        String content = Files.readString(agentsMd, StandardCharsets.UTF_8).trim();
        if (content.isBlank()) {
            throw new IllegalStateException("AGENTS.md 内容为空");
        }

        String targetField;
        if (roleId != null && !roleId.isBlank() && !DEFAULTS_ROLE_ID.equals(roleId)) {
            ArrayNode list = agents.path("list") instanceof ArrayNode arr ? arr : null;
            if (list == null || list.isEmpty()) {
                throw new IllegalArgumentException("agents.list 为空，无法写入指定 Agent: " + roleId);
            }
            ObjectNode agentNode = findAgentById(list, roleId.trim())
                .orElseThrow(() -> new NoSuchElementException("角色不存在: " + roleId));
            agentNode.put("systemPrompt", content);
            targetField = "agents.list[].systemPrompt (id=" + roleId.trim() + ")";
        } else {
            defaults.put("systemPrompt", content);
            targetField = "agents.defaults.systemPrompt";
        }

        writeConfig(root, configPath);

        int lines = (int) content.lines().count();
        log.info("Synced AGENTS.md ({} chars) to {}", content.length(), targetField);

        return BootstrapSyncResultDto.builder()
            .targetField(targetField)
            .bootstrapFile(agentsMd.toString())
            .charCount(content.length())
            .lineCount(lines)
            .message("已将 AGENTS.md 同步到 openclaw.json，可在配置中心继续编辑")
            .build();
    }

    /** 工作区缺少 AGENTS.md 时写入 OpenClaw 兼容的默认模板。 */
    public BootstrapSyncResultDto initDefaultAgentsMd() throws Exception {
        Path configPath = getConfigPath();
        ObjectNode root;
        if (Files.isRegularFile(configPath)) {
            root = (ObjectNode) MAPPER.readTree(configPath.toFile());
        } else {
            root = MAPPER.createObjectNode();
            Files.createDirectories(configPath.getParent());
        }

        ObjectNode agents = ensureObject(root, "agents");
        ObjectNode defaults = ensureObject(agents, "defaults");

        String workspace = defaults.path("workspace").asText(null);
        if (workspace == null || workspace.isBlank()) {
            String home = System.getProperty("user.home");
            workspace = Path.of(home, ".openclaw", "workspace").toString().replace('\\', '/');
            defaults.put("workspace", workspace);
        }

        Path workspacePath = Path.of(workspace.trim());
        Files.createDirectories(workspacePath);
        Path agentsMd = workspacePath.resolve("AGENTS.md");

        if (Files.isRegularFile(agentsMd) && Files.size(agentsMd) > 0) {
            String existing = Files.readString(agentsMd, StandardCharsets.UTF_8).trim();
            return BootstrapSyncResultDto.builder()
                .targetField("agents.defaults.workspace")
                .bootstrapFile(agentsMd.toString())
                .charCount(existing.length())
                .lineCount((int) existing.lines().count())
                .message("AGENTS.md 已存在，无需初始化")
                .build();
        }

        String template = DEFAULT_AGENTS_MD_TEMPLATE;
        Files.writeString(agentsMd, template, StandardCharsets.UTF_8);

        writeConfig(root, configPath);

        return BootstrapSyncResultDto.builder()
            .targetField("workspace/AGENTS.md")
            .bootstrapFile(agentsMd.toString())
            .charCount(template.length())
            .lineCount((int) template.lines().count())
            .message("已创建工作区默认 AGENTS.md，OpenClaw 启动时将自动加载")
            .build();
    }

    static Optional<String> readBootstrapFullContent(String workspace) {
        if (workspace == null || workspace.isBlank()) {
            return Optional.empty();
        }
        Path agentsMd = Path.of(workspace.trim()).resolve("AGENTS.md");
        if (!Files.isRegularFile(agentsMd)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(agentsMd, StandardCharsets.UTF_8).trim();
            return content.isBlank() ? Optional.empty() : Optional.of(content);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static final String DEFAULT_AGENTS_MD_TEMPLATE = """
        # AGENTS.md - 工作区说明

        你是 OpenClaw 助手，请用中文与用户交流，回答简洁、准确、可执行。

        ## 行为准则

        - 优先理解用户真实意图，再给出方案或代码
        - 涉及文件、命令或配置变更时，说明风险与步骤
        - 不确定时主动澄清，避免臆测

        ## 工作区

        本目录为 Agent 工作区；运行时上下文可能包含 `SOUL.md`、`USER.md` 与 `memory/` 下的日记。
        """;

    /**
     * OpenClaw 在未配置 {@code agents.list} 时，会在 {@code ~/.openclaw/agents/<id>/} 下落地 Agent 目录。
     */
    private static List<String> discoverAgentIdsFromDisk(Path configPath) {
        Path agentsDir = resolveAgentsDir(configPath);
        if (!Files.isDirectory(agentsDir)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try (Stream<Path> stream = Files.list(agentsDir)) {
            stream.filter(Files::isDirectory)
                .filter(OpenClawAgentConfigService::looksLikeAgentHome)
                .map(p -> p.getFileName().toString())
                .sorted()
                .forEach(ids::add);
        } catch (Exception e) {
            log.debug("Failed to scan agent dirs under {}: {}", agentsDir, e.getMessage());
        }
        return ids;
    }

    private static Path resolveAgentsDir(Path configPath) {
        Path parent = configPath.getParent();
        if (parent != null) {
            return parent.resolve("agents");
        }
        return Path.of(System.getProperty("user.home"), ".openclaw", "agents");
    }

    private static boolean looksLikeAgentHome(Path dir) {
        return Files.isDirectory(dir.resolve("agent"))
            || Files.isDirectory(dir.resolve("sessions"))
            || Files.isRegularFile(dir.resolve("agent").resolve("models.json"));
    }

    private AgentRoleDto mapDiscoveredAgent(String agentId, JsonNode defaults, boolean asDefault) {
        String primary = readPrimaryModelRef(defaults.path("model"), defaults);
        String workspace = defaults.path("workspace").asText(null);
        PromptContext prompt = resolvePromptContext(defaults, null, workspace);
        String displayName = DEFAULT_AGENT_ID.equals(agentId) ? "默认 Agent" : agentId;

        return AgentRoleDto.builder()
            .id(agentId)
            .name(displayName)
            .systemPrompt(prompt.systemPrompt())
            .promptSource(prompt.promptSource())
            .bootstrapFile(prompt.bootstrapFile())
            .bootstrapLineCount(prompt.bootstrapLineCount())
            .bootstrapPreview(prompt.bootstrapPreview())
            .temperature(readTemperature(defaults, null, primary))
            .maxTokens(readMaxTokens(defaults, null, primary))
            .temperatureFromConfig(hasParamDouble(defaults, null, "temperature")
                || hasModelParamDouble(defaults, primary, "temperature"))
            .maxTokensFromConfig(hasParamInt(defaults, null, "maxTokens")
                || hasModelParamInt(defaults, primary, "maxTokens"))
            .defaultModel(primary != null ? primary : "")
            .workspace(workspace != null ? workspace : "")
            .builtin(true)
            .openclaw(true)
            .defaultAgent(asDefault)
            .updatedAt(configLastModified())
            .build();
    }

    private AgentRoleDto mapDefaultsAsRole(JsonNode defaults) {
        String primary = readPrimaryModelRef(defaults.path("model"), defaults);
        String workspace = defaults.path("workspace").asText(null);
        PromptContext prompt = resolvePromptContext(defaults, null, workspace);
        return AgentRoleDto.builder()
            .id(DEFAULTS_ROLE_ID)
            .name("默认 Agent")
            .systemPrompt(prompt.systemPrompt())
            .promptSource(prompt.promptSource())
            .bootstrapFile(prompt.bootstrapFile())
            .bootstrapLineCount(prompt.bootstrapLineCount())
            .bootstrapPreview(prompt.bootstrapPreview())
            .temperature(readTemperature(defaults, null, primary))
            .maxTokens(readMaxTokens(defaults, null, primary))
            .temperatureFromConfig(hasParamDouble(defaults, null, "temperature")
                || hasModelParamDouble(defaults, primary, "temperature"))
            .maxTokensFromConfig(hasParamInt(defaults, null, "maxTokens")
                || hasModelParamInt(defaults, primary, "maxTokens"))
            .defaultModel(primary != null ? primary : "")
            .workspace(workspace != null ? workspace : "")
            .builtin(true)
            .openclaw(true)
            .defaultAgent(true)
            .updatedAt(configLastModified())
            .build();
    }

    private AgentRoleDto mapAgentEntry(JsonNode agent, JsonNode defaults) {
        String id = agent.path("id").asText(DEFAULT_AGENT_ID);
        String name = agent.path("name").asText(null);
        if (name == null || name.isBlank()) {
            name = agent.path("identity").path("name").asText(id);
        }
        String modelRef = readAgentModelRef(agent, defaults);
        String workspace = agent.path("workspace").asText(defaults.path("workspace").asText(null));
        boolean isDefault = agent.path("default").asBoolean(false);
        PromptContext prompt = resolvePromptContext(defaults, agent, workspace);

        return AgentRoleDto.builder()
            .id(id)
            .name(name)
            .systemPrompt(prompt.systemPrompt())
            .promptSource(prompt.promptSource())
            .bootstrapFile(prompt.bootstrapFile())
            .bootstrapLineCount(prompt.bootstrapLineCount())
            .bootstrapPreview(prompt.bootstrapPreview())
            .temperature(readTemperature(defaults, agent, modelRef))
            .maxTokens(readMaxTokens(defaults, agent, modelRef))
            .temperatureFromConfig(hasParamDouble(defaults, agent, "temperature")
                || hasModelParamDouble(defaults, modelRef, "temperature"))
            .maxTokensFromConfig(hasParamInt(defaults, agent, "maxTokens")
                || hasModelParamInt(defaults, modelRef, "maxTokens"))
            .defaultModel(modelRef != null ? modelRef : "")
            .workspace(workspace != null ? workspace : "")
            .builtin(isDefault || DEFAULT_AGENT_ID.equals(id))
            .openclaw(true)
            .defaultAgent(isDefault)
            .updatedAt(configLastModified())
            .build();
    }

    private void applyRoleToDefaults(ObjectNode defaults, AgentRoleDto dto) {
        if (dto.getSystemPrompt() != null) {
            if (dto.getSystemPrompt().isBlank()) {
                defaults.remove("systemPrompt");
            } else {
                defaults.put("systemPrompt", dto.getSystemPrompt().trim());
            }
        }

        if (dto.getWorkspace() != null && !dto.getWorkspace().isBlank()) {
            defaults.put("workspace", dto.getWorkspace().trim());
        }

        if (dto.getTimeoutSeconds() != null && dto.getTimeoutSeconds() > 0) {
            defaults.put("timeoutSeconds", dto.getTimeoutSeconds());
        }

        ObjectNode params = ensureObject(defaults, "params");
        params.put("temperature", dto.getTemperature() > 0 ? dto.getTemperature() : FALLBACK_TEMPERATURE);
        params.put("maxTokens", dto.getMaxTokens() > 0 ? dto.getMaxTokens() : FALLBACK_MAX_TOKENS);

        if (dto.getDefaultModel() != null && !dto.getDefaultModel().isBlank()) {
            ObjectNode model = ensureObject(defaults, "model");
            model.put("primary", dto.getDefaultModel().trim());
        }
    }

    /** 读取工作区 AGENTS.md 全文（用于配置中心预览）。 */
    public String readBootstrapMarkdown() {
        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalStateException("OpenClaw 配置文件不存在: " + configPath);
        }
        try {
            JsonNode root = MAPPER.readTree(configPath.toFile());
            String workspace = root.path("agents").path("defaults").path("workspace").asText(null);
            return readBootstrapFullContent(workspace)
                .orElseThrow(() -> new IllegalStateException(
                    "工作区中未找到 AGENTS.md，请先配置 agents.defaults.workspace 或初始化 Bootstrap"
                ));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取 AGENTS.md 失败: " + e.getMessage(), e);
        }
    }

    private void applyRoleToAgent(ObjectNode agent, AgentRoleDto dto, JsonNode defaults) {
        agent.put("name", dto.getName().trim());

        if (dto.getSystemPrompt() != null) {
            if (dto.getSystemPrompt().isBlank()) {
                agent.remove("systemPrompt");
            } else {
                agent.put("systemPrompt", dto.getSystemPrompt().trim());
            }
        }

        ObjectNode params = ensureObject(agent, "params");
        params.put("temperature", dto.getTemperature() > 0 ? dto.getTemperature() : FALLBACK_TEMPERATURE);
        params.put("maxTokens", dto.getMaxTokens() > 0 ? dto.getMaxTokens() : FALLBACK_MAX_TOKENS);

        if (dto.getDefaultModel() != null && !dto.getDefaultModel().isBlank()) {
            agent.put("model", dto.getDefaultModel().trim());
        } else if (agent.has("model")) {
            agent.remove("model");
        }

        if (dto.getWorkspace() != null && !dto.getWorkspace().isBlank()) {
            agent.put("workspace", dto.getWorkspace().trim());
        }
    }

    private record PromptContext(
        String systemPrompt,
        String promptSource,
        String bootstrapFile,
        Integer bootstrapLineCount,
        String bootstrapPreview
    ) {}

    private static PromptContext resolvePromptContext(JsonNode defaults, JsonNode agent, String workspace) {
        if (agent != null) {
            String agentPrompt = agent.path("systemPrompt").asText(null);
            if (agentPrompt != null && !agentPrompt.isBlank()) {
                return new PromptContext(agentPrompt.trim(), "config", null, null, null);
            }
        }
        String defaultsPrompt = defaults.path("systemPrompt").asText(null);
        if (defaultsPrompt != null && !defaultsPrompt.isBlank()) {
            return new PromptContext(defaultsPrompt.trim(), "config", null, null, null);
        }
        return readBootstrapContext(workspace).orElse(
            new PromptContext("", "none", null, null, null)
        );
    }

    private static Optional<PromptContext> readBootstrapContext(String workspace) {
        if (workspace == null || workspace.isBlank()) {
            return Optional.empty();
        }
        Path agentsMd = Path.of(workspace.trim()).resolve("AGENTS.md");
        if (!Files.isRegularFile(agentsMd)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(agentsMd, StandardCharsets.UTF_8).trim();
            if (content.isBlank()) {
                return Optional.empty();
            }
            int lines = (int) content.lines().count();
            return Optional.of(new PromptContext(
                "",
                "bootstrap",
                agentsMd.toString(),
                lines,
                previewBootstrap(content)
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String previewBootstrap(String content) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String text = trimmed.replaceAll("^#+\\s*", "").trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > 100) {
                return text.substring(0, 97) + "...";
            }
            return text;
        }
        return "";
    }

    private static boolean hasParamDouble(JsonNode defaults, JsonNode agent, String key) {
        return readParamDouble(agent, key) != null || readParamDouble(defaults, key) != null;
    }

    private static boolean hasParamInt(JsonNode defaults, JsonNode agent, String key) {
        return readParamInt(agent, key) != null || readParamInt(defaults, key) != null;
    }

    private static boolean hasModelParamDouble(JsonNode defaults, String modelRef, String key) {
        return readModelParamDouble(defaults, modelRef, key) != null;
    }

    private static boolean hasModelParamInt(JsonNode defaults, String modelRef, String key) {
        return readModelParamInt(defaults, modelRef, key) != null;
    }

    private static double readTemperature(JsonNode defaults, JsonNode agent, String modelRef) {
        Double fromAgent = readParamDouble(agent, "temperature");
        if (fromAgent != null) {
            return fromAgent;
        }
        Double fromModel = readModelParamDouble(defaults, modelRef, "temperature");
        if (fromModel != null) {
            return fromModel;
        }
        Double fromDefaults = readParamDouble(defaults, "temperature");
        return fromDefaults != null ? fromDefaults : FALLBACK_TEMPERATURE;
    }

    private static int readMaxTokens(JsonNode defaults, JsonNode agent, String modelRef) {
        Integer fromAgent = readParamInt(agent, "maxTokens");
        if (fromAgent != null) {
            return fromAgent;
        }
        Integer fromModel = readModelParamInt(defaults, modelRef, "maxTokens");
        if (fromModel != null) {
            return fromModel;
        }
        Integer fromDefaults = readParamInt(defaults, "maxTokens");
        if (fromDefaults != null) {
            return fromDefaults;
        }
        if (defaults.has("contextTokens")) {
            return Math.min(defaults.path("contextTokens").asInt(), FALLBACK_MAX_TOKENS);
        }
        return FALLBACK_MAX_TOKENS;
    }

    private static Double readParamDouble(JsonNode node, String key) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode params = node.path("params");
        if (!params.has(key)) {
            return null;
        }
        return params.path(key).asDouble();
    }

    private static Integer readParamInt(JsonNode node, String key) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode params = node.path("params");
        if (!params.has(key)) {
            return null;
        }
        return params.path(key).asInt();
    }

    private static Double readModelParamDouble(JsonNode defaults, String modelRef, String key) {
        if (modelRef == null || modelRef.isBlank()) {
            return null;
        }
        JsonNode modelParams = defaults.path("models").path(modelRef).path("params");
        if (!modelParams.has(key)) {
            return null;
        }
        return modelParams.path(key).asDouble();
    }

    private static Integer readModelParamInt(JsonNode defaults, String modelRef, String key) {
        if (modelRef == null || modelRef.isBlank()) {
            return null;
        }
        JsonNode modelParams = defaults.path("models").path(modelRef).path("params");
        if (!modelParams.has(key)) {
            return null;
        }
        return modelParams.path(key).asInt();
    }

    private static String readAgentModelRef(JsonNode agent, JsonNode defaults) {
        JsonNode model = agent.path("model");
        if (model.isTextual()) {
            return model.asText(null);
        }
        if (model.isObject()) {
            String primary = model.path("primary").asText(null);
            if (primary != null && !primary.isBlank()) {
                return primary;
            }
        }
        return readPrimaryModelRef(defaults.path("model"), defaults);
    }

    private static String readPrimaryModelRef(JsonNode modelNode, JsonNode defaults) {
        if (modelNode.isTextual()) {
            return modelNode.asText(null);
        }
        if (modelNode.isObject()) {
            String primary = modelNode.path("primary").asText(null);
            if (primary != null && !primary.isBlank()) {
                return primary;
            }
        }
        return null;
    }

    private static List<String> readFallbackRefs(JsonNode modelNode) {
        JsonNode fb = modelNode.path("fallbacks");
        if (!fb.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : fb) {
            String ref = item.asText(null);
            if (ref != null && !ref.isBlank()) {
                out.add(ref);
            }
        }
        return out;
    }

    private static Optional<ObjectNode> findAgentById(ArrayNode list, String id) {
        for (JsonNode node : list) {
            if (id.equals(node.path("id").asText(null))) {
                return Optional.of((ObjectNode) node);
            }
        }
        return Optional.empty();
    }

    private static String ensureUniqueAgentId(ArrayNode list, String baseId) {
        String candidate = baseId;
        int suffix = 2;
        while (agentIdExists(list, candidate)) {
            candidate = baseId + "-" + suffix++;
        }
        return candidate;
    }

    private static boolean agentIdExists(ArrayNode list, String id) {
        for (JsonNode node : list) {
            if (id.equals(node.path("id").asText(null))) {
                return true;
            }
        }
        return false;
    }

    private static String slugify(String name) {
        String slug = name.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\u4e00-\\u9fff]+", "-")
            .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            return "agent";
        }
        if (slug.length() > 32) {
            slug = slug.substring(0, 32);
        }
        return slug;
    }

    private static void writeConfig(ObjectNode root, Path configPath) {
        OpenClawConfigJsonWriter.writePretty(configPath, MAPPER, root);
        OpenClawGatewayConfigReader.invalidateConfigCache();
    }

    private long configLastModified() {
        try {
            return Files.getLastModifiedTime(getConfigPath()).toMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static ObjectNode ensureObject(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = MAPPER.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private static ArrayNode ensureArray(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = MAPPER.createArrayNode();
        parent.set(field, created);
        return created;
    }
}
