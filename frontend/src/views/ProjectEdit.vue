<template>
  <div class="project-edit">
    <van-nav-bar
      title="编辑工程"
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

    <div class="content-wrapper">
      <van-form @submit="handleSubmit">
        <van-cell-group inset title="基本信息">
          <van-field
            v-model="form.name"
            name="name"
            label="工程名称"
            placeholder="请输入工程名称"
            :rules="[{ required: true, message: '请输入工程名称' }]"
          />
          <van-field
            name="status"
            label="工程状态"
            readonly
            is-link
            :model-value="statusTextMap[form.status || ''] || ''"
            @click="showStatusPicker = true"
          />
        </van-cell-group>

        <van-cell-group inset title="工资分配">
          <van-field label="分配方式">
            <template #input>
              <van-radio-group v-model="form.salaryDistribution" direction="horizontal">
                <van-radio name="average">平均</van-radio>
                <van-radio name="work_days">按工日</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <div v-if="form.salaryDistribution === 'work_days'" class="work-days-section">
            <van-cell title="总工日" :value="formatTotalWorkDays" />
            <van-cell-group inset title="个人工日（仅可以修改自己的工日）">
              <van-field
                v-for="worker in workers"
                :key="worker.id"
                :label="worker.nickname || worker.username"
                type="number"
                v-model.number="worker.workdays"
                placeholder="请输入工日（0.5天起）"
                :step="0.5"
                :min="0.5"
                :max="9999"
                inputmode="decimal"
                :disabled="isConstructorUser && worker.id !== currentUser?.id"
                :rules="[
                  { required: true, message: '请输入该人员的工日' },
                  { 
                    validator: (val: any) => {
                      if (val === '' || val === null || val === undefined) return false
                      const num = Number(val)
                      if (isNaN(num)) return false
                      const isHalfDay = num % 0.5 === 0
                      return isHalfDay
                    },
                    message: '工日必须是整数或0.5的倍数'
                  }
                ]"
                @focus="handleWorkdayFocus(worker)"
              >
                <template #button>
                  <span style="color: #999; font-size: 14px;">天</span>
                </template>
              </van-field>
            </van-cell-group>
          </div>
        </van-cell-group>

        <van-cell-group inset title="施工人员">
          <van-cell title="请选择施工人员">
            <template #label>
              <div class="constructor-scroll">
                <van-checkbox-group v-model="selectedWorkerIds" direction="horizontal">
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
        </van-cell-group>

        <van-cell-group inset title="备注">
          <van-field
            v-model="form.remark"
            name="remark"
            type="textarea"
            placeholder="请输入工程备注"
            rows="3"
          />
        </van-cell-group>

        <div class="form-actions">
          <van-button type="primary" native-type="submit" :loading="loading">保存</van-button>
          <van-button type="default" @click="goBack">取消</van-button>
        </div>
      </van-form>
    </div>

    <van-popup v-model:show="showStatusPicker" position="bottom">
      <van-picker
        :columns="statusOptions"
        @confirm="onStatusConfirm"
        @cancel="showStatusPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { projectsApi } from '@/api/projects'
import { usersApi } from '@/api/users'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const projectId = ref<number>(parseInt(route.params.id as string))
const loading = ref(false)
const showStatusPicker = ref(false)

const currentUser = computed(() => authStore.userInfo)
const unreadCount = computed(() => authStore.unreadCount)

const isConstructorUser = computed(() => {
  return currentUser.value?.role === 'constructor'
})

const form = reactive({
  name: '',
  remark: '',
  status: 'constructing' as 'constructing' | 'completed' | 'preparing' | 'canceled',
  salaryDistribution: 'average' as 'average' | 'work_days',
  totalWorkDays: 0
})

const workers = ref<{ id: number; nickname: string; username: string; workdays: number; originalWorkdays: number }[]>([])
const selectedWorkerIds = ref<number[]>([])
const constructorUsers = ref<{ id: number; nickname?: string; username: string }[]>([])

const statusOptions = ref<{ text: string; value: string }[]>([
  { text: '备料中', value: 'preparing' },
  { text: '施工中', value: 'constructing' },
  { text: '已完工', value: 'completed' },
  { text: '已取消', value: 'canceled' }
])

const statusTextMap = computed(() => {
  const map: Record<string, string> = {}
  statusOptions.value.forEach(option => {
    map[option.value] = option.text
  })
  return map
})

const calculateTotalWorkDays = computed(() => {
  return workers.value.reduce((total, worker) => {
    const workdays = Number(worker.workdays)
    return total + (isNaN(workdays) ? 0 : workdays)
  }, 0)
})

