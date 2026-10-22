import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import http from '../api/client'
import { Patient } from '../api/types'

export default function Patients() {
  const navigate = useNavigate()
  const [list, setList] = useState<Patient[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [gender, setGender] = useState<string>()
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()

  const load = async (p = page, s = size, kw = keyword, g = gender) => {
    setLoading(true)
    try {
      const data: any = await http.get('/patients', {
        params: { keyword: kw || undefined, gender: g, page: p, size: s },
      })
      setList(data.list || [])
      setTotal(data.total || 0)
      setPage(p)
      setSize(s)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(1, 10)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const submit = async () => {
    const values = await form.validateFields()
    await http.post('/patients', {
      ...values,
      dateOfBirth: values.dateOfBirth ? values.dateOfBirth.format('YYYY-MM-DD') : undefined,
    })
    message.success('患者已建档')
    setOpen(false)
    form.resetFields()
    load(1, size)
  }

  const columns = [
    {
      title: '患者',
      dataIndex: 'name',
      render: (name: string, row: Patient) => (
        <a onClick={() => navigate(`/patients/${row.patientId}`)}>{name}</a>
      ),
    },
    {
      title: '性别',
      dataIndex: 'gender',
      width: 90,
      render: (g: string) => (g === 'Male' ? '男' : g === 'Female' ? '女' : g || '-'),
    },
    { title: '年龄', dataIndex: 'age', width: 80, render: (v: number) => (v == null ? '-' : `${v} 岁`) },
    { title: '出生日期', dataIndex: 'dateOfBirth', width: 130 },
    {
      title: '联系电话',
      dataIndex: 'contact',
      width: 140,
      render: (v: string) => v || <Tag>演示数据</Tag>,
    },
    {
      title: '建档时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      width: 120,
      render: (_: any, row: Patient) => (
        <Button type="link" onClick={() => navigate(`/patients/${row.patientId}`)}>
          查看台账
        </Button>
      ),
    },
  ]

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-title">患者台账</h2>
          <div className="page-subtitle">按患者查看历次检查、进展提示与随访计划</div>
        </div>
        <Space>
          <Input.Search
            allowClear
            placeholder="搜索患者姓名"
            style={{ width: 220 }}
            onSearch={(v) => load(1, size, v)}
          />
          <Select
            allowClear
            placeholder="性别"
            style={{ width: 110 }}
            options={[
              { value: 'Male', label: '男' },
              { value: 'Female', label: '女' },
            ]}
            onChange={(v) => load(1, size, keyword, v)}
          />
          <Button icon={<ReloadOutlined />} onClick={() => load()}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
            新建患者
          </Button>
        </Space>
      </div>

      <Card className="soft-card" bordered={false}>
        <Table
          rowKey="patientId"
          loading={loading}
          columns={columns as any}
          dataSource={list}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 位患者`,
            onChange: (p, s) => load(p, s),
          }}
        />
      </Card>

      <Modal title="新建患者" open={open} onOk={submit} onCancel={() => setOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input placeholder="演示数据请使用虚构姓名" />
          </Form.Item>
          <Form.Item name="gender" label="性别">
            <Select
              allowClear
              options={[
                { value: 'Male', label: '男' },
                { value: 'Female', label: '女' },
              ]}
            />
          </Form.Item>
          <Form.Item name="dateOfBirth" label="出生日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="contact" label="联系电话">
            <Input placeholder="可选，演示建议填写虚拟号码" />
          </Form.Item>
          <Form.Item name="medicalHistory" label="既往病史">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
