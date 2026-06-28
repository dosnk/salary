<template>
  <div class="dashboard">
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
      <van-cell-group inset>
        <van-field
          v-model="formData.name"
          label="客户地址"
          placeholder="请输入客户地址"
          @update:model-value="handleProjectNameInput"
        />

        <van-field
          v-model="formData.spaceType"
          readonly
          clickable
          label="空间类型"
          placeholder="请选择空间类型"
          @click="showSpaceTypePicker = true"
        />

        <van-field
          v-model="formData.constructionScheme"
          readonly
          clickable
          label="施工方案"
          placeholder="请选择施工方案"
          @click="showSchemePicker = true"
        />

        <van-field
          v-model.number="formData.length"
          type="number"
          label="长度(cm)"
          placeholder="请输入长度"
        />

        <van-field
          v-model.number="formData.width"
          type="number"
          label="宽度(cm)"
          placeholder="请输入宽度"
          :disabled="isCurtainBox"
        />

        <van-field label="分配方式">
          <template #input>
            <van-radio-group v-model="formData.salaryDistribution" direction="horizontal">
              <van-radio name="average">平均</van-radio>
            </van-radio-group>
            <div class="distribution-hint">如需按工日分配，在工程管理里更改</div>
          </template>
        </van-field>

        <van-cell title="请选择施工人员">
          <template #label>
            <div class="constructor-scroll">
              <van-checkbox-group v-model="formData.constructors" direction="horizontal">
                <van-checkbox
                  v-for="user in constructorUsers"
                  :key="user.id"
                  :name="user.id"
                  shape="square"
                >
                  {{ user.nickname || user.username }}
                </van-checkbox>
              </van-checkbox-group>
            </div>
          </template>
        </van-cell>

        <div class="calculation-result">
          <div class="result-item">
            <span class="value formula">计算预览：{{ calculationFormula }}</span>
          </div>
        </div>

        <van-field
          v-model="formData.remark"
          type="textarea"
          label="工程备注"
          label-class="remark-label"
          placeholder="请输入工程备注"
          :autosize="{ minHeight: 10, maxHeight: 200 }"
          class="remark-field"
        />

        <van-cell>
          <template #value>
            <van-button type="primary" @click="handleSave" :loading="saving" class="save-button">
              保存
            </van-button>
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group title="工程历史" inset style="margin-top: 8px">
        <van-field
          v-model="selectedYearMonth"
          readonly
          clickable
          label="年月"
          placeholder="选择年月"
          @click="showMonthPicker = true"
          style="background-color: #e3f2fd;"
        />

        <van-empty v-if="projects.length === 0" description="暂无数据" />
        <div v-else class="project-list">
          <div
            v-for="project in projects"
            :key="project.id"
            class="project-item"
          >
            <!-- 工程标题行 -->
            <div class="project-header">
              <div class="project-title">
                <span class="project-name">{{ project.name }}</span>
                <span class="project-amount">¥{{ formatNumber(project.total_amount) }}</span>
              </div>
            </div>

            <!-- 施工人员列表行 -->
            <div class="project-constructors">
              <span
                v-for="(constructor, index) in project.constructors || []"
                :key="constructor.id"
                class="constructor-name"
              >
                {{ constructor.nickname || constructor.username }}{{ index < (project.constructors?.length || 0) - 1 ? '、' : '' }}
              </span>
            </div>

            <!-- 附件操作行 -->
            <div class="project-actions">
              <van-button type="default" size="small" @click="handleViewAttachments(project)">
                查看附件 ({{ project.files_count || 0 }})
              </van-button>
              <van-button type="default" size="small" @click="handleUpload(project)">
                上传附件
              </van-button>
            </div>

            <!-- 时间信息行 -->
            <div class="project-time-info">
              <span>创建时间：{{ formatDate(project.created_at) }}</span>
              <span>更新时间：{{ formatDate(project.updated_at) }}</span>
            </div>

            <!-- 子项目表格 -->
            <div class="subproject-table-container">
              <table class="subproject-table">
                <thead>
                  <tr>
                    <th>序号</th>
                    <th>空间</th>
                    <th>方案</th>
                    <th>尺寸(米)</th>
                    <th>数量</th>
                    <th>金额</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(sub, index) in project.sub_projects"
                    :key="sub.id"
                  >
                    <td>{{ index + 1 }}</td>
                    <td>{{ sub.space_type_name }}</td>
                    <td>{{ sub.construction_plan_name }}</td>
                    <td>{{ formatNumber(sub.length) }} × {{ formatNumber(sub.width) }}</td>
                    <td>{{ formatNumber(sub.quantity) }} {{ getUnitName(sub.unit || '') }}</td>
                    <td>¥{{ formatNumber(sub.amount) }}</td>
                    <td>
                      <van-button type="primary" size="mini" @click="handleEdit(sub)">
                        编辑
                      </van-button>
                      <van-button type="danger" size="mini" @click="handleDelete(sub)">
                        删除
                      </van-button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </van-cell-group>
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
          <van-button v-if="unreadCount > 0" size="small" type="primary" @click="handleMarkAllAsRead" :loading="markingAll">全部已读</van-button>
        </template>
      </van-nav-bar>
      <van-tabs v-model:active="messageActiveTab" @change="onMessageTabChange">
        <van-tab title="全部" name="all">
          <van-pull-refresh v-model="messageRefreshing" @refresh="onMessageRefresh">
            <van-list
              v-model:loading="messageLoading"
              :finished="messageFinished"
              finished-text="没有更多了"
              @load="onMessageLoad"
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
                  <van-icon name="delete-o" class="delete-icon" @click.stop="handleDeleteMessage(message)" />
                </div>
                <div class="message-content">{{ message.content }}</div>
                <div class="message-footer">
                  <span class="message-time">{{ formatMessageDate(message.created_at) }}</span>
                </div>
              </div>
            </van-list>
          </van-pull-refresh>
        </van-tab>
        <van-tab title="未读" name="unread">
          <van-pull-refresh v-model="messageRefreshing" @refresh="onMessageRefresh">
            <van-list
              v-model:loading="messageLoading"
              :finished="messageFinished"
              finished-text="没有更多了"
              @load="onMessageLoad"
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
                  <van-icon name="delete-o" class="delete-icon" @click.stop="handleDeleteMessage(message)" />
                </div>
                <div class="message-content">{{ message.content }}</div>
                <div class="message-footer">
                  <span class="message-time">{{ formatMessageDate(message.created_at) }}</span>
                </div>
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
        <div class="detail-content">
          <p>{{ selectedMessage?.content }}</p>
        </div>
        <div class="detail-meta">
          <div class="meta-item">
            <van-icon name="clock-o" />
            <span>{{ selectedMessage ? formatMessageDate(selectedMessage.created_at) : '' }}</span>
          </div>
          <div class="meta-item">
            <van-icon name="user-o" />
            <span>发送人：{{ selectedMessage?.creator_name || '系统' }}</span>
          </div>
        </div>
        <div class="detail-actions">
          <van-button v-if="!selectedMessage?.is_read" type="primary" @click="handleMarkAsRead">标记已读</van-button>
          <van-button type="danger" @click="handleDeleteFromDetail">删除</van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="showSpaceTypePicker" position="bottom">
      <van-picker
        :columns="spaceTypeOptions.map(option => ({ text: option, value: option }))"
        @confirm="onSpaceTypeConfirm"
        @cancel="showSpaceTypePicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showSchemePicker" position="bottom">
      <van-picker
        :columns="schemeOptions.map(option => ({ text: option, value: option }))"
        @confirm="onSchemeConfirm"
        @cancel="showSchemePicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showMonthPicker" position="bottom">
      <van-date-picker
        v-model="monthValue"
        type="year-month"
        title="选择月份"
        :min-date="minDate"
        :max-date="maxDate"
        class="year-month-picker"
        @confirm="onMonthConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showUpload" position="bottom" :style="{ height: '60%' }">
      <div class="upload-popup">
        <div class="upload-header">
          <span class="upload-title">上传附件</span>
          <van-button 
            v-if="uploading" 
            type="danger" 
            size="small" 
            @click="handleCancelUpload"
          >
            取消上传
          </van-button>
        </div>
        
        <van-uploader
          v-model="fileList"
          multiple
          :max-count="9"
          :after-read="afterRead"
          :disabled="uploading"
        />
        
        <div v-if="uploading" class="upload-progress">
          <van-progress :percentage="uploadProgress" stroke-width="8" />
          <span class="progress-text">上传中... {{ uploadProgress }}%</span>
        </div>
        
        <van-button type="primary" block @click="showUpload = false" style="margin-top: 20px">
          完成
        </van-button>
      </div>
    </van-popup>

    <FileListPopup v-model:show="showAttachments" :project-name="currentProjectName" :files="attachments" :can-delete="false" />

    <van-popup v-model:show="showEdit" position="center" :style="{ width: '90%', maxWidth: '600px', maxHeight: '90vh', overflow: 'auto' }">
      <div class="edit-popup">
        <div class="edit-header">
          <span class="edit-title">编辑子项目</span>
          <van-icon name="cross" @click="showEdit = false" />
        </div>
        
        <div class="edit-content">
          <van-field
            v-model="editForm.spaceType"
            readonly
            clickable
            label="空间类型"
            placeholder="请选择空间类型"
            @click="showSpaceTypePicker = true"
          />
          
          <van-field
            v-model="editForm.constructionScheme"
            readonly
            clickable
            label="施工方案"
            placeholder="请选择施工方案"
            @click="showSchemePicker = true"
          />
          
          <van-field
            v-model.number="editForm.length"
            type="number"
            label="长度(cm)"
            placeholder="请输入长度"
            @input="updateEditUnitPrice"
          />
          
          <van-field
            v-model.number="editForm.width"
            type="number"
            label="宽度(cm)"
            placeholder="请输入宽度"
            :disabled="isEditCurtainBox"
            @input="updateEditUnitPrice"
          />
          
          <van-field
            v-model.number="editForm.unitPrice"
            type="number"
            label="单价"
            placeholder="单价"
            readonly
            style="background-color: #f5f5f5;"
          />
          
          <div class="calculation-result">
            <div class="result-item">
              <span class="value formula">{{ editCalculationFormula }}</span>
            </div>
          </div>
          
          <van-field
            v-model="editForm.remark"
            type="textarea"
            label="备注"
            placeholder="请输入备注"
            rows="3"
          />
          
          <div class="edit-actions">
            <van-button @click="showEdit = false">取消</van-button>
            <van-button type="primary" @click="handleSaveEdit" :loading="saving">保存</van-button>
          </div>
        </div>
      </div>
    </van-popup>

    <div class="footer">
      <div class="footer-content">
        <div class="copyright">©微信群：三人行必有我师</div>
        <div class="api-latency">API时延：{{ apiLatency }}ms</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: 'Dashboard'
}
</script>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { projectsApi, type Project } from '@/api/projects'
import dictionaryApi from '@/api/dictionary'
import { uploadApi } from '@/api/upload'
import { useAuthStore } from '@/stores/auth'
import type { UserInfo } from '@/api/auth'
import type { ConstructorUser } from '@/api/users'
import { baseURL } from '@/utils/request'
import { useDateFormat } from '@/composables/useDateFormat'
import { useMessage } from '@/composables/useMessage'
import FileListPopup from '@/components/FileListPopup.vue'

