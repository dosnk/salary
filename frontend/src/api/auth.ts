import request from '@/utils/request'

export interface LoginParams {
  username: string
  password: string
  rememberMe?: boolean
}

export interface UserInfo {
  id: number
  username: string
  nickname?: string
  role: 'admin' | 'documenter' | 'constructor'
  created_at: string
}

export const authApi = {
  login: (params: LoginParams) => {
    return request.post<{ token: string; user: UserInfo }>('/v1/auth/login', params)
  },

  register: (params: { username: string; password: string; role: string }) => {
    return request.post('/v1/auth/register', params)
  },

  getUserInfo: () => {
    return request.get<UserInfo>('/v1/auth/me')
  },

  logout: () => {
    return request.post('/v1/auth/logout')
  }
}
