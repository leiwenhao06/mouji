import { useEffect, useMemo, useState } from 'react'
import { Badge, Button, Dropdown, Layout as AntLayout, Menu, Space, Tag } from 'antd'
import {
  AuditOutlined,
  DashboardOutlined,
  FileSearchOutlined,
  LogoutOutlined,
  MedicineBoxOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  RobotOutlined,
  ScheduleOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import http, { USER_KEY } from '../api/client'
import { Account } from '../api/types'
import UserAvatar from './UserAvatar'
import ProfileModal from './ProfileModal'

const { Sider, Header, Content } = AntLayout

const TITLES: Record<string, string> = {
  '/patients': '患者台账',
  '/import': '报告导入',
  '/followup': '随访计划',
  '/suggestions': 'AI 随访建议',
  '/dashboard': '数据看板',
  '/users': '用户管理',
  '/audit': '审计日志',
}

export default function Layout() {
  const navigate = useNavigate()
  const location = useLocation()
  const [overdue, setOverdue] = useState(0)
  const [collapsed, setCollapsed] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const [avatarVersion, setAvatarVersion] = useState(0)
  const [account, setAccount] = useState<Account>(() => {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) || '{}')
    } catch {
      return { username: '', role: 'user' }
    }
  })

  useEffect(() => {
    http
      .get('/account')
      .then((data: any) => {
        setAccount(data)
        const stored = JSON.parse(localStorage.getItem(USER_KEY) || '{}')
        localStorage.setItem(USER_KEY, JSON.stringify({ ...stored, ...data }))
      })
      .catch(() => undefined)
  }, [])

  useEffect(() => {
    let alive = true
    const load = () => {
      http
        .get('/followup/overdue-count')
        .then((d: any) => {
          if (alive) setOverdue(d?.count || 0)
        })
        .catch(() => undefined)
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

  const items: any[] = useMemo(() => {
    const list: any[] = [
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
    if (account.role === 'admin') {
      list.push({ key: '/users', icon: <UserOutlined />, label: '用户管理' })
      list.push({ key: '/audit', icon: <AuditOutlined />, label: '审计日志' })
    }
    return list
  }, [overdue, account.role])

  const logout = () => {
    localStorage.removeItem('mouji_token')
    localStorage.removeItem(USER_KEY)
    navigate('/login', { replace: true })
  }

  const handleUpdated = (next: Account) => {
    setAccount(next)
    setAvatarVersion((v) => v + 1)
    const stored = JSON.parse(localStorage.getItem(USER_KEY) || '{}')
    localStorage.setItem(USER_KEY, JSON.stringify({ ...stored, ...next }))
  }

  return (
    <AntLayout className="app-shell">
      <Sider
        width={226}
        collapsedWidth={0}
        collapsed={collapsed}
        trigger={null}
        theme="light"
        className="app-sider"
      >
        <div className="sider-inner">
          <div className="brand" onClick={() => navigate('/patients')}>
            <div className="brand-logo">
              <MedicineBoxOutlined />
            </div>
            <div className="brand-text">
              明眸随访
              <div className="brand-sub">圆锥角膜随访台账</div>
            </div>
          </div>

          <Menu
            mode="inline"
            selectedKeys={[selected]}
            items={items}
            className="app-menu"
            onClick={({ key }) => navigate(key)}
          />

          <div className="sider-profile" onClick={() => setProfileOpen(true)}>
            <UserAvatar
              name={account.displayName || account.username}
              size={40}
              version={avatarVersion}
            />
            <div className="sider-profile-info">
              <div className="sider-profile-name">{account.displayName || account.username || '未登录'}</div>
              <div className="sider-profile-role">
                {account.role === 'admin' ? '管理员' : '医生'} · 个人资料
              </div>
            </div>
            <SettingOutlined className="sider-profile-gear" />
          </div>
        </div>
      </Sider>

      <AntLayout>
        <Header className="app-header">
          <Button
            type="text"
            className="collapse-btn"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed((v) => !v)}
          />
          <div className="header-title">{TITLES[selected] || '工作台'}</div>
          <div className="header-spacer" />

          {overdue > 0 && (
            <div className="header-alert" onClick={() => navigate('/followup')}>
              <WarningOutlined />
              逾期随访 <strong>{overdue}</strong>
            </div>
          )}

          <Dropdown
            menu={{
              items: [
                {
                  key: 'profile',
                  icon: <UserOutlined />,
                  label: '个人资料',
                  onClick: () => setProfileOpen(true),
                },
                { type: 'divider' },
                { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: logout },
              ],
            }}
          >
            <Space className="header-user" size={10}>
              <UserAvatar
                name={account.displayName || account.username}
                size={34}
                version={avatarVersion}
              />
              <span className="header-user-name">{account.displayName || account.username}</span>
              <Tag color={account.role === 'admin' ? 'blue' : 'default'}>
                {account.role === 'admin' ? '管理员' : '医生'}
              </Tag>
            </Space>
          </Dropdown>
        </Header>

        <Content className="app-content">
          <Outlet />
        </Content>
      </AntLayout>

      <ProfileModal
        open={profileOpen}
        account={account}
        onClose={() => setProfileOpen(false)}
        onUpdated={handleUpdated}
      />
    </AntLayout>
  )
}
