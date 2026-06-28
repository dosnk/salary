<template>
  <div class="forbidden-container">
    <div class="forbidden-content">
      <van-icon name="warning-o" size="80" color="#ee0a24" />
      <h1>403</h1>
      <p>抱歉，您没有权限访问此页面</p>
      <van-button type="primary" @click="goBack">返回上一页</van-button>
      <van-button plain @click="goHome">返回首页</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goBack = () => {
  router.back()
}

const goHome = () => {
  router.push('/dashboard')
}
</script>

<style scoped>
.forbidden-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f7f8fa;
}

.forbidden-content {
  text-align: center;
  padding: 20px;
}

.forbidden-content h1 {
  font-size: 48px;
  color: #ee0a24;
  margin: 20px 0 10px;
}

.forbidden-content p {
  font-size: 16px;
  color: #646566;
  margin-bottom: 30px;
}

.forbidden-content .van-button {
  margin: 0 10px;
}
</style>
