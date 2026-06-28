<template>
  <div class="migration-container">
    <div class="migration-header">
      <h1>数据迁移工具</h1>
      <p>从旧SQLite数据库迁移数据到新系统</p>
    </div>

    <div v-if="!isLoggedIn" class="login-section">
      <div class="login-card">
        <h2>登录新系统</h2>
        <form @submit.prevent="handleLogin">
          <div class="form-group">
            <label>用户名</label>
            <input
              v-model="loginForm.username"
              type="text"
              placeholder="请输入用户名"
              autocomplete="username"
              required
            />
          </div>
          <div class="form-group">
            <label>密码</label>
            <input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              required
            />
          </div>
          <button type="submit" :disabled="loginLoading" class="login-btn">
            {{ loginLoading ? '登录中...' : '登录' }}
          </button>
        </form>
        <p v-if="loginError" class="error-message">{{ loginError }}</p>
      </div>
    </div>

    <div v-else class="migration-content">
      <div class="user-info">
        <span>当前用户: {{ userInfo?.nickname || userInfo?.username }}</span>
        <button @click="handleLogout" class="logout-btn">退出登录</button>
      </div>

      <div class="migration-sections">
        <div class="projects-section">
          <div class="section-header">
            <button @click="checkDatabase" class="check-btn">检查数据库</button>
            <h2>旧数据库工程列表</h2>
            <button @click="loadProjects" class="refresh-btn">刷新</button>
          </div>
          
          <div class="selection-actions">
            <van-checkbox v-model="selectAll" @change="handleSelectAll">全选</van-checkbox>
            <span class="selected-count">已选: {{ selectedProjectIds.length }} 个</span>
            <button 
              @click="migrateSelectedProjects" 
              :disabled="migrating || selectedProjectIds.length === 0"
              class="migrate-selected-btn"
            >
              {{ migrating ? '迁移中...' : '迁移选中' }}
            </button>
          </div>
          
          <div v-if="projectsLoading" class="loading">加载中...</div>
          
          <div v-else-if="projects.length === 0" class="empty">
            暂无工程数据
          </div>
          
          <div v-else class="projects-list">
            <van-checkbox-group v-model="selectedProjectIds">
              <div
                v-for="project in projects"
                :key="project.id"
                class="project-item"
                :class="{ 
                  selected: selectedProjectIds.includes(project.id),
                  migrated: migratedProjectIds.includes(project.id)
                }"
              >
                <div class="project-checkbox">
                  <van-checkbox :name="project.id" :disabled="migratedProjectIds.includes(project.id)" />
                </div>
                <div class="project-info" @click="selectProject(project)">
                  <h3>
                    {{ project.name }}
                    <van-tag v-if="migratedProjectIds.includes(project.id)" type="success">已迁移</van-tag>
                  </h3>
                  <p>子项目: {{ project.item_count }} 个</p>
                  <p>创建时间: {{ formatDate(project.created_at) }}</p>
                </div>
                <div class="project-actions">
                  <button @click="viewProjectDetail(project.id)" class="view-btn">
                    详情
                  </button>
                  <button
                    @click="migrateSingleProject(project.id)"
                    :disabled="migrating || migratedProjectIds.includes(project.id)"
                    class="migrate-btn"
                  >
                    {{ migratedProjectIds.includes(project.id) ? '已迁移' : '迁移' }}
                  </button>
                </div>
              </div>
            </van-checkbox-group>
          </div>
        </div>

        <div class="detail-section">
          <div v-if="!selectedProject" class="no-selection">
            <p>请选择一个工程查看详情</p>
          </div>
          
          <div v-else class="project-detail">
            <div class="detail-header">
              <h2>{{ selectedProject.name }}</h2>
              <button @click="closeDetail" class="close-btn">关闭</button>
            </div>
            
            <div v-if="detailLoading" class="loading">加载详情中...</div>
            
            <div v-else-if="projectDetail" class="detail-content">
              <div class="detail-section-item">
                <h3>基本信息</h3>
                <div class="info-grid">
                  <div class="info-item">
                    <label>总金额:</label>
                    <span>¥{{ projectDetail.project.total_amount }}</span>
                  </div>
                  <div class="info-item">
                    <label>分配方式:</label>
                    <span>{{ projectDetail.project.distribution_method }}</span>
                  </div>
                  <div class="info-item">
                    <label>创建者ID:</label>
                    <span>{{ projectDetail.project.created_by }}</span>
                  </div>
                  <div class="info-item">
                    <label>创建时间:</label>
                    <span>{{ formatDate(projectDetail.project.created_at) }}</span>
                  </div>
                </div>
              </div>

              <div class="detail-section-item">
                <h3>子项目 ({{ projectDetail.projectItems.length }})</h3>
                <div class="subprojects-list">
                  <div
                    v-for="item in projectDetail.projectItems"
                    :key="item.id"
                    class="subproject-item"
                  >
                    <h4>{{ item.space_type }} - {{ item.construction_plan }}</h4>
                    <p>长度: {{ item.length }} cm | 宽度: {{ item.width }} cm</p>
                    <p>金额: ¥{{ item.amount }}</p>
                    <p v-if="item.note">备注: {{ item.note }}</p>
                  </div>
                </div>
              </div>

              <div class="detail-section-item">
                <h3>附件 ({{ projectDetail.attachments.length }})</h3>
                <div class="attachments-list">
                  <div
                    v-for="attachment in projectDetail.attachments"
                    :key="attachment.id"
                    class="attachment-item"
                  >
                    <span>{{ attachment.name }}</span>
                    <span class="file-type">{{ attachment.type }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="control-section">
          <div class="section-header">
            <h2>迁移控制</h2>
          </div>
          
          <div class="progress-info">
            <div class="progress-stats">
              <div class="stat-item">
                <label>总数:</label>
                <span>{{ progress.total }}</span>
              </div>
              <div class="stat-item success">
                <label>成功:</label>
                <span>{{ progress.completed }}</span>
              </div>
              <div class="stat-item error">
                <label>失败:</label>
                <span>{{ progress.failed }}</span>
              </div>
            </div>
            
            <div v-if="progress.currentProject" class="current-project">
              <label>当前迁移:</label>
              <span>{{ progress.currentProject }}</span>
            </div>
            
            <div v-if="progress.status" class="status-info">
              <van-tag :type="getStatusType(progress.status)" size="medium">
                {{ getStatusText(progress.status) }}
              </van-tag>
            </div>
          </div>

          <div class="progress-bar">
            <div
              class="progress-fill"
              :style="{ width: progressPercentage + '%' }"
            ></div>
            <span class="progress-text">{{ progressPercentage }}%</span>
          </div>

          <div class="migration-actions">
            <button
              @click="migrateAllProjects"
              :disabled="migrating || progress.total === 0"
              class="migrate-all-btn"
            >
              {{ migrating ? '迁移中...' : '批量迁移所有工程' }}
            </button>
          </div>

          <div class="logs-section">
            <h3>迁移日志</h3>
            <div class="logs-container">
              <div
                v-for="(log, index) in progress.logs"
                :key="index"
                class="log-item"
                :class="log.type"
              >
                <span class="log-time">{{ log.timestamp }}</span>
                <span class="log-message">{{ log.message }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <van-dialog v-model:show="showResultDialog" :title="resultTitle" :message="resultMessage" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { showToast, showSuccessToast, showFailToast } from 'vant'
import { migrationApi, type OldProject, type OldProjectDetail, type MigrationProgress, type MigrationLoginResponse } from '@/api/migration'

const isLoggedIn = ref(false)
const userInfo = ref<MigrationLoginResponse['user'] | null>(null)
const token = ref('')

const loginForm = ref({
  username: '',
  password: ''
})
const loginLoading = ref(false)
const loginError = ref('')

const projects = ref<OldProject[]>([])
const projectsLoading = ref(false)
const selectedProject = ref<OldProject | null>(null)
const projectDetail = ref<OldProjectDetail | null>(null)
const detailLoading = ref(false)

const migrating = ref(false)
const progress = ref<MigrationProgress>({
  total: 0,
  completed: 0,
  failed: 0,
  currentProject: null,
  status: 'idle',
  logs: []
})

const selectedProjectIds = ref<number[]>([])
const migratedProjectIds = ref<number[]>([])
const selectAll = ref(false)

const showResultDialog = ref(false)
const resultTitle = ref('')
const resultMessage = ref('')

let progressInterval: number | null = null

const progressPercentage = computed(() => {
  if (progress.value.total === 0) return 0
  return Math.round(((progress.value.completed + progress.value.failed) / progress.value.total) * 100)
})

const formatDate = (dateString: string) => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleString('zh-CN')
}

const getStatusType = (status: string) => {
  switch (status) {
    case 'idle': return 'default'
    case 'running': return 'primary'
    case 'completed': return 'success'
    case 'error': return 'danger'
    default: return 'default'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'idle': return '空闲'
    case 'running': return '迁移中'
    case 'completed': return '已完成'
    case 'error': return '出错'
    default: return status
  }
}

const handleSelectAll = (val: boolean) => {
  if (val) {
    selectedProjectIds.value = projects.value
      .filter(p => !migratedProjectIds.value.includes(p.id))
      .map(p => p.id)
  } else {
    selectedProjectIds.value = []
  }
}

const handleLogin = async () => {
  loginLoading.value = true
  loginError.value = ''
  
  try {
    const response = await migrationApi.login(loginForm.value.username, loginForm.value.password)
    if (response) {
      token.value = response.token
      userInfo.value = response.user
      isLoggedIn.value = true
      await loadProjects()
      startProgressMonitoring()
    }
  } catch (error: any) {
    loginError.value = error?.message || '登录失败，请检查网络连接'
  } finally {
    loginLoading.value = false
  }
}

const handleLogout = () => {
  isLoggedIn.value = false
  userInfo.value = null
  token.value = ''
  projects.value = []
  selectedProject.value = null
  projectDetail.value = null
  selectedProjectIds.value = []
  migratedProjectIds.value = []
  stopProgressMonitoring()
}

const loadProjects = async () => {
  projectsLoading.value = true
  
  try {
    const response = await migrationApi.getProjects()
    if (response) {
      projects.value = response
    }
  } catch (error: any) {
    showToast(error?.message || '加载工程列表失败')
  } finally {
    projectsLoading.value = false
  }
}

const selectProject = (project: OldProject) => {
  selectedProject.value = project
  projectDetail.value = null
}

const viewProjectDetail = async (projectId: number) => {
  detailLoading.value = true
  
  try {
    const response = await migrationApi.getProjectById(projectId)
    if (response) {
      projectDetail.value = response
    }
  } catch (error: any) {
    showToast(error?.message || '加载工程详情失败')
  } finally {
    detailLoading.value = false
  }
}

const closeDetail = () => {
  projectDetail.value = null
}

const migrateSingleProject = async (projectId: number) => {
  if (migratedProjectIds.value.includes(projectId)) {
    showToast('该工程已迁移')
    return
  }
  
  migrating.value = true
  const project = projects.value.find(p => p.id === projectId)
  
  try {
    const response = await migrationApi.migrateProject(projectId, token.value)
    if (response?.success) {
      migratedProjectIds.value.push(projectId)
      selectedProjectIds.value = selectedProjectIds.value.filter(id => id !== projectId)
      showSuccessToast(`工程「${project?.name || projectId}」迁移成功`)
      await loadProgress()
    } else {
      showFailToast(`工程「${project?.name || projectId}」迁移失败`)
    }
  } catch (error: any) {
    showFailToast(error?.message || '迁移失败')
  } finally {
    migrating.value = false
  }
}

const migrateSelectedProjects = async () => {
  if (selectedProjectIds.value.length === 0) {
    showToast('请先选择要迁移的工程')
    return
  }
  
  migrating.value = true
  let successCount = 0
  let failCount = 0
  
  for (const projectId of selectedProjectIds.value) {
    if (migratedProjectIds.value.includes(projectId)) continue
    
    try {
      const response = await migrationApi.migrateProject(projectId, token.value)
      if (response?.success) {
        migratedProjectIds.value.push(projectId)
        successCount++
      } else {
        failCount++
      }
    } catch {
      failCount++
    }
  }
  
  selectedProjectIds.value = []
  selectAll.value = false
  await loadProgress()
  
  if (failCount === 0) {
    showSuccessToast(`成功迁移 ${successCount} 个工程`)
  } else if (successCount === 0) {
    showFailToast(`迁移失败 ${failCount} 个工程`)
  } else {
    showToast(`成功 ${successCount} 个，失败 ${failCount} 个`)
  }
  
  migrating.value = false
}

const migrateAllProjects = async () => {
  migrating.value = true
  
  try {
    const response = await migrationApi.migrateAll(token.value)
    if (response) {
      await loadProgress()
      if (response.failed === 0) {
        showSuccessToast(`全部迁移完成，成功 ${response.completed} 个`)
      } else {
        showToast(`迁移完成：成功 ${response.completed} 个，失败 ${response.failed} 个`)
      }
      projects.value.forEach(p => {
        if (!migratedProjectIds.value.includes(p.id)) {
          migratedProjectIds.value.push(p.id)
        }
      })
    }
  } catch (error: any) {
    showFailToast(error?.message || '批量迁移失败')
  } finally {
    migrating.value = false
  }
}

const loadProgress = async () => {
  try {
    const response = await migrationApi.getProgress()
    if (response) {
      progress.value = response
    }
  } catch (error: any) {
    console.error('加载进度失败:', error)
  }
}

const startProgressMonitoring = () => {
  loadProgress()
  progressInterval = window.setInterval(() => {
    loadProgress()
  }, 2000)
}

const stopProgressMonitoring = () => {
  if (progressInterval) {
    clearInterval(progressInterval)
    progressInterval = null
  }
}

const checkDatabase = async () => {
  try {
    const response = await migrationApi.checkDatabase(token.value)
    if (response) {
      resultTitle.value = '数据库检查结果'
      resultMessage.value = `数据库路径: ${response.databasePath}\n表数量: ${Object.keys(response.tables).length}\n工程数量: ${response.tables.projects?.recordCount || 0}\n子项目数量: ${response.tables.project_items?.recordCount || 0}`
      showResultDialog.value = true
    }
  } catch (error: any) {
    showFailToast(error?.message || '检查数据库失败')
  }
}
</script>

<style scoped>
.migration-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 16px;
}

