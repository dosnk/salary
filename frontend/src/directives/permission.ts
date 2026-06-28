import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

export const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding
    const authStore = useAuthStore()

    if (value && value instanceof Array && value.length > 0) {
      const requiredRoles = value
      const hasPermission = requiredRoles.includes(authStore.userInfo?.role || '')

      if (!hasPermission) {
        el.parentNode?.removeChild(el)
      }
    } else {
      throw new Error('需要指定角色数组，如 v-permission="[\'admin\']"')
    }
  }
}
