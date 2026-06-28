import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL
if (!baseURL) {
  throw new Error('VITE_API_BASE_URL 环境变量未配置')
}

const DEFAULT_TIMEOUT = 30000
const UPLOAD_TIMEOUT = 120000
const SETTLE_TIMEOUT = 300000 // 结算超时5分钟（支持大量工程结算）
const RETRY_COUNT = 3
const RETRY_DELAY = 1000

const instance = axios.create({
  baseURL,
  timeout: DEFAULT_TIMEOUT
}) as AxiosInstance

const isOnline = (): boolean => {
  return navigator.onLine
}

const getNetworkType = (): string => {
  const connection = (navigator as any).connection || (navigator as any).mozConnection || (navigator as any).webkitConnection
  if (connection) {
    return connection.effectiveType || connection.type || 'unknown'
  }
  return 'unknown'
}

const isSlowNetwork = (): boolean => {
  const connection = (navigator as any).connection || (navigator as any).mozConnection || (navigator as any).webkitConnection
  if (connection) {
    const type = connection.effectiveType || connection.type
    if (type === 'slow-2g' || type === '2g' || type === '3g') {
      return true
    }
    if (connection.downlink && connection.downlink < 1.5) {
      return true
    }
  }
  return false
}

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

const retryRequest = async (config: AxiosRequestConfig, retries: number = RETRY_COUNT): Promise<any> => {
  try {
    return await instance(config)
  } catch (error: any) {
    const shouldRetry = 
      retries > 0 &&
      !error.response &&
      (error.code === 'ECONNABORTED' || 
       error.code === 'ERR_NETWORK' ||
       error.message === 'Network Error' ||
       error.message.includes('timeout'))
    
    if (shouldRetry) {
      const delay = RETRY_DELAY * (RETRY_COUNT - retries + 1)
      await sleep(delay)
      return retryRequest(config, retries - 1)
    }
    
    throw error
  }
}

instance.interceptors.request.use(
  (config) => {
    if (!isOnline()) {
      return Promise.reject(new Error('网络连接已断开，请检查网络设置'))
    }
    
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    
    // 上传文件请求延长超时时间
    if (config.url?.includes('/upload') || config.url?.includes('/files')) {
      config.timeout = UPLOAD_TIMEOUT
      
      if (isSlowNetwork()) {
        config.timeout = UPLOAD_TIMEOUT * 2
      }
    }
    
    // 结算请求延长超时时间（支持大量工程结算）
    if (config.url?.includes('/settle') || config.url?.includes('/settlements')) {
      config.timeout = SETTLE_TIMEOUT
    }
    
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

instance.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, data, msg } = response.data
    if (code === 200) {
      return data
    } else if (code === 4001) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
      return Promise.reject(new Error(msg || '登录已过期，请重新登录'))
    } else {
      return Promise.reject(new Error(msg || '请求失败'))
    }
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        window.location.href = '/login'
        return Promise.reject(new Error('未授权，请重新登录'))
      } else if (status === 404) {
        return Promise.reject(new Error('请求的资源不存在'))
      } else if (status === 500) {
        return Promise.reject(new Error('服务器错误'))
      } else if (status === 429) {
        return Promise.reject(new Error('请求过于频繁，请稍后再试'))
      } else if (status >= 502 && status <= 504) {
        return Promise.reject(new Error('服务器暂时不可用，请稍后再试'))
      } else {
        return Promise.reject(new Error(data?.msg || '请求失败'))
      }
    } else if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时，请检查网络连接'))
    } else if (error.code === 'ERR_NETWORK' || error.message === 'Network Error') {
      return Promise.reject(new Error('网络连接失败，请检查网络设置'))
    } else if (!isOnline()) {
      return Promise.reject(new Error('网络连接已断开'))
    } else {
      return Promise.reject(new Error('网络错误，请稍后重试'))
    }
  }
)

const request = {
  get: <T = any>(url: string, config?: AxiosRequestConfig): Promise<T> => {
    return retryRequest({ ...config, method: 'GET', url })
  },
  
  post: <T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> => {
    return retryRequest({ ...config, method: 'POST', url, data })
  },
  
  put: <T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> => {
    return retryRequest({ ...config, method: 'PUT', url, data })
  },
  
  delete: <T = any>(url: string, config?: AxiosRequestConfig): Promise<T> => {
    return retryRequest({ ...config, method: 'DELETE', url })
  },
  
  upload: <T = any>(url: string, formData: FormData, config?: AxiosRequestConfig): Promise<T> => {
    const uploadConfig: AxiosRequestConfig = {
      ...config,
      method: 'POST',
      url,
      data: formData,
      timeout: isSlowNetwork() ? UPLOAD_TIMEOUT * 2 : UPLOAD_TIMEOUT,
      headers: {
        'Content-Type': 'multipart/form-data',
        ...config?.headers
      }
    }
    return instance(uploadConfig)
  },
  
  createAbortController: () => new AbortController(),
  
  getWithAbort: <T = any>(url: string, signal: AbortSignal, config?: AxiosRequestConfig): Promise<T> => {
    return retryRequest({ ...config, method: 'GET', url, signal })
  }
}

const networkUtils = {
  isOnline,
  getNetworkType,
  isSlowNetwork
}

export default request
export { baseURL, networkUtils }
