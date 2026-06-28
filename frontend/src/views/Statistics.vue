<template>
  <div class="statistics">
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
      <div class="stats-container">
        <van-grid :column-num="2" :border="false">
          <van-grid-item @click="showConstructingProjects">
            <div class="stat-card">
              <div class="stat-top">
                <div class="stat-icon" style="background-color: #e6a23c">
                  <van-icon name="clock-o" size="20" />
                </div>
                <div class="stat-title"> {{ settlementStatistics.unsettled.count }} 份施工中</div>
              </div>
              <div class="stat-bottom">
                <span class="stat-label">总额：</span>
                <span class="stat-amount">¥{{ formatNumber(settlementStatistics.unsettled.amount) }}</span>
              </div>
            </div>
          </van-grid-item>
          <van-grid-item @click="showSettlingProjects">
            <div class="stat-card">
              <div class="stat-top">
                <div class="stat-icon" style="background-color: #409eff">
                  <van-icon name="balance-o" size="20" />
                </div>
                <div class="stat-title"> {{ settlementStatistics.settling.count }} 份统计中</div>
              </div>
              <div class="stat-bottom">
                <span class="stat-label">总额：</span>
                <span class="stat-amount">¥{{ formatNumber(settlementStatistics.settling.amount) }}</span>
              </div>
            </div>
          </van-grid-item>
          <van-grid-item @click="showSettledProjects">
            <div class="stat-card">
              <div class="stat-top">
                <div class="stat-icon" style="background-color: #84cc16">
                  <van-icon name="checked" size="20" />
                </div>
                <div class="stat-title">今年结算：{{ settlementStatistics.settled.count }} 份</div>
              </div>
              <div class="stat-bottom">
                <span class="stat-label">总额：</span>
                <span class="stat-amount">¥{{ formatNumber(settlementStatistics.settled.amount) }}</span>
              </div>
            </div>
          </van-grid-item>
          <van-grid-item @click="showThisMonthProjects">
            <div class="stat-card">
              <div class="stat-top">
                <div class="stat-icon" style="background-color: #9333ea">
                  <van-icon name="calendar-o" size="20" />
                </div>
                <div class="stat-title">本月 {{ settlementStatistics.thisMonth.count }} 份</div>
              </div>
              <div class="stat-bottom">
                <span class="stat-label">总额：</span>
                <span class="stat-amount">¥{{ formatNumber(settlementStatistics.thisMonth.amount) }}</span>
              </div>
            </div>
          </van-grid-item>
        </van-grid>
      </div>

      <div class="settlement-section">
        <!-- 固定标题栏 -->
        <div class="fixed-title-bar">
          <div class="title-content">
            <span class="title-text">📋 结算单</span>
            <div class="title-actions">
              <span class="selected-count">已选择 {{ selectedProjectIds.length }} 个工程</span>
              <van-button type="primary" size="small" @click="handleSettle" :loading="settling" :disabled="selectedProjectIds.length === 0">
                {{ settling ? '结算中...' : `结算${selectedProjectIds.length > 0 ? `(${selectedProjectIds.length}个)` : ''}` }}
              </van-button>
            </div>
          </div>
        </div>
        <!-- 表格内容 -->
        <div class="table-wrapper">
          <table class="salary-table" v-if="projectData.length > 0 && constructionPlans.length > 0">
            <thead>
              <tr>
                <th style="width: 80px;">
                  <van-button type="primary" size="mini" @click="toggleSelectAll">
                    {{ selectedProjectIds.length > 0 ? '取消选择' : '选择全部' }}
                  </van-button>
                </th>
                <th>序号</th>
                <th>工程名称</th>
                <th v-for="plan in constructionPlans" :key="plan.id">{{ plan.name }}</th>
                <th>总额</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="(project, index) in projectData" :key="project.id">
                <tr :class="{ 'unselected-row': !selectedProjectIds.includes(project.id) }">
                  <td>
                    <van-checkbox :model-value="selectedProjectIds.includes(project.id)" @update:model-value="(val) => toggleProjectSelection(project.id, val)" />
                  </td>
                  <td>{{ index + 1 }}</td>
                  <td class="project-name-cell" @click="toggleProjectExpand(project.id)">
                    <span class="expand-icon">{{ expandedProjects.includes(project.id) ? '▼' : '▶' }}</span>
                    {{ project.project_name }}
                  </td>
                  <td v-for="plan in constructionPlans" :key="'data-' + project.id + '-' + plan.id">
                    {{ getProjectQuantity(project, plan.id) }}
                  </td>
                  <td>-</td>
                </tr>
                <!-- 展开的子项目明细行 -->
                <template v-if="expandedProjects.includes(project.id)">
                  <tr v-for="sub in getProjectSubprojects(project)" :key="'sub-' + sub.subproject_id" class="subproject-row">
                    <td></td>
                    <td></td>
                    <td class="subproject-name">{{ sub.space_type_name }} - {{ sub.plan_name }}</td>
                    <td v-for="plan in constructionPlans" :key="'subdata-' + sub.subproject_id + '-' + plan.id">
                      {{ sub.plan_id === plan.id ? formatSubQuantity(sub) : '-' }}
                    </td>
                    <td>-</td>
                  </tr>
                </template>
              </template>
              <tr class="price-row">
                <td colspan="3">单价</td>
                <td v-for="plan in constructionPlans" :key="'price-' + plan.id">
                  ¥{{ plan.price }}/{{ getUnitName(plan.unit) }}
                </td>
                <td>-</td>
              </tr>
              <tr class="total-row">
                <td colspan="3">合计</td>
                <td v-for="plan in constructionPlans" :key="'total-' + plan.id">
                  {{ selectedPlanTotals[String(plan.id)] && selectedPlanTotals[String(plan.id)].total_quantity !== null ? `${Number(selectedPlanTotals[String(plan.id)].total_quantity).toFixed(2)}${getUnitName(plan.unit)}` : '-' }}
                </td>
                <td>-</td>
              </tr>
              <tr class="grand-total-row">
                <td colspan="3">总计</td>
                <td v-for="plan in constructionPlans" :key="'grand-' + plan.id">
                  ¥{{ formatNumber(selectedPlanTotals[String(plan.id)] ? selectedPlanTotals[String(plan.id)].total_amount : 0) }}
                </td>
                <td>¥{{ formatNumber(selectedGrandTotal) }}</td>
              </tr>
              <tr class="advance-row" v-for="(advance, index) in backendAdvances" :key="'advance-' + advance.id">
                <td colspan="2">
                  {{ formatDate(advance.advance_date) }} 预支
                </td>
                <td></td>
                <td v-for="plan in constructionPlans" :key="'advance-' + plan.id + '-' + index">
                  -
                </td>
                <td>¥{{ formatNumber(advance.advance_amount) }}</td>
              </tr>
              <tr class="final-total-row">
                <td colspan="3">总额</td>
                <td v-for="plan in constructionPlans" :key="'final-' + plan.id">
                  -
                </td>
                <td>¥{{ formatNumber(selectedFinalTotal) }}</td>
              </tr>
            </tbody>
          </table>
          <van-empty v-else-if="projectData.length === 0" description="暂无统计工程，请先在工程管理中确认工程完工" />
          <van-empty v-else description="暂无施工方案数据" />
        </div>
      </div>

      <div class="history-section">
        <template v-if="settlementHistoryList.length > 0 && constructionPlans.length > 0">
          <template v-for="settlement in settlementHistoryList" :key="settlement.settlement_id">
            <!-- 固定标题栏 -->
            <div class="fixed-title-bar">
              <div class="title-content">
                <div class="title-info">
                  <span class="title-text">📜 结算历史</span>
                  <span class="settlement-no">{{ settlement.settlement_no }}</span>
                  <span class="settlement-date">{{ formatMonth(settlement.start_month) }} 至 {{ formatMonth(settlement.end_month) }}</span>
                </div>
                <van-button 
                  type="primary" 
                  size="mini" 
                  @click="exportSettlementToExcel(settlement)" 
                  :loading="exportingId === settlement.settlement_id"
                  class="export-btn"
                >
                  <template #icon>
                    <van-icon name="down" />
                  </template>
                  {{ exportingId === settlement.settlement_id ? '导出中' : '导出Excel' }}
                </van-button>
              </div>
            </div>
            <!-- 表格内容 -->
            <div class="table-wrapper">
              <table class="salary-table">
                <thead>
                  <tr>
                    <th>序号</th>
                    <th>工程名称</th>
                    <th v-for="plan in constructionPlans" :key="plan.id">{{ plan.name }}</th>
                    <th>总额</th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="(project, index) in getUniqueProjects(settlement.projects)" :key="project.id">
                    <tr>
                      <td>{{ index + 1 }}</td>
                      <td class="project-name-cell" @click="toggleHistoryProjectExpand(settlement.settlement_id, project.id)">
                        <span class="expand-icon">{{ isHistoryProjectExpanded(settlement.settlement_id, project.id) ? '▼' : '▶' }}</span>
                        {{ project.project_name }}
                      </td>
                      <td v-for="plan in constructionPlans" :key="'data-' + project.id + '-' + plan.id">
                        {{ getSettlementProjectQuantity(settlement, project, plan.id) }}
                      </td>
                      <td>-</td>
                    </tr>
                    <!-- 展开的子项目明细行 -->
                    <template v-if="isHistoryProjectExpanded(settlement.settlement_id, project.id)">
                      <tr v-for="sub in getHistorySubprojects(settlement, project)" :key="'sub-' + settlement.settlement_id + '-' + sub.subproject_id" class="subproject-row">
                        <td></td>
                        <td class="subproject-name">{{ sub.space_type_name }} - {{ sub.plan_name }}</td>
                        <td v-for="plan in constructionPlans" :key="'subdata-' + sub.subproject_id + '-' + plan.id">
                          {{ sub.plan_id === plan.id ? formatSubQuantity(sub) : '-' }}
                        </td>
                        <td>-</td>
                      </tr>
                    </template>
                  </template>
                  <tr class="price-row">
                    <td colspan="2">单价</td>
                    <td v-for="plan in constructionPlans" :key="'price-' + plan.id">
                      ¥{{ plan.price }}/{{ getUnitName(plan.unit) }}
                    </td>
                    <td>-</td>
                  </tr>
                  <tr class="total-row">
                    <td colspan="2">合计</td>
                    <td v-for="plan in constructionPlans" :key="'total-' + plan.id">
                      {{ settlement.plan_totals && settlement.plan_totals[String(plan.id)] && settlement.plan_totals[String(plan.id)].total_quantity !== null ? `${Number(settlement.plan_totals[String(plan.id)].total_quantity).toFixed(2)}${getUnitName(plan.unit)}` : '-' }}
                    </td>
                    <td>-</td>
                  </tr>
                  <tr class="grand-total-row">
                    <td colspan="2">总计</td>
                    <td v-for="plan in constructionPlans" :key="'grand-' + plan.id">
                      ¥{{ formatNumber(settlement.plan_totals && settlement.plan_totals[String(plan.id)] ? Number(settlement.plan_totals[String(plan.id)].total_amount) : 0) }}
                    </td>
                    <td>¥{{ formatNumber(settlement.grand_total || 0) }}</td>
                  </tr>
                  <tr class="advance-row" v-for="(advance, index) in settlement.advances" :key="'advance-' + settlement.settlement_id + '-' + advance.id">
                    <td colspan="2">{{ formatDate(advance.advance_date) }} 预支</td>
                    <td v-for="plan in constructionPlans" :key="'advance-' + plan.id + '-' + index">
                      -
                    </td>
                    <td>¥{{ formatNumber(advance.advance_amount) }}</td>
                  </tr>
                  <tr class="final-total-row">
                    <td colspan="2">总额</td>
                    <td v-for="plan in constructionPlans" :key="'final-' + plan.id">
                      -
                    </td>
                    <td>¥{{ formatNumber(settlement.final_total || 0) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </template>
        <van-empty v-else description="暂无已结算工程" />
      </div>
    </div>

    <van-popup v-model:show="showProjectsPopup" position="bottom" :style="{ height: '80%' }">
      <van-nav-bar
        :title="popupTitle"
        left-text="关闭"
        class="white-text-nav"
        @click-left="showProjectsPopup = false"
      />
      <!-- 月份筛选器（仅在本月工程标签显示） -->
      <div v-if="activeTab === 'thisMonth'" class="month-filter-bar">
        <div class="filter-label">选择月份：</div>
        <van-button type="primary" size="small" @click="showMonthPickerPopup = true">
          {{ selectedMonthDisplay }}
        </van-button>
      </div>
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          :loading="loading"
          :finished="finished"
          finished-text="没有更多了"
          loading-text="加载中..."
          :offset="200"
          :immediate-check="true"
          @load="onLoad"
        >
          <!-- 工程列表（施工中/统计中/今年结算） -->
          <template v-if="activeTab !== 'settled' || activeTab === 'settled'">
            <div class="project-card" v-for="item in projectList" :key="item.id" @click="handleProjectClick(item)">
              <div class="card-header">
                <div class="header-top">
                  <div class="project-name">{{ item.name }}</div>
                </div>
                <div class="code-wrapper">
                  <div class="time-info">{{ formatDate(item.created_at) }}</div>
                  <div class="project-code">XLM-668-{{ item.id }}</div>
                </div>
              </div>
              <div class="amount-wrap">
                <div class="amount-left">
                  <div class="amount-label">工费总额</div>
                  <div class="amount-value">¥{{ formatNumber(item.total_amount) }}</div>
                </div>
              </div>
              <div class="info-list">
                <div class="info-item">
                  <div class="info-left">
                    <span class="info-icon">👷</span>
                    <span class="info-text">人员：{{ item.workers && item.workers.length > 0 ? item.workers.map(w => w.nickname || w.username).join('、') : '暂无' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </van-list>
      </van-pull-refresh>
    </van-popup>

    <van-popup v-model:show="showMonthPickerPopup" position="bottom">
      <van-date-picker
        v-model="selectedMonthDate"
        type="year-month"
        title="选择月份"
        :item-height="36"
        :min-date="new Date(2020, 0, 1)"
        :max-date="new Date()"
        @confirm="onMonthConfirm"
        @cancel="showMonthPickerPopup = false"
      />
    </van-popup>

    <van-tabbar v-model="activeTabbar" active-color="#84cc16" inactive-color="#64748b">
      <van-tabbar-item icon="home-o" to="/dashboard">主页</van-tabbar-item>
      <van-tabbar-item icon="apps-o" to="/project">工程管理</van-tabbar-item>
      <van-tabbar-item icon="chart-trending-o" to="/statistic">统计</van-tabbar-item>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import settlementsApi, { type SettlementStatistics } from '@/api/settlements'
import { projectsApi, type Project } from '@/api/projects'
import { useAuthStore } from '@/stores/auth'
import type { UserInfo } from '@/api/auth'
import dayjs from 'dayjs'
import salarySheetApi, { type ConstructionPlan, type ProjectData, type AdvanceData, type SettlementHistory } from '@/api/salarySheet'
import { baseURL } from '@/utils/request'
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
const unreadCount = computed(() => authStore.unreadCount)

const settlementStatistics = reactive<SettlementStatistics>({
  settling: {
    amount: 0,
    count: 0,
    description: '',
    thisMonthCount: 0
  },
  unsettled: {
    amount: 0,
    count: 0,
    description: '',
    thisMonthCount: 0
  },
  settled: {
    amount: 0,
    count: 0,
    description: '',
    thisMonthCount: 0
  },
  userUnconfirmed: {
    amount: 0,
    count: 0,
    description: ''
  },
  userConfirmed: {
    amount: 0,
    count: 0,
    description: ''
  },
  thisMonth: {
    amount: 0,
    count: 0,
    description: ''
  }
})

const activeTab = ref('constructing')
const activeTabbar = ref(2)

const showProjectsPopup = ref(false)
const popupTitle = ref('')

const projectList = ref<Project[]>([])
const loading = ref(false)
const refreshing = ref(false)
const finished = ref(false)
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const constructionPlans = ref<ConstructionPlan[]>([])
const projectData = ref<ProjectData[]>([])
const advances = ref<AdvanceData[]>([])
const settling = ref(false)

const backendPlanTotals = ref<Record<number, { total_quantity: number; total_amount: number }>>({})
const backendGrandTotal = ref<number>(0)
const backendTotalAdvance = ref<number>(0)
const backendAdvances = ref<AdvanceData[]>([])
const backendFinalTotal = ref<number>(0)

const selectedProjectIds = ref<number[]>([])
const allSelected = ref(false)
const expandedProjects = ref<number[]>([])  // 结算单展开的工程ID列表
const expandedHistoryProjects = ref<string[]>([])  // 结算历史展开的工程（格式：settlementId-projectId）

const calculationResult = ref<{
  plan_totals: Record<string, { total_quantity: number; total_amount: number }>
  grand_total: number
  total_advance: number
  final_total: number
}>({
  plan_totals: {},
  grand_total: 0,
  total_advance: 0,
  final_total: 0
})

const selectedPlanTotals = computed(() => calculationResult.value.plan_totals)
const selectedGrandTotal = computed(() => calculationResult.value.grand_total)
const selectedFinalTotal = computed(() => calculationResult.value.final_total)

const settlementHistoryList = ref<SettlementHistory[]>([])

const selectedMonth = ref('')
const selectedMonthDate = ref<string[]>([
  String(new Date().getFullYear()),
  String(new Date().getMonth() + 1).padStart(2, '0')
])
const showMonthPicker = ref(false)
const showMonthPickerPopup = ref(false)
const exportingId = ref<number | null>(null)  // 当前正在导出的结算单ID

// 选中月份的显示文本
const selectedMonthDisplay = computed(() => {
  if (selectedMonthDate.value && selectedMonthDate.value.length === 2) {
    const year = selectedMonthDate.value[0]
    const month = selectedMonthDate.value[1]
    return `${year}年${month}月`
  }
  return '选择月份'
})

const formatNumber = (num: number | string | undefined) => {
  if (num === undefined || num === null) return '0.00'
  const value = typeof num === 'string' ? parseFloat(num) : num
  if (isNaN(value)) return '0.00'
  return value.toFixed(2)
}

const formatMonth = (date: string) => {
  return dayjs(date).format('YYYY-MM-DD')
}

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

const showConstructingProjects = () => {
  activeTab.value = 'constructing'
  popupTitle.value = '施工中工程'
  showProjectsPopup.value = true
  onRefresh()
}

const showSettlingProjects = () => {
  activeTab.value = 'settling'
  popupTitle.value = '统计中工程'
  showProjectsPopup.value = true
  onRefresh()
}

const showSettledProjects = () => {
  activeTab.value = 'settled'
  popupTitle.value = '今年结算工程'
  showProjectsPopup.value = true
  onRefresh()
}

const showThisMonthProjects = () => {
  activeTab.value = 'thisMonth'
  popupTitle.value = '本月工程'
  showProjectsPopup.value = true
  onRefresh()
}

const loadSettlementStatistics = async () => {
  try {
    const response = await settlementsApi.getSettlementStatistics()
    Object.assign(settlementStatistics, response)
  } catch (error: any) {
    console.error('获取结算统计失败:', error)
  }
}

const loadProjectList = async () => {
  if (loading.value || finished.value) return

  try {
    loading.value = true
    
    let params: any = {
      page: pagination.page,
      size: pagination.size
    }
    
    if (activeTab.value === 'constructing') {
      params.status = 'constructing'
      params.settlementStatus = 'unsettled'
    } else if (activeTab.value === 'settling') {
      params.status = 'completed'
      params.settlementStatus = 'settling'
    } else if (activeTab.value === 'settled') {
      params.status = 'completed'
      params.settlementStatus = 'settled'
      params.year = new Date().getFullYear()
    } else if (activeTab.value === 'thisMonth') {
      const year = selectedMonthDate.value[0]
      const month = selectedMonthDate.value[1]
      params.yearMonth = `${year}-${month}`
    }
    
    const response = await projectsApi.getProjects(params)

    const list = Array.isArray(response?.list) ? response.list : []
    if (list.length === 0) {
      finished.value = true
    } else {
      projectList.value = [...projectList.value, ...list]
      pagination.page++
      if (list.length < pagination.size) {
        finished.value = true
      }
    }
  } catch (error: any) {
    console.error('加载工程列表失败:', error)
    finished.value = true
    const errorMsg = (error as any)?.message || '加载失败'
    if (errorMsg.includes('服务器错误') || errorMsg.includes('500')) {
      showToast('暂无数据，请先添加工程')
    } else {
      showToast(errorMsg)
    }
  } finally {
    loading.value = false
  }
}

const onRefresh = async () => {
  projectList.value = []
  finished.value = false
  pagination.page = 1
  refreshing.value = true
  await loadSettlementStatistics()
  await loadProjectList()
  refreshing.value = false
}

const onLoad = () => {
  loadProjectList()
}

const getUnitName = (unit: string) => {
  const unitMap: Record<string, string> = {
    length: '米',
    perimeter: '米',
    area: '㎡'
  }
  return unitMap[unit] || unit
}

const getProjectQuantity = (project: ProjectData, planId: number) => {
  // 使用新的数据结构：project.plan_quantities
  const planQty = project.plan_quantities?.[planId]
  if (!planQty) return '-'
  
  const quantity = planQty.total_quantity
  if (quantity === undefined || quantity === null) return '-'
  
  const plan = constructionPlans.value.find(p => p.id === planId)
  return `${quantity.toFixed(2)}${plan ? getUnitName(plan.unit) : ''}`
}

// 切换工程展开/折叠
const toggleProjectExpand = (projectId: number) => {
  const index = expandedProjects.value.indexOf(projectId)
  if (index === -1) {
    expandedProjects.value = [...expandedProjects.value, projectId]
  } else {
    expandedProjects.value = expandedProjects.value.filter(id => id !== projectId)
  }
}

// 获取工程的子项目列表
const getProjectSubprojects = (project: ProjectData) => {
  return project.subprojects || []
}

// 格式化子项目数量显示
const formatSubQuantity = (sub: any) => {
  const quantity = sub.user_quantity || 0
  const plan = constructionPlans.value.find(p => p.id === sub.plan_id)
  return `${quantity.toFixed(2)}${plan ? getUnitName(plan.unit) : ''}`
}

// 结算历史：切换工程展开/折叠
const toggleHistoryProjectExpand = (settlementId: number, projectId: number) => {
  const key = `${settlementId}-${projectId}`
  const index = expandedHistoryProjects.value.indexOf(key)
  if (index === -1) {
    expandedHistoryProjects.value = [...expandedHistoryProjects.value, key]
  } else {
    expandedHistoryProjects.value = expandedHistoryProjects.value.filter(k => k !== key)
  }
}

// 结算历史：检查工程是否展开
const isHistoryProjectExpanded = (settlementId: number, projectId: number) => {
  return expandedHistoryProjects.value.includes(`${settlementId}-${projectId}`)
}

// 结算历史：获取工程的子项目列表
const getHistorySubprojects = (_settlement: any, project: any) => {
  return project.subprojects || []
}

const toggleSelectAll = () => {
  if (allSelected.value) {
    selectedProjectIds.value = []
  } else {
    selectedProjectIds.value = projectData.value.map(p => p.id)
  }
}

const toggleProjectSelection = (projectId: number, selected: boolean) => {
  const currentIndex = selectedProjectIds.value.indexOf(projectId)
  if (selected && currentIndex === -1) {
    selectedProjectIds.value = [...selectedProjectIds.value, projectId]
  } else if (!selected && currentIndex !== -1) {
    selectedProjectIds.value = selectedProjectIds.value.filter(id => id !== projectId)
  }
}

watch(selectedProjectIds, (newVal) => {
  allSelected.value = newVal.length === projectData.value.length && projectData.value.length > 0
  calculateSettlement()
})

const calculateSettlement = async () => {
  if (selectedProjectIds.value.length === 0) {
    calculationResult.value = {
      plan_totals: {},
      grand_total: 0,
      total_advance: 0,
      final_total: 0
    }
    return
  }

  try {
    const result = await salarySheetApi.calculate({
      projectIds: selectedProjectIds.value
    })
    calculationResult.value = result
  } catch (error: any) {
    console.error('计算结算金额失败:', error)
    const message = error?.message || '计算失败'
    showToast(message)
  }
}

const handleSettle = async () => {
  if (selectedProjectIds.value.length === 0) {
    showToast('请选择要结算的工程')
    return
  }

  if (!navigator.onLine) {
    showToast({ type: 'fail', message: '网络连接已断开，请检查网络后重试' })
    return
  }

  try {
    settling.value = true
    const response = await salarySheetApi.settle({
      projectIds: selectedProjectIds.value
    })
    showSuccessToast(`结算成功！单号：${response.settlement_no}，历史快照已保存`)
    selectedProjectIds.value = []
    allSelected.value = false
    await loadSalarySheetData()
    await loadSettlementStatistics()
    await loadSettlementList()
  } catch (error: any) {
    console.error('结算失败:', error)
    let errorMessage = '结算失败'
    
    if (!navigator.onLine) {
      errorMessage = '网络连接已断开，请检查网络设置'
    } else if (error.message?.includes('超时') || error.message?.includes('timeout')) {
      errorMessage = '网络响应超时，结算操作较慢，请稍后查看结算历史'
    } else if (error.message?.includes('网络') || error.message?.includes('Network')) {
      errorMessage = '网络连接失败，请检查网络设置'
    } else if (error.message) {
      errorMessage = error.message
    }
    
    showToast(errorMessage)
  } finally {
    settling.value = false
  }
}

const loadSalarySheetData = async () => {
  try {
    const [plans, data] = await Promise.all([
      salarySheetApi.getConstructionPlans(),
      salarySheetApi.getProjects()
    ])

    constructionPlans.value = Array.isArray(plans) ? plans : []

    const responseData = data as any
    const projects = responseData.projects || []
    const planTotals = responseData.plan_totals || {}
    const grandTotal = responseData.grand_total || 0
    const totalAdvance = responseData.total_advance || 0
    const finalTotal = responseData.final_total || 0
    const advancesData = responseData.advances || []

    advances.value = Array.isArray(advancesData) ? advancesData : []

    // 直接使用后端返回的工程数据（已包含子项目明细）
    projectData.value = Array.isArray(projects) ? projects : []

    backendPlanTotals.value = planTotals
    backendGrandTotal.value = grandTotal
    backendTotalAdvance.value = totalAdvance
    backendAdvances.value = advancesData
    backendFinalTotal.value = finalTotal
  } catch (error: any) {
    console.error('加载结算单数据失败:', error)
    const errorMsg = error?.message || '加载失败'
    if (errorMsg.includes('服务器错误') || errorMsg.includes('500')) {
      showToast('暂无结算数据')
    } else {
      showToast(errorMsg)
    }
  }
}

const loadSettlementList = async () => {
  try {
    const response = await salarySheetApi.getSettlementHistory()
    const settlements = Array.isArray(response) ? response : []
    
    settlementHistoryList.value = settlements
  } catch (error) {
    console.error('加载结算历史数据失败:', error)
    const errorMsg = (error as any)?.message || '加载失败'
    showToast(errorMsg)
  }
}

// 获取唯一的工程列表（去重）
const getUniqueProjects = (projects: ProjectData[]) => {
  const seen = new Set()
  return projects.filter(project => {
    if (seen.has(project.id)) {
      return false
    }
    seen.add(project.id)
    return true
  })
}

const getSettlementProjectQuantity = (_settlement: SettlementHistory, project: ProjectData, planId: number) => {
  // 使用新的数据结构：project.plan_quantities
  const planQty = project.plan_quantities?.[planId]
  if (!planQty) return '-'
  
  const quantity = planQty.total_quantity
  if (quantity === undefined || quantity === null) return '-'
  
  const plan = constructionPlans.value.find(p => p.id === planId)
  return `${quantity.toFixed(2)}${plan ? getUnitName(plan.unit) : ''}`
}

const handleProjectClick = (project: Project) => {
  router.push(`/project/${project.id}`)
}

const onMonthConfirm = ({ selectedValues }: any) => {
  if (selectedValues && selectedValues.length >= 2) {
    const year = selectedValues[0]
    const month = selectedValues[1]
    selectedMonthDate.value = [year, month]
    selectedMonth.value = `${year}-${month}`
  }
  showMonthPicker.value = false
  showMonthPickerPopup.value = false
  // 刷新列表
  onRefresh()
}

const exportSettlementToExcel = async (settlement: SettlementHistory) => {
  try {
    exportingId.value = settlement.settlement_id
    const response = await fetch(`${baseURL}/v1/settlements/history/export/${settlement.settlement_id}`, {
      headers: {
        'Authorization': `Bearer ${authStore.token}`
      }
    })
    
    if (!response.ok) {
      throw new Error('导出失败')
    }
    
    // 从响应头中读取文件名
    let fileName = ''
    const contentDisposition = response.headers.get('Content-Disposition')
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename\*?=['"]?(?:UTF-8'')?([^;'\s]+)/i)
      if (filenameMatch && filenameMatch[1]) {
        fileName = decodeURIComponent(filenameMatch[1])
      }
    }
    
    const blob = await response.blob()
    
    // 将文件名存储到blob对象上（通过自定义属性）
    ;(blob as any).fileName = fileName
    
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    
    // 使用从响应头读取的文件名，如果没有则使用默认格式
    if (fileName) {
      a.download = fileName
    } else {
      // 备用文件名格式：用户全名 结算时间段
      const userName = authStore.userInfo?.nickname || authStore.userInfo?.username || '未知用户'
      const startDate = formatDate(settlement.start_month)
      const endDate = formatDate(settlement.end_month)
      a.download = `${userName} ${startDate} 至 ${endDate}.xlsx`
    }
    
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    
    showSuccessToast('导出成功')
  } catch (error: any) {
    console.error('导出Excel失败:', error)
    const errorMsg = error?.message || '导出失败'
    showToast(errorMsg)
  } finally {
    exportingId.value = null
  }
}

onMounted(() => {
  loadSettlementStatistics()
  loadProjectList()
  loadSalarySheetData()
  loadSettlementList()
})
</script>

<style scoped>
.statistics {
  min-height: 100vh;
  background: linear-gradient(180deg, #f9fef5 0%, #f9fef5 100%);
  padding-top: 46px;
  padding-bottom: 50px;
}

.statistics :deep(.van-nav-bar) {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
}

.statistics :deep(.van-nav-bar__title) {
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
  padding: 0;
  width: 100%;
  margin: 0 auto;
}

.stats-container {
  width: 96%;
  margin: 0 auto 16px;
}

.stats-container :deep(.van-grid-item__content) {
  padding: 8px;
  overflow: hidden;
}

.stats-container :deep(.van-grid-item) {
  overflow: hidden;
}

@media screen and (max-width: 375px) {
  .stats-container {
    width: 96%;
    margin: 0 auto 12px;
  }
}

@media screen and (max-width: 320px) {
  .stats-container {
    width: 96%;
    margin: 0 auto 10px;
  }
}

.stat-card {
  display: flex;
  flex-direction: column;
  padding: 10px;
  background: linear-gradient(135deg, #ffffff 0%, #f9fef5 100%);
  border-radius: 8px;
  box-shadow: 0 2px 6px rgba(132, 204, 22, 0.08);
  transition: all 0.3s ease;
  cursor: pointer;
  border: 1px solid #e6f4d0;
  overflow: hidden;
  min-width: 0;
  height: 100%;
}

.stat-card:active {
  transform: scale(0.98);
  box-shadow: 0 1px 4px rgba(132, 204, 22, 0.15);
}

.stat-top {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  min-width: 0;
  overflow: hidden;
}

.stat-icon {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 8px;
  color: white;
  box-shadow: 0 1px 4px rgba(132, 204, 22, 0.2);
}

.stat-title {
  font-size: 12px;
  color: #333842;
  font-weight: 500;
  line-height: 1.3;
  word-break: break-all;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.stat-bottom {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  min-width: 0;
  overflow: hidden;
  padding-left: 40px;
}

.stat-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 400;
  flex-shrink: 0;
}

.stat-amount {
  font-size: 14px;
  font-weight: bold;
  color: #333842;
  word-break: break-all;
  line-height: 1.2;
}

.stat-month {
  font-size: 11px;
  color: #84cc16;
  font-weight: 500;
  margin-top: 2px;
}

.project-card {
  background: linear-gradient(135deg, #ffffff 0%, #f0fdf4 100%);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(132, 204, 22, 0.08);
  border: 1px solid #e6f4d0;
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f9fef5;
}

.header-top {
  flex: 1;
}

.project-name {
  font-size: 16px;
  font-weight: bold;
  color: #333842;
}

.code-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.time-info {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.project-code {
  font-size: 12px;
  color: #64748b;
}

.amount-wrap {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f9fef5;
}

.amount-left {
  flex: 1;
}

.amount-label {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.amount-value {
  font-size: 18px;
  font-weight: bold;
  color: #333842;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-item {
  display: flex;
  align-items: center;
}

.info-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.info-icon {
  font-size: 16px;
}

.info-text {
  font-size: 14px;
  color: #64748b;
}

.settlement-section {
  margin-top: 16px;
  padding: 0;
  width: 98%;
  margin-left: auto;
  margin-right: auto;
}

@media screen and (max-width: 375px) {
  .settlement-section {
    width: 98%;
    margin-top: 12px;
  }
}

@media screen and (max-width: 320px) {
  .settlement-section {
    width: 98%;
    margin-top: 10px;
  }
}

/* 固定标题栏样式 */
.fixed-title-bar {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  border-radius: 8px 8px 0 0;
  margin-bottom: 0;
}

.fixed-title-bar .title-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  width: 100%;
}

.fixed-title-bar .title-text {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.fixed-title-bar .title-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.fixed-title-bar .title-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.fixed-title-bar .settlement-no {
  font-size: 13px;
  font-weight: 500;
  color: #fff;
}

.fixed-title-bar .settlement-date {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.9);
}

.fixed-title-bar .selected-count {
  font-size: 11px;
  color: #fff;
  font-weight: 500;
}

/* 导出按钮样式 */
.export-btn {
  min-width: 80px;
}

.export-btn .van-icon {
  margin-right: 4px;
}

/* 表格标题行样式 */
.salary-table .title-row .title-cell {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  padding: 0;
  border: none;
}

.title-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  width: 100%;
}

.title-text {
  font-size: 18px;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.title-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.title-info .settlement-no {
  font-size: 14px;
  font-weight: 500;
  color: #fff;
}

.title-info .settlement-date {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
}

.selected-count {
  font-size: 12px;
  color: #fff;
  font-weight: 500;
}

.unselected-row {
  opacity: 0.4;
  background-color: #f5f5f5;
}

.history-section {
  margin-top: 16px;
  padding: 0;
  width: 98%;
  margin-left: auto;
  margin-right: auto;
}

@media screen and (max-width: 375px) {
  .history-section {
    width: 98%;
    margin-top: 12px;
  }
}

@media screen and (max-width: 320px) {
  .history-section {
    width: 98%;
    margin-top: 10px;
  }
}

.table-wrapper {
  overflow-x: auto;
  width: 100%;
}

/* 结算区域的表格包装器与固定标题栏融合 */
.settlement-section .table-wrapper,
.history-section .table-wrapper {
  border-radius: 0 0 8px 8px;
  overflow-x: auto;
  overflow-y: visible;
}

.salary-table {
  border-collapse: collapse;
  width: 100%;
  margin: 0;
  background-color: white;
}

.salary-table th,
.salary-table td {
  border: 1px solid #9ca3af;
  padding: 8px 12px;
  text-align: center;
  font-size: 12px;
  vertical-align: middle;
  white-space: nowrap;
}

.salary-table th {
  background: linear-gradient(135deg, #f9fafb 0%, #f3f4f6 100%);
  font-weight: bold;
  color: #374151;
  border-bottom: 2px solid #84cc16;
}

.salary-table tbody tr:nth-child(even) {
  background-color: #fafafa;
}

.salary-table tbody tr:hover {
  background-color: #f0f9ff;
}

.salary-table .price-row td {
  background-color: #f5f5f5;
  font-size: 11px;
  font-weight: 500;
  color: #666;
}

.salary-table .total-row td {
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  font-weight: bold;
  color: #1e40af;
}

.salary-table .advance-row td {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  font-weight: 500;
  color: #92400e;
}

.salary-table .grand-total-row td {
  background: linear-gradient(135deg, #d4edda 0%, #c3e6cb 100%);
  font-weight: bold;
  font-size: 13px;
  color: #1e40af;
  border-bottom: 2px solid #000000;
}

.salary-table .final-total-row td {
  background: linear-gradient(135deg, #fce4ec 0%, #fadbd8 100%);
  font-weight: bold;
  font-size: 13px;
  color: #1e40af;
  border-bottom: 2px solid #000000;
}

@media screen and (max-width: 375px) {
  .stat-card {
    padding: 8px;
  }
  
  .stat-icon {
    width: 28px;
    height: 28px;
    min-width: 28px;
    margin-right: 6px;
  }
  
  .stat-icon :deep(.van-icon) {
    font-size: 16px;
  }
  
  .stat-title {
    font-size: 11px;
  }
  
  .stat-bottom {
    padding-left: 34px;
  }
  
  .stat-label {
    font-size: 11px;
  }
  
  .stat-amount {
    font-size: 13px;
  }
  
  .salary-table th,
  .salary-table td {
    padding: 6px 8px;
    font-size: 11px;
  }
  
  .salary-table .price-row td {
    font-size: 10px;
  }
  
  .title-text {
    font-size: 16px;
  }
}

@media screen and (max-width: 320px) {
  .stat-card {
    padding: 6px;
  }
  
  .stat-icon {
    width: 24px;
    height: 24px;
    min-width: 24px;
    margin-right: 4px;
  }
  
  .stat-icon :deep(.van-icon) {
    font-size: 14px;
  }
  
  .stat-title {
    font-size: 10px;
  }
  
  .stat-bottom {
    padding-left: 28px;
  }
  
  .stat-label {
    font-size: 10px;
  }
  
  .stat-amount {
    font-size: 12px;
  }
  
  .salary-table th,
  .salary-table td {
    padding: 4px 1px;
    font-size: 10px;
  }
  
  .salary-table .price-row td {
    font-size: 9px;
  }
  
  .title-text {
    font-size: 14px;
  }
}

.settlement-section :deep(.van-empty),
.history-section :deep(.van-empty) {
  padding: 20px 0;
}

.settlement-section :deep(.van-empty__image),
.history-section :deep(.van-empty__image) {
  width: 80px;
  height: 80px;
}

.settlement-section :deep(.van-empty__description),
.history-section :deep(.van-empty__description) {
  padding: 10px 20px;
  font-size: 13px;
}

/* 工程名称单元格样式 */
.project-name-cell {
  cursor: pointer;
  user-select: none;
}

.project-name-cell:hover {
  background-color: #f0f9ff;
}

.expand-icon {
  display: inline-block;
  width: 16px;
  font-size: 10px;
  color: #64748b;
  margin-right: 4px;
}

/* 子项目行样式 */
.subproject-row {
  background-color: #f8fafc !important;
}

.subproject-row td {
  font-size: 11px !important;
  color: #64748b !important;
}

.subproject-name {
  padding-left: 28px !important;
  color: #475569 !important;
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

.white-arrow-nav :deep(.van-nav-bar__arrow) {
  color: #fff !important;
}

.white-text-nav :deep(.van-nav-bar__text) {
  color: #fff !important;
}

/* 月份筛选器样式 */
.month-filter-bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f9fafb 0%, #f3f4f6 100%);
  border-bottom: 1px solid #e5e7eb;
}

.filter-label {
  font-size: 14px;
  color: #374151;
  font-weight: 500;
  margin-right: 12px;
}
</style>
