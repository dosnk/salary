<template>
  <div class="projects">
    <van-nav-bar fixed>
      <template #left>
        <span class="copyright-info">三人行装修管理系统</span>
      </template>
      <template #right>
        <div class="nav-right">
          <span class="user-info">{{ currentUser?.nickname || currentUser?.username || '未登录' }}</span>
          <van-badge :content="unreadCount > 0 ? unreadCount : undefined" :show-zero="false" max="99">
            <van-icon name="envelop-o" size="20" color="#fff" @click="goToMessages" />
          </van-badge>
        </div>
      </template>
    </van-nav-bar>

    <div class="content-wrapper">
      <div class="search-row">
        <van-search
          v-model="searchForm.keyword"
          placeholder="请输入项目名称、描述或施工员"
          @search="handleSearch"
          @clear="handleSearch"
          @input="debouncedSearch"
          class="search-input"
        />
        <van-button type="primary" size="small" plain @click="handleOpenAdvancedFilter" class="advanced-filter-btn">
          <van-icon name="filter-o" />
        </van-button>
      </div>

      <div v-if="hasActiveFilters" class="filter-tags">
        <van-tag v-if="searchForm.month" type="primary" closeable size="medium" @close="clearFilter('month')">{{ searchForm.year || new Date().getFullYear() }}年{{ searchForm.month }}月</van-tag>
        <van-tag v-if="searchForm.status" type="primary" closeable size="medium" @close="clearFilter('status')">{{ statusTextMap[searchForm.status] }}</van-tag>
        <van-tag v-if="searchForm.settlementStatus" type="primary" closeable size="medium" @close="clearFilter('settlementStatus')">{{ settlementStatusTextMap[searchForm.settlementStatus] }}</van-tag>
        <van-tag v-if="searchForm.startDate || searchForm.endDate" type="primary" closeable size="medium" @close="clearFilter('date')">日期: {{ searchForm.startDate || '起始' }} ~ {{ searchForm.endDate || '结束' }}</van-tag>
        <van-button type="default" size="mini" @click="clearAllFilters" class="clear-all-btn">清空</van-button>
      </div>

      <div class="list-container">
        <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
          <!-- 修复1：immediateCheck → immediate-check 拼写错误（关键） -->
          <van-list
            :loading="loading"
            :finished="finished"
            finished-text="没有更多了"
            loading-text="加载中..."
            :offset="200"
            :immediate-check="true"
            @load="onLoad"
          >
          <template v-for="(projects, yearMonth) in groupedProjects" :key="yearMonth">
            <div class="group-header">{{ yearMonth }}</div>
            <div class="project-card" v-for="item in projects" :key="item.id">
              <!-- 卡片头部：工程名称和编号 -->
              <div class="card-header">
                <div class="header-top">
                  <div class="project-name">{{ item.name }}</div>
                  <div class="code-wrapper">
                    <div class="time-info">{{ formatDate(item.created_at) }}</div>
                    <div class="project-code">单号:668{{ item.id }}</div>
                  </div>
                </div>
              </div>

              <!-- 金额区：左右分栏 -->
              <div class="amount-wrap">
                <div class="amount-left">
                  <div class="amount-label">工费总额</div>
                  <div class="amount-value">¥{{ formatNumber(item.total_amount) }}</div>
                </div>
                <div class="amount-right">
                  <div class="per-person">人均工费</div>
                  <div class="per-person-value">¥{{ formatNumber(item.workers && item.workers.length > 0 ? item.total_amount / item.workers.length : 0) }}</div>
                </div>
              </div>

              <!-- 人员信息区 -->
              <div class="info-list">
                <div class="info-item">
                  <div class="info-left">
                    <span class="info-icon">👷</span>
                    <span class="info-text">人员：{{ item.workers && item.workers.length > 0 ? item.workers.map(w => w.nickname || w.username).join('、') : '暂无' }}</span>
                  </div>
                </div>
                <div class="info-item">
                  <div class="info-left">
                    <span class="info-icon">⚖️</span>
                    <span class="info-text">分配：{{ item.salary_distribution === 'average' ? '平均分配' : '按工时分配' }}</span>
                  </div>
                </div>
                <div v-if="item.description" class="info-item">
                  <div class="info-left">
                    <span class="info-icon">📝</span>
                    <span class="info-text">{{ item.description }}</span>
                  </div>
                </div>
              </div>

              <!-- 按钮区 -->
              <div class="btn-group">
                <van-button 
                  v-if="item.status === 'constructing'" 
                  class="btn btn-primary"
                  @click="handleStatusChange(item, 'completed')"
                >
                  确认完工
                </van-button>
                <van-button 
                  v-else-if="item.status === 'completed' && item.settlement_status === 'settling'" 
                  class="btn btn-secondary"
                  @click="handleSettlingClick"
                >
                  统计中
                </van-button>
                <van-button 
                  v-else-if="item.status === 'completed' && item.settlement_status === 'settled'" 
                  class="btn btn-secondary"
                  @click="handleSettledClick"
                >
                  已结算
                </van-button>
                <van-button 
                  v-else 
                  class="btn btn-secondary"
                  disabled
                >
                  已完工
                </van-button>
                <van-button class="btn btn-secondary" @click="handleView(item)">查看详情</van-button>
              </div>
            </div>
          </template>
          <van-empty v-if="!loading && tableData.length === 0" :description="emptyText" />
        </van-list>
        <!-- 再次上拉提示 -->
        <div v-if="!finished && !loading && tableData.length > 0" class="load-more-tip">
          再次上滑加载更多内容
        </div>
      </van-pull-refresh>
      </div>
    </div>

    <!-- 高级筛选弹出框 -->
    <van-popup v-model:show="showAdvancedFilter" position="bottom" :style="{ height: 'auto' }" round>
      <van-nav-bar
        title="高级筛选"
        left-text="取消"
        right-text="应用"
        class="filter-nav-bar"
        @click-left="showAdvancedFilter = false"
        @click-right="handleApplyAdvancedFilter"
      />
      <van-form @submit="handleApplyAdvancedFilter">
        <van-cell-group inset>
          <van-field
            :model-value="searchForm.month ? `${searchForm.year || new Date().getFullYear()}年${searchForm.month}月` : ''"
            name="month"
            label="月份"
            readonly
            is-link
            placeholder="请选择月份"
            @click="showMonthPicker = true"
          />
          <van-field
            :model-value="statusTextMap[searchForm.status || ''] || ''"
            name="status"
            label="工程状态"
            readonly
            is-link
            @click="showStatusPicker = true"
          />
          <van-field
            :model-value="settlementStatusTextMap[searchForm.settlementStatus || ''] || ''"
            name="settlementStatus"
            label="结算状态"
            readonly
            is-link
            @click="showSettlementStatusPicker = true"
          />
          <van-field
            v-model="advancedForm.startDate"
            name="startDate"
            label="开始日期"
            type="date"
            placeholder="请选择开始日期"
          />
          <van-field
            v-model="advancedForm.endDate"
            name="endDate"
            label="结束日期"
            type="date"
            placeholder="请选择结束日期"
          />
          <div class="filter-actions">
            <van-button block @click="handleResetAdvancedFilter">重置</van-button>
          </div>
        </van-cell-group>
      </van-form>
    </van-popup>

    <!-- 月份选择器 -->
    <van-popup v-model:show="showMonthPicker" position="bottom">
      <van-date-picker
        v-model="monthPickerValue"
        type="year-month"
        title="选择月份"
        :min-date="new Date(2020, 0, 1)"
        :max-date="new Date(2030, 11, 31)"
        @confirm="onMonthConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>

    <!-- 状态选择器 -->
    <van-popup v-model:show="showStatusPicker" position="bottom">
      <van-picker
        :columns="statusOptions"
        @confirm="onStatusPickerConfirm"
        @cancel="showStatusPicker = false"
      />
    </van-popup>

    <!-- 结算状态选择器 -->
    <van-popup v-model:show="showSettlementStatusPicker" position="bottom">
      <van-picker
        :columns="settlementStatusOptions"
        @confirm="onSettlementStatusPickerConfirm"
        @cancel="showSettlementStatusPicker = false"
      />
    </van-popup>

    <!-- 状态修改确认对话框 -->
    <van-dialog
      v-model:show="showStatusConfirm"
      title="确认修改工程状态"
      show-cancel-button
      @confirm="confirmStatusChange"
      @cancel="cancelStatusChange"
    >
      <div class="status-confirm-content">
        <p>工程名称：{{ statusConfirmProject?.name }}</p>
        <p>当前状态：{{ statusConfirmProject ? getStatusText(statusConfirmProject.status) : '' }}</p>
        <p>新状态：{{ getStatusText(newStatus) }}</p>
        <p class="warning-text">确定要修改工程状态吗？</p>
      </div>
    </van-dialog>

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
          <van-button v-if="unreadCount > 0" size="small" type="primary" @click="handleMarkAllAsRead" :loading="markingAll">全部已读</van-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { projectsApi, type Project } from '@/api/projects'
