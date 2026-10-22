import { useEffect, useMemo, useState } from 'react'
import { Avatar, Badge, Dropdown, Layout as AntLayout, Menu, Space, Tag } from 'antd'
import {
  DashboardOutlined,
  FileSearchOutlined,
  LogoutOutlined,
  MedicineBoxOutlined,
  RobotOutlined,
  ScheduleOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import http, { USER_KEY } from '../api/client'

const { Sider, Header, Content } = AntLayout

export default function Layout() {
  const navigate = useNavigate()
  const location = useLocation()
  const [overdue, setOverdue] = useState(0)
  const user = useMemo(() => {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) || '{}')
    } catch {
      return {}
    }
  }, [])

  useEffect(() => {
    let alive = true
    const load = () => {
      http.get('/followup/overdue-count').then((d: any) => {
        if (alive) setOverdue(d?.count || 0)
      }).catch(() => undefined)
    }
    load()
    const timer = window.setInterval(load, 60000)
    return () => {
      alive = false
      window.clearInterval(timer)
    }
  }, [])

  const selected = location.pathname.startsWith('/patients')
    ? '/patients'
    : location.pathname

  const items: any[] = [
    { key: '/patients', icon: <TeamOutlined />, label: '患者台账' },
    { key: '/import', icon: <FileSearchOutlined />, label: '报告导入' },
    {
      key: '/followup',
      icon: <ScheduleOutlined />,
      label: (
        <Space>
          随访计划
          {overdue > 0 && <Badge count={overdue} size="small" />}
        </Space>
      ),
    },
    { key: '/suggestions', icon: <RobotOutlined />, label: 'AI 随访建议' },
    { key: '/dashboard', icon: <DashboardOutlined />, label: '数据看板' },
  ]
  if (user.role === 'admin') {
    items.push({ key: '/users', icon: <UserOutlined />, label: '用户管理' })
  }

  const logout = () => {
    localStorage.removeItem('mouji_token')
    localStorage.removeItem(USER_KEY)
    navigate('/login', { replace: true })
  }

  return (
    <AntLayout style={{ minHeight: '100%' }}>
      <Sider width={216} theme="light" style={{ borderRight: '1px solid #e8eef5' }}>
        <div className="brand">
          <div className="brand-logo">
            <MedicineBoxOutlined />
          </div>
          <div>
            明眸随访
            <div style={{ fontSize: 11, fontWeight: 400, color: '#7a8ba0', letterSpacing: 0 }}>
              圆锥角膜随访台账
            </div>
          </div>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selected]}
          items={items}
          style={{ borderInlineEnd: 'none', padding: '4px 8px' }}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <AntLayout>
        <Header
          style={{
            background: '#fff',
            borderBottom: '1px solid #e8eef5',
            padding: '0 24px',
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            height: 56,
          }}
        >
          <Dropdown
            menu={{
              items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: logout }],
            }}
          >
            <Space style={{ cursor: 'pointer' }}>
              <Avatar size={30} style={{ background: '#1677ff' }}>
                {(user.displayName || user.username || '医').slice(0, 1)}
              </Avatar>
              <span>{user.displayName || user.username}</span>
              <Tag color={user.role === 'admin' ? 'blue' : 'default'}>
                {user.role === 'admin' ? '管理员' : '医生'}
              </Tag>
            </Space>
          </Dropdown>
        </Header>
        <Content style={{ padding: 24, overflow: 'auto' }}>
          <Outlet />
        </Content>
      </AntLayout>
    </AntLayout>
  )
}
