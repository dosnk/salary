<template>
  <div class="page-container">
    <van-nav-bar title="AI大模型配置" left-arrow @click-left="router.back()" />

    <div class="content">
      <van-loading v-if="loading" class="loading-center" />

      <template v-else>
        <van-notice-bar v-if="errorMsg" mode="closeable" color="#d32f2f" background="#ffebee">
          {{ errorMsg }}
        </van-notice-bar>

        <van-notice-bar v-if="saveSuccess" mode="closeable" color="#52c41a" background="#e8f5e9">
          配置已保存，将在下次请求时生效
        </van-notice-bar>

        <van-cell-group title="默认AI提供商" inset>
          <van-cell
            v-for="provider in providerList"
            :key="provider.key"
            :title="provider.name"
            clickable
            @click="selectProvider(provider.key)"
          >
            <template #icon>
              <van-icon
                :name="selectedProvider === provider.key ? 'checked' : 'circle'"
                :color="selectedProvider === provider.key ? '#84cc16' : '#ccc'"
                size="20"
                class="radio-icon"
              />
            </template>
            <template #value>
              <van-tag :type="config?.providers[provider.key]?.hasApiKey ? 'success' : 'default'" size="medium">
                {{ config?.providers[provider.key]?.hasApiKey ? '已配置' : '未配置' }}
              </van-tag>
            </template>
          </van-cell>
        </van-cell-group>

        <van-cell-group :title="currentProviderName + ' 参数配置'" inset>
          <div class="provider-config-body">
            <van-field
              v-model="editProviderConfigs[selectedProvider].apiKey"
              label="API Key"
              :placeholder="config?.providers[selectedProvider]?.hasApiKey ? '已配置（留空不修改）' : '请输入API Key'"
              :type="showKeys[selectedProvider] ? 'text' : 'password'"
              clearable
            >
              <template #button>
                <van-button size="small" @click="toggleShowKey(selectedProvider)">
                  {{ showKeys[selectedProvider] ? '隐藏' : '显示' }}
                </van-button>
              </template>
            </van-field>

            <van-field
              v-if="selectedProvider === 'wenxin'"
              v-model="editProviderConfigs[selectedProvider].secretKey"
              label="Secret Key"
              :placeholder="config?.providers[selectedProvider]?.hasSecretKey ? '已配置（留空不修改）' : '请输入Secret Key'"
              :type="showSecretKeys[selectedProvider] ? 'text' : 'password'"
              clearable
            >
              <template #button>
                <van-button size="small" @click="toggleShowSecretKey(selectedProvider)">
                  {{ showSecretKeys[selectedProvider] ? '隐藏' : '显示' }}
                </van-button>
              </template>
            </van-field>

            <van-field
              v-model="editProviderConfigs[selectedProvider].model"
              label="模型名称"
              :placeholder="'当前：' + (config?.providers[selectedProvider]?.model || '')"
              clearable
            />

            <div class="config-info">
              <span class="info-label">服务地址：</span>
              <span class="info-value">{{ config?.providers[selectedProvider]?.baseUrl || '' }}</span>
            </div>
            <div class="config-info">
              <span class="info-label">最大Token：{{ config?.providers[selectedProvider]?.maxTokens || '' }}</span>
              <span class="info-value" style="margin-left: 16px;">温度：{{ config?.providers[selectedProvider]?.temperature || '' }}</span>
            </div>
          </div>
        </van-cell-group>

        <div class="btn-container">
          <van-button
            plain
            type="primary"
            class="test-btn"
            :loading="testing"
            @click="handleTest"
          >
            API连接测试
          </van-button>
          <van-button
            type="primary"
            class="save-btn"
            :loading="saving"
            @click="handleSave"
          >
            保存配置
          </van-button>
        </div>
      </template>
    </div>

    <!-- API连接测试结果弹窗 -->
    <van-dialog
      v-model:show="testDialogShow"
      :title="testDialogTitle"
      confirm-button-text="确认"
      confirm-button-color="#65a30d"
      :show-cancel-button="false"
      @confirm="testDialogShow = false"
    >
      <div class="dialog-content">{{ testDialogMessage }}</div>
    </van-dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { aiApi, type AiConfigResponse, type AiProviderConfigUpdate } from '@/api/ai'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const errorMsg = ref('')
const saveSuccess = ref(false)

// API连接测试弹窗状态
const testDialogShow = ref(false)
const testDialogTitle = ref('')
const testDialogMessage = ref('')
const config = ref<AiConfigResponse | null>(null)
const selectedProvider = ref('deepseek')

const providerList = [
  { key: 'deepseek', name: 'DeepSeek' },
  { key: 'tongyi', name: '通义千问' },
  { key: 'wenxin', name: '文心一言' },
  { key: 'glm', name: '智谱ChatGLM' },
  { key: 'doubao', name: '豆包' }
]