const debounce = <T extends (...args: any[]) => any>(fn: T, delay: number): ((...args: Parameters<T>) => void) => {
  let timer: ReturnType<typeof setTimeout> | null = null
  return (...args: Parameters<T>) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(...args), delay)
  }
}

const router = useRouter()
const authStore = useAuthStore()
const { formatDate, formatMessageDate } = useDateFormat()
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
const isDocumenter = computed(() => authStore.userInfo?.role === 'documenter')

interface FormData {
  name: string
  spaceType: string
  constructionScheme: string
  length: number
  width: number
  salaryDistribution: 'average' | 'work_days'
  constructors: number[]
  unitPrice: number
  remark: string
}

const constructorUsers = ref<ConstructorUser[]>([])
const projects = ref<Project[]>([])
const saving = ref(false)
const apiLatency = ref(0)
const requestStartTime = ref(0)
const activeTab = ref(0)
const pageAbortController = ref<AbortController | null>(null)

const showSpaceTypePicker = ref(false)
const showSchemePicker = ref(false)
const showMonthPicker = ref(false)
const showUpload = ref(false)
const showAttachments = ref(false)
const showEdit = ref(false)
const currentUploadProject = ref<Project | null>(null)
const currentEditProject = ref<Project | null>(null)
const currentEditSubProject = ref<any>(null)
const fileList = ref<any[]>([])
const uploadProgress = ref(0)
const uploading = ref(false)
const uploadAbortController = ref<AbortController | null>(null)
const attachments = ref<any[]>([])
const currentProjectName = ref('')

