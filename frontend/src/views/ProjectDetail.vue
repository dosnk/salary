<template>
  <div class="project-detail">
    <van-nav-bar
      title="工程详情"
      left-arrow
      fixed
      @click-left="goBack"
    >
      <template #right>
        <div class="nav-right">
          <span class="user-info">{{ currentUser?.nickname || currentUser?.username || '未登录' }}</span>
          <van-badge :content="unreadCount > 0 ? unreadCount : undefined" :show-zero="false" max="99">
            <van-icon name="envelop-o" size="20" color="#fff" @click="goToMessages" />
          </van-badge>
        </div>
      </template>
    </van-nav-bar>

    <div class="content-wrapper" v-if="projectDetail">
      <van-cell-group title="工程信息" inset>
        <van-cell title="工程名称" :value="projectDetail.name" />
        <van-cell title="总金额" :value="`¥${formatNumber(projectDetail.total_amount)}`" />
        <van-cell title="工资分配方式">
          <template #value>
            <van-tag v-if="projectDetail.salary_distribution === 'average'" type="primary">平均分配</van-tag>
            <van-tag v-else type="success">按工时分配</van-tag>
          </template>
        </van-cell>
        <van-cell v-if="projectDetail.description" title="工程备注" :value="projectDetail.description" />
        <van-cell title="状态">
          <template #value>
            <van-tag :type="getStatusType(projectDetail.status)">{{ getStatusText(projectDetail.status) }}</van-tag>
          </template>
        </van-cell>
        <van-cell class="time-cell">
          <template #value>
            <div class="time-info">
              <div class="time-item">
                <span class="time-label">创建：</span>
                <span class="time-value">{{ formatDate(projectDetail.created_at) }}</span>
              </div>
              <div class="time-item">
                <span class="time-label">更新：</span>
                <span class="time-value">{{ formatDate(projectDetail.updated_at) }}</span>
              </div>
            </div>
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group title="施工人员" inset>
        <van-cell v-if="projectDetail.constructors.length === 0" title="暂无施工人员" />
        <van-cell v-else>
          <template #value>
            <div class="constructors-list">
              <van-tag
                v-for="constructor in projectDetail.constructors"
                :key="constructor.id"
                type="primary"
                class="constructor-tag"
              >
                {{ constructor.nickname || constructor.username }}
                <span v-if="projectDetail.salary_distribution === 'work_days'" class="workdays-badge">
                  {{ constructor.workdays || 1 }}工日
                </span>
              </van-tag>
            </div>
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group title="操作" inset>
        <van-cell>
          <template #value>
            <div class="action-buttons">
              <van-button type="primary" size="small" @click="handleEditProject">编辑工程</van-button>
            </div>
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group title="子项目列表" inset>
        <van-cell v-if="projectDetail.sub_projects.length === 0" title="暂无子项目" />
        <template v-else>
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
                  v-for="(sub, index) in projectDetail.sub_projects"
                  :key="sub.id"
                >
                  <td>{{ index + 1 }}</td>
                  <td>{{ sub.space_type_name || sub.space_type }}</td>
                  <td>{{ sub.construction_plan_name || sub.construction_scheme }}</td>
                  <td>{{ formatNumber(sub.length) }} × {{ formatNumber(sub.width) }}</td>
                  <td>{{ formatNumber(sub.quantity) }} {{ getUnitName(sub.unit || '') }}</td>
                  <td>¥{{ formatNumber(sub.amount) }}</td>
                  <td>
                    <van-button type="primary" size="mini" @click="handleEditSubProject(sub)">
                      编辑
                    </van-button>
                    <van-button type="warning" size="mini" disabled @click="showDevToast">
                      转交
                    </van-button>
                    <van-button type="default" size="mini" disabled @click="showDevToast">
                      历史
                    </van-button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </van-cell-group>

      <van-cell-group title="附件管理" inset>
        <van-cell>
          <template #value>
            <div class="attachment-buttons">
              <van-button type="primary" size="small" @click="handleViewAttachments">
                查看附件 ({{ projectDetail.files.length }})
              </van-button>
            </div>
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group title="修改历史" inset>
        <van-cell v-if="historyList.length === 0" title="暂无修改历史" />
        <template v-else>
          <div class="history-list" :class="{ 'history-collapsed': historyCollapsed }">
            <div
              v-for="(history, index) in historyList"
              :key="index"
              class="history-item"
            >
              <div class="history-header">
                <span class="history-action">{{ history.action_name || history.action }}</span>
                <span class="history-time">{{ history.created_at }}</span>
              </div>
              <div class="history-user">操作人：{{ history.nickname || history.username }}</div>
              <div v-if="history.description" class="history-desc">{{ history.description }}</div>
            </div>
          </div>
          <div v-if="historyList.length > 3" class="history-toggle" @click="historyCollapsed = !historyCollapsed">
            {{ historyCollapsed ? `展开全部 (${historyList.length}条)` : '收起' }}
            <van-icon :name="historyCollapsed ? 'arrow-down' : 'arrow-up'" />
          </div>
        </template>
      </van-cell-group>
    </div>

    <van-loading v-else type="spinner" size="24px" vertical>加载中...</van-loading>

    <!-- 子项目编辑弹窗 -->
    <van-popup v-model:show="showSubProjectEdit" position="center" :style="{ width: '90%', maxWidth: '600px', maxHeight: '90vh', overflow: 'auto' }">
      <div class="edit-popup">
        <div class="edit-header">
          <span class="edit-title">编辑子项目</span>
          <van-icon name="cross" @click="showSubProjectEdit = false" />
        </div>
        
        <div class="edit-content">
          <van-field
            v-model="subProjectForm.spaceType"
            readonly
            clickable
            label="空间类型"
            placeholder="请选择空间类型"
            @click="showSpaceTypePicker = true"
          />
          
          <van-field
            v-model="subProjectForm.constructionScheme"
            readonly
            clickable
            label="施工方案"
            placeholder="请选择施工方案"
            @click="showSchemePicker = true"
          />
          
          <van-field
            v-model.number="subProjectForm.length"
            type="number"
            label="长度(cm)"
            placeholder="请输入长度"
            @input="updateEditUnitPrice"
          />
          
          <van-field
            v-model.number="subProjectForm.width"
            type="number"
            label="宽度(cm)"
            placeholder="请输入宽度"
            @input="updateEditUnitPrice"
          />
          
          <van-field
            v-model.number="subProjectForm.unitPrice"
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
            v-model="subProjectForm.remark"
            type="textarea"
            label="备注"
            placeholder="请输入备注"
            rows="3"
          />
          
          <div class="edit-actions">
            <van-button @click="showSubProjectEdit = false">取消</van-button>
            <van-button type="primary" @click="handleSubProjectSubmit" :loading="loading">保存</van-button>
          </div>
        </div>
      </div>
    </van-popup>

    <!-- 空间类型选择器 -->
    <van-popup v-model:show="showSpaceTypePicker" position="bottom">
      <van-picker
        :columns="spaceTypeOptions"
        @confirm="onSpaceTypeConfirm"
        @cancel="showSpaceTypePicker = false"
      />
    </van-popup>

    <!-- 施工方案选择器 -->
    <van-popup v-model:show="showSchemePicker" position="bottom">
      <van-picker
        :columns="schemeOptions"
        @confirm="onSchemeConfirm"
        @cancel="showSchemePicker = false"
      />
    </van-popup>

    <FileListPopup v-model:show="showAttachments" :project-name="projectDetail?.name || ''" :files="projectDetail?.files || []" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { projectsApi, type ProjectDetail, type SubProject } from '@/api/projects'
