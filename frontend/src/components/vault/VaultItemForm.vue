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
  /** 新建/编辑均可留空有效期，表示永久有效 */
  mode?: 'create' | 'edit'
}>()

const fieldTypes = fieldTypeItems
const accountTypeItems = fieldTypeItems.filter((item) => (
  item.value === 'TEXT' || item.value === 'EMAIL' || item.value === 'PHONE'
))

const accountField = computed(() =>
  form.value.fields.find((field) => field.systemKey === 'account')!,
)

const passwordField = computed(() =>
  form.value.fields.find((field) => field.systemKey === 'password')!,
)

/** 账号/密码 + 模板标记必填的字段 */
const requiredFields = computed(() =>
  form.value.fields.filter((field) => Boolean(field.systemKey) || field.required),
)

/** 未标记必填的扩展字段 */
const customFields = computed(() =>
  form.value.fields.filter((field) => !field.systemKey && !field.required),
)

function syncFieldOrders(required: VaultField[], customs: VaultField[]) {
  form.value.fields = [...required, ...customs].map((field, order) => ({ ...field, order }))
}

function addField() {
  const required = requiredFields.value
  const customs = [
    ...customFields.value,
    {
      id: crypto.randomUUID(),
      name: `字段 ${customFields.value.length + 1}`,
      type: 'TEXT' as const,
      value: '',
      required: false,
      sensitive: false,
      copyable: true,
      hint: '',
      order: 0,
    },
  ]
  syncFieldOrders(required, customs)
}

function removeField(id: string) {
  const target = form.value.fields.find((field) => field.id === id)
  if (!target || target.systemKey || target.required) return
  syncFieldOrders(
    requiredFields.value,
    customFields.value.filter((field) => field.id !== id),
  )
}

function moveField(id: string, delta: number) {
  const customs = [...customFields.value]
  const index = customs.findIndex((field) => field.id === id)
  const target = index + delta
  if (index < 0 || target < 0 || target >= customs.length) return
  const [item] = customs.splice(index, 1)
  customs.splice(target, 0, item)
  syncFieldOrders(requiredFields.value, customs)
}

function onTypeChange(field: VaultField, type: FieldType) {
  if (field.systemKey === 'password') return
  if (
    field.systemKey === 'account'
    && type !== 'TEXT'
    && type !== 'EMAIL'
    && type !== 'PHONE'
  ) {
    return
  }
  field.type = type
  if (type === 'PASSWORD') {
    field.sensitive = true
    field.copyable = true
  }
  if (type === 'URL' || type === 'PHONE') {
    field.copyable = true
  }
}

function fieldTone(type: FieldType | string): string {
  if (type === 'PASSWORD') return 'password'
  if (type === 'EMAIL') return 'email'
  if (type === 'URL') return 'url'
  if (type === 'PHONE') return 'phone'
  return 'text'
}

function valueLabel(type: FieldType | string): string {
  if (type === 'URL') return '网址'
  if (type === 'PHONE') return '手机号'
  if (type === 'EMAIL') return '邮箱'
  return '字段值'
}

function valuePlaceholder(type: FieldType | string): string | undefined {
  if (type === 'URL') return 'https://example.com'
  if (type === 'PHONE') return '例如 13800138000'
  if (type === 'EMAIL') return 'name@example.com'
  return undefined
}
</script>