const selectedYearMonth = ref(new Date().toISOString().slice(0, 7))
const monthValue = ref<string[]>([
  String(new Date().getFullYear()),
  String(new Date().getMonth() + 1)
])

const minDate = new Date(2020, 0, 1)
const maxDate = new Date(2030, 11, 31)

const spaceTypeOptions = ref<string[]>([])
const schemeOptions = ref<string[]>([])
const spaceTypeMap = ref<Map<string, any>>(new Map())
const schemeMap = ref<Map<string, any>>(new Map())

const formData = reactive<FormData>({
  name: '',
  spaceType: '',
  constructionScheme: '',
  length: 0,
  width: 0,
  salaryDistribution: 'average',
  constructors: [],
  unitPrice: 0,
  remark: ''
})

// 客户地址与施工人员的映射缓存
const addressConstructorMap = ref<Map<string, number[]>>(new Map())

const editForm = reactive<FormData>({
  name: '',
  spaceType: '',
  constructionScheme: '',
  length: 0,
  width: 0,
  salaryDistribution: 'average',
  constructors: [],
  unitPrice: 0,
  remark: ''
})

const editArea = computed(() => {
  return (editForm.length * editForm.width) / 10000
})

const editPerimeter = computed(() => {
  return (editForm.length + editForm.width) * 2 / 100
})

const editLengthValue = computed(() => {
  return editForm.length / 100
})

const editCurrentScheme = computed(() => {
  if (!editForm.constructionScheme) return null
  return schemeMap.value.get(editForm.constructionScheme)
})

const editCalculationFormula = computed(() => {
  const scheme = editCurrentScheme.value
  if (!scheme) return `${formatNumber(editArea.value)} m² × ¥${formatNumber(editForm.unitPrice)}/m² = ¥${formatNumber(editArea.value * editForm.unitPrice)}`
  
  let formula = ''
  switch (scheme.unit) {
    case 'area':
      formula = `${formatNumber(editArea.value)} m² × ¥${formatNumber(editForm.unitPrice)}/m² = ¥${formatNumber(editArea.value * editForm.unitPrice)}`
      break
    case 'perimeter':
      formula = `${formatNumber(editPerimeter.value)} m × ¥${formatNumber(editForm.unitPrice)}/m = ¥${formatNumber(editPerimeter.value * editForm.unitPrice)}`
      break
    case 'length':
      formula = `${formatNumber(editLengthValue.value)} m × ¥${formatNumber(editForm.unitPrice)}/m = ¥${formatNumber(editLengthValue.value * editForm.unitPrice)}`
      break
    default:
      formula = `${formatNumber(editArea.value)} m² × ¥${formatNumber(editForm.unitPrice)}/m² = ¥${formatNumber(editArea.value * editForm.unitPrice)}`
  }
  return formula
})

const updateEditUnitPrice = () => {
  const scheme = schemeMap.value.get(editForm.constructionScheme)
  if (scheme) {
    editForm.unitPrice = scheme.price
  }
}

const quantity = computed(() => {
  const scheme = currentScheme.value
  if (!scheme) return 0
  
  switch (scheme.unit) {
    case 'area':
      return (formData.length * formData.width) / 10000
    case 'perimeter':
      return (formData.length + formData.width) * 2 / 100
    case 'length':
      return formData.length / 100
    default:
      return (formData.length * formData.width) / 10000
  }
})

const perimeter = computed(() => {
  return (formData.length + formData.width) * 2 / 100
})

const lengthValue = computed(() => {
  return formData.length / 100
})

const currentScheme = computed(() => {
  if (!formData.constructionScheme) return null
  return schemeMap.value.get(formData.constructionScheme)
})

const isCurtainBox = computed(() => {
  return formData.constructionScheme === '窗帘盒'
})

const isEditCurtainBox = computed(() => {
  return editForm.constructionScheme === '窗帘盒'
})

const calculationFormula = computed(() => {
  const scheme = currentScheme.value
  if (!scheme) return `${formatNumber(quantity.value)} m² × ¥${formatNumber(formData.unitPrice)}/m² = ¥${formatNumber(quantity.value * formData.unitPrice)}`
  
  let formula = ''
  switch (scheme.unit) {
    case 'area':
      formula = `${formatNumber(quantity.value)} m² × ¥${formatNumber(formData.unitPrice)}/m² = ¥${formatNumber(quantity.value * formData.unitPrice)}`
      break
    case 'perimeter':
      formula = `${formatNumber(perimeter.value)} m × ¥${formatNumber(formData.unitPrice)}/m = ¥${formatNumber(perimeter.value * formData.unitPrice)}`
      break
    case 'length':
      formula = `${formatNumber(lengthValue.value)} m × ¥${formatNumber(formData.unitPrice)}/m = ¥${formatNumber(lengthValue.value * formData.unitPrice)}`
      break
    default:
      formula = `${formatNumber(quantity.value)} m² × ¥${formatNumber(formData.unitPrice)}/m² = ¥${formatNumber(quantity.value * formData.unitPrice)}`
  }
  return formula
})

