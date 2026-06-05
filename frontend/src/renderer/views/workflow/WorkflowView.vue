<template>
  <div class="page workflow-page">
    <HeaderToolbar>
      <n-input
        v-model:value="wfName"
        placeholder="工作流名称"
        style="width: 200px"
        size="small"
      />
      <n-button size="small" @click="newWorkflow">
        <template #icon><n-icon><document-outline /></n-icon></template>
        新建
      </n-button>
      <n-button size="small" :loading="saving" @click="saveWorkflow">
        <template #icon><n-icon><save-outline /></n-icon></template>
        保存
      </n-button>
      <n-button size="small" :disabled="!hasWorkflow" @click="clearCanvas">
        <template #icon><n-icon><trash-outline /></n-icon></template>
        清空
      </n-button>
    </HeaderToolbar>

    <div class="page-body">
    <!-- 主体 -->
    <div class="wf-layout">
      <!-- 左侧节点面板 -->
      <n-card size="small" class="wf-palette" :bordered="true">
        <template #header><n-text strong>节点</n-text></template>
        <div class="palette-list">
          <div
            v-for="nt in nodeTypes"
            :key="nt.type"
            class="palette-item"
            :class="`nt-${nt.type}`"
            draggable="true"
            @dragstart="onDragStart($event, nt.type)"
          >
            <n-icon size="18"><component :is="nt.icon" /></n-icon>
            <span>{{ nt.label }}</span>
          </div>
        </div>
      </n-card>

      <!-- 中央画布 -->
      <div class="wf-canvas" ref="canvasWrapper">
        <VueFlow
          v-if="showFlow"
          id="wf-flow"
          :nodes="flowNodes"
          :edges="flowEdges"
          :default-viewport="{ zoom: 0.8 }"
          :min-zoom="0.3"
          :max-zoom="2"
          @nodes-change="onNodesChange"
          @edges-change="onEdgesChange"
          @drop="onDrop"
          @dragover.prevent="onDragOver"
          @node-click="onNodeClick"
          fit-view-on-init
        >
          <Background :gap="20" />
          <Controls showInteractive="false" />
          <MiniMap
            :node-color="getMiniMapColor"
            mask-color="rgba(0,0,0,0.08)"
            style="border: 1px solid var(--n-border-color)"
          />

          <!-- 自定义节点模板 -->
          <template #node-llm="nodeProps">
            <div class="custom-node node-llm" @click.stop="onNodeClick($event, nodeProps)">
              <div class="node-header">
                <n-icon size="16"><sparkles-outline /></n-icon>
                <span>{{ nodeProps.data.label }}</span>
              </div>
              <div class="node-body">
                <n-text depth="3" class="node-preview">
                  {{ nodeProps.data.config?.model || '未配置模型' }}
                </n-text>
              </div>
              <Handle type="target" :position="Position.Top" />
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>

          <template #node-tool="nodeProps">
            <div class="custom-node node-tool" @click.stop="onNodeClick($event, nodeProps)">
              <div class="node-header">
                <n-icon size="16"><construct-outline /></n-icon>
                <span>{{ nodeProps.data.label }}</span>
              </div>
              <div class="node-body">
                <n-text depth="3" class="node-preview">
                  {{ nodeProps.data.config?.tool || '未选择工具' }}
                </n-text>
              </div>
              <Handle type="target" :position="Position.Top" />
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>

          <template #node-input="nodeProps">
            <div class="custom-node node-input" @click.stop="onNodeClick($event, nodeProps)">
              <div class="node-header">
                <n-icon size="16"><code-outline /></n-icon>
                <span>{{ nodeProps.data.label }}</span>
              </div>
              <div class="node-body">
                <n-text depth="3" class="node-preview">输入节点</n-text>
              </div>
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>

          <template #node-output="nodeProps">
            <div class="custom-node node-output" @click.stop="onNodeClick($event, nodeProps)">
              <div class="node-header">
                <n-icon size="16"><exit-outline /></n-icon>
                <span>{{ nodeProps.data.label }}</span>
              </div>
              <div class="node-body">
                <n-text depth="3" class="node-preview">输出节点</n-text>
              </div>
              <Handle type="target" :position="Position.Top" />
            </div>
          </template>

          <template #node-condition="nodeProps">
            <div class="custom-node node-condition" @click.stop="onNodeClick($event, nodeProps)">
              <div class="node-header">
                <n-icon size="16"><git-branch-outline /></n-icon>
                <span>{{ nodeProps.data.label }}</span>
              </div>
              <div class="node-body">
                <n-text depth="3" class="node-preview">
                  {{ nodeProps.data.config?.condition || '条件' }}
                </n-text>
              </div>
              <Handle type="target" :position="Position.Top" />
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>

          <template #node-loop="nodeProps">
            <div class="custom-node node-loop" @click.stop="onNodeClick($event, nodeProps)">
              <div class="node-header">
                <n-icon size="16"><repeat-outline /></n-icon>
                <span>{{ nodeProps.data.label }}</span>
              </div>
              <div class="node-body">
                <n-text depth="3" class="node-preview">循环节点</n-text>
              </div>
              <Handle type="target" :position="Position.Top" />
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>

          <template #node-code="nodeProps">
            <div class="custom-node node-code" @click.stop="onNodeClick($event, nodeProps)">
              <div class="node-header">
                <n-icon size="16"><code-slash-outline /></n-icon>
                <span>{{ nodeProps.data.label }}</span>
              </div>
              <div class="node-body">
                <n-text depth="3" class="node-preview">代码节点</n-text>
              </div>
              <Handle type="target" :position="Position.Top" />
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>
        </VueFlow>
        <div v-if="!hasWorkflow" class="wf-empty">
          <n-empty description="从左侧拖拽节点到画布开始编排" />
        </div>
      </div>
    </div>
    </div>

    <!-- 节点配置弹窗 -->
    <n-modal
      v-model:show="showConfig"
      :title="`配置 - ${configNode?.label || ''}`"
      preset="card"
      style="width: 480px; max-width: 90vw"
    >
      <n-form label-placement="top" size="small">
        <n-form-item label="节点名称">
          <n-input v-model:value="configNodeLabel" />
        </n-form-item>
        <template v-if="configNode?.type === 'llm'">
          <n-form-item label="模型">
            <n-input v-model:value="configNodeModel" placeholder="如 deepseek-chat" />
          </n-form-item>
          <n-form-item label="System Prompt">
            <n-input
              v-model:value="configNodePrompt"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 6 }"
              placeholder="可选 System Prompt"
            />
          </n-form-item>
          <n-form-item label="温度">
            <n-input-number v-model:value="configNodeTemp" :min="0" :max="2" :step="0.1" />
          </n-form-item>
        </template>
        <template v-else-if="configNode?.type === 'tool'">
          <n-form-item label="工具名称">
            <n-input v-model:value="configNodeTool" placeholder="如 web_search" />
          </n-form-item>
        </template>
        <template v-else-if="configNode?.type === 'condition'">
          <n-form-item label="条件表达式">
            <n-input v-model:value="configNodeCondition" placeholder="如 {{output}} contains 'error'" />
          </n-form-item>
        </template>
        <template v-else-if="configNode?.type === 'code'">
          <n-form-item label="执行代码">
            <n-input
              v-model:value="configNodeCode"
              type="textarea"
              :autosize="{ minRows: 4, maxRows: 10 }"
              placeholder="JavaScript 代码..."
            />
          </n-form-item>
        </template>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showConfig = false">关闭</n-button>
          <n-button type="primary" @click="applyNodeConfig">应用</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, shallowRef } from 'vue';
