import request from '@/utils/request'

export interface Message {
  id: number
  user_id: number
  title: string
  content: string
  type: string
  is_read: boolean
  related_type?: string
  related_id?: number
  created_at: string
  created_by?: number
  creator_name?: string
}

export interface MessageListParams {
  page?: number
  size?: number
  isRead?: boolean
  type?: string
}

export interface MessageListResponse {
  list: Message[]
  total: number
  page: number
  size: number
  hasNext: boolean
}

export interface UnreadCountResponse {
  count: number
}

export const messagesApi = {
  getMessages: (params: MessageListParams) => {
    return request.get<MessageListResponse>('/v1/messages', { params })
  },

  getUnreadCount: () => {
    return request.get<UnreadCountResponse>('/v1/messages/unread/count')
  },

  markAsRead: (id: number) => {
    return request.put<Message>(`/v1/messages/${id}/read`)
  },

  markAllAsRead: () => {
    return request.put<{ count: number }>('/v1/messages/read-all')
  },

  deleteMessage: (id: number) => {
    return request.delete<{ message: string }>(`/v1/messages/${id}`)
  }
}
