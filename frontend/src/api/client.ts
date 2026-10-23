import axios from 'axios'
import { message } from 'antd'

export const TOKEN_KEY = 'mouji_token'
export const USER_KEY = 'mouji_user'

const http = axios.create({ baseURL: '/api', timeout: 180000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    if ((response.config as any).raw) {
      return response
    }
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        message.error(body.message || '请求失败')
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      return body.data
    }
    return body
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message || error.message || '网络错误'
    // silent 请求（如"尚未设置头像"的 404）不弹全局提示
    if (!(error.config as any)?.silent) {
      message.error(msg)
    }
    return Promise.reject(error)
  },
)

/** 下载附件（CSV 等）：从 Content-Disposition 取文件名并触发浏览器保存。 */
export async function downloadFile(path: string, fallbackName: string) {
  const res: any = await http.get(path, { responseType: 'blob', raw: true } as any)
  const disposition: string = res.headers?.['content-disposition'] || ''
  let filename = fallbackName
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  if (star) {
    filename = decodeURIComponent(star[1])
  } else if (plain) {
    filename = plain[1]
  }
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export default http
