<template>
  <div class="deployment-page page">
    <HeaderToolbar>
      <n-button :loading="pageLoading" size="small" @click="refreshPage(true)">
        <template #icon><refresh-outline /></template>
        刷新
      </n-button>
      <n-button
        v-if="!showWizard && !isDeploying"
        type="primary"
        size="small"
        @click="openDeployWizard"
      >
        <template #icon><cloud-download-outline /></template>
        {{ hasInstance ? '重新部署' : '开始安装' }}
      </n-button>
      <n-button v-if="showWizard && !isDeploying" size="small" @click="closeDeployWizard">
        取消安装
      </n-button>
    </HeaderToolbar>

    <div class="page-body">
    <!-- 部署进行中 -->
    <n-card v-if="isDeploying" size="small" class="deploy-active-card" title="正在安装 OpenClaw">
      <template #header-extra>
        <n-tag :type="getProgressStatusType(deployProgress?.stage)">
          {{ getStageLabel(deployProgress?.stage || 'checking') }}
        </n-tag>
      </template>

      <div class="progress-section">
        <n-progress
          type="line"
          :percentage="deployProgress?.percentage || 0"
          :indicator-placement="deployProgress?.percentage === 100 ? 'inside' : 'outside'"
          :color="getProgressColor(deployProgress?.percentage || 0)"
          :height="22"
          :border-radius="4"
        />
        <div class="progress-info">
          <n-text strong>{{ deployProgress?.currentAction || '正在准备...' }}</n-text>
          <n-text depth="3" style="font-size: 12px">
            {{ getStageLabel(deployProgress?.stage || 'checking') }}
          </n-text>
        </div>
      </div>

      <n-collapse :default-expanded-names="['logs']" class="mt-3">
        <n-collapse-item title="安装日志" name="logs">
          <div class="log-container">
            <n-virtual-list
              v-if="deployLogItems.length"
              ref="deployVirtualListRef"
              class="log-virtual-list"
              :items="deployLogItems"
              :item-size="22"
              :style="{ maxHeight: '256px' }"
            >
              <template #default="{ item }">
                <div class="log-line">{{ item.value }}</div>
              </template>
            </n-virtual-list>
            <EmptyState v-else description="暂无日志" />
          </div>
        </n-collapse-item>
      </n-collapse>

      <template #footer>
        <n-space justify="end">
          <n-button
            v-if="canCancelDeploy"
            type="error"
            :loading="cancelLoading"
            @click="cancelCurrentDeployment"
          >
            取消安装
          </n-button>
        </n-space>
      </template>
    </n-card>

    <!-- 实例状态条 -->
    <n-card
      v-else
      size="small"
      class="instance-hero oc-gateway-card"
      :bordered="true"
    >
      <div class="hero-row">
        <div class="hero-main">
          <div class="status-indicator" :class="gatewayIndicatorClass">
            <span class="status-dot" aria-hidden="true" />
          </div>
          <div class="hero-text">
            <div class="hero-title">
              OpenClaw
              <n-tag v-if="hasInstance" type="success" size="small">已安装</n-tag>
              <n-tag v-else-if="hasDiscoveredInstall" type="warning" size="small">检测到安装</n-tag>
              <n-tag v-else size="small" type="default">未安装</n-tag>
            </div>
            <n-text depth="3" class="hero-sub">
              <template v-if="hasInstance && currentDeployment">
                v{{ currentDeployment.version || gatewayStore.version || '—' }}
                · {{ installMethodLabel(currentDeployment.installMethod) }}
                <template v-if="currentDeployment.port"> · 端口 {{ currentDeployment.port }}</template>
              </template>
              <template v-else-if="hasDiscoveredInstall">
                本机已存在 OpenClaw，可关联后直接使用 Gateway 与 Agent
              </template>
              <template v-else>完成安装后即可在本机运行 Gateway 与 Agent</template>
            </n-text>
            <n-text v-if="hasInstance && currentDeployment?.workDir" depth="3" class="hero-path">
              {{ currentDeployment.workDir }}
            </n-text>
          </div>
        </div>

        <n-space wrap align="center">
          <n-text v-if="gatewayStore.lastRefreshedLabel" depth="3" class="status-refresh-hint">
            {{ gatewayStore.lastRefreshedLabel }} 更新
          </n-text>
          <n-button
            v-if="!gatewayStore.processOnline && gatewayPort"
            type="primary"
            size="small"
            :loading="gatewayStore.isStartupInProgress || gatewayLoading || gatewayStore.isStarting"
            :disabled="gatewayStore.isStartupInProgress"
            @click="handleStartGateway()"
          >
            <template #icon><play-outline /></template>
            启动 Gateway
          </n-button>
          <n-button
            v-else-if="gatewayStore.processOnline"
            type="error"
            size="small"
            secondary
            :loading="gatewayLoading || gatewayStore.isStopping"
            @click="handleStopGateway()"
          >
            停止 Gateway
          </n-button>
          <n-button
            v-if="gatewayStore.portReady && gatewayEndpoint"
            size="small"
            @click="openControlUI"
          >
            打开 Control UI
          </n-button>
        </n-space>
      </div>

      <!-- Gateway 运行状态 -->
      <div v-if="hasInstance" class="gateway-status-panel">
        <div class="gateway-status-head">
          <n-text strong>Gateway 运行状态</n-text>
          <n-tag size="small" :type="gatewayMainTagType" round>
            {{ gatewayStore.statusLabel }}
          </n-tag>
        </div>

        <div v-if="gatewayStore.isStartupInProgress" class="gateway-startup-progress">
          <n-progress
            type="line"
            :percentage="gatewayStore.startupProgress"
            :height="8"
            :border-radius="4"
            :show-indicator="false"
          />
          <n-text depth="3" class="gateway-startup-label">{{ gatewayStore.startupMessage }}</n-text>
        </div>

        <div class="status-chips">
          <div class="status-chip" :class="processChipClass">
            <span class="chip-label">进程</span>
            <span class="chip-value">{{ processChipLabel }}</span>
          </div>
          <div class="status-chip" :class="portChipClass">
            <span class="chip-label">端口</span>
            <span class="chip-value">{{ portChipLabel }}</span>
          </div>
          <div class="status-chip" :class="rpcChipClass">
            <span class="chip-label">RPC</span>
            <span class="chip-value">{{ rpcChipLabel }}</span>
          </div>
        </div>

        <n-alert
          v-if="gatewayStore.message && !gatewayStore.isStartupInProgress"
          type="warning"
          :show-icon="true"
          class="status-alert"
        >
          {{ gatewayStore.message }}
        </n-alert>

        <n-space v-if="gatewayStore.processOnline" :size="20" wrap class="hero-meta">
          <n-text depth="3">
            PID
            <n-text strong>{{ gatewayPid ?? '—' }}</n-text>
          </n-text>
          <n-text v-if="gatewayUptime" depth="3">
            运行
            <n-text strong>{{ gatewayUptime }}</n-text>
          </n-text>
          <n-text v-if="gatewayStore.startTime" depth="3">
            启动于
            <n-text strong>{{ gatewayStore.startTime }}</n-text>
          </n-text>
          <n-text v-if="gatewayEndpoint" depth="3">
            访问
            <n-a :href="gatewayControlUrl" target="_blank">{{ gatewayEndpoint }}</n-a>
          </n-text>
          <n-text v-if="gatewayStore.version" depth="3">
            Gateway
            <n-text strong>v{{ gatewayStore.version }}</n-text>
          </n-text>
        </n-space>
        <n-text v-else depth="3" class="gateway-stopped-hint">
          Gateway 未运行。点击「启动 Gateway」将以后台服务方式长期运行；关闭本工具后 Gateway 仍保持运行，下次打开会自动连接。
        </n-text>
      </div>

      <template v-else-if="currentDeployment">
        <n-divider style="margin: 12px 0" />
        <n-space :size="20" wrap class="hero-meta">
          <n-text depth="3">
            安装时间
            <n-text strong>{{ currentDeployment.createdAt || '—' }}</n-text>
          </n-text>
        </n-space>
      </template>
    </n-card>

    <!-- 检测到已有安装 -->
    <n-card
      v-if="hasDiscoveredInstall && !isDeploying && !showWizard"
      size="small"
      class="discover-card"
      title="检测到本机已有 OpenClaw"
    >
      <n-text depth="3" style="display: block; margin-bottom: 12px">
        {{ discoveredInstall?.message }}
      </n-text>
      <n-descriptions label-placement="left" size="small" :column="1" class="discover-details">
        <n-descriptions-item v-if="discoveredInstall?.version" label="版本">
          {{ discoveredInstall.version }}
        </n-descriptions-item>
        <n-descriptions-item v-if="discoveredInstall?.commandResolvedPath || discoveredInstall?.commandPath" label="命令">
          <n-text code>{{ discoveredInstall?.commandResolvedPath || discoveredInstall?.commandPath }}</n-text>
        </n-descriptions-item>
        <n-descriptions-item v-if="discoveredInstall?.configPath" label="配置">
          <n-text code>{{ discoveredInstall.configPath }}</n-text>
        </n-descriptions-item>
        <n-descriptions-item v-if="discoveredInstall?.workDir" label="工作目录">
          <n-text code>{{ discoveredInstall.workDir }}</n-text>
        </n-descriptions-item>
        <n-descriptions-item v-if="discoveredInstall?.gatewayPort" label="Gateway 端口">
          {{ discoveredInstall.gatewayPort }}
        </n-descriptions-item>
      </n-descriptions>
      <n-space class="discover-actions" :size="12">
        <n-button type="primary" :loading="linkLoading" @click="handleLinkExisting">
          <template #icon><link-outline /></template>
          关联现有安装
        </n-button>
        <n-button @click="openDeployWizard">
          <template #icon><cloud-download-outline /></template>
          重新安装
        </n-button>
      </n-space>
    </n-card>

    <!-- 未安装引导 -->
    <EmptyState
      v-if="!hasInstance && !hasDiscoveredInstall && !isDeploying && !showWizard"
      class="empty-guide"
      description="尚未安装 OpenClaw，点击下方按钮开始本机单实例安装"
    >
      <template #actions>
        <n-button type="primary" @click="openDeployWizard">
          <template #icon><cloud-download-outline /></template>
          开始安装
        </n-button>
      </template>
    </EmptyState>

    <!-- 双栏：实例信息 + 环境 -->
    <n-grid
      v-if="(hasInstance || hasDiscoveredInstall || showEnvAlways) && !showWizard && !isDeploying"
      :cols="2"
      :x-gap="16"
      :y-gap="16"
      responsive="screen"
      class="section-grid"
    >
      <n-grid-item>
        <n-card size="small" title="实例配置">
          <template #header-extra>
            <n-button text type="primary" size="tiny" @click="openDeployWizard">
              重新部署
            </n-button>
          </template>

          <n-skeleton v-if="instanceLoading" text :repeat="4" />

          <n-descriptions
            v-else-if="currentDeployment"
            label-placement="left"
            size="small"
            :column="1"
          >
            <n-descriptions-item label="部署 ID">
              <n-text code>{{ currentDeployment.id }}</n-text>
            </n-descriptions-item>
            <n-descriptions-item label="OpenClaw 版本">
              {{ currentDeployment.version || '—' }}
            </n-descriptions-item>
            <n-descriptions-item label="安装方式">
              <n-tag :type="currentDeployment.installMethod === 'npm' ? 'info' : 'warning'" size="small">
                {{ installMethodLabel(currentDeployment.installMethod) }}
              </n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="Gateway 端口">
              {{ currentDeployment.port ?? deployConfig.gatewayPort }}
            </n-descriptions-item>
            <n-descriptions-item label="工作目录">
              <n-text code>{{ currentDeployment.workDir || '默认' }}</n-text>
            </n-descriptions-item>
            <n-descriptions-item label="Gateway 状态">
              <n-space :size="6" align="center">
                <n-tag :type="gatewayMainTagType" size="small">
                  {{ gatewayStore.statusLabel }}
                </n-tag>
                <n-tag v-if="gatewayStore.wsConnected" size="tiny" type="success">RPC 已连接</n-tag>
                <n-tag v-else-if="gatewayStore.processOnline" size="tiny" type="warning">RPC 未连接</n-tag>
              </n-space>
            </n-descriptions-item>
            <n-descriptions-item label="部署状态">
              <n-tag :type="getDeployStatusType(currentDeployment.status)">
                {{ getDeployStatusLabel(currentDeployment.status) }}
              </n-tag>
            </n-descriptions-item>
          </n-descriptions>

          <n-space v-if="hasInstance" class="instance-actions" :size="8">
            <n-button size="small" @click="openConfigDir">
              <template #icon><folder-open-outline /></template>
              配置目录
            </n-button>
            <n-button size="small" @click="viewDocumentation">
              <template #icon><book-outline /></template>
              文档
            </n-button>
            <n-button
              size="small"
              type="error"
              secondary
              :loading="deleteLoading"
              @click="handleRemoveInstance"
            >
              清除安装记录
            </n-button>
          </n-space>
        </n-card>
      </n-grid-item>

      <n-grid-item>
        <n-card size="small" title="运行环境">
          <template #header-extra>
            <n-space :size="8">
              <n-tag :type="envSummaryTagType" size="small">
                {{ checkPassCount }}/{{ environmentCheck.length }} 通过
              </n-tag>
              <n-button size="tiny" quaternary :loading="checkLoading" @click="refreshCheck">
                <template #icon><refresh-outline /></template>
              </n-button>
              <n-button
                size="tiny"
                type="primary"
                :disabled="!hasCheckFailures"
                :loading="fixLoading"
                @click="showFixDialog = true"
              >
                修复
              </n-button>
            </n-space>
          </template>

          <n-skeleton v-if="checkLoading && environmentCheck.length === 0" text :repeat="5" />

          <div v-else class="env-check-grid">
            <div
              v-for="(check, index) in environmentCheck"
              :key="index"
              class="env-check-item"
              :class="check.status"
            >
              <span class="env-check-icon">{{ getStatusIcon(check.status) }}</span>
              <div class="env-check-body">
                <n-text strong style="font-size: 13px">{{ check.checkName }}</n-text>
                <n-text depth="3" style="font-size: 12px">{{ check.message }}</n-text>
              </div>
            </div>
          </div>

          <n-alert v-if="hasCheckFailures" type="warning" class="mt-3" :show-icon="false">
            存在未通过项，安装前请先修复或点击「修复」。
          </n-alert>
        </n-card>
      </n-grid-item>
    </n-grid>

    <!-- 内联安装向导 -->
    <n-card v-if="showWizard && !isDeploying" size="small" class="wizard-card" title="安装向导">
      <n-steps :current="currentStep" :status="stepStatus" class="deploy-steps" size="small">
        <n-step title="环境检测" />
        <n-step title="安装配置" />
        <n-step title="确认安装" />
      </n-steps>

      <div class="step-content">
        <!-- 步骤 0 -->
        <div v-if="currentStep === 0" class="step-panel">
          <n-list v-if="environmentCheck.length" hoverable size="small">
            <n-list-item v-for="(check, index) in environmentCheck" :key="index">
              <template #prefix>
                <n-tag :type="getStatusType(check.status)" size="small">
                  {{ getStatusIcon(check.status) }}
                </n-tag>
              </template>
              <n-thing :title="check.checkName">
                <template #description>
                  <div>{{ check.message }}</div>
                  <n-text
                    v-if="check.suggestion"
                    type="info"
                    depth="3"
                    style="font-size: 12px; margin-top: 4px"
                  >
                    建议: {{ check.suggestion }}
                  </n-text>
                </template>
              </n-thing>
            </n-list-item>
          </n-list>
          <n-skeleton v-else text :repeat="6" />

          <n-alert v-if="hasCheckFailures" type="error" title="环境检测未通过" class="mt-3" />

          <n-space justify="end" class="step-actions">
            <n-button @click="closeDeployWizard">取消</n-button>
            <n-button
              type="primary"
              :disabled="hasCheckFailures"
              :loading="checkLoading"
              @click="currentStep = 1"
            >
              下一步
            </n-button>
          </n-space>
        </div>

        <!-- 步骤 1 -->
        <div v-if="currentStep === 1" class="step-panel">
          <n-form :model="deployConfig" :rules="configRules" label-placement="top">
            <n-form-item label="安装来源" path="installSource">
              <n-radio-group v-model:value="deployConfig.installSource">
                <n-space>
                  <n-radio value="npm">npm 全局安装（推荐）</n-radio>
                  <n-radio value="github">GitHub 源码安装</n-radio>
                </n-space>
              </n-radio-group>
            </n-form-item>

            <n-grid :cols="2" :x-gap="16">
              <n-grid-item>
                <n-form-item label="Gateway 端口" path="gatewayPort">
                  <n-input-number
                    v-model:value="deployConfig.gatewayPort"
                    :min="1024"
                    :max="65535"
                    style="width: 100%"
                  />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="工作目录">
                  <n-input v-model:value="deployConfig.workDir" placeholder="留空使用默认目录" />
                </n-form-item>
              </n-grid-item>
            </n-grid>

            <n-collapse v-if="systemInfo" class="mt-2">
              <n-collapse-item title="本机系统信息" name="sys">
                <n-descriptions label-placement="left" size="small" :column="2">
                  <n-descriptions-item label="系统">{{ systemInfo.os }}</n-descriptions-item>
                  <n-descriptions-item label="CPU">{{ systemInfo.cpu }}</n-descriptions-item>
                  <n-descriptions-item label="内存">{{ systemInfo.memory }}</n-descriptions-item>
                  <n-descriptions-item label="磁盘">{{ systemInfo.disk }}</n-descriptions-item>
                  <n-descriptions-item label="Node">{{ systemInfo.nodeVersion }}</n-descriptions-item>
                  <n-descriptions-item label="Python">{{ systemInfo.pythonVersion }}</n-descriptions-item>
                </n-descriptions>
              </n-collapse-item>
            </n-collapse>
          </n-form>

          <n-space justify="space-between" class="step-actions">
            <n-button @click="currentStep = 0">上一步</n-button>
            <n-space>
              <n-button @click="closeDeployWizard">取消</n-button>
              <n-button type="primary" @click="currentStep = 2">下一步</n-button>
            </n-space>
          </n-space>
        </div>

        <!-- 步骤 2 确认 -->
        <div v-if="currentStep === 2" class="step-panel">
          <n-alert type="info" :show-icon="false" class="confirm-summary">
            即将在本机安装单个 OpenClaw 实例，安装过程可能需要数分钟。
          </n-alert>
          <n-descriptions label-placement="left" size="small" :column="1" class="mt-3">
            <n-descriptions-item label="安装来源">
              {{ deployConfig.installSource === 'npm' ? 'npm 全局安装' : 'GitHub 源码安装' }}
            </n-descriptions-item>
            <n-descriptions-item label="Gateway 端口">{{ deployConfig.gatewayPort }}</n-descriptions-item>
            <n-descriptions-item label="工作目录">
              {{ deployConfig.workDir?.trim() || '默认目录' }}
            </n-descriptions-item>
          </n-descriptions>

          <n-space justify="space-between" class="step-actions">
            <n-button @click="currentStep = 1">上一步</n-button>
            <n-space>
              <n-button @click="closeDeployWizard">取消</n-button>
              <n-button type="primary" :loading="deployLoading" @click="startDeployment">
                开始安装
              </n-button>
            </n-space>
          </n-space>
        </div>
      </div>
    </n-card>

    <!-- 安装成功摘要 -->
    <n-card
      v-if="showSuccessBanner"
      size="small"
      class="success-banner"
    >
      <n-result status="success" title="安装完成" description="OpenClaw 已就绪，可启动 Gateway 开始使用">
        <template #footer>
          <n-space justify="center">
            <n-button type="primary" @click="dismissSuccessBanner">知道了</n-button>
            <n-button v-if="!isGatewayRunning" @click="handleStartGateway(); dismissSuccessBanner()">
              启动 Gateway
            </n-button>
          </n-space>
        </template>
      </n-result>
    </n-card>

    <!-- 环境修复 -->
    <n-modal v-model:show="showFixDialog" :mask-closable="false">
      <n-card title="修复运行环境" style="width: 560px" :bordered="false" size="small">
        <div v-if="!activeFixId">
          <n-alert type="info" class="mb-3">将尝试自动修复以下检测失败项：</n-alert>
          <n-list hoverable size="small">
            <n-list-item v-for="(check, index) in failedChecks" :key="index">
              <template #prefix>
                <n-tag type="error" size="small">✗</n-tag>
              </template>
              <n-thing :title="check.checkName" :description="check.message" />
            </n-list-item>
          </n-list>
        </div>

        <div v-else>
          <n-alert :type="fixProgress?.stage === 'completed' ? 'success' : 'info'" class="mb-3">
            {{ fixProgress?.currentAction || '正在修复...' }}
          </n-alert>
          <n-progress
            type="line"
            :percentage="fixProgress?.percentage || 0"
            :color="getFixProgressColor(fixProgress?.percentage || 0)"
            :height="18"
            class="mb-3"
          />
          <div class="log-container" style="max-height: 180px">
            <n-virtual-list
              v-if="fixLogItems.length"
              ref="fixVirtualListRef"
              class="log-virtual-list"
              :items="fixLogItems"
              :item-size="22"
              :style="{ maxHeight: '156px' }"
            >
              <template #default="{ item }">
                <div class="log-line">{{ item.value }}</div>
              </template>
            </n-virtual-list>
          </div>
        </div>

        <template #footer>
          <n-space justify="end">
            <n-button v-if="!activeFixId" @click="showFixDialog = false">取消</n-button>
            <n-button v-if="!activeFixId" type="primary" :loading="fixLoading" @click="startFix">
              开始修复
            </n-button>
            <n-button
              v-if="activeFixId && fixProgress?.stage === 'completed'"
              type="primary"
              @click="handleFixComplete"
            >
              完成并重新检测
            </n-button>
            <n-button
              v-if="activeFixId && fixProgress?.stage === 'failed'"
              @click="showFixDialog = false"
            >
              关闭
            </n-button>
          </n-space>
        </template>
      </n-card>
    </n-modal>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'DeploymentView' });

