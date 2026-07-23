<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ApiRequestError } from '@/api/client'
import {
  invitationsApi,
  type Invitation,
  type InvitationStats,
  type InvitePurpose,
} from '@/api/invitations'
import AdminEllipsisText from '@/components/AdminEllipsisText.vue'
import OsConfirmDialog from '@/components/OsConfirmDialog.vue'

const loading = ref(false)
const creating = ref(false)
const errorMessage = ref('')
const items = ref<Invitation[]>([])
const stats = ref<InvitationStats>({ total: 0, active: 0, expiringSoon: 0, disabledOrExhausted: 0 })
const page = ref(1)
const pageSize = 10
const totalElements = ref(0)
const statusFilter = ref('')
const purposeFilter = ref('')
const usageFilter = ref('')
const query = ref('')

const createDialog = ref(false)
const plainCodeDialog = ref(false)
const createdCodeHint = ref('')
const createdPlainCode = ref('')
const copyToast = ref(false)
const copyToastText = ref('')
const copyToastColor = ref<'success' | 'warning'>('success')
const purpose = ref<InvitePurpose>('USER_REGISTRATION')
const maxUses = ref(1)
const note = ref('')
const expiresAtLocal = ref('')
const createError = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / pageSize)))

const statusOptions = [
  { title: '状态：全部', value: '' },
  { title: '可用', value: 'ACTIVE' },
  { title: '已停用', value: 'DISABLED' },
  { title: '已耗尽', value: 'EXHAUSTED' },
  { title: '已过期', value: 'EXPIRED' },
]

const purposeOptions = [
  { title: '类型：全部', value: '' },
  { title: '用户注册', value: 'USER_REGISTRATION' },
]

const purposeCreateOptions = [
  { title: '用户注册', value: 'USER_REGISTRATION' },
]

const usageOptions = [
  { title: '使用方式：全部', value: '' },
  { title: '单次', value: 'SINGLE' },
  { title: '多次', value: 'MULTI' },
]

onMounted(loadData)
watch([page, statusFilter, purposeFilter, usageFilter], loadData)

async function loadData() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [statsResult, listResult] = await Promise.all([
      invitationsApi.stats(),
      invitationsApi.list({
        page: page.value - 1,
        size: pageSize,
        status: statusFilter.value || undefined,
        purpose: purposeFilter.value || undefined,
        type: usageFilter.value || undefined,
        q: query.value.trim() || undefined,
      }),
    ])
    stats.value = statsResult
    items.value = listResult.content
    totalElements.value = listResult.totalElements
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '加载邀请码失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function search() {
  page.value = 1
  await loadData()
}

function statusLabel(status: string) {
  switch (status) {
    case 'ACTIVE':
      return '可用'
    case 'ACTIVE_ATTENTION':
      return '可用 · 需注意'
    case 'DISABLED':
      return '已停用'
    case 'EXHAUSTED':
      return '已耗尽'
    case 'EXPIRED':
      return '已过期'
    default:
      return status
  }
}

function statusColor(status: string) {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'ACTIVE_ATTENTION':
      return 'warning'
    case 'DISABLED':
      return 'error'
    case 'EXHAUSTED':
    case 'EXPIRED':
      return 'info'
    default:
      return 'secondary'
  }
}

function purposeLabel(value: string | null | undefined) {
  if (value === 'USER_REGISTRATION') return '用户注册'
  return value || '—'
}

function usageLabel(type: string) {
  return type === 'SINGLE' ? '单次' : '多次'
}

