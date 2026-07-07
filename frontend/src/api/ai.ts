import request from '@/utils/request'

export interface AiProviderConfigDto {
  name: string
  apiKey: string
  secretKey: string
  model: string
  maxTokens: number
  temperature: number
  baseUrl: string
  hasApiKey: boolean
  hasSecretKey: boolean
}

export interface AiConfigResponse {
  defaultProvider: string
  providers: Record<string, AiProviderConfigDto>
}

export interface AiProviderConfigUpdate {
  apiKey?: string | null
  secretKey?: string | null
  model?: string | null
}

export interface AiConfigUpdateRequest {
  defaultProvider: string
  providerConfigs: Record<string, AiProviderConfigUpdate>
}

export interface AiConfigUpdateResponse {
  message: string
}

export interface AiTestRequest {
  provider: string
}

export interface AiTestResponse {
  provider: string
  providerName: string
  model: string
  response: string
  message: string
}

export const aiApi = {
  getConfig: () => {
    return request.get<AiConfigResponse>('/v1/ai/config')
  },

  updateConfig: (params: AiConfigUpdateRequest) => {
    return request.put<AiConfigUpdateResponse>('/v1/ai/config', params)
  },

  testConnection: (params: AiTestRequest) => {
    return request.post<AiTestResponse>('/v1/ai/test', params)
  }
}
