import type { CronDelivery, CronJob, CronJobCreatePayload, CronPayload, CronSchedule } from '@shared/types';

export type ScheduleKind = 'at' | 'every' | 'cron';
export type SessionTargetMode = 'main' | 'isolated' | 'current' | 'custom';
export type DeliveryMode = 'announce' | 'webhook' | 'none';
export type PayloadMode = 'systemEvent' | 'message';

export interface TimerFormModel {
  name: string;
  description: string;
  enabled: boolean;
  agentId: string;
  clearAgent: boolean;
  scheduleKind: ScheduleKind;
  atValue: string;
  everyMinutes: number;
  cronExpr: string;
  tz: string;
  deleteAfterRun: boolean;
  sessionMode: SessionTargetMode;
  customSessionId: string;
  payloadMode: PayloadMode;
  systemEvent: string;
  wakeMode: 'now' | 'next-heartbeat';
  message: string;
  model: string;
  thinking: string;
  lightContext: boolean;
  deliveryMode: DeliveryMode;
  channel: string;
  to: string;
  threadId: string;
  webhookUrl: string;
  bestEffortDeliver: boolean;
  timeoutSeconds: number | null;
  includeSkippedAlerts: boolean;
}

export function defaultFormModel(): TimerFormModel {
  return {
    name: '',
    description: '',
    enabled: true,
    agentId: '',
    clearAgent: false,
    scheduleKind: 'cron',
    atValue: '',
    everyMinutes: 60,
    cronExpr: '0 9 * * *',
    tz: '',
    deleteAfterRun: true,
    sessionMode: 'isolated',
    customSessionId: '',
    payloadMode: 'message',
    systemEvent: '',
    wakeMode: 'now',
    message: '',
    model: '',
    thinking: '',
    lightContext: false,
    deliveryMode: 'announce',
    channel: '',
    to: '',
    threadId: '',
    webhookUrl: '',
    bestEffortDeliver: false,
    timeoutSeconds: null,
    includeSkippedAlerts: false,
  };
}

export function formFromJob(job: CronJob): TimerFormModel {
  const m = defaultFormModel();
  m.name = job.name ?? '';
  m.description = job.description ?? '';
  m.enabled = job.enabled !== false;
  m.agentId = job.agentId ?? '';

  const sk = job.schedule?.kind as ScheduleKind | undefined;
  if (sk === 'at' || sk === 'every' || sk === 'cron') m.scheduleKind = sk;
  if (job.schedule?.at) m.atValue = String(job.schedule.at);
  if (job.schedule?.everyMs) m.everyMinutes = Math.round(Number(job.schedule.everyMs) / 60_000);
  if (job.schedule?.expr) m.cronExpr = String(job.schedule.expr);
  if (job.schedule?.tz) m.tz = String(job.schedule.tz);

  const st = job.sessionTarget ?? 'isolated';
  if (st === 'main' || st === 'isolated' || st === 'current') {
    m.sessionMode = st;
  } else if (st.startsWith('session:')) {
    m.sessionMode = 'custom';
    m.customSessionId = st.slice('session:'.length);
  }

  const payload = job.payload ?? {};
  if (payload.systemEvent || job.sessionTarget === 'main') {
    m.payloadMode = 'systemEvent';
    m.systemEvent = String(payload.systemEvent ?? payload.text ?? '');
  } else {
    m.payloadMode = 'message';
    m.message = String(payload.message ?? payload.text ?? '');
  }
  if (payload.model) m.model = String(payload.model);
  if (payload.thinking) m.thinking = String(payload.thinking);
  if (payload.lightContext) m.lightContext = true;

  const delivery = job.delivery;
  if (delivery?.mode === 'webhook' || delivery?.webhookUrl) {
    m.deliveryMode = 'webhook';
    m.webhookUrl = String(delivery.webhookUrl ?? '');
  } else if (delivery?.mode === 'none') {
    m.deliveryMode = 'none';
  } else {
    m.deliveryMode = 'announce';
    m.channel = String(delivery?.channel ?? '');
    m.to = String(delivery?.to ?? '');
    if (delivery?.threadId != null) m.threadId = String(delivery.threadId);
    if (delivery?.bestEffort) m.bestEffortDeliver = true;
  }

  return m;
}

