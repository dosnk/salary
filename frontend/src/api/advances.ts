import request from '@/utils/request'

export interface Advance {
  id: number
  user_id: number
  advance_amount: number
  advance_date: string
  settled: boolean
  created_by: number
  remark?: string
  created_at: string
  updated_at: string
  user?: {
    id: number
    username: string
    nickname?: string
  }
  creator?: {
    id: number
    username: string
    nickname?: string
  }
}

export interface CreateAdvanceParams {
  userId: number
  advanceAmount: number
  advanceDate: string
  remark?: string
}

export interface AdvanceListParams {
  page?: number
  size?: number
  keyword?: string
  userId?: number
  year?: number
  month?: number
}

export interface AdvanceListResponse {
  list: Advance[]
  total: number
  page: number
  size: number
  hasNext: boolean
}

export interface AdvanceTotalResponse {
  total: number
}

export const advancesApi = {
  createAdvance: (params: CreateAdvanceParams) => {
    return request.post<Advance>('/v1/advances', params)
  },

  getAdvances: (params: AdvanceListParams) => {
    return request.get<AdvanceListResponse>('/v1/advances', { params })
  },

  getAdvanceTotal: (userId?: number) => {
    return request.get<AdvanceTotalResponse>('/v1/advances/total', {
      params: userId ? { userId } : undefined
    })
  },

  deleteAdvance: (id: number) => {
    return request.delete(`/v1/advances/${id}`)
  },

  getMyAdvances: (params?: { page?: number; size?: number; year?: number; month?: number }) => {
    return request.get<AdvanceListResponse>('/v1/advances/my', { params })
  },

  getMyAdvanceTotal: () => {
    return request.get<AdvanceTotalResponse>('/v1/advances/my/total')
  }
}
