<script setup lang="ts" generic="T">
defineProps<{ records: T[]; recordKey: (record: T) => string | number }>()
</script>

<template>
  <div class="admin-record-cards">
    <article v-for="record in records" :key="recordKey(record)" class="admin-record-card">
      <div class="admin-record-heading"><div class="admin-record-identity"><slot name="title" :record="record" /></div><slot name="status" :record="record" /></div>
      <div v-if="$slots.description" class="admin-record-description"><slot name="description" :record="record" /></div>
      <div v-if="$slots.meta" class="admin-record-meta"><slot name="meta" :record="record" /></div>
      <div v-if="$slots.actions" class="admin-record-actions"><slot name="actions" :record="record" /></div>
    </article>
  </div>
</template>
