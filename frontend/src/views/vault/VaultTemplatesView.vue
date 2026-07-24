<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { UserSystemTemplate } from '@/api/systemTemplates'
import {
  fieldTypeItems,
  fieldTypeLabel,
  type PrivateTemplatePayload,
} from '@/domain/vaultPayload'
import OsConfirmDialog from '@/components/OsConfirmDialog.vue'
import { useOsToast } from '@/composables/useOsToast'
import { useVaultItemEditor } from '@/composables/useVaultItemEditor'
import { useVaultStore } from '@/stores/vault'

type TemplateTab = 'system' | 'private'

type ViewingTemplate = {
  id: string
  source: TemplateTab
  name: string
  platform: string
  channel: string
  channelUrl: string
  fields: Array<{
    id: string
    name: string
    type: string
    required: boolean
    sensitive: boolean
    copyable: boolean
  }>
}

const vault = useVaultStore()
const editor = useVaultItemEditor()
const toast = useOsToast()
const loading = ref(true)
const activeTab = ref<TemplateTab>('private')
const dialog = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)
const viewOpen = ref(false)
const viewing = ref<ViewingTemplate | null>(null)
const form = ref<PrivateTemplatePayload>({
  name: '',
  platform: '',
  channel: '',
  channelUrl: '',
  fields: [],
})

const tabHint = computed(() => (
  activeTab.value === 'system'
    ? '可查看字段结构，或使用模板创建记录。系统模板由管理员维护。'
    : '仅自己可见，可自由增删改；只保存字段结构，不保存真实账密值。'
))

const headingCount = computed(() => (
  activeTab.value === 'system'
    ? `${vault.systemTemplates.length} 个系统模板`
    : `${vault.templates.length} 个个人模板`
))

const editorTitle = computed(() => (editingId.value ? '编辑个人模板' : '新建个人模板'))

onMounted(async () => {
  try {
    await Promise.all([vault.loadTemplates(), vault.loadSystemTemplates()])
  } finally {
    loading.value = false
  }
})

function openCreate() {
  activeTab.value = 'private'
  editingId.value = null
  form.value = {
    name: '',
    platform: '',
    channel: '',
    channelUrl: '',
    fields: [
      {
        id: crypto.randomUUID(),
        name: '账号',
        type: 'TEXT',
        required: true,
        sensitive: false,
        copyable: true,
        hint: '',
        order: 0,
        systemKey: 'account',
      },
      {
        id: crypto.randomUUID(),
        name: '密码',
        type: 'PASSWORD',
        required: true,
        sensitive: true,
        copyable: true,
        hint: '',
        order: 1,
        systemKey: 'password',
      },
    ],
  }
  dialog.value = true
}

function openEditPrivate(id: string) {
  const item = vault.templates.find((template) => template.envelope.id === id)
  if (!item) return
  activeTab.value = 'private'
  editingId.value = id
  form.value = JSON.parse(JSON.stringify(item.payload)) as PrivateTemplatePayload
  dialog.value = true
}

function addField() {
  form.value.fields.push({
    id: crypto.randomUUID(),
    name: `字段 ${form.value.fields.length + 1}`,
    type: 'TEXT',
    required: false,
    sensitive: false,
    copyable: true,
    hint: '',
    order: form.value.fields.length,
  })
}

function removeTemplateField(id: string) {
  const target = form.value.fields.find((field) => field.id === id)
  if (!target || target.systemKey) return
  form.value.fields = form.value.fields
    .filter((field) => field.id !== id)
    .map((field, index) => ({ ...field, order: index }))
}

