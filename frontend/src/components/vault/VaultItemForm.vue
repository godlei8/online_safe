<script setup lang="ts">
import { computed } from 'vue'
import {
  fieldTypeItems,
  type FieldType,
  type VaultField,
  type VaultItemPayload,
} from '@/domain/vaultPayload'

const form = defineModel<VaultItemPayload>({ required: true })

defineProps<{
  loading?: boolean
  errorMessage?: string
  /** 新建时有效期必填；编辑时可改有效期，不可改状态 */
  mode?: 'create' | 'edit'
}>()

const fieldTypes = fieldTypeItems
const accountTypeItems = fieldTypeItems.filter((item) => item.value === 'TEXT' || item.value === 'EMAIL')

const accountField = computed(() =>
  form.value.fields.find((field) => field.systemKey === 'account')!,
)

const passwordField = computed(() =>
  form.value.fields.find((field) => field.systemKey === 'password')!,
)

const requiredFields = computed(() => [accountField.value, passwordField.value].filter(Boolean))

/** 动态字段默认空；仅用户点击「添加字段」后才有 */
const customFields = computed(() =>
  form.value.fields.filter((field) => !field.systemKey),
)

function addField() {
  form.value.fields.push({
    id: crypto.randomUUID(),
    name: `字段 ${customFields.value.length + 1}`,
    type: 'TEXT',
    value: '',
    required: false,
    sensitive: false,
    copyable: true,
    hint: '',
    order: form.value.fields.length,
  })
}

function removeField(id: string) {
  form.value.fields = form.value.fields
    .filter((field) => field.systemKey || field.id !== id)
    .map((field, index) => ({ ...field, order: index }))
}

function moveField(id: string, delta: number) {
  const customs = customFields.value
  const index = customs.findIndex((field) => field.id === id)
  const target = index + delta
  if (index < 0 || target < 0 || target >= customs.length) return
  const next = [...customs]
  const [item] = next.splice(index, 1)
  next.splice(target, 0, item)
  form.value.fields = [
    accountField.value,
    passwordField.value,
    ...next.map((field, order) => ({ ...field, order: order + 2 })),
  ]
}

function onTypeChange(field: VaultField, type: FieldType) {
  if (field.systemKey === 'password') return
  if (field.systemKey === 'account' && type !== 'TEXT' && type !== 'EMAIL') return
  field.type = type
  if (type === 'PASSWORD') {
    field.sensitive = true
    field.copyable = true
  }
  if (type === 'URL') {
    field.copyable = true
  }
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
        </div>
        <v-text-field
          v-model="form.channel"
          class="mt-2"
          label="获取渠道"
          density="compact"
          hint="例如：自行注册、朋友分享、工作发放"
          persistent-hint
        />
        <v-text-field
          class="mt-2"
          label="有效期"
          type="date"
          density="compact"
          :model-value="form.expiresAt ?? ''"
          :hint="mode === 'create' ? '到期后状态将自动变为「过期」' : '可调整有效期；到期后自动变为「过期」'"
          persistent-hint
          :clearable="mode === 'edit'"
          @update:model-value="form.expiresAt = $event || null"
        />
      </section>

      <section class="vault-item-form__section">
        <h3 class="vault-item-form__section-title">必填字段</h3>
        <p class="vault-item-form__section-hint">账号与密码为固定必填，样式与动态字段一致，不可删除。</p>

        <div
          v-for="field in requiredFields"
          :key="field.id"
          class="vault-item-form__field"
        >
          <div class="vault-item-form__field-meta">
            <v-text-field
              :model-value="field.name"
              label="字段名称"
              density="compact"
              hide-details
              readonly
            />
            <v-select
              :model-value="field.type"
              :items="field.systemKey === 'password' ? fieldTypes.filter((item) => item.value === 'PASSWORD') : accountTypeItems"
              label="类型"
              density="compact"
              hide-details
              :disabled="field.systemKey === 'password'"
              @update:model-value="(value) => onTypeChange(field, value as FieldType)"
            />
          </div>

          <v-text-field
            v-model="field.value"
            class="mt-2"
            label="字段值"
            density="compact"
            hide-details
            type="text"
            :autocomplete="field.systemKey === 'password' ? 'new-password' : 'off'"
          />

          <div class="vault-item-form__field-actions">
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
          </div>
        </div>
      </section>

      <section class="vault-item-form__section">
        <div class="vault-item-form__section-head">
          <div>
            <h3 class="vault-item-form__section-title">动态字段</h3>
            <p class="vault-item-form__section-hint">默认无字段，点击「添加字段」后按需补充</p>
          </div>
          <v-btn size="small" variant="tonal" prepend-icon="mdi-plus" @click="addField">
            添加字段
          </v-btn>
        </div>

        <p v-if="!customFields.length" class="vault-item-form__empty-fields">
          暂无扩展字段，可点击「添加字段」按需补充。
        </p>

        <div
          v-for="field in customFields"
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

          <v-text-field
            v-model="field.value"
            class="mt-2"
            :label="field.type === 'URL' ? '网址' : '字段值'"
            :placeholder="field.type === 'URL' ? 'https://example.com' : undefined"
            density="compact"
            hide-details
            type="text"
          />

          <div class="vault-item-form__field-actions">
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
              @click="moveField(field.id, -1)"
            />
            <v-btn
              size="x-small"
              variant="text"
              icon="mdi-arrow-down"
              aria-label="下移"
              @click="moveField(field.id, 1)"
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
          <p v-if="field.type === 'URL'" class="vault-item-form__url-tip">
            网址类型在详情中会显示为可点击链接。
          </p>
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
  color: var(--os-text-title);
  font-size: 0.8125rem;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.vault-item-form__section-hint {
  margin: -2px 0 10px;
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.4;
}

.vault-item-form__section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.vault-item-form__section-head .vault-item-form__section-title {
  margin-bottom: 4px;
}

.vault-item-form__section-head .vault-item-form__section-hint {
  margin: 0;
}

.vault-item-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.vault-item-form__empty-fields {
  margin: 0;
  padding: 12px;
  border: 1px dashed var(--os-border);
  border-radius: var(--os-radius-card);
  color: var(--os-text-muted);
  font-size: 0.8125rem;
  background: var(--os-bg);
}

.vault-item-form__field {
  margin-bottom: 8px;
  padding: 10px 12px;
  border: 1px solid var(--os-border);
  border-radius: var(--os-radius-card);
  background: var(--os-bg);
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

.vault-item-form__url-tip {
  margin: 4px 0 0;
  color: var(--os-text-muted);
  font-size: 0.75rem;
}

@media (max-width: 720px) {
  .vault-item-form__grid,
  .vault-item-form__field-meta {
    grid-template-columns: 1fr;
  }
}
</style>
