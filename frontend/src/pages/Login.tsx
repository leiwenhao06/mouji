import { useState } from 'react'
import { Button, Card, Form, Input, message } from 'antd'
import {
  CameraOutlined,
  LineChartOutlined,
  LockOutlined,
  MedicineBoxOutlined,
  RobotOutlined,
  ScheduleOutlined,
  UserOutlined,
} from '@ant-design/icons'
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
    <div className="login-page">
      <div className="login-hero">
        <div className="login-hero-inner">
          <div className="hero-badge">2026 华为开发者大赛 · 福建赛区</div>
          <h1>
            让每一次随访，
            <br />
            都不被遗漏
          </h1>
          <p className="hero-sub">
            圆锥角膜需要长期随访。明眸随访把每一份检查报告变成结构化台账，
            自动盯进展、排随访、写建议。
          </p>

          <div className="hero-points">
            <div className="hero-point">
              <span className="hero-point-icon"><CameraOutlined /></span>
              <div>
                <b>报告一拍即入台账</b>
                <div>OCR 自动提取 KMax、最薄点厚度等关键指标</div>
              </div>
            </div>
            <div className="hero-point">
              <span className="hero-point-icon"><LineChartOutlined /></span>
              <div>
                <b>进展自动识别</b>
                <div>按年化 KMax / 厚度变化标记进展风险</div>
              </div>
            </div>
            <div className="hero-point">
              <span className="hero-point-icon"><ScheduleOutlined /></span>
              <div>
                <b>随访到期提醒 + AI 建议</b>
                <div>循证草稿、指南守卫，建议可追溯可采纳</div>
              </div>
            </div>
          </div>

          <div className="hero-mock">
            <div className="hero-mock-head">
              <span>今日随访概览</span>
              <span className="hero-mock-live">LIVE</span>
            </div>
            <div className="hero-mock-stats">
              <div><b>30</b><span>在管患者</span></div>
              <div className="danger"><b>6</b><span>逾期随访</span></div>
              <div><b>17</b><span>进展风险</span></div>
            </div>
            <div className="hero-mock-chart">
              {[38, 52, 44, 68, 57, 76, 63, 84, 72, 90].map((h, i) => (
                <span key={i} style={{ height: `${h}%` }} />
              ))}
            </div>
            <div className="hero-mock-foot">
              <RobotOutlined /> AI 随访建议：每 3 个月复查，关注角膜最薄点变化
            </div>
          </div>
        </div>
      </div>

      <div className="login-panel">
        <Card className="login-card" bordered={false}>
          <div className="login-brand">
            <div className="brand-logo login-logo">
              <MedicineBoxOutlined />
            </div>
            <div>
              <div className="login-title">明眸随访</div>
              <div className="login-subtitle">圆锥角膜眼健康随访台账助手</div>
            </div>
          </div>

          <Form
            layout="vertical"
            onFinish={onFinish}
            initialValues={{ username: 'admin', password: 'Admin@2026' }}
            style={{ marginTop: 26 }}
          >
            <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input prefix={<UserOutlined />} placeholder="用户名" size="large" />
            </Form.Item>
            <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
            </Form.Item>
            <Button type="primary" htmlType="submit" size="large" block loading={loading} className="login-btn">
              登 录
            </Button>
          </Form>

          <div className="login-demo">
            演示账号 <code>admin</code> / <code>Admin@2026</code>
            <div>全部患者数据均为程序生成的模拟数据</div>
          </div>
        </Card>
        <div className="login-copyright">明眸随访 · 眼健康随访台账</div>
      </div>
    </div>
  )
}
