import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type UserInfo } from '@/api/auth'
import { messagesApi } from '@/api/messages'
import dictionaryApi from '@/api/dictionary'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(
    localStorage.getItem('userInfo') ? JSON.parse(localStorage.getItem('userInfo')!) : null
  )
  const unreadCount = ref<number>(0)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'admin')
  const isDocumenter = computed(() => userInfo.value?.role === 'documenter')
  const isConstructor = computed(() => userInfo.value?.role === 'constructor')

  const login = async (username: string, password: string, rememberMe: boolean = false) => {
    const data = await authApi.login({ username, password, rememberMe }) as unknown as { token: string; user: UserInfo }
    token.value = data.token
    userInfo.value = data.user
    localStorage.setItem('token', data.token)
    localStorage.setItem('userInfo', JSON.stringify(data.user))
    
    // 登录成功后自动刷新字典缓存
    try {
      await dictionaryApi.getDictionaryWithCache(true)
    } catch (error) {
      console.error('刷新字典缓存失败:', error)
    }
    
    await fetchUnreadCount()
    return data
  }

  const logout = async () => {
    try {
      await authApi.logout()
    } catch (error) {
      console.error('登出失败:', error)
    } finally {
      token.value = ''
      userInfo.value = null
      unreadCount.value = 0
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }

  const fetchUserInfo = async () => {
    const data = await authApi.getUserInfo() as unknown as UserInfo
    userInfo.value = data
    localStorage.setItem('userInfo', JSON.stringify(data))
    return data
  }

  const fetchUnreadCount = async () => {
    try {
      const response = await messagesApi.getUnreadCount()
      unreadCount.value = response.count
    } catch (error) {
      console.error('获取未读消息数量失败:', error)
    }
  }

  const decrementUnreadCount = () => {
    if (unreadCount.value > 0) {
      unreadCount.value--
    }
  }

  const clearUnreadCount = () => {
    unreadCount.value = 0
  }

  return {
    token,
    userInfo,
    unreadCount,
    isLoggedIn,
    isAdmin,
    isDocumenter,
    isConstructor,
    login,
    logout,
    fetchUserInfo,
    fetchUnreadCount,
    decrementUnreadCount,
    clearUnreadCount
  }
})
