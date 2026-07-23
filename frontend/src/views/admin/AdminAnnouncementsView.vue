<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ApiRequestError } from '@/api/client'
import {
  adminAnnouncementsApi,
  buildUpsertPayload,
  localInputFromIso,
  type AdminAnnouncement,
  type AnnouncementStatus,
} from '@/api/announcements'

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const items = ref<AdminAnnouncement[]>([])
const page = ref(1)
const pageSize = 10
const totalElements = ref(0)
const statusFilter = ref<AnnouncementStatus | ''>('')

const editorOpen = ref(false)
const editingId = ref<string | null>(null)
const title = ref('')
const body = ref('')
const pinned = ref(false)
const startsAtLocal = ref('')
const endsAtLocal = ref('')
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

const statusLabelMap: Record<AnnouncementStatus, string> = {
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
    const result = await adminAnnouncementsApi.list({
      page: page.value - 1,
      size: pageSize,
      status: statusFilter.value || undefined,
    })
    items.value = result.content
    totalElements.value = result.totalElements
  } catch (error) {
    items.value = []
    totalElements.value = 0
    errorMessage.value = error instanceof ApiRequestError ? error.message : '加载公告失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  title.value = ''
  body.value = ''
  pinned.value = false
  startsAtLocal.value = ''
  endsAtLocal.value = ''
  formError.value = ''
  editorOpen.value = true
}

function openEdit(item: AdminAnnouncement) {
  editingId.value = item.id
  title.value = item.title
  body.value = item.body
  pinned.value = item.pinned
  startsAtLocal.value = localInputFromIso(item.startsAt)
  endsAtLocal.value = localInputFromIso(item.endsAt)
  formError.value = ''
  editorOpen.value = true
}

async function submitEditor() {
  formError.value = ''
  if (!title.value.trim() || !body.value.trim()) {
    formError.value = '请填写标题和正文'
    return
  }
  saving.value = true
  try {
    const payload = buildUpsertPayload({
      title: title.value,
      body: body.value,
      pinned: pinned.value,
      startsAtLocal: startsAtLocal.value,
      endsAtLocal: endsAtLocal.value,
    })
    if (editingId.value) {
      const wasPublished = items.value.find((item) => item.id === editingId.value)?.status === 'PUBLISHED'
      await adminAnnouncementsApi.update(editingId.value, payload)
      toastText.value = wasPublished
        ? '已保存为草稿，请重新发布后用户端才会更新'
        : '公告已更新'
    } else {
      await adminAnnouncementsApi.create(payload)
      toastText.value = '草稿已创建'
    }
    toast.value = true
    editorOpen.value = false
    await loadData()
  } catch (error) {
    formError.value = error instanceof ApiRequestError ? error.message : '保存失败，请稍后重试。'
  } finally {
    saving.value = false
  }
}

async function publish(item: AdminAnnouncement) {
  try {
    await adminAnnouncementsApi.publish(item.id)
    toastText.value = '公告已发布'
    toast.value = true
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '发布失败'
  }
}

async function offline(item: AdminAnnouncement) {
  try {
    await adminAnnouncementsApi.offline(item.id)
    toastText.value = '公告已下线'
    toast.value = true
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '下线失败'
  }
}

function statusTone(status: AnnouncementStatus) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'OFFLINE') return 'error'
  return 'warning'
}

function formatTime(value: string | null) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

function formatWindow(item: AdminAnnouncement) {
  if (!item.startsAt && !item.endsAt) return '永久有效'
  return `${formatTime(item.startsAt)} ~ ${formatTime(item.endsAt)}`
}
</script>