const formatNumber = (num: number | string | undefined | null) => {
  if (num === undefined || num === null || num === '') return '0.00'
  const parsedNum = typeof num === 'string' ? parseFloat(num) : num
  if (typeof parsedNum !== 'number' || isNaN(parsedNum)) return '0.00'
  return parsedNum.toFixed(2)
}

const getUnitName = (unit: string) => {
  const unitMap: Record<string, string> = {
    length: '米',
    perimeter: '米',
    area: '㎡'
  }
  return unitMap[unit] || unit
}

const handleProjectNameInput = (newName: string) => {
  const oldName = formData.name
  formData.name = newName
  
  // 只有当地址真正改变时才更新施工人员
  if (oldName && oldName !== newName) {
    // 保存当前施工人员到旧地址的缓存
    if (formData.constructors.length > 0) {
      addressConstructorMap.value.set(oldName, [...formData.constructors])
      // 保存地址与施工人员的映射到缓存
      localStorage.setItem('addressConstructorMap', JSON.stringify(Array.from(addressConstructorMap.value.entries())))
    }
    
    // 从缓存中加载新地址对应的施工人员
    if (addressConstructorMap.value.has(newName)) {
      formData.constructors = [...addressConstructorMap.value.get(newName)!]
    }
    // 如果新地址不在缓存中，不清空施工人员，让用户自己选择
  }
  
  // 保存工程名称到缓存
  localStorage.setItem('projectName', newName)
}

const loadDictionaryData = async (forceRefresh = false) => {
  try {
    requestStartTime.value = Date.now()
    const dictionaryData = await dictionaryApi.getDictionaryWithCache(forceRefresh)
    apiLatency.value = Date.now() - requestStartTime.value
    
    spaceTypeOptions.value = dictionaryData.spaceTypes.map(type => type.name)
    schemeOptions.value = dictionaryData.constructionPlans.map(plan => plan.name)
    constructorUsers.value = dictionaryData.constructorUsers
    
    spaceTypeMap.value = new Map(dictionaryData.spaceTypes.map(type => [type.name, type]))
    schemeMap.value = new Map(dictionaryData.constructionPlans.map(plan => [plan.name, plan]))
  } catch (error) {
    console.error('加载字典数据失败:', error)
    showToast({ type: 'fail', message: '加载字典数据失败，请重试' })
  }
}