function formatTime(value: string | null) {
  if (!value) return '无限制'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function openCreate() {
  createError.value = ''
  purpose.value = 'USER_REGISTRATION'
  maxUses.value = 1
  note.value = ''
  expiresAtLocal.value = ''
  createDialog.value = true
}

async function submitCreate() {
  createError.value = ''
  if (!purpose.value) {
    createError.value = '请选择邀请码类型'
    return
  }
  if (!Number.isInteger(maxUses.value) || maxUses.value < 1) {
    createError.value = '请填写有效的最大使用次数'
    return
  }
  creating.value = true
  try {
    const payload = {
      purpose: purpose.value,
      maxUses: maxUses.value,
      note: note.value.trim() || null,
      expiresAt: expiresAtLocal.value ? new Date(expiresAtLocal.value).toISOString() : null,
    }
    const result = await invitationsApi.create(payload)
    createdCodeHint.value = result.invitation.codeHint
    createdPlainCode.value = result.plainCode
    createDialog.value = false
    plainCodeDialog.value = true
    await copyText(result.plainCode, '邀请码已复制到剪贴板')
    await loadData()
  } catch (error) {
    createError.value = error instanceof ApiRequestError ? error.message : '创建失败，请稍后重试。'
  } finally {
    creating.value = false
  }
}

const deleteConfirmOpen = ref(false)
const deleting = ref(false)
const deleteTarget = ref<Invitation | null>(null)

function askDeleteInvite(item: Invitation) {
  deleteTarget.value = item
  deleteConfirmOpen.value = true
}

async function confirmDeleteInvite() {
  if (!deleteTarget.value) return
  deleting.value = true
  loading.value = true
  try {
    await invitationsApi.remove(deleteTarget.value.id)
    deleteConfirmOpen.value = false
    deleteTarget.value = null
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '删除失败，请稍后重试。'
    loading.value = false
  } finally {
    deleting.value = false
  }
}

async function copyText(text: string, successMessage = '已复制') {
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const input = document.createElement('textarea')
      input.value = text
      input.setAttribute('readonly', 'true')
      input.style.position = 'fixed'
      input.style.opacity = '0'
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
    }
    copyToastColor.value = 'success'
    copyToastText.value = successMessage
    copyToast.value = true
  } catch {
    copyToastColor.value = 'warning'
    copyToastText.value = '复制失败，请手动选中后复制'
    copyToast.value = true
  }
}

async function copyInviteCode(item: Invitation) {
  try {
    const result = await invitationsApi.plainCode(item.id)
    await copyText(result.plainCode, '邀请码已复制到剪贴板')
  } catch (error) {
    copyToastColor.value = 'warning'
    copyToastText.value = error instanceof ApiRequestError
      ? error.message
      : '邀请码明文不可用，请新建邀请码。'
    copyToast.value = true
  }
}

function copyCreatedPlainCode() {
  return copyText(createdPlainCode.value, '邀请码已复制到剪贴板')
}
</script>