import { ref, computed, onMounted, onActivated, nextTick, watch } from 'vue';
import { useMessage } from 'naive-ui';
import {
  NButton, NTag, NSpace, NCard, NList, NListItem, NThing,
  NModal, NForm, NFormItem, NProgress, NSkeleton,
  NAlert, NSteps, NStep, NRadioGroup, NRadio, NInputNumber, NDescriptions,
  NDescriptionsItem, NResult, NCollapse, NCollapseItem, NText,
  NGrid, NGridItem, NInput, NDivider, NA, NVirtualList,
} from 'naive-ui';
import type { VirtualListInst } from 'naive-ui';
import {
  RefreshOutline,
  PlayOutline, FolderOpenOutline, BookOutline,
  CloudDownloadOutline,
  LinkOutline,
} from '@vicons/ionicons5';
import HeaderToolbar from '@/components/common/HeaderToolbar.vue';
import EmptyState from '@/components/EmptyState.vue';
import { useDeploymentStore } from '@/stores/deployment';
import { useGatewayStore } from '@/stores/gateway';
import { deploymentProgressColor } from '@/utils/chartColors';
import type { DeploymentListItem, StartDeploymentRequest } from '@shared/types';

const message = useMessage();
const store = useDeploymentStore();
const gatewayStore = useGatewayStore();
const deployVirtualListRef = ref<VirtualListInst | null>(null);
const fixVirtualListRef = ref<VirtualListInst | null>(null);