const loadProjects = async () => {
  try {
    if (pageAbortController.value) {
      pageAbortController.value.abort()
    }
    pageAbortController.value = new AbortController()
    
    requestStartTime.value = Date.now()
    const response = await projectsApi.getProjects({
      yearMonth: selectedYearMonth.value,
      page: 1,
      size: 20,
      signal: pageAbortController.value.signal
    })
    apiLatency.value = Date.now() - requestStartTime.value
    
    // 暂时恢复 N+1 查询，因为后端简化了查询
    const projectsWithDetails = await Promise.all(
      response.list.map(async (project) => {
        try {
          const detail = await projectsApi.getProject(project.id)
          return {
            ...project,
            sub_projects: detail.sub_projects || [],
            constructors: detail.constructors || [],
            files: detail.files || []
          }
        } catch (error: any) {
          console.error('加载工程详情失败:', project.id, error)
          if (error?.message?.includes('工程不存在')) {
            return null
          }
          return {
            ...project,
            sub_projects: [],
            constructors: [],
            files: []
          }
        }
      })
    )
    
    projects.value = projectsWithDetails.filter(p => p !== null)
  } catch (error: any) {
    if (error.name === 'AbortError') {
      return
    }
    console.error('加载工程列表失败:', error)
    if (error?.code === 5001 || error?.response?.status === 500) {
      projects.value = []
    }
  }
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

const onSpaceTypeConfirm = ({ selectedValues }: any) => {
  if (showEdit.value) {
    editForm.spaceType = selectedValues[0]
  } else {
    formData.spaceType = selectedValues[0]
  }
  showSpaceTypePicker.value = false
}

const onSchemeConfirm = ({ selectedValues }: any) => {
  const scheme = schemeMap.value.get(selectedValues[0])
  if (showEdit.value) {
    editForm.constructionScheme = selectedValues[0]
    if (scheme) {
      editForm.unitPrice = Number(scheme.price)
    }
  } else {
    formData.constructionScheme = selectedValues[0]
    if (scheme) {
      formData.unitPrice = Number(scheme.price)
    }
  }
  showSchemePicker.value = false
}

const onMonthConfirm = ({ selectedValues }: any) => {
  const year = selectedValues[0]
  const month = selectedValues[1]
  selectedYearMonth.value = `${year}-${month.toString().padStart(2, '0')}`
  showMonthPicker.value = false
  loadProjects()
}

const handleSave = async () => {
  if (!formData.name || formData.name.trim() === '') {
    showToast('请输入工程名称')
    return
  }
  if (!formData.spaceType) {
    showToast('请选择空间类型')
    return
  }
  if (!formData.constructionScheme) {
    showToast('请选择施工方案')
    return
  }
  if (!formData.length || formData.length <= 0) {
    showToast('请输入有效的长度')
    return
  }
  const scheme = currentScheme.value
  if ((!scheme || scheme.unit !== 'length') && (!formData.width || formData.width <= 0)) {
    showToast('请输入有效的宽度')
    return
  }
  if (!formData.salaryDistribution) {
    showToast('请选择分配方式')
    return
  }
  if (!formData.constructors || formData.constructors.length === 0) {
    showToast('请选择施工人员')
    return
  }
  if (!formData.unitPrice || formData.unitPrice <= 0) {
    showToast('请输入有效的单价')
    return
  }

  if (!navigator.onLine) {
    showToast({ type: 'fail', message: '网络连接已断开，请检查网络后重试' })
    return
  }

  try {
    saving.value = true
    const existingProject = projects.value.find(p => p.name === formData.name)

    if (existingProject) {
      await projectsApi.createProject({
        name: formData.name,
        remark: formData.remark,
        spaceType: formData.spaceType,
        constructionScheme: formData.constructionScheme,
        length: formData.length,
        width: formData.width,
        salaryDistribution: formData.salaryDistribution,
        constructors: formData.constructors.map(id => ({ userId: id }))
      })
      showToast('已添加为子项目')
    } else {
      await projectsApi.createProject({
        name: formData.name,
        remark: formData.remark,
        spaceType: formData.spaceType,
        constructionScheme: formData.constructionScheme,
        length: formData.length,
        width: formData.width,
        salaryDistribution: formData.salaryDistribution,
        constructors: formData.constructors.map(id => ({ userId: id }))
      })
      showToast('工程创建成功')
      
      localStorage.removeItem('formData')
    }

    formData.spaceType = ''
    formData.constructionScheme = ''
    formData.length = 0
    formData.width = 0
    formData.unitPrice = 0
    formData.remark = ''
    loadProjects()
  } catch (error: any) {
    console.error('保存失败:', error)
    let errorMessage = '保存失败'
    
    if (!navigator.onLine) {
      errorMessage = '网络连接已断开，请检查网络设置'
    } else if (error.message?.includes('超时') || error.message?.includes('timeout')) {
      errorMessage = '网络响应超时，请稍后重试'
    } else if (error.message?.includes('网络') || error.message?.includes('Network')) {
      errorMessage = '网络连接失败，请检查网络设置'
    } else if (error.message) {
      errorMessage = error.message
    }
    
    showToast({ type: 'fail', message: errorMessage })
  } finally {
    saving.value = false
  }
}

const handleEdit = (sub: any) => {
  currentEditSubProject.value = sub
  const project = projects.value.find(p => p.id === sub.project_id)
  if (project) {
    currentEditProject.value = project
  }
  
  editForm.spaceType = sub.space_type_name
  editForm.constructionScheme = sub.construction_plan_name
  editForm.length = Number((sub.length * 100).toFixed(0))
  editForm.width = Number((sub.width * 100).toFixed(0))
  editForm.remark = sub.remark || ''
  editForm.salaryDistribution = 'average'
  editForm.constructors = []
  
  const scheme = schemeMap.value.get(sub.construction_plan_name)
  if (scheme) {
    editForm.unitPrice = scheme.price
  }
  
  showEdit.value = true
}

const handleSaveEdit = async () => {
  if (!editForm.spaceType || !editForm.constructionScheme) {
    showToast({ type: 'fail', message: '请填写完整信息' })
    return
  }
  
  if (editForm.length <= 0) {
    showToast({ type: 'fail', message: '长度必须大于0' })
    return
  }
  const editScheme = editCurrentScheme.value
  if ((!editScheme || editScheme.unit !== 'length') && editForm.width <= 0) {
    showToast({ type: 'fail', message: '宽度必须大于0' })
    return
  }
  
  if (!currentEditProject.value || !currentEditSubProject.value) {
    showToast({ type: 'fail', message: '工程或子项目不存在' })
    return
  }
  
  try {
    saving.value = true
    
    await projectsApi.updateSubProject(
      currentEditProject.value.id,
      currentEditSubProject.value.id,
      {
        spaceType: editForm.spaceType,
        constructionScheme: editForm.constructionScheme,
        length: editForm.length,
        width: editForm.width,
        remark: editForm.remark
      }
    )
    
    showToast({ type: 'success', message: '修改成功' })
    showEdit.value = false
    loadProjects()
  } catch (error: any) {
    console.error('修改失败:', error)
    const errorMsg = error?.message || '修改失败'
    showToast({ type: 'fail', message: errorMsg })
  } finally {
    saving.value = false
  }
}

const handleDelete = async (sub: any) => {
  try {
    await showConfirmDialog({
      title: '提示',
      message: '确定要删除这个子项目吗？'
    })
    
    const project = projects.value.find(p => p.id === sub.project_id)
    if (!project) {
      showToast({ type: 'fail', message: '工程不存在' })
      return
    }
    
    await projectsApi.deleteSubProject(project.id, sub.id)
    showToast({ type: 'success', message: '删除成功' })
    loadProjects()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      const errorMsg = error?.message || '删除失败'
      showToast({ type: 'fail', message: errorMsg })
    }
  }
}

const handleUpload = (project: Project) => {
  currentUploadProject.value = project
  fileList.value = []
  showUpload.value = true
}

const handleViewAttachments = async (project: Project) => {
  try {
    const files = project.files || []
    
    if (files.length === 0) {
      showToast({ type: 'fail', message: '暂无附件' })
      return
    }

    currentProjectName.value = project.name
    attachments.value = files.map(file => ({
      ...file,
      url: file.path.startsWith('http') ? file.path : `${baseURL}${file.path}`
    }))
    showAttachments.value = true
  } catch (error) {
    console.error('获取附件失败:', error)
    showToast({ type: 'fail', message: '获取附件失败' })
  }
}

const FILE_SIZE_LIMITS: Record<string, number> = {
  image: 10 * 1024 * 1024,
  document: 20 * 1024 * 1024,
  video: 200 * 1024 * 1024,
  audio: 50 * 1024 * 1024
}

const ALLOWED_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.pdf', '.doc', '.docx', '.mp4', '.avi', '.mov', '.wmv', '.flv', '.mp3', '.wav', '.aac']

const getFileCategory = (type: string): string => {
  if (type.startsWith('image/')) return 'image'
  if (type.startsWith('video/')) return 'video'
  if (type.startsWith('audio/')) return 'audio'
  if (type.includes('pdf') || type.includes('word') || type.includes('document')) return 'document'
  return 'document'
}

