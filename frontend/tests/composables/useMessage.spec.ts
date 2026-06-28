import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useMessage } from '@/composables/useMessage'
import { messagesApi } from '@/api/messages'

vi.mock('@/api/messages', () => ({
  messagesApi: {
    getMessages: vi.fn(),
    getUnreadCount: vi.fn(),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
    deleteMessage: vi.fn()
  }
}))

vi.mock('vant', () => ({
  showToast: vi.fn(),
  showSuccessToast: vi.fn(),
  showConfirmDialog: vi.fn().mockResolvedValue(true)
}))

describe('useMessage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('loadMessages', () => {
    it('should load messages successfully', async () => {
      const mockMessages = [
        { id: 1, title: '消息1', content: '内容1', is_read: false, created_at: '2026-03-02T10:00:00' },
        { id: 2, title: '消息2', content: '内容2', is_read: true, created_at: '2026-03-01T10:00:00' }
      ]
      
      vi.mocked(messagesApi.getMessages).mockResolvedValue({
        list: mockMessages,
        total: 2
      })
      
      const { messages, loading, finished, loadMessages } = useMessage()
      
      await loadMessages()
      
      expect(messages.value).toEqual(mockMessages)
      expect(loading.value).toBe(false)
      expect(finished.value).toBe(true)
    })

    it('should append messages on subsequent loads', async () => {
      const firstBatch = [
        { id: 1, title: '消息1', content: '内容1', is_read: false, created_at: '2026-03-02T10:00:00' }
      ]
      const secondBatch = [
        { id: 2, title: '消息2', content: '内容2', is_read: true, created_at: '2026-03-01T10:00:00' }
      ]
      
      vi.mocked(messagesApi.getMessages)
        .mockResolvedValueOnce({ list: firstBatch, total: 2 })
        .mockResolvedValueOnce({ list: secondBatch, total: 2 })
      
      const { messages, loadMessages } = useMessage()
      
      await loadMessages()
      expect(messages.value).toEqual(firstBatch)
      
      await loadMessages()
      expect(messages.value).toEqual([...firstBatch, ...secondBatch])
    })

    it('should handle errors', async () => {
      vi.mocked(messagesApi.getMessages).mockRejectedValue(new Error('网络错误'))
      
      const { messages, finished, loadMessages } = useMessage()
      
      await loadMessages()
      
      expect(messages.value).toEqual([])
      expect(finished.value).toBe(false)
    })
  })

  describe('loadUnreadCount', () => {
    it('should load unread count successfully', async () => {
      vi.mocked(messagesApi.getUnreadCount).mockResolvedValue({ count: 5 })
      
      const { unreadCount, loadUnreadCount } = useMessage()
      
      await loadUnreadCount()
      
      expect(unreadCount.value).toBe(5)
    })
  })

  describe('markAsRead', () => {
    it('should mark message as read', async () => {
      vi.mocked(messagesApi.markAsRead).mockResolvedValue({})
      
      const { messages, unreadCount, markAsRead } = useMessage()
      messages.value = [
        { id: 1, title: '消息1', content: '内容1', is_read: false, created_at: '2026-03-02T10:00:00' }
      ]
      unreadCount.value = 1
      
      const result = await markAsRead(1)
      
      expect(result).toBe(true)
      expect(messages.value[0].is_read).toBe(true)
      expect(unreadCount.value).toBe(0)
    })

    it('should return false on error', async () => {
      vi.mocked(messagesApi.markAsRead).mockRejectedValue(new Error('标记失败'))
      
      const { markAsRead } = useMessage()
      
      const result = await markAsRead(1)
      
      expect(result).toBe(false)
    })
  })

  describe('markAllAsRead', () => {
    it('should mark all messages as read', async () => {
      vi.mocked(messagesApi.markAllAsRead).mockResolvedValue({ count: 3 })
      
      const { messages, unreadCount, markAllAsRead } = useMessage()
      messages.value = [
        { id: 1, title: '消息1', content: '内容1', is_read: false, created_at: '2026-03-02T10:00:00' },
        { id: 2, title: '消息2', content: '内容2', is_read: false, created_at: '2026-03-01T10:00:00' }
      ]
      unreadCount.value = 2
      
      const result = await markAllAsRead()
      
      expect(result).toBe(true)
      expect(messages.value.every(m => m.is_read)).toBe(true)
      expect(unreadCount.value).toBe(0)
    })
  })

  describe('deleteMessage', () => {
    it('should delete message successfully', async () => {
      vi.mocked(messagesApi.deleteMessage).mockResolvedValue({})
      
      const { messages, unreadCount, deleteMessage } = useMessage()
      messages.value = [
        { id: 1, title: '消息1', content: '内容1', is_read: false, created_at: '2026-03-02T10:00:00' },
        { id: 2, title: '消息2', content: '内容2', is_read: true, created_at: '2026-03-01T10:00:00' }
      ]
      unreadCount.value = 1
      
      const result = await deleteMessage(1)
      
      expect(result).toBe(true)
      expect(messages.value.length).toBe(1)
      expect(messages.value[0].id).toBe(2)
      expect(unreadCount.value).toBe(0)
    })
  })

  describe('reset', () => {
    it('should reset all state', async () => {
      vi.mocked(messagesApi.getMessages).mockResolvedValue({
        list: [{ id: 1, title: '消息1', content: '内容1', is_read: false, created_at: '2026-03-02T10:00:00' }],
        total: 1
      })
      
      const { messages, loading, finished, unreadCount, loadMessages, reset } = useMessage()
      
      await loadMessages()
      unreadCount.value = 5
      
      reset()
      
      expect(messages.value).toEqual([])
      expect(loading.value).toBe(false)
      expect(finished.value).toBe(false)
      expect(unreadCount.value).toBe(0)
    })
  })
})
