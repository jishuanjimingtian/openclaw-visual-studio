package com.openclaw.vs.service;

import com.openclaw.vs.dto.ModelMarketOverviewDto;
import com.openclaw.vs.dto.ModelConfigRequest;
import com.openclaw.vs.dto.ModelConfigView;
import com.openclaw.vs.dto.ModelTestResult;
import com.openclaw.vs.dto.OpenClawCatalogModelDto;
import com.openclaw.vs.dto.OpenClawEndpointOptionDto;
import com.openclaw.vs.dto.OpenClawModelEntry;
import com.openclaw.vs.dto.OpenClawModelOverview;
import com.openclaw.vs.gateway.OpenClawModelCatalog;
import com.openclaw.vs.exception.BadRequestException;
import com.openclaw.vs.exception.NotFoundException;
import com.openclaw.vs.model.ModelConfig;
import com.openclaw.vs.repository.ModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

    private static final Set<String> MODEL_REF_PROVIDERS = Set.of(
        "qwen", "openclaw", "deepseek", "openai", "claude", "ollama", "google", "openrouter"
    );

    private final ModelConfigRepository modelConfigRepository;
    private final TextEncryptor textEncryptor;
    private final OpenClawModelConfigService openClawModelConfigService;
    private final ModelConnectivityService modelConnectivityService;

    public Page<ModelConfigView> listModels(Pageable pageable) {
        Page<ModelConfig> models = modelConfigRepository.findAll(pageable);
        OpenClawModelOverview overview = openClawModelConfigService.readOverview();
        List<ModelConfigView> views = models.getContent().stream()
            .map(m -> toView(m, overview))
            .toList();
        return new PageImpl<>(views, pageable, models.getTotalElements());
    }

    public ModelConfigView getModel(String id) {
        ModelConfig model = modelConfigRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("模型配置", id));
        return toView(model, openClawModelConfigService.readOverview());
    }

    public List<ModelConfigView> listAllModels() {
        OpenClawModelOverview overview = openClawModelConfigService.readOverview();
        return modelConfigRepository.findAll().stream()
            .map(m -> toView(m, overview))
            .toList();
    }

    @Transactional
    public ModelConfigView addModel(ModelConfigRequest request) throws Exception {
        ModelConfig model = new ModelConfig();
        model.setId(UUID.randomUUID().toString());
        applyRequest(model, request, true);
        ModelConfig saved = modelConfigRepository.save(model);
        scheduleOpenClawSync(saved, request);
        return toView(saved, openClawModelConfigService.readOverview());
    }

    @Transactional
    public ModelConfigView updateModel(String id, ModelConfigRequest request) throws Exception {
        ModelConfig existing = modelConfigRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("模型配置", id));
        applyRequest(existing, request, false);
        ModelConfig saved = modelConfigRepository.save(existing);
        scheduleOpenClawSync(saved, request);
        return toView(saved, openClawModelConfigService.readOverview());
    }

    @Transactional
    public ModelConfigView setApiKey(String id, String apiKey, boolean syncToOpenClaw) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        ModelConfig existing = modelConfigRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("模型配置", id));
        existing.setApiKey(textEncryptor.encrypt(apiKey.trim()));
        ModelConfig saved = modelConfigRepository.save(existing);
        if (syncToOpenClaw) {
            String modelRef = resolveModelRef(saved);
            if (modelRef != null && !modelRef.isBlank()) {
                openClawModelConfigService.applyModelToConfig(modelRef, true, false, apiKey.trim(), null, null);
            }
        }
        return toView(saved, openClawModelConfigService.readOverview());
    }

    @Transactional
    public void deleteModel(String id) {
        if (!modelConfigRepository.existsById(id)) {
            throw new NotFoundException("模型配置", id);
        }
        modelConfigRepository.deleteById(id);
    }

    public ModelTestResult testModel(String id) {
        ModelConfig model = modelConfigRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("模型配置", id));
        String plainKey = resolvePlainApiKey(model);
        return modelConnectivityService.test(model, plainKey);
    }

    public OpenClawModelOverview getOpenClawOverview() {
        return openClawModelConfigService.readOverview();
    }

    @Transactional
    public List<ModelConfigView> syncFromOpenClaw() {
        OpenClawModelOverview overview = openClawModelConfigService.readOverview();
        for (OpenClawModelEntry entry : overview.getModels()) {
            upsertFromOpenClaw(entry);
        }
        return listAllModels();
    }

    public void applyToOpenClaw(
        String id,
        boolean register,
        boolean setAsPrimary,
        boolean syncApiKey,
        List<String> fallbackModelRefs,
        String baseUrl
    ) throws Exception {
        ModelConfig model = modelConfigRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("模型配置", id));
        String modelRef = resolveModelRef(model);
        if (modelRef == null || modelRef.isBlank()) {
            throw new IllegalArgumentException("该模型未配置 OpenClaw 模型引用（provider/modelId）");
        }
        String plainKey = null;
        if (syncApiKey) {
            plainKey = resolvePlainApiKey(model);
            if (plainKey == null || plainKey.isBlank()) {
                log.info("Skip OpenClaw apiKey sync: no key in DB for model {}", id);
            }
        }
        openClawModelConfigService.applyModelToConfig(
            modelRef,
            register || setAsPrimary,
            setAsPrimary,
            plainKey,
            fallbackModelRefs,
            baseUrl
        );
    }

    public void setOpenClawPrimary(String modelRef, List<String> fallbackModelRefs) throws Exception {
        openClawModelConfigService.setPrimaryModel(modelRef, fallbackModelRefs);
    }

    public List<OpenClawCatalogModelDto> listOpenClawCatalog(String provider) {
        return OpenClawModelCatalog.listSuggestions(provider);
    }

    public List<OpenClawEndpointOptionDto> listQwenEndpoints() {
        return OpenClawModelCatalog.listQwenEndpoints();
    }

    public List<OpenClawEndpointOptionDto> listDeepSeekEndpoints() {
        return OpenClawModelCatalog.listDeepSeekEndpoints();
    }

    public List<OpenClawEndpointOptionDto> listProviderEndpoints(String provider) {
        return OpenClawModelCatalog.listProviderEndpoints(provider);
    }

    public ModelMarketOverviewDto getModelMarket(String category, String provider, String query) {
        List<OpenClawCatalogModelDto> models = OpenClawModelCatalog.listMarket(category, provider, query);
        Map<String, String> configuredByRef = new java.util.HashMap<>();
        for (ModelConfig m : modelConfigRepository.findAll()) {
            String ref = resolveModelRef(m);
            if (ref != null && !ref.isBlank()) {
                configuredByRef.put(ref, m.getId());
            }
        }
        List<OpenClawCatalogModelDto> enriched = models.stream()
            .map(dto -> {
                String id = configuredByRef.get(dto.getModelRef());
                if (id != null) {
                    dto.setConfigured(true);
                    dto.setConfiguredModelId(id);
                } else {
                    dto.setConfigured(false);
                }
                return dto;
            })
            .toList();
        return ModelMarketOverviewDto.builder()
            .categories(OpenClawModelCatalog.listCategories())
            .models(enriched)
            .build();
    }

    private void applyRequest(ModelConfig target, ModelConfigRequest request, boolean isCreate) {
        if (request.getName() != null && !request.getName().isBlank()) {
            target.setName(request.getName().trim());
        }
        if (request.getProvider() != null && !request.getProvider().isBlank()) {
            target.setProvider(request.getProvider().trim());
        }
        if (request.getEndpoint() != null && !request.getEndpoint().isBlank()) {
            target.setEndpoint(request.getEndpoint().trim());
        }
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            target.setApiKey(textEncryptor.encrypt(request.getApiKey().trim()));
        }
        if (request.getEnabled() != null) {
            target.setEnabled(request.getEnabled());
        } else if (isCreate) {
            target.setEnabled(true);
        }
    }

    private void scheduleOpenClawSync(ModelConfig saved, ModelConfigRequest request) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runOpenClawSync(saved, request);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runOpenClawSync(saved, request);
            }
        });
    }

    /** 事务提交后执行；失败不回滚数据库。 */
    private void runOpenClawSync(ModelConfig saved, ModelConfigRequest request) {
        boolean legacyBundle = Boolean.TRUE.equals(request.getApplyToOpenClaw());
        boolean register = Boolean.TRUE.equals(request.getRegisterInOpenClaw()) || legacyBundle;
        boolean setPrimary = Boolean.TRUE.equals(request.getSetAsOpenClawPrimary()) || legacyBundle;
        boolean syncKey = Boolean.TRUE.equals(request.getSyncApiKeyToOpenClaw());
        String baseUrl = trimToNull(request.getOpenclawBaseUrl());
        boolean enabledChanged = request.getEnabled() != null;

        if (!register && !setPrimary && !syncKey && baseUrl == null && !enabledChanged) {
            return;
        }
        try {
            if (Boolean.FALSE.equals(request.getEnabled())) {
                syncEnabledStateToOpenClaw(saved, false);
                return;
            }
            if (Boolean.TRUE.equals(request.getEnabled())) {
                syncEnabledStateToOpenClaw(saved, true);
            }
            if (register || setPrimary || syncKey) {
                applyToOpenClaw(
                    saved.getId(),
                    register || setPrimary,
                    setPrimary,
                    syncKey,
                    request.getFallbackModelRefs(),
                    baseUrl
                );
            } else if (baseUrl != null) {
                String modelRef = resolveModelRef(saved);
                if (modelRef == null || modelRef.isBlank()) {
                    throw new IllegalArgumentException("请先配置 OpenClaw 模型引用");
                }
                openClawModelConfigService.applyModelToConfig(
                    modelRef, false, false, null, null, baseUrl
                );
            }
        } catch (Exception e) {
            log.error("OpenClaw sync failed for model {}: {}", saved.getId(), e.getMessage(), e);
            throw new BadRequestException(
                "模型已写入本地数据库，但同步 OpenClaw 配置失败: " + e.getMessage()
            );
        }
    }

    private void syncEnabledStateToOpenClaw(ModelConfig saved, boolean enabled) throws Exception {
        String modelRef = resolveModelRef(saved);
        if (modelRef == null || modelRef.isBlank()) {
            return;
        }
        if (enabled) {
            openClawModelConfigService.applyModelToConfig(
                modelRef, true, false, null, null, null
            );
        } else {
            openClawModelConfigService.unregisterModel(modelRef);
        }
    }

    private String resolvePlainApiKey(ModelConfig model) {
        if (!isApiKeyConfigured(model)) {
            return null;
        }
        try {
            String plain = textEncryptor.decrypt(model.getApiKey());
            if (plain != null && plain.startsWith("{cipher}")) {
                return null;
            }
            return plain;
        } catch (Exception e) {
            log.warn("Failed to decrypt api key for model {}: {}", model.getId(), e.getMessage());
            return null;
        }
    }

    private String resolveOpenClawProvider(ModelConfig model) {
        String modelRef = resolveModelRef(model);
        if (modelRef != null && modelRef.contains("/")) {
            return OpenClawModelConfigService.extractProvider(modelRef);
        }
        return model.getProvider();
    }

    private void upsertFromOpenClaw(OpenClawModelEntry entry) {
        String modelRef = entry.getModelRef();
        ModelConfig existing = modelConfigRepository.findAll().stream()
            .filter(m -> modelRef.equals(resolveModelRef(m)))
            .findFirst()
            .orElse(null);

        if (existing == null) {
            ModelConfig created = ModelConfig.builder()
                .id(UUID.randomUUID().toString())
                .name(entry.getDisplayName())
                .provider(entry.getProvider())
                .endpoint(modelRef)
                .enabled(true)
                .build();
            modelConfigRepository.save(created);
        } else {
            existing.setName(entry.getDisplayName());
            existing.setProvider(entry.getProvider());
            existing.setEndpoint(modelRef);
            existing.setEnabled(true);
            modelConfigRepository.save(existing);
        }
    }

    private ModelConfigView toView(ModelConfig model, OpenClawModelOverview overview) {
        ModelConfigView view = new ModelConfigView();
        view.setId(model.getId());
        view.setName(model.getName());
        view.setProvider(model.getProvider());
        view.setEndpoint(model.getEndpoint());
        view.setEnabled(model.isEnabled());
        view.setCreatedAt(model.getCreatedAt());
        view.setUpdatedAt(model.getUpdatedAt());
        view.setApiKey(null);
        String provider = resolveOpenClawProvider(model);
        boolean inOpenClaw = openClawModelConfigService.hasProviderApiKey(provider);
        String plainKey = resolvePlainApiKey(model);
        boolean inDb = plainKey != null && !plainKey.isBlank();
        view.setApiKeyInOpenClaw(inOpenClaw);
        view.setApiKeyPreview(inDb ? maskApiKey(plainKey) : null);
        view.setApiKeyConfigured(inDb || inOpenClaw);
        String modelRef = resolveModelRef(model);
        view.setModelRef(modelRef);
        String primaryRef = overview != null ? overview.getPrimaryModelRef() : null;
        view.setOpenclawPrimary(modelRef != null && modelRef.equals(primaryRef));
        view.setRegisteredInOpenClaw(isRegisteredInOpenClaw(modelRef, overview));
        return view;
    }

    private static boolean isRegisteredInOpenClaw(String modelRef, OpenClawModelOverview overview) {
        if (modelRef == null || modelRef.isBlank() || overview == null || overview.getModels() == null) {
            return false;
        }
        return overview.getModels().stream()
            .anyMatch(e -> modelRef.equals(e.getModelRef()));
    }

    static String maskApiKey(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        String trimmed = plain.trim();
        if (trimmed.length() <= 8) {
            return "********";
        }
        int prefixLen = Math.min(trimmed.length() <= 12 ? 4 : 7, trimmed.length() - 5);
        return trimmed.substring(0, prefixLen) + "••••" + trimmed.substring(trimmed.length() - 4);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isApiKeyConfigured(ModelConfig model) {
        return model.getApiKey() != null && !model.getApiKey().isBlank();
    }

    static String resolveModelRef(ModelConfig model) {
        if (model == null) {
            return null;
        }
        String provider = model.getProvider();
        if (MODEL_REF_PROVIDERS.contains(provider)) {
            return model.getEndpoint();
        }
        if (model.getEndpoint() != null && model.getEndpoint().contains("/")) {
            return model.getEndpoint();
        }
        return null;
    }
}
