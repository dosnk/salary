<template>
  <div class="page-container">
    <van-nav-bar title="用户管理" left-arrow @click-left="router.back()">
      <template #right>
        <van-icon name="plus" size="20" @click="handleAdd" />
      </template>
    </van-nav-bar>
    
    <div class="content">
      <van-loading v-if="loading" class="loading-center" />
      
      <template v-else>
        <van-search v-model="keyword" placeholder="搜索用户名、昵称、手机号" @search="handleSearch" @clear="handleSearch" />
        
        <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
          <van-list
            v-model:loading="listLoading"
            :finished="finished"
            finished-text="没有更多了"
            @load="onLoad"
          >
            <van-cell-group inset>
              <van-swipe-cell v-for="item in list" :key="item.id">
                <van-cell :title="item.nickname || item.username" :label="getLabel(item)" is-link @click="handleEdit(item)" />
                <template #right>
                  <van-button square type="primary" text="重置密码" class="swipe-btn" @click="handleResetPassword(item)" />
                  <van-button square type="danger" text="删除" class="swipe-btn" @click="handleDelete(item)" :disabled="item.id === currentUserId" />
                </template>
              </van-swipe-cell>
            </van-cell-group>
          </van-list>
        </van-pull-refresh>
        
        <van-empty v-if="list.length === 0 && !loading" description="暂无用户" />
      </template>
    </div>
    
    <van-dialog v-model:show="showDialog" :title="dialogTitle" show-cancel-button @confirm="handleSubmit" @cancel="resetForm">
      <van-field v-model="form.username" label="用户名" placeholder="请输入用户名" :rules="[{ required: true, message: '请输入用户名' }]" :disabled="isEdit" />
      <van-field v-if="!isEdit" v-model="form.password" type="password" label="密码" placeholder="请输入密码(3-8位)" :rules="[{ required: true, message: '请输入密码' }]" />
      <van-field v-model="form.nickname" label="昵称" placeholder="请输入昵称" />
      <van-field v-model="form.phone" label="手机号" placeholder="请输入手机号" />
      <van-field name="role" label="角色">
        <template #input>
          <van-radio-group v-model="form.role" direction="horizontal">
            <van-radio name="constructor">施工员</van-radio>
            <van-radio name="documenter">资料员</van-radio>
            <van-radio name="admin">管理员</van-radio>
          </van-radio-group>
        </template>
      </van-field>
    </van-dialog>
    
    <van-dialog v-model:show="showResetDialog" title="重置密码" show-cancel-button @confirm="handleResetSubmit">
      <van-field v-model="resetPasswordForm.new_password" type="password" label="新密码" placeholder="请输入新密码(3-8位)" :rules="[{ required: true, message: '请输入新密码' }]" />
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { usersApi, type User } from '@/api/users'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const currentUserId = computed(() => authStore.userInfo?.id || 0)

const loading = ref(false)
const listLoading = ref(false)
const refreshing = ref(false)
const finished = ref(false)
const keyword = ref('')
const list = ref<User[]>([])
const page = ref(1)
const size = 20

const showDialog = ref(false)
const dialogTitle = ref('新增用户')
const isEdit = ref(false)
const form = ref({
  id: 0,
  username: '',
  password: '',
  nickname: '',
  phone: '',
  role: 'constructor'
})

const showResetDialog = ref(false)
const resetPasswordForm = ref({
  userId: 0,
  new_password: ''
})

const getLabel = (item: User) => {
  const roleMap: Record<string, string> = {
    admin: '管理员',
    documenter: '资料员',
    constructor: '施工员'
  }
  return `${item.username} | ${roleMap[item.role] || item.role} | ${item.phone || '未绑定手机'}`
}

