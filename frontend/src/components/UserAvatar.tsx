import { useEffect, useState } from 'react'
import { Avatar } from 'antd'
import http from '../api/client'

const PALETTE = [
  ['#1677ff', '#69b1ff'],
  ['#13c2c2', '#5cdbd3'],
  ['#722ed1', '#b37feb'],
  ['#fa8c16', '#ffc069'],
  ['#eb2f96', '#ff85c0'],
  ['#52c41a', '#95de64'],
]

function pick(name: string) {
  let h = 0
  for (let i = 0; i < name.length; i += 1) {
    h = (h * 31 + name.charCodeAt(i)) % 997
  }
  return PALETTE[h % PALETTE.length]
}

export default function UserAvatar({
  name,
  size = 36,
  version = 0,
}: {
  name?: string
  size?: number
  version?: number
}) {
  const [src, setSrc] = useState<string>()

  useEffect(() => {
    let url: string | undefined
    let alive = true
    setSrc(undefined)
    http
      .get('/account/avatar', { responseType: 'blob', silent: true } as any)
      .then((blob: any) => {
        if (!alive) return
        url = URL.createObjectURL(blob)
        setSrc(url)
      })
      .catch(() => undefined)
    return () => {
      alive = false
      if (url) URL.revokeObjectURL(url)
    }
  }, [version])

  if (src) {
    return <Avatar size={size} src={src} style={{ boxShadow: '0 2px 8px rgba(15,39,66,.14)' }} />
  }
  const display = (name || '医').trim()
  const initial = display.slice(0, 1).toUpperCase()
  const [c1, c2] = pick(display)
  return (
    <Avatar
      size={size}
      style={{
        background: `linear-gradient(135deg, ${c1}, ${c2})`,
        fontWeight: 600,
        boxShadow: '0 2px 8px rgba(15,39,66,.14)',
      }}
    >
      {initial}
    </Avatar>
  )
}