const validateFile = (file: File): { valid: boolean; error?: string } => {
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    return { valid: false, error: `不支持的文件类型: ${ext}` }
  }
  
  const category = getFileCategory(file.type)
  const maxSize = FILE_SIZE_LIMITS[category] || FILE_SIZE_LIMITS.document
  
  if (file.size > maxSize) {
    const maxSizeMB = Math.round(maxSize / 1024 / 1024)
    return { valid: false, error: `文件超过${maxSizeMB}MB限制` }
  }
  
  if (file.size === 0) {
    return { valid: false, error: '文件内容为空' }
  }
  
  return { valid: true }
}

const uploadSingleFile = async (
  file: File, 
  projectName: string, 
  projectId: number,
  onProgress: (percent: number) => void,
  signal?: AbortSignal,
  maxRetries: number = 3
): Promise<{ success: boolean; fileName: string; fileUrl?: string; error?: string }> => {
  let lastError: string = ''
  
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      const uploadResult = await uploadApi.uploadFile(file, projectName, {
        onProgress,
        signal
      })
      
      const fileUrl = uploadResult.url
      const fileName = file.name
      const fileSize = file.size
      const fileType = file.type

      await projectsApi.uploadFile(projectId, {
        filename: fileName,
        originalName: fileName,
        path: fileUrl,
        size: fileSize,
        type: fileType
      })
      
      return { success: true, fileName, fileUrl }
    } catch (error: any) {
      lastError = error.message || '上传失败'
      
      if (error.name === 'AbortError') {
        return { success: false, fileName: file.name, error: '已取消上传' }
      }
      
      if (attempt < maxRetries) {
        await new Promise(resolve => setTimeout(resolve, 1000 * attempt))
      }
    }
  }
  
  return { success: false, fileName: file.name, error: lastError }
}

const afterRead = async (file: any) => {
  if (!currentUploadProject.value) {
    showToast({ type: 'fail', message: '请先选择要上传附件的工程' })
    return
  }

  const files = Array.isArray(file) ? file : [file]
  
  const validationErrors: string[] = []
  const validFiles: File[] = []
  
  for (const fileItem of files) {
    const actualFile = fileItem.file || fileItem
    const validation = validateFile(actualFile)
    
    if (!validation.valid) {
      validationErrors.push(`${actualFile.name}: ${validation.error}`)
    } else {
      validFiles.push(actualFile)
    }
  }
  
  if (validationErrors.length > 0) {
    showToast({ 
      type: 'fail', 
      message: validationErrors.length === 1 ? validationErrors[0] : `${validationErrors.length}个文件不符合要求`,
      duration: 3000
    })
    
    if (validFiles.length === 0) {
      return
    }
  }

  uploading.value = true
  uploadProgress.value = 0
  
  const abortController = new AbortController()
  uploadAbortController.value = abortController

  try {
    const uploadResults: { fileName: string; fileUrl?: string; success: boolean; error?: string }[] = []
    const totalFiles = validFiles.length
    let completedFiles = 0
    
    for (const actualFile of validFiles) {
      const result = await uploadSingleFile(
        actualFile,
        currentUploadProject.value.name,
        currentUploadProject.value.id,
        (percent) => {
          const baseProgress = (completedFiles / totalFiles) * 100
          const currentFileProgress = percent / totalFiles
          uploadProgress.value = Math.round(baseProgress + currentFileProgress)
        },
        abortController.signal,
        3
      )
      
      uploadResults.push(result)
      completedFiles++
      
      if (result.success) {
        uploadProgress.value = Math.round((completedFiles / totalFiles) * 100)
      }
    }

    const successCount = uploadResults.filter(r => r.success).length
    const failCount = uploadResults.filter(r => !r.success).length
    
    if (failCount === 0) {
      showToast({ type: 'success', message: `成功上传${successCount}个文件` })
      fileList.value = []
      showUpload.value = false
    } else if (successCount > 0) {
      showToast({ 
        type: 'fail', 
        message: `成功${successCount}个，失败${failCount}个`,
        duration: 3000
      })
      fileList.value = []
      showUpload.value = false
    } else {
      const failedNames = uploadResults.filter(r => !r.success).map(r => r.fileName).join(', ')
      showToast({ type: 'fail', message: `上传失败: ${failedNames}`, duration: 3000 })
    }
    
    await loadProjects()
  } catch (error: any) {
    if (error.name === 'AbortError') {
      showToast({ message: '已取消上传' })
    } else {
      console.error('上传失败:', error)
      showToast({ type: 'fail', message: error.message || '上传失败，请重试' })
    }
  } finally {
    uploading.value = false
    uploadProgress.value = 0
    uploadAbortController.value = null
  }
}

const handleCancelUpload = () => {
  if (uploadAbortController.value) {
    uploadAbortController.value.abort()
  }
}

// 监听客户地址变化，立即保存地址信息并恢复施工人员
watch(
  () => formData.name,
  (newAddress, oldAddress) => {
    if (newAddress && newAddress !== oldAddress) {
      // 1. 先保存旧地址和施工人员到缓存（如果旧地址存在）
      if (oldAddress) {
        addressConstructorMap.value.set(oldAddress, [...formData.constructors])
        localStorage.setItem('addressConstructorMap', JSON.stringify(Array.from(addressConstructorMap.value.entries())))
      }
      
      // 2. 检查缓存中是否有新地址对应的施工人员
      if (addressConstructorMap.value.has(newAddress)) {
        const cachedConstructors = addressConstructorMap.value.get(newAddress)
        if (cachedConstructors) {
          formData.constructors = [...cachedConstructors]
        }
      } else {
        // 新地址，清空施工人员
        formData.constructors = []
      }
      
      // 3. 立即保存新地址和施工人员到缓存
      addressConstructorMap.value.set(newAddress, [...formData.constructors])
      localStorage.setItem('addressConstructorMap', JSON.stringify(Array.from(addressConstructorMap.value.entries())))
    }
  }
)