const currentDeployment = ref<DeploymentListItem | null>(null);
const instanceLoading = ref(false);
const showWizard = ref(false);
const showSuccessBanner = ref(false);
const showEnvAlways = ref(true);

const currentStep = ref(0);
const stepStatus = ref<'process' | 'finish' | 'error' | 'wait'>('process');
const deployLoading = ref(false);
const cancelLoading = ref(false);
const deleteLoading = ref(false);
const linkLoading = computed(() => store.linkLoading);
const gatewayLoading = ref(false);

const showFixDialog = ref(false);
const fixLoading = ref(false);

const deployConfig = ref<StartDeploymentRequest>({
  installSource: 'npm',
  gatewayPort: 18789,
  workDir: '',
});

const configRules = {
  gatewayPort: [
    { required: true, message: '请输入端口号', trigger: 'blur' },
    { type: 'number' as const, min: 1024, max: 65535, message: '端口范围 1024-65535', trigger: 'blur' },
  ],
};

const environmentCheck = computed(() => store.environmentCheck);
const checkLoading = computed(() => store.checkLoading);
const hasCheckFailures = computed(() => store.hasCheckFailures);
const checkPassCount = computed(() => store.checkPassCount);
const deployProgress = computed(() => store.deployProgress);
const deployLogItems = computed(() =>
  (deployProgress.value?.logs ?? []).map((value, index) => ({ key: index, value })),
);
const systemInfo = computed(() => store.systemInfo);
const activeFixId = computed(() => store.activeFixId);
const fixProgress = computed(() => store.fixProgress);
const fixLogItems = computed(() =>
  (fixProgress.value?.logs ?? []).map((value, index) => ({ key: index, value })),
);
const failedChecks = computed(() => environmentCheck.value.filter((c) => c.status === 'fail'));

