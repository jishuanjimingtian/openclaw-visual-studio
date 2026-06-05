package com.openclaw.vs.controller;

import com.openclaw.vs.dto.ApiResponse;
import com.openclaw.vs.dto.ModelMarketOverviewDto;
import com.openclaw.vs.dto.ModelConfigRequest;
import com.openclaw.vs.dto.ModelConfigView;
import com.openclaw.vs.dto.ModelTestResult;
import com.openclaw.vs.dto.PageResult;
import com.openclaw.vs.dto.OpenClawCatalogModelDto;
import com.openclaw.vs.dto.OpenClawEndpointOptionDto;
import com.openclaw.vs.dto.OpenClawModelOverview;
import com.openclaw.vs.dto.SetOpenClawPrimaryRequest;
import com.openclaw.vs.service.ModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/models")
@RequiredArgsConstructor
@Tag(name = "模型管理", description = "多 Provider 模型配置 API")
public class ModelController {

    private final ModelService modelService;

    @GetMapping
    @Operation(summary = "分页查询模型配置")
    public ApiResponse<PageResult<ModelConfigView>> listModels(
        @Parameter(description = "分页参数") @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(PageResult.from(modelService.listModels(pageable)));
    }

    @GetMapping("/openclaw/overview")
    @Operation(summary = "读取 OpenClaw 本地模型配置概览")
    public ApiResponse<OpenClawModelOverview> getOpenClawOverview() {
        return ApiResponse.success(modelService.getOpenClawOverview());
    }

    @PostMapping("/openclaw/sync")
    @Operation(summary = "从 OpenClaw 配置同步模型到本平台")
    public ApiResponse<java.util.List<ModelConfigView>> syncFromOpenClaw() {
        return ApiResponse.success(modelService.syncFromOpenClaw());
    }

    @GetMapping("/openclaw/catalog")
    @Operation(summary = "OpenClaw 内置模型建议列表（仅用于 UI 选择，不会自动写入配置）")
    public ApiResponse<java.util.List<OpenClawCatalogModelDto>> getOpenClawCatalog(
        @Parameter(description = "Provider，如 qwen") @RequestParam(defaultValue = "qwen") String provider) {
        return ApiResponse.success(modelService.listOpenClawCatalog(provider));
    }

    @GetMapping("/market")
    @Operation(summary = "模型市场（按分类浏览，含已配置状态）")
    public ApiResponse<ModelMarketOverviewDto> getModelMarket(
        @Parameter(description = "分类：all/domestic/international/local/aggregator/coding/reasoning")
        @RequestParam(required = false) String category,
        @Parameter(description = "Provider 筛选") @RequestParam(required = false) String provider,
        @Parameter(description = "搜索关键词") @RequestParam(required = false) String q) {
        return ApiResponse.success(modelService.getModelMarket(category, provider, q));
    }

    @GetMapping("/openclaw/provider-endpoints")
    @Operation(summary = "Provider 可选 API 端点")
    public ApiResponse<java.util.List<OpenClawEndpointOptionDto>> getProviderEndpoints(
        @RequestParam String provider) {
        return ApiResponse.success(modelService.listProviderEndpoints(provider));
    }

    @GetMapping("/openclaw/qwen-endpoints")
    @Operation(summary = "Qwen 可选 API 端点（写入 models.providers.qwen.baseUrl）")
    public ApiResponse<java.util.List<OpenClawEndpointOptionDto>> getQwenEndpoints() {
        return ApiResponse.success(modelService.listQwenEndpoints());
    }

    @GetMapping("/openclaw/deepseek-endpoints")
    @Operation(summary = "DeepSeek 可选 API 端点（写入 models.providers.deepseek.baseUrl）")
    public ApiResponse<java.util.List<OpenClawEndpointOptionDto>> getDeepSeekEndpoints() {
        return ApiResponse.success(modelService.listDeepSeekEndpoints());
    }

    @PostMapping("/openclaw/primary")
    @Operation(summary = "设置 OpenClaw 默认模型与可选备用链")
    public ApiResponse<Void> setOpenClawPrimary(@RequestBody SetOpenClawPrimaryRequest body) throws Exception {
        if (body.getModelRef() == null || body.getModelRef().isBlank()) {
            throw new IllegalArgumentException("modelRef 不能为空");
        }
        modelService.setOpenClawPrimary(body.getModelRef().trim(), body.getFallbackModelRefs());
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/apply-openclaw")
    @Operation(summary = "将模型写入 OpenClaw 配置")
    public ApiResponse<Void> applyToOpenClaw(
        @Parameter(description = "模型ID") @PathVariable String id,
        @Parameter(description = "是否注册到 openclaw.json") @RequestParam(defaultValue = "true") boolean register,
        @Parameter(description = "是否设为默认主模型") @RequestParam(defaultValue = "false") boolean setAsPrimary,
        @Parameter(description = "是否同步 API Key 到 openclaw.json") @RequestParam(defaultValue = "true") boolean syncApiKey) throws Exception {
        modelService.applyToOpenClaw(id, register, setAsPrimary, syncApiKey, null, null);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取模型详情")
    public ApiResponse<ModelConfigView> getModel(
        @Parameter(description = "模型ID") @PathVariable String id) {
        return ApiResponse.success(modelService.getModel(id));
    }

    @PostMapping
    @Operation(summary = "添加模型配置")
    public ApiResponse<ModelConfigView> addModel(@RequestBody ModelConfigRequest request) throws Exception {
        return ApiResponse.success(modelService.addModel(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模型配置")
    public ApiResponse<ModelConfigView> updateModel(
        @PathVariable String id,
        @RequestBody ModelConfigRequest request) throws Exception {
        return ApiResponse.success(modelService.updateModel(id, request));
    }

    @PatchMapping("/{id}/api-key")
    @Operation(summary = "单独设置模型 API Key")
    public ApiResponse<ModelConfigView> setApiKey(
        @PathVariable String id,
        @RequestBody SetApiKeyBody body) throws Exception {
        return ApiResponse.success(modelService.setApiKey(
            id,
            body.getApiKey(),
            body.isSyncToOpenClaw()
        ));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型配置")
    public ApiResponse<Void> deleteModel(@PathVariable String id) {
        modelService.deleteModel(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "测试模型连通性")
    public ApiResponse<ModelTestResult> testModel(@PathVariable String id) {
        return ApiResponse.success(modelService.testModel(id));
    }

    @Data
    public static class SetApiKeyBody {
        private String apiKey;
        private boolean syncToOpenClaw = true;
    }
}
