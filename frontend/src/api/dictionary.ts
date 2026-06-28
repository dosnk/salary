import request from '@/utils/request'
import { usersApi, type ConstructorUser } from './users'

export interface SpaceType {
  id: number
  name: string
}

export interface CreateSpaceTypeParams {
  name: string
}

export interface UpdateSpaceTypeParams {
  name: string
}

export interface ConstructionPlan {
  id: number
  name: string
  unit: string
  price: number
}

export interface CreateConstructionPlanParams {
  name: string
  unit: string
  price: number
}

export interface UpdateConstructionPlanParams {
  name: string
  unit: string
  price: number
}

export interface WageDistributionType {
  id: number
  name: string
  code: string
}

export interface ProjectStatus {
  code: string
  name: string
}

export interface SettlementStatus {
  code: string
  name: string
}

export interface ConstructionUnit {
  code: string
  name: string
}

export interface DictionaryData {
  spaceTypes: SpaceType[]
  constructionPlans: ConstructionPlan[]
  wageDistributionTypes: WageDistributionType[]
  constructorUsers: ConstructorUser[]
  projectStatuses: ProjectStatus[]
  settlementStatuses: SettlementStatus[]
}

const CACHE_KEY = 'dictionary_cache'
const CACHE_VERSION = 2
const CACHE_EXPIRY = 60 * 60 * 1000

const dictionaryApi = {
  getSpaceTypes: async () => {
    return request.get<SpaceType[]>('/v1/dictionary/space-types')
  },

  createSpaceType: (params: CreateSpaceTypeParams) => {
    return request.post<SpaceType>('/v1/dictionary/space-types', params)
  },

  updateSpaceType: (id: number, params: UpdateSpaceTypeParams) => {
    return request.put<SpaceType>(`/v1/dictionary/space-types/${id}`, params)
  },

  deleteSpaceType: (id: number) => {
    return request.delete(`/v1/dictionary/space-types/${id}`)
  },

  getConstructionPlans: async () => {
    return request.get<ConstructionPlan[]>('/v1/dictionary/construction-plans')
  },

  createConstructionPlan: (params: CreateConstructionPlanParams) => {
    return request.post<ConstructionPlan>('/v1/dictionary/construction-plans', params)
  },

  updateConstructionPlan: (id: number, params: UpdateConstructionPlanParams) => {
    return request.put<ConstructionPlan>(`/v1/dictionary/construction-plans/${id}`, params)
  },

  deleteConstructionPlan: (id: number) => {
    return request.delete(`/v1/dictionary/construction-plans/${id}`)
  },

  getWageDistributionTypes: async () => {
    return request.get<WageDistributionType[]>('/v1/dictionary/wage-distribution-types')
  },

  getProjectStatuses: async () => {
    return request.get<ProjectStatus[]>('/v1/dictionary/project-statuses')
  },

  getSettlementStatuses: async () => {
    return request.get<SettlementStatus[]>('/v1/dictionary/settlement-statuses')
  },

  getConstructionUnits: async () => {
    return request.get<ConstructionUnit[]>('/v1/dictionary/construction-units')
  },

  getAllDictionary: async () => {
    try {
      const results = await Promise.allSettled([
        dictionaryApi.getSpaceTypes(),
        dictionaryApi.getConstructionPlans(),
        dictionaryApi.getWageDistributionTypes(),
        usersApi.getConstructors(),
        dictionaryApi.getProjectStatuses(),
        dictionaryApi.getSettlementStatuses()
      ])

      const spaceTypes = results[0].status === 'fulfilled' ? results[0].value : []
      const constructionPlans = results[1].status === 'fulfilled' ? results[1].value : []
      const wageDistributionTypes = results[2].status === 'fulfilled' ? results[2].value : []
      const constructorUsers = results[3].status === 'fulfilled' ? results[3].value : []
      const projectStatuses = results[4].status === 'fulfilled' ? results[4].value : []
      const settlementStatuses = results[5].status === 'fulfilled' ? results[5].value : []

      return {
        spaceTypes: Array.isArray(spaceTypes) ? spaceTypes : [],
        constructionPlans: Array.isArray(constructionPlans) ? constructionPlans : [],
        wageDistributionTypes: Array.isArray(wageDistributionTypes) ? wageDistributionTypes : [],
        constructorUsers: Array.isArray(constructorUsers) ? constructorUsers : [],
        projectStatuses: Array.isArray(projectStatuses) ? projectStatuses : [],
        settlementStatuses: Array.isArray(settlementStatuses) ? settlementStatuses : []
      }
    } catch (error) {
      console.error('获取字典数据失败:', error)
      throw error
    }
  },

  getCachedDictionary: (): DictionaryData | null => {
    try {
      const cached = localStorage.getItem(CACHE_KEY)
      if (!cached) return null

      const { data, timestamp, version } = JSON.parse(cached)
      const now = Date.now()

      if (now - timestamp > CACHE_EXPIRY) {
        localStorage.removeItem(CACHE_KEY)
        return null
      }

      if (version !== CACHE_VERSION) {
        localStorage.removeItem(CACHE_KEY)
        return null
      }

      return data
    } catch (error) {
      console.error('读取缓存失败:', error)
      return null
    }
  },

  setCachedDictionary: (data: DictionaryData) => {
    try {
      const cacheData = {
        data,
        timestamp: Date.now(),
        version: CACHE_VERSION
      }
      localStorage.setItem(CACHE_KEY, JSON.stringify(cacheData))
    } catch (error) {
      console.error('写入缓存失败:', error)
    }
  },

  clearCache: () => {
    try {
      localStorage.removeItem(CACHE_KEY)
    } catch (error) {
      console.error('清除缓存失败:', error)
    }
  },

  getDictionaryWithCache: async (forceRefresh = false): Promise<DictionaryData> => {
    if (!forceRefresh) {
      const cached = dictionaryApi.getCachedDictionary()
      if (cached) {
        return cached
      }
    }

    const data = await dictionaryApi.getAllDictionary()
    dictionaryApi.setCachedDictionary(data)
    return data
  }
}

export default dictionaryApi