const fetchList = async (isRefresh = false) => {
  if (isRefresh) {
    page.value = 1
    finished.value = false
  }
  
  try {
    const data = await usersApi.getUsers({
      page: page.value,
      size,
      keyword: keyword.value || undefined
    })
    
    if (isRefresh) {
      list.value = data.list || []
    } else {
      list.value = [...list.value, ...(data.list || [])]
    }
    
    finished.value = !data.hasNext
    page.value++
  } catch (error) {
    console.error('获取用户列表失败:', error)
    showToast({ type: 'fail', message: '获取列表失败' })
  } finally {
    loading.value = false
    listLoading.value = false
    refreshing.value = false
  }
}

const onLoad = () => {
  fetchList()
}

const onRefresh = () => {
  fetchList(true)
}

const handleSearch = () => {
  loading.value = true
  fetchList(true)
}

const handleAdd = () => {
  dialogTitle.value = '新增用户'
  isEdit.value = false
  form.value = {
    id: 0,
    username: '',
    password: '',
    nickname: '',
    phone: '',
    role: 'constructor'
  }
  showDialog.value = true
}

const handleEdit = (item: User) => {
  dialogTitle.value = '编辑用户'
  isEdit.value = true
  form.value = {
    id: item.id,
    username: item.username,
    password: '',
    nickname: item.nickname || '',
    phone: item.phone || '',
    role: item.role
  }
  showDialog.value = true
}

const handleSubmit = async () => {
  if (!form.value.username.trim()) {
    showToast({ type: 'fail', message: '请输入用户名' })
    return
  }
  if (!isEdit.value && !form.value.password.trim()) {
    showToast({ type: 'fail', message: '请输入密码' })
    return
  }
  if (form.value.phone && !/^1[3-9]\d{9}$/.test(form.value.phone)) {
    showToast({ type: 'fail', message: '手机号格式错误' })
    return
  }
  
  try {
    if (isEdit.value) {
      await usersApi.updateUser(form.value.id, {
        nickname: form.value.nickname,
        phone: form.value.phone,
        role: form.value.role
      })
      showToast({ type: 'success', message: '更新成功' })
    } else {
      await usersApi.createUser({
        username: form.value.username,
        password: form.value.password,
        nickname: form.value.nickname,
        phone: form.value.phone,
        role: form.value.role
      })
      showToast({ type: 'success', message: '创建成功' })
    }
    showDialog.value = false
    fetchList(true)
  } catch (error: any) {
    console.error('操作失败:', error)
    showToast({ type: 'fail', message: error.message || '操作失败' })
  }
}

const handleDelete = async (item: User) => {
  if (item.id === currentUserId.value) {
    showToast({ type: 'fail', message: '不能删除自己' })
    return
  }
  
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除用户"${item.nickname || item.username}"吗？`
    })
    await usersApi.deleteUser(item.id)
    showToast({ type: 'success', message: '删除成功' })
    fetchList(true)
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      showToast({ type: 'fail', message: error.message || '删除失败' })
    }
  }
}

const handleResetPassword = (item: User) => {
  resetPasswordForm.value = {
    userId: item.id,
    new_password: ''
  }
  showResetDialog.value = true
}

const handleResetSubmit = async () => {
  if (!resetPasswordForm.value.new_password.trim()) {
    showToast({ type: 'fail', message: '请输入新密码' })
    return
  }
  if (resetPasswordForm.value.new_password.length < 3 || resetPasswordForm.value.new_password.length > 8) {
    showToast({ type: 'fail', message: '密码长度需3-8位' })
    return
  }
  
  try {
    await usersApi.resetPassword(resetPasswordForm.value.userId, {
      new_password: resetPasswordForm.value.new_password
    })
    showToast({ type: 'success', message: '密码重置成功' })
    showResetDialog.value = false
  } catch (error: any) {
    console.error('重置密码失败:', error)
    showToast({ type: 'fail', message: error.message || '重置密码失败' })
  }
}

const resetForm = () => {
  form.value = {
    id: 0,
    username: '',
    password: '',
    nickname: '',
    phone: '',
    role: 'constructor'
  }
}

onMounted(() => {
  loading.value = true
  fetchList()
})
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f7f8fa;
}

.content {
  padding: 16px;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 40px;
}

.swipe-btn {
  height: 100%;
}
</style>