.migration-header {
  text-align: center;
  margin-bottom: 24px;
}

.migration-header h1 {
  font-size: 24px;
  color: #333;
  margin-bottom: 8px;
}

.migration-header p {
  color: #666;
  font-size: 14px;
}

.login-section {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
}

.login-card {
  background: white;
  padding: 32px 24px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 360px;
}

.login-card h2 {
  text-align: center;
  margin-bottom: 24px;
  color: #333;
  font-size: 20px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  color: #333;
  font-weight: 500;
  font-size: 14px;
}

.form-group input {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  box-sizing: border-box;
}

.form-group input:focus {
  outline: none;
  border-color: #1989fa;
}

.login-btn {
  width: 100%;
  padding: 12px;
  background: #1989fa;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  cursor: pointer;
  transition: background 0.3s;
}

.login-btn:active {
  background: #0c7cd5;
}

.login-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.error-message {
  color: #ee0a24;
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
}

.user-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: white;
  border-radius: 8px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  font-size: 14px;
}

.logout-btn {
  padding: 6px 16px;
  background: #ee0a24;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.migration-sections {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 8px;
}

.section-header h2 {
  font-size: 16px;
  color: #333;
  flex: 1;
}

.check-btn, .refresh-btn {
  padding: 6px 12px;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
}

