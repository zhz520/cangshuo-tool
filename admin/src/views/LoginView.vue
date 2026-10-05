<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import { ApiRequestError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const form = reactive({ username: '', password: '' })
const errorMessage = ref<string | null>(null)

function messageFor(error: unknown): string {
  if (error instanceof ApiRequestError) {
    switch (error.code) {
      case 20004: return '管理员账号或密码不正确。'
      case 10007: return '尝试次数过多，请稍后再试。'
      case 10001: return '请检查账号和密码格式。'
      default: return error.message
    }
  }
  return '暂时无法连接平台服务，请稍后重试。'
}

async function submit(): Promise<void> {
  if (auth.loggingIn) return
  errorMessage.value = null
  const username = form.username.trim()
  if (username.length < 3 || form.password.length < 8) {
    errorMessage.value = '请输入至少 3 位账号和至少 8 位密码。'
    return
  }
  try {
    await auth.login(username, form.password)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (error: unknown) {
    errorMessage.value = messageFor(error)
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <div class="login-brand">
        <span class="brand-mark">沧</span>
        <span><strong>沧烁工具箱</strong><small>管理控制台</small></span>
      </div>
      <h1 class="login-heading">管理员登录</h1>
      <p class="login-hint">使用部署环境初始化的管理员账号登录；连续失败会暂时锁定账号与来源地址，登录与退出会写入管理日志。</p>
      <ElAlert v-if="errorMessage" class="login-error" type="error" :closable="false" :title="errorMessage" show-icon />
      <ElForm label-position="top" @submit.prevent="submit">
        <ElFormItem label="管理员账号">
          <ElInput v-model="form.username" name="username" autocomplete="username" maxlength="32"
            placeholder="3-32 位字母、数字或 . _ -" clearable />
        </ElFormItem>
        <ElFormItem label="密码">
          <ElInput v-model="form.password" name="password" type="password" autocomplete="current-password"
            maxlength="72" placeholder="请输入密码" show-password />
        </ElFormItem>
        <ElButton class="login-submit" type="primary" native-type="submit" :loading="auth.loggingIn">登录</ElButton>
      </ElForm>
      <p class="login-footer">密码不会写入浏览器存储；登录令牌仅保存在本机浏览器。</p>
    </section>
  </main>
</template>
