import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  List,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import { RobotOutlined } from '@ant-design/icons'
import { useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import http from '../api/client'
import { Suggestion } from '../api/types'

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

export default function Suggestions() {
  const [searchParams] = useSearchParams()
  const [patients, setPatients] = useState<any[]>([])
  const [patientId, setPatientId] = useState<string | undefined>(searchParams.get('patientId') || undefined)
  const [generating, setGenerating] = useState(false)
  const [current, setCurrent] = useState<Suggestion>()
  const [advice, setAdvice] = useState<any>()
  const [history, setHistory] = useState<Suggestion[]>([])
  const [agent, setAgent] = useState<any>({})

  useEffect(() => {
    http.get('/patients', { params: { page: 1, size: 100 } }).then((d: any) => {
      setPatients(d.list || [])
    }).catch(() => undefined)
    http.get('/followup/agent-status').then(setAgent).catch(() => undefined)
  }, [])

  const loadHistory = async (pid = patientId) => {
    if (!pid) return
    const data: any = await http.get('/followup/suggestions', { params: { patientId: pid, limit: 20 } })
    setHistory(data || [])
  }

  useEffect(() => {
    loadHistory()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [patientId])

  const generate = async () => {
    if (!patientId) {
      message.warning('请先选择患者')
      return
    }
    setGenerating(true)
    setAdvice(undefined)
    setCurrent(undefined)
    try {
      const task: any = await http.post('/followup/suggestions', { patientId })
      let tries = 0
      while (tries < 90) {
        await sleep(1500)
        const s: any = await http.get(`/followup/suggestions/${task.requestId}`)
        if (s.status === 'SUCCESS') {
          setCurrent(s)
          try {
            setAdvice(JSON.parse(s.resultJson))
          } catch {
            message.error('建议结果解析失败')
          }
          loadHistory()
          break
        }
        if (s.status === 'FAILED') {
          message.error(s.errorMessage || '生成失败')
          break
        }
        tries += 1
      }
      if (tries >= 90) message.warning('生成超时，请稍后在历史记录中查看')
    } finally {
      setGenerating(false)
    }
  }

  const viewHistory = (s: Suggestion) => {
    setCurrent(s)
    try {
      setAdvice(JSON.parse(s.resultJson || '{}'))
    } catch {
      setAdvice(undefined)
    }
  }

  const adopt = async (adopted: boolean) => {
    if (!current) return
    await http.put(`/followup/suggestions/${current.suggestionId}/adoption`, { adopted })
    message.success(adopted ? '已采纳该建议' : '已标记为不采纳')
    setCurrent({ ...current, adopted })
    loadHistory()
  }

  const engineTag = useMemo(() => {
    if (!current) return null
    return current.engine === 'llm' ? (
      <Tag color="purple">大模型 + 指南守卫</Tag>
    ) : (
      <Tag color="blue">循证规则引擎（可降级）</Tag>
    )
  }, [current])

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-title">AI 随访建议</h2>
          <div className="page-subtitle">
            脱敏上下文 → 本地知识检索 → 循证草稿 → 大模型个体化（可选）→ 指南一致性守卫
          </div>
        </div>
        <Space>
          <Tag color={agent.llmHealthy ? 'green' : 'default'}>
            {agent.llmHealthy ? '大模型已连接' : '规则引擎模式'}
          </Tag>
          <Tag>知识库 {agent.knowledgeCount || 0} 条</Tag>
        </Space>
      </div>

      <Row gutter={16}>
        <Col span={8}>
          <Card className="soft-card" bordered={false} title="选择患者">
            <Select
              showSearch
              allowClear
              style={{ width: '100%' }}
              placeholder="搜索并选择患者"
              optionFilterProp="label"
              value={patientId}
              options={patients.map((p) => ({
                value: p.patientId,
                label: `${p.name}（${p.gender === 'Male' ? '男' : p.gender === 'Female' ? '女' : '未知'}${p.age != null ? `，${p.age}岁` : ''}）`,
              }))}
              onChange={setPatientId}
            />
            <Button
              type="primary"
              icon={<RobotOutlined />}
              block
              style={{ marginTop: 14 }}
              loading={generating}
              onClick={generate}
            >
              生成随访建议
            </Button>
            <div className="page-subtitle" style={{ marginTop: 12, lineHeight: 1.7 }}>
              建议仅用于随访管理参考，不包含药物剂量与手术决策；生成过程全程留痕。
            </div>

            <Card
              size="small"
              title="历史建议"
              bordered={false}
              style={{ marginTop: 16, background: '#f8fafc' }}
            >
              <Table
                size="small"
                rowKey="suggestionId"
                pagination={false}
                dataSource={history}
                locale={{ emptyText: '暂无历史' }}
                columns={[
                  {
                    title: '时间',
                    dataIndex: 'requestedAt',
                    width: 92,
                    render: (v: string) => (v ? dayjs(v).format('MM-DD HH:mm') : '-'),
                  },
                  {
                    title: '引擎',
                    dataIndex: 'engine',
                    width: 70,
                    render: (v: string) => (v === 'llm' ? 'LLM' : v === 'rules' ? '规则' : '-'),
                  },
                  {
                    title: '采纳',
                    dataIndex: 'adopted',
                    width: 60,
                    render: (v: boolean | null) => (v === true ? '是' : v === false ? '否' : '-'),
                  },
                  {
                    title: '',
                    width: 50,
                    render: (_: any, row: Suggestion) => (
                      <a onClick={() => viewHistory(row)}>查看</a>
                    ),
                  },
                ]}
              />
            </Card>
          </Card>
        </Col>

        <Col span={16}>
          <Card className="soft-card" bordered={false} style={{ minHeight: 560 }}>
            {generating && (
              <div style={{ textAlign: 'center', padding: '120px 0' }}>
                <Spin size="large" />
                <div style={{ marginTop: 16, color: '#7a8ba0' }}>
                  Agent 正在检索循证依据并生成建议…
                </div>
              </div>
            )}

            {!generating && !advice && (
              <Empty
                style={{ padding: '100px 0' }}
                description="选择患者后点击“生成随访建议”，结果将在此展示"
              />
            )}

            {!generating && advice && (
              <div className="advice-block">
                <Space style={{ marginBottom: 10 }}>
                  {engineTag}
                  {current?.adopted === true && <Tag color="green">已采纳</Tag>}
                  {current?.adopted === false && <Tag>未采纳</Tag>}
                </Space>

                <Typography.Paragraph style={{ fontSize: 15, color: '#102a43', lineHeight: 1.8 }}>
                  {advice.summary}
                </Typography.Paragraph>

                <Alert
                  type="error"
                  showIcon
                  message="需要立即就医的信号"
                  description={
                    <ul style={{ margin: 0, paddingLeft: 18 }}>
                      {(advice.redFlags || []).map((f: string, i: number) => (
                        <li key={i}>{f}</li>
                      ))}
                    </ul>
                  }
                  style={{ marginBottom: 12 }}
                />

                <Descriptions
                  size="small"
                  column={2}
                  bordered
                  style={{ marginBottom: 8 }}
                  items={[
                    {
                      key: 'stage',
                      label: '分期评估',
                      children: advice.stageAssessment?.stage || '-',
                    },
                    {
                      key: 'conf',
                      label: '可信度',
                      children: advice.stageAssessment?.confidence || '-',
                    },
                    {
                      key: 'freq',
                      label: '随访频率',
                      children: advice.followUpPlan?.frequency || '-',
                      span: 2,
                    },
                    {
                      key: 'basis',
                      label: '评估依据',
                      children: (
                        <ul style={{ margin: 0, paddingLeft: 18 }}>
                          {(advice.stageAssessment?.basis || []).map((b: string, i: number) => (
                            <li key={i}>{b}</li>
                          ))}
                        </ul>
                      ),
                      span: 2,
                    },
                  ]}
                />

                <h3>复查项目</h3>
                <ul className="advice-list">
                  {(advice.followUpPlan?.items || []).map((x: string, i: number) => (
                    <li key={i}>{x}</li>
                  ))}
                </ul>

                <h3>随访警告信号</h3>
                <ul className="advice-list">
                  {(advice.followUpPlan?.warningSigns || []).map((x: string, i: number) => (
                    <li key={i}>{x}</li>
                  ))}
                </ul>

                <h3>生活与自我管理建议</h3>
                <ul className="advice-list">
                  {(advice.lifestyleAdvice || []).map((x: string, i: number) => (
                    <li key={i}>{x}</li>
                  ))}
                </ul>

                {(advice.knowledgeRefs || []).length > 0 && (
                  <>
                    <h3>参考依据</h3>
                    <List
                      size="small"
                      dataSource={advice.knowledgeRefs}
                      renderItem={(r: any) => (
                        <List.Item>
                          <span>{r.title}</span>
                          <span className="page-subtitle">
                            {r.source} {r.year}
                          </span>
                        </List.Item>
                      )}
                    />
                  </>
                )}

                <Alert
                  type="warning"
                  showIcon
                  message="免责声明"
                  description={advice.disclaimer}
                  style={{ marginTop: 16 }}
                />

                {current && (
                  <Space style={{ marginTop: 16 }}>
                    <Button
                      type="primary"
                      disabled={current.adopted === true}
                      onClick={() => adopt(true)}
                    >
                      采纳建议
                    </Button>
                    <Button disabled={current.adopted === false} onClick={() => adopt(false)}>
                      不采纳
                    </Button>
                    <span className="page-subtitle">
                      {current.durationMs
                        ? `生成耗时 ${
                            current.durationMs < 1000
                              ? `${current.durationMs} 毫秒`
                              : `${(current.durationMs / 1000).toFixed(1)} 秒`
                          }`
                        : ''}
                    </span>
                  </Space>
                )}
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  )
}