// 监听施工人员勾选变化，立即保存到当前地址
watch(
  () => formData.constructors,
  (newConstructors) => {
    // 只有当地址存在时才保存施工人员信息到当前地址
    if (formData.name) {
      addressConstructorMap.value.set(formData.name, [...newConstructors])
      localStorage.setItem('addressConstructorMap', JSON.stringify(Array.from(addressConstructorMap.value.entries())))
    }
  },
  { deep: true }
)

const saveFormDataDebounced = debounce((data: FormData) => {
  localStorage.setItem('formData', JSON.stringify(data))
}, 800)

const saveAllData = () => {
  localStorage.setItem('formData', JSON.stringify(formData))
  localStorage.setItem('addressConstructorMap', JSON.stringify(Array.from(addressConstructorMap.value.entries())))
}

// 监听表单数据变化，使用防抖保存到localStorage
watch(
  formData,
  (newFormData) => {
    saveFormDataDebounced(newFormData)
  },
  { deep: true }
)

onMounted(async () => {
  window.addEventListener('beforeunload', saveAllData)
  
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
  
  // 从缓存中加载地址与施工人员的映射
  try {
    const cachedMap = localStorage.getItem('addressConstructorMap')
    if (cachedMap) {
      addressConstructorMap.value = new Map(JSON.parse(cachedMap))
    }
  } catch (error) {
    console.error('加载地址与施工人员映射失败:', error)
  }
  
  // 从缓存中加载完整的表单数据
  try {
    const cachedFormData = localStorage.getItem('formData')
    if (cachedFormData) {
      const parsedFormData = JSON.parse(cachedFormData)
      // 恢复表单数据
      Object.assign(formData, parsedFormData)
    }
  } catch (error) {
    // 加载表单数据失败时静默处理
  }
  
  loadDictionaryData(true)
  loadProjects()
  authStore.fetchUnreadCount()
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', saveAllData)
  saveAllData()
  if (pageAbortController.value) {
    pageAbortController.value.abort()
  }
  if (uploadAbortController.value) {
    uploadAbortController.value.abort()
  }
})
</script>