<template>
  <div class="vault-item-form">
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-2" height="2" />

    <template v-if="!loading">
      <section class="vault-item-form__section vault-item-form__section--basic">
        <header class="vault-item-form__section-head">
          <span class="vault-item-form__section-icon" aria-hidden="true">
            <v-icon icon="mdi-card-account-details-outline" size="14" />
          </span>
          <div>
            <h3 class="vault-item-form__section-title">基本信息</h3>
            <p class="vault-item-form__section-hint">名称、平台与渠道</p>
          </div>
        </header>

        <div class="vault-item-form__grid">
          <v-text-field
            v-model="form.name"
            class="vault-item-form__control"
            label="记录名称"
            density="compact"
            hide-details="auto"
            required
          />
          <v-text-field
            v-model="form.platform"
            class="vault-item-form__control"
            label="所属平台"
            density="compact"
            hide-details="auto"
            required
          />
          <v-text-field
            v-model="form.channel"
            class="vault-item-form__control"
            label="渠道名"
            density="compact"
            hide-details="auto"
            placeholder="自行注册 / 朋友分享"
          />
          <v-text-field
            v-model="form.channelUrl"
            class="vault-item-form__control"
            label="渠道网址"
            density="compact"
            hide-details="auto"
            placeholder="https://"
          />
          <v-text-field
            class="vault-item-form__control vault-item-form__control--span"
            label="有效期"
            type="date"
            density="compact"
            hide-details="auto"
            :model-value="form.expiresAt ?? ''"
            placeholder="留空 = 永久有效"
            clearable
            @update:model-value="form.expiresAt = $event || null"
          />
        </div>
      </section>

      <section class="vault-item-form__section vault-item-form__section--required">
        <header class="vault-item-form__section-head">
          <span class="vault-item-form__section-icon" aria-hidden="true">
            <v-icon icon="mdi-shield-key-outline" size="14" />
          </span>
          <div>
            <h3 class="vault-item-form__section-title">必填字段</h3>
            <p class="vault-item-form__section-hint">账号、密码及模板标记为必填的字段，不可删除</p>
          </div>
        </header>

        <div
          v-for="(field, index) in requiredFields"
          :key="field.id"
          class="vault-item-form__field"
          :class="`vault-item-form__field--${fieldTone(field.type)}`"
          :style="{ '--field-delay': `${index * 40}ms` }"
        >
          <div class="vault-item-form__field-meta">
            <v-text-field
              class="vault-item-form__control"
              :model-value="field.name"
              label="字段名称"
              density="compact"
              hide-details
              readonly
            />
            <v-select
              class="vault-item-form__control"
              :model-value="field.type"
              :items="field.systemKey === 'password'
                ? fieldTypes.filter((item) => item.value === 'PASSWORD')
                : field.systemKey === 'account'
                  ? accountTypeItems
                  : fieldTypes"
              label="类型"
              density="compact"
              hide-details
              :disabled="Boolean(field.systemKey)"
              @update:model-value="(value) => onTypeChange(field, value as FieldType)"
            />
          </div>

          <v-text-field
            v-model="field.value"
            class="vault-item-form__control mt-1"
            :label="valueLabel(field.type)"
            :placeholder="valuePlaceholder(field.type)"
            density="compact"
            hide-details
            type="text"
            :autocomplete="field.systemKey === 'password' ? 'new-password' : 'off'"
          />

          <div class="vault-item-form__field-actions">
            <v-checkbox
              v-model="field.sensitive"
              class="vault-item-form__check"
              label="敏感"
              hide-details
              density="compact"
            />
            <v-checkbox
              v-model="field.copyable"
              class="vault-item-form__check"
              label="可复制"
              hide-details
              density="compact"
            />
          </div>
        </div>
      </section>

      <section class="vault-item-form__section vault-item-form__section--dynamic">
        <header class="vault-item-form__section-head vault-item-form__section-head--row">
          <div class="vault-item-form__section-head-main">
            <span class="vault-item-form__section-icon" aria-hidden="true">
              <v-icon icon="mdi-puzzle-outline" size="14" />
            </span>
            <div>
              <h3 class="vault-item-form__section-title">动态字段</h3>
              <p class="vault-item-form__section-hint">模板未标必填的字段，可按需增删</p>
            </div>
          </div>
          <v-btn
            class="vault-item-form__add-btn"
            size="small"
            variant="flat"
            color="primary"
            prepend-icon="mdi-plus"
            @click="addField"
          >
            添加字段
          </v-btn>
        </header>

        <p v-if="!customFields.length" class="vault-item-form__empty-fields">
          暂无扩展字段，点击「添加字段」补充。
        </p>

        <div
          v-for="(field, index) in customFields"
          :key="field.id"
          class="vault-item-form__field"
          :class="`vault-item-form__field--${fieldTone(field.type)}`"
          :style="{ '--field-delay': `${(index + 2) * 40}ms` }"
        >
          <div class="vault-item-form__field-meta">
            <v-text-field
              v-model="field.name"
              class="vault-item-form__control"
              label="字段名称"
              density="compact"
              hide-details
            />
            <v-select
              class="vault-item-form__control"
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
            class="vault-item-form__control mt-1"
            :label="valueLabel(field.type)"
            :placeholder="valuePlaceholder(field.type)"
            density="compact"
            hide-details
            type="text"
          />

          <div class="vault-item-form__field-actions">
            <v-checkbox
              v-model="field.sensitive"
              class="vault-item-form__check"
              label="敏感"
              hide-details
              density="compact"
            />
            <v-checkbox
              v-model="field.copyable"
              class="vault-item-form__check"
              label="可复制"
              hide-details
              density="compact"
            />
            <v-spacer />
            <v-btn
              class="vault-item-form__icon-btn"
              size="x-small"
              variant="text"
              icon="mdi-arrow-up"
              aria-label="上移"
              @click="moveField(field.id, -1)"
            />
            <v-btn
              class="vault-item-form__icon-btn"
              size="x-small"
              variant="text"
              icon="mdi-arrow-down"
              aria-label="下移"
              @click="moveField(field.id, 1)"
            />
            <v-btn
              class="vault-item-form__icon-btn"
              size="x-small"
              variant="text"
              color="error"
              icon="mdi-delete-outline"
              aria-label="删除字段"
              @click="removeField(field.id)"
            />
          </div>
          <p v-if="field.type === 'URL'" class="vault-item-form__tip">
            网址在详情中显示为可点击链接。
          </p>
          <p v-else-if="field.type === 'PHONE'" class="vault-item-form__tip">
            手机号在详情中可一键拨打。
          </p>
        </div>
      </section>

      <section class="vault-item-form__section vault-item-form__section--notes">
        <header class="vault-item-form__section-head">
          <span class="vault-item-form__section-icon" aria-hidden="true">
            <v-icon icon="mdi-note-text-outline" size="14" />
          </span>
          <div>
            <h3 class="vault-item-form__section-title">备注</h3>
            <p class="vault-item-form__section-hint">常用补充说明，可选填写</p>
          </div>
        </header>
        <v-textarea
          v-model="form.notes"
          class="vault-item-form__control"
          label="备注"
          density="compact"
          rows="2"
          hide-details
          auto-grow
          :max-rows="4"
        />
      </section>
    </template>
  </div>