import dictionaryApi from '@/api/dictionary'
import { useAuthStore } from '@/stores/auth'
import { baseURL } from '@/utils/request'
import dayjs from 'dayjs'
import FileListPopup from '@/components/FileListPopup.vue'
import { TOAST_MESSAGES } from '@/constants'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const currentUser = computed(() => authStore.userInfo)
const unreadCount = computed(() => authStore.unreadCount)

const projectId = ref<number>(Number(route.params.id))
const projectDetail = ref<ProjectDetail | null>(null)
const historyList = ref<any[]>([])
const loading = ref(false)

const showSubProjectEdit = ref(false)
const showSpaceTypePicker = ref(false)
const showSchemePicker = ref(false)
const showAttachments = ref(false)
const historyCollapsed = ref(true)

const subProjectForm = reactive({
  id: 0,
  spaceType: '',
  constructionScheme: '',
  length: 0,
  width: 0,
  unitPrice: 0,
  remark: ''
})

const spaceTypeOptions = ref<Array<{ text: string; value: string }>>([])
const schemeOptions = ref<Array<{ text: string; value: string }>>([])

const spaceTypeMap = ref<Map<string, any>>(new Map())
const schemeMap = ref<Map<string, any>>(new Map())

const formatNumber = (num: number | string) => {
  if (!num) return '0.00'
  const numberValue = typeof num === 'string' ? parseFloat(num) : num
  if (isNaN(numberValue)) return '0.00'
  return numberValue.toFixed(2)
}

