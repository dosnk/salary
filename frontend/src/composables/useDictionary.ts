import { ref, computed } from 'vue'
import dictionaryApi, { 
  type SpaceType, 
  type ConstructionPlan,
  type ProjectStatus,
  type SettlementStatus
} from '@/api/dictionary'
import { type ConstructorUser } from '@/api/users'

export function useDictionary() {
  const loading = ref(false)
  const error = ref<string | null>(null)
  
  const spaceTypes = ref<SpaceType[]>([])
  const constructionPlans = ref<ConstructionPlan[]>([])
  const constructorUsers = ref<ConstructorUser[]>([])
  const projectStatuses = ref<ProjectStatus[]>([])
  const settlementStatuses = ref<SettlementStatus[]>([])

  const isLoaded = computed(() => 
    spaceTypes.value.length > 0 || 
    constructionPlans.value.length > 0
  )

  const loadDictionary = async (forceRefresh = false) => {
    if (loading.value) return
    
    try {
      loading.value = true
      error.value = null
      
      const data = await dictionaryApi.getDictionaryWithCache(forceRefresh)
      
      spaceTypes.value = data.spaceTypes
      constructionPlans.value = data.constructionPlans
      constructorUsers.value = data.constructorUsers
      projectStatuses.value = data.projectStatuses
      settlementStatuses.value = data.settlementStatuses
    } catch (err: any) {
      error.value = err?.message || '加载字典数据失败'
    } finally {
      loading.value = false
    }
  }

  const getSpaceTypeName = (id: number | null | undefined): string => {
    if (!id) return '-'
    const found = spaceTypes.value.find(s => s.id === id)
    return found?.name || '-'
  }

  const getConstructionPlanName = (id: number | null | undefined): string => {
    if (!id) return '-'
    const found = constructionPlans.value.find(p => p.id === id)
    return found?.name || '-'
  }

  const getConstructionPlan = (id: number | null | undefined): ConstructionPlan | undefined => {
    if (!id) return undefined
    return constructionPlans.value.find(p => p.id === id)
  }

  const getUserName = (id: number | null | undefined): string => {
    if (!id) return '-'
    const found = constructorUsers.value.find(u => u.id === id)
    return found?.nickname || found?.username || '-'
  }

  const getProjectStatusName = (code: string | null | undefined): string => {
    if (!code) return '-'
    const found = projectStatuses.value.find(s => s.code === code)
    return found?.name || code
  }

  const getSettlementStatusName = (code: string | null | undefined): string => {
    if (!code) return '-'
    const found = settlementStatuses.value.find(s => s.code === code)
    return found?.name || code
  }

  const clearCache = () => {
    dictionaryApi.clearCache()
  }

  const reset = () => {
    spaceTypes.value = []
    constructionPlans.value = []
    constructorUsers.value = []
    projectStatuses.value = []
    settlementStatuses.value = []
    error.value = null
  }

  return {
    loading,
    error,
    spaceTypes,
    constructionPlans,
    constructorUsers,
    projectStatuses,
    settlementStatuses,
    isLoaded,
    loadDictionary,
    getSpaceTypeName,
    getConstructionPlanName,
    getConstructionPlan,
    getUserName,
    getProjectStatusName,
    getSettlementStatusName,
    clearCache,
    reset
  }
}
