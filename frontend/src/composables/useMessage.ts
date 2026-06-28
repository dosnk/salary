import { ref, computed, reactive } from 'vue'
import { showToast, showSuccessToast, showConfirmDialog } from 'vant'
import { messagesApi, type Message, type MessageListParams } from '@/api/messages'

export function useMessage() {
  const messages = ref<Message[]>([])
  const loading = ref(false)
  const refreshing = ref(false)
  const finished = ref(false)
  const errorCount = ref(0)
  const MAX_ERROR_COUNT = 3
  
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const unreadCount = ref(0)
  const hasUnread = computed(() => unreadCount.value > 0)
  
  const activeTab = ref('all')
  const showDetail = ref(false)
  const selectedMessage = ref<Message | null>(null)
  const markingAll = ref(false)

  const loadMessages = async (isRefresh = false) => {
    if (loading.value) return
    
    try {
      loading.value = true
      
      if (isRefresh) {
        pagination.page = 1
        finished.value = false
        messages.value = []
        errorCount.value = 0
      }

      const params: MessageListParams = {
        page: pagination.page,
        size: pagination.size
      }
      
      if (activeTab.value === 'unread') {
        (params as any).isRead = false
      }

      const response = await messagesApi.getMessages(params)
      
      if (isRefresh || pagination.page === 1) {
        messages.value = response.list
      } else {
        messages.value = [...messages.value, ...response.list]
      }
      
      pagination.total = response.total
      pagination.page++
      
      if (messages.value.length >= response.total) {
        finished.value = true
      }
      
      errorCount.value = 0
    } catch (error: any) {
      errorCount.value++
      const errorMsg = error?.response?.data?.message || error?.message || '获取消息失败'
      showToast({ type: 'fail', message: errorMsg })
      
      if (errorCount.value >= MAX_ERROR_COUNT) {
        finished.value = true
      }
    } finally {
      loading.value = false
      refreshing.value = false
    }
  }

  const loadUnreadCount = async () => {
    try {
      const response = await messagesApi.getUnreadCount()
      unreadCount.value = response.count
    } catch (error) {
      console.error('获取未读消息数量失败:', error)
    }
  }

  const handleMessageClick = async (message: Message, onUnreadChange?: () => void) => {
    selectedMessage.value = message
    showDetail.value = true
    if (!message.is_read) {
      try {
        await messagesApi.markAsRead(message.id)
        message.is_read = true
        unreadCount.value = Math.max(0, unreadCount.value - 1)
        onUnreadChange?.()
      } catch (error) {
        console.error('标记消息已读失败:', error)
      }
    }
  }

  const markAsRead = async (onUnreadChange?: () => void) => {
    if (!selectedMessage.value) return false
    try {
      await messagesApi.markAsRead(selectedMessage.value.id)
      selectedMessage.value.is_read = true
      const msg = messages.value.find(m => m.id === selectedMessage.value?.id)
      if (msg) msg.is_read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
      onUnreadChange?.()
      showSuccessToast('已标记为已读')
      showDetail.value = false
      return true
    } catch (error: any) {
      const errorMsg = error?.response?.data?.message || '标记失败'
      showToast({ type: 'fail', message: errorMsg })
      return false
    }
  }

  const markAllAsRead = async () => {
    markingAll.value = true
    try {
      const response = await messagesApi.markAllAsRead()
      
      messages.value.forEach(m => {
        m.is_read = true
      })
      unreadCount.value = 0
      
      showSuccessToast(`已标记 ${response.count} 条消息为已读`)
      return true
    } catch (error: any) {
      const errorMsg = error?.response?.data?.message || '标记失败'
      showToast({ type: 'fail', message: errorMsg })
      return false
    } finally {
      markingAll.value = false
    }
  }

  const deleteMessage = async (message: Message, onUnreadChange?: () => void) => {
    try {
      await showConfirmDialog({
        title: '确认删除',
        message: '确定要删除这条消息吗？'
      })
      
      await messagesApi.deleteMessage(message.id)
      
      const index = messages.value.findIndex(m => m.id === message.id)
      if (index > -1) {
        if (!message.is_read) {
          unreadCount.value = Math.max(0, unreadCount.value - 1)
          onUnreadChange?.()
        }
        messages.value.splice(index, 1)
      }
      
      showSuccessToast('删除成功')
      return true
    } catch (error: any) {
      if (error === 'cancel') return false
      
      const errorMsg = error?.response?.data?.message || '删除失败'
      showToast({ type: 'fail', message: errorMsg })
      return false
    }
  }

  const deleteSelectedMessage = async (onUnreadChange?: () => void) => {
    if (!selectedMessage.value) return false
    try {
      await showConfirmDialog({
        title: '确认删除',
        message: '确定要删除这条消息吗？'
      })
      
      await messagesApi.deleteMessage(selectedMessage.value.id)
      
      const index = messages.value.findIndex(m => m.id === selectedMessage.value?.id)
      if (index > -1) {
        if (!selectedMessage.value.is_read) {
          unreadCount.value = Math.max(0, unreadCount.value - 1)
          onUnreadChange?.()
        }
        messages.value.splice(index, 1)
      }
      
      showSuccessToast('删除成功')
      showDetail.value = false
      return true
    } catch (error: any) {
      if (error === 'cancel') return false
      
      const errorMsg = error?.response?.data?.message || '删除失败'
      showToast({ type: 'fail', message: errorMsg })
      return false
    }
  }

  const onRefresh = async () => {
    refreshing.value = true
    await loadMessages(true)
  }

  const onTabChange = () => {
    messages.value = []
    pagination.page = 1
    finished.value = false
    loading.value = true
    loadMessages()
  }

  const reset = () => {
    messages.value = []
    loading.value = false
    refreshing.value = false
    finished.value = false
    errorCount.value = 0
    pagination.page = 1
    pagination.total = 0
    unreadCount.value = 0
    activeTab.value = 'all'
    showDetail.value = false
    selectedMessage.value = null
  }

  return {
    messages,
    loading,
    refreshing,
    finished,
    pagination,
    unreadCount,
    hasUnread,
    activeTab,
    showDetail,
    selectedMessage,
    markingAll,
    loadMessages,
    loadUnreadCount,
    handleMessageClick,
    markAsRead,
    markAllAsRead,
    deleteMessage,
    deleteSelectedMessage,
    onRefresh,
    onTabChange,
    reset
  }
}