import { useAuthStore } from '@/stores/auth'
import type { UserInfo } from '@/api/auth'
import dictionaryApi from '@/api/dictionary'
import { useDateFormat } from '@/composables/useDateFormat'
import { useMessage } from '@/composables/useMessage'

const router = useRouter()
const authStore = useAuthStore()
const { formatDateDash: formatDate, formatMessageDate } = useDateFormat()
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

const currentUser = computed<UserInfo | null>(() => authStore.userInfo)
const isDocumenter = computed(() => authStore.userInfo?.role === 'documenter')
const unreadCount = computed(() => authStore.unreadCount)

const loading = ref(false)
const refreshing = ref(false)
const finished = ref(false)
const activeTab = ref(1)
const errorCount = ref(0) // 错误计数器，防止无限重试
const MAX_ERROR_COUNT = 3 // 最大重试次数

const searchForm = reactive({
  keyword: '',
  year: undefined as number | undefined,
  month: undefined as number | undefined,
  status: undefined as 'constructing' | 'completed' | 'preparing' | 'canceled' | undefined,
  settlementStatus: undefined as 'settling' | 'unsettled' | 'settled' | undefined,
  startDate: '',
  endDate: ''
})

const advancedForm = reactive({
  startDate: '',
  endDate: ''
})

const showAdvancedFilter = ref(false)
const showMonthPicker = ref(false)
const showStatusPicker = ref(false)
const showSettlementStatusPicker = ref(false)
const showStatusConfirm = ref(false)
const statusConfirmProject = ref<Project | null>(null)
const newStatus = ref<'constructing' | 'completed' | 'preparing' | 'canceled'>('constructing')

