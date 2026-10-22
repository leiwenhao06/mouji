import { useEffect, useState } from 'react'
import http from '../api/client'

/** 通过鉴权接口拉取图片 Blob 并展示（避免在 URL 中携带令牌）。 */
export default function AuthImage({
  imageId,
  width = 120,
  height = 84,
}: {
  imageId: number
  width?: number
  height?: number
}) {
  const [src, setSrc] = useState<string>()

  useEffect(() => {
    let url: string | undefined
    let alive = true
    http
      .get(`/images/${imageId}`, { responseType: 'blob' })
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
  }, [imageId])

  if (!src) {
    return <div style={{ width, height, background: '#f0f3f7', borderRadius: 6 }} />
  }
  return (
    <img
      src={src}
      width={width}
      height={height}
      style={{ objectFit: 'cover', borderRadius: 6, border: '1px solid #e8eef5', cursor: 'zoom-in' }}
      onClick={() => window.open(src, '_blank')}
    />
  )
}
