<template>
  <div class="messages">
    <van-nav-bar fixed>
      <template #left>
        <span class="copyright-info">三人行装修管理系统</span>
      </template>
      <template #right>
        <div class="nav-right">
          <span class="user-info">{{ currentUser?.nickname || currentUser?.username || '未登录' }}</span>
          <van-badge :content="unreadCount > 0 ? unreadCount : undefined" :show-zero="false" max="99">
            <van-icon name="envelop-o" size="20" color="#fff" />
          </van-badge>
        </div>
      </template>
    </van-nav-bar>

    <div class="content-wrapper">
      <div class="action-bar" v-if="unreadCount > 0">
        <van-button type="primary" size="small" @click="handleMarkAllAsRead" :loading="markingAll">
          一键标记全部已读
        </van-button>
      </div>
      <van-tabs v-model:active="activeTab" sticky @change="onTabChange">
        <van-tab title="全部消息" name="all">
          <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
            <van-list
              v-model:loading="loading"
              :finished="finished"
              finished-text="没有更多了"
              @load="onLoad"
            >
              <div
                v-for="message in messages"
                :key="message.id"
                :class="['message-card', { 'unread': !message.is_read }]"
                @click="handleMessageClick(message)"
              >
                <div class="message-header">
                  <div class="message-title">
                    <span>{{ message.title }}</span>
                    <van-tag v-if="!message.is_read" type="danger">未读</van-tag>
                  </div>
                  <van-icon name="delete-o" class="delete-icon" @click.stop="handleDelete(message)" />
                </div>
                <div class="message-content">{{ message.content }}</div>
                <div class="message-footer">
                  <span class="message-time">{{ formatDate(message.created_at) }}</span>
                  <span class="message-sender">发送人：{{ message.creator_name || '系统' }}</span>
                </div>
              </div>
            </van-list>
          </van-pull-refresh>
        </van-tab>

        <van-tab title="未读消息" name="unread">
          <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
            <van-list
              v-model:loading="loading"
              :finished="finished"
              finished-text="没有更多了"
              @load="onLoad"
            >
              <div
                v-for="message in messages"
                :key="message.id"
                class="message-card unread"
                @click="handleMessageClick(message)"
              >
                <div class="message-header">
                  <div class="message-title">
                    <span>{{ message.title }}</span>
                    <van-tag type="danger">未读</van-tag>
                  </div>
                  <van-icon name="delete-o" class="delete-icon" @click.stop="handleDelete(message)" />
                </div>
                <div class="message-content">{{ message.content }}</div>
                <div class="message-footer">
                  <span class="message-time">{{ formatDate(message.created_at) }}</span>
                  <span class="message-sender">发送人：{{ message.creator_name || '系统' }}</span>
                </div>
              </div>
            </van-list>
          </van-pull-refresh>
        </van-tab>
      </van-tabs>

      <van-empty v-if="messages.length === 0 && !loading" description="暂无消息" />
    </div>

    <van-tabbar v-model="activeTabbar" active-color="#52c41a" inactive-color="#000">
      <van-tabbar-item icon="home-o" to="/dashboard">主页</van-tabbar-item>
      <van-tabbar-item icon="apps-o" to="/project">工程管理</van-tabbar-item>
      <van-tabbar-item icon="chart-trending-o" to="/statistic">统计</van-tabbar-item>
      <van-tabbar-item icon="user-o" to="/profile">我的</van-tabbar-item>
    </van-tabbar>

    <!-- 消息详情弹窗 -->
    <van-popup v-model:show="showDetail" round position="bottom" :style="{ height: '60%' }">
      <div class="message-detail">
        <div class="detail-header">
          <h3>{{ selectedMessage?.title }}</h3>
          <van-icon name="close" @click="showDetail = false" />
        </div>
        <div class="detail-content">
          <p>{{ selectedMessage?.content }}</p>
        </div>
        <div class="detail-meta">
          <div class="meta-item">
            <van-icon name="clock-o" />
            <span>{{ selectedMessage ? formatDate(selectedMessage.created_at) : '' }}</span>
          </div>
          <div class="meta-item">
            <van-icon name="user-o" />
            <span>发送人：{{ selectedMessage?.creator_name || '系统' }}</span>
          </div>
        </div>
        <div class="detail-actions">
          <van-button v-if="!selectedMessage?.is_read" block type="primary" @click="handleMarkAsRead">标记为已读</van-button>
          <van-button block @click="handleDeleteFromDetail">删除消息</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { messagesApi } from '@/api/messages'
import { useAuthStore } from '@/stores/auth'
import type { Message } from '@/api/messages'
import type { UserInfo } from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()
const currentUser = computed<UserInfo | null>(() => authStore.userInfo)
const unreadCount = computed(() => authStore.unreadCount)

const activeTab = ref('all')
const activeTabbar = ref(3)
const messages = ref<Message[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const currentPage = ref(1)
const pageSize = 20
const showDetail = ref(false)
const selectedMessage = ref<Message | null>(null)
const markingAll = ref(false)

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    const hours = Math.floor(diff / (1000 * 60 * 60))
    if (hours === 0) {
      const minutes = Math.floor(diff / (1000 * 60))
      return minutes < 1 ? '刚刚' : `${minutes}分钟前`
    }
    return `${hours}小时前`
  } else if (days === 1) {
    return '昨天'
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return date.toLocaleDateString('zh-CN')
  }
}

