<template>
  <div class="login-container">
    <van-form @submit="handleLogin">
      <van-cell-group inset title="三人行装修管理系统">
        <van-field
          v-model="loginForm.username"
          name="username"
          label="用户名"
          placeholder="请输入中文姓名"
          autocomplete="username"
          :rules="[{ required: true, message: '请输入用户名（中文）' }]"
        />
        <van-field
          v-model="loginForm.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          autocomplete="current-password"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
        <div class="remember-row">
          <van-checkbox v-model="rememberMe" shape="square" icon-size="18px">
            保持登录状态
          </van-checkbox>
        </div>
        <van-button
          round
          block
          type="primary"
          native-type="submit"
          :loading="loading"
          loading-text="登录中..."
        >
          登录
        </van-button>
      </van-cell-group>
    </van-form>
    <div class="login-footer">
      <p>@微信群：三人行必有我师</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const rememberMe = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const handleLogin = async () => {
  if (!loginForm.username.trim()) {
    showToast('请输入用户名')
    return
  }

  if (!loginForm.password.trim()) {
    showToast('请输入密码')
    return
  }

  if (loginForm.username.length < 2 || loginForm.username.length > 10) {
    showToast('用户名长度在 2 到 10 个字符')
    return
  }

  if (loginForm.password.length < 3 || loginForm.password.length > 8) {
    showToast('密码长度在 3 到 8 个字符')
    return
  }

  loading.value = true
  try {
    await authStore.login(loginForm.username, loginForm.password, rememberMe.value)
    showToast({ type: 'success', message: '登录成功' })
    router.push('/dashboard')
  } catch (error: any) {
    console.error('登录失败:', error)
    const errorMsg = error?.message || '登录失败，请稍后重试'
    showToast({ type: 'fail', message: errorMsg })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: linear-gradient(135deg, #52c41a 0%, #1890ff 100%);
  padding: 20px;
}

.login-container :deep(.van-cell-group) {
  width: 98%;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  padding: 20px;
}

.login-container :deep(.van-cell-group__title) {
  font-size: 28px;
  font-weight: bold;
  color: #fff;
  text-align: center;
  margin-bottom: 30px;
  padding: 10px 0;
}

.login-container :deep(.van-cell) {
  background: transparent;
  padding: 16px 0;
}

.login-container :deep(.van-field__label) {
  color: #323233;
  font-weight: 500;
  font-size: 16px;
}

.login-container :deep(.van-button--primary) {
  background: linear-gradient(135deg, #52c41a 0%, #1890ff 100%);
  border: none;
  height: 50px;
  font-size: 18px;
  font-weight: bold;
  margin-top: 20px;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
}

.login-container :deep(.van-button--primary:active) {
  background: linear-gradient(135deg, #1890ff 0%, #52c41a 100%);
}

.login-footer {
  text-align: center;
  color: #fff;
  margin-top: 20px;
  font-size: 14px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.login-footer p {
  margin: 0;
  padding: 10px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  display: inline-block;
}

.login-container :deep(.van-toast) {
  color: #fff !important;
  background-color: rgba(0, 0, 0, 0.8) !important;
}

.login-container :deep(.van-toast--fail) {
  color: #fff !important;
  background-color: rgba(245, 34, 45, 0.9) !important;
}

.login-container :deep(.van-toast--success) {
  color: #fff !important;
  background-color: rgba(82, 196, 26, 0.9) !important;
}

.login-container :deep(.van-toast__text) {
  color: #fff !important;
}

.remember-row {
  padding: 12px 0;
  display: flex;
  justify-content: flex-start;
}

.remember-row :deep(.van-checkbox__label) {
  color: #666;
  font-size: 14px;
}
</style>
