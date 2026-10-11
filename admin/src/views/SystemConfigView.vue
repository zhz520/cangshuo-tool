<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { CONFIG_KEYS, listAdminConfig, updateAdminConfig, type ConfigKey } from '@/api/config'
import { ApiRequestError } from '@/api/client'

const fields = reactive<Record<ConfigKey, string>>({
  APP_LATEST_VERSION_CODE: '1',
  APP_LATEST_VERSION_NAME: '0.1.0',
  APP_UPDATE_URL: '',
  APP_UPDATE_NOTES: '',
})
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const errorTraceId = ref('')

async function load(): Promise<void> {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  errorTraceId.value = ''
  try {
    const result = await listAdminConfig()
    for (const entry of result.data) {
      if ((CONFIG_KEYS as readonly string[]).includes(entry.key)) {
        fields[entry.key as ConfigKey] = entry.value
      }
    }
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '读取配置失败，请稍后重试。'
    errorTraceId.value = error instanceof ApiRequestError ? (error.traceId ?? '') : ''
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (saving.value || loading.value) return
  saving.value = true
  errorMessage.value = ''
  errorTraceId.value = ''
  try {
    for (const key of CONFIG_KEYS) {
      await updateAdminConfig(key, fields[key])
    }
    ElMessage.success('配置已保存，立即生效')
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '保存配置失败，请稍后重试。'
    errorTraceId.value = error instanceof ApiRequestError ? (error.traceId ?? '') : ''
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section>
    <AdminPageHeader title="系统配置" description="管理客户端更新等业务配置；保存后立即生效，无需重启服务。">
      <template #actions>
        <ElButton :loading="loading" :disabled="saving" @click="load">重新读取</ElButton>
        <ElButton type="primary" :loading="saving" :disabled="loading" @click="save">保存全部</ElButton>
      </template>
    </AdminPageHeader>

    <div v-if="errorMessage" class="admin-error" role="alert">
      <ElAlert type="error" :closable="false" :title="errorMessage" show-icon />
      <p v-if="errorTraceId" class="admin-trace">请求标识：<code>{{ errorTraceId }}</code></p>
    </div>

    <ElForm label-position="top" class="system-config-form" @submit.prevent="save">
      <ElFormItem label="最新版本 Code（整数，用于比较新旧）">
        <ElInput v-model="fields.APP_LATEST_VERSION_CODE" maxlength="10" :disabled="saving || loading" />
      </ElFormItem>
      <ElFormItem label="最新版本号（例如 0.2.0）">
        <ElInput v-model="fields.APP_LATEST_VERSION_NAME" maxlength="40" :disabled="saving || loading" />
      </ElFormItem>
      <ElFormItem label="更新下载地址（http/https，可留空）">
        <ElInput v-model="fields.APP_UPDATE_URL" maxlength="500" :disabled="saving || loading" placeholder="填写后 App 内可一键打开发布页" />
      </ElFormItem>
      <ElFormItem label="更新说明（可留空）">
        <ElInput v-model="fields.APP_UPDATE_NOTES" type="textarea" :rows="3" maxlength="300" show-word-limit
          :disabled="saving || loading" placeholder="会展示给检测到新版本的用户" />
      </ElFormItem>
    </ElForm>

    <p class="admin-muted system-config-help">
      App 设置页的「检查更新」读取这里的版本号与版本名；当服务端版本 Code 大于手机上的版本时，会提示新版本并展示更新说明与下载入口。
    </p>
  </section>
</template>

<style scoped>
.system-config-form { max-width: 640px; }
.system-config-help { font-size: 12px; color: var(--admin-muted); margin-top: 4px; }
</style>
