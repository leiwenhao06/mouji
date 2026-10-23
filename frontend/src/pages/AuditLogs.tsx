import { useEffect, useState } from 'react'
import { Button, Card, Input, Select, Space, Table, Tag, Tooltip } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import http from '../api/client'

const MODULE_LABEL: Record<string, string> = {
  USER: '账号',
  PATIENT: '患者',
  EXAM: '检查',
  IMPORT: '报告导入',
  SUGGESTION: 'AI 建议',
  EXPORT: '导出',
}

const MODULE_COLOR: Record<string, string> = {
  USER: 'blue',
  PATIENT: 'geekblue',
  EXAM: 'cyan',
  IMPORT: 'purple',
  SUGGESTION: 'orange',
  EXPORT: 'green',
}

/** 仅管理员可见：关键操作留痕查询。 */
export default function AuditLogs() {
  const [list, setList] = useState<any[]>([])
  const [modules, setModules] = useState<string[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [module, setModule] = useState<string>()
  const [username, setUsername] = useState('')
  const [loading, setLoading] = useState(false)

  const load = async (p = page, s = size, m = module, u = username) => {
    setLoading(true)
    try {
      const data: any = await http.get('/audit', {
        params: { module: m || undefined, username: u || undefined, page: p, size: s },
      })
      setList(data.list || [])
      setTotal(data.total || 0)
      setModules(data.modules || [])
      setPage(p)
      setSize(s)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(1, 20)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const columns = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 165,
      render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    { title: '操作人', dataIndex: 'username', width: 110 },
    {
      title: '模块',
      dataIndex: 'module',
      width: 110,
      render: (v: string) => <Tag color={MODULE_COLOR[v] || 'default'}>{MODULE_LABEL[v] || v}</Tag>,
    },
    { title: '动作', dataIndex: 'action', width: 150 },
    {
      title: '详情',
      dataIndex: 'detail',
      ellipsis: true,
      render: (v: string) => (
        <Tooltip title={v || '-'} placement="topLeft">
          <span style={{ color: '#52606d' }}>{v || '-'}</span>
        </Tooltip>
      ),
    },
    {
      title: '患者ID',
      dataIndex: 'patientId',
      width: 180,
      ellipsis: true,
      render: (v: string) => (v ? <span className="mono" style={{ fontSize: 12 }}>{v}</span> : '-'),
    },
    { title: '来源 IP', dataIndex: 'ip', width: 130, render: (v: string) => v || '-' },
  ]

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-title">审计日志</h2>
          <div className="page-subtitle">
            关键操作只追加、不可修改；共 {total} 条记录。日志不含患者姓名等直接标识。
          </div>
        </div>
        <Space>
          <Input.Search
            allowClear
            placeholder="按操作人筛选"
            style={{ width: 180 }}
            onSearch={(v) => {
              setUsername(v)
              load(1, size, module, v)
            }}
          />
          <Select
            allowClear
            placeholder="全部模块"
            style={{ width: 140 }}
            value={module}
            options={modules.map((m) => ({ value: m, label: MODULE_LABEL[m] || m }))}
            onChange={(v) => {
              setModule(v)
              load(1, size, v, username)
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => load()}>
            刷新
          </Button>
        </Space>
      </div>

      <Card className="soft-card" bordered={false}>
        <Table
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns as any}
          dataSource={list}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => load(p, s),
          }}
        />
      </Card>
    </div>
  )
}