const isGatewayRunning = computed(() => gatewayStore.isHealthy);
const gatewayEndpoint = computed(() => gatewayStore.endpoint);
const gatewayControlUrl = computed(() => gatewayStore.controlUrl);
const gatewayPid = computed(() => gatewayStore.pid);
const gatewayUptime = computed(() => gatewayStore.uptime);

const gatewayMainTagType = computed(() => {
  if (gatewayStore.isHealthy) return 'success';
  if (gatewayStore.isStarting || gatewayStore.isStopping) return 'info';
  if (gatewayStore.isRunning && !gatewayStore.wsConnected) return 'warning';
  if (gatewayStore.isError) return 'error';
  return 'default';
});

const gatewayIndicatorClass = computed(() => {
  if (gatewayStore.isHealthy) return 'healthy';
  if (gatewayStore.isStarting || gatewayStore.isStopping) return 'pending';
  if (gatewayStore.isRunning || gatewayStore.isError) return 'warning';
  return 'offline';
});

const processChipClass = computed(() => {
  if (gatewayStore.wsConnected || gatewayStore.isRunning || gatewayStore.isStarting) return 'ok';
  if (gatewayStore.isError) return 'err';
  return 'off';
});

const portChipClass = computed(() => {
  if (gatewayStore.isHealthy) return 'ok';
  if (gatewayStore.portReady || gatewayStore.wsConnected) return 'warn';
  return 'off';
});