async function save() {
  if (!form.value.name.trim()) {
    toast.error('请填写模板名称')
    return
  }
  saving.value = true
  try {
    await vault.saveTemplate(editingId.value, form.value)
    dialog.value = false
    editingId.value = null
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

const deleteConfirmOpen = ref(false)
const deleteTargetId = ref<string | null>(null)
const deleteTargetName = ref('')
const deleting = ref(false)

function askRemove(id: string, name: string) {
  deleteTargetId.value = id
  deleteTargetName.value = name
  deleteConfirmOpen.value = true
}

async function confirmRemove() {
  if (!deleteTargetId.value) return
  deleting.value = true
  try {
    await vault.deleteTemplate(deleteTargetId.value)
    deleteConfirmOpen.value = false
    deleteTargetId.value = null
    deleteTargetName.value = ''
  } finally {
    deleting.value = false
  }
}

function createFromPrivate(id: string) {
  editor.openCreate({ templateId: id, templateSource: 'private' })
}

function createFromSystem(id: string) {
  editor.openCreate({ templateId: id, templateSource: 'system' })
}

function openViewSystem(item: UserSystemTemplate) {
  viewing.value = {
    id: item.id,
    source: 'system',
    name: item.name,
    platform: item.platform,
    channel: item.channel,
    channelUrl: item.channelUrl,
    fields: item.fields,
  }
  viewOpen.value = true
}

function openViewPrivate(id: string) {
  const item = vault.templates.find((template) => template.envelope.id === id)
  if (!item) return
  viewing.value = {
    id: item.envelope.id,
    source: 'private',
    name: item.payload.name,
    platform: item.payload.platform,
    channel: item.payload.channel,
    channelUrl: item.payload.channelUrl,
    fields: item.payload.fields,
  }
  viewOpen.value = true
}

function useViewingTemplate() {
  if (!viewing.value) return
  if (viewing.value.source === 'system') {
    createFromSystem(viewing.value.id)
  } else {
    createFromPrivate(viewing.value.id)
  }
  viewOpen.value = false
}

function fieldFlags(field: { required: boolean; sensitive: boolean; copyable: boolean }) {
  const flags: string[] = []
  if (field.required) flags.push('必填')
  if (field.sensitive) flags.push('敏感')
  if (field.copyable) flags.push('可复制')
  return flags.join(' · ') || '无特殊标记'
}
</script>

<template>
  <section class="vault-content vault-templates-panel vault-page" aria-labelledby="templates-title">
    <div class="vault-page__chrome">
    <div class="vault-title-row">
      <div>
        <p class="vault-eyebrow">模板中心</p>
        <div class="vault-title-row__heading">
          <h1 id="templates-title">模板库</h1>
          <span>{{ headingCount }}</span>
        </div>
      </div>
      <v-btn
        v-if="activeTab === 'private'"
        color="primary"
        class="vault-new-record"
        size="small"
        prepend-icon="mdi-plus"
        @click="openCreate"
      >
        新建个人模板
      </v-btn>
    </div>

    <div class="vault-templates-tabs" role="tablist" aria-label="模板分类">
      <button
        type="button"
        role="tab"
        class="vault-templates-tabs__item"
        :class="{ 'vault-templates-tabs__item--active': activeTab === 'private' }"
        :aria-selected="activeTab === 'private'"
        @click="activeTab = 'private'"
      >
        我的模板
        <span class="vault-templates-tabs__count">{{ vault.templates.length }}</span>
      </button>
      <button
        type="button"
        role="tab"
        class="vault-templates-tabs__item"
        :class="{ 'vault-templates-tabs__item--active': activeTab === 'system' }"
        :aria-selected="activeTab === 'system'"
        @click="activeTab = 'system'"
      >
        系统模板
        <span class="vault-templates-tabs__count">{{ vault.systemTemplates.length }}</span>
      </button>
    </div>

    <div class="vault-privacy-note" role="note">
      <v-icon icon="mdi-information-outline" size="16" />
      <span>{{ tabHint }}</span>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" height="2" />
    </div>

    <div class="vault-page__body">
    <template v-if="!loading && activeTab === 'system'">
      <div v-if="vault.systemTemplates.length" class="vault-template-grid">
        <article
          v-for="item in vault.systemTemplates"
          :key="item.id"
          class="vault-template-card vault-template-card--system"
        >
          <div class="vault-template-card__heading">
            <v-avatar size="30" rounded="lg" class="vault-template-card__icon">
              <v-icon icon="mdi-earth" size="16" color="primary" />
            </v-avatar>
            <div class="vault-template-card__title">
              <strong>{{ item.name }}</strong>
              <span class="vault-template-card__platform">
                {{ item.platform || '未指定平台' }}
                <template v-if="item.channel"> · {{ item.channel }}</template>
                · {{ item.fields.length }} 字段
              </span>
            </div>
            <span class="vault-template-card__badge">系统</span>
          </div>

          <p class="vault-template-card__fields">
            <template v-for="(field, index) in item.fields.slice(0, 5)" :key="field.id">
              <span v-if="index > 0" class="vault-template-card__dot">·</span>
              <span>{{ field.name }}</span>
            </template>
            <template v-if="item.fields.length > 5">
              <span class="vault-template-card__dot">·</span>
              <span class="vault-template-card__more">+{{ item.fields.length - 5 }}</span>
            </template>
          </p>

          <div class="vault-template-card__actions">
            <v-btn
              class="vault-template-card__btn"
              size="x-small"
              variant="text"
              color="primary"
              density="compact"
              prepend-icon="mdi-eye-outline"
              @click="openViewSystem(item)"
            >
              查看
            </v-btn>
            <v-btn
              class="vault-template-card__btn vault-template-card__btn--primary"
              size="x-small"
              variant="flat"
              color="primary"
              density="compact"
              prepend-icon="mdi-plus"
              @click="createFromSystem(item.id)"
            >
              使用
            </v-btn>
          </div>
        </article>
      </div>
      <v-alert v-else type="info" variant="tonal" density="compact">
        暂无已发布的系统模板，请稍后或联系管理员。
      </v-alert>
    </template>

    <template v-if="!loading && activeTab === 'private'">
      <div v-if="vault.templates.length" class="vault-template-grid">
        <article
          v-for="item in vault.templates"
          :key="item.envelope.id"
          class="vault-template-card"
        >
          <div class="vault-template-card__heading">
            <v-avatar size="30" rounded="lg" class="vault-template-card__icon">
              <v-icon icon="mdi-account-box-outline" size="16" color="primary" />
            </v-avatar>
            <div class="vault-template-card__title">
              <strong>{{ item.payload.name }}</strong>
              <span class="vault-template-card__platform">
                {{ item.payload.platform || '未指定平台' }}
                <template v-if="item.payload.channel"> · {{ item.payload.channel }}</template>
                · {{ item.payload.fields.length }} 字段
              </span>
            </div>
            <span class="vault-template-card__badge vault-template-card__badge--private">个人</span>
          </div>

          <p class="vault-template-card__fields">
            <template v-for="(field, index) in item.payload.fields.slice(0, 5)" :key="field.id">
              <span v-if="index > 0" class="vault-template-card__dot">·</span>
              <span>{{ field.name }}</span>
            </template>
            <template v-if="item.payload.fields.length > 5">
              <span class="vault-template-card__dot">·</span>
              <span class="vault-template-card__more">+{{ item.payload.fields.length - 5 }}</span>
            </template>
          </p>

          <div class="vault-template-card__actions">
            <v-btn
              class="vault-template-card__btn"
              size="x-small"
              variant="text"
              color="primary"
              density="compact"
              prepend-icon="mdi-pencil-outline"
              @click="openEditPrivate(item.envelope.id)"
            >
              编辑
            </v-btn>
            <v-btn
              class="vault-template-card__btn"
              size="x-small"
              variant="text"
              color="primary"
              density="compact"
              prepend-icon="mdi-eye-outline"
              @click="openViewPrivate(item.envelope.id)"
            >
              查看
            </v-btn>
            <v-btn
              class="vault-template-card__btn vault-template-card__btn--primary"
              size="x-small"
              variant="flat"
              color="primary"
              density="compact"
              prepend-icon="mdi-plus"
              @click="createFromPrivate(item.envelope.id)"
            >
              使用
            </v-btn>
            <v-btn
              class="vault-template-card__btn vault-template-card__btn--danger"
              size="x-small"
              variant="text"
              color="error"
              density="compact"
              prepend-icon="mdi-delete-outline"
              @click="askRemove(item.envelope.id, item.payload.name)"
            >
              删除
            </v-btn>
          </div>
        </article>
      </div>
      <v-alert v-else type="info" variant="tonal" density="compact">
        还没有个人模板。
        <v-btn
          class="ms-1 vault-template-card__btn"
          size="x-small"
          variant="text"
          color="primary"
          prepend-icon="mdi-plus"
          @click="openCreate"
        >
          立即新建
        </v-btn>
      </v-alert>
    </template>
    </div>

    <OsConfirmDialog
      v-model="deleteConfirmOpen"
      variant="danger"
      title="确认删除模板？"
      :message="`将删除个人模板「${deleteTargetName || '未命名'}」。此操作不可恢复，是否继续？`"
      confirm-text="确认删除"
      :loading="deleting"
      @confirm="confirmRemove"
    />

    <v-dialog v-model="viewOpen" max-width="520" scrollable>
      <v-card v-if="viewing" class="os-form-dialog">
        <v-card-title class="os-form-dialog__title">
          <div class="os-form-dialog__heading">
            <span class="os-form-dialog__mark" aria-hidden="true">
              <v-icon icon="mdi-eye-outline" size="18" />
            </span>
            <div>
              <p class="os-form-dialog__eyebrow">{{ viewing.source === 'system' ? '系统模板' : '个人模板' }}</p>
              <h2>{{ viewing.name }}</h2>
            </div>
          </div>
          <v-btn icon="mdi-close" variant="text" size="small" aria-label="关闭" @click="viewOpen = false" />
        </v-card-title>
        <v-card-text class="os-form-dialog__body">
          <section class="os-form-dialog__section os-form-dialog__section--basic">
            <header class="os-form-dialog__section-head">
              <span class="os-form-dialog__section-icon" aria-hidden="true">
                <v-icon icon="mdi-information-outline" size="14" />
              </span>
              <div>
                <h3 class="os-form-dialog__section-title">基本信息</h3>
                <p class="os-form-dialog__section-hint">仅展示结构，不含真实账密</p>
              </div>
            </header>
            <dl class="vault-template-view-meta">
              <div><dt>平台</dt><dd>{{ viewing.platform || '—' }}</dd></div>
              <div><dt>渠道</dt><dd>{{ viewing.channel || '—' }}</dd></div>
              <div class="vault-template-view-meta__span">
                <dt>渠道网址</dt>
                <dd>{{ viewing.channelUrl || '—' }}</dd>
              </div>
            </dl>
          </section>
          <section class="os-form-dialog__section os-form-dialog__section--fields">
            <header class="os-form-dialog__section-head">
              <span class="os-form-dialog__section-icon" aria-hidden="true">
                <v-icon icon="mdi-form-select" size="14" />
              </span>
              <div>
                <h3 class="os-form-dialog__section-title">字段结构</h3>
                <p class="os-form-dialog__section-hint">共 {{ viewing.fields.length }} 个字段</p>
              </div>
            </header>
            <ul class="vault-template-view-fields">
              <li v-for="field in viewing.fields" :key="field.id">
                <strong>{{ field.name }}</strong>
                <span>{{ fieldTypeLabel(String(field.type)) }}</span>
                <small>{{ fieldFlags(field) }}</small>
              </li>
            </ul>
          </section>
        </v-card-text>
        <v-card-actions class="os-form-dialog__actions">
          <v-spacer />
          <v-btn
            v-if="viewing.source === 'private'"
            variant="text"
            size="small"
            prepend-icon="mdi-pencil-outline"
            @click="viewOpen = false; openEditPrivate(viewing.id)"
          >
            编辑
          </v-btn>
          <v-btn variant="text" size="small" @click="viewOpen = false">关闭</v-btn>
          <v-btn
            class="os-form-dialog__save"
            color="primary"
            size="small"
            prepend-icon="mdi-file-plus-outline"
            @click="useViewingTemplate"
          >
            使用模板创建
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="dialog" max-width="640" scrollable persistent>
      <v-card class="os-form-dialog">
        <v-card-title class="os-form-dialog__title">
          <div class="os-form-dialog__heading">
            <span class="os-form-dialog__mark" aria-hidden="true">
              <v-icon :icon="editingId ? 'mdi-pencil-outline' : 'mdi-plus-circle-outline'" size="18" />
            </span>
            <div>
              <p class="os-form-dialog__eyebrow">个人模板</p>
              <h2>{{ editorTitle }}</h2>
            </div>
          </div>
          <v-btn
            icon="mdi-close"
            variant="text"
            size="small"
            aria-label="关闭"
            :disabled="saving"
            @click="dialog = false"
          />
        </v-card-title>

        <v-card-text class="os-form-dialog__body">
          <section class="os-form-dialog__section os-form-dialog__section--basic">
            <header class="os-form-dialog__section-head">
              <span class="os-form-dialog__section-icon" aria-hidden="true">
                <v-icon icon="mdi-card-account-details-outline" size="14" />
              </span>
              <div>
                <h3 class="os-form-dialog__section-title">基本信息</h3>
                <p class="os-form-dialog__section-hint">仅自己可见，不保存真实账密</p>
              </div>
            </header>
            <div class="os-form-dialog__grid">
              <v-text-field
                v-model="form.name"
                class="os-form-dialog__control os-form-dialog__control--span"
                label="模板名称"
                density="compact"
                hide-details="auto"
              />
              <v-text-field
                v-model="form.platform"
                class="os-form-dialog__control"
                label="默认平台"
                density="compact"
                hide-details="auto"
              />
              <v-text-field
                v-model="form.channel"
                class="os-form-dialog__control"
                label="默认渠道名"
                density="compact"
                hide-details="auto"
              />
              <v-text-field
                v-model="form.channelUrl"
                class="os-form-dialog__control os-form-dialog__control--span"
                label="默认渠道网址"
                density="compact"
                hide-details="auto"
              />
            </div>
          </section>

          <section class="os-form-dialog__section os-form-dialog__section--fields">
            <header class="os-form-dialog__section-head os-form-dialog__section-head--row">
              <div class="os-form-dialog__section-head-main">
                <span class="os-form-dialog__section-icon" aria-hidden="true">
                  <v-icon icon="mdi-form-select" size="14" />
                </span>
                <div>
                  <h3 class="os-form-dialog__section-title">字段结构</h3>
                  <p class="os-form-dialog__section-hint">勾选「必填」的字段会进入录入时的必填区</p>
                </div>
              </div>
              <v-btn
                class="os-form-dialog__add-btn"
                size="small"
                variant="flat"
                color="primary"
                prepend-icon="mdi-plus"
                @click="addField"
              >
                添加字段
              </v-btn>
            </header>

            <div
              v-for="(field, index) in form.fields"
              :key="field.id"
              class="os-form-dialog__field"
              :class="`os-form-dialog__field--${field.type === 'PASSWORD' ? 'password' : field.type === 'EMAIL' ? 'email' : field.type === 'URL' ? 'url' : field.type === 'PHONE' ? 'phone' : 'text'}`"
              :style="{ '--field-delay': `${index * 40}ms` }"
            >
              <div class="os-form-dialog__field-meta">
                <v-text-field
                  v-model="field.name"
                  class="os-form-dialog__control"
                  label="名称"
                  density="compact"
                  hide-details
                  :readonly="Boolean(field.systemKey)"
                />
                <v-select
                  v-model="field.type"
                  class="os-form-dialog__control"
                  :items="field.systemKey === 'password'
                    ? fieldTypeItems.filter((item) => item.value === 'PASSWORD')
                    : field.systemKey === 'account'
                      ? fieldTypeItems.filter((item) => item.value === 'TEXT' || item.value === 'EMAIL' || item.value === 'PHONE')
                      : fieldTypeItems"
                  label="类型"
                  density="compact"
                  hide-details
                  :disabled="field.systemKey === 'password'"
                />
              </div>
              <div class="os-form-dialog__field-actions">
                <v-checkbox
                  v-model="field.required"
                  class="os-form-dialog__check"
                  label="必填"
                  hide-details
                  density="compact"
                  :disabled="Boolean(field.systemKey)"
                />
                <v-checkbox v-model="field.sensitive" class="os-form-dialog__check" label="敏感" hide-details density="compact" />
                <v-checkbox v-model="field.copyable" class="os-form-dialog__check" label="可复制" hide-details density="compact" />
                <v-spacer />
                <v-btn
                  v-if="!field.systemKey"
                  size="x-small"
                  variant="text"
                  color="error"
                  icon="mdi-delete-outline"
                  aria-label="删除字段"
                  @click="removeTemplateField(field.id)"
                />
              </div>
            </div>
          </section>
        </v-card-text>

        <v-card-actions class="os-form-dialog__actions">
          <v-spacer />
          <v-btn variant="text" size="small" :disabled="saving" @click="dialog = false">取消</v-btn>
          <v-btn class="os-form-dialog__save" color="primary" size="small" :loading="saving" @click="save">
            保存模板
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </section>
</template>

<style scoped>
.vault-templates-panel :deep(.vault-privacy-note) {
  margin: 10px 0 12px;
}

.vault-templates-tabs {
  display: flex;
  gap: 20px;
  margin: 22px 0 6px;
  border-bottom: 1px solid var(--os-border);
}

.vault-templates-tabs__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 6px 2px 10px;
  border: 0;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  color: var(--os-text-muted);
  background: transparent;
  font: inherit;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
}

.vault-templates-tabs__item--active {
  color: var(--os-primary);
  border-bottom-color: var(--os-primary);
}

.vault-templates-tabs__count {
  display: inline-grid;
  min-width: 22px;
  height: 20px;
  place-items: center;
  padding: 0 6px;
  border-radius: 999px;
  color: var(--os-text-muted);
  background: var(--os-bg);
  font-size: 0.75rem;
  font-variant-numeric: tabular-nums;
}

.vault-templates-tabs__item--active .vault-templates-tabs__count {
  color: var(--os-primary);
  background: var(--os-primary-tint);
}

.vault-template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.vault-template-card {
  display: grid;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--os-border);
  border-radius: var(--os-radius-card);
  background: var(--os-surface);
  box-shadow: var(--os-shadow-1);
  transition:
    border-color var(--os-duration-fast) ease,
    box-shadow var(--os-duration-fast) ease,
    transform var(--os-duration-fast) ease;
}