// 可选：如果需要改为5条/页，修改 size 值即可
const pagination = reactive({
  page: 1,
  size: 20, // 改为5即可实现5条/页
  total: 0
})

const tableData = ref<Project[]>([])

const monthPickerValue = ref<string[]>([
  String(new Date().getFullYear()),
  String(new Date().getMonth() + 1).padStart(2, '0')
])

const statusOptions = ref<{ text: string; value: string }[]>([])
const settlementStatusOptions = ref<{ text: string; value: string }[]>([])

const statusTextMap = computed(() => {
  const map: Record<string, string> = {}
  statusOptions.value.forEach(option => {
    map[option.value] = option.text
  })
  return map
})

const settlementStatusTextMap = computed(() => {
  const map: Record<string, string> = {}
  settlementStatusOptions.value.forEach(option => {
    map[option.value] = option.text
  })
  return map
})

const getStatusText = (status: string) => {
  const localMap: Record<string, string> = {
    'preparing': '备料中',
    'constructing': '施工中',
    'completed': '已完工',
    'canceled': '已取消'
  }
  return localMap[status] || statusTextMap.value[status] || status
}

const formatNumber = (num: number | string) => {
  const numValue = typeof num === 'string' ? parseFloat(num) : num
  if (!numValue || isNaN(numValue)) return '0.00'
  return numValue.toFixed(2)
}

const groupedProjects = computed(() => {
  const groups: Record<string, Project[]> = {}
  tableData.value.forEach(project => {
    const date = new Date(project.created_at)
    const yearMonth = `${date.getFullYear()}年${date.getMonth() + 1}月`
    if (!groups[yearMonth]) {
      groups[yearMonth] = []
    }
    groups[yearMonth].push(project)
  })
  return groups
})

const goToMessages = () => {
  showMessagesPopup.value = true
  loadMessages()
}

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

