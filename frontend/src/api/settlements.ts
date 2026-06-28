import request from '@/utils/request'

export interface Settlement {
  id: number
  settlement_no: string
  project_id: number
  project_name?: string
  user_id: number
  user_name?: string
  user_nickname?: string
  start_month: string
  end_month: string
  total_amount: number
  advance_amount: number
  actual_amount: number
  confirmed: boolean
  confirmed_at?: string
  paid: boolean
  paid_at?: string
  settled_by: number
  settled_by_name?: string
  settled_at?: string
  remark?: string
}

export interface SettlementListParams {
  page?: number
  size?: number
  startDate?: string
  endDate?: string
  projectId?: number
  userId?: number
}

export interface SettlementDetail extends Settlement {
  advances: any[]
  distributions: any[]
}

export interface UserSettlementHistory extends Settlement {
  user_amount: number
}

export interface UserSettlementHistoryParams {
  page?: number
  size?: number
  confirmed?: boolean
}

export interface CreateSettlementParams {
  startMonth: string
  endMonth: string
  remark?: string
}

export interface SettlementStatistics {
  settling: {
    amount: number
    count: number
    thisMonthCount: number
    description: string
  }
  unsettled: {
    amount: number
    count: number
    thisMonthCount: number
    description: string
  }
  settled: {
    amount: number
    count: number
    thisMonthCount: number
    description: string
  }
  userUnconfirmed: {
    amount: number
    count: number
    description: string
  }
  userConfirmed: {
    amount: number
    count: number
    description: string
  }
  thisMonth: {
    amount: number
    count: number
    description: string
  }
}

const settlementsApi = {
  getSettlements: (params?: SettlementListParams) => {
    return request.get<any>('/v1/settlements', { params })
  },

  getSettlementDetail: (id: number) => {
    return request.get<SettlementDetail>(`/v1/settlements/${id}`)
  },

  createSettlement: (data: CreateSettlementParams) => {
    return request.post<Settlement>('/v1/settlements', data)
  },

  confirmSettlement: (id: number) => {
    return request.post<any>(`/v1/settlements/${id}/confirm`)
  },

  getUserSettlementHistory: (params?: UserSettlementHistoryParams) => {
    return request.get<any>('/v1/settlements/history', { params })
  },

  getSettlementStatistics: () => {
    return request.get<SettlementStatistics>('/v1/statistics/settlements')
  }
}

export default settlementsApi