const formatDate = (date: string) => {
  return dayjs(date).format('YYYY-MM-DD')
}

const getUnitName = (unit: string) => {
  const unitMap: Record<string, string> = {
    length: '米',
    perimeter: '米',
    area: '㎡'
  }
  return unitMap[unit] || unit
}

const editArea = computed(() => {
  return (subProjectForm.length * subProjectForm.width) / 10000
})

const editPerimeter = computed(() => {
  return (subProjectForm.length + subProjectForm.width) * 2 / 100
})

const editLengthValue = computed(() => {
  return subProjectForm.length / 100
})

const editCurrentScheme = computed(() => {
  if (!subProjectForm.constructionScheme) return null
  return schemeMap.value.get(subProjectForm.constructionScheme)
})

const editCalculationFormula = computed(() => {
  const scheme = editCurrentScheme.value
  if (!scheme) return `${formatNumber(editArea.value)} m² × ¥${formatNumber(subProjectForm.unitPrice)}/m² = ¥${formatNumber(editArea.value * subProjectForm.unitPrice)}`
  
  let formula = ''
  switch (scheme.unit) {
    case 'area':
      formula = `${formatNumber(editArea.value)} m² × ¥${formatNumber(subProjectForm.unitPrice)}/m² = ¥${formatNumber(editArea.value * subProjectForm.unitPrice)}`
      break
    case 'perimeter':
      formula = `${formatNumber(editPerimeter.value)} m × ¥${formatNumber(subProjectForm.unitPrice)}/m = ¥${formatNumber(editPerimeter.value * subProjectForm.unitPrice)}`
      break
    case 'length':
      formula = `${formatNumber(editLengthValue.value)} m × ¥${formatNumber(subProjectForm.unitPrice)}/m = ¥${formatNumber(editLengthValue.value * subProjectForm.unitPrice)}`
      break
    default:
      formula = `${formatNumber(editArea.value)} m² × ¥${formatNumber(subProjectForm.unitPrice)}/m² = ¥${formatNumber(editArea.value * subProjectForm.unitPrice)}`
  }
  return formula
})

const updateEditUnitPrice = () => {
  const scheme = schemeMap.value.get(subProjectForm.constructionScheme)
  if (scheme) {
    subProjectForm.unitPrice = scheme.price
  }
}

const getStatusText = (status: string) => {
  const statusMap: Record<string, string> = {
    'preparing': '备料中',
    'constructing': '施工中',
    'completed': '已完工',
    'canceled': '已取消'
  }
  return statusMap[status] || status
}

const getStatusType = (status: string) => {
  const typeMap: Record<string, 'default' | 'primary' | 'success' | 'danger' | 'warning'> = {
    'preparing': 'warning',
    'constructing': 'primary',
    'completed': 'success',
    'canceled': 'danger'
  }
  return typeMap[status] || 'default'
}

