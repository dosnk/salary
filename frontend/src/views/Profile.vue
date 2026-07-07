<template>
  <div class="profile">
    <van-nav-bar fixed>
      <template #left>
        <span class="copyright-info">三人行装修管理系统</span>
      </template>
      <template #right>
        <div class="nav-right">
          <span class="user-info">{{ userInfo?.nickname || userInfo?.username || '未登录' }}</span>
          <van-badge :content="authStore.unreadCount > 0 ? authStore.unreadCount : undefined" :show-zero="false" max="99">
            <van-icon name="envelop-o" size="20" color="#fff" @click="goToMessages" />
          </van-badge>
        </div>
      </template>
    </van-nav-bar>

    <div class="content-wrapper">
      <van-cell-group title="个人信息" inset>
        <van-cell title="用户名" :value="userInfo.username">
          <template #value>
            <span class="readonly-text">{{ userInfo.username }}</span>
          </template>
        </van-cell>
        <van-cell title="别名" is-link @click="showNicknameDialog = true">
          <template #title>
            <span class="editable-text">别名</span>
          </template>
          <template #value>
            <span>{{ userInfo.nickname || '未设置' }}</span>
          </template>
        </van-cell>
        <van-cell title="手机号" is-link @click="showPhoneDialog = true">
          <template #title>
            <span class="editable-text">手机号</span>
          </template>
          <template #value>
            <span>{{ userInfo.phone || '未设置' }}</span>
          </template>
        </van-cell>
        <van-cell title="角色" :value="getRoleText(userInfo.role)">
          <template #value>
            <span class="readonly-text">{{ getRoleText(userInfo.role) }}</span>
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group title="安全设置" inset>
        <van-cell title="修改密码" is-link @click="showPasswordDialog = true">
          <template #title>
            <span class="editable-text">修改密码</span>
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group title="预支管理" inset>
        <van-cell title="预支总额">
          <template #value>
            <span class="readonly-text">¥{{ Number(advanceTotal).toFixed(2) }}</span>
          </template>
        </van-cell>
        <van-cell title="预支记录" is-link @click="showAdvanceList = true">
          <template #title>
            <span class="editable-text">预支记录</span>
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group title="其他" inset>
        <van-cell v-if="isAdmin" title="空间类型管理" is-link @click="router.push('/admin/space-type')">
          <template #title>
            <span class="editable-text">空间类型管理</span>
          </template>
        </van-cell>
        <van-cell v-if="isAdmin" title="施工方案管理" is-link @click="router.push('/admin/construction-plan')">
          <template #title>
            <span class="editable-text">施工方案管理</span>
          </template>
        </van-cell>
        <van-cell v-if="isAdmin" title="用户管理" is-link @click="router.push('/admin/user')">
          <template #title>
            <span class="editable-text">用户管理</span>
          </template>
        </van-cell>
        <van-cell v-if="isAdmin" title="AI大模型配置" is-link @click="router.push('/admin/ai-config')">
          <template #title>
            <span class="editable-text">AI大模型配置</span>
          </template>
        </van-cell>
        <van-cell v-if="isAdmin" title="数据迁移" is-link @click="router.push('/migrate')">
          <template #title>
            <span class="editable-text">数据迁移</span>
          </template>
        </van-cell>
        <van-cell title="关于" is-link @click="showAboutDialog = true">
          <template #title>
            <span class="editable-text">关于</span>
          </template>
        </van-cell>
      </van-cell-group>

      <div class="logout-section">
        <van-button type="danger" block @click="handleLogout">退出登录</van-button>
      </div>
    </div>

    <van-tabbar v-model="activeTab" active-color="#84cc16" inactive-color="#64748b">
      <van-tabbar-item icon="home-o" to="/dashboard">主页</van-tabbar-item>
      <van-tabbar-item icon="apps-o" to="/project">工程管理</van-tabbar-item>
      <van-tabbar-item v-if="!isDocumenter" icon="chart-trending-o" to="/statistic">统计</van-tabbar-item>
      <van-tabbar-item icon="user-o" to="/profile">我的</van-tabbar-item>
    </van-tabbar>

    <!-- 消息弹窗 -->
    <van-popup v-model:show="showMessagesPopup" position="bottom" :style="{ height: '70%' }">
      <van-nav-bar title="消息中心" left-arrow class="white-arrow-nav" @click-left="showMessagesPopup = false">
        <template #right>
          <van-button v-if="authStore.unreadCount > 0" size="small" type="primary" @click="handleMarkAllAsRead" :loading="markingAll">全部已读</van-button>
        </template>
      </van-nav-bar>
      <van-tabs v-model:active="messageActiveTab" @change="onMessageTabChange">
        <van-tab title="全部" name="all">
          <van-pull-refresh v-model="messageRefreshing" @refresh="onMessageRefresh">
            <van-list v-model:loading="messageLoading" :finished="messageFinished" finished-text="没有更多了" @load="onMessageLoad">
              <div v-for="message in messages" :key="message.id" :class="['message-card', { 'unread': !message.is_read }]" @click="handleMessageClick(message)">
                <div class="message-header">
                  <div class="message-title"><span>{{ message.title }}</span><van-tag v-if="!message.is_read" type="danger">未读</van-tag></div>
                  <van-icon name="delete-o" class="delete-icon" @click.stop="handleDeleteMessage(message)" />
                </div>
                <div class="message-content">{{ message.content }}</div>
                <div class="message-footer"><span class="message-time">{{ formatMessageDate(message.created_at) }}</span></div>
              </div>
            </van-list>
          </van-pull-refresh>
        </van-tab>
        <van-tab title="未读" name="unread">
          <van-pull-refresh v-model="messageRefreshing" @refresh="onMessageRefresh">
            <van-list v-model:loading="messageLoading" :finished="messageFinished" finished-text="没有更多了" @load="onMessageLoad">
              <div v-for="message in messages" :key="message.id" class="message-card unread" @click="handleMessageClick(message)">
                <div class="message-header">
                  <div class="message-title"><span>{{ message.title }}</span><van-tag type="danger">未读</van-tag></div>
                  <van-icon name="delete-o" class="delete-icon" @click.stop="handleDeleteMessage(message)" />
                </div>
                <div class="message-content">{{ message.content }}</div>
                <div class="message-footer"><span class="message-time">{{ formatMessageDate(message.created_at) }}</span></div>
              </div>
            </van-list>
          </van-pull-refresh>
        </van-tab>
      </van-tabs>
      <van-empty v-if="messages.length === 0 && !messageLoading" description="暂无消息" />
    </van-popup>

    <!-- 消息详情弹窗 -->
    <van-popup v-model:show="showMessageDetail" round position="bottom" :style="{ height: '50%' }">
      <div class="message-detail">
        <div class="detail-header">
          <h3>{{ selectedMessage?.title }}</h3>
          <van-icon name="close" @click="showMessageDetail = false" />
        </div>
        <div class="detail-content"><p>{{ selectedMessage?.content }}</p></div>
        <div class="detail-meta">
          <div class="meta-item"><van-icon name="clock-o" /><span>{{ selectedMessage ? formatMessageDate(selectedMessage.created_at) : '' }}</span></div>
          <div class="meta-item"><van-icon name="user-o" /><span>发送人：{{ selectedMessage?.creator_name || '系统' }}</span></div>
        </div>
        <div class="detail-actions">
          <van-button v-if="!selectedMessage?.is_read" type="primary" @click="handleMarkAsRead">标记已读</van-button>
          <van-button type="danger" @click="handleDeleteFromDetail">删除</van-button>
        </div>
      </div>
    </van-popup>

    <van-dialog v-model:show="showNicknameDialog" title="修改别名" show-cancel-button @confirm="handleUpdateNickname">
      <van-field
        v-model="nicknameForm.nickname"
        label="别名"
        placeholder="请输入别名"
        maxlength="20"
        show-word-limit
      />
    </van-dialog>

    <van-dialog v-model:show="showPhoneDialog" title="修改手机号" show-cancel-button @confirm="handleUpdatePhone">
      <van-field
        v-model="phoneForm.phone"
        type="tel"
        label="手机号"
        placeholder="请输入手机号"
        maxlength="11"
      />
    </van-dialog>

    <van-dialog v-model:show="showPasswordDialog" title="修改密码" show-cancel-button @confirm="handleUpdatePassword">
      <form @submit.prevent="handleUpdatePassword">
        <input type="text" name="username" :value="userInfo.username" autocomplete="username" style="display: none" />
        <van-field
          v-model="passwordForm.oldPassword"
          type="password"
          label="旧密码"
          placeholder="请输入旧密码"
          autocomplete="current-password"
        />
        <van-field
          v-model="passwordForm.newPassword"
          type="password"
          label="新密码"
          placeholder="请输入新密码（3-8位）"
          maxlength="8"
          autocomplete="new-password"
        />
        <van-field
          v-model="passwordForm.confirmPassword"
          type="password"
          label="确认密码"
          placeholder="请再次输入新密码"
          maxlength="8"
          autocomplete="new-password"
        />
      </form>
    </van-dialog>

    <!-- 关于弹窗 -->
    <van-dialog v-model:show="showAboutDialog" title="关于" :show-confirm-button="true">
      <div class="about-content">
        <div class="about-logo">🏠</div>
        <h3 class="about-title">三人行吊顶管理系统</h3>
        <p class="about-version">版本：{{ appVersion }}</p>
        <div class="about-info">
          <p>三人行装修团队专用管理系统</p>
          <p>支持工程管理、工资结算、数据统计等功能</p>
        </div>
        <div class="about-copyright">
          <p>© 2026 三人行装修团队</p>
        </div>
      </div>
    </van-dialog>

    <van-popup v-model:show="showAdvanceList" position="bottom" :style="{ height: '90%' }">
      <van-nav-bar title="预支记录" left-arrow class="white-arrow-nav" @click-left="showAdvanceList = false">
        <template #right>
          <van-button size="small" type="primary" @click="showAdvanceForm = true">新增预支</van-button>
        </template>
      </van-nav-bar>

      <!-- 年月筛选器 -->
      <div class="advance-filter">
        <div class="filter-item" @click="showYearPicker = true">
          <span class="filter-label">年份</span>
          <span class="filter-value">{{ advanceFilterYear || '全部' }}</span>
          <van-icon name="arrow-down" />
        </div>
        <div class="filter-item" @click="showMonthPicker = true">
          <span class="filter-label">月份</span>
          <span class="filter-value">{{ advanceFilterMonth || '全部' }}</span>
          <van-icon name="arrow-down" />
        </div>
        <van-button size="small" type="default" @click="handleResetAdvanceFilter">重置</van-button>
      </div>

      <!-- 年份选择器 -->
      <van-popup v-model:show="showYearPicker" position="bottom">
        <van-picker
          :columns="yearColumns"
          @confirm="onYearConfirm"
          @cancel="showYearPicker = false"
        />
      </van-popup>

      <!-- 月份选择器 -->
      <van-popup v-model:show="showMonthPicker" position="bottom">
        <van-picker
          :columns="monthColumns"
          @confirm="onMonthConfirm"
          @cancel="showMonthPicker = false"
        />
      </van-popup>

      <van-pull-refresh v-model="advanceRefreshing" @refresh="onAdvanceRefresh">
        <!-- 当前预支金额 -->
        <div v-if="currentAdvances.length > 0" class="advance-section">
          <div class="section-title">当前预支金额（未结算）</div>
          <van-list v-model:loading="advanceLoading" :finished="currentAdvanceFinished" finished-text="没有更多了" @load="onAdvanceLoad">
            <van-cell v-for="advance in currentAdvances" :key="advance.id" center>
              <template #title>
                <div>预支金额: ¥{{ Number(advance.advance_amount).toFixed(2) }}</div>
              </template>
              <template #label>
                <div>预支日期: {{ advance.advance_date }}</div>
                <div v-if="advance.remark">备注: {{ advance.remark }}</div>
                <div style="color: #ff9800">未结算</div>
              </template>
              <template #right-icon>
                <van-button size="small" type="danger" @click="handleDeleteAdvance(advance)">删除</van-button>
              </template>
            </van-cell>
          </van-list>
        </div>

        <!-- 历史预支记录 -->
        <div v-if="historyAdvances.length > 0" class="advance-section">
          <div class="section-title">历史预支记录（已结算）</div>
          <van-list v-model:loading="advanceLoading" :finished="historyAdvanceFinished" finished-text="没有更多了" @load="onAdvanceLoad">
            <van-cell v-for="advance in historyAdvances" :key="advance.id" center>
              <template #title>
                <div>预支金额: ¥{{ Number(advance.advance_amount).toFixed(2) }}</div>
              </template>
              <template #label>
                <div>预支日期: {{ advance.advance_date }}</div>
                <div v-if="advance.remark">备注: {{ advance.remark }}</div>
                <div style="color: #52c41a">已结算</div>
              </template>
            </van-cell>
          </van-list>
        </div>

        <van-empty v-if="currentAdvances.length === 0 && historyAdvances.length === 0" description="暂无预支记录" />
      </van-pull-refresh>
    </van-popup>

    <van-dialog v-model:show="showAdvanceForm" title="新增预支" show-cancel-button @confirm="handleCreateAdvance">
      <van-field
        v-model="advanceForm.advanceAmount"
        type="number"
        label="预支金额"
        placeholder="请输入预支金额"
      />
      <van-field
        v-model="advanceForm.advanceDate"
        type="date"
        label="预支日期"
      />
      <van-field
        v-model="advanceForm.remark"
        type="textarea"
        label="备注"
        placeholder="请输入备注（可选）"
        maxlength="200"
        show-word-limit
      />
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { useAuthStore } from '@/stores/auth'
import { usersApi } from '@/api/users'
import { advancesApi } from '@/api/advances'
import { useDateFormat } from '@/composables/useDateFormat'
import { useMessage } from '@/composables/useMessage'
import pkg from '../../package.json'

