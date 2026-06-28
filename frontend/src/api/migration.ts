import request from '@/utils/request'

export interface OldProject {
  id: number
  name: string
  total_amount: number
  distribution_method: string
  created_by: number
  created_at: string
  updated_at: string
  item_count: number
}

export interface OldProjectItem {
  id: number
  project_id: number
  space_type: string
  construction_plan: string
  length: number
  width: number
  amount: number
  note: string
  created_at: string
  updated_at: string
}

export interface OldAttachment {
  id: number
  project_id: number
  name: string
  path: string
  type: string
  size: number
  uploaded_by: number
  uploaded_at: string
}

export interface OldProjectDetail {
  project: OldProject
  projectItems: OldProjectItem[]
  attachments: OldAttachment[]
}

export interface MigrationProgress {
  total: number
  completed: number
  failed: number
  currentProject: string | null
  status: 'idle' | 'running' | 'completed' | 'error'
  logs: Array<{
    timestamp: string
    message: string
    type: 'info' | 'success' | 'error'
  }>
}

export interface MigrationLoginResponse {
  token: string
  user: {
    id: number
    username: string
    nickname: string
    role: string
  }
}

export interface MigrationResult {
  success: boolean
  newProjectId?: number
  message: string
}

export interface DatabaseCheckResult {
  databasePath: string
  tables: Record<string, {
    columns: Array<{
      name: string
      type: string
      notNull: boolean
      primaryKey: boolean
      defaultValue: any
    }>
    recordCount: number
  }>
  sampleProjects: any[]
  sampleSubprojects: any[]
}

export const migrationApi = {
  login: (username: string, password: string) => {
    return request.post<MigrationLoginResponse>('/v1/migration/login', {
      username,
      password
    })
  },

  getProjects: () => {
    return request.get<OldProject[]>('/v1/migration/projects')
  },

  getProjectById: (id: number) => {
    return request.get<OldProjectDetail>(`/v1/migration/project/${id}`)
  },

  migrateProject: (projectId: number, token: string) => {
    return request.post<MigrationResult>('/v1/migration/migrate-project', {
      projectId,
      token
    })
  },

  migrateAll: (token: string) => {
    return request.post<{
      success: boolean
      total: number
      completed: number
      failed: number
    }>('/v1/migration/migrate-all', {
      token
    })
  },

  getProgress: () => {
    return request.get<MigrationProgress>('/v1/migration/progress')
  },

  checkDatabase: (token: string) => {
    return request.get<DatabaseCheckResult>('/v1/migration/check-database', {
      headers: {
        Authorization: `Bearer ${token}`
      }
    })
  }
}
