import { useState } from 'react'
import { Button, Card, Form, Input, Typography, message } from 'antd'
import { LockOutlined, MedicineBoxOutlined, UserOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import http, { TOKEN_KEY, USER_KEY } from '../api/client'

export default function Login() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true)
    try {
      const data: any = await http.post('/auth/login', values)
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data))
      message.success('登录成功')
      navigate('/patients', { replace: true })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-wrap">
      <Card className="login-card soft-card" bordered={false}>
        <div style={{ textAlign: 'center', marginBottom: 8 }}>
          <div
            className="brand-logo"
            style={{ width: 52, height: 52, margin: '0 auto 14px', fontSize: 26, borderRadius: 14 }}
          >
            <MedicineBoxOutlined />
          </div>
          <Typography.Title level={3} style={{ marginBottom: 4 }}>
            明眸随访
          </Typography.Title>
          <Typography.Text type="secondary">
            圆锥角膜眼健康随访台账助手
          </Typography.Text>
        </div>
        <Form
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ username: 'admin', password: 'Admin@2026' }}
          style={{ marginTop: 20 }}
        >
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" size="large" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={loading}>
            登录
          </Button>
        </Form>
        <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginTop: 16, marginBottom: 0 }}>
          演示环境账号 admin / Admin@2026；全部患者数据均为程序生成的模拟数据。
        </Typography.Paragraph>
      </Card>
    </div>
  )
}
