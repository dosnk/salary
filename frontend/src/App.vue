<template>
  <router-view v-slot="{ Component, route }">
    <keep-alive :include="['Dashboard']">
      <component :is="Component" :key="route.path" />
    </keep-alive>
  </router-view>
</template>

<script setup lang="ts">
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial,
    'Noto Sans', sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol',
    'Noto Color Emoji';
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* 全局 Toast 样式 */
.van-toast {
  color: #fff !important;
  background-color: rgba(0, 0, 0, 0.8) !important;
}

.van-toast--fail {
  color: #fff !important;
  background-color: rgba(245, 34, 45, 0.9) !important;
}

.van-toast--success {
  color: #fff !important;
  background-color: rgba(82, 196, 26, 0.9) !important;
}

.van-toast--loading {
  color: #fff !important;
  background-color: rgba(0, 0, 0, 0.8) !important;
}

.van-toast__text {
  color: #fff !important;
}
</style>
