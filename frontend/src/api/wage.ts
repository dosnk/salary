import request from '@/utils/request'

export interface WageDistribution {
  id?: number
  subproject_id: number
  user_id: number
  workdays: number
  amount: number
  status?: string
  created_at?: string
  user_name?: string
  subproject_name?: string
  project_name?: string
}

export interface WageSettlement {
  id?: number
  settlement_no: string
  start_month: string
  end_month: string
  total_amount: number
  advance_amount?: number
  actual_amount: number
  settled_by?: number
  settled_at?: string
  remark?: string
  settled_by_name?: string
}

export interface WageAdvance {
  id?: number
  user_id: number
  advance_amount: number
  advance_date: string
  settled?: boolean
  settlement_id?: number
  created_by?: number
  created_at?: string
  remark?: string
  user_name?: string
  created_by_name?: string
}

export interface WageDistributionListParams {
  page?: number
  size?: number
  keyword?: string
  status?: string
  user_id?: number
  subproject_id?: number
}

export interface WageSettlementListParams {
  page?: number
  size?: number
  keyword?: string
  start_date?: string
  end_date?: string
}

export interface WageAdvanceListParams {
  page?: number
  size?: number
  keyword?: string
  user_id?: number
  settled?: boolean
}

export interface WageStatistics {
  total_distributions: number
  total_amount: number
  unsettled_amount: number
  settled_amount: number
  total_advances: number
  advance_amount: number
}

export interface UserWageSummary {
  user_id: number
  username: string
  nickname: string
  total_workdays: number
  total_amount: number
  settled_amount: number
  unpaid_amount: number
}

export interface WageDistributionDetail {
  id: number
  project_id: number
  project_name: string
  subproject_id: number
  subproject_name: string
  user_id: number
  username: string
  amount: number
  status: string
  created_at: string
  settled_at?: string
}

export interface CreateWageDistributionParams {
  subproject_id: number
  user_id: number
  workdays: number
  amount: number
  remark?: string
}

export interface CreateBatchWageDistributionsParams {
  subproject_id: number
  distribution_type: 'average' | 'by_workday'
}

export interface CreateWageSettlementParams {
  start_month: string
  end_month: string
  remark?: string
}

export interface CreateWageAdvanceParams {
  user_id: number
  advance_amount: number
  advance_date: string
  remark?: string
}

export interface UpdateWageDistributionParams {
  id: number
  workdays?: number
  amount?: number
  status?: string
}

export interface UpdateWageAdvanceParams {
  id: number
  settled?: boolean
  settlement_id?: number
  remark?: string
}

const wageApi = {
  getDistributions: (params: WageDistributionListParams) => {
    return request.get('/v1/settlements/wage_distributions', { params })
  },

  getDistributionById: (id: number) => {
    return request.get(`/v1/settlements/wage_distributions/${id}`)
  },

  createDistribution: (data: CreateWageDistributionParams) => {
    return request.post('/v1/settlements/wage-distributions', data)
  },

  createBatchDistributions: (data: CreateBatchWageDistributionsParams) => {
    return request.post('/v1/settlements/wage-distributions/batch', data)
  },

  updateDistribution: (data: UpdateWageDistributionParams) => {
    return request.put(`/v1/settlements/wage_distributions/${data.id}`, data)
  },

  deleteDistribution: (id: number) => {
    return request.delete(`/v1/settlements/wage_distributions/${id}`)
  },

  getSettlements: (params: WageSettlementListParams) => {
    return request.get('/v1/settlements', { params })
  },

  getSettlementById: (id: number) => {
    return request.get(`/v1/settlements/${id}`)
  },

  createSettlement: (data: CreateWageSettlementParams) => {
    return request.post('/v1/settlements', data)
  },

  deleteSettlement: (id: number) => {
    return request.delete(`/v1/settlements/${id}`)
  },

  getAdvances: (params: WageAdvanceListParams) => {
    return request.get('/v1/settlements/advances', { params })
  },

  getAdvanceById: (id: number) => {
    return request.get(`/v1/settlements/advances/${id}`)
  },

  createAdvance: (data: CreateWageAdvanceParams) => {
    return request.post('/v1/settlements/advances', data)
  },

  updateAdvance: (data: UpdateWageAdvanceParams) => {
    return request.put(`/v1/settlements/advances/${data.id}`, data)
  },

  deleteAdvance: (id: number) => {
    return request.delete(`/v1/settlements/advances/${id}`)
  },

  getStatistics: () => {
    const now = new Date()
    const year = now.getFullYear()
    const month = now.getMonth() + 1
    return request.get('/v1/statistics/monthly/wages', { params: { year, month } })
  },

  getUserWageSummary: (params: { start_date?: string; end_date?: string }) => {
    return request.get('/v1/statistics/user/wages', { params })
  },

  getWageDistributions: (params: { start_date?: string; end_date?: string }) => {
    return request.get('/v1/statistics/wage/distributions', { params })
  },

  getMultiMonthStatistics: (months: string) => {
    return request.get('/v1/statistics/multi-month', { params: { months } })
  },

  getSettlementDetails: (id: number) => {
    return request.get(`/v1/settlements/${id}`)
  },

  getProjectStatusStatistics: () => {
    return request.get('/v1/statistics/project-status')
  }
}

export default wageApi