import { useMessage } from 'naive-ui';
import {
  NCard, NButton, NIcon, NInput, NInputNumber, NSpace, NText, NEmpty, NModal, NForm, NFormItem, NSelect,
} from 'naive-ui';
import {
  DocumentOutline, SaveOutline, TrashOutline,
  SparklesOutline, ConstructOutline, CodeOutline, ExitOutline,
  GitBranchOutline, RepeatOutline, CodeSlashOutline,
} from '@vicons/ionicons5';
import {
  VueFlow, useVueFlow, Handle, Position,
  type Node, type Edge, type NodeProps, type Connection, type NodeChange, type EdgeChange,
} from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import { MiniMap } from '@vue-flow/minimap';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import '@vue-flow/controls/dist/style.css';
import '@vue-flow/minimap/dist/style.css';

import HeaderToolbar from '@/components/common/HeaderToolbar.vue';
import { useWorkflowStore } from '@/stores/workflow';
import type { NodeType } from '@shared/types';

// ---------- Types ----------
interface NodeTypeDef {
  type: NodeType;
  label: string;
  icon: any;
  color: string;
}

const nodeTypes: NodeTypeDef[] = [
  { type: 'input', label: '输入', icon: CodeOutline, color: '#0d9488' },
  { type: 'llm', label: 'LLM 调用', icon: SparklesOutline, color: '#6366f1' },
  { type: 'tool', label: '工具', icon: ConstructOutline, color: '#d97706' },
  { type: 'condition', label: '条件判断', icon: GitBranchOutline, color: '#dc2626' },
  { type: 'loop', label: '循环', icon: RepeatOutline, color: '#059669' },
  { type: 'code', label: '代码执行', icon: CodeSlashOutline, color: '#0891b2' },
  { type: 'output', label: '输出', icon: ExitOutline, color: '#475569' },
];

