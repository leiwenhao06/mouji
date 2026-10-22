import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Modal, Select, Space, Table, Tag, message } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import http from '../api/client'

export default function Users() {
  const [list, setList] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [pwdOpen, setPwdOpen] = useState<any>()
  const [form] = Form.useForm()
  const [pwdForm] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const data: any = await http.get('/users')
      setList(data || [])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const create = async () => {
    const v = await form.validateFields()
    await http.post('/users', v)
    message.success('用户已创建')
    setOpen(false)
    form.resetFields()
    load()
  }

  const resetPwd = async () => {
    const v = await pwdForm.validateFields()
    await http.put(`/users/${pwdOpen.id}/password`, { password: v.password })
    message.success('密码已重置')
    setPwdOpen(undefined)
    pwdForm.resetFields()
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-title">用户管理</h2>
          <div className="page-subtitle">管理员可创建医生账号；所有关键操作均写入审计日志</div>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
          新建用户
        </Button>
      </div>

      <Card className="soft-card" bordered={false}>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={list}
          pagination={false}
          columns={[
            { title: '用户名', dataIndex: 'username' },
            { title: '显示名称', dataIndex: 'displayName' },
            {
              title: '角色',
              dataIndex: 'role',
              render: (v: string) => (
                <Tag color={v === 'admin' ? 'blue' : 'default'}>{v === 'admin' ? '管理员' : '医生'}</Tag>
              ),
            },
            {
              title: '创建时间',
              dataIndex: 'createdAt',
              render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
            },
            {
              title: '操作',
              render: (_: any, row: any) => (
                <Space>
                  <Button type="link" size="small" onClick={() => setPwdOpen(row)}>
                    重置密码
                  </Button>
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal title="新建用户" open={open} onOk={create} onCancel={() => setOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[{ required: true, min: 6, message: '至少 6 位' }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item name="displayName" label="显示名称">
            <Input />
          </Form.Item>
          <Form.Item name="role" label="角色" initialValue="user">
            <Select
              options={[
                { value: 'user', label: '医生' },
                { value: 'admin', label: '管理员' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`重置密码：${pwdOpen?.username || ''}`}
        open={!!pwdOpen}
        onOk={resetPwd}
        onCancel={() => setPwdOpen(undefined)}
        destroyOnClose
      >
        <Form form={pwdForm} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item name="password" label="新密码" rules={[{ required: true, min: 6, message: '至少 6 位' }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