const fetchProjects = async () => {
  if (loading.value) return
  
  loading.value = true
  
  try {
    const data = await projectsApi.getProjects({
      page: pagination.page,
      size: pagination.size,
      keyword: searchForm.keyword || undefined,
      year: searchForm.year ? String(searchForm.year) : undefined,
      month: searchForm.month && searchForm.month > 0 ? String(searchForm.month) : undefined,
      status: searchForm.status || undefined,
      settlementStatus: searchForm.settlementStatus || undefined,
      startDate: searchForm.startDate || undefined,
      endDate: searchForm.endDate || undefined
    })
    
    const currentPageData = Array.isArray(data.list) ? data.list : []
    const total = typeof data.total === 'number' ? data.total : 0
    const hasNext = typeof data.hasNext === 'boolean' ? data.hasNext : false
    
    // 数据拼接（去重，避免重复加载）
    if (pagination.page === 1) {
      tableData.value = [...currentPageData]
    } else {
      const newData = currentPageData.filter(item => 
        !tableData.value.some(exist => exist.id === item.id)
      )
      tableData.value = [...tableData.value, ...newData]
    }
    
    pagination.total = total
    
    // 使用后端返回的 hasNext 字段判断是否还有更多数据
    finished.value = !hasNext
    
    // 只有还有更多数据才递增页码
    if (hasNext) {
      pagination.page++
    }
  } catch (error) {
    errorCount.value++
    showToast({ type: 'fail', message: '获取工程列表失败' })
    // 超过最大重试次数后停止加载，避免无限重试
    if (errorCount.value >= MAX_ERROR_COUNT) {
      finished.value = true
    }
  } finally {
    loading.value = false
  }
}

const onLoad = () => {
  // 只有"未加载完成"且"不在加载中"，才请求
  if (!finished.value && !loading.value) {
    fetchProjects()
  }
}

const onRefresh = async () => {
  // 重置分页状态和错误计数
  pagination.page = 1
  pagination.total = 0
  finished.value = false
  loading.value = false
  errorCount.value = 0 // 重置错误计数
  // 清空现有数据，避免刷新时新旧数据混合
  tableData.value = []
  // 直接调用 fetchProjects，不通过 onLoad，避免重复检查
  await fetchProjects()
  // 加载完成后设置 refreshing 为 false
  refreshing.value = false
}

const handleSearch = () => {
  pagination.page = 1
  pagination.total = 0
  finished.value = false
  loading.value = false
  errorCount.value = 0
  tableData.value = []
  onLoad()
}

let searchTimeout: ReturnType<typeof setTimeout> | null = null
const debouncedSearch = () => {
  if (searchTimeout) {
    clearTimeout(searchTimeout)
  }
  searchTimeout = setTimeout(() => {
    handleSearch()
  }, 300)
}

const handleOpenAdvancedFilter = () => {
  showAdvancedFilter.value = true
}

const handleApplyAdvancedFilter = () => {
  if (advancedForm.startDate && advancedForm.endDate) {
    if (new Date(advancedForm.startDate) > new Date(advancedForm.endDate)) {
      showToast('开始日期不能大于结束日期')
      return
    }
  }
  searchForm.startDate = advancedForm.startDate
  searchForm.endDate = advancedForm.endDate
  showAdvancedFilter.value = false
  pagination.page = 1
  pagination.total = 0
  finished.value = false
  loading.value = false
  tableData.value = []
  onLoad()
}

const handleResetAdvancedFilter = () => {
  advancedForm.startDate = ''
  advancedForm.endDate = ''
  searchForm.startDate = ''
  searchForm.endDate = ''
  showAdvancedFilter.value = false
  pagination.page = 1
  finished.value = false
  tableData.value = []
  onLoad()
}

const hasActiveFilters = computed(() => {
  return !!(
    searchForm.month ||
    searchForm.status ||
    searchForm.settlementStatus ||
    searchForm.startDate ||
    searchForm.endDate
  )
})

const clearFilter = (type: string) => {
  switch (type) {
    case 'keyword':
      searchForm.keyword = ''
      break
    case 'month':
      searchForm.month = undefined
      searchForm.year = undefined
      break
    case 'status':
      searchForm.status = undefined
      break
    case 'settlementStatus':
      searchForm.settlementStatus = undefined
      break
    case 'date':
      searchForm.startDate = ''
      searchForm.endDate = ''
      advancedForm.startDate = ''
      advancedForm.endDate = ''
      break
  }
  handleSearch()
}

const clearAllFilters = () => {
  searchForm.keyword = ''
  searchForm.year = undefined
  searchForm.month = undefined
  searchForm.status = undefined
  searchForm.settlementStatus = undefined
  searchForm.startDate = ''
  searchForm.endDate = ''
  advancedForm.startDate = ''
  advancedForm.endDate = ''
  handleSearch()
}

