<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ApiRequestError } from '@/api/client'
import {
  adminSystemTemplatesApi,
  type AdminSystemTemplate,
  type SystemTemplateField,
  type SystemTemplateStatus,
} from '@/api/systemTemplates'
import AdminEllipsisText from '@/components/AdminEllipsisText.vue'
import { useOsToast } from '@/composables/useOsToast'
import { fieldTypeItems, type FieldType } from '@/domain/vaultPayload'

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const items = ref<AdminSystemTemplate[]>([])
const page = ref(1)
const pageSize = 10
const totalElements = ref(0)
const statusFilter = ref<SystemTemplateStatus | ''>('')
const platformFilter = ref('')
const keyword = ref('')

const editorOpen = ref(false)
const editingId = ref<string | null>(null)
const name = ref('')
const platform = ref('')
const channel = ref('')
const channelUrl = ref('')
const sortOrder = ref(0)
const fields = ref<SystemTemplateField[]>([])
const toast = useOsToast()
const successToast = ref(false)
const toastText = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / pageSize)))
const isEdit = computed(() => Boolean(editingId.value))

const statusOptions = [
  { title: '状态：全部', value: '' },
  { title: '草稿', value: 'DRAFT' },
  { title: '已发布', value: 'PUBLISHED' },
  { title: '已下线', value: 'OFFLINE' },
]

const statusLabelMap: Record<SystemTemplateStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  OFFLINE: '已下线',
}

onMounted(loadData)
watch([page, statusFilter], loadData)

async function loadData() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await adminSystemTemplatesApi.list({
      page: page.value - 1,
      size: pageSize,
      status: statusFilter.value || undefined,
      platform: platformFilter.value || undefined,
      q: keyword.value || undefined,
    })
    items.value = result.content
    totalElements.value = result.totalElements
  } catch (error) {
    items.value = []
    totalElements.value = 0
    errorMessage.value = error instanceof ApiRequestError ? error.message : '加载系统模板失败'
  } finally {
    loading.value = false
  }
}

function defaultFields(): SystemTemplateField[] {
  return [
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
  ]
}

function openCreate() {
  editingId.value = null
  name.value = ''
  platform.value = ''
  channel.value = ''
  channelUrl.value = ''
  sortOrder.value = 0
  fields.value = defaultFields()
  editorOpen.value = true
}

function openEdit(item: AdminSystemTemplate) {
  editingId.value = item.id
  name.value = item.name
  platform.value = item.platform
  channel.value = item.channel
  channelUrl.value = item.channelUrl
  sortOrder.value = item.sortOrder
  fields.value = item.fields.map((field) => ({ ...field, value: '' }))
  editorOpen.value = true
}

function addField() {
  fields.value.push({
    id: crypto.randomUUID(),
    name: `字段 ${fields.value.length + 1}`,
    type: 'TEXT',
    required: false,
    sensitive: false,
    copyable: true,
    hint: '',
    order: fields.value.length,
  })
}

function removeField(id: string) {
  fields.value = fields.value.filter((field) => field.systemKey || field.id !== id)
}

function onTypeChange(field: SystemTemplateField, type: FieldType) {
  if (field.systemKey === 'password') return
  if (field.systemKey === 'account' && type !== 'TEXT' && type !== 'EMAIL' && type !== 'PHONE') return
  field.type = type
  if (type === 'PASSWORD') {
    field.sensitive = true
    field.copyable = true
  }
}

async function submitEditor() {
  if (!name.value.trim() || !platform.value.trim()) {
    toast.error('请填写模板名称和所属平台')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: name.value.trim(),
      platform: platform.value.trim(),
      channel: channel.value.trim(),
      channelUrl: channelUrl.value.trim(),
      sortOrder: sortOrder.value,
      fields: fields.value.map((field, index) => ({
        ...field,
        value: '',
        order: index,
      })),
    }
    if (editingId.value) {
      await adminSystemTemplatesApi.update(editingId.value, payload)
      toastText.value = '已保存为草稿，请重新发布后用户端才会更新'
    } else {
      await adminSystemTemplatesApi.create(payload)
      toastText.value = '草稿已创建'
    }
    successToast.value = true
    editorOpen.value = false
    await loadData()
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function publish(item: AdminSystemTemplate) {
  try {
    await adminSystemTemplatesApi.publish(item.id)
    toastText.value = '模板已发布'
    successToast.value = true
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '发布失败'
  }
}

async function offline(item: AdminSystemTemplate) {
  try {
    await adminSystemTemplatesApi.offline(item.id)
    toastText.value = '模板已下线'
    successToast.value = true
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '下线失败'
  }
}

async function removeDraft(item: AdminSystemTemplate) {
  try {
    await adminSystemTemplatesApi.remove(item.id)
    toastText.value = '草稿已删除'
    successToast.value = true
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '删除失败'
  }
}

async function bumpSort(item: AdminSystemTemplate, delta: number) {
  try {
    await adminSystemTemplatesApi.updateSort(item.id, item.sortOrder + delta)
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '排序失败'
  }
}

function statusTone(status: SystemTemplateStatus) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'OFFLINE') return 'error'
  return 'warning'
}