export function buildSchedule(model: TimerFormModel): CronSchedule {
  if (model.scheduleKind === 'at') {
    return { kind: 'at', at: model.atValue.trim() };
  }
  if (model.scheduleKind === 'every') {
    return { kind: 'every', everyMs: Math.max(1, model.everyMinutes) * 60_000 };
  }
  const s: CronSchedule = { kind: 'cron', expr: model.cronExpr.trim() };
  if (model.tz.trim()) s.tz = model.tz.trim();
  return s;
}

export function buildSessionTarget(model: TimerFormModel): string {
  if (model.sessionMode === 'custom') {
    const id = model.customSessionId.trim();
    return id.startsWith('session:') ? id : `session:${id}`;
  }
  return model.sessionMode;
}

export function buildPayload(model: TimerFormModel): CronPayload {
  if (model.sessionMode === 'main' || model.payloadMode === 'systemEvent') {
    const p: CronPayload = {
      kind: 'systemEvent',
      text: model.systemEvent.trim(),
      systemEvent: model.systemEvent.trim(),
    };
    return p;
  }
  const p: CronPayload = {
    kind: 'agentTurn',
    message: model.message.trim(),
    text: model.message.trim(),
  };
  if (model.model.trim()) p.model = model.model.trim();
  if (model.thinking.trim()) p.thinking = model.thinking.trim();
  if (model.lightContext) p.lightContext = true;
  return p;
}

export function buildDelivery(model: TimerFormModel): CronDelivery | undefined {
  if (model.sessionMode === 'main') return undefined;
  if (model.deliveryMode === 'webhook') {
    return { mode: 'webhook', webhookUrl: model.webhookUrl.trim() };
  }
  if (model.deliveryMode === 'none') {
    return { mode: 'none' };
  }
  const d: CronDelivery = { mode: 'announce' };
  if (model.channel.trim()) d.channel = model.channel.trim();
  if (model.to.trim()) d.to = model.to.trim();
  if (model.threadId.trim()) d.threadId = model.threadId.trim();
  if (model.bestEffortDeliver) d.bestEffort = true;
  return d;
}

export function buildCreatePayload(model: TimerFormModel): CronJobCreatePayload {
  const body: CronJobCreatePayload = {
    name: model.name.trim(),
    schedule: buildSchedule(model),
    sessionTarget: buildSessionTarget(model),
    payload: buildPayload(model),
    enabled: model.enabled,
  };
  if (model.description.trim()) body.description = model.description.trim();
  if (model.agentId.trim() && !model.clearAgent) body.agentId = model.agentId.trim();
  const delivery = buildDelivery(model);
  if (delivery) body.delivery = delivery;
  if (model.sessionMode === 'main') {
    body.wakeMode = model.wakeMode;
  }
  if (model.scheduleKind === 'at' && model.deleteAfterRun) {
    body.deleteAfterRun = true;
  }
  if (model.timeoutSeconds != null && model.timeoutSeconds > 0) {
    body.timeoutSeconds = model.timeoutSeconds;
  }
  if (model.includeSkippedAlerts) {
    body.failureAlert = { includeSkipped: true };
  }
  return body;
}