const emptyText = computed(() => {
  if (hasActiveFilters.value) {
    return '没有找到符合条件的工程'
  }
  return '暂无工程数据'
})

const handleView = async (row: Project) => {
  router.push(`/project/${row.id}`)
}

const handleStatusChange = (project: Project, status: 'constructing' | 'completed' | 'preparing' | 'canceled') => {
  statusConfirmProject.value = project
  newStatus.value = status
  showStatusConfirm.value = true
}

const confirmStatusChange = async () => {
  if (!statusConfirmProject.value) return
  
  try {
    await projectsApi.updateProject(statusConfirmProject.value.id, { status: newStatus.value })
    showStatusConfirm.value = false
    
    // 直接更新列表中的工程状态，不重新加载整个列表，保持页面滚动位置
    const projectIndex = tableData.value.findIndex(p => p.id === statusConfirmProject.value?.id)
    if (projectIndex !== -1) {
      tableData.value[projectIndex].status = newStatus.value
      // 确认完工后，后端会自动将结算状态设置为"统计中"
      if (newStatus.value === 'completed') {
        tableData.value[projectIndex].settlement_status = 'settling'
      }
    }
    
    statusConfirmProject.value = null
    newStatus.value = 'constructing'
    showToast({ type: 'success', message: '工程状态已更新' })
  } catch (error) {
    console.error('修改工程状态失败:', error)
    const errorMsg = (error as any)?.message || '修改工程状态失败'
    showToast({ type: 'fail', message: errorMsg })
  }
}

const cancelStatusChange = () => {
  showStatusConfirm.value = false
  statusConfirmProject.value = null
  newStatus.value = 'constructing'
}

const handleSettlingClick = () => {
  showToast({ message: '该工程正在统计中，请到统计页面查看统计结果' })
}

const handleSettledClick = () => {
  showToast({ message: '该工程已结算完成，如需查看详情请点击"查看详情"按钮' })
}

const onMonthConfirm = ({ selectedValues }: any) => {
  if (selectedValues && selectedValues.length >= 2) {
    const year = parseInt(selectedValues[0])
    const month = parseInt(selectedValues[1])
    searchForm.year = year
    searchForm.month = month
    monthPickerValue.value = [selectedValues[0], selectedValues[1]]
  }
  showMonthPicker.value = false
  handleSearch()
}

const onStatusPickerConfirm = ({ selectedOptions }: any) => {
  if (selectedOptions && selectedOptions.length > 0) {
    searchForm.status = selectedOptions[0].value
  }
  showStatusPicker.value = false
  handleSearch()
}

const onSettlementStatusPickerConfirm = ({ selectedOptions }: any) => {
  if (selectedOptions && selectedOptions.length > 0) {
    searchForm.settlementStatus = selectedOptions[0].value
  }
  showSettlementStatusPicker.value = false
  handleSearch()
}

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
  
  // 加载字典数据
  try {
    const dictionaryData = await dictionaryApi.getDictionaryWithCache()
    statusOptions.value = [
      { text: '全部状态', value: '' },
      ...(Array.isArray(dictionaryData.projectStatuses) ? dictionaryData.projectStatuses.map(status => ({ text: status.name, value: status.code })) : [])
    ]
    settlementStatusOptions.value = [
      { text: '全部结算状态', value: '' },
      ...(Array.isArray(dictionaryData.settlementStatuses) ? dictionaryData.settlementStatuses.map(status => ({ text: status.name, value: status.code })) : [])
    ]
  } catch (error) {
    console.error('加载字典数据失败:', error)
    showToast({ type: 'fail', message: '加载字典数据失败' })
  }
  
  // 检查URL查询参数，应用筛选条件
  const query = router.currentRoute.value.query
  if (query.status) {
    searchForm.status = query.status as 'constructing' | 'completed' | 'preparing' | 'canceled'
  }
  if (query.settlementStatus) {
    searchForm.settlementStatus = query.settlementStatus as 'settling' | 'unsettled' | 'settled'
  }
  
  // 默认不设置日期范围，让用户可以看到所有工程
  // 用户可以通过高级筛选来设置日期范围
  // 手动触发第一次加载
  fetchProjects()
  authStore.fetchUnreadCount()
})
</script>

