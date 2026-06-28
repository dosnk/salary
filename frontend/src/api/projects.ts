import request from '@/utils/request'

export interface Project {
  id: number
  name: string
  description?: string
  remark?: string
  user_id: number
  total_amount: number
  salary_distribution: 'average' | 'work_days'
  total_work_days?: number
  status: 'constructing' | 'completed' | 'preparing' | 'canceled'
  settlement_status?: 'settling' | 'unsettled' | 'settled'
  settlement_id?: number
  is_settled: boolean
  created_at: string
  updated_at: string
  workers?: Array<{
    id: number
    nickname?: string
    username: string
    workdays?: number
  }>
  files?: Array<{
    id: number
    filename: string
    original_name?: string
    originalName?: string
    path: string
    url?: string
    size: number
    type: string
    createdAt?: string
    created_at?: string
  }>
  files_count?: number
  sub_projects?: SubProject[]
  constructors?: User[]
}

export interface SubProject {
  id: number
  project_id: number
  space_type_id?: number
  construction_plan_id?: number
  length: number
  width: number
  quantity: number
  amount: number
  remark?: string
  status?: string
  created_by?: number
  created_at: string
  updated_at: string
  space_type_name?: string
  construction_plan_name?: string
  unit?: string
  price?: number
  // 兼容旧格式
  space_type?: string
  construction_scheme?: string
  unit_price?: number
  unit_type?: string
}

export interface User {
  id: number
  username: string
  nickname?: string
  phone?: string
  role: 'admin' | 'documenter' | 'constructor'
  created_at: string
  workdays?: number
}

export interface File {
  id: number
  filename: string
  original_name: string
  path: string
  url: string
  size: number
  type: string
  created_at: string
}

export interface ProjectDetail extends Project {
  sub_projects: SubProject[]
  constructors: User[]
  files: File[]
}

export interface ProjectListParams {
  page?: number
  size?: number
  month?: string
  yearMonth?: string
  year?: string
  keyword?: string
  sort?: string
  status?: 'constructing' | 'completed' | 'preparing' | 'canceled'
  creatorNickname?: string
  workerNickname?: string
  startDate?: string
  endDate?: string
  settlementStatus?: 'settling' | 'unsettled' | 'settled'
  signal?: AbortSignal
}

export interface ProjectListResponse {
  list: Project[]
  total: number
  page: number
  size: number
  hasNext: boolean
}

export interface CreateProjectParams {
  name?: string
  remark?: string
  status?: 'constructing' | 'completed' | 'preparing' | 'canceled'
  settlement_status?: 'settling' | 'unsettled' | 'settled'
  spaceType?: string
  constructionScheme?: string
  length?: number
  width?: number
  salaryDistribution?: 'average' | 'work_days'
  constructors?: Array<{ userId: number }>
}

export interface UpdateProjectParams {
  name?: string
  remark?: string
  status?: 'constructing' | 'completed' | 'preparing' | 'canceled'
}

export interface UpdateSubProjectParams {
  spaceType: string
  constructionScheme: string
  length: number
  width: number
  salaryDistribution?: 'average' | 'work_days'
  remark?: string
}

export interface TransferSubProjectParams {
  toUserId: number
  transferReason?: string
}

export interface SubProjectTransfer {
  id: number
  subproject_id: number
  from_user_id: number
  from_username: string
  from_nickname: string
  to_user_id: number
  to_username: string
  to_nickname: string
  transfer_reason: string
  transferred_at: string
  transferred_by: number
  transferred_by_username: string
  transferred_by_nickname: string
}

export interface ProjectHistory {
  id: number
  project_id: number
  action: string
  action_name: string
  description: string
  performed_by: number
  username: string
  nickname: string
  created_at: string
}

export const projectsApi = {
  getProjects: (params: ProjectListParams) => {
    const { signal, ...restParams } = params
    return request.get<ProjectListResponse>('/v1/projects', { params: restParams, signal })
  },

  getProject: (id: number) => {
    return request.get<ProjectDetail>(`/v1/projects/${id}`)
  },

  createProject: (params: CreateProjectParams) => {
    return request.post<Project>('/v1/projects', params)
  },

  updateProject: (id: number, params: UpdateProjectParams) => {
    return request.put<Project>(`/v1/projects/${id}`, params)
  },

  uploadFile: (projectId: number, fileData: any) => {
    return request.post(`/v1/projects/${projectId}/files`, fileData)
  },

  updateSubProject: (projectId: number, subProjectId: number, params: UpdateSubProjectParams) => {
    return request.put(`/v1/projects/${projectId}/subprojects/${subProjectId}`, params)
  },

  deleteSubProject: (projectId: number, subProjectId: number) => {
    return request.delete(`/v1/projects/${projectId}/subprojects/${subProjectId}`)
  },

  deleteProject: (id: number) => {
    return request.delete(`/v1/projects/${id}`)
  },

  transferSubProject: (projectId: number, subProjectId: number, params: TransferSubProjectParams) => {
    return request.post(`/v1/projects/${projectId}/subprojects/${subProjectId}/transfer`, params)
  },

  getSubProjectTransfers: (projectId: number, subProjectId: number) => {
    return request.get<SubProjectTransfer[]>(`/v1/projects/${projectId}/subprojects/${subProjectId}/transfers`)
  },

  getProjectWorkers: (projectId: number) => {
    return request.get<User[]>(`/v1/projects/${projectId}/workers`)
  },

  getProjectHistory: (projectId: number) => {
    return request.get<ProjectHistory[]>(`/v1/projects/${projectId}/history`)
  }
}