const appVersion = pkg.version

const router = useRouter()
const authStore = useAuthStore()
const { formatMessageDate } = useDateFormat()
const {
  messages,
  loading: messageLoading,
  refreshing: messageRefreshing,
  finished: messageFinished,
  activeTab: messageActiveTab,
  showDetail: showMessageDetail,
  selectedMessage,
  markingAll,
  loadMessages,
  handleMessageClick,
  markAsRead,
  markAllAsRead,
  deleteMessage,
  deleteSelectedMessage,
  onRefresh: onMessageRefresh,
  onTabChange: onMessageTabChange
} = useMessage()

const activeTab = ref(3)
const isAdmin = computed(() => authStore.isAdmin)
const isDocumenter = computed(() => authStore.userInfo?.role === 'documenter')
const userInfo = reactive({
  id: 0,
  username: '',
  nickname: '',
  phone: '',
  role: ''
})

const showNicknameDialog = ref(false)
const showPhoneDialog = ref(false)
const showPasswordDialog = ref(false)
const showAboutDialog = ref(false)

const nicknameForm = reactive({
  nickname: ''
})

const phoneForm = reactive({
  phone: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const showAdvanceList = ref(false)
const showAdvanceForm = ref(false)
const advanceTotal = ref(0)
const currentAdvances = ref<any[]>([])
const historyAdvances = ref<any[]>([])
const advanceLoading = ref(false)
const currentAdvanceFinished = ref(false)
const historyAdvanceFinished = ref(false)
const advanceRefreshing = ref(false)
const advancePage = ref(1)
const advanceSize = ref(50)

const showMessagesPopup = ref(false)

const onMessageLoad = () => {
  if (messageRefreshing.value) {
    onMessageRefresh()
  } else {
    loadMessages()
  }
}

const handleMarkAsRead = () => markAsRead(() => authStore.decrementUnreadCount())
const handleMarkAllAsRead = () => markAllAsRead()
const handleDeleteMessage = (message: any) => deleteMessage(message, () => authStore.decrementUnreadCount())
const handleDeleteFromDetail = () => deleteSelectedMessage(() => authStore.decrementUnreadCount())

// 预支记录筛选器相关
const currentYear = new Date().getFullYear()
const currentMonth = new Date().getMonth() + 1
const advanceFilterYear = ref(`${currentYear}年`)
const advanceFilterMonth = ref(`${currentMonth}月`)
const showYearPicker = ref(false)
const showMonthPicker = ref(false)

// 年份选项
const yearColumns = ref(
  Array.from({ length: 10 }, (_, i) => ({
    text: `${currentYear - i}年`,
    value: currentYear - i
  }))
)

// 月份选项
const monthColumns = ref(
  Array.from({ length: 12 }, (_, i) => ({
    text: `${i + 1}月`,
    value: i + 1
  }))
)

const advanceForm = reactive({
  advanceAmount: '',
  advanceDate: new Date().toISOString().split('T')[0],
  remark: ''
})

const getRoleText = (role: string) => {
  const roleMap: Record<string, string> = {
    admin: '管理员',
    constructor: '施工人员',
    manager: '项目经理'
  }
  return roleMap[role] || role
}

const loadUserInfo = async () => {
  try {
    const data = await usersApi.getProfile()
    Object.assign(userInfo, data)
    nicknameForm.nickname = data.nickname || ''
    phoneForm.phone = data.phone || ''
  } catch (error) {
    showToast({ type: 'fail', message: '加载用户信息失败' })
  }
}

const handleUpdateNickname = async () => {
  if (!nicknameForm.nickname.trim()) {
    showToast('请输入别名')
    return false
  }

  try {
    await usersApi.updateProfile({
      nickname: nicknameForm.nickname
    })
    showToast({ type: 'success', message: '修改成功' })
    userInfo.nickname = nicknameForm.nickname
    return true
  } catch (error: any) {
    const errorMsg = error?.message || '修改失败'
    showToast({ type: 'fail', message: errorMsg })
    return false
  }
}

const handleUpdatePhone = async () => {
  if (!phoneForm.phone.trim()) {
    showToast('请输入手机号')
    return false
  }

  const phoneRegex = /^1[3-9]\d{9}$/
  if (!phoneRegex.test(phoneForm.phone)) {
    showToast('请输入正确的手机号')
    return false
  }

  try {
    await usersApi.updateProfile({
      phone: phoneForm.phone
    })
    showToast({ type: 'success', message: '修改成功' })
    userInfo.phone = phoneForm.phone
    return true
  } catch (error: any) {
    console.error('修改手机号失败:', error)
    const errorMsg = error?.message || '修改失败'
    showToast({ type: 'fail', message: errorMsg })
    return false
  }
}

const handleUpdatePassword = async () => {
  if (!passwordForm.oldPassword) {
    showToast('请输入旧密码')
    return false
  }

  if (!passwordForm.newPassword) {
    showToast('请输入新密码')
    return false
  }

  if (passwordForm.newPassword.length < 3 || passwordForm.newPassword.length > 8) {
    showToast('新密码长度应为3-8位')
    return false
  }

  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    showToast('两次输入的密码不一致')
    return false
  }

  try {
    await usersApi.changePassword({
      old_password: passwordForm.oldPassword,
      new_password: passwordForm.newPassword
    })
    showToast({ type: 'success', message: '修改成功，请重新登录' })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    setTimeout(() => {
      handleLogout()
    }, 1500)
    return true
  } catch (error: any) {
    const errorMsg = error?.message || '修改失败'
    showToast({ type: 'fail', message: errorMsg })
    return false
  }
}

const handleLogout = async () => {
  showConfirmDialog({
    title: '确认退出',
    message: '确定要退出登录吗？'
  }).then(async () => {
    await authStore.logout()
    router.replace('/login')
  }).catch(() => {
  })
}

const loadAdvanceTotal = async () => {
  try {
    const data = await advancesApi.getMyAdvanceTotal()
    advanceTotal.value = data.total || 0
  } catch (error) {
    console.error('加载预支总额失败:', error)
  }
}

const onAdvanceLoad = async () => {
  if (advanceRefreshing.value) {
    currentAdvances.value = []
    historyAdvances.value = []
    advancePage.value = 1
    currentAdvanceFinished.value = false
    historyAdvanceFinished.value = false
    advanceRefreshing.value = false
  }

  try {
    const params: any = {
      page: advancePage.value,
      size: advanceSize.value
    }

    if (advanceFilterYear.value) {
      const yearMatch = advanceFilterYear.value.match(/(\d+)/)
      if (yearMatch) {
        params.year = parseInt(yearMatch[1])
      }
    }

    if (advanceFilterMonth.value) {
      const monthMatch = advanceFilterMonth.value.match(/(\d+)/)
      if (monthMatch) {
        params.month = parseInt(monthMatch[1])
      }
    }

    const data = await advancesApi.getMyAdvances(params)
    
    if (data.list.length > 0) {
      // 将预支记录分为当前预支（未结算）和历史预支（已结算）
      data.list.forEach((advance: any) => {
        if (advance.settled) {
          historyAdvances.value.push(advance)
        } else {
          currentAdvances.value.push(advance)
        }
      })
    }
    
    advanceLoading.value = false
    
    if (data.list.length < advanceSize.value) {
      currentAdvanceFinished.value = true
      historyAdvanceFinished.value = true
    } else {
      advancePage.value++
    }
  } catch (error: any) {
    advanceLoading.value = false
    const errorMsg = error?.message || '加载预支记录失败'
    showToast({ type: 'fail', message: errorMsg })
  }
}

const onAdvanceRefresh = () => {
  currentAdvances.value = []
  historyAdvances.value = []
  advancePage.value = 1
  currentAdvanceFinished.value = false
  historyAdvanceFinished.value = false
  advanceLoading.value = true
  advanceRefreshing.value = true
  onAdvanceLoad()
}

const onYearConfirm = ({ selectedOptions }: any) => {
  advanceFilterYear.value = selectedOptions[0].text
  showYearPicker.value = false
  onAdvanceRefresh()
}

const onMonthConfirm = ({ selectedOptions }: any) => {
  advanceFilterMonth.value = selectedOptions[0].text
  showMonthPicker.value = false
  onAdvanceRefresh()
}

const handleResetAdvanceFilter = () => {
  advanceFilterYear.value = `${currentYear}年`
  advanceFilterMonth.value = `${currentMonth}月`
  onAdvanceRefresh()
}

const handleCreateAdvance = async () => {
  if (!advanceForm.advanceAmount || parseFloat(advanceForm.advanceAmount) <= 0) {
    showToast('请输入有效的预支金额')
    return false
  }

  if (!advanceForm.advanceDate) {
    showToast('请选择预支日期')
    return false
  }

  try {
    await advancesApi.createAdvance({
      userId: userInfo.id,
      advanceAmount: parseFloat(advanceForm.advanceAmount),
      advanceDate: advanceForm.advanceDate,
      remark: advanceForm.remark || undefined
    })
    
    showToast({ type: 'success', message: '创建成功' })
    
    advanceForm.advanceAmount = ''
    advanceForm.advanceDate = new Date().toISOString().split('T')[0]
    advanceForm.remark = ''
    
    await loadAdvanceTotal()
    
    currentAdvances.value = []
    historyAdvances.value = []
    advancePage.value = 1
    currentAdvanceFinished.value = false
    historyAdvanceFinished.value = false
    advanceLoading.value = true
    advanceRefreshing.value = true
    
    await onAdvanceLoad()
    
    return true
  } catch (error: any) {
    const errorMsg = error?.message || '创建失败'
    showToast({ type: 'fail', message: errorMsg })
    return false
  }
}

const handleDeleteAdvance = async (advance: any) => {
  if (advance.settled) {
    showToast('已结算的预支记录不能删除')
    return
  }

  showConfirmDialog({
    title: '确认删除',
    message: `确定要删除这条预支记录吗？金额：¥${Number(advance.advance_amount).toFixed(2)}`
  }).then(async () => {
    try {
      await advancesApi.deleteAdvance(advance.id)
      showToast({ type: 'success', message: '删除成功' })
      
      await loadAdvanceTotal()
      
      currentAdvances.value = []
      historyAdvances.value = []
      advancePage.value = 1
      currentAdvanceFinished.value = false
      historyAdvanceFinished.value = false
      advanceRefreshing.value = true
      await onAdvanceLoad()
    } catch (error: any) {
      const errorMsg = error?.message || '删除失败'
      showToast({ type: 'fail', message: errorMsg })
    }
  }).catch(() => {
  })
}

// 消息相关函数
const goToMessages = () => {
  showMessagesPopup.value = true
  loadMessages()
}

// 监听预支记录弹窗打开，自动加载数据
watch(showAdvanceList, (newVal) => {
  if (newVal) {
    onAdvanceRefresh()
  }
})

onMounted(async () => {
  // 确保用户信息已加载完成
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
  
  loadUserInfo()
  loadAdvanceTotal()
  
  // 检查是否需要显示预支列表
  if (router.currentRoute.value.query.showAdvanceList === 'true') {
    showAdvanceList.value = true
  }
})
</script>

<style scoped>
.profile {
  min-height: 100vh;
  background: #ffffff;
  padding-top: 46px;
  padding-bottom: 50px;
}

.profile :deep(.van-nav-bar) {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
}

.profile :deep(.van-nav-bar__title) {
  color: #fff;
}

.copyright-info {
  font-size: 16px;
  color: #fff;
  font-weight: 500;
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

.content-wrapper {
  padding: 16px;
}

.content-wrapper :deep(.van-cell-group) {
  width: 98%;
  margin: 0 auto;
  margin-bottom: 12px;
}

.content-wrapper :deep(.van-cell-group:last-child) {
  margin-bottom: 0;
}

.editable-title {
  color: #1f2937;
  font-weight: 500;
}

.readonly-value {
  color: #9ca3af;
}

.editable-text {
  color: #1f2937;
  font-weight: 600;
}

.readonly-text {
  color: #9ca3af;
}

.content-wrapper :deep(.editable-title) {
  color: #1f2937 !important;
  font-weight: 500 !important;
}

.content-wrapper :deep(.readonly-value) {
  color: #9ca3af !important;
}

.content-wrapper :deep(.editable-cell .van-cell__title) {
  color: #1f2937;
  font-weight: 500;
}

.content-wrapper :deep(.readonly-cell .van-cell__value) {
  color: #9ca3af;
}

.profile :deep(.van-toast) {
  color: #fff !important;
  background-color: rgba(0, 0, 0, 0.8) !important;
}

.profile :deep(.van-toast--fail) {
  color: #fff !important;
  background-color: rgba(220, 38, 38, 0.9) !important;
}

.profile :deep(.van-toast--success) {
  color: #fff !important;
  background-color: rgba(16, 185, 129, 0.9) !important;
}

.profile :deep(.van-toast__text) {
  color: #fff !important;
}

/* 预支记录筛选器样式 */
.advance-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #ffffff 0%, #f9fef5 100%);
  border-bottom: 1px solid #e6f4d0;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e6f4d0;
  font-size: 14px;
  cursor: pointer;
}

.filter-label {
  color: #666;
}

.filter-value {
  color: #333;
  font-weight: 500;
}

.advance-filter :deep(.van-button) {
  flex-shrink: 0;
}

/* 预支记录分节样式 */
.advance-section {
  margin-bottom: 16px;
}

.section-title {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #333842;
  background: linear-gradient(135deg, #f9fef5 0%, #e6f4d0 100%);
  border-left: 3px solid #84cc16;
}

/* 消息卡片样式 */
.message-card {
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}

.message-card.unread {
  background: #f0f9eb;
}

.message-header{
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.message-title{
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 500;
  color: #333;
}

.delete-icon{
  color: #999;
  font-size: 18px;
}

.message-content{
  font-size: 14px;
  color: #666;
  line-height: 1.5;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.message-footer{
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
}

/* 消息详情样式 */
.message-detail{
  padding: 16px;
}

.detail-header{
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.detail-header h3{
  margin: 0;
  font-size: 18px;
  color: #333;
}

.detail-content{
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  margin-bottom: 16px;
  line-height: 1.6;
  color: #333;
}

.detail-meta{
  margin-bottom: 16px;
}

.meta-item{
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 14px;
  color: #666;
}

.detail-actions{
  display: flex;
  gap: 10px;
}

.detail-actions .van-button{
  flex: 1;
}

/* 关于弹窗样式 */
.about-content {
  padding: 20px;
  text-align: center;
}

.about-logo {
  font-size: 48px;
  margin-bottom: 12px;
}

.about-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px 0;
}

.about-version {
  font-size: 14px;
  color: #84cc16;
  margin: 0 0 16px 0;
}

.about-info {
  font-size: 14px;
  color: #666;
  line-height: 1.8;
  margin-bottom: 16px;
}

.about-info p {
  margin: 0;
}

.about-copyright {
  font-size: 12px;
  color: #999;
}

.about-copyright p {
  margin: 0;
}

.logout-section {
  padding: 24px 16px;
  margin-top: 16px;
}

.white-arrow-nav :deep(.van-nav-bar__arrow) {
  color: #fff !important;
}
</style>