// ---------- State ----------
const message = useMessage();
const store = useWorkflowStore();
const canvasWrapper = ref<HTMLElement | null>(null);
const wfName = ref('未命名工作流');
const showConfig = ref(false);
const configNode = ref<any>(null);

// Node editing bindings
const configNodeLabel = ref('');
const configNodeModel = ref('');
const configNodePrompt = ref('');
const configNodeTemp = ref(0.7);
const configNodeTool = ref('');
const configNodeCondition = ref('');
const configNodeCode = ref('');

const showFlow = ref(true);

// ---------- Vue Flow ----------
const { project } = useVueFlow({ id: 'wf-flow' });

const flowNodes = computed<Node[]>(() => {
  const wf = store.currentWorkflow;
  if (!wf) return [];
  return wf.nodes.map((n) => ({
    id: n.id,
    type: n.type,
    position: n.position,
    data: {
      label: n.label,
      config: n.config || {},
    },
  }));
});

const flowEdges = computed<Edge[]>(() => {
  const wf = store.currentWorkflow;
  if (!wf) return [];
  return wf.edges.map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    label: e.label,
    animated: true,
    style: { stroke: 'var(--oc-primary, #0d9488)', strokeWidth: 2 },
  }));
});

const hasWorkflow = computed(() => {
  const wf = store.currentWorkflow;
  return wf && wf.nodes.length > 0;
});
const saving = computed(() => store.saving);
const wfStore = store; // alias

// ---------- Drag & Drop ----------
let nodeIdCounter = 0;

function onDragStart(event: DragEvent, type: NodeType) {
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/vnd.wf-node-type', type);
    event.dataTransfer.effectAllowed = 'move';
  }
}

function onDragOver(event: DragEvent) {
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move';
  }
}

function onDrop(event: DragEvent) {
  event.preventDefault();
  const type = event.dataTransfer?.getData('application/vnd.wf-node-type') as NodeType | undefined;
  if (!type || !store.currentWorkflow) return;

  const pane = canvasWrapper.value?.querySelector('.vue-flow') as HTMLElement | null;
  const rect = pane?.getBoundingClientRect() ?? canvasWrapper.value?.getBoundingClientRect();
  if (!rect) return;

  const position = project({
    x: event.clientX - rect.left,
    y: event.clientY - rect.top,
  });

  nodeIdCounter++;
  const nodeTypeDef = nodeTypes.find((nt) => nt.type === type);
  const newNode = {
    id: `node_${Date.now()}_${nodeIdCounter}`,
    type,
    label: nodeTypeDef?.label || type,
    position: { x: Math.round(position.x), y: Math.round(position.y) },
    config: {},
  };

  store.currentWorkflow.nodes.push(newNode as any);
  store.currentWorkflow.updatedAt = new Date().toISOString();
}

// ---------- Node/Edge Changes ----------
function onNodesChange(changes: NodeChange[]) {
  if (!store.currentWorkflow) return;
  // Apply changes to internal structure
  for (const change of changes) {
    if (change.type === 'position' && change.position) {
      const node = store.currentWorkflow.nodes.find((n) => n.id === change.id);
      if (node) {
        node.position = { x: Math.round(change.position.x), y: Math.round(change.position.y) };
      }
    }
    if (change.type === 'remove') {
      store.currentWorkflow.nodes = store.currentWorkflow.nodes.filter((n) => n.id !== change.id);
      store.currentWorkflow.edges = store.currentWorkflow.edges.filter(
        (e) => e.source !== change.id && e.target !== change.id
      );
    }
  }
  store.currentWorkflow.updatedAt = new Date().toISOString();
}

function onEdgesChange(changes: EdgeChange[]) {
  if (!store.currentWorkflow) return;
  for (const change of changes) {
    if (change.type === 'remove') {
      store.currentWorkflow.edges = store.currentWorkflow.edges.filter((e) => e.id !== change.id);
    }
    if (change.type === 'add' && 'source' in change && 'target' in change) {
      const conn = change as unknown as Connection;
      store.currentWorkflow.edges.push({
        id: `edge_${conn.source}_${conn.target}`,
        source: conn.source!,
        target: conn.target!,
      });
    }
  }
  store.currentWorkflow.updatedAt = new Date().toISOString();
}