const rpcChipClass = computed(() => {
  if (gatewayStore.wsConnected) return 'ok';
  if (gatewayStore.processOnline) return 'warn';
  return 'off';
});

const processChipLabel = computed(() => {
  if (gatewayStore.isStarting) return '启动中';
  if (gatewayStore.isStopping) return '停止中';
  if (gatewayStore.wsConnected || gatewayStore.isRunning || gatewayStore.isStarting) return '运行中';
  if (gatewayStore.isError) return '异常';
  return '已停止';
});

const portChipLabel = computed(() => {
  if (gatewayStore.isHealthy) return `:${gatewayStore.port} 就绪`;
  if (gatewayStore.portReady || gatewayStore.wsConnected) return `:${gatewayStore.port} 监听中`;
  return '未监听';
});

const rpcChipLabel = computed(() => {
  if (gatewayStore.wsConnected) return '已连接';
  if (gatewayStore.processOnline) return '握手中';
  return '未连接';
});

const hasInstance = computed(
  () => currentDeployment.value?.status === 'completed',
);

const discoveredInstall = computed(() => store.installDiscovery);
const hasDiscoveredInstall = computed(
  () => !hasInstance.value && discoveredInstall.value?.installed === true,
);

const isDeploying = computed(() => {
  const stage = deployProgress.value?.stage;
  if (store.activeDeployId && stage) {
    return !['completed', 'failed', 'cancelled', 'not_found'].includes(stage);
  }
  return deployLoading.value;
});