.vault-template-card:hover {
  border-color: #d6e0ff;
  box-shadow: var(--os-shadow-2);
  transform: translateY(-1px);
}

.vault-template-card--system {
  border-color: #d6e0ff;
  background: linear-gradient(160deg, #f8faff 0%, #fff 48%);
}

.vault-template-card__heading {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
}

.vault-template-card__icon {
  background: var(--os-primary-tint) !important;
}

.vault-template-card__title {
  min-width: 0;
}

.vault-template-card__title strong {
  display: block;
  overflow: hidden;
  color: var(--os-text-title);
  font-size: 0.9375rem;
  font-weight: 650;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vault-template-card__platform {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vault-template-card__badge {
  flex: 0 0 auto;
  margin-top: 2px;
  padding: 3px 8px;
  border-radius: 999px;
  color: var(--os-primary);
  background: var(--os-primary-tint);
  font-size: 0.6875rem;
  font-weight: 650;
  line-height: 1.4;
}

.vault-template-card__badge--private {
  color: #0f766e;
  background: rgb(14 165 164 / 12%);
}

.vault-template-card__fields {
  display: flex;
  flex-wrap: wrap;
  gap: 0 4px;
  align-items: center;
  min-width: 0;
  margin: 0;
  padding: 10px 0 0 40px;
  border-top: 1px solid var(--os-border);
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.5;
}

.vault-template-card__dot {
  margin: 0 2px;
  color: #c5cddd;
}

.vault-template-card__more {
  color: var(--os-primary);
  font-weight: 600;
}

.vault-template-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  justify-content: flex-start;
  padding-top: 2px;
  padding-left: 0;
}

.vault-template-card__btn {
  min-width: auto !important;
  min-height: 24px !important;
  height: 24px !important;
  padding-inline: 6px !important;
  font-size: 0.6875rem !important;
  letter-spacing: 0;
}

.vault-template-card__btn .v-icon {
  font-size: 14px !important;
  margin-inline-end: 1px !important;
}

.vault-template-card__btn--primary {
  padding-inline: 8px !important;
}

.vault-template-card__btn--danger {
  margin-left: 2px;
}

.vault-template-view-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
  margin: 0;
}

.vault-template-view-meta__span {
  grid-column: 1 / -1;
}

.vault-template-view-meta dt {
  color: var(--os-text-muted);
  font-size: 0.6875rem;
}

.vault-template-view-meta dd {
  margin: 2px 0 0;
  color: var(--os-text-title);
  font-size: 0.8125rem;
  word-break: break-all;
}

.vault-template-view-fields {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.vault-template-view-fields li {
  display: grid;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  background: #fff;
}

.vault-template-view-fields strong {
  color: var(--os-text-title);
  font-size: 0.8125rem;
}

.vault-template-view-fields span {
  color: var(--os-primary);
  font-size: 0.75rem;
}

.vault-template-view-fields small {
  color: var(--os-text-muted);
  font-size: 0.6875rem;
}

@media (max-width: 599px) {
  .vault-template-grid {
    grid-template-columns: 1fr;
  }

  .vault-template-card {
    padding: 14px 16px;
  }

  .vault-template-card__fields {
    padding-left: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .vault-template-card {
    transition: none;
  }

  .vault-template-card:hover {
    transform: none;
  }
}
</style>