// ---------- Node Click / Config ----------
function onNodeClick(event: MouseEvent, nodeProps: NodeProps) {
  const wfNode = store.currentWorkflow?.nodes.find((n) => n.id === nodeProps.id);
  if (!wfNode) return;

  configNode.value = wfNode;
  configNodeLabel.value = wfNode.label;
  configNodeModel.value = (wfNode.config?.model as string) || '';
  configNodePrompt.value = (wfNode.config?.systemPrompt as string) || '';
  configNodeTemp.value = (wfNode.config?.temperature as number) || 0.7;
  configNodeTool.value = (wfNode.config?.tool as string) || '';
  configNodeCondition.value = (wfNode.config?.condition as string) || '';
  configNodeCode.value = (wfNode.config?.code as string) || '';
  showConfig.value = true;
}

function applyNodeConfig() {
  if (!configNode.value || !store.currentWorkflow) return;

  configNode.value.label = configNodeLabel.value;
  const cfg: Record<string, any> = { ...configNode.value.config };

  if (configNode.value.type === 'llm') {
    cfg.model = configNodeModel.value;
    cfg.systemPrompt = configNodePrompt.value;
    cfg.temperature = configNodeTemp.value;
  } else if (configNode.value.type === 'tool') {
    cfg.tool = configNodeTool.value;
  } else if (configNode.value.type === 'condition') {
    cfg.condition = configNodeCondition.value;
  } else if (configNode.value.type === 'code') {
    cfg.code = configNodeCode.value;
  }

  configNode.value.config = cfg;
  store.currentWorkflow.updatedAt = new Date().toISOString();
  showConfig.value = false;
  message.success('配置已应用');
}

// ---------- Workflow Ops ----------
function newWorkflow() {
  store.newWorkflow(wfName.value || '未命名工作流');
  showFlow.value = false;
  nextTick(() => { showFlow.value = true; });
  message.success('已新建工作流');
}

function saveWorkflow() {
  if (!store.currentWorkflow) {
    store.newWorkflow(wfName.value || '未命名工作流');
  }
  store.currentWorkflow!.name = wfName.value;
  // In a real app, save to backend
  message.success('工作流已保存到本地');
}

function clearCanvas() {
  if (store.currentWorkflow) {
    store.currentWorkflow.nodes = [];
    store.currentWorkflow.edges = [];
    store.currentWorkflow.updatedAt = new Date().toISOString();
    showFlow.value = false;
    nextTick(() => { showFlow.value = true; });
  }
}

function getMiniMapColor(node: Node): string {
  const def = nodeTypes.find((nt) => nt.type === node.type);
  return def?.color || '#64748b';
}



// ---------- Init ----------
onMounted(() => {
  store.newWorkflow(wfName.value);
});
</script>

<style scoped>
.workflow-page .page-body {
  display: flex;
  flex-direction: column;
}

.wf-layout {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
}
.wf-palette {
  width: 160px;
  flex-shrink: 0;
  overflow-y: auto;
}
.palette-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--oc-radius-md, 10px);
  cursor: grab;
  font-size: 13px;
  transition: background 0.15s;
  user-select: none;
}
.palette-item:hover {
  background: var(--n-color-modal);
}
.palette-item:active {
  cursor: grabbing;
}
.wf-canvas {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--n-border-color);
  border-radius: var(--oc-radius-lg, 12px);
  overflow: hidden;
  position: relative;
  background: var(--n-color);
}
.wf-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
:deep(.vue-flow__node) {
  cursor: pointer;
}
:deep(.vue-flow__handle) {
  width: 10px;
  height: 10px;
  border: 2px solid var(--oc-primary, #0d9488);
  background: white;
}
:deep(.vue-flow__handle:hover) {
  transform: scale(1.3);
}
/* Custom nodes */
.custom-node {
  min-width: 160px;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  font-size: 13px;
  cursor: pointer;
}
.custom-node .node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-weight: 600;
  font-size: 12px;
  color: #fff;
}
.custom-node .node-body {
  padding: 8px 12px;
  background: var(--n-color);
}
.node-preview {
  font-size: 11px;
}
.node-llm .node-header { background: #6366f1; }
.node-tool .node-header { background: #d97706; }
.node-input .node-header { background: #0d9488; }
.node-output .node-header { background: #475569; }
.node-condition .node-header { background: #dc2626; }
.node-loop .node-header { background: #059669; }
.node-code .node-header { background: #0891b2; }
</style>