const goBack = () => {
  router.back()
}

const goToMessages = () => {
  router.push('/message')
}

const fetchProjectDetail = async () => {
  loading.value = true
  try {
    const data = await projectsApi.getProject(projectId.value)
    projectDetail.value = data
    
    if (data.files) {
      projectDetail.value.files = data.files.map((file: any) => ({
        ...file,
        url: file.path.startsWith('http') ? file.path : `${baseURL}${file.path}`
      }))
    }
    
    fetchHistory()
  } catch (error: any) {
    console.error('获取工程详情失败:', error)
    if (error?.message?.includes('工程不存在')) {
      showToast({ type: 'fail', message: '工程已被删除' })
      setTimeout(() => {
        router.push('/project')
      }, 1500)
    } else {
      showToast({ type: 'fail', message: '获取工程详情失败' })
    }
  } finally {
    loading.value = false
  }
}

const fetchHistory = async () => {
  try {
    const data = await projectsApi.getProjectHistory(projectId.value)
    historyList.value = data
  } catch (error) {
    console.error('获取修改历史失败:', error)
  }
}

const handleEditSubProject = (sub: SubProject) => {
  subProjectForm.id = sub.id
  subProjectForm.spaceType = sub.space_type_name || sub.space_type || ''
  subProjectForm.constructionScheme = sub.construction_plan_name || sub.construction_scheme || ''
  subProjectForm.length = Math.round(sub.length * 100)
  subProjectForm.width = Math.round(sub.width * 100)
  subProjectForm.remark = sub.remark || ''
  
  const scheme = schemeMap.value.get(sub.construction_plan_name || sub.construction_scheme || '')
  if (scheme) {
    subProjectForm.unitPrice = scheme.price
  }
  
  showSubProjectEdit.value = true
}

const showDevToast = () => {
  showToast({
    message: TOAST_MESSAGES.FEATURE_DEVELOPING,
    position: 'bottom'
  })
}

const handleSubProjectSubmit = async () => {
  try {
    await projectsApi.updateSubProject(projectId.value, subProjectForm.id, {
      spaceType: subProjectForm.spaceType,
      constructionScheme: subProjectForm.constructionScheme,
      length: subProjectForm.length,
      width: subProjectForm.width,
      remark: subProjectForm.remark
    })
    showToast({ type: 'success', message: '子项目更新成功' })
    showSubProjectEdit.value = false
    fetchProjectDetail()
  } catch (error) {
    console.error('更新子项目失败:', error)
    const errorMsg = (error as any)?.message || '更新失败'
    showToast({ type: 'fail', message: errorMsg })
  }
}

const handleViewAttachments = () => {
  showAttachments.value = true
}

const handleEditProject = () => {
  router.push(`/project/edit/${projectId.value}`)
}

const onSpaceTypeConfirm = ({ selectedOptions }: any) => {
  subProjectForm.spaceType = selectedOptions[0].text
  showSpaceTypePicker.value = false
}

const onSchemeConfirm = ({ selectedOptions }: any) => {
  subProjectForm.constructionScheme = selectedOptions[0].text
  updateEditUnitPrice()
  showSchemePicker.value = false
}

onMounted(async () => {
  loadDictionary()
  fetchProjectDetail()
})

const loadDictionary = async () => {
  try {
    const dictionaryData = await dictionaryApi.getAllDictionary()
    spaceTypeOptions.value = dictionaryData.spaceTypes.map((type: any) => ({
      text: type.name,
      value: type.name
    }))
    schemeOptions.value = dictionaryData.constructionPlans.map((plan: any) => ({
      text: plan.name,
      value: plan.name
    }))
    spaceTypeMap.value = new Map(dictionaryData.spaceTypes.map((type: any) => [type.name, type]))
    schemeMap.value = new Map(dictionaryData.constructionPlans.map((plan: any) => [plan.name, plan]))
  } catch (error) {
    console.error('加载字典数据失败:', error)
  }
}
</script>