// 当前选中的提供商名称（用于配置区标题）
const currentProviderName = computed(() => {
  return providerList.find(p => p.key === selectedProvider.value)?.name || ''
})

const showKeys = reactive<Record<string, boolean>>({
  deepseek: false,
  tongyi: false,
  wenxin: false,
  glm: false,
  doubao: false
})

const showSecretKeys = reactive<Record<string, boolean>>({
  deepseek: false,
  tongyi: false,
  wenxin: false,
  glm: false,
  doubao: false
})

const editProviderConfigs = reactive<Record<string, { apiKey: string; secretKey: string; model: string }>>({
  deepseek: { apiKey: '', secretKey: '', model: '' },
  tongyi: { apiKey: '', secretKey: '', model: '' },
  wenxin: { apiKey: '', secretKey: '', model: '' },
  glm: { apiKey: '', secretKey: '', model: '' },
  doubao: { apiKey: '', secretKey: '', model: '' }
})

const toggleShowKey = (key: string) => {
  showKeys[key] = !showKeys[key]
}

const toggleShowSecretKey = (key: string) => {
  showSecretKeys[key] = !showSecretKeys[key]
}

const selectProvider = (key: string) => {
  selectedProvider.value = key
}

const loadConfig = async () => {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await aiApi.getConfig()
    config.value = data
    selectedProvider.value = data.defaultProvider || 'deepseek'

    for (const key of providerList.map(p => p.key)) {
      const provider = data.providers[key]
      if (provider) {
        editProviderConfigs[key] = {
          apiKey: provider.apiKey || '',
          secretKey: provider.secretKey || '',
          model: provider.model || ''
        }
      }
    }
  } catch (error: any) {
    errorMsg.value = error.message || '加载AI配置失败'
    showToast({ type: 'fail', message: error.message || '加载AI配置失败' })
  } finally {
    loading.value = false
  }
}

const handleTest = async () => {
  testing.value = true
  errorMsg.value = ''
  try {
    const data = await aiApi.testConnection({ provider: selectedProvider.value })
    testDialogTitle.value = '连接测试成功'
    testDialogMessage.value = `${data.providerName}（${data.model}）连接测试成功\nAI回复：${data.response}`
  } catch (error: any) {
    testDialogTitle.value = '连接测试失败'
    testDialogMessage.value = error.message || '连接测试失败'
  } finally {
    testing.value = false
    testDialogShow.value = true
  }
}

const handleSave = async () => {
  saving.value = true
  errorMsg.value = ''
  saveSuccess.value = false

  try {
    const providerConfigs: Record<string, AiProviderConfigUpdate> = {}

    for (const key of providerList.map(p => p.key)) {
      const original = config.value?.providers[key]
      const edited = editProviderConfigs[key]

      const updateItem: AiProviderConfigUpdate = {}

      if (edited.apiKey && edited.apiKey !== original?.apiKey) {
        updateItem.apiKey = edited.apiKey
      }

      if (key === 'wenxin' && edited.secretKey && edited.secretKey !== original?.secretKey) {
        updateItem.secretKey = edited.secretKey
      }

      if (edited.model && edited.model !== original?.model) {
        updateItem.model = edited.model
      }

      if (updateItem.apiKey || updateItem.secretKey || updateItem.model) {
        providerConfigs[key] = updateItem
      }
    }

    await aiApi.updateConfig({
      defaultProvider: selectedProvider.value,
      providerConfigs
    })

    saveSuccess.value = true
    showToast({ type: 'success', message: '配置已保存' })

    await loadConfig()
  } catch (error: any) {
    errorMsg.value = error.message || '保存AI配置失败'
    showToast({ type: 'fail', message: error.message || '保存AI配置失败' })
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f5f7fa;
}

.content {
  padding: 12px 0 24px;
}

.loading-center {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}

.radio-icon {
  margin-right: 8px;
}

.provider-config-body {
  padding: 8px 0;
  background: #fafafa;
}

.provider-config-body :deep(.van-field) {
  background: #fff;
}

.config-info {
  padding: 8px 16px;
  font-size: 12px;
  color: #969799;
  background: #fafafa;
}

.info-label {
  font-weight: 500;
}

.info-value {
  color: #646566;
}

.btn-container {
  display: flex;
  gap: 12px;
  padding: 24px 16px;
}

.test-btn {
  flex: 1;
  border-radius: 8px;
  border-color: #84cc16;
  color: #65a30d;
}

.save-btn {
  flex: 1;
  border-radius: 8px;
  background: linear-gradient(135deg, #84cc16 0%, #65a30d 100%);
  border: none;
}

.dialog-content {
  padding: 20px 16px;
  font-size: 14px;
  color: #323233;
  line-height: 1.6;
  white-space: pre-wrap;
  text-align: center;
}
</style>
