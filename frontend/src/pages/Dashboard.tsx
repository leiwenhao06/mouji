import { useEffect, useState } from 'react'
import { Button, Card, Col, Row, Table, Tag } from 'antd'
import { ReloadOutlined, TeamOutlined, FileDoneOutlined, WarningOutlined, RiseOutlined } from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import http from '../api/client'
import { RiskTag, StageTag, eyeLabel } from '../components/StatusTag'

const STAGE_LABEL: Record<string, string> = {
  NORMAL: '稳定/正常',
  SUSPECT: '可疑',
  CLINICAL: '临床期',
  ADVANCED: '重度',
  POST_CXL: '交联术后',
  UNKNOWN: '未分期',
}

const RISK_LABEL: Record<string, string> = {
  NONE: '无',
  SUSPECT: '需关注',
  PROGRESSION: '进展',
  SEVERE: '重度风险',
}

export default function Dashboard() {
  const [data, setData] = useState<any>({})
  const [loading, setLoading] = useState(false)

  const load = async (refresh = false) => {
    setLoading(true)
    try {
      const d: any = refresh ? await http.post('/dashboard/refresh') : await http.get('/dashboard/overview')
      setData(d || {})
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    const timer = window.setInterval(() => load(), 60000)
    return () => window.clearInterval(timer)
  }, [])

  const summary = data.summary || {}
  const pie = (list: any[], labels: Record<string, string>, title: string) => ({
    title: { text: title, left: 8, top: 4, textStyle: { fontSize: 14, color: '#102a43' } },
    tooltip: { trigger: 'item', formatter: '{b}：{c} 人（{d}%）' },
    legend: {
      bottom: 0,
      left: 'center',
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 8,
      textStyle: { fontSize: 11 },
    },
    series: [
      {
        type: 'pie',
        radius: ['44%', '66%'],
        center: ['50%', '45%'],
        label: { show: false },
        labelLine: { show: false },
        data: (list || []).map((x) => ({
          name: labels[x.name] || x.name || '未填',
          value: Number(x.value),
        })),
      },
    ],
  })

  const bar = (list: any[], title: string, nameKey: string, color: string) => ({
    title: { text: title, left: 8, top: 4, textStyle: { fontSize: 14, color: '#102a43' } },
    tooltip: { trigger: 'axis' },
    grid: { left: 44, right: 16, top: 46, bottom: 34 },
    xAxis: { type: 'category', data: (list || []).map((x) => x[nameKey]) },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        barMaxWidth: 26,
        itemStyle: { color, borderRadius: [4, 4, 0, 0] },
        data: (list || []).map((x) => Number(x.value)),
      },
    ],
  })

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-title">数据看板</h2>
          <div className="page-subtitle">
            随访运行态概览 · 每 60 秒自动刷新
            {data.cacheAgeSeconds != null ? ` · 缓存 ${data.cacheAgeSeconds} 秒前` : ''}
          </div>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => load(true)}>
          刷新缓存
        </Button>
      </div>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card className="soft-card stat-tile" bordered={false}>
            <div className="stat-icon" style={{ background: '#eaf2ff', color: '#1677ff' }}>
              <TeamOutlined />
            </div>
            <div>
              <div className="stat-label">在管患者</div>
              <div className="stat-value">{summary.totalPatients || 0}</div>
            </div>
          </Card>
        </Col>
        <Col span={6}>
          <Card className="soft-card stat-tile" bordered={false}>
            <div className="stat-icon" style={{ background: '#e6fffb', color: '#13c2c2' }}>
              <FileDoneOutlined />
            </div>
            <div>
              <div className="stat-label">本月检查</div>
              <div className="stat-value">{summary.examsThisMonth || 0}</div>
            </div>
          </Card>
        </Col>
        <Col span={6}>
          <Card className="soft-card stat-tile" bordered={false}>
            <div className="stat-icon" style={{ background: '#fff1f0', color: '#cf1322' }}>
              <WarningOutlined />
            </div>
            <div>
              <div className="stat-label">逾期随访</div>
              <div className="stat-value" style={{ color: '#cf1322' }}>
                {summary.overduePlans || 0}
              </div>
            </div>
          </Card>
        </Col>
        <Col span={6}>
          <Card className="soft-card stat-tile" bordered={false}>
            <div className="stat-icon" style={{ background: '#fff7e6', color: '#fa8c16' }}>
              <RiseOutlined />
            </div>
            <div>
              <div className="stat-label">进展风险患者</div>
              <div className="stat-value" style={{ color: '#d46b08' }}>
                {summary.progressionPatients || 0}
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card className="soft-card chart-card" bordered={false}>
            <ReactECharts
              option={pie(data.stageDistribution, STAGE_LABEL, '分期分布')}
              style={{ height: 280 }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card className="soft-card chart-card" bordered={false}>
            <ReactECharts
              option={pie(data.riskDistribution, RISK_LABEL, '风险分布')}
              style={{ height: 280 }}
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card className="soft-card chart-card" bordered={false}>
            <ReactECharts
              option={bar(data.monthlyExams, '近 12 个月检查量', 'month', '#1677ff')}
              style={{ height: 280 }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={8}>
          <Card className="soft-card" bordered={false} title="年龄分布">
            <ReactECharts
              option={bar(data.ageDistribution, '', 'name', '#36cfc9')}
              style={{ height: 260 }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card className="soft-card" bordered={false} title="逾期随访 TOP10">
            <Table
              size="small"
              rowKey="planId"
              pagination={false}
              dataSource={data.overduePlans || []}
              columns={[
                { title: '患者', dataIndex: 'patientName', width: 90 },
                { title: '下次随访', dataIndex: 'nextDueDate', width: 110 },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 80,
                  render: () => <Tag color="red">已逾期</Tag>,
                },
              ]}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card className="soft-card" bordered={false} title="进展风险患者">
            <Table
              size="small"
              rowKey={(r: any) => `${r.patient_id}-${r.exam_date}`}
              pagination={false}
              dataSource={data.progressionTop || []}
              columns={[
                { title: '患者', dataIndex: 'name', width: 80 },
                {
                  title: '眼别',
                  dataIndex: 'eye_side',
                  width: 60,
                  render: (v: string) => eyeLabel(v),
                },
                {
                  title: '风险',
                  dataIndex: 'risk_flag',
                  width: 90,
                  render: (v: string) => <RiskTag value={v} />,
                },
                {
                  title: 'KMax',
                  dataIndex: 'front_kmax',
                  width: 70,
                  render: (v: any) => (v == null ? '-' : Number(v).toFixed(1)),
                },
              ]}
              scroll={{ y: 180 }}
            />
          </Card>
        </Col>
      </Row>

      <Card className="soft-card" bordered={false} style={{ marginTop: 16 }}>
        <span className="page-subtitle">
          AI 建议：累计生成 {data.suggestionStats?.total || 0} 条，采纳 {data.suggestionStats?.adopted || 0} 条，
          平均耗时 {data.suggestionStats?.avg_duration_ms ? (data.suggestionStats.avg_duration_ms / 1000).toFixed(1) : 0} 秒。
          全部数据为程序生成的模拟数据。
        </span>
      </Card>
    </div>
  )
}