.check-btn {
  background: #07c160;
}

.refresh-btn {
  background: #1989fa;
}

.selection-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: #f7f8fa;
  border-radius: 8px;
  margin-bottom: 12px;
}

.selected-count {
  font-size: 13px;
  color: #666;
}

.migrate-selected-btn {
  margin-left: auto;
  padding: 6px 16px;
  background: #ff976a;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.migrate-selected-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.loading, .empty {
  text-align: center;
  padding: 32px;
  color: #999;
  font-size: 14px;
}

.projects-list {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.project-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s;
}

.project-item:last-child {
  border-bottom: none;
}

.project-item.selected {
  background: #e8f4ff;
}

.project-item.migrated {
  background: #f0fff4;
}

.project-checkbox {
  margin-right: 12px;
}

.project-info {
  flex: 1;
  min-width: 0;
}

.project-info h3 {
  font-size: 15px;
  color: #333;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-info p {
  font-size: 12px;
  color: #999;
  margin: 2px 0;
}

.project-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.view-btn, .migrate-btn {
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}

.view-btn {
  background: #f5f5f5;
  color: #666;
}

.migrate-btn {
  background: #07c160;
  color: white;
}

.migrate-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.no-selection {
  background: white;
  border-radius: 12px;
  padding: 32px;
  text-align: center;
  color: #999;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.project-detail {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-header h2 {
  font-size: 16px;
  color: #333;
}