</template>

<style scoped>
.vault-item-form {
  display: grid;
  gap: 12px;
}

.vault-item-form__section {
  padding: 10px 12px 12px;
  border: 1px solid var(--os-border);
  border-radius: var(--os-radius-card);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 96%), rgb(255 255 255 / 100%)),
    var(--os-surface);
  box-shadow: inset 3px 0 0 var(--section-accent, var(--os-primary-border));
  transition:
    border-color var(--os-duration-fast) ease,
    box-shadow var(--os-duration-base) ease,
    transform var(--os-duration-base) ease;
}

.vault-item-form__section:hover {
  border-color: #d6e0ff;
  box-shadow:
    inset 3px 0 0 var(--section-accent, var(--os-primary)),
    var(--os-shadow-1);
}

.vault-item-form__section--basic { --section-accent: #3b82f6; }
.vault-item-form__section--required { --section-accent: #155eef; }
.vault-item-form__section--dynamic { --section-accent: #0ea5a4; }
.vault-item-form__section--notes { --section-accent: #7c6af2; }

.vault-item-form__section-head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}

.vault-item-form__section-head--row {
  align-items: center;
  justify-content: space-between;
}

.vault-item-form__section-head-main {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.vault-item-form__section-icon {
  display: inline-grid;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 7px;
  color: #fff;
  background: var(--section-accent, var(--os-primary));
  box-shadow: 0 4px 10px -4px color-mix(in srgb, var(--section-accent, var(--os-primary)) 55%, transparent);
}

.vault-item-form__section-title {
  margin: 0;
  color: var(--os-text-title);
  font-size: 0.8125rem;
  font-weight: 650;
  letter-spacing: 0.01em;
  line-height: 1.25;
}

.vault-item-form__section-hint {
  margin: 2px 0 0;
  color: var(--os-text-muted);
  font-size: 0.6875rem;
  line-height: 1.35;
}

.vault-item-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 10px;
}

.vault-item-form__control--span {
  grid-column: 1 / -1;
}

.vault-item-form__empty-fields {
  margin: 0;
  padding: 10px 12px;
  border: 1px dashed color-mix(in srgb, var(--section-accent, var(--os-primary)) 45%, var(--os-border));
  border-radius: 8px;
  color: var(--os-text-muted);
  font-size: 0.75rem;
  background: color-mix(in srgb, var(--section-accent, var(--os-primary)) 6%, #fff);
}

.vault-item-form__field {
  --field-accent: #94a3b8;
  margin-bottom: 6px;
  padding: 8px 10px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--field-accent) 10%, #fff) 0, #fff 28%);
  box-shadow: inset 2px 0 0 var(--field-accent);
  animation: vault-field-in 280ms ease both;
  animation-delay: var(--field-delay, 0ms);
  transition:
    border-color var(--os-duration-fast) ease,
    box-shadow var(--os-duration-fast) ease,
    transform var(--os-duration-fast) ease,
    background var(--os-duration-fast) ease;
}

.vault-item-form__field:last-child {
  margin-bottom: 0;
}

.vault-item-form__field:hover {
  border-color: color-mix(in srgb, var(--field-accent) 40%, var(--os-border));
  transform: translateY(-1px);
  box-shadow:
    inset 2px 0 0 var(--field-accent),
    0 6px 14px -8px color-mix(in srgb, var(--field-accent) 45%, transparent);
}

.vault-item-form__field--text { --field-accent: #64748b; }
.vault-item-form__field--password { --field-accent: #155eef; }
.vault-item-form__field--email { --field-accent: #0ea5a4; }
.vault-item-form__field--url { --field-accent: #7c6af2; }
.vault-item-form__field--phone { --field-accent: #f59e0b; }

.vault-item-form__field-meta {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 6px;
}

.vault-item-form__field-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0 4px;
  align-items: center;
  margin-top: 2px;
  min-height: 28px;
}

.vault-item-form__tip {
  margin: 2px 0 0;
  color: color-mix(in srgb, var(--field-accent) 75%, var(--os-text-muted));
  font-size: 0.6875rem;
}

.vault-item-form__add-btn {
  flex: 0 0 auto;
  min-height: 30px !important;
  font-size: 0.75rem !important;
  letter-spacing: 0;
  transition: transform var(--os-duration-fast) ease, box-shadow var(--os-duration-fast) ease;
}

.vault-item-form__add-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 14px -6px rgb(21 94 239 / 45%);
}

.vault-item-form__icon-btn {
  transition: transform var(--os-duration-fast) ease, background-color var(--os-duration-fast) ease;
}

.vault-item-form__icon-btn:hover {
  transform: scale(1.08);
}

/* 控件高度再压一档，表单更紧凑 */
.vault-item-form :deep(.vault-item-form__control .v-field) {
  --v-field-padding-start: 10px;
  --v-field-padding-end: 10px;
  font-size: 0.8125rem;
}

.vault-item-form :deep(.vault-item-form__control .v-field__input) {
  min-height: 34px;
  padding-top: 4px;
  padding-bottom: 4px;
}

.vault-item-form :deep(.vault-item-form__control .v-field-label) {
  font-size: 0.75rem;
}

.vault-item-form :deep(.vault-item-form__check) {
  margin-inline-start: 0;
  --v-selection-control-size: 28px;
}

.vault-item-form :deep(.vault-item-form__check .v-label) {
  font-size: 0.75rem;
  opacity: 0.9;
}

.vault-item-form :deep(.v-textarea .v-field__input) {
  min-height: 56px;
  padding-top: 6px;
}

@keyframes vault-field-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 720px) {
  .vault-item-form__grid,
  .vault-item-form__field-meta {
    grid-template-columns: 1fr;
  }

  .vault-item-form__section-head--row {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .vault-item-form__field,
  .vault-item-form__section,
  .vault-item-form__add-btn,
  .vault-item-form__icon-btn {
    animation: none !important;
    transition: none !important;
  }

  .vault-item-form__field:hover,
  .vault-item-form__add-btn:hover,
  .vault-item-form__icon-btn:hover {
    transform: none;
  }
}
</style>
