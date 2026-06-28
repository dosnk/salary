<template>
  <div class="page-container">
    <van-nav-bar title="空间类型管理" left-arrow @click-left="router.back()" />
    
    <div class="content">
      <van-loading v-if="loading" class="loading-center" />
      
      <template v-else>
        <div class="list-container">
          <van-cell-group inset>
            <van-swipe-cell v-for="item in list" :key="item.id">
              <van-cell :title="item.name" :value="`ID: ${item.id}`" />
              <template #right>
                <van-button square type="primary" text="编辑" class="swipe-btn" @click="handleEdit(item)" />
                <van-button square type="danger" text="删除" class="swipe-btn" @click="handleDelete(item)" />
              </template>
            </van-swipe-cell>
          </van-cell-group>
          
          <van-empty v-if="list.length === 0" description="暂无数据" />
        </div>
        
        <div class="add-btn-container">
          <van-button type="primary" block @click="handleAdd">
            <van-icon name="plus" /> 新增空间类型
          </van-button>
        </div>
      </template>
    </div>
    
    <van-dialog v-model:show="showDialog" :title="dialogTitle" show-cancel-button @confirm="handleSubmit" @cancel="resetForm">
      <van-field v-model="form.name" label="名称" placeholder="请输入空间类型名称" :rules="[{ required: true, message: '请输入名称' }]" />
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import dictionaryApi, { type SpaceType } from '@/api/dictionary'

const router = useRouter()
const loading = ref(false)
const list = ref<SpaceType[]>([])
const showDialog = ref(false)
const dialogTitle = ref('新增空间类型')
const form = ref({
  id: 0,
  name: ''
})

const fetchList = async () => {
  loading.value = true
  try {
    const data = await dictionaryApi.getSpaceTypes()
    list.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.error('获取空间类型列表失败:', error)
    showToast({ type: 'fail', message: '获取列表失败' })
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  dialogTitle.value = '新增空间类型'
  form.value = { id: 0, name: '' }
  showDialog.value = true
}

const handleEdit = (item: SpaceType) => {
  dialogTitle.value = '编辑空间类型'
  form.value = { id: item.id, name: item.name }
  showDialog.value = true
}

const handleSubmit = async () => {
  if (!form.value.name.trim()) {
    showToast({ type: 'fail', message: '请输入名称' })
    return
  }
  
  try {
    if (form.value.id) {
      await dictionaryApi.updateSpaceType(form.value.id, { name: form.value.name })
      showToast({ type: 'success', message: '更新成功' })
    } else {
      await dictionaryApi.createSpaceType({ name: form.value.name })
      showToast({ type: 'success', message: '创建成功' })
    }
    showDialog.value = false
    dictionaryApi.clearCache()
    fetchList()
  } catch (error: any) {
    console.error('操作失败:', error)
    showToast({ type: 'fail', message: error.message || '操作失败' })
  }
}

const handleDelete = async (item: SpaceType) => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除"${item.name}"吗？`
    })
    await dictionaryApi.deleteSpaceType(item.id)
    showToast({ type: 'success', message: '删除成功' })
    dictionaryApi.clearCache()
    fetchList()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      showToast({ type: 'fail', message: error.message || '删除失败' })
    }
  }
}

const resetForm = () => {
  form.value = { id: 0, name: '' }
}

onMounted(() => {
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

.list-container {
  margin-bottom: 80px;
}

.swipe-btn {
  height: 100%;
}

.add-btn-container {
  position: fixed;
  bottom: 20px;
  left: 16px;
  right: 16px;
}
</style>