.close-btn {
  padding: 6px 16px;
  background: #ee0a24;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.detail-content {
  padding: 16px;
  max-height: 400px;
  overflow-y: auto;
}

.detail-section-item {
  margin-bottom: 20px;
}

.detail-section-item h3 {
  font-size: 14px;
  color: #333;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid #1989fa;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.info-item {
  display: flex;
  align-items: center;
  font-size: 13px;
}

.info-item label {
  color: #999;
  margin-right: 8px;
}

.info-item span {
  color: #333;
}

.subprojects-list, .attachments-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.subproject-item, .attachment-item {
  padding: 10px;
  background: #f7f8fa;
  border-radius: 6px;
}

.subproject-item h4 {
  font-size: 13px;
  color: #333;
  margin-bottom: 4px;
}

.subproject-item p {
  font-size: 12px;
  color: #999;
  margin: 2px 0;
}

.attachment-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.file-type {
  font-size: 11px;
  color: #999;
  background: #eee;
  padding: 2px 8px;
  border-radius: 4px;
}

.progress-info {
  background: white;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.progress-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  font-size: 14px;
}

.stat-item label {
  color: #999;
  margin-right: 6px;
}

.stat-item.success span {
  color: #07c160;
  font-weight: bold;
}

.stat-item.error span {
  color: #ee0a24;
  font-weight: bold;
}

