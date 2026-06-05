import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { Workflow, WorkflowNode, WorkflowEdge } from '@shared/types';

export const useWorkflowStore = defineStore('workflow', () => {
  const workflows = ref<Workflow[]>([]);
  const currentWorkflow = ref<Workflow | null>(null);
  const loading = ref(false);
  const saving = ref(false);

  function createBlank(name: string) {
    const now = new Date().toISOString();
    return {
      id: `wf_${Date.now()}`,
      name,
      description: '',
      nodes: [],
      edges: [],
      createdAt: now,
      updatedAt: now,
    };
  }

  function newWorkflow(name = '未命名工作流') {
    currentWorkflow.value = createBlank(name);
  }

  function selectWorkflow(wf: Workflow) {
    currentWorkflow.value = JSON.parse(JSON.stringify(wf));
  }

  function updateNodes(nodes: WorkflowNode[]) {
    if (currentWorkflow.value) {
      currentWorkflow.value.nodes = nodes;
      currentWorkflow.value.updatedAt = new Date().toISOString();
    }
  }

  function updateEdges(edges: WorkflowEdge[]) {
    if (currentWorkflow.value) {
      currentWorkflow.value.edges = edges;
      currentWorkflow.value.updatedAt = new Date().toISOString();
    }
  }

  return {
    workflows,
    currentWorkflow,
    loading,
    saving,
    createBlank,
    newWorkflow,
    selectWorkflow,
    updateNodes,
    updateEdges,
  };
});