export function buildPatch(model: TimerFormModel, original: CronJob): Record<string, unknown> {
  const patch: Record<string, unknown> = {
    name: model.name.trim(),
    enabled: model.enabled,
    schedule: buildSchedule(model),
    sessionTarget: buildSessionTarget(model),
    payload: buildPayload(model),
  };
  if (model.description.trim()) patch.description = model.description.trim();
  if (model.clearAgent) patch.agentId = null;
  else if (model.agentId.trim()) patch.agentId = model.agentId.trim();

  const delivery = buildDelivery(model);
  if (delivery) patch.delivery = delivery;

  if (model.sessionMode === 'main') {
    patch.wakeMode = model.wakeMode;
  }

  if (model.timeoutSeconds != null && model.timeoutSeconds > 0) {
    patch.timeoutSeconds = model.timeoutSeconds;
  }

  void original;
  return patch;
}

export function validateForm(model: TimerFormModel): string | null {
  if (!model.name.trim()) return '请填写任务名称';
  if (model.scheduleKind === 'at' && !model.atValue.trim()) return '请填写一次性执行时间';
  if (model.scheduleKind === 'every' && model.everyMinutes < 1) return '间隔至少 1 分钟';
  if (model.scheduleKind === 'cron' && !model.cronExpr.trim()) return '请填写 Cron 表达式';
  if (model.sessionMode === 'custom' && !model.customSessionId.trim()) return '请填写自定义 Session ID';
  if (model.sessionMode === 'main' || model.payloadMode === 'systemEvent') {
    if (!model.systemEvent.trim()) return '主会话任务需填写系统事件内容';
  } else if (!model.message.trim()) {
    return '隔离任务需填写 Agent 提示词';
  }
  if (model.deliveryMode === 'webhook') {
    if (!model.webhookUrl.trim()) return '请填写 Webhook URL';
    if (model.channel.trim() || model.to.trim()) {
      return 'Webhook 投递不能与频道/目标同时使用';
    }
  }
  if (model.deliveryMode === 'announce' && model.channel.trim() && model.to.trim()) {
    const ch = model.channel.trim().toLowerCase();
    const to = model.to.trim().toLowerCase();
    if (ch === 'whatsapp' && to.startsWith('telegram:')) {
      return '频道与目标前缀不一致（whatsapp + telegram）';
    }
  }
  return null;
}

export const FORM_TEMPLATES = {
  mainReminder: (): TimerFormModel => ({
    ...defaultFormModel(),
    name: '主会话提醒',
    scheduleKind: 'at',
    atValue: '20m',
    sessionMode: 'main',
    payloadMode: 'systemEvent',
    systemEvent: '提醒：请检查待办事项。',
    wakeMode: 'now',
    deliveryMode: 'none',
    deleteAfterRun: true,
  }),
  isolatedDaily: (): TimerFormModel => ({
    ...defaultFormModel(),
    name: '隔离日报',
    cronExpr: '0 9 * * *',
    scheduleKind: 'cron',
    sessionMode: 'isolated',
    message: '汇总过去 24 小时项目进展，用中文简要输出。',
    deliveryMode: 'none',
  }),
  webhookDigest: (): TimerFormModel => ({
    ...defaultFormModel(),
    name: 'Webhook 摘要',
    cronExpr: '0 18 * * 1-5',
    sessionMode: 'isolated',
    message: '生成今日部署摘要（JSON 格式）。',
    deliveryMode: 'webhook',
    webhookUrl: 'https://example.invalid/openclaw/cron',
  }),
};

export function scheduleSummary(job: CronJob): string {
  const s = job.schedule;
  if (!s?.kind) return '—';
  if (s.kind === 'at') return `一次性 ${s.at ?? ''}`;
  if (s.kind === 'every') {
    const mins = s.everyMs ? Math.round(Number(s.everyMs) / 60_000) : '?';
    return `每 ${mins} 分钟`;
  }
  const expr = s.expr ?? '?';
  return s.tz ? `${expr} (${s.tz})` : expr;
}

export function statusTagType(status?: string): 'default' | 'info' | 'success' | 'warning' | 'error' {
  switch (status) {
    case 'running': return 'info';
    case 'ok': return 'success';
    case 'error': return 'error';
    case 'skipped': return 'warning';
    case 'disabled': return 'default';
    default: return 'default';
  }
}
