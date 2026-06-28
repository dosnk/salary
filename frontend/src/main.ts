import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'
import { permission } from './directives/permission'

const app = createApp(App)
const pinia = createPinia()

app.directive('permission', permission)

app.use(pinia)
app.use(router)

app.mount('#app')
