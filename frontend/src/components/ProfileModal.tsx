import { useEffect, useState } from 'react'
import { Button, Form, Input, Modal, Tag, Upload, message } from 'antd'
import { CameraOutlined } from '@ant-design/icons'
import http from '../api/client'
import { Account } from '../api/types'
import UserAvatar from './UserAvatar'

/**
 * 个人资料编辑：昵称 + 头像。
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
  const [version, setVersion] = useState(0)
  const [uploading, setUploading] = useState(false)
  const [saving, setSaving] = useState(false)

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
    </Modal>
  )
}
