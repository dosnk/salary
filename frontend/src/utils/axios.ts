import axios from 'axios'
import type { AxiosInstance, AxiosResponse } from 'axios'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const baseURL = import.meta.env.VITE_API_BASE_URL
if (!baseURL) {
  throw new Error('VITE_API_BASE_URL 环境变量未配置')
}

const request: AxiosInstance = axios.create({
  baseURL,
  timeout: 30000
})

request.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, data, msg } = response.data
    
    if (code === 200) {
      return data as any
    } else {
      showToast({ type: 'fail', message: msg || '请求失败' })
      return Promise.reject(new Error(msg || '请求失败'))
    }
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      
      switch (status) {
        case 401:
          showToast({ type: 'fail', message: '登录已过期，请重新登录' })
          const authStore = useAuthStore()
          authStore.logout()
          router.push('/login')
          break
        case 403:
          showToast({ type: 'fail', message: '没有权限访问该资源' })
          break
        case 404:
          showToast({ type: 'fail', message: '请求的资源不存在' })
          break
        case 500:
          showToast({ type: 'fail', message: '服务器错误，请稍后重试' })
          break
        default:
          showToast({ type: 'fail', message: data?.msg || '请求失败' })
      }
    } else if (error.request) {
      showToast({ type: 'fail', message: '网络错误，请检查网络连接' })
    } else {
      showToast({ type: 'fail', message: '请求配置错误' })
    }
    
    return Promise.reject(error)
  }
)

export default request
