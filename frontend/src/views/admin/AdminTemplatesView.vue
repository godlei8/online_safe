<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ApiRequestError } from '@/api/client'
import {
  adminSystemTemplatesApi,
  type AdminSystemTemplate,
  type SystemTemplateField,
  type SystemTemplateStatus,
} from '@/api/systemTemplates'
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
const formError = ref('')
const toast = ref(false)
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
  formError.value = ''
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
  formError.value = ''
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
  formError.value = ''
  if (!name.value.trim() || !platform.value.trim()) {
    formError.value = '请填写模板名称和所属平台'
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
      toastText.value = '模板已更新'
    } else {
      await adminSystemTemplatesApi.create(payload)
      toastText.value = '草稿已创建'
    }
    toast.value = true
    editorOpen.value = false
    await loadData()
  } catch (error) {
    formError.value = error instanceof ApiRequestError ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function publish(item: AdminSystemTemplate) {
  try {
    await adminSystemTemplatesApi.publish(item.id)
    toastText.value = '模板已发布'
    toast.value = true
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '发布失败'
  }
}

async function offline(item: AdminSystemTemplate) {
  try {
    await adminSystemTemplatesApi.offline(item.id)
    toastText.value = '模板已下线'
    toast.value = true
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '下线失败'
  }
}

async function removeDraft(item: AdminSystemTemplate) {
  try {
    await adminSystemTemplatesApi.remove(item.id)
    toastText.value = '草稿已删除'
    toast.value = true
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
</script>

<template>
  <div>
    <div class="d-flex flex-wrap align-center justify-space-between ga-3 mb-6">
      <p class="text-body-2 text-medium-emphasis mb-0">
        系统模板只保存字段结构；发布后全体用户可选用创建记录。已发布可直接编辑，不影响历史记录。
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
            <th>状态</th>
            <th>字段数</th>
            <th>排序</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="text-medium-emphasis">加载中…</td>
          </tr>
          <tr v-else-if="!items.length">
            <td colspan="7" class="text-medium-emphasis">暂无系统模板</td>
          </tr>
          <tr v-for="item in items" :key="item.id">
            <td>
              <strong>{{ item.name }}</strong>
              <div class="text-caption text-medium-emphasis">{{ item.channel || '未填渠道' }}</div>
            </td>
            <td>{{ item.platform }}</td>
            <td>
              <v-chip size="small" :color="statusTone(item.status)" variant="tonal">
                {{ statusLabelMap[item.status] }}
              </v-chip>
            </td>
            <td>{{ item.fields.length }}</td>
            <td>
              <div class="d-flex align-center ga-1">
                <span>{{ item.sortOrder }}</span>
                <v-btn size="x-small" variant="text" icon="mdi-arrow-up" aria-label="上移" @click="bumpSort(item, -1)" />
                <v-btn size="x-small" variant="text" icon="mdi-arrow-down" aria-label="下移" @click="bumpSort(item, 1)" />
              </div>
            </td>
            <td>{{ formatTime(item.updatedAt) }}</td>
            <td>
              <div class="d-flex flex-wrap ga-1">
                <v-btn size="small" variant="text" @click="openEdit(item)">编辑</v-btn>
                <v-btn
                  v-if="item.status !== 'PUBLISHED'"
                  size="small"
                  variant="tonal"
                  color="primary"
                  @click="publish(item)"
                >
                  发布
                </v-btn>
                <v-btn
                  v-if="item.status === 'PUBLISHED'"
                  size="small"
                  variant="tonal"
                  color="warning"
                  @click="offline(item)"
                >
                  下线
                </v-btn>
                <v-btn
                  v-if="item.status === 'DRAFT'"
                  size="small"
                  variant="text"
                  color="error"
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
      <v-card class="admin-dialog-card">
        <v-card-title>{{ isEdit ? '编辑系统模板' : '新建系统模板' }}</v-card-title>
        <v-card-text>
          <div class="admin-template-grid">
            <v-text-field v-model="name" label="模板名称" density="compact" hide-details="auto" />
            <v-text-field v-model="platform" label="所属平台" density="compact" hide-details="auto" />
            <v-text-field v-model="channel" label="渠道名" density="compact" hide-details="auto" />
            <v-text-field v-model="channelUrl" label="渠道网址" density="compact" hide-details="auto" />
            <v-text-field
              v-model.number="sortOrder"
              type="number"
              label="排序值"
              density="compact"
              hide-details="auto"
              class="admin-template-grid__span"
            />
          </div>

          <div class="d-flex justify-space-between align-center mt-4 mb-2">
            <h3 class="text-subtitle-2 mb-0">字段结构</h3>
            <v-btn size="small" variant="tonal" color="primary" prepend-icon="mdi-plus" @click="addField">
              添加字段
            </v-btn>
          </div>

          <div v-for="field in fields" :key="field.id" class="admin-template-field">
            <div class="admin-template-grid">
              <v-text-field
                v-model="field.name"
                label="字段名称"
                density="compact"
                hide-details
                :readonly="Boolean(field.systemKey)"
              />
              <v-select
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
            <div class="d-flex flex-wrap align-center ga-2 mt-1">
              <v-checkbox v-model="field.required" label="必填" hide-details density="compact" :disabled="Boolean(field.systemKey)" />
              <v-checkbox v-model="field.sensitive" label="敏感" hide-details density="compact" />
              <v-checkbox v-model="field.copyable" label="可复制" hide-details density="compact" />
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

          <v-alert v-if="formError" type="error" variant="tonal" class="mt-3">{{ formError }}</v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" :disabled="saving" @click="editorOpen = false">取消</v-btn>
          <v-btn color="primary" :loading="saving" @click="submitEditor">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="toast" :timeout="2200" color="primary">{{ toastText }}</v-snackbar>
  </div>
</template>

<style scoped>
.admin-template-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.admin-template-grid__span {
  grid-column: 1 / -1;
}

.admin-template-field {
  margin-bottom: 8px;
  padding: 10px 12px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  background: var(--os-bg);
}

@media (max-width: 720px) {
  .admin-template-grid {
    grid-template-columns: 1fr;
  }
}
</style>