const onLoad = async () => {
  try {
    const params: any = {
      page: currentPage.value,
      size: pageSize
    }

    if (activeTab.value === 'unread') {
      params.isRead = false
    }

    const response = await messagesApi.getMessages(params)

    if (currentPage.value === 1) {
      messages.value = response.list
    } else {
      messages.value = [...messages.value, ...response.list]
    }

    finished.value = !response.hasNext
    currentPage.value++
  } catch (error: any) {
    showToast(error.message || '加载消息失败')
  } finally {
    loading.value = false
  }
}

const onRefresh = async () => {
  currentPage.value = 1
  finished.value = false
  messages.value = []
  await onLoad()
  refreshing.value = false
}

const onTabChange = async () => {
  currentPage.value = 1
  finished.value = false
  messages.value = []
  await onLoad()
}

const handleMessageClick = async (message: Message) => {
  selectedMessage.value = message
  showDetail.value = true

  if (!message.is_read) {
    try {
      await messagesApi.markAsRead(message.id)
      message.is_read = true
      authStore.decrementUnreadCount()
    } catch (error: any) {
      console.error('标记消息已读失败:', error)
    }
  }
}

const handleMarkAsRead = async () => {
  if (!selectedMessage.value) return

  try {
    await messagesApi.markAsRead(selectedMessage.value.id)
    
    // 更新本地消息列表中的状态
    const msg = messages.value.find(m => m.id === selectedMessage.value!.id)
    if (msg) {
      msg.is_read = true
    }
    selectedMessage.value.is_read = true
    
    // 更新未读数
    authStore.decrementUnreadCount()
    
    showToast('已标记为已读')
    showDetail.value = false
  } catch (error: any) {
    const errorMsg = error?.message || '标记失败'
    showToast(errorMsg)
  }
}

const handleMarkAllAsRead = async () => {
  try {
    markingAll.value = true
    await messagesApi.markAllAsRead()
    
    // 更新本地消息列表状态
    messages.value.forEach(m => {
      m.is_read = true
    })
    
    // 清空未读数
    authStore.clearUnreadCount()
    
    showToast('已全部标记为已读')
  } catch (error: any) {
    const errorMsg = error?.message || '操作失败'
    showToast(errorMsg)
  } finally {
    markingAll.value = false
  }
}

const handleDelete = async (message: Message) => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: '确定要删除这条消息吗？'
    })

    await messagesApi.deleteMessage(message.id)
    messages.value = messages.value.filter(m => m.id !== message.id)
    if (!message.is_read) {
      authStore.decrementUnreadCount()
    }
    showToast('删除成功')
  } catch (error: any) {
    if (error !== 'cancel') {
      const errorMsg = error?.message || '删除失败'
      showToast(errorMsg)
    }
  }
}

const handleDeleteFromDetail = async () => {
  if (!selectedMessage.value) return

  try {
    await showConfirmDialog({
      title: '确认删除',
      message: '确定要删除这条消息吗？'
    })

    await messagesApi.deleteMessage(selectedMessage.value.id)
    messages.value = messages.value.filter(m => m.id !== selectedMessage.value!.id)
    if (!selectedMessage.value.is_read) {
      authStore.decrementUnreadCount()
    }
    showToast('删除成功')
    showDetail.value = false
  } catch (error: any) {
    if (error !== 'cancel') {
      showToast(error.message || '删除失败')
    }
  }
}

onMounted(async () => {
  if (!authStore.userInfo) {
    try {
      await authStore.fetchUserInfo()
    } catch (error) {
      console.error('获取用户信息失败:', error)
      showToast({ type: 'fail', message: '获取用户信息失败，请重新登录' })
      router.push('/login')
      return
    }
  }
})
</script>

<style scoped>
.messages {
  min-height: 100vh;
  background-color: #fff;
  padding-bottom: 50px;
}

.messages :deep(.van-nav-bar) {
  background: linear-gradient(135deg, #52c41a 0%, #1890ff 100%);
}

.messages :deep(.van-nav-bar__title) {
  color: #fff;
}

.content-wrapper {
  padding-top: 46px;
}

.action-bar {
  padding: 12px 16px;
  background-color: #fff;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: flex-end;
}

.message-card {
  background-color: #fff;
  margin: 8px 12px;
  padding: 12px;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.2s;
}

.message-card.unread {
  background-color: #e3f2fd;
  border-left: 3px solid #1890ff;
}

.message-card:active {
  background-color: #f0f0f0;
  transform: scale(0.98);
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.message-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  font-size: 15px;
  color: #333;
  flex: 1;
  min-width: 0;
}

.message-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-icon {
  font-size: 18px;
  color: #999;
  padding: 4px;
  transition: color 0.2s;
}

.delete-icon:active {
  color: #ff4d4f;
}

.message-content {
  color: #666;
  font-size: 14px;
  margin-bottom: 8px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.message-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #999;
}

.message-time,
.message-sender {
  display: flex;
  align-items: center;
  gap: 4px;
}

.message-detail {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
}

.detail-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
  color: #333;
  flex: 1;
  padding-right: 12px;
}

.detail-header .van-icon {
  font-size: 20px;
  color: #999;
  cursor: pointer;
}

.detail-content {
  flex: 1;
  overflow-y: auto;
  margin-bottom: 20px;
}

.detail-content p {
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
  color: #666;
}

.detail-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
  padding: 12px;
  background-color: #f5f5f5;
  border-radius: 6px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #666;
}

.meta-item .van-icon {
  color: #999;
}

.detail-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-actions .van-button {
  border-radius: 6px;
}

.copyright-info {
  font-size: 14px;
  color: #666;
}

.user-info {
  font-size: 14px;
  color: #fff;
  font-weight: 500;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
