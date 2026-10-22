import { Tag } from 'antd'

const RISK: Record<string, { color: string; text: string }> = {
  NONE: { color: 'default', text: '无' },
  SUSPECT: { color: 'gold', text: '需关注' },
  PROGRESSION: { color: 'orange', text: '进展' },
  SEVERE: { color: 'red', text: '重度风险' },
}

const STAGE: Record<string, { color: string; text: string }> = {
  NORMAL: { color: 'green', text: '稳定/正常' },
  SUSPECT: { color: 'gold', text: '可疑' },
  CLINICAL: { color: 'orange', text: '临床期' },
  ADVANCED: { color: 'red', text: '重度' },
  POST_CXL: { color: 'blue', text: '交联术后' },
  UNKNOWN: { color: 'default', text: '未分期' },
}

const PLAN: Record<string, { color: string; text: string }> = {
  PENDING: { color: 'blue', text: '待随访' },
  DONE: { color: 'green', text: '已随访' },
  OVERDUE: { color: 'red', text: '已逾期' },
  CLOSED: { color: 'default', text: '已关闭' },
}

export function RiskTag({ value }: { value?: string }) {
  const meta = RISK[value || 'NONE'] || RISK.NONE
  return <Tag color={meta.color}>{meta.text}</Tag>
}

export function StageTag({ value }: { value?: string }) {
  const meta = STAGE[value || 'UNKNOWN'] || STAGE.UNKNOWN
  return <Tag color={meta.color}>{meta.text}</Tag>
}

export function PlanStatusTag({ value }: { value?: string }) {
  const meta = PLAN[value || 'PENDING'] || PLAN.PENDING
  return <Tag color={meta.color}>{meta.text}</Tag>
}

export function eyeLabel(side?: string) {
  if (side === 'left') return '左眼'
  if (side === 'right') return '右眼'
  if (side === 'both') return '双眼'
  return side || '-'
}