<template>
  <div>
    <div class="d-flex flex-wrap align-center justify-space-between ga-3 mb-6">
      <div />
      <v-btn class="admin-toolbar-btn" color="primary" prepend-icon="mdi-plus" @click="openCreate">创建邀请码</v-btn>
    </div>

    <div class="admin-stat-grid mb-4">
      <v-card class="admin-stat-card admin-stat-card--primary" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">邀请码总数</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-ticket-confirmation-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.total }}</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--success" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">可用中</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-check-circle-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.active }}</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--warning" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">即将过期</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-clock-alert-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.expiringSoon }}</div>
        <div class="admin-stat-card__hint">48 小时内</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--error" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">已停用/已耗尽</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-close-octagon-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.disabledOrExhausted }}</div>
      </v-card>
    </div>

    <v-alert type="info" variant="tonal" class="mb-4">
      列表仅显示掩码；点击「复制」可将完整邀请码写入剪贴板，页面不会展示明文。
    </v-alert>

    <v-card class="admin-panel mb-4" elevation="0">
      <div class="admin-filter-row">
        <v-text-field
          v-model="query"
          class="admin-filter-field"
          hide-details
          label="搜索"
          placeholder="按邀请码备注或创建者搜索"
          prepend-inner-icon="mdi-magnify"
          @keyup.enter="search"
        />
        <v-select
          v-model="statusFilter"
          class="admin-filter-select"
          hide-details
          :items="statusOptions"
          item-title="title"
          item-value="value"
        />
        <v-select
          v-model="purposeFilter"
          class="admin-filter-select"
          hide-details
          :items="purposeOptions"
          item-title="title"
          item-value="value"
        />
        <v-select
          v-model="usageFilter"
          class="admin-filter-select"
          hide-details
          :items="usageOptions"
          item-title="title"
          item-value="value"
        />
        <v-btn class="admin-toolbar-btn" variant="tonal" color="primary" @click="search">查询</v-btn>
      </div>
    </v-card>

    <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4">{{ errorMessage }}</v-alert>

    <v-card class="admin-panel" elevation="0">
      <v-table class="admin-table">
        <thead>
          <tr>
            <th>类型</th>
            <th>邀请码</th>
            <th>使用方式</th>
            <th>使用进度</th>
            <th>有效期</th>
            <th>状态</th>
            <th>创建者</th>
            <th>最近使用</th>
            <th>备注</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && items.length === 0">
            <td colspan="10" class="text-medium-emphasis py-8 text-center">暂无邀请码，点击右上角创建。</td>
          </tr>
          <tr
            v-for="item in items"
            :key="item.id"
            :class="{ 'admin-table__row--disabled': item.status === 'DISABLED' }"
          >
            <td data-label="类型">{{ purposeLabel(item.purpose) }}</td>
            <td data-label="邀请码">
              <div class="d-flex align-center ga-1" style="min-width: 0">
                <AdminEllipsisText
                  class="font-weight-medium"
                  :text="item.codeHint"
                  max-width="9rem"
                />
                <v-btn
                  class="admin-icon-action"
                  icon="mdi-content-copy"
                  size="x-small"
                  variant="text"
                  color="primary"
                  aria-label="复制邀请码"
                  @click="copyInviteCode(item)"
                />
              </div>
            </td>
            <td data-label="使用方式">{{ usageLabel(item.type) }}</td>
            <td data-label="使用进度" style="min-width: 140px">
              <div class="mb-1">{{ item.usedCount }} / {{ item.maxUses }}</div>
              <v-progress-linear
                :model-value="item.maxUses ? (item.usedCount / item.maxUses) * 100 : 0"
                color="primary"
                height="6"
                rounded
              />
            </td>
            <td data-label="有效期">
              <AdminEllipsisText
                :text="item.expiresAt ? formatTime(item.expiresAt) : '无限制'"
                max-width="9rem"
              />
            </td>
            <td data-label="状态">
              <span class="admin-status-text" :data-tone="statusColor(item.status)">
                {{ statusLabel(item.status) }}
              </span>
            </td>
            <td data-label="创建者">
              <AdminEllipsisText :text="item.creatorUsername" max-width="8rem" />
            </td>
            <td data-label="最近使用">
              <AdminEllipsisText
                :text="item.lastUsedAt ? formatTime(item.lastUsedAt) : '—'"
                max-width="9rem"
              />
            </td>
            <td data-label="备注">
              <AdminEllipsisText :text="item.note" max-width="10rem" />
            </td>
            <td data-label="操作">
              <div class="admin-row-actions">
                <v-btn
                  class="admin-row-actions__btn admin-row-actions__btn--danger"
                  size="x-small"
                  variant="text"
                  color="error"
                  prepend-icon="mdi-delete-outline"
                  @click="askDeleteInvite(item)"
                >
                  删除
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

    <v-dialog v-model="createDialog" max-width="480" persistent>
      <v-card class="os-form-dialog">
        <v-card-title class="os-form-dialog__title">
          <div class="os-form-dialog__heading">
            <span class="os-form-dialog__mark" aria-hidden="true">
              <v-icon icon="mdi-ticket-confirmation-outline" size="18" />
            </span>
            <div>
              <p class="os-form-dialog__eyebrow">邀请管理</p>
              <h2>创建邀请码</h2>
            </div>
          </div>
          <v-btn
            icon="mdi-close"
            variant="text"
            size="small"
            aria-label="关闭"
            :disabled="creating"
            @click="createDialog = false"
          />
        </v-card-title>

        <v-card-text class="os-form-dialog__body">
          <v-alert v-if="createError" type="error" variant="tonal" density="compact" class="mb-3">
            {{ createError }}
          </v-alert>

          <section class="os-form-dialog__section os-form-dialog__section--basic">
            <header class="os-form-dialog__section-head">
              <span class="os-form-dialog__section-icon" aria-hidden="true">
                <v-icon icon="mdi-cog-outline" size="14" />
              </span>
              <div>
                <h3 class="os-form-dialog__section-title">使用规则</h3>
                <p class="os-form-dialog__section-hint">次数与有效期，创建后立即生效</p>
              </div>
            </header>
            <div class="os-form-dialog__grid">
              <v-select
                v-model="purpose"
                class="os-form-dialog__control os-form-dialog__control--span"
                :items="purposeCreateOptions"
                item-title="title"
                item-value="value"
                label="类型"
                hint="决定邀请码可用于何种业务"
                density="compact"
                hide-details="auto"
              />
              <v-text-field
                v-model.number="maxUses"
                class="os-form-dialog__control os-form-dialog__control--span"
                type="number"
                min="1"
                label="最大使用次数"
                hint="1 表示单次邀请码"
                density="compact"
                hide-details="auto"
              />
              <v-text-field
                v-model="expiresAtLocal"
                class="os-form-dialog__control os-form-dialog__control--span"
                type="datetime-local"
                label="过期时间（可选）"
                hint="留空表示不过期"
                density="compact"
                hide-details="auto"
                clearable
              />
              <v-text-field
                v-model="note"
                class="os-form-dialog__control os-form-dialog__control--span"
                label="备注（可选）"
                maxlength="200"
                density="compact"
                hide-details="auto"
              />
            </div>
          </section>
        </v-card-text>

        <v-card-actions class="os-form-dialog__actions">
          <v-btn
            variant="text"
            size="small"
            :disabled="creating"
            @click="createDialog = false"
          >
            取消
          </v-btn>
          <v-spacer />
          <v-btn
            class="os-form-dialog__save"
            color="primary"
            size="small"
            prepend-icon="mdi-plus"
            :loading="creating"
            @click="submitCreate"
          >
            创建
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="plainCodeDialog" max-width="480">
      <v-card class="os-form-dialog">
        <v-card-title class="os-form-dialog__title">
          <div class="os-form-dialog__heading">
            <span class="os-form-dialog__mark" aria-hidden="true">
              <v-icon icon="mdi-check-circle-outline" size="18" />
            </span>
            <div>
              <p class="os-form-dialog__eyebrow">创建成功</p>
              <h2>邀请码已生成</h2>
            </div>
          </div>
          <v-btn
            icon="mdi-close"
            variant="text"
            size="small"
            aria-label="关闭"
            @click="plainCodeDialog = false"
          />
        </v-card-title>
        <v-card-text class="os-form-dialog__body">
          <section class="os-form-dialog__section os-form-dialog__section--basic">
            <header class="os-form-dialog__section-head">
              <span class="os-form-dialog__section-icon" aria-hidden="true">
                <v-icon icon="mdi-key-outline" size="14" />
              </span>
              <div>
                <h3 class="os-form-dialog__section-title">邀请码</h3>
                <p class="os-form-dialog__section-hint">页面仅显示掩码；完整码可再次复制</p>
              </div>
            </header>
            <v-text-field
              class="os-form-dialog__control"
              :model-value="createdCodeHint"
              readonly
              label="邀请码（掩码）"
              density="compact"
              hide-details
              append-inner-icon="mdi-content-copy"
              @click:append-inner="copyCreatedPlainCode"
            />
          </section>
        </v-card-text>
        <v-card-actions class="os-form-dialog__actions">
          <v-btn
            variant="text"
            size="small"
            color="primary"
            prepend-icon="mdi-content-copy"
            @click="copyCreatedPlainCode"
          >
            复制完整邀请码
          </v-btn>
          <v-spacer />
          <v-btn
            class="os-form-dialog__save"
            color="primary"
            size="small"
            @click="plainCodeDialog = false"
          >
            关闭
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <OsConfirmDialog
      v-model="deleteConfirmOpen"
      variant="danger"
      title="确认删除邀请码？"
      :message="`将删除邀请码 ${deleteTarget?.codeHint || ''}。删除后不可恢复，是否继续？`"
      confirm-text="确认删除"
      :loading="deleting"
      @confirm="confirmDeleteInvite"
    />

    <v-snackbar v-model="copyToast" :color="copyToastColor" timeout="2600" location="top">
      {{ copyToastText }}
    </v-snackbar>
  </div>
</template>