const canCancelDeploy = computed(() => {
  const stage = deployProgress.value?.stage;
  return stage === 'checking' || stage === 'installing' || stage === 'configuring';
});

const gatewayPort = computed(() => {
  return (
    currentDeployment.value?.port
    ?? deployProgress.value?.summary?.gatewayPort
    ?? deployConfig.value.gatewayPort
  );
});

const pageRefreshing = ref(false);

const pageLoading = computed(
  () => pageRefreshing.value || instanceLoading.value,
);

const envSummaryTagType = computed(() => {
  if (hasCheckFailures.value) return 'warning';
  if (environmentCheck.value.length && checkPassCount.value === environmentCheck.value.length) {
    return 'success';
  }
  return 'default';
});

function installMethodLabel(method?: string) {
  if (method === 'github') return 'GitHub 源码';
  if (method === 'external') return '本机已有';
  return 'npm 全局';
}

function getStatusType(status: string) {
  switch (status) {
    case 'pass': return 'success';
    case 'fail': return 'error';
    case 'warn': return 'warning';
    default: return 'default';
  }
}

function getStatusIcon(status: string) {
  switch (status) {
    case 'pass': return '✓';
    case 'fail': return '✗';
    case 'warn': return '⚠';
    default: return '?';
  }
}

function getStageLabel(stage: string) {
  const map: Record<string, string> = {
    checking: '环境检测',
    installing: '安装中',
    configuring: '配置中',
    completed: '已完成',
    failed: '失败',
    cancelled: '已取消',
    not_found: '未找到',
  };
  return map[stage] || stage;
}

function getProgressStatusType(stage?: string) {
  switch (stage) {
    case 'completed': return 'success';
    case 'failed': return 'error';
    case 'cancelled': return 'warning';
    default: return 'info';
  }
}

function getProgressColor(percentage: number) {
  return deploymentProgressColor(percentage);
}

function getFixProgressColor(percentage: number) {
  return deploymentProgressColor(percentage);
}

function getDeployStatusType(status: string) {
  switch (status) {
    case 'completed': return 'success';
    case 'running': return 'info';
    case 'failed': return 'error';
    case 'cancelled': return 'warning';
    default: return 'default';
  }
}

function getDeployStatusLabel(status: string) {
  const map: Record<string, string> = {
    pending: '等待中',
    running: '进行中',
    completed: '已完成',
    failed: '失败',
    cancelled: '已取消',
  };
  return map[status] || status;
}

async function loadCurrentInstance() {
  instanceLoading.value = true;
  try {
    const current = await store.fetchCurrentDeployment();
    currentDeployment.value = current;
    if (currentDeployment.value?.status !== 'completed') {
      await store.discoverInstallation();
    }
    if (currentDeployment.value?.port) {
      deployConfig.value.gatewayPort = currentDeployment.value.port;
    }
    if (currentDeployment.value?.workDir) {
      deployConfig.value.workDir = currentDeployment.value.workDir;
    }
    if (currentDeployment.value?.installMethod) {
      deployConfig.value.installSource =
        currentDeployment.value.installMethod === 'github' ? 'github' : 'npm';
    } else if (discoveredInstall.value?.gatewayPort) {
      deployConfig.value.gatewayPort = discoveredInstall.value.gatewayPort;
    }
    if (!currentDeployment.value?.workDir && discoveredInstall.value?.workDir) {
      deployConfig.value.workDir = discoveredInstall.value.workDir;
    }
  } finally {
    instanceLoading.value = false;
  }
}

async function handleLinkExisting() {
  try {
    const linked = await store.linkExistingInstallation({
      workDir: discoveredInstall.value?.workDir,
      gatewayPort: discoveredInstall.value?.gatewayPort,
    });
    currentDeployment.value = linked;
    message.success('已关联本机 OpenClaw 安装');
    await refreshGatewayStatus();
  } catch (error: unknown) {
    const err = error as { message?: string };
    message.error(err.message || '关联失败');
  }
}

async function refreshGatewayStatus(force = false) {
  await gatewayStore.syncStatus(force);
}

async function refreshPage(forceEnvCheck = false) {
  pageRefreshing.value = true;
  try {
    await Promise.all([
      loadCurrentInstance(),
      refreshGatewayStatus(forceEnvCheck),
      store.checkEnvironment(forceEnvCheck),
    ]);
  } finally {
    pageRefreshing.value = false;
  }
}

async function refreshCheck() {
  await store.checkEnvironment(true);
}

