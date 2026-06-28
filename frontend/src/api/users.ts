import request from '@/utils/request'

export interface User {
  id: number
  username: string
  nickname?: string
  phone?: string
  role: 'admin' | 'documenter' | 'constructor'
  created_at: string
}

export interface UserListParams {
  page?: number
  size?: number
  keyword?: string
  role?: string
}

export interface UserListResponse {
  list: User[]
  total: number
  page: number
  size: number
  hasNext: boolean
}

export interface CreateUserParams {
  username: string
  password: string
  nickname?: string
  phone?: string
  role: string
}

export interface UpdateUserParams {
  nickname?: string
  phone?: string
  role?: string
}

export interface ChangePasswordParams {
  old_password: string
  new_password: string
}

export interface ResetPasswordParams {
  new_password: string
}

export interface ConstructorUser {
  id: number
  username: string
  nickname?: string
  phone?: string
}

export const usersApi = {
  getUsers: (params: UserListParams) => {
    return request.get<UserListResponse>('/v1/users', { params })
  },

  getConstructors: () => {
    return request.get<ConstructorUser[]>('/v1/users/constructors')
  },

  getUser: (id: number) => {
    return request.get<User>(`/v1/users/${id}`)
  },

  getProfile: () => {
    return request.get<User>('/v1/users/profile')
  },

  createUser: (params: CreateUserParams) => {
    return request.post<User>('/v1/users', params)
  },

  updateUser: (id: number, params: UpdateUserParams) => {
    return request.put<User>(`/v1/users/${id}`, params)
  },

  updateProfile: (params: UpdateUserParams) => {
    return request.put<User>('/v1/users/profile', params)
  },

  deleteUser: (id: number) => {
    return request.delete(`/v1/users/${id}`)
  },

  changePassword: (params: ChangePasswordParams) => {
    return request.post('/v1/users/change-password', params)
  },

  resetPassword: (id: number, params: ResetPasswordParams) => {
    return request.post(`/v1/users/${id}/reset-password`, params)
  }
}
