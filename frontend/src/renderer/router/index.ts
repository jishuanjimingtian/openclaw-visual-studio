import { createRouter, createWebHashHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: {
      title: '仪表盘',
      icon: 'grid-outline',
      description: 'OpenClaw 本地控制台 · Gateway 与关键指标',
    },
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/chat/ChatView.vue'),
    meta: {
      title: 'OpenClaw 对话',
      icon: 'chatbox-outline',
      description: '与 Agent 实时对话，左侧切换会话',
    },
  },
  {
    path: '/sessions',
    name: 'Sessions',
    component: () => import('@/views/sessions/SessionsView.vue'),
    meta: {
      title: '会话管理',
      icon: 'chatbubbles-outline',
      description: '浏览、检索 Gateway 会话，查看历史并同步到本地',
    },
  },
  {
    path: '/timers',
    name: 'Timers',
    component: () => import('@/views/timers/TimersView.vue'),
    meta: {
      title: '定时任务',
      icon: 'timer-outline',
      description: '管理 OpenClaw Gateway 定时任务、投递与执行历史',
    },
  },
  {
    path: '/workflow',
    name: 'Workflow',
    component: () => import('@/views/workflow/WorkflowView.vue'),
    meta: {
      title: '工作流编排',
      icon: 'git-branch-outline',
      description: '可视化拖拽编排 AI 工作流',
    },
  },
  {
    path: '/analytics',
    name: 'Analytics',
    component: () => import('@/views/analytics/AnalyticsView.vue'),
    meta: {
      title: '数据分析',
      icon: 'analytics-outline',
      description: 'Token 用量、模型分布与消息趋势',
    },
  },
  {
    path: '/config',
    name: 'Config',
    component: () => import('@/views/config/ConfigView.vue'),
    meta: {
      title: '配置中心',
      icon: 'settings-outline',
      description: '管理 Agent 角色、System Prompt 与运行时参数',
    },
  },
  {
    path: '/models',
    name: 'Models',
    component: () => import('@/views/models/ModelsView.vue'),
    meta: {
      title: '模型管理',
      icon: 'hardware-chip-outline',
      description: '模型市场、API Key 与 OpenClaw 配置同步',
    },
  },
  {
    path: '/marketplace',
    name: 'Marketplace',
    component: () => import('@/views/marketplace/MarketplaceView.vue'),
    meta: {
      title: 'Skill 市场',
      icon: 'storefront-outline',
      description: '从 ClawHub / GitHub 发现并安装 Skill',
    },
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/knowledge/KnowledgeView.vue'),
    meta: {
      title: '知识库',
      icon: 'library-outline',
      description: '长期记忆、每日笔记与知识图谱',
    },
  },
  {
    path: '/deployment',
    name: 'Deployment',
    component: () => import('@/views/deployment/DeploymentView.vue'),
    meta: {
      title: 'OpenClaw 部署',
      icon: 'cloud-download-outline',
      description: '本机安装、运行环境与 Gateway 管理',
    },
  },
  {
    path: '/monitor',
    name: 'Monitor',
    component: () => import('@/views/monitor/MonitorView.vue'),
    meta: {
      title: '系统监控',
      icon: 'pulse-outline',
      description: '系统资源、会话状态与性能指标',
    },
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/settings/SettingsView.vue'),
    meta: {
      title: '设置',
      icon: 'cog-outline',
      description: '应用偏好、Gateway 连接与数据管理',
    },
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

router.onError((error) => {
  console.error('[router]', error);
});

export default router;