const formatTotalWorkDays = computed(() => {
  const total = calculateTotalWorkDays.value
  if (!total || total === 0) {
    return '自动计算'
  }
  return Number(total).toFixed(2)
})

const handleWorkdayFocus = (worker: any) => {
  if (worker.workdays === 1) {
    worker.workdays = '' as any
  }
}

const goBack = () => {
  router.back()
}

const goToMessages = () => {
  router.push('/message')
}

const onStatusConfirm = ({ selectedOptions }: any) => {
  if (selectedOptions && selectedOptions.length > 0) {
    form.status = selectedOptions[0].value
  }
  showStatusPicker.value = false
}

const handleSubmit = async () => {
  if (!form.name || form.name.trim() === '') {
    showToast('请输入工程名称')
    return
  }

  if (selectedWorkerIds.value.length === 0) {
    showToast('请选择施工人员')
    return
  }

  if (form.salaryDistribution === 'work_days') {
    const emptyWorker = workers.value.find(w => !w.workdays || w.workdays <= 0)
    if (emptyWorker) {
      showToast(`请输入${emptyWorker.nickname || emptyWorker.username}的工日`)
      return
    }
    
    if (isConstructorUser.value) {
      const modifiedOtherWorker = workers.value.find(w => {
        const isSelf = w.id === currentUser.value?.id
        const originalWorkdays = w.originalWorkdays || 0
        const currentWorkdays = w.workdays || 0
        return !isSelf && currentWorkdays !== originalWorkdays
      })
      
      if (modifiedOtherWorker) {
        showToast('施工员只能修改自己的工日')
        return
      }
    }
  }

  try {
    loading.value = true
    const params: any = {
      name: form.name,
      description: form.remark,
      status: form.status,
      salaryDistribution: form.salaryDistribution,
      constructors: selectedWorkerIds.value.map(id => ({ userId: id }))
    }

    if (form.salaryDistribution === 'work_days') {
      params.totalWorkDays = calculateTotalWorkDays.value
      params.workerWorkDays = workers.value.map(w => ({
        userId: w.id,
        workdays: w.workdays
      }))
    }
    
    await projectsApi.updateProject(projectId.value, params)
    showToast({ type: 'success', message: '保存成功' })
    router.back()
  } catch (error: any) {
    console.error('保存失败:', error)
    const errorMsg = error?.message || '保存失败'
    showToast({ type: 'fail', message: errorMsg })
  } finally {
    loading.value = false
  }
}

const loadProjectDetail = async () => {
  try {
    const detail = await projectsApi.getProject(projectId.value)
    form.name = detail.name
    form.remark = detail.description || ''
    form.status = detail.status
    form.salaryDistribution = detail.salary_distribution || 'average'
    form.totalWorkDays = detail.total_work_days || 0

    if (detail.constructors && Array.isArray(detail.constructors)) {
      selectedWorkerIds.value = detail.constructors.map((w: any) => w.id)
      workers.value = detail.constructors.map((w: any) => ({
        id: w.id,
        nickname: w.nickname,
        username: w.username,
        workdays: w.workdays || 1,
        originalWorkdays: w.workdays || 1
      }))
    }
  } catch (error: any) {
    console.error('加载工程详情失败:', error)
    showToast({ type: 'fail', message: '加载工程详情失败' })
    router.back()
  }
}

const loadConstructorUsers = async () => {
  try {
    const result = await usersApi.getConstructors()
    constructorUsers.value = result
  } catch (error: any) {
    console.error('加载用户列表失败:', error)
  }
}

onMounted(() => {
  loadProjectDetail()
  loadConstructorUsers()
})
</script>

<style scoped>
.project-edit {
  min-height: 100vh;
  background: linear-gradient(180deg, #f9fef5 0%, #f9fef5 100%);
  padding-top: 46px;
}

.project-edit :deep(.van-nav-bar) {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
}

.project-edit :deep(.van-nav-bar__arrow) {
  color: #fff !important;
}

.content-wrapper {
  padding: 16px;
}

.form-actions {
  margin-top: 20px;
  display: flex;
  gap: 10px;
}

.form-actions .van-button {
  flex: 1;
  border: none;
}

.form-actions .van-button--primary {
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  color: #fff;
}

.form-actions .van-button--default {
  background: #f5f5f5;
  color: #333;
  border: 1px solid #ddd;
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

.constructor-scroll {
  max-height: 120px;
  overflow-y: auto;
  padding: 8px 0;
}

.constructor-scroll :deep(.van-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.constructor-scroll :deep(.van-checkbox) {
  margin-right: 0;
}

.constructor-scroll :deep(.van-checkbox__label) {
  font-size: 12px;
  color: #333;
}

.constructor-scroll :deep(.van-checkbox__icon) {
  font-size: 16px;
}
</style>