function formatTime(value: string | null) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

function accountTypeItems() {
  return fieldTypeItems.filter((item) => item.value === 'TEXT' || item.value === 'EMAIL' || item.value === 'PHONE')
}

function fieldTone(type: string): string {
  if (type === 'PASSWORD') return 'password'
  if (type === 'EMAIL') return 'email'
  if (type === 'URL') return 'url'
  if (type === 'PHONE') return 'phone'
  return 'text'
}
</script>

<template>
  <div>
    <div class="d-flex flex-wrap align-center justify-space-between ga-3 mb-6">
      <p class="text-body-2 text-medium-emphasis mb-0">
        系统模板只保存字段结构；发布后全体用户可选用创建记录。编辑已发布/已下线模板会回到草稿，需重新发布后用户端才更新；不影响已创建记录。
      </p>
      <v-btn class="admin-toolbar-btn" color="primary" prepend-icon="mdi-plus" @click="openCreate">
        新建模板
      </v-btn>
    </div>

    <v-card class="admin-panel mb-4" elevation="0">
      <div class="admin-filter-row">
        <v-select
          v-model="statusFilter"
          class="admin-filter-select"
          hide-details
          :items="statusOptions"
          item-title="title"
          item-value="value"
          label="状态"
        />
        <v-text-field
          v-model="platformFilter"
          class="admin-filter-select"
          hide-details
          label="平台"
          clearable
          @keyup.enter="loadData"
        />
        <v-text-field
          v-model="keyword"
          class="admin-filter-select"
          hide-details
          label="名称关键词"
          clearable
          @keyup.enter="loadData"
        />
        <v-spacer />
        <v-btn class="admin-toolbar-btn" variant="tonal" color="primary" :loading="loading" @click="loadData">
          刷新
        </v-btn>
      </div>
    </v-card>

    <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4" closable @click:close="errorMessage = ''">
      {{ errorMessage }}
    </v-alert>

    <v-card class="admin-panel" elevation="0">
      <v-table class="admin-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>平台</th>
            <th>渠道</th>
            <th>渠道网址</th>
            <th>状态</th>
            <th>字段数</th>
            <th>排序</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="9" class="text-medium-emphasis">加载中…</td>
          </tr>
          <tr v-else-if="!items.length">
            <td colspan="9" class="text-medium-emphasis">暂无系统模板</td>
          </tr>
          <tr v-for="item in items" :key="item.id">
            <td data-label="名称">
              <AdminEllipsisText class="font-weight-medium" :text="item.name" max-width="10rem" />
            </td>
            <td data-label="平台">
              <AdminEllipsisText :text="item.platform" max-width="8rem" />
            </td>
            <td data-label="渠道">
              <AdminEllipsisText :text="item.channel" empty="未填渠道" max-width="8rem" />
            </td>
            <td data-label="渠道网址">
              <AdminEllipsisText :text="item.channelUrl" max-width="12rem" />
            </td>
            <td data-label="状态">
              <v-chip size="small" :color="statusTone(item.status)" variant="tonal">
                {{ statusLabelMap[item.status] }}
              </v-chip>
            </td>
            <td data-label="字段数">{{ item.fields.length }}</td>
            <td data-label="排序">
              <div class="d-flex align-center ga-1">
                <span>{{ item.sortOrder }}</span>
                <v-btn size="x-small" variant="text" icon="mdi-arrow-up" aria-label="上移" @click="bumpSort(item, -1)" />
                <v-btn size="x-small" variant="text" icon="mdi-arrow-down" aria-label="下移" @click="bumpSort(item, 1)" />
              </div>
            </td>
            <td data-label="更新时间">
              <AdminEllipsisText :text="formatTime(item.updatedAt)" max-width="9rem" />
            </td>
            <td>
              <div class="admin-row-actions">
                <v-btn
                  class="admin-row-actions__btn"
                  size="x-small"
                  variant="text"
                  color="primary"
                  prepend-icon="mdi-pencil-outline"
                  @click="openEdit(item)"
                >
                  编辑
                </v-btn>
                <v-btn
                  v-if="item.status !== 'PUBLISHED'"
                  class="admin-row-actions__btn admin-row-actions__btn--primary"
                  size="x-small"
                  variant="flat"
                  color="primary"
                  prepend-icon="mdi-publish"
                  @click="publish(item)"
                >
                  发布
                </v-btn>
                <v-btn
                  v-if="item.status === 'PUBLISHED'"
                  class="admin-row-actions__btn admin-row-actions__btn--warn"
                  size="x-small"
                  variant="text"
                  color="warning"
                  prepend-icon="mdi-eye-off-outline"
                  @click="offline(item)"
                >
                  下线
                </v-btn>
                <v-btn
                  v-if="item.status === 'DRAFT'"
                  class="admin-row-actions__btn admin-row-actions__btn--danger"
                  size="x-small"
                  variant="text"
                  color="error"
                  prepend-icon="mdi-delete-outline"
                  @click="removeDraft(item)"
                >
                  删除
                </v-btn>
              </div>
            </td>
          </tr>
        </tbody>
      </v-table>

      <div v-if="totalElements > pageSize" class="d-flex justify-center pa-4">
        <v-pagination v-model="page" :length="totalPages" density="compact" />
      </div>
    </v-card>

    <v-dialog v-model="editorOpen" max-width="720" scrollable persistent>
      <v-card class="os-form-dialog">
        <v-card-title class="os-form-dialog__title">
          <div class="os-form-dialog__heading">
            <span class="os-form-dialog__mark" aria-hidden="true">
              <v-icon :icon="isEdit ? 'mdi-pencil-outline' : 'mdi-view-grid-plus-outline'" size="18" />
            </span>
            <div>
              <p class="os-form-dialog__eyebrow">{{ isEdit ? '编辑模板' : '新建模板' }}</p>
              <h2>{{ isEdit ? '编辑系统模板' : '新建系统模板' }}</h2>
              <p v-if="isEdit" class="text-caption text-medium-emphasis mt-1 mb-0">
                修改已发布或已下线模板后会回到草稿，需重新发布后用户端才更新。
              </p>
            </div>
          </div>
          <v-btn
            icon="mdi-close"
            variant="text"
            size="small"
            aria-label="关闭"
            :disabled="saving"
            @click="editorOpen = false"
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
                <p class="os-form-dialog__section-hint">名称、平台与渠道；排序越小越靠前</p>
              </div>
            </header>
            <div class="os-form-dialog__grid">
              <v-text-field
                v-model="name"
                class="os-form-dialog__control"
                label="模板名称"
                density="compact"
                hide-details="auto"
              />
              <v-text-field
                v-model="platform"
                class="os-form-dialog__control"
                label="所属平台"
                density="compact"
                hide-details="auto"
              />
              <v-text-field
                v-model="channel"
                class="os-form-dialog__control"
                label="渠道名"
                density="compact"
                hide-details="auto"
              />
              <v-text-field
                v-model="channelUrl"
                class="os-form-dialog__control"
                label="渠道网址"
                density="compact"
                hide-details="auto"
              />
              <v-text-field
                v-model.number="sortOrder"
                class="os-form-dialog__control os-form-dialog__control--span"
                type="number"
                label="排序值"
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
                  <p class="os-form-dialog__section-hint">勾选「必填」的字段会进入用户录入时的必填区</p>
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
              v-for="(field, index) in fields"
              :key="field.id"
              class="os-form-dialog__field"
              :class="`os-form-dialog__field--${fieldTone(String(field.type))}`"
              :style="{ '--field-delay': `${index * 40}ms` }"
            >
              <div class="os-form-dialog__field-meta">
                <v-text-field
                  v-model="field.name"
                  class="os-form-dialog__control"
                  label="字段名称"
                  density="compact"
                  hide-details
                  :readonly="Boolean(field.systemKey)"
                />
                <v-select
                  class="os-form-dialog__control"
                  :model-value="field.type"
                  :items="field.systemKey === 'password'
                    ? fieldTypeItems.filter((item) => item.value === 'PASSWORD')
                    : field.systemKey === 'account' ? accountTypeItems() : fieldTypeItems"
                  label="类型"
                  density="compact"
                  hide-details
                  :disabled="field.systemKey === 'password'"
                  @update:model-value="(value) => onTypeChange(field, value as FieldType)"
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
                <v-checkbox
                  v-model="field.sensitive"
                  class="os-form-dialog__check"
                  label="敏感"
                  hide-details
                  density="compact"
                />
                <v-checkbox
                  v-model="field.copyable"
                  class="os-form-dialog__check"
                  label="可复制"
                  hide-details
                  density="compact"
                />
                <v-spacer />
                <v-btn
                  v-if="!field.systemKey"
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
        </v-card-text>

        <v-card-actions class="os-form-dialog__actions">
          <v-spacer />
          <v-btn variant="text" size="small" :disabled="saving" @click="editorOpen = false">取消</v-btn>
          <v-btn
            class="os-form-dialog__save"
            color="primary"
            size="small"
            :loading="saving"
            @click="submitEditor"
          >
            保存
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="successToast" :timeout="2200" color="primary">{{ toastText }}</v-snackbar>
  </div>
</template>