<style scoped>
.dashboard {
  min-height: 100vh;
  background: linear-gradient(180deg, #f9fef5 0%, #f9fef5 100%);
  padding-bottom: 100px;
}

.dashboard :deep(.van-nav-bar__title) {
  font-size: 16px;
}

.dashboard :deep(.van-nav-bar) {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
}

.dashboard :deep(.van-nav-bar__title) {
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

.dashboard :deep(.van-cell-group__title) {
  font-size: 14px;
  color: #84cc16;
  font-weight: 600;
}

.content-wrapper {
  padding-top: 46px;
  padding-bottom: 0;
  width: 100%;
}

.content-wrapper :deep(.van-cell-group) {
  width: 100%;
  margin: 0;
}

.calculation-result {
  background: linear-gradient(135deg, #f9fef5 0%, #f9fef5 100%);
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #e6f4d0;
  width: 98%;
  margin: 0 auto;
}

.result-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.result-item:last-child {
  margin-bottom: 0;
}

.result-item .label {
  width: 80px;
  color: #333842;
  font-size: 14px;
}

.result-item .value {
  font-weight: 600;
  color: #333842;
  font-size: 14px;
}

.result-item .value.total {
  color: #84cc16;
  font-size: 16px;
}

.result-item .value.formula {
  font-family: 'Courier New', monospace;
  font-size: 14px;
  color: #000000;
  padding: 4px 8px;
  border-radius: 4px;
  word-break: break-all;
}

.result-item .unit {
  margin-left: 5px;
  color: #64748b;
  font-size: 12px;
}

.remark-field {
  margin-top: 10px;
}

.remark-field :deep(.van-field__label) {
  display: block;
  margin-bottom: 8px;
  width: 100%;
}

.remark-field :deep(.van-field__control) {
  border: 1px solid #e6f4d0;
  border-radius: 4px;
  padding: 8px;
  min-height: 60px;
}

.distribution-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.save-button {
  width: 70%;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  margin: 0 auto;
  display: block;
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  border: none;
}

.project-amount {
  color: #84cc16;
  font-weight: 600;
}

.project-detail {
  padding: 10px 0;
}

.project-constructors {
  font-size: 14px;
  color: #333842;
  margin-bottom: 10px;
}

.constructor-scroll {
  width: 100%;
  overflow-x: auto;
  white-space: nowrap;
}

.constructor-scroll :deep(.van-checkbox-group) {
  display: flex;
  gap: 8px;
}

.constructor-scroll :deep(.van-checkbox) {
  flex-shrink: 0;
}

.constructor-scroll :deep(.van-checkbox__label) {
  margin-left: 4px;
  font-size: 13px;
}

.constructor-scroll :deep(.van-checkbox__icon) {
  font-size: 14px;
}

.footer {
  background-color: #f9fef5;
  border-top: 1px solid #e6f4d0;
  padding: 10px 10px;
  margin-top: 10px;
}

.footer-content {
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
}

.copyright {
  color: #333842;
  font-size: 12px;
  position: absolute;
  left: 30%;
  transform: translateX(-30%);
}

.api-latency {
  color: #64748b;
  font-size: 12px;
  margin-left: auto;
}

.project-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 10px 0;
}

.project-item {
  width: 100%;
  padding: 24px 10px;
  background: linear-gradient(135deg, #ffffff 0%, #f9fef5 100%);
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(132, 204, 22, 0.08);
  border: 1px solid #e6f4d0;
  transform: translateY(0);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  margin-bottom: 16px;
}

.project-header {
  margin-bottom: 8px;
  background: linear-gradient(135deg, #ecfccb 0%, #bbf7d0 100%);
  padding: 8px;
  border-radius: 12px;
  border: 1px solid #10b981;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.1);
}

.project-title {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.project-name {
  font-size: 20px;
  font-weight: 600;
  color: #333842;
  letter-spacing: 0.5px;
  flex: 1;
}

.project-amount {
  color: #84cc16;
  font-weight: 600;
  font-size: 15px;
  margin-left: 10px;
}

.project-constructors {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  padding: 0;
  border: none;
  margin-bottom: 5px;
}

.constructor-name {
  font-size: 15px;
  color: #333842;
  line-height: 1.5;
}

.project-actions {
  display: flex;
  gap: 10px;
  padding: 6px 0;
  border-top: 1px solid #f9fef5;
  border-bottom: 1px solid #f9fef5;
  margin-bottom: 10px;
}

.project-time-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #64748b;
  margin-bottom: 10px;
}

.project-time-info span {
  background: #dcfce7;
  padding: 4px 10px;
  border-radius: 12px;
  color: #84cc16;
}

.project-actions .van-button {
  flex: 1;
  background: #f5f5f5;
  border: 1px solid #e5e7eb;
  color: #333842;
}

.subproject-table-container {
  width: 100%;
  overflow-x: auto;
  margin-top: 10px;
  background: #f0fdf4;
  border-radius: 8px;
  border: 1px solid #86efac;
}

.attachments-popup {
  background-color: #f9fef5;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e6f4d0;
}

.attachments-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  border-bottom: 1px solid #e6f4d0;
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.attachments-project-name {
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachments-title {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.attachments-header .van-icon {
  font-size: 20px;
  color: #fff;
  cursor: pointer;
}

.empty-attachments {
  padding: 40px 20px;
}

.attachments-list {
  max-height: 60vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  width: 100%;
}

.attachment-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid #f9fef5;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
  background: #ffffff;
  width: 100%;
}

.attachment-item:hover {
  background-color: #f9fef5;
}

.attachment-preview {
  width: 100%;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f9fef5;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #e6f4d0;
}

.attachment-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  cursor: pointer;
}

.attachment-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.attachment-icon {
  color: #64748b;
}

.attachment-info {
  display: flex;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
  color: #64748b;
}

.preview-popup {
  background-color: transparent;
  border-radius: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  border: none;
}

.preview-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  overflow: hidden;
  background-color: transparent;
  cursor: grab;
  position: relative;
  touch-action: none;
}

.preview-content:active {
  cursor: grabbing;
}

.preview-image-container {
  transition: transform 0.1s ease-out;
  transform-origin: center center;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-close-btn {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 40px;
  height: 40px;
  background-color: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 1000;
  transition: background-color 0.2s;
}

.preview-close-btn:hover {
  background-color: rgba(0, 0, 0, 0.7);
}

.preview-image {
  max-width: 100%;
  max-height: 85vh;
  object-fit: contain;
  cursor: pointer;
}

.preview-video {
  max-width: 100%;
  max-height: 85vh;
}

.preview-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  text-align: center;
}

.preview-unsupported .van-icon {
  color: #84cc16;
  margin-bottom: 16px;
}

.preview-unsupported p {
  margin: 0 0 16px 0;
  font-size: 14px;
}

.subproject-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  min-width: 600px;
  border-top: 1px solid #000000;
  border-bottom: 1px solid #000000;
}

.subproject-table thead {
  background: linear-gradient(135deg, #f9fafb 0%, #f3f4f6 100%);
}

.subproject-table th {
  padding: 12px 8px;
  text-align: left;
  font-weight: 600;
  color: #374151;
  border-bottom: 2px solid #000000;
  border-left: none;
  border-right: none;
  white-space: nowrap;
}

.subproject-table td {
  padding: 12px 8px;
  border-bottom: 1px solid #000000;
  border-left: none;
  border-right: none;
  color: #333842;
  white-space: nowrap;
}

.subproject-table tbody tr:nth-child(odd) {
  background: #ffffff;
}

.subproject-table tbody tr:nth-child(even) {
  background: #fafafa;
}

.subproject-table tbody tr:hover {
  background: #f0fdf4;
}

.subproject-table tbody tr:last-child td {
  border-bottom: none;
}

.subproject-table .van-button {
  margin-right: 5px;
}

.subproject-table .van-button:last-child {
  margin-right: 0;
}

.dashboard :deep(.van-toast) {
  color: #fff !important;
  background-color: rgba(21, 128, 61, 0.9) !important;
}

.dashboard :deep(.van-toast--fail) {
  color: #fff !important;
  background-color: rgba(245, 34, 45, 0.9) !important;
}

.dashboard :deep(.van-toast--success) {
  color: #fff !important;
  background-color: rgba(21, 128, 61, 0.9) !important;
}

.edit-popup {
  background-color: #f0fdf4;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  border: 1px solid #86efac;
}

.edit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #86efac;
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
}

.edit-title {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.edit-header .van-icon {
  font-size: 20px;
  color: #fff;
  cursor: pointer;
}

.edit-content {
  padding: 16px;
  overflow-y: auto;
}

.edit-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.edit-actions .van-button {
  flex: 1;
}

.edit-actions .van-button--primary {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  border: none;
}

.dashboard :deep(.van-toast--loading) {
  color: #fff !important;
  background-color: rgba(12, 74, 110, 0.9) !important;
}

.dashboard :deep(.van-toast__text) {
  color: #fff !important;
}

.upload-popup {
  padding: 16px;
  background-color: #f0fdf4;
}

.upload-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.upload-title {
  font-size: 16px;
  font-weight: 600;
  color: #064e3b;
}

.upload-progress {
  margin-top: 16px;
  padding: 12px;
  background-color: #dcfce7;
  border-radius: 8px;
  border: 1px solid #86efac;
}

.progress-text {
  display: block;
  text-align: center;
  margin-top: 8px;
  font-size: 14px;
  color: #064e3b;
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
</style>
