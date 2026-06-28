<template>
  <div class="page-container">
    <van-nav-bar title="施工方案管理" left-arrow @click-left="router.back()" />
    
    <div class="content">
      <van-loading v-if="loading" class="loading-center" />
      
      <template v-else>
        <div class="list-container">
          <van-cell-group inset>
            <van-swipe-cell v-for="item in list" :key="item.id">
              <van-cell :title="item.name" :label="`单位: ${getUnitName(item.unit)} | 单价: ${item.price}元`" />
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
            <van-icon name="plus" /> 新增施工方案
          </van-button>
        </div>
      </template>
    </div>
    
    <van-dialog v-model:show="showDialog" :title="dialogTitle" show-cancel-button @confirm="handleSubmit" @cancel="resetForm">
      <van-field v-model="form.name" label="名称" placeholder="请输入施工方案名称" :rules="[{ required: true, message: '请输入名称' }]" />
      <van-field
        v-model="form.unitName"
        is-link
        readonly
        label="单位"
        placeholder="请选择单位"
        @click="showUnitPicker = true"
        :rules="[{ required: true, message: '请选择单位' }]"
      />
      <van-field v-model.number="form.price" type="number" label="单价(元)" placeholder="请输入单价" :rules="[{ required: true, message: '请输入单价' }]" />
    </van-dialog>
    
    <van-popup v-model:show="showUnitPicker" position="bottom" round>
      <van-picker
        title="选择单位"
        :columns="unitColumns"
        @confirm="onUnitConfirm"
        @cancel="showUnitPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import dictionaryApi, { type ConstructionPlan, type ConstructionUnit } from '@/api/dictionary'

const router = useRouter()
const loading = ref(false)
const list = ref<ConstructionPlan[]>([])
const showDialog = ref(false)
const dialogTitle = ref('新增施工方案')
const showUnitPicker = ref(false)
const unitList = ref<ConstructionUnit[]>([])
const form = ref({
  id: 0,
  name: '',
  unit: '',
  unitName: '',
  price: 0
})

const unitColumns = computed(() => {
  return unitList.value.map(item => ({
    text: item.name,
    value: item.code
  }))
})

const getUnitName = (code: string) => {
  const unit = unitList.value.find(u => u.code === code)
  return unit ? unit.name : code
}

const onUnitConfirm = ({ selectedOptions }: any) => {
  const selected = selectedOptions[0]
  form.value.unit = selected.value
  form.value.unitName = selected.text
  showUnitPicker.value = false
}

const fetchUnits = async () => {
  try {
    const data = await dictionaryApi.getConstructionUnits()
    unitList.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.error('获取单位列表失败:', error)
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const data = await dictionaryApi.getConstructionPlans()
    list.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.error('获取施工方案列表失败:', error)
    showToast({ type: 'fail', message: '获取列表失败' })
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  dialogTitle.value = '新增施工方案'
  form.value = { id: 0, name: '', unit: '', unitName: '', price: 0 }
  showDialog.value = true
}

const handleEdit = (item: ConstructionPlan) => {
  dialogTitle.value = '编辑施工方案'
  form.value = { 
    id: item.id, 
    name: item.name, 
    unit: item.unit, 
    unitName: getUnitName(item.unit),
    price: item.price 
  }
  showDialog.value = true
}

const handleSubmit = async () => {
  if (!form.value.name.trim()) {
    showToast({ type: 'fail', message: '请输入名称' })
    return
  }
  if (!form.value.unit) {
    showToast({ type: 'fail', message: '请选择单位' })
    return
  }
  if (!form.value.price || form.value.price <= 0) {
    showToast({ type: 'fail', message: '请输入有效单价' })
    return
  }
  
  try {
    if (form.value.id) {
      await dictionaryApi.updateConstructionPlan(form.value.id, {
        name: form.value.name,
        unit: form.value.unit,
        price: form.value.price
      })
      showToast({ type: 'success', message: '更新成功' })
    } else {
      await dictionaryApi.createConstructionPlan({
        name: form.value.name,
        unit: form.value.unit,
        price: form.value.price
      })
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

const handleDelete = async (item: ConstructionPlan) => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除"${item.name}"吗？`
    })
    await dictionaryApi.deleteConstructionPlan(item.id)
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
  form.value = { id: 0, name: '', unit: '', unitName: '', price: 0 }
}

onMounted(() => {
  fetchUnits()
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