<style scoped>
.projects {
  height: 100vh;
  background: linear-gradient(180deg, #f9fef5 0%, #f9fef5 100%);
  padding: 8px;
  padding-bottom: 60px;
  overflow: hidden;
}

.projects :deep(.van-nav-bar) {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%) !important;
}

.projects :deep(.van-nav-bar__title) {
  color: #fff;
}

.filter-nav-bar :deep(.van-nav-bar__text) {
  color: #fff !important;
}

.white-arrow-nav :deep(.van-nav-bar__arrow) {
  color: #fff !important;
}

.content-wrapper { overflow: visible !important;
  padding-top: 46px;
  height: calc(100vh - 110px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: linear-gradient(135deg, #ffffff 0%, #f9fef5 100%);
  border: 1px solid #e6f4d0;
  border-radius: 8px;
}

.search-row .search-input {
  flex: 1;
}

.search-row .advanced-filter-btn {
  margin: 0;
  width: auto;
  flex-shrink: 0;
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  border: none;
  color: #fff;
}

.filter-tags {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border-radius: 8px;
  margin: 0 0 8px 0;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  white-space: nowrap;
}

.filter-tags::-webkit-scrollbar {
  display: none;
}

.filter-tags .van-tag {
  flex-shrink: 0;
  white-space: nowrap;
}

.clear-all-btn {
  flex-shrink: 0;
  white-space: nowrap;
}

.list-container {
  flex: 1;
  overflow-y: auto;
  position: relative;
  -webkit-overflow-scrolling: touch;
  padding: 0;
  padding-bottom: 120px;
  background: #f9fef5;
  overflow-x: hidden;
  width: 100%;
  max-width: 100%;
}

.list-container :deep(.van-pull-refresh),
.list-container :deep(.van-list) {
  min-height: 100%;
  width: 100%;
}

.group-header {
  padding: 6px 16px;
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 10;
  border-bottom: 1px solid #e6f4d0;
  width: 100%;
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

.project-card {
          width: 100%;
          padding: 20px;
          background: linear-gradient(135deg, #ffffff 0%, #f9fef5 100%);
          border-radius: 16px;
          box-shadow: 0 4px 12px rgba(132, 204, 22, 0.08);
          border: 1px solid #e6f4d0;
          transform: translateY(0);
          transition: transform 0.3s ease, box-shadow 0.3s ease;
          margin-bottom: 8px;
          min-height: 240px;
          box-sizing: border-box;
        }

@media (max-width: 768px) {
  .project-card {
    padding: 16px;
    border-radius: 12px;
    min-height: 200px;
  }
}

@media (max-width: 480px) {
  .project-card {
    padding: 12px;
    border-radius: 8px;
    min-height: 180px;
  }
}

.project-card:active {
  transform: translateY(1px);
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.05);
}

.load-more-tip {
  text-align: center;
  padding: 20px;
  color: #64748b;
  font-size: 14px;
  background: #f0fdf4;
}

.card-header {
  margin-bottom: 16px;
}

@media (max-width: 768px) {
  .card-header {
    margin-bottom: 12px;
  }
}

@media (max-width: 480px) {
  .card-header {
    margin-bottom: 10px;
  }
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.project-name {
  font-size: 20px;
  font-weight: 600;
  color: #333842;
  letter-spacing: 0.5px;
  line-height: 1.5;
  flex: 1;
  min-width: 0;
}

@media (max-width: 768px) {
  .project-name {
    font-size: 18px;
  }
}

@media (max-width: 480px) {
  .project-name {
    font-size: 16px;
  }
}

.project-code {
  font-size: 10px;
  color: #64748b;
  background: #f9fef5;
  padding: 4px 10px;
  border-radius: 12px;
  border: 1px solid #e6f4d0;
  line-height: 1.5;
}

@media (max-width: 480px) {
  .project-code {
    font-size: 9px;
    padding: 3px 8px;
  }
}

.code-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  line-height: 1.5;
  flex-shrink: 0;
}

.time-info {
  font-size: 13px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: color 0.2s ease;
  line-height: 1.5;
}

.time-info::before {
  content: "📅";
  font-size: 14px;
}

.time-info:active {
  color: #84cc16;
}

.amount-wrap {
  padding: 16px 0;
  border-top: 1px solid #dcfce7;
  border-bottom: 1px solid #dcfce7;
  margin-bottom: 16px;
  transition: background 0.2s ease;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.amount-wrap:active {
  background: #f0fdf4;
}

.amount-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.amount-label {
  font-size: 14px;
  color: #333842;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.amount-label::before {
  content: "💰";
  font-size: 16px;
}

.amount-value {
  font-size: 36px;
  font-weight: 700;
  color: #84cc16;
  letter-spacing: 1px;
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.amount-right {
  text-align: left;
  background: #f9fef5;
  padding: 12px 16px;
  border-radius: 12px;
  min-width: 120px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  border: 1px solid #e6f4d0;
}

.per-person {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 4px;
}

.per-person-value {
  font-size: 20px;
  color: #84cc16;
  font-weight: 600;
}

.info-list {
  margin-bottom: 20px;
  overflow: hidden;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #333842;
  padding: 10px 8px;
  border-bottom: 1px solid #f9fef5;
  border-radius: 8px;
  margin: 0 -8px;
}

.info-item:last-child {
  border-bottom: none;
}

.info-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.info-icon {
  font-size: 20px;
  width: 24px;
  text-align: center;
  flex-shrink: 0;
}

.info-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.info-right {
  display: none;
}

.btn-group {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.btn {
  padding: 18px 0;
  border: none;
  border-radius: 12px;
  font-size: 17px;
  font-weight: 600;
  cursor: pointer;
  touch-action: manipulation;
  transition: all 0.2s ease;
  box-shadow: 0 2px 8px rgba(132, 204, 22, 0.08);
  position: relative;
  overflow: hidden;
}

.btn-primary {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  color: #fff;
}

.btn-primary::after {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  background: rgba(255,255,255,0.2);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  transition: width 0.6s ease, height 0.6s ease;
}

.btn-primary:active::after {
  width: 300px;
  height: 300px;
  opacity: 0;
}

.btn-primary:active {
  background: linear-gradient(135deg, #65a30d 0%, #65a30d 100%);
  transform: translateY(2px);
  box-shadow: 0 1px 4px rgba(132, 204, 22, 0.05);
}

.btn-confirm-settlement {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  color: #fff;
}

.btn-confirm-settlement::after {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  background: rgba(255,255,255,0.2);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  transition: width 0.6s ease, height 0.6s ease;
}

.btn-confirm-settlement:active::after {
  width: 300px;
  height: 300px;
  opacity: 0;
}

.btn-confirm-settlement:active {
  background: linear-gradient(135deg, #65a30d 0%, #65a30d 100%);
  transform: translateY(2px);
  box-shadow: 0 1px 4px rgba(132, 204, 22, 0.05);
}

.btn-secondary {
  background: #fff;
  color: #333842;
  border: 1px solid #e6f4d0;
}

.btn-secondary:active {
  background: #f9fef5;
  transform: translateY(2px);
  box-shadow: 0 1px 4px rgba(132, 204, 22, 0.05);
  border-color: #84cc16;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn:disabled:active {
  transform: none;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.1);
}

@media (max-width: 375px) {
  .project-name {
    font-size: 18px;
  }
  .amount-value {
    font-size: 28px;
  }
  .per-person-value {
    font-size: 18px;
  }
  .btn {
    padding: 16px 0;
    font-size: 16px;
  }
}

.filter-actions {
  padding: 16px;
}

.filter-actions .van-button {
  margin-top: 8px;
}

.status-actions {
  margin-top: 8px;
  display: flex;
  justify-content: center;
}

.status-actions .van-dropdown-menu {
  width: 100%;
}

.status-confirm-content {
  padding: 20px;
}

.status-confirm-content p {
  margin: 8px 0;
  font-size: 14px;
  color: #333842;
}

.status-confirm-content .warning-text {
  color: #dc2626;
  font-weight: 500;
  margin-top: 12px;
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
  font-size: 15px;
  font-weight: 500;
  color: #333;
}

.delete-icon {
  color: #999;
  font-size: 18px;
}

.message-content {
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

.message-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
}

/* 消息详情样式 */
.message-detail {
  padding: 16px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.detail-header h3 {
  margin: 0;
  font-size: 18px;
  color: #333;
}

.detail-content {
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  margin-bottom: 16px;
  line-height: 1.6;
  color: #333;
}

.detail-meta {
  margin-bottom: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 14px;
  color: #666;
}

.detail-actions {
  display: flex;
  gap: 10px;
}

.detail-actions .van-button {
  flex: 1;
}
</style>