import request, { networkUtils } from '@/utils/request'

export interface UploadResponse {
  url: string
  fileName?: string
  fileSize?: number
  fileType?: string
}

export interface UploadOptions {
  onProgress?: (percent: number) => void
  signal?: AbortSignal
  retries?: number
}

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

export const uploadApi = {
  uploadFile: async (file: File, projectName: string, options?: UploadOptions): Promise<UploadResponse> => {
    const maxRetries = options?.retries ?? 3
    let lastError: Error | null = null
    
    for (let attempt = 0; attempt < maxRetries; attempt++) {
      if (!networkUtils.isOnline()) {
        throw new Error('网络连接已断开，请检查网络设置')
      }
      
      try {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('projectName', projectName)
        
        const result = await request.upload<UploadResponse>('/v1/upload', formData, {
          onUploadProgress: (progressEvent) => {
            if (options?.onProgress && progressEvent.total) {
              const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
              options.onProgress(percent)
            }
          },
          signal: options?.signal
        })
        
        return result
      } catch (error: any) {
        lastError = error
        
        if (error.name === 'AbortError' || error.message?.includes('取消')) {
          throw error
        }
        
        if (attempt < maxRetries - 1) {
          const delay = 1000 * (attempt + 1)
          await sleep(delay)
        }
      }
    }
    
    throw lastError || new Error('上传失败')
  },
  
  uploadFiles: async (
    files: File[], 
    projectName: string, 
    options?: UploadOptions & { onFileProgress?: (fileIndex: number, percent: number) => void }
  ): Promise<{ success: UploadResponse[]; failed: { file: File; error: string }[] }> => {
    const success: UploadResponse[] = []
    const failed: { file: File; error: string }[] = []
    
    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      
      try {
        const result = await uploadApi.uploadFile(file, projectName, {
          ...options,
          onProgress: (percent) => {
            options?.onFileProgress?.(i, percent)
          }
        })
        success.push(result)
      } catch (error: any) {
        failed.push({ file, error: error.message || '上传失败' })
      }
    }
    
    return { success, failed }
  }
}