.current-project {
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
  font-size: 13px;
}

.current-project label {
  color: #999;
  margin-right: 6px;
}

.current-project span {
  color: #1989fa;
  font-weight: 500;
}

.status-info {
  margin-top: 8px;
}

.progress-bar {
  height: 24px;
  background: #f0f0f0;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 16px;
  position: relative;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #1989fa, #07c160);
  transition: width 0.3s;
  border-radius: 12px;
}

.progress-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 12px;
  color: #333;
  font-weight: 500;
}

.migration-actions {
  margin-bottom: 16px;
}

.migrate-all-btn {
  width: 100%;
  padding: 14px;
  background: #1989fa;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}

.migrate-all-btn:active {
  background: #0c7cd5;
}

.migrate-all-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.logs-section h3 {
  font-size: 14px;
  color: #333;
  margin-bottom: 10px;
}

.logs-container {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 12px;
  max-height: 200px;
  overflow-y: auto;
}

.log-item {
  padding: 8px 0;
  border-bottom: 1px solid #eee;
  font-size: 12px;
}

.log-item:last-child {
  border-bottom: none;
}

.log-time {
  color: #999;
  margin-right: 8px;
}

.log-message {
  color: #333;
}

.log-item.success .log-message {
  color: #07c160;
}

.log-item.error .log-message {
  color: #ee0a24;
}

@media screen and (min-width: 768px) {
  .migration-sections {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  
  .control-section {
    grid-column: 1 / -1;
  }
  
  .migration-header h1 {
    font-size: 28px;
  }
}

@media screen and (min-width: 1024px) {
  .migration-sections {
    grid-template-columns: 1fr 1fr 1fr;
  }
  
  .control-section {
    grid-column: auto;
  }
}
</style>