function openDeployWizard() {
  if (hasInstance.value && !window.confirm('重新部署将重新执行安装流程，是否继续？')) {
    return;
  }
  showWizard.value = true;
  showSuccessBanner.value = false;
  currentStep.value = 0;
  stepStatus.value = 'process';
  store.checkEnvironment(false);
  store.fetchSystemInfo();
}

function closeDeployWizard() {
  showWizard.value = false;
  currentStep.value = 0;
}

function dismissSuccessBanner() {
  showSuccessBanner.value = false;
}

watch(
  () => deployProgress.value?.logs?.length ?? 0,
  (len, prevLen) => {
    if (!len || len === prevLen) return;
    nextTick(() => {
      deployVirtualListRef.value?.scrollTo({ index: len - 1, behavior: 'auto' });
    });
  },
);

watch(
  () => fixProgress.value?.logs?.length ?? 0,
  (len, prevLen) => {
    if (!len || len === prevLen) return;
    nextTick(() => {
      fixVirtualListRef.value?.scrollTo({ index: len - 1, behavior: 'auto' });
    });
  },
);

watch(
  () => deployProgress.value?.stage,
  (stage, prevStage) => {
    if (!stage || stage === prevStage) return;
    if (stage === 'completed') {
      stepStatus.value = 'finish';
      showWizard.value = false;
      showSuccessBanner.value = true;
      loadCurrentInstance();
    } else if (stage === 'failed' || stage === 'cancelled') {
      stepStatus.value = 'error';
      message.error(deployProgress.value?.currentAction || '安装失败');
    }
  },
);

async function startDeployment() {
  deployLoading.value = true;
  showWizard.value = false;
  try {
    const deployId = await store.startDeployment(deployConfig.value);
    if (deployId) {
      stepStatus.value = 'process';
      store.startDeployPolling();
    }
  } catch (error: unknown) {
    const err = error as { message?: string };
    message.error('启动安装失败: ' + (err.message || '未知错误'));
    showWizard.value = true;
    currentStep.value = 2;
  } finally {
    deployLoading.value = false;
  }
}

async function cancelCurrentDeployment() {
  cancelLoading.value = true;
  try {
    const cancelled = await store.cancelDeployment();
    if (cancelled) {
      message.success('安装已取消');
      store.resetDeployment();
    }
  } catch (error: unknown) {
    const err = error as { message?: string };
    message.error('取消失败: ' + (err.message || '未知错误'));
  } finally {
    cancelLoading.value = false;
  }
}

async function handleStartGateway(port?: number) {
  gatewayLoading.value = true;
  try {
    await gatewayStore.startAndPoll(port ?? gatewayPort.value);
    if (gatewayStore.isHealthy) {
      message.success('Gateway 已就绪');
    } else if (gatewayStore.isStarting || gatewayStore.wsConnected) {
      message.warning('Gateway 仍在启动中，请稍候或点击刷新查看状态');
    }
    await loadCurrentInstance();
  } catch (error: unknown) {
    const err = error as { message?: string };
    message.error('启动失败: ' + (err.message || '未知错误'));
  } finally {
    gatewayLoading.value = false;
  }
}

async function handleStopGateway() {
  gatewayLoading.value = true;
  try {
    await gatewayStore.stopAndHalt();
    message.success('Gateway 已停止');
    await loadCurrentInstance();
  } catch (error: unknown) {
    const err = error as { message?: string };
    message.error('停止失败: ' + (err.message || '未知错误'));
  } finally {
    gatewayLoading.value = false;
  }
}

async function handleRemoveInstance() {
  if (!currentDeployment.value) return;
  if (!window.confirm('确定清除本机安装记录？不会卸载已安装的 OpenClaw 文件，仅删除平台内的部署记录。')) {
    return;
  }
  deleteLoading.value = true;
  try {
    await store.deleteDeployment(currentDeployment.value.id);
    currentDeployment.value = null;
    message.success('记录已清除');
  } catch (error: unknown) {
    const err = error as { message?: string };
    message.error('清除失败: ' + (err.message || '未知错误'));
  } finally {
    deleteLoading.value = false;
  }
}

function openControlUI() {
  const url = gatewayControlUrl.value || gatewayEndpoint.value;
  if (url) {
    window.open(url, '_blank');
  } else {
    message.warning('Gateway 地址不可用');
  }
}

function openConfigDir() {
  const path =
    deployProgress.value?.summary?.configPath
    ?? deployProgress.value?.summary?.path
    ?? currentDeployment.value?.workDir;
  if (path) {
    message.info('配置目录: ' + path);
  } else {
    message.info('配置目录信息不可用');
  }
}

function viewDocumentation() {
  window.open('https://docs.openclaw.ai', '_blank');
}

async function startFix() {
  fixLoading.value = true;
  try {
    const fixId = await store.fixEnvironment({ fixItems: undefined });
    if (fixId) store.startFixPolling();
  } catch (error: unknown) {
    const err = error as { message?: string };
    message.error('启动修复失败: ' + (err.message || '未知错误'));
    showFixDialog.value = false;
  } finally {
    fixLoading.value = false;
  }
}

async function handleFixComplete() {
  store.resetFix();
  showFixDialog.value = false;
  await refreshCheck();
  message.success('环境已修复并重新检测');
}

let lastPageRefreshAt = 0;

async function initDeploymentPage() {
  await refreshPage();
  await Promise.all([
    store.resumeDeployPollingIfNeeded(),
    store.resumeFixPollingIfNeeded(),
  ]);
  if (!hasInstance.value) {
    await store.fetchSystemInfo();
  }
  lastPageRefreshAt = Date.now();
}

onMounted(() => {
  void initDeploymentPage();
});

