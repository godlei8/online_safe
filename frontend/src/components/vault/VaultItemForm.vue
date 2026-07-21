<script setup lang="ts">
import { ref } from 'vue'
import {
  fieldTypeSchema,
  type FieldType,
  type VaultField,
  type VaultItemPayload,
} from '@/domain/vaultPayload'

const form = defineModel<VaultItemPayload>({ required: true })

defineProps<{
  loading?: boolean
  errorMessage?: string
}>()

const tagInput = ref('')
const fieldTypes = fieldTypeSchema.options

const statusItems = [
  { title: '正常', value: 'NORMAL' },
  { title: '待验证', value: 'PENDING' },
  { title: '异常', value: 'ABNORMAL' },
  { title: '失效', value: 'INVALID' },
  { title: '已归档', value: 'ARCHIVED' },
]

function addField() {
  const order = form.value.fields.length
  form.value.fields.push({
    id: crypto.randomUUID(),
    name: `字段 ${order + 1}`,
    type: 'TEXT',
    value: '',
    required: false,
    sensitive: false,
    copyable: true,
    hint: '',
    order,
  })
}

function removeField(id: string) {
  form.value.fields = form.value.fields
    .filter((field) => field.id !== id)
    .map((field, index) => ({ ...field, order: index }))
}

function moveField(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= form.value.fields.length) return
  const fields = [...form.value.fields]
  const [item] = fields.splice(index, 1)
  fields.splice(target, 0, item)
  form.value.fields = fields.map((field, order) => ({ ...field, order }))
}

function onTypeChange(field: VaultField, type: FieldType) {
  field.type = type
  if (type === 'PASSWORD' || type === 'CODE') {
    field.sensitive = true
    field.copyable = true
  }
}

function addTag() {
  const tag = tagInput.value.trim()
  if (!tag || form.value.tags.includes(tag)) return
  form.value.tags.push(tag)
  tagInput.value = ''
}

function removeTag(tag: string) {
  form.value.tags = form.value.tags.filter((item) => item !== tag)
}
</script>

<template>
  <div class="vault-item-form">
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-3" />

    <v-alert v-if="errorMessage" type="error" variant="tonal" density="compact" class="mb-3">
      {{ errorMessage }}
    </v-alert>

    <template v-if="!loading">
      <section class="vault-item-form__section">
        <h3 class="vault-item-form__section-title">基本信息</h3>
        <div class="vault-item-form__grid">
          <v-text-field v-model="form.name" label="记录名称" density="compact" required />
          <v-text-field v-model="form.platform" label="所属平台" density="compact" required />
          <v-text-field v-model="form.channel" label="获取渠道" density="compact" />
          <v-select
            v-model="form.status"
            :items="statusItems"
            label="状态"
            density="compact"
          />
        </div>
      </section>

      <section class="vault-item-form__section">
        <h3 class="vault-item-form__section-title">标签</h3>
        <div class="vault-item-form__tags-row">
          <v-text-field
            v-model="tagInput"
            label="添加标签"
            density="compact"
            hide-details
            @keyup.enter="addTag"
          />
          <v-btn variant="tonal" size="small" @click="addTag">添加</v-btn>
        </div>
        <div v-if="form.tags.length" class="vault-item-form__chips">
          <v-chip
            v-for="tag in form.tags"
            :key="tag"
            size="small"
            closable
            @click:close="removeTag(tag)"
          >
            {{ tag }}
          </v-chip>
        </div>
      </section>

      <section class="vault-item-form__section">
        <div class="vault-item-form__section-head">
          <h3 class="vault-item-form__section-title">动态字段</h3>
          <v-btn size="small" variant="tonal" prepend-icon="mdi-plus" @click="addField">
            添加字段
          </v-btn>
        </div>

        <div
          v-for="(field, index) in form.fields"
          :key="field.id"
          class="vault-item-form__field"
        >
          <div class="vault-item-form__field-meta">
            <v-text-field
              v-model="field.name"
              label="字段名称"
              density="compact"
              hide-details
            />
            <v-select
              :model-value="field.type"
              :items="fieldTypes"
              label="类型"
              density="compact"
              hide-details
              @update:model-value="(value) => onTypeChange(field, value as FieldType)"
            />
          </div>

          <v-textarea
            v-if="field.type === 'MULTILINE'"
            v-model="field.value"
            label="字段值"
            density="compact"
            rows="2"
            hide-details
            class="mt-2"
          />
          <v-text-field
            v-else
            v-model="field.value"
            label="字段值"
            density="compact"
            hide-details
            type="text"
            class="mt-2"
          />

          <div class="vault-item-form__field-actions">
            <v-checkbox
              v-model="field.required"
              label="必填"
              hide-details
              density="compact"
            />
            <v-checkbox
              v-model="field.sensitive"
              label="敏感"
              hide-details
              density="compact"
            />
            <v-checkbox
              v-model="field.copyable"
              label="可复制"
              hide-details
              density="compact"
            />
            <v-spacer />
            <v-btn
              size="x-small"
              variant="text"
              icon="mdi-arrow-up"
              aria-label="上移"
              @click="moveField(index, -1)"
            />
            <v-btn
              size="x-small"
              variant="text"
              icon="mdi-arrow-down"
              aria-label="下移"
              @click="moveField(index, 1)"
            />
            <v-btn
              size="x-small"
              variant="text"
              color="error"
              icon="mdi-delete-outline"
              aria-label="删除字段"
              @click="removeField(field.id)"
            />
          </div>
        </div>
      </section>

      <section class="vault-item-form__section">
        <h3 class="vault-item-form__section-title">备注</h3>
        <v-textarea
          v-model="form.notes"
          label="备注"
          density="compact"
          rows="2"
          hide-details
        />
      </section>
    </template>
  </div>
</template>

<style scoped>
.vault-item-form {
  display: grid;
  gap: 16px;
}

.vault-item-form__section-title {
  margin: 0 0 8px;
  color: #334155;
  font-size: 0.8125rem;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.vault-item-form__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.vault-item-form__section-head .vault-item-form__section-title {
  margin-bottom: 0;
}

.vault-item-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.vault-item-form__tags-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.vault-item-form__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.vault-item-form__field {
  margin-bottom: 8px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fafbfc;
}

.vault-item-form__field:last-child {
  margin-bottom: 0;
}

.vault-item-form__field-meta {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 8px;
}

.vault-item-form__field-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 8px;
  align-items: center;
  margin-top: 4px;
}

@media (max-width: 720px) {
  .vault-item-form__grid,
  .vault-item-form__field-meta {
    grid-template-columns: 1fr;
  }
}
</style>
