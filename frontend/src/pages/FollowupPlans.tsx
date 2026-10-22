import { useEffect, useState } from 'react'
import { Button, Card, Input, Select, Space, Table, Tooltip, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import http from '../api/client'
import { FollowupPlan } from '../api/types'
import { PlanStatusTag, RiskTag, StageTag } from '../components/StatusTag'

export default function FollowupPlans() {
  const navigate = useNavigate()
  const [list, setList] = useState<FollowupPlan[]>([])
  const [status, setStatus] = useState<string>()
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)

  const load = async (s = status, kw = keyword) => {
    setLoading(true)
    try {
      const data: any = await http.get('/followup/plans', {
        params: { status: s || undefined, keyword: kw || undefined },
      })
      setList(data || [])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const act = async (id: number, action: 'done' | 'close') => {
    await http.put(`/followup/plans/${id}/${action}`)
    message.success(action === 'done' ? '已标记为完成随访' : '计划已关闭')
    load()
  }

  const columns = [
    {
      title: '患者',
      dataIndex: 'patientName',
      width: 130,
      render: (name: string, row: FollowupPlan) => (
        <a onClick={() => navigate(`/patients/${row.patientId}`)}>{name}</a>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => <PlanStatusTag value={v} />,
    },
    {
      title: '分期',
      dataIndex: 'stageCode',
      width: 110,
      render: (v: string) => <StageTag value={v} />,
    },
    {
      title: '风险',
      dataIndex: 'riskFlag',
      width: 100,
      render: (v: string) => <RiskTag value={v} />,
    },
    {
      title: '频率',
      dataIndex: 'frequencyMonths',
      width: 110,
      render: (v: number) => <span style={{ whiteSpace: 'nowrap' }}>每 {v} 个月</span>,
    },
    { title: '最近检查', dataIndex: 'lastExamDate', width: 120 },
    {
      title: '下次随访',
      dataIndex: 'nextDueDate',
      width: 130,
      render: (v: string, row: FollowupPlan) => (
        <span style={{ color: row.status === 'OVERDUE' ? '#cf1322' : undefined, fontWeight: row.status === 'OVERDUE' ? 600 : 400 }}>
          {v || '-'}
        </span>
      ),
    },
    {
      title: '制定依据',
      dataIndex: 'basis',
      ellipsis: true,
      render: (v: string) => (
        <Tooltip title={v}>
          <span style={{ color: '#52606d' }}>{v}</span>
        </Tooltip>
      ),
    },
    {
      title: '操作',
      width: 170,
      render: (_: any, row: FollowupPlan) => (
        <Space size={4}>
          {(row.status === 'PENDING' || row.status === 'OVERDUE') && (
            <Button size="small" type="link" onClick={() => act(row.planId, 'done')}>
              已随访
            </Button>
          )}
          {row.status !== 'CLOSED' && (
            <Button size="small" type="link" danger onClick={() => act(row.planId, 'close')}>
              关闭
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const overdueCount = list.filter((p) => p.status === 'OVERDUE').length

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-title">随访计划</h2>
          <div className="page-subtitle">
            新增检查后系统自动调整计划；逾期患者置顶，当前逾期 {overdueCount} 人
          </div>
        </div>
        <Space>
          <Input.Search
            allowClear
            placeholder="搜索患者姓名"
            style={{ width: 200 }}
            onSearch={(v) => {
              setKeyword(v)
              load(status, v)
            }}
          />
          <Select
            allowClear
            placeholder="全部状态"
            style={{ width: 130 }}
            options={[
              { value: 'OVERDUE', label: '已逾期' },
              { value: 'PENDING', label: '待随访' },
              { value: 'DONE', label: '已随访' },
              { value: 'CLOSED', label: '已关闭' },
            ]}
            onChange={(v) => {
              setStatus(v)
              load(v, keyword)
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => load()}>
            刷新
          </Button>
        </Space>
      </div>

      <Card className="soft-card" bordered={false}>
        <Table
          rowKey="planId"
          loading={loading}
          columns={columns as any}
          dataSource={list}
          rowClassName={(r) => (r.status === 'OVERDUE' ? 'overdue-row' : '')}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条计划` }}
        />
      </Card>
    </div>
  )
}