<template>
  <div>
    <div class="d-flex flex-wrap align-center justify-space-between ga-3 mb-6">
      <p class="text-body-2 text-medium-emphasis mb-0">
        草稿可反复编辑；发布后用户端可见，未读最新一条会强制确认。
      </p>
      <v-btn class="admin-toolbar-btn" color="primary" prepend-icon="mdi-plus" @click="openCreate">
        新建公告
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
      <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-2" />
      <v-table class="admin-table">
        <thead>
          <tr>
            <th>标题</th>
            <th>状态</th>
            <th>置顶</th>
            <th>有效期</th>
            <th>发布时间</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && items.length === 0">
            <td colspan="7" class="text-medium-emphasis py-8 text-center">
              {{ errorMessage ? '暂时无法加载公告列表。' : '暂无公告，点击右上角新建。' }}
            </td>
          </tr>
          <tr
            v-for="item in items"
            :key="item.id"
            :class="{ 'admin-table__row--disabled': item.status === 'OFFLINE' }"
          >
            <td data-label="标题">
              <div class="font-weight-medium">{{ item.title }}</div>
              <div class="text-caption text-medium-emphasis">
                {{ item.body.slice(0, 56) }}{{ item.body.length > 56 ? '…' : '' }}
              </div>
            </td>
            <td data-label="状态">
              <span class="admin-status-text" :data-tone="statusTone(item.status)">
                {{ statusLabelMap[item.status] }}
              </span>
            </td>
            <td data-label="置顶">{{ item.pinned ? '是' : '否' }}</td>
            <td data-label="有效期">{{ formatWindow(item) }}</td>
            <td data-label="发布时间">{{ formatTime(item.publishedAt) }}</td>
            <td data-label="更新时间">{{ formatTime(item.updatedAt) }}</td>
            <td data-label="操作">
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
              </div>
            </td>
          </tr>
        </tbody>
      </v-table>

      <div class="admin-pagination-row mt-4">
        <div class="text-caption text-medium-emphasis">
          共 {{ totalElements }} 条，第 {{ page }} / {{ totalPages }} 页
        </div>
        <v-pagination v-model="page" :length="totalPages" total-visible="5" />
      </div>
    </v-card>

    <v-dialog
      v-model="editorOpen"
      class="admin-announcement-editor-dialog"
      max-width="680"
      persistent
      transition="dialog-transition"
    >
      <v-card class="admin-announcement-editor">
        <header class="admin-announcement-editor__hero">
          <span class="admin-announcement-editor__mark" aria-hidden="true">
            <v-icon :icon="isEdit ? 'mdi-pencil-outline' : 'mdi-bullhorn-outline'" size="20" />
          </span>
          <div>
            <p class="admin-announcement-editor__eyebrow">{{ isEdit ? '编辑中' : '新建草稿' }}</p>
            <h2>{{ isEdit ? '编辑公告' : '新建公告' }}</h2>
            <p class="admin-announcement-editor__hint">
              {{ isEdit
                ? '修改已发布公告后会回到草稿，需重新发布后用户端才更新；弹窗按最新发布时间展示。'
                : '标题简洁、正文说清事项；可不填有效期表示永久有效。' }}
            </p>
          </div>
        </header>

        <div class="admin-announcement-editor__body">
          <v-alert v-if="formError" type="error" variant="tonal" density="compact">{{ formError }}</v-alert>

          <section class="admin-announcement-editor__section">
            <h3>内容</h3>
            <v-text-field
              v-model="title"
              label="标题"
              placeholder="例如：系统维护通知"
              density="comfortable"
              maxlength="200"
              counter
              variant="outlined"
              hide-details="auto"
            />
            <v-textarea
              v-model="body"
              class="admin-announcement-editor__body-field"
              label="正文"
              placeholder="写清楚时间、影响范围与用户需要做的事"
              density="comfortable"
              rows="7"
              maxlength="10000"
              counter
              variant="outlined"
              auto-grow
              hide-details="auto"
            />
          </section>

          <section class="admin-announcement-editor__section">
            <h3>展示</h3>
            <div
              class="admin-announcement-pin"
              :class="{ 'admin-announcement-pin--on': pinned }"
              role="group"
              aria-label="置顶设置"
            >
              <span class="admin-announcement-pin__icon" aria-hidden="true">
                <v-icon :icon="pinned ? 'mdi-pin' : 'mdi-pin-outline'" size="18" />
              </span>
              <button type="button" class="admin-announcement-pin__copy" @click="pinned = !pinned">
                <strong>置顶到保险箱首页</strong>
                <small>开启后，用户首页会显示最新置顶公告横幅</small>
              </button>
              <v-switch
                v-model="pinned"
                color="primary"
                hide-details
                density="compact"
                inset
                class="admin-announcement-pin__switch"
                aria-label="置顶到保险箱首页"
              />
            </div>
          </section>

          <section class="admin-announcement-editor__section">
            <h3>有效期</h3>
            <div class="admin-announcement-window">
              <v-text-field
                v-model="startsAtLocal"
                label="开始时间"
                type="datetime-local"
                density="comfortable"
                variant="outlined"
                hint="不填表示立即生效"
                persistent-hint
                prepend-inner-icon="mdi-calendar-start"
                clearable
              />
              <v-text-field
                v-model="endsAtLocal"
                label="结束时间"
                type="datetime-local"
                density="comfortable"
                variant="outlined"
                hint="不填表示永久有效"
                persistent-hint
                prepend-inner-icon="mdi-calendar-end"
                clearable
              />
            </div>
          </section>
        </div>

        <footer class="admin-announcement-editor__actions">
          <v-btn variant="text" :disabled="saving" @click="editorOpen = false">取消</v-btn>
          <v-btn color="primary" min-width="112" :loading="saving" @click="submitEditor">保存</v-btn>
        </footer>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="toast" color="primary" :timeout="2200">{{ toastText }}</v-snackbar>
  </div>
</template>