<style scoped>
.project-detail {
  min-height: 100vh;
  background: linear-gradient(180deg, #f9fef5 0%, #f9fef5 100%);
  padding-top: 46px;
}

.project-detail :deep(.van-nav-bar) {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
}

.project-detail :deep(.van-nav-bar__title) {
  color: #fff;
}

.project-detail :deep(.van-nav-bar__arrow) {
  color: #fff;
}

.content-wrapper {
  padding-bottom: 20px;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  color: #fff;
  font-size: 14px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.action-buttons .van-button {
  flex: 1;
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  border: none;
  color: #fff;
}

.time-info {
  display: flex;
  flex-direction: row;
  gap: 20px;
  justify-content: space-between;
}

.time-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.time-label {
  font-size: 12px;
  color: #86909c;
  min-width: 40px;
  flex-shrink: 0;
}

.time-value {
  font-size: 12px;
  color: #1d2129;
}

.time-cell {
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
}

.time-cell :deep(.van-cell__value) {
  background: rgba(255, 255, 255, 0.6);
  border-radius: 4px;
  padding: 6px 10px;
}

.constructors-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.constructor-tag {
  margin-right: 0;
}

.workdays-badge {
  margin-left: 4px;
  opacity: 0.85;
}

.attachment-buttons {
  display: flex;
  justify-content: space-between;
  width: 100%;
}

.subproject-table-container {
  width: 100%;
  overflow-x: auto;
  margin-top: 10px;
  background: #ffffff;
  border-radius: 8px;
  border-top: 1px solid #000000;
  border-bottom: 1px solid #000000;
}

.subproject-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  min-width: 600px;
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
  white-space: nowrap;
}

.subproject-table td {
  padding: 12px 8px;
  border-bottom: 1px solid #000000;
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
  background: #f0f9ff;
}

.subproject-table tbody tr:last-child td {
  border-bottom: none;
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
  padding: 16px;
  border-bottom: 1px solid #e6f4d0;
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.project-name {
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

.attachment-meta {
  display: flex;
  gap: 8px;
}

.preview-popup {
  background-color: #f9fef5;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  border: 1px solid #e6f4d0;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e6f4d0;
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
}

.preview-title {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
}

.preview-header .van-icon {
  font-size: 20px;
  color: #fff;
  cursor: pointer;
}

.preview-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  overflow: auto;
  background-color: #333842;
}

.preview-image {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.preview-video {
  max-width: 100%;
  max-height: 70vh;
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

.history-description {
  font-size: 12px;
  color: #969799;
  line-height: 1.5;
}

.history-list {
  padding: 0 12px;
}

.history-collapsed .history-item:nth-child(n+3) {
  display: none;
}

.history-item {
  background: #fafafa;
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 8px;
  border-left: 3px solid #84cc16;
}

.history-header{
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.history-action{
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.history-time{
  font-size: 11px;
  color: #999;
}

.history-user{
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
}

.history-desc{
  font-size: 12px;
  color: #84cc16;
  background: #f0f9eb;
  padding: 6px 8px;
  border-radius: 4px;
  margin-top: 4px;
  line-height: 1.4;
}

.history-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px;
  color: #84cc16;
  font-size: 13px;
  cursor: pointer;
}

.copyright-info {
  color: #fff;
  font-size: 16px;
  font-weight: bold;
}

.status-popup :deep(.van-nav-bar) {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
}

.status-popup :deep(.van-nav-bar__title) {
  color: #fff;
}

.status-popup :deep(.van-nav-bar__text) {
  color: #fff;
}

.status-popup :deep(.van-picker__confirm) {
  color: #84cc16;
}

.status-popup :deep(.van-picker__cancel) {
  color: #64748b;
}

.popup-buttons {
  display: flex;
  gap: 10px;
  padding: 16px;
}

.popup-buttons .van-button {
  flex: 1;
}

.popup-buttons .van-button--default {
  background: #f5f5f5;
  color: #333;
  border: 1px solid #ddd;
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
  color: #333842;
  font-size: 13px;
  word-break: break-all;
}
</style>