onActivated(async () => {
  await Promise.all([
    store.resumeDeployPollingIfNeeded(),
    store.resumeFixPollingIfNeeded(),
  ]);
  if (Date.now() - lastPageRefreshAt > 30_000) {
    await refreshPage();
    lastPageRefreshAt = Date.now();
  }
});
</script>

<style scoped>
.discover-card {
  margin-bottom: 20px;
  border-left: 3px solid var(--n-warning-color);
}

.discover-details {
  margin-bottom: 12px;
}

.discover-actions {
  margin-top: 4px;
}

.instance-hero {
  margin-bottom: 20px;
}

.oc-gateway-card {
  border-left: 3px solid var(--oc-primary);
  box-shadow: var(--oc-shadow-card);
}

.hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.hero-main {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-width: 0;
}

.hero-icon-wrap {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--n-action-color);
  flex-shrink: 0;
}

.hero-icon-wrap.installed {
  background: color-mix(in srgb, var(--n-success-color) 12%, transparent);
}

/* Gateway 状态指示器 */
.status-indicator {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: color-mix(in srgb, var(--oc-muted) 12%, transparent);
}

.status-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--oc-muted);
}

.status-indicator.healthy {
  background: color-mix(in srgb, var(--oc-success) 14%, transparent);
}

.status-indicator.healthy .status-dot {
  background: var(--oc-success);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--oc-success) 25%, transparent);
}

.status-indicator.pending {
  background: color-mix(in srgb, var(--oc-primary) 12%, transparent);
}

.status-indicator.pending .status-dot {
  background: var(--oc-primary);
  animation: status-pulse 1.4s ease-in-out infinite;
}

.status-indicator.warning {
  background: color-mix(in srgb, var(--oc-warning) 14%, transparent);
}

.status-indicator.warning .status-dot {
  background: var(--oc-warning);
}

.status-indicator.offline .status-dot {
  background: var(--oc-muted);
}

@keyframes status-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.55;
    transform: scale(0.88);
  }
}

.status-refresh-hint {
  font-size: 12px;
}

.gateway-status-panel {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--n-border-color);
}

.gateway-status-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.gateway-startup-progress {
  margin-bottom: 12px;
}

.gateway-startup-label {
  display: block;
  margin-top: 6px;
  font-size: 12px;
}

.status-chips {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

.status-chip {
  padding: 10px 12px;
  border-radius: var(--oc-radius-md);
  border: 1px solid var(--n-border-color);
  background: var(--n-color-modal);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-chip.ok {
  border-color: color-mix(in srgb, var(--oc-success) 35%, transparent);
  background: color-mix(in srgb, var(--oc-success) 8%, transparent);
}

.status-chip.warn {
  border-color: color-mix(in srgb, var(--oc-warning) 35%, transparent);
  background: color-mix(in srgb, var(--oc-warning) 8%, transparent);
}

.status-chip.err {
  border-color: color-mix(in srgb, var(--oc-error) 35%, transparent);
  background: color-mix(in srgb, var(--oc-error) 8%, transparent);
}

.status-chip.off {
  opacity: 0.72;
}

.chip-label {
  font-size: 11px;
  color: var(--oc-muted, var(--n-text-color-3));
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.chip-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--oc-chat-msg-text, var(--n-text-color));
}

.status-alert {
  margin-bottom: 12px;
}

.gateway-stopped-hint {
  font-size: 12px;
  display: block;
  margin-top: 4px;
}

.hero-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 16px;
  margin-bottom: 4px;
}

.hero-sub,
.hero-path {
  font-size: 12px;
  display: block;
}

.hero-path {
  margin-top: 4px;
  word-break: break-all;
}

.hero-meta {
  font-size: 12px;
}

.hero-meta :deep(.n-text) {
  color: var(--oc-muted, var(--n-text-color-3));
}

.hero-meta :deep(.n-text strong) {
  color: var(--oc-chat-msg-text, var(--n-text-color));
}

@media (max-width: 640px) {
  .status-chips {
    grid-template-columns: 1fr;
  }
}

.section-grid {
  margin-bottom: 20px;
}

.empty-guide {
  margin: 24px 0 32px;
}

.deploy-active-card {
  margin-bottom: 20px;
  border-left: 3px solid var(--oc-primary);
}

.wizard-card {
  margin-bottom: 20px;
}

.deploy-steps {
  margin-bottom: 24px;
}

.step-panel {
  max-width: 720px;
}

.step-actions {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--n-border-color);
}

.confirm-summary {
  margin-bottom: 0;
}

.success-banner {
  margin-bottom: 20px;
}

.instance-actions {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--n-divider-color);
}

.env-check-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.env-check-item {
  display: flex;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--n-action-color);
}

.env-check-item.pass {
  border-left: 3px solid var(--n-success-color);
}

.env-check-item.fail {
  border-left: 3px solid var(--n-error-color);
}

.env-check-item.warn {
  border-left: 3px solid var(--n-warning-color);
}

.env-check-icon {
  font-size: 14px;
  line-height: 1.4;
  flex-shrink: 0;
}

.env-check-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.progress-section {
  margin-bottom: 12px;
}

.progress-info {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-container {
  background: var(--n-color-modal);
  border-radius: 6px;
  padding: 12px;
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.6;
}

.log-virtual-list {
  width: 100%;
}

.log-line {
  color: var(--n-text-color-2);
  margin-bottom: 2px;
  white-space: pre-wrap;
  word-break: break-all;
}

.mt-3 {
  margin-top: 12px;
}

.mb-3 {
  margin-bottom: 12px;
}
</style>
