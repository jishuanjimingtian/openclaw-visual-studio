// ============== API 通用类型 ==============

export interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
  timestamp: number;
}

export interface PageRequest {
  page: number;
  pageSize: number;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface Resp<T = unknown> extends ApiResponse<T> {}

// ============== OpenClaw 核心模型 ==============

export type MessageRole = 'system' | 'user' | 'assistant' | 'tool';

export interface Conversation {
  id: string;
  title: string;
  model: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
  tags: string[];
  archived: boolean;
  openclawSessionKey?: string;
  lastMessagePreview?: string;
  fromOpenClaw?: boolean;
}

export interface OpenClawSession {
  key: string;
  title: string;
  model?: string;
  modelProvider?: string;
  lastMessagePreview?: string;
  updatedAt?: number;
  totalTokens?: number;
  hasActiveRun?: boolean;
  kind?: string;
  channel?: string;
  sessionId?: string;
}

export interface OpenClawSessionsResult {
  gatewayConnected: boolean;
  gatewayPort?: number;
  gatewayWsUrl?: string;
  connectionHint?: string;
  defaultModel?: string | null;
  sessions: OpenClawSession[];
}

export type ChatPartType = 'text' | 'image' | 'file';

export interface TextPart {
  type: 'text';
  text: string;
}

export interface AttachmentPart {
  type: 'image' | 'file';
  attachmentId: string;
  name: string;
  mime: string;
  sizeBytes: number;
  width?: number;
  height?: number;
  thumbReady?: boolean;
}

export type ChatPart = TextPart | AttachmentPart;

export interface ChatMessage {
  role: string;
  content?: string;
  parts?: ChatPart[];
  timestamp?: number;
}

export interface UiChatMessage extends ChatMessage {
  id: string;
  streaming?: boolean;
  /** 本地乐观 UI：附件上传中 */
  uploadState?: 'uploading' | 'ready' | 'error';
  /** 本地预览 URL（发送后 revoke） */
  localPreviewUrl?: string;
}

export interface ChatAttachmentRef {
  attachmentId: string;
  name: string;
  mime: string;
  sizeBytes: number;
  width?: number;
  height?: number;
  thumbReady: boolean;
}

export interface AttachmentInitResult {
  uploadId: string;
  chunkSize: number;
  maxChunks: number;
  uploadedChunks: number[];
}

export interface OpenClawChatEvent {
  runId: string;
  sessionKey: string;
  state: 'delta' | 'final' | 'aborted' | 'error';
  text?: string;
  deltaText?: string;
  errorMessage?: string;
}

export interface OpenClawChatStatus {
  gatewayConnected: boolean;
  defaultSessionKey: string;
}

export interface OpenClawSessionUsage {
  sessionKey: string;
  totalTokens: number;
  inputTokens: number;
  outputTokens: number;
  totalCost?: number;
  fromGateway: boolean;
}

export interface ChatSendResult {
  runId: string;
  sessionKey: string;
}

export interface Message {
  id: string;
  conversationId: string;
  role: MessageRole;
  content: string;
  tokens?: number;
  createdAt: string;
}

export type SkillStatus = 'installed' | 'available' | 'update_available' | 'not_installed';

export interface Skill {
  id: string;
  name: string;
  version: string;
  author: string;
  description: string;
  source: 'ClawdHub' | 'GitHub';
  status: SkillStatus;
  installedAt?: string;
  rating: number;
  downloads: number;
  license: string;
  marketSlug?: string;
  installPath?: string;
}

export type SkillInstallScope = 'workspace' | 'global' | 'both';

/** 市场发现条目（ClawHub / GitHub） */
export interface MarketSkill {
  slug: string;
  name: string;
  version: string;
  author: string;
  description: string;
  source: 'ClawdHub' | 'GitHub';
  status: SkillStatus;
  rating: number;
  downloads: number;
  stars: number;
  license: string;
  homepageUrl?: string;
  updatedAt?: string;
  tags?: string[];
  installedId?: string;
  githubRepo?: string;
  installPath?: string;
  nameOriginal?: string;
  descriptionOriginal?: string;
  nameZh?: string;
  descriptionZh?: string;
  localized?: boolean;
}

export interface SkillInstallRequest {
  slug: string;
  source: string;
  version?: string;
  scope?: SkillInstallScope;
  name?: string;
  author?: string;
  description?: string;
  license?: string;
  rating?: number;
  downloads?: number;
  githubRepo?: string;
  /** 安装后将 SKILL.md 译为中文 */
  localizeToChinese?: boolean;
}

export interface SkillInstallResult {
  success: boolean;
  message: string;
  skillId?: string;
  installPath?: string;
  installPaths?: string[];
  openclawReady?: boolean;
  skillMdLocalized?: boolean;
  configPath?: string;
  workspacePath?: string;
  workspaceAutoConfigured?: boolean;
}

export interface SkillMarketPage {
  items: MarketSkill[];
  nextCursor?: string | null;
  totalCount: number;
  fromCache?: boolean;
  source?: string;
  sort?: string;
  locale?: string;
  localized?: boolean;
}

// ============== 数据分析新增类型 ==============

export interface AnalyticsOverview {
  totalMessages: number;
  totalConversations: number;
  totalTokens: number;
  todayMessages: number;
  todayTokens: number;
  activeModels: number;
}

export interface DailyTokenUsage {
  date: string;
  tokens: number;
  localTokens?: number;
  gatewayTokens?: number;
  messageCount: number;
}

export interface ModelUsage {
  model: string;
  sessionCount: number;
  percentage: number;
  totalTokens?: number;
  messageCount?: number;
  tokenPercentage?: number;
}

export interface TopSessionUsage {
  id: string;
  title: string;
  model: string;
  totalTokens: number;
  messageCount: number;
  updatedAt?: string | null;
  source: 'local' | 'gateway' | string;
  inputTokens?: number;
  outputTokens?: number;
  totalCost?: number | null;
}

export interface AnalyticsSourceBreakdown {
  gatewayConnected: boolean;
  rows: AnalyticsSourceBreakdownRow[];
}

export interface AnalyticsSourceBreakdownRow {
  metric: string;
  label: string;
  local: number;
  gateway: number;
  total: number;
}

export interface MessageTrend {
  date: string;
  messageCount: number;
}

export type DeploymentStatus = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';

export interface DeploymentTask {
  id: string;
  status: DeploymentStatus;
  progress: number;
  startTime: string;
  endTime?: string;
  log: string[];
}

export type ModelProvider =
  | 'openai'
  | 'claude'
  | 'ollama'
  | 'qwen'
  | 'deepseek'
  | 'google'
  | 'openrouter'
  | 'openclaw'
  | 'custom';

export interface ModelConfig {
  id: string;
  name: string;
  provider: ModelProvider;
  endpoint: string;
  enabled: boolean;
  apiKeyConfigured: boolean;
  /** 本地库 Key 脱敏预览，如 sk-45ed••••1a08f */
  apiKeyPreview?: string | null;
  /** openclaw.json 中对应 provider 是否已有 apiKey */
  apiKeyInOpenClaw?: boolean;
  modelRef?: string;
  openclawPrimary?: boolean;
  /** 是否已在 OpenClaw 本地配置中注册 */
  registeredInOpenClaw?: boolean;
}

/** OpenClaw 同步方式（保存时映射为 register / primary / apiKey 开关） */
export type OpenClawSyncMode = 'local' | 'register' | 'sync' | 'primary';

export interface OpenClawModelEntry {
  modelRef: string;
  provider: string;
  displayName: string;
  primary: boolean;
  apiKeyConfigured?: boolean;
}

export interface ModelConfigSaveRequest {
  name: string;
  provider: ModelProvider;
  endpoint: string;
  apiKey?: string;
  enabled?: boolean;
  /** @deprecated use registerInOpenClaw + setAsOpenClawPrimary */
  applyToOpenClaw?: boolean;
  registerInOpenClaw?: boolean;
  setAsOpenClawPrimary?: boolean;
  fallbackModelRefs?: string[];
  syncApiKeyToOpenClaw?: boolean;
  /** models.providers.qwen.baseUrl */
  openclawBaseUrl?: string;
}

export interface OpenClawEndpointOption {
  id: string;
  label: string;
  baseUrl: string;
  description?: string;
}

export interface OpenClawCatalogModel {
  modelRef: string;
  modelId: string;
  displayName: string;
  provider: string;
  uiProvider?: string;
  category?: string;
  tags?: string[];
  description?: string;
  contextWindow?: number;
  defaultBaseUrl?: string;
  configured?: boolean;
  configuredModelId?: string | null;
}

export interface ModelMarketCategory {
  id: string;
  label: string;
  description?: string;
}

export interface ModelMarketOverview {
  categories: ModelMarketCategory[];
  models: OpenClawCatalogModel[];
}

export interface SetOpenClawPrimaryRequest {
  modelRef: string;
  fallbackModelRefs?: string[];
}

export interface ModelTestResult {
  success: boolean;
  message: string;
  latencyMs?: number | null;
}

export interface OpenClawModelOverview {
  configPath: string;
  primaryModelRef: string | null;
  fallbackModelRefs?: string[];
  qwenBaseUrl?: string | null;
  deepseekBaseUrl?: string | null;
  models: OpenClawModelEntry[];
}

/** Electron 主进程采集的本机资源占用（%） */
export interface LocalSystemMetrics {
  cpu: number;
  memory: number;
  disk: number;
}

export interface SystemMetrics {
  cpu: number;
  memory: number;
  disk: number;
  uptime: number;
  sessionCount: number;
  /** 今日 Token 总量（个数） */
  tokenUsage: number;
}

export interface DashboardStats {
  totalConversations: number;
  activeModels: number;
  totalModels: number;
  activeDeployments: number;
  totalDeployments: number;
  installedSkills: number;
  totalSkills: number;
  totalMessages: number;
  messagesToday: number;
}

export interface DashboardGateway {
  status: string;
  port?: number;
  endpoint?: string;
  pid?: number;
  wsConnected: boolean;
  message?: string;
}

export interface DashboardRecentConversation {
  id: string;
  title: string;
  model: string;
  updatedAt: string;
  messageCount: number;
  lastMessagePreview?: string;
}

export interface DashboardRecentDeployment {
  id: string;
  status: string;
  progress: number;
  startTime: string;
  installMethod?: string;
  port?: number;
}

export interface DashboardOverview {
  stats: DashboardStats;
  metrics: SystemMetrics;
  gateway: DashboardGateway;
  openclawConfigPath?: string;
  primaryModelRef?: string | null;
  recentConversations: DashboardRecentConversation[];
  recentDeployments: DashboardRecentDeployment[];
}

// ============== 配置中心类型 ==============

export interface AgentConfigOverview {
  configPath: string;
  configExists: boolean;
  workspace?: string;
  timeoutSeconds?: number;
  primaryModelRef?: string;
  fallbackModelRefs?: string[];
  toolsProfile?: string;
  systemPrompt?: string;
  promptSource?: 'none' | 'config' | 'bootstrap';
  bootstrapFile?: string;
  bootstrapLineCount?: number;
  bootstrapPreview?: string;
  defaultTemperature?: number;
  defaultMaxTokens?: number;
  temperatureFromConfig?: boolean;
  maxTokensFromConfig?: boolean;
}

export interface BootstrapSyncResult {
  targetField: string;
  bootstrapFile: string;
  charCount: number;
  lineCount: number;
  message: string;
}

export interface AgentRole {
  id: string;
  name: string;
  systemPrompt: string;
  promptSource?: 'none' | 'config' | 'bootstrap';
  bootstrapFile?: string;
  bootstrapLineCount?: number;
  bootstrapPreview?: string;
  temperature: number;
  maxTokens: number;
  temperatureFromConfig?: boolean;
  maxTokensFromConfig?: boolean;
  defaultModel: string;
  /** OpenClaw 默认 Agent，不可删除 */
  builtin: boolean;
  /** 数据来自 openclaw.json */
  openclaw: boolean;
  /** agents.list[].default */
  defaultAgent?: boolean;
  workspace?: string;
  /** 保存全局默认时写入 agents.defaults.timeoutSeconds */
  timeoutSeconds?: number;
  updatedAt: number;
}

// ============== 一键部署新增类型 ==============

export interface DeploymentListItem {
  id: string;
  createdAt: string;
  version: string;
  installMethod: string;
  port: number | null;
  workDir: string;
  status: string;
  gatewayRunning: boolean;
  summary?: DeploySummary;
}

export interface EnvironmentCheckResult {
  checkName: string;
  status: 'pass' | 'fail' | 'warn';
  message: string;
  suggestion?: string;
}

export interface StartDeploymentRequest {
  installSource: 'npm' | 'github';
  gatewayPort: number;
  workDir?: string;
  autoFix?: boolean;
}

export interface StartDeploymentResponse {
  deployId: string;
  message: string;
}

export interface OpenClawInstallDiscovery {
  installed: boolean;
  commandPath?: string;
  commandResolvedPath?: string;
  version?: string;
  configPath?: string;
  workDir?: string;
  gatewayPort?: number;
  installMethod?: string;
  message?: string;
}

export interface LinkExistingInstallRequest {
  workDir?: string;
  gatewayPort?: number;
}

export interface DeployProgress {
  stage: 'checking' | 'installing' | 'configuring' | 'completed' | 'failed' | 'cancelled' | 'not_found';
  percentage: number;
  currentAction: string;
  logs: string[];
  logTotal?: number;
  summary?: DeploySummary;
}

export interface DeploySummary {
  version: string;
  path: string;
  gatewayPort: number;
  configPath: string;
  workDir: string;
  installSource: string;
}

export interface SystemInfo {
  os: string;
  cpu: string;
  memory: string;
  disk: string;
  nodeVersion: string;
  pythonVersion: string;
  gitVersion: string;
}

// ============== Gateway 管理 ==============

export interface GatewayInfo {
  version?: string;
  port?: number;
  status: string;
  pid?: number;
  configPath?: string;
  workDir?: string;
  startTime?: string;
  uptime?: string;
  memoryUsage?: string;
  endpoint?: string;
  message?: string;
  wsConnected?: boolean;
  /** vs-process | daemon | external */
  managedBy?: string;
  serviceInstalled?: boolean;
  /** 异步启动阶段 */
  startupPhase?: GatewayStartupPhase;
  /** 启动进度 0-100 */
  startupProgress?: number;
}

export type GatewayStartupPhase =
  | 'idle'
  | 'launching'
  | 'port_wait'
  | 'rpc_connect'
  | 'running'
  | 'failed';

// ============== 环境修复新增类型 ==============

export interface EnvironmentFixRequest {
  fixItems?: string[];
  autoFix?: boolean;
}

export interface EnvironmentFixResult {
  itemName: string;
  status: 'success' | 'fail' | 'skipped';
  detail: string;
  afterFixStatus: 'pass' | 'fail' | 'warn';
  afterFixMessage: string;
}

export interface FixProgress {
  fixId: string;
  stage: 'queued' | 'running' | 'completed' | 'failed' | 'not_found';
  percentage: number;
  totalItems: number;
  completedItems: number;
  successCount: number;
  failCount: number;
  currentAction: string;
  results: EnvironmentFixResult[];
  logs: string[];
  logTotal?: number;
}

// ============== 知识库 ==============

export type KnowledgeFileKind =
  | 'hub'
  | 'daily'
  | 'dream'
  | 'dream_shard'
  | 'soul'
  | 'user'
  | 'agents'
  | 'unknown';
export type KnowledgeNodeKind =
  | 'hub'
  | 'daily'
  | 'dream'
  | 'dream_shard'
  | 'soul'
  | 'user'
  | 'agents'
  | 'chunk'
  | 'topic';
export type KnowledgeEdgeKind = 'temporal' | 'link' | 'promote' | 'tag' | 'semantic';

export interface KnowledgeFile {
  path: string;
  kind: KnowledgeFileKind | string;
  sizeBytes: number;
  updatedAt?: string;
  lineCount?: number;
}

export interface KnowledgeFileContent {
  path: string;
  content: string;
  kind: string;
  sizeBytes: number;
  updatedAt?: string;
}

export interface KnowledgeWorkspaceCandidate {
  path: string;
  memoryFileCount: number;
  source: string;
  configured: boolean;
  active: boolean;
}

export interface KnowledgeDiscoverResult {
  chosenWorkspacePath: string;
  resolutionSource: string;
  persistedToConfig: boolean;
  memoryFileCount: number;
  candidates: KnowledgeWorkspaceCandidate[];
  overview: KnowledgeOverview;
}

export interface KnowledgeOverview {
  workspacePath: string;
  configPath?: string;
  configuredWorkspacePath?: string;
  resolutionSource?: string;
  candidates?: KnowledgeWorkspaceCandidate[];
  workspaceAutoConfigured?: boolean;
  fileCount: number;
  memoryMdSizeBytes: number;
  dailyNoteCount: number;
  dreamsPresent: boolean;
  workspaceConfigCount?: number;
  dreamShardCount?: number;
  gatewayConnected: boolean;
  gatewayPort?: number;
  indexStatusSummary?: string;
  lastSyncedAt?: string;
}

export interface KnowledgeGraphNode {
  id: string;
  label: string;
  kind: KnowledgeNodeKind | string;
  path?: string;
  lineStart?: number;
  size?: number;
  tags?: string[];
  updatedAt?: string;
}

export interface KnowledgeGraphEdge {
  id: string;
  source: string;
  target: string;
  kind: KnowledgeEdgeKind | string;
  value?: number;
  label?: string;
}

export interface KnowledgeGraphMeta {
  workspacePath: string;
  generatedAt: string;
  fileCount: number;
  includeChunks?: boolean;
  dailyWindowDays?: number;
  hiddenDailyCount?: number;
}

export interface KnowledgeBootstrap {
  overview: KnowledgeOverview;
  files: KnowledgeFile[];
  graph?: KnowledgeGraph | null;
}

export interface KnowledgeGraph {
  nodes: KnowledgeGraphNode[];
  edges: KnowledgeGraphEdge[];
  meta: KnowledgeGraphMeta;
}

export interface KnowledgeGraphOverlay {
  highlightNodeIds: string[];
  edges: KnowledgeGraphEdge[];
}

export interface KnowledgeSearchHit {
  path: string;
  lineStart?: number;
  lineEnd?: number;
  snippet: string;
  score?: number;
  nodeId?: string;
}

export interface KnowledgeSearchResult {
  hits: KnowledgeSearchHit[];
  fallback: boolean;
  source: string;
  graphOverlay?: KnowledgeGraphOverlay;
}

export interface KnowledgeIndexStatus {
  gatewayConnected: boolean;
  available: boolean;
  summary?: string;
  raw?: unknown;
  error?: string;
}

export interface KnowledgeIndexRebuildResult {
  success: boolean;
  message: string;
  exitCode: number;
}

export type KnowledgeBrowseView = 'graph' | 'list';

// ============== OpenClaw 定时任务 (Cron) ==============

export type CronJobStatus =
  | 'disabled'
  | 'running'
  | 'ok'
  | 'error'
  | 'skipped'
  | 'idle'
  | string;

export type CronScheduleKind = 'at' | 'every' | 'cron';

export interface CronSchedule {
  kind?: CronScheduleKind;
  at?: string;
  everyMs?: number;
  expr?: string;
  tz?: string;
  staggerMs?: number;
  exact?: boolean;
  [key: string]: unknown;
}

export interface CronPayload {
  kind?: string;
  text?: string;
  message?: string;
  systemEvent?: string;
  model?: string;
  thinking?: string;
  tools?: string[];
  lightContext?: boolean;
  fallbacks?: string[];
  [key: string]: unknown;
}

export interface CronDelivery {
  mode?: 'announce' | 'webhook' | 'none' | string;
  channel?: string;
  to?: string;
  threadId?: string | number;
  account?: string;
  webhookUrl?: string;
  bestEffort?: boolean;
  failureDestination?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface CronJobState {
  lastRunStatus?: string;
  lastRunAtMs?: number;
  runningAtMs?: number;
  consecutiveErrors?: number;
  lastError?: string;
  [key: string]: unknown;
}

export interface CronJob {
  id: string;
  name?: string;
  description?: string;
  enabled?: boolean;
  status?: CronJobStatus;
  sessionTarget?: string;
  agentId?: string;
  schedule?: CronSchedule;
  payload?: CronPayload;
  delivery?: CronDelivery;
  state?: CronJobState;
  nextRunAtMs?: number;
  updatedAtMs?: number;
  createdAtMs?: number;
}

export interface CronGatewayMeta {
  gatewayConnected: boolean;
  gatewayPort?: number;
  gatewayWsUrl?: string;
  connectionHint?: string;
}

export interface CronListPage extends CronGatewayMeta {
  jobs: CronJob[];
  total?: number;
  limit?: number;
  offset?: number;
}

export interface CronStatusResult extends CronGatewayMeta {
  status: Record<string, unknown> | null;
}

export interface CronOverview extends CronGatewayMeta {
  schedulerStatus: Record<string, unknown> | null;
  jobs: CronJob[];
  total?: number;
  limit?: number;
  offset?: number;
}

export interface CronRunEntry {
  runId?: string;
  jobId?: string;
  status?: string;
  startedAtMs?: number;
  finishedAtMs?: number;
  durationMs?: number;
  error?: string;
  reason?: string;
  delivery?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface CronRunsPage extends CronGatewayMeta {
  jobId?: string;
  entries: CronRunEntry[];
  total?: number;
  limit?: number;
  offset?: number;
}

export interface CronListParams {
  limit?: number;
  offset?: number;
  query?: string;
  enabled?: 'all' | 'enabled' | 'disabled';
  sortBy?: 'nextRunAtMs' | 'updatedAtMs' | 'name';
  sortDir?: 'asc' | 'desc';
  includeDisabled?: boolean;
}

export type CronJobCreatePayload = Record<string, unknown>;

export interface CronRunResult {
  ok?: boolean;
  enqueued?: boolean;
  runId?: string;
  [key: string]: unknown;
}

// ============== 工作流引擎 ==============

export type NodeType = 'input' | 'output' | 'llm' | 'tool' | 'condition' | 'loop' | 'code';

export interface WorkflowNode {
  id: string;
  type: NodeType;
  label: string;
  position: { x: number; y: number };
  config: Record<string, unknown>;
}

export interface WorkflowEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
}

export interface Workflow {
  id: string;
  name: string;
  description: string;
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  createdAt: string;
  updatedAt: string;
}