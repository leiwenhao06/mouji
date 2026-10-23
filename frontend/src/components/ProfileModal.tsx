import { useEffect, useState } from 'react'
import { Button, Divider, Form, Input, Modal, Tag, Upload, message } from 'antd'
import { CameraOutlined, LockOutlined } from '@ant-design/icons'
import http from '../api/client'
import { Account } from '../api/types'
import UserAvatar from './UserAvatar'

/**
 * 个人资料编辑：昵称 + 头像 + 修改密码。
 */
export default function ProfileModal({
  open,
  account,
  onClose,
  onUpdated,
}: {
  open: boolean
  account?: Account
  onClose: () => void
  onUpdated: (account: Account) => void
}) {
  const [form] = Form.useForm()
  const [pwdForm] = Form.useForm()
  const [version, setVersion] = useState(0)
  const [uploading, setUploading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [pwdSaving, setPwdSaving] = useState(false)

  useEffect(() => {
    if (open) {
      form.setFieldsValue({ displayName: account?.displayName || account?.username })
      setVersion((v) => v + 1)
    }
  }, [open, account, form])

  const save = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const data: any = await http.put('/account/profile', { displayName: values.displayName })
      onUpdated(data)
      message.success('昵称已更新')
      onClose()
    } finally {
      setSaving(false)
    }
  }

  const changePassword = async () => {
    const values = await pwdForm.validateFields()
    setPwdSaving(true)
    try {
      await http.put('/account/password', {
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      })
      message.success('密码已修改，下次登录请使用新密码')
      pwdForm.resetFields()
    } catch {
      // 失败提示已由 axios 拦截器统一弹出
    } finally {
      setPwdSaving(false)
    }
  }

  const upload = async (file: File) => {
    if (file.size > 2 * 1024 * 1024) {
      message.error('头像图片不能超过 2MB')
      return false
    }
    const fd = new FormData()
    fd.append('file', file)
    setUploading(true)
    try {
      const data: any = await http.post('/account/avatar', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      onUpdated(data)
      setVersion((v) => v + 1)
      message.success('头像已更新')
    } finally {
      setUploading(false)
    }
    return false
  }

  return (
    <Modal
      title="个人资料"
      open={open}
      onCancel={onClose}
      width={440}
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button key="save" type="primary" loading={saving} onClick={save}>
          保存
        </Button>,
      ]}
    >
      <div className="profile-editor">
        <Upload
          accept="image/jpeg,image/png,image/webp"
          showUploadList={false}
          beforeUpload={(file) => upload(file as File)}
        >
          <div className="avatar-uploader" title="点击更换头像">
            <UserAvatar name={account?.displayName || account?.username} size={86} version={version} />
            <div className="avatar-uploader-mask">
              {uploading ? '上传中…' : <><CameraOutlined /> 更换头像</>}
            </div>
          </div>
        </Upload>
        <div className="profile-editor-meta">
          <div className="profile-editor-name">{account?.displayName || account?.username}</div>
          <div className="profile-editor-sub">
            账号 {account?.username}
            <Tag color={account?.role === 'admin' ? 'blue' : 'default'} style={{ marginLeft: 8 }}>
              {account?.role === 'admin' ? '管理员' : '医生'}
            </Tag>
          </div>
          <div className="profile-editor-tip">支持 jpg / png / webp，大小不超过 2MB</div>
        </div>
      </div>
      <Form form={form} layout="vertical" style={{ marginTop: 18 }}>
        <Form.Item
          name="displayName"
          label="昵称"
          rules={[
            { required: true, message: '请输入昵称' },
            { max: 50, message: '昵称最多 50 个字符' },
          ]}
        >
          <Input placeholder="显示在工作台与看板中的名称" maxLength={50} showCount />
        </Form.Item>
      </Form>

      <Divider plain style={{ margin: '6px 0 14px', color: '#7a8ba0', fontSize: 13 }}>
        <LockOutlined /> 修改密码
      </Divider>
      <Form form={pwdForm} layout="vertical">
        <Form.Item
          name="oldPassword"
          label="原密码"
          rules={[{ required: true, message: '请输入原密码' }]}
        >
          <Input.Password placeholder="当前使用的密码" autoComplete="current-password" />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label="新密码"
          rules={[
            { required: true, message: '请输入新密码' },
            {
              pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/,
              message: '8-64 位，需同时包含字母和数字',
            },
          ]}
        >
          <Input.Password placeholder="8-64 位，包含字母和数字" autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirm"
          label="确认新密码"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: '请再次输入新密码' },
            ({ getFieldValue }) => ({
              validator: (_, v) =>
                !v || v === getFieldValue('newPassword')
                  ? Promise.resolve()
                  : Promise.reject(new Error('两次输入的密码不一致')),
            }),
          ]}
        >
          <Input.Password placeholder="再次输入新密码" autoComplete="new-password" />
        </Form.Item>
        <Button block loading={pwdSaving} onClick={changePassword} style={{ marginBottom: 4 }}>
          修改密码
        </Button>
      </Form>
    </Modal>
  )
}
