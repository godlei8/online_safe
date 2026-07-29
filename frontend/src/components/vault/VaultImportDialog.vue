<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useDisplay } from 'vuetify'
import { ApiRequestError } from '@/api/client'
import {
  vaultImportApi,
  type ImportCandidate,
  type ImportCommitResult,
  type ImportMode,
  type ImportSession,
} from '@/api/vaultImport'
import { useOsToast } from '@/composables/useOsToast'
import { useVaultStore } from '@/stores/vault'

const open = defineModel<boolean>({ default: false })
const emit = defineEmits<{ imported: [] }>()

const { xs } = useDisplay()
const toast = useOsToast()
const vault = useVaultStore()

type Step = 'pick' | 'working' | 'summary' | 'reauth' | 'result'
type TabKey = 'READY' | 'NEEDS_REVIEW' | 'SKIPPED'
type ReviewFilter = 'ALL' | 'MISSING_FIELDS' | 'LOW_CONFIDENCE' | 'DUPLICATE' | 'OTHER'

const MISSING_FIELD_ISSUES = ['MISSING_ACCOUNT', 'MISSING_PASSWORD'] as const

const STEPS = [
  { key: 'pick', label: '选择文件' },
  { key: 'summary', label: '核对结果' },
  { key: 'reauth', label: '验证导入' },
  { key: 'result', label: '完成' },
] as const

const ACCEPT = '.xlsx,.xls,.csv,.md,.markdown,.txt'
const ACCEPT_EXT = ['xlsx', 'xls', 'csv', 'md', 'markdown', 'txt']
const step = ref<Step>('pick')
const busy = ref(false)
const file = ref<File | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)
const importMode = ref<ImportMode>('FAST')
const session = ref<ImportSession | null>(null)
const tab = ref<TabKey>('READY')
const reviewFilter = ref<ReviewFilter>('ALL')
const loginPassword = ref('')
const showPassword = ref(false)
const commitResult = ref<ImportCommitResult | null>(null)
const workingHint = ref('正在上传文件…')
const editDrafts = ref<Record<string, { name: string; platform: string }>>({})
const previewItem = ref<ImportCandidate | null>(null)
const previewOpen = computed({
  get: () => previewItem.value != null,
  set: (value: boolean) => {
    if (!value) previewItem.value = null
  },
})

let pollTimer: ReturnType<typeof setTimeout> | null = null
let pollToken = 0

const readyItems = computed(() => session.value?.candidates.filter((item) => item.bucket === 'READY') ?? [])
const reviewItems = computed(
  () => session.value?.candidates.filter((item) => item.bucket === 'NEEDS_REVIEW') ?? [],
)
const skippedItems = computed(
  () => session.value?.candidates.filter((item) => item.bucket === 'SKIPPED') ?? [],
)
const reviewIssueStats = computed(() => {
  const items = reviewItems.value
  return {
    missingFields: items.filter((item) =>
      MISSING_FIELD_ISSUES.some((code) => issuesOf(item).includes(code)),
    ).length,
    lowConfidence: items.filter((item) => issuesOf(item).includes('LOW_CONFIDENCE')).length,
    duplicate: items.filter((item) => issuesOf(item).includes('DUPLICATE_SUSPECTED')).length,
    other: items.filter((item) => {
      const issues = issuesOf(item)
      return (
        !MISSING_FIELD_ISSUES.some((code) => issues.includes(code)) &&
        !issues.includes('LOW_CONFIDENCE') &&
        !issues.includes('DUPLICATE_SUSPECTED')
      )
    }).length,
  }
})

const listItems = computed(() => {
  if (tab.value === 'READY') return readyItems.value
  if (tab.value === 'SKIPPED') return skippedItems.value

  const filtered = reviewItems.value.filter((item) => {
    const issues = issuesOf(item)
    switch (reviewFilter.value) {
      case 'MISSING_FIELDS':
        return MISSING_FIELD_ISSUES.some((code) => issues.includes(code))
      case 'LOW_CONFIDENCE':
        return issues.includes('LOW_CONFIDENCE')
      case 'DUPLICATE':
        return issues.includes('DUPLICATE_SUSPECTED')
      case 'OTHER':
        return (
          !MISSING_FIELD_ISSUES.some((code) => issues.includes(code)) &&
          !issues.includes('LOW_CONFIDENCE') &&
          !issues.includes('DUPLICATE_SUSPECTED')
        )
      default:
        return true
    }
  })

  return [...filtered].sort((a, b) => {
    const score = (item: ImportCandidate) => {
      const issues = issuesOf(item)
      let value = 0
      if (issues.includes('MISSING_ACCOUNT')) value += 5
      if (issues.includes('MISSING_PASSWORD')) value += 5
      if (issues.includes('DUPLICATE_SUSPECTED')) value += 2
      if (issues.includes('LOW_CONFIDENCE')) value += 1
      return value
    }
    const diff = score(b) - score(a)
    return diff !== 0 ? diff : a.rowIndex - b.rowIndex
  })
})

function setTab(next: TabKey) {
  tab.value = next
  if (next === 'NEEDS_REVIEW') {
    reviewFilter.value = 'ALL'
    seedReviewDrafts()
  }
}

function seedReviewDrafts() {
  for (const item of reviewItems.value) {
    seedDraft(item)
  }
}

watch(
  () => session.value?.candidates,
  () => {
    seedReviewDrafts()
  },
  { deep: true },
)

const stepIndex = computed(() => {
  if (step.value === 'working') return 0
  const idx = STEPS.findIndex((item) => item.key === step.value)
  return idx >= 0 ? idx : 0
})

const heading = computed(() => {
  switch (step.value) {
    case 'pick':
      return { eyebrow: '智能导入', title: '从文件导入账密' }
    case 'working':
      return { eyebrow: '智能导入', title: '正在处理' }
    case 'summary':
      return { eyebrow: '智能导入', title: '核对识别结果' }
    case 'reauth':
      return { eyebrow: '安全确认', title: '验证后写入保险箱' }
    case 'result':
      return { eyebrow: '智能导入', title: '导入完成' }
    default:
      return { eyebrow: '智能导入', title: '从文件导入账密' }
  }
})

const progressStages = computed(() => {
  const status = String(session.value?.status ?? (step.value === 'working' ? 'UPLOADING' : ''))
  const fast = importMode.value === 'FAST'
  const labels: Array<{ key: string; label: string }> = fast
    ? [
        { key: 'UPLOADING', label: '上传文件' },
        { key: 'PARSING', label: '解析映射' },
        { key: 'READY', label: '整理结果' },
      ]
    : [
        { key: 'UPLOADING', label: '上传文件' },
        { key: 'PARSING', label: '解析内容' },
        { key: 'AI_MAPPING', label: 'AI 识别' },
        { key: 'READY', label: '整理结果' },
      ]
  let normalized = status
  if (fast && status === 'AI_MAPPING') normalized = 'PARSING'
  const keys = labels.map((item) => item.key)
  let currentIdx = keys.indexOf(normalized)
  if (currentIdx < 0) currentIdx = status === 'FAILED' ? -1 : 0
  return labels.map((item, index) => ({
    ...item,
    done: status === 'READY' || (currentIdx > index && status !== 'FAILED'),
    active: status !== 'FAILED' && currentIdx === index && status !== 'READY',
  }))
})

const recognitionLabel = computed(() => {
  const mode = session.value?.recognitionMode || importMode.value
  if (mode === 'FAST' || session.value?.modelProvider === 'fast') {
    return '本地规则（快速导入）'
  }
  if (session.value?.modelName) {
    return `AI 模型 ${session.value.modelName}`
  }
  return 'AI 智能识别'
})

watch(open, (value) => {
  if (value) reset()
  else stopPolling()
})

onBeforeUnmount(() => stopPolling())

function reset() {
  stopPolling()
  step.value = 'pick'
  busy.value = false
  file.value = null
  dragOver.value = false
  importMode.value = 'FAST'
  session.value = null
  tab.value = 'READY'
  reviewFilter.value = 'ALL'
  loginPassword.value = ''
  showPassword.value = false
  commitResult.value = null
  workingHint.value = '正在上传文件…'
  editDrafts.value = {}
  previewItem.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function stopPolling() {
  pollToken += 1
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

function isAcceptedFile(candidate: File) {
  const ext = candidate.name.split('.').pop()?.toLowerCase() ?? ''
  return ACCEPT_EXT.includes(ext)
}

function assignFile(candidate: File | null) {
  if (!candidate) {
    file.value = null
    return
  }
  if (!isAcceptedFile(candidate)) {
    toast.error('仅支持 xlsx / xls / csv / md / txt')
    return
  }
  file.value = candidate
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  assignFile(input.files?.[0] ?? null)
}

function openFilePicker() {
  fileInputRef.value?.click()
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  dragOver.value = false
  const dropped = event.dataTransfer?.files?.[0]
  if (dropped) assignFile(dropped)
}

function formatBytes(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function fieldValue(item: ImportCandidate, systemKey: string) {
  const fields = payloadOf(item).fields
  if (!Array.isArray(fields)) return ''
  for (const field of fields) {
    if (!field || typeof field !== 'object') continue
    const row = field as Record<string, unknown>
    if (row.systemKey === systemKey) return String(row.value ?? '').trim()
  }
  return ''
}

function accountOf(item: ImportCandidate) {
  return fieldValue(item, 'account')
}

function passwordOf(item: ImportCandidate) {
  return fieldValue(item, 'password')
}

function passwordMasked(item: ImportCandidate) {
  const value = passwordOf(item)
  return value ? '••••••••' : '未识别'
}

function payloadFields(item: ImportCandidate) {
  const fields = payloadOf(item).fields
  if (!Array.isArray(fields)) return [] as Array<Record<string, unknown>>
  return fields.filter((field): field is Record<string, unknown> => Boolean(field && typeof field === 'object'))
}

function issuesOf(item: ImportCandidate): string[] {
  return Array.isArray(item.issues) ? item.issues : []
}

function payloadOf(item: ImportCandidate): Record<string, unknown> {
  return item.payload && typeof item.payload === 'object'
    ? (item.payload as Record<string, unknown>)
    : {}
}

function seedDraft(item: ImportCandidate) {
  if (editDrafts.value[item.id]) return
  const payload = payloadOf(item)
  editDrafts.value[item.id] = {
    name: String(payload.name ?? ''),
    platform: String(payload.platform ?? ''),
  }
}

function draftOf(item: ImportCandidate) {
  seedDraft(item)
  return editDrafts.value[item.id]
}

function ensureDraft(item: ImportCandidate) {
  return draftOf(item)
}

function issueLabel(code: string) {
  const map: Record<string, string> = {
    LOW_CONFIDENCE: '置信度偏低',
    MISSING_NAME: '缺少名称',
    MISSING_PLATFORM: '缺少平台',
    MISSING_ACCOUNT: '缺少账号',
    MISSING_PASSWORD: '缺少密码',
    DUPLICATE_SUSPECTED: '疑似重复',
    AI_OUTPUT_INVALID: 'AI 结果待核对',
    AI_UNAVAILABLE: 'AI 未参与（规则识别）',
    MANUAL_MAPPING_REQUIRED: '需人工核对',
    EMPTY_ROW: '空行',
  }
  return map[code] || code
}

function issueTone(code: string) {
  if (code === 'MISSING_ACCOUNT' || code === 'MISSING_PASSWORD') return 'danger'
  if (code === 'DUPLICATE_SUSPECTED' || code === 'EMPTY_ROW' || code === 'LOW_CONFIDENCE') return 'warn'
  if (code === 'AI_UNAVAILABLE' || code === 'AI_OUTPUT_INVALID' || code === 'MANUAL_MAPPING_REQUIRED') {
    return 'info'
  }
  return 'muted'
}

function missingFieldLabels(item: ImportCandidate) {
  const labels: string[] = []
  if (issuesOf(item).includes('MISSING_ACCOUNT') || !fieldValue(item, 'account')) {
    labels.push('账号')
  }
  if (issuesOf(item).includes('MISSING_PASSWORD') || !fieldValue(item, 'password')) {
    labels.push('密码')
  }
  return labels
}

function identityLine(item: ImportCandidate) {
  const parts: string[] = []
  const account = accountOf(item)
  if (account && account !== '未识别') parts.push(`账号 ${account}`)
  else parts.push('账号未识别')
  const password = passwordOf(item)
  if (password) parts.push('密码已识别')
  else parts.push('密码未识别')
  return parts.join(' · ')
}

function reviewHintText() {
  const stats = reviewIssueStats.value
  const bits: string[] = []
  if (stats.missingFields) bits.push(`${stats.missingFields} 条缺账号/密码`)
  if (stats.lowConfidence) bits.push(`${stats.lowConfidence} 条置信度偏低`)
  if (stats.duplicate) bits.push(`${stats.duplicate} 条疑似重复`)
  if (!bits.length) {
    return '名称、平台等为选填。账号与密码齐全即可导入；有问题的条目会标红。'
  }
  return `${bits.join('，')}。请优先补全账号/密码后再标记可导入。`
}

function skipReasons(item: ImportCandidate) {
  const reasons: string[] = []
  const issues = issuesOf(item)
  if (issues.includes('EMPTY_ROW')) {
    reasons.push('判定为空行，已自动跳过')
  }
  if (issues.includes('DUPLICATE_SUSPECTED')) {
    reasons.push(
      item.duplicateAction === 'SKIP' || !item.duplicateAction
        ? '疑似与保险箱已有记录重复，已选择跳过'
        : '疑似重复（仍标记为跳过）',
    )
  }
  if (!reasons.length) {
    reasons.push('已跳过（无更多说明）')
  }
  return reasons
}

function openPreview(item: ImportCandidate) {
  previewItem.value = item
}

function closePreview() {
  previewItem.value = null
}

async function startParse() {
  if (!file.value || busy.value) return
  busy.value = true
  step.value = 'working'
  workingHint.value = '正在上传文件…'
  session.value = null
  try {
    const created = await vaultImportApi.createSession(file.value, importMode.value)
    session.value = created
    workingHint.value = created.progressMessage || '文件已接收，正在处理…'
    await pollUntilReady(created.sessionId)
  } catch (error) {
    step.value = 'pick'
    toast.error(error instanceof ApiRequestError ? error.message : '上传失败，请稍后重试')
    busy.value = false
  }
}

async function pollUntilReady(sessionId: string) {
  const token = ++pollToken
  const poll = async () => {
    if (token !== pollToken) return
    try {
      const latest = await vaultImportApi.getSession(sessionId)
      if (token !== pollToken) return
      session.value = latest
      workingHint.value = latest.progressMessage || statusFallback(latest.status)

      if (latest.status === 'FAILED') {
        step.value = 'pick'
        busy.value = false
        toast.error(latest.progressMessage || '识别失败，请重试')
        return
      }
      if (latest.status === 'DISCARDED') {
        step.value = 'pick'
        busy.value = false
        return
      }
      if (latest.status === 'READY') {
        editDrafts.value = {}
        for (const item of latest.candidates) {
          if (item.bucket === 'NEEDS_REVIEW') ensureDraft(item)
        }
        step.value = 'summary'
        tab.value =
          latest.needsReviewCount > 0 && latest.readyCount === 0
            ? 'NEEDS_REVIEW'
            : latest.readyCount > 0
              ? 'READY'
              : latest.skippedCount > 0
                ? 'SKIPPED'
                : 'READY'
        busy.value = false
        if (latest.readyCount === 0 && latest.needsReviewCount > 0) {
          toast.warning('识别完成，请先处理「需核对」条目')
        }
        return
      }

      pollTimer = setTimeout(() => {
        void poll()
      }, importMode.value === 'FAST' ? 350 : 700)
    } catch (error) {
      if (token !== pollToken) return
      step.value = 'pick'
      busy.value = false
      toast.error(error instanceof ApiRequestError ? error.message : '查询进度失败')
    }
  }
  await poll()
}

function statusFallback(status: string) {
  switch (status) {
    case 'UPLOADING':
      return '正在接收文件…'
    case 'PARSING':
      return '正在解析文件…'
    case 'AI_MAPPING':
      return '正在智能识别字段…'
    default:
      return '处理中…'
  }
}

async function setDuplicateAction(item: ImportCandidate, action: 'SKIP' | 'CREATE') {
  if (!session.value || busy.value) return
  busy.value = true
  try {
    session.value = await vaultImportApi.patchCandidate(session.value.sessionId, item.id, {
      duplicateAction: action,
    })
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '更新失败')
  } finally {
    busy.value = false
  }
}

function buildReadyPayload(item: ImportCandidate) {
  const draft = ensureDraft(item)
  const name = draft.name.trim()
  const platform = draft.platform.trim()
  return {
    ...payloadOf(item),
    name: name || String(payloadOf(item).name || '').trim() || '导入记录',
    platform: platform || String(payloadOf(item).platform || '').trim() || '',
  }
}

async function markReady(item: ImportCandidate) {
  if (!session.value || busy.value) return
  if (!fieldValue(item, 'account')) {
    toast.error('请先确认账号已识别（可预览后核对）')
    return
  }
  if (!fieldValue(item, 'password')) {
    toast.error('请先确认密码已识别（可预览后核对）')
    return
  }
  busy.value = true
  try {
    session.value = await vaultImportApi.patchCandidate(session.value.sessionId, item.id, {
      payload: buildReadyPayload(item),
      confidence: 1,
      forceReady: true,
      duplicateAction: issuesOf(item).includes('DUPLICATE_SUSPECTED') ? 'CREATE' : undefined,
    })
    tab.value = 'READY'
    closePreview()
    toast.success('已移入「将导入」')
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '更新失败')
  } finally {
    busy.value = false
  }
}

async function markAllReviewReady() {
  if (!session.value || busy.value || reviewItems.value.length === 0) return
  const incomplete = reviewItems.value.filter(
    (item) => !fieldValue(item, 'account') || !fieldValue(item, 'password'),
  )
  if (incomplete.length) {
    reviewFilter.value = 'MISSING_FIELDS'
    toast.error(`还有 ${incomplete.length} 条缺少账号或密码，已筛选出来`)
    return
  }
  busy.value = true
  try {
    let latest = session.value
    const pending = [...reviewItems.value]
    for (const item of pending) {
      latest = await vaultImportApi.patchCandidate(latest.sessionId, item.id, {
        payload: buildReadyPayload(item),
        confidence: 1,
        forceReady: true,
        duplicateAction: issuesOf(item).includes('DUPLICATE_SUSPECTED') ? 'CREATE' : undefined,
      })
    }
    session.value = latest
    tab.value = 'READY'
    toast.success(`已标记 ${latest.readyCount} 条为可导入`)
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '批量标记失败')
  } finally {
    busy.value = false
  }
}

function askCommit() {
  if (!session.value || session.value.readyCount <= 0) return
  loginPassword.value = ''
  showPassword.value = false
  step.value = 'reauth'
}

function backToSummary() {
  if (busy.value) return
  step.value = 'summary'
}

async function confirmCommit() {
  if (!session.value || !loginPassword.value || busy.value) return
  busy.value = true
  try {
    await vaultImportApi.reauth(loginPassword.value)
    commitResult.value = await vaultImportApi.commit(session.value.sessionId)
    await vault.loadItems()
    step.value = 'result'
    emit('imported')
    if (commitResult.value.succeededCount > 0) {
      toast.success(`已导入 ${commitResult.value.succeededCount} 条`)
    } else {
      toast.warning(
        commitResult.value.failedCount > 0
          ? `未能写入保险箱（失败 ${commitResult.value.failedCount} 条）`
          : '没有可导入条目',
      )
    }
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '导入失败')
  } finally {
    busy.value = false
  }
}

async function closeDialog() {
  if (busy.value && step.value === 'working') {
    // 允许取消进行中的会话
    const id = session.value?.sessionId
    stopPolling()
    if (id) {
      try {
        await vaultImportApi.discard(id)
      } catch {
        // ignore
      }
    }
    open.value = false
    return
  }
  if (
    session.value &&
    step.value !== 'result' &&
    (session.value.status === 'READY' ||
      session.value.status === 'PARSING' ||
      session.value.status === 'AI_MAPPING' ||
      session.value.status === 'UPLOADING')
  ) {
    try {
      await vaultImportApi.discard(session.value.sessionId)
    } catch {
      // 关闭时尽力清理
    }
  }
  stopPolling()
  open.value = false
}

function restart() {
  reset()
}
</script>

<template>
  <v-dialog
    v-model="open"
    :fullscreen="xs"
    :max-width="xs ? undefined : 920"
    scrollable
    persistent
  >
    <v-card class="os-form-dialog vault-import-dialog">
      <v-card-title class="os-form-dialog__title">
        <div class="os-form-dialog__heading">
          <span class="os-form-dialog__mark" aria-hidden="true">
            <v-icon icon="mdi-file-upload-outline" size="18" />
          </span>
          <div>
            <p class="os-form-dialog__eyebrow">{{ heading.eyebrow }}</p>
            <h2>{{ heading.title }}</h2>
          </div>
        </div>
        <v-btn icon="mdi-close" variant="text" aria-label="关闭" @click="closeDialog" />
      </v-card-title>

      <v-card-text class="vault-import-dialog__body">
        <ol class="vault-import-dialog__steps" aria-label="导入步骤">
          <li
            v-for="(item, index) in STEPS"
            :key="item.key"
            class="vault-import-dialog__step"
            :class="{
              'vault-import-dialog__step--done': index < stepIndex,
              'vault-import-dialog__step--active':
                index === stepIndex || (step === 'working' && index === 0),
            }"
          >
            <span class="vault-import-dialog__step-dot">{{ index + 1 }}</span>
            <span class="vault-import-dialog__step-label">{{ item.label }}</span>
          </li>
        </ol>

        <template v-if="step === 'pick'">
          <p class="vault-import-dialog__notice">
            识别结果需你确认后才会写入保险箱。支持 xlsx / xls / csv / md / txt。
          </p>

          <div class="vault-import-dialog__mode" role="radiogroup" aria-label="导入模式">
            <button
              type="button"
              class="vault-import-dialog__mode-card"
              :class="{ 'vault-import-dialog__mode-card--active': importMode === 'FAST' }"
              role="radio"
              :aria-checked="importMode === 'FAST'"
              @click="importMode = 'FAST'"
            >
              <strong>快速导入</strong>
              <span>按表头本地映射，速度快、不调用大模型</span>
            </button>
            <button
              type="button"
              class="vault-import-dialog__mode-card"
              :class="{ 'vault-import-dialog__mode-card--active': importMode === 'AI' }"
              role="radio"
              :aria-checked="importMode === 'AI'"
              @click="importMode = 'AI'"
            >
              <strong>AI 智能识别</strong>
              <span>适合自由文本或表头不规范；需等待模型响应</span>
            </button>
          </div>

          <div
            class="vault-import-dialog__drop"
            :class="{
              'vault-import-dialog__drop--active': dragOver,
              'vault-import-dialog__drop--has-file': file,
            }"
            role="button"
            tabindex="0"
            @click="openFilePicker"
            @keydown.enter.prevent="openFilePicker"
            @dragenter.prevent="dragOver = true"
            @dragover.prevent="dragOver = true"
            @dragleave.prevent="dragOver = false"
            @drop="onDrop"
          >
            <input
              ref="fileInputRef"
              class="vault-import-dialog__file-input"
              type="file"
              :accept="ACCEPT"
              @change="onFileChange"
              @click.stop
            >
            <div class="vault-import-dialog__drop-icon" aria-hidden="true">
              <v-icon :icon="file ? 'mdi-file-check-outline' : 'mdi-cloud-upload-outline'" size="28" />
            </div>
            <template v-if="file">
              <p class="vault-import-dialog__drop-title">{{ file.name }}</p>
              <p class="vault-import-dialog__drop-meta">{{ formatBytes(file.size) }} · 点击可更换文件</p>
            </template>
            <template v-else>
              <p class="vault-import-dialog__drop-title">拖拽文件到此处，或点击选择</p>
              <p class="vault-import-dialog__drop-meta">支持 xlsx / xls / csv / md / txt</p>
            </template>
          </div>
          <div class="vault-import-dialog__formats">
            <span v-for="ext in ['xlsx', 'xls', 'csv', 'md', 'txt']" :key="ext">.{{ ext }}</span>
          </div>
        </template>

        <template v-else-if="step === 'working'">
          <div class="vault-import-dialog__working">
            <ol class="vault-import-dialog__progress">
              <li
                v-for="stage in progressStages"
                :key="stage.key"
                class="vault-import-dialog__progress-item"
                :class="{
                  'vault-import-dialog__progress-item--done': stage.done,
                  'vault-import-dialog__progress-item--active': stage.active,
                }"
              >
                <v-icon
                  :icon="stage.done ? 'mdi-check-circle' : stage.active ? 'mdi-progress-clock' : 'mdi-circle-outline'"
                  size="18"
                />
                <span>{{ stage.label }}</span>
              </li>
            </ol>
            <v-progress-linear indeterminate color="primary" rounded height="4" class="mb-3" />
            <p class="vault-import-dialog__working-title">{{ workingHint }}</p>
            <p class="vault-import-dialog__working-meta">
              {{ file?.name || session?.fileName || '文件' }}
              <template v-if="session?.status === 'AI_MAPPING' && session.rowCount">
                · 共 {{ session.rowCount }} 行
              </template>
            </p>
          </div>
        </template>

        <template v-else-if="step === 'summary' && session">
          <div class="vault-import-dialog__stats">
            <button
              type="button"
              class="vault-import-dialog__stat"
              :class="{ 'vault-import-dialog__stat--active': tab === 'READY' }"
              @click="setTab('READY')"
            >
              <strong>{{ session.readyCount }}</strong>
              <span>将导入</span>
            </button>
            <button
              type="button"
              class="vault-import-dialog__stat vault-import-dialog__stat--warn"
              :class="{ 'vault-import-dialog__stat--active': tab === 'NEEDS_REVIEW' }"
              @click="setTab('NEEDS_REVIEW')"
            >
              <strong>{{ session.needsReviewCount }}</strong>
              <span>需核对</span>
            </button>
            <button
              type="button"
              class="vault-import-dialog__stat vault-import-dialog__stat--muted"
              :class="{ 'vault-import-dialog__stat--active': tab === 'SKIPPED' }"
              @click="setTab('SKIPPED')"
            >
              <strong>{{ session.skippedCount }}</strong>
              <span>已跳过</span>
            </button>
          </div>

          <div class="vault-import-dialog__meta-row">
            <p class="vault-import-dialog__meta mb-0">
              {{ session.fileName }}
              · {{ recognitionLabel }}
              · 共 {{ session.rowCount }} 行
            </p>
            <v-btn
              v-if="tab === 'NEEDS_REVIEW' && reviewItems.length"
              class="admin-toolbar-btn"
              size="small"
              variant="tonal"
              color="primary"
              :disabled="busy"
              :loading="busy"
              @click="markAllReviewReady"
            >
              全部标记可导入
            </v-btn>
          </div>

          <p
            v-if="tab === 'NEEDS_REVIEW' && reviewItems.length"
            class="vault-import-dialog__hint"
          >
            {{ reviewHintText() }}
          </p>
          <p v-else-if="tab === 'SKIPPED'" class="vault-import-dialog__hint vault-import-dialog__hint--muted">
            以下条目不会写入保险箱。点击「查看原因 / 预览」了解详情。
          </p>

          <div
            v-if="tab === 'NEEDS_REVIEW' && reviewItems.length"
            class="vault-import-dialog__filters"
            role="tablist"
            aria-label="按问题筛选"
          >
            <button
              type="button"
              class="vault-import-dialog__filter"
              :class="{ 'vault-import-dialog__filter--active': reviewFilter === 'ALL' }"
              @click="reviewFilter = 'ALL'"
            >
              全部 {{ reviewItems.length }}
            </button>
            <button
              v-if="reviewIssueStats.missingFields"
              type="button"
              class="vault-import-dialog__filter vault-import-dialog__filter--danger"
              :class="{ 'vault-import-dialog__filter--active': reviewFilter === 'MISSING_FIELDS' }"
              @click="reviewFilter = 'MISSING_FIELDS'"
            >
              缺账密 {{ reviewIssueStats.missingFields }}
            </button>
            <button
              v-if="reviewIssueStats.lowConfidence"
              type="button"
              class="vault-import-dialog__filter"
              :class="{ 'vault-import-dialog__filter--active': reviewFilter === 'LOW_CONFIDENCE' }"
              @click="reviewFilter = 'LOW_CONFIDENCE'"
            >
              低置信度 {{ reviewIssueStats.lowConfidence }}
            </button>
            <button
              v-if="reviewIssueStats.duplicate"
              type="button"
              class="vault-import-dialog__filter"
              :class="{ 'vault-import-dialog__filter--active': reviewFilter === 'DUPLICATE' }"
              @click="reviewFilter = 'DUPLICATE'"
            >
              疑似重复 {{ reviewIssueStats.duplicate }}
            </button>
            <button
              v-if="reviewIssueStats.other"
              type="button"
              class="vault-import-dialog__filter"
              :class="{ 'vault-import-dialog__filter--active': reviewFilter === 'OTHER' }"
              @click="reviewFilter = 'OTHER'"
            >
              其他 {{ reviewIssueStats.other }}
            </button>
          </div>

          <div class="vault-import-dialog__list">
            <article
              v-for="item in listItems"
              :key="item.id"
              class="vault-import-dialog__item"
              :class="{
                'vault-import-dialog__item--review': tab === 'NEEDS_REVIEW',
                'vault-import-dialog__item--gap':
                  tab === 'NEEDS_REVIEW' && missingFieldLabels(item).length > 0,
                'vault-import-dialog__item--skipped': tab === 'SKIPPED',
              }"
            >
              <div class="vault-import-dialog__item-top">
                <span class="vault-import-dialog__row">第 {{ item.rowIndex }} 行</span>
                <span class="vault-import-dialog__confidence">
                  置信度 {{ Math.round(item.confidence * 100) }}%
                </span>
              </div>

              <template v-if="tab === 'NEEDS_REVIEW'">
                <p
                  v-if="missingFieldLabels(item).length"
                  class="vault-import-dialog__gap mb-0"
                >
                  <v-icon icon="mdi-alert-circle-outline" size="14" />
                  待补全：{{ missingFieldLabels(item).join('、') }}
                </p>
                <p v-if="identityLine(item)" class="vault-import-dialog__identity mb-0">
                  {{ identityLine(item) }}
                </p>
                <div class="vault-import-dialog__edit-grid">
                  <v-text-field
                    v-model="ensureDraft(item).name"
                    label="名称（选填）"
                    placeholder="记录名称"
                    density="compact"
                    hide-details
                    class="os-form-dialog__control"
                  />
                  <v-text-field
                    v-model="ensureDraft(item).platform"
                    label="平台（选填）"
                    placeholder="例如：谷歌 / GitHub"
                    density="compact"
                    hide-details
                    class="os-form-dialog__control"
                  />
                </div>
              </template>
              <template v-else>
                <strong class="vault-import-dialog__item-name">
                  {{ String(payloadOf(item).name || '未命名') }}
                </strong>
                <p class="vault-import-dialog__item-sub mb-0">
                  {{ String(payloadOf(item).platform || '未填写') }}
                  <template v-if="accountOf(item)"> · {{ accountOf(item) }}</template>
                </p>
              </template>

              <div v-if="tab === 'SKIPPED'" class="vault-import-dialog__skip-reasons">
                <p v-for="(reason, idx) in skipReasons(item)" :key="idx" class="mb-0">
                  {{ reason }}
                </p>
              </div>

              <div v-else-if="issuesOf(item).length" class="vault-import-dialog__issues">
                <span
                  v-for="code in issuesOf(item)"
                  :key="code"
                  class="vault-import-dialog__issue"
                  :class="`vault-import-dialog__issue--${issueTone(code)}`"
                >
                  {{ issueLabel(code) }}
                </span>
              </div>

              <div class="vault-import-dialog__item-actions">
                <v-btn
                  class="admin-toolbar-btn"
                  size="small"
                  variant="text"
                  color="primary"
                  @click="openPreview(item)"
                >
                  {{ tab === 'SKIPPED' ? '查看原因 / 预览' : '预览' }}
                </v-btn>
                <template v-if="tab === 'NEEDS_REVIEW'">
                  <template v-if="issuesOf(item).includes('DUPLICATE_SUSPECTED')">
                    <v-btn
                      class="admin-toolbar-btn"
                      size="small"
                      variant="tonal"
                      :color="item.duplicateAction === 'SKIP' ? 'primary' : undefined"
                      :disabled="busy"
                      @click="setDuplicateAction(item, 'SKIP')"
                    >
                      跳过重复
                    </v-btn>
                    <v-btn
                      class="admin-toolbar-btn"
                      size="small"
                      variant="outlined"
                      color="primary"
                      :disabled="busy"
                      @click="setDuplicateAction(item, 'CREATE')"
                    >
                      仍要新建
                    </v-btn>
                  </template>
                  <v-btn
                    class="admin-toolbar-btn"
                    size="small"
                    variant="flat"
                    color="primary"
                    :disabled="busy"
                    @click="markReady(item)"
                  >
                    标记为可导入
                  </v-btn>
                </template>
              </div>
            </article>

            <div v-if="listItems.length === 0" class="vault-import-dialog__empty">
              <v-icon icon="mdi-clipboard-text-outline" size="28" />
              <p class="mb-0">
                {{
                  tab === 'READY'
                    ? '暂无将导入条目'
                    : tab === 'NEEDS_REVIEW'
                      ? reviewFilter === 'ALL'
                        ? '没有需要核对的条目'
                        : '当前筛选下没有条目，可切换「全部」'
                      : '没有跳过的条目'
                }}
              </p>
            </div>
          </div>
        </template>

        <template v-else-if="step === 'reauth'">
          <div class="vault-import-dialog__reauth">
            <div class="vault-import-dialog__reauth-card">
              <v-icon icon="mdi-shield-lock-outline" size="22" color="primary" />
              <div>
                <p class="vault-import-dialog__reauth-title mb-1">
                  即将写入 {{ session?.readyCount || 0 }} 条记录
                </p>
                <p class="vault-import-dialog__reauth-meta mb-0">
                  为防止误操作，请再次输入当前登录密码完成确认。
                </p>
              </div>
            </div>
            <v-text-field
              v-model="loginPassword"
              :type="showPassword ? 'text' : 'password'"
              label="当前登录密码"
              density="compact"
              hide-details="auto"
              autocomplete="current-password"
              class="os-form-dialog__control"
              :append-inner-icon="showPassword ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
              @click:append-inner="showPassword = !showPassword"
              @keyup.enter="confirmCommit"
            />
          </div>
        </template>

        <template v-else-if="step === 'result' && commitResult">
          <div class="vault-import-dialog__result">
            <div
              class="vault-import-dialog__result-icon"
              :class="
                commitResult.succeededCount > 0
                  ? 'vault-import-dialog__result-icon--ok'
                  : 'vault-import-dialog__result-icon--warn'
              "
            >
              <v-icon
                :icon="
                  commitResult.succeededCount > 0 ? 'mdi-check-circle-outline' : 'mdi-alert-circle-outline'
                "
                size="32"
              />
            </div>
            <h3 class="vault-import-dialog__result-title">
              {{
                commitResult.succeededCount > 0
                  ? `成功导入 ${commitResult.succeededCount} 条`
                  : '没有写入任何记录'
              }}
            </h3>
            <p v-if="commitResult.failedCount" class="vault-import-dialog__result-fail mb-0">
              失败 {{ commitResult.failedCount }} 条
              <template v-if="commitResult.failures.length">
                （行号 {{ commitResult.failures.map((item) => item.rowIndex).join('、') }}）
              </template>
            </p>
            <p v-else class="vault-import-dialog__result-meta mb-0">
              可在保险箱列表中查看与编辑刚导入的记录。
            </p>
          </div>
        </template>
      </v-card-text>

      <v-card-actions class="os-form-dialog__actions vault-import-dialog__actions">
        <v-btn v-if="step === 'reauth'" variant="text" :disabled="busy" @click="backToSummary">
          返回核对
        </v-btn>
        <v-btn v-else-if="step === 'result'" variant="text" @click="restart">再导入一份</v-btn>
        <v-spacer />
        <v-btn
          v-if="step !== 'result'"
          variant="text"
          :disabled="busy && step !== 'working'"
          @click="closeDialog"
        >
          {{ step === 'working' ? '取消识别' : '取消' }}
        </v-btn>
        <v-btn
          v-if="step === 'pick'"
          color="primary"
          class="admin-toolbar-btn os-form-dialog__save"
          :disabled="!file || busy"
          :loading="busy"
          @click="startParse"
        >
          开始识别
        </v-btn>
        <v-btn
          v-else-if="step === 'summary'"
          color="primary"
          class="admin-toolbar-btn os-form-dialog__save"
          :disabled="!session || session.readyCount <= 0 || busy"
          @click="askCommit"
        >
          确认导入 {{ session?.readyCount || 0 }} 条
        </v-btn>
        <v-btn
          v-else-if="step === 'reauth'"
          color="primary"
          class="admin-toolbar-btn os-form-dialog__save"
          :disabled="!loginPassword || busy"
          :loading="busy"
          @click="confirmCommit"
        >
          验证并导入
        </v-btn>
        <v-btn
          v-else-if="step === 'result'"
          color="primary"
          class="admin-toolbar-btn os-form-dialog__save"
          @click="open = false"
        >
          完成
        </v-btn>
      </v-card-actions>
    </v-card>

    <v-dialog v-model="previewOpen" max-width="520" scrollable>
      <v-card v-if="previewItem" class="os-form-dialog vault-import-preview">
        <v-card-title class="os-form-dialog__title">
          <div class="os-form-dialog__heading">
            <span class="os-form-dialog__mark" aria-hidden="true">
              <v-icon icon="mdi-eye-outline" size="18" />
            </span>
            <div>
              <p class="os-form-dialog__eyebrow">记录预览</p>
              <h2>第 {{ previewItem.rowIndex }} 行</h2>
            </div>
          </div>
          <v-btn icon="mdi-close" variant="text" aria-label="关闭预览" @click="closePreview" />
        </v-card-title>
        <v-card-text class="vault-import-preview__body">
          <dl class="vault-import-preview__dl">
            <div>
              <dt>名称</dt>
              <dd>{{ String(previewItem.payload.name || '未命名') }}</dd>
            </div>
            <div>
              <dt>平台</dt>
              <dd>{{ String(previewItem.payload.platform || '未填写') }}</dd>
            </div>
            <div>
              <dt>账号</dt>
              <dd>{{ accountOf(previewItem) || '未识别' }}</dd>
            </div>
            <div>
              <dt>密码</dt>
              <dd>{{ passwordMasked(previewItem) }}</dd>
            </div>
            <div>
              <dt>置信度</dt>
              <dd>{{ Math.round(previewItem.confidence * 100) }}%</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>
                {{
                  previewItem.bucket === 'READY'
                    ? '将导入'
                    : previewItem.bucket === 'NEEDS_REVIEW'
                      ? '需核对'
                      : '已跳过'
                }}
              </dd>
            </div>
          </dl>

          <div v-if="previewItem.bucket === 'SKIPPED'" class="vault-import-preview__block">
            <h3>跳过原因</h3>
            <ul>
              <li v-for="(reason, idx) in skipReasons(previewItem)" :key="idx">{{ reason }}</li>
            </ul>
          </div>

          <div v-else-if="previewItem.issues.length" class="vault-import-preview__block">
            <h3>标记</h3>
            <div class="vault-import-dialog__issues">
              <span
                v-for="code in previewItem.issues"
                :key="code"
                class="vault-import-dialog__issue"
                :class="`vault-import-dialog__issue--${issueTone(code)}`"
              >
                {{ issueLabel(code) }}
              </span>
            </div>
          </div>

          <div v-if="payloadFields(previewItem).length" class="vault-import-preview__block">
            <h3>字段明细</h3>
            <ul class="vault-import-preview__fields">
              <li v-for="(field, idx) in payloadFields(previewItem)" :key="idx">
                <span>{{ String(field.name || field.systemKey || '字段') }}</span>
                <strong>
                  {{
                    field.sensitive || field.type === 'PASSWORD'
                      ? field.value
                        ? '••••••••'
                        : '空'
                      : String(field.value ?? '') || '空'
                  }}
                </strong>
              </li>
            </ul>
          </div>

          <p v-if="previewItem.payload.notes" class="vault-import-preview__notes mb-0">
            备注：{{ String(previewItem.payload.notes) }}
          </p>
        </v-card-text>
        <v-card-actions class="os-form-dialog__actions">
          <v-spacer />
          <v-btn variant="text" @click="closePreview">关闭</v-btn>
          <v-btn
            v-if="previewItem.bucket === 'NEEDS_REVIEW'"
            color="primary"
            class="admin-toolbar-btn os-form-dialog__save"
            :disabled="busy"
            @click="markReady(previewItem)"
          >
            标记为可导入
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-dialog>
</template>

<style scoped>
.vault-import-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.vault-import-dialog__steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.vault-import-dialog__step {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--os-text-muted);
  font-size: 0.75rem;
}

.vault-import-dialog__step-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  border: 1px solid var(--os-border);
  background: #fff;
  font-size: 0.6875rem;
  font-weight: 650;
  flex: 0 0 auto;
}

.vault-import-dialog__step-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vault-import-dialog__step--active {
  color: var(--os-primary);
}

.vault-import-dialog__step--active .vault-import-dialog__step-dot {
  border-color: var(--os-primary);
  background: var(--os-primary-tint);
  color: var(--os-primary);
}

.vault-import-dialog__step--done {
  color: var(--os-text-body);
}

.vault-import-dialog__step--done .vault-import-dialog__step-dot {
  border-color: transparent;
  background: var(--os-primary);
  color: #fff;
}

.vault-import-dialog__notice {
  margin: 0;
  color: var(--os-text-muted);
  font-size: 0.8125rem;
  line-height: 1.5;
}

.vault-import-dialog__mode {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.vault-import-dialog__mode-card {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.14s ease,
    background 0.14s ease,
    box-shadow 0.14s ease;
}

.vault-import-dialog__mode-card strong {
  color: var(--os-text-title);
  font-size: 0.875rem;
}

.vault-import-dialog__mode-card span {
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.4;
}

.vault-import-dialog__mode-card--active {
  border-color: #9db7f5;
  background: var(--os-primary-tint);
  box-shadow: 0 0 0 3px var(--os-focus-ring);
}

.vault-import-dialog__drop {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 28px 16px;
  border: 1.5px dashed var(--os-border);
  border-radius: 10px;
  background: linear-gradient(180deg, #fbfcfe 0%, #f6f9fc 100%);
  text-align: center;
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    box-shadow 0.16s ease;
}

.vault-import-dialog__drop:hover,
.vault-import-dialog__drop--active {
  border-color: #6b93f0;
  background: var(--os-primary-tint);
  box-shadow: 0 0 0 3px var(--os-focus-ring);
}

.vault-import-dialog__drop--has-file {
  border-style: solid;
  border-color: #c7d7fb;
}

.vault-import-dialog__file-input {
  display: none;
}

.vault-import-dialog__drop-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: #fff;
  color: var(--os-primary);
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.06);
}

.vault-import-dialog__drop-title {
  margin: 4px 0 0;
  color: var(--os-text-title);
  font-size: 0.9375rem;
  font-weight: 650;
  word-break: break-all;
}

.vault-import-dialog__drop-meta {
  margin: 0;
  color: var(--os-text-muted);
  font-size: 0.75rem;
}

.vault-import-dialog__formats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.vault-import-dialog__formats span {
  padding: 2px 8px;
  border-radius: 999px;
  background: #eef2f6;
  color: var(--os-text-muted);
  font-size: 0.6875rem;
  font-weight: 600;
}

.vault-import-dialog__working {
  padding: 12px 4px 8px;
}

.vault-import-dialog__progress {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 0 0 14px;
  padding: 0;
  list-style: none;
}

.vault-import-dialog__progress-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--os-text-muted);
  font-size: 0.75rem;
}

.vault-import-dialog__progress-item--active {
  color: var(--os-primary);
  font-weight: 650;
}

.vault-import-dialog__progress-item--done {
  color: #108c3d;
}

.vault-import-dialog__working-title {
  margin: 0 0 6px;
  color: var(--os-text-title);
  font-size: 0.9375rem;
  font-weight: 650;
  text-align: center;
}

.vault-import-dialog__working-meta {
  margin: 0;
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.45;
  text-align: center;
}

.vault-import-dialog__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.vault-import-dialog__stat {
  display: grid;
  gap: 2px;
  padding: 10px 12px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.14s ease,
    background 0.14s ease;
}

.vault-import-dialog__stat strong {
  color: var(--os-text-title);
  font-size: 1.125rem;
  line-height: 1.2;
}

.vault-import-dialog__stat span {
  color: var(--os-text-muted);
  font-size: 0.75rem;
}

.vault-import-dialog__stat--warn strong {
  color: #b54708;
}

.vault-import-dialog__stat--muted strong {
  color: var(--os-text-body);
}

.vault-import-dialog__stat--active {
  border-color: #9db7f5;
  background: var(--os-primary-tint);
}

.vault-import-dialog__meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.vault-import-dialog__meta,
.vault-import-dialog__hint {
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.4;
}

.vault-import-dialog__hint {
  margin: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fffaf0;
  color: #b54708;
}

.vault-import-dialog__hint--muted {
  background: #eef2f6;
  color: var(--os-text-muted);
}

.vault-import-dialog__list {
  display: grid;
  gap: 8px;
  max-height: min(48vh, 420px);
  overflow: auto;
  padding-right: 2px;
}

.vault-import-dialog__item {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  background: #fff;
}

.vault-import-dialog__item--review {
  border-color: #f0d7a8;
  background: linear-gradient(180deg, #fffdf8 0%, #fff 48%);
}

.vault-import-dialog__item--gap {
  border-color: #f3b0b0;
  background: linear-gradient(180deg, #fff7f7 0%, #fff 52%);
  box-shadow: inset 3px 0 0 #d92d20;
}

.vault-import-dialog__item--skipped {
  border-color: #e1e6ee;
  background: #fafbfc;
}

.vault-import-dialog__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.vault-import-dialog__filter {
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--os-border);
  border-radius: 999px;
  background: #fff;
  color: var(--os-text-body);
  font-size: 0.75rem;
  line-height: 1;
  cursor: pointer;
  transition:
    border-color 0.14s ease,
    background 0.14s ease,
    color 0.14s ease;
}

.vault-import-dialog__filter--danger {
  border-color: #f3b0b0;
  color: #b42318;
}

.vault-import-dialog__filter--active {
  border-color: #9db7f5;
  background: var(--os-primary-tint);
  color: var(--os-primary);
  font-weight: 600;
}

.vault-import-dialog__filter--danger.vault-import-dialog__filter--active {
  border-color: #f3b0b0;
  background: #fef3f2;
  color: #b42318;
}

.vault-import-dialog__gap {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #b42318;
  font-size: 0.75rem;
  font-weight: 650;
  line-height: 1.35;
}

.vault-import-dialog__identity {
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.4;
}

.vault-import-dialog__control--miss :deep(.v-field) {
  border-radius: 6px;
  box-shadow: inset 0 0 0 1px #f04438;
  background: #fff8f7;
}

.vault-import-dialog__item-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: var(--os-text-muted);
  font-size: 0.6875rem;
}

.vault-import-dialog__item-name {
  color: var(--os-text-title);
  font-size: 0.875rem;
}

.vault-import-dialog__item-sub,
.vault-import-dialog__skip-reasons {
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.4;
}

.vault-import-dialog__skip-reasons {
  display: grid;
  gap: 2px;
}

.vault-import-dialog__edit-grid {
  display: grid;
  gap: 8px;
}

.vault-import-dialog__issues {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.vault-import-dialog__issue {
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 0.6875rem;
  line-height: 1.4;
}

.vault-import-dialog__issue--info {
  background: var(--os-primary-tint);
  color: var(--os-primary);
}

.vault-import-dialog__issue--warn {
  background: #fffaeb;
  color: #b54708;
}

.vault-import-dialog__issue--danger {
  background: #fef3f2;
  color: #b42318;
  font-weight: 600;
}

.vault-import-dialog__issue--muted {
  background: #eef2f6;
  color: var(--os-text-muted);
}

.vault-import-dialog__item-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.vault-import-dialog__empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 28px 12px;
  color: var(--os-text-muted);
  font-size: 0.8125rem;
  text-align: center;
}

.vault-import-dialog__reauth {
  display: grid;
  gap: 14px;
}

.vault-import-dialog__reauth-card {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  border-radius: 8px;
  background: var(--os-primary-tint);
}

.vault-import-dialog__reauth-title {
  color: var(--os-text-title);
  font-size: 0.875rem;
  font-weight: 650;
}

.vault-import-dialog__reauth-meta {
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.45;
}

.vault-import-dialog__result {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 20px 8px 8px;
  text-align: center;
}

.vault-import-dialog__result-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 16px;
}

.vault-import-dialog__result-icon--ok {
  background: rgba(21, 190, 83, 0.12);
  color: #108c3d;
}

.vault-import-dialog__result-icon--warn {
  background: #fffaeb;
  color: #b54708;
}

.vault-import-dialog__result-title {
  margin: 4px 0 0;
  color: var(--os-text-title);
  font-size: 1.0625rem;
  font-weight: 650;
}

.vault-import-dialog__result-meta,
.vault-import-dialog__result-fail {
  color: var(--os-text-muted);
  font-size: 0.8125rem;
  line-height: 1.45;
}

.vault-import-dialog__result-fail {
  color: #b42318;
}

.vault-import-dialog__actions {
  flex-wrap: wrap;
}

.vault-import-preview__body {
  display: grid;
  gap: 14px;
}

.vault-import-preview__dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.vault-import-preview__dl dt {
  color: var(--os-text-muted);
  font-size: 0.6875rem;
}

.vault-import-preview__dl dd {
  margin: 2px 0 0;
  color: var(--os-text-title);
  font-size: 0.875rem;
  word-break: break-all;
}

.vault-import-preview__block h3 {
  margin: 0 0 6px;
  color: var(--os-text-title);
  font-size: 0.8125rem;
  font-weight: 650;
}

.vault-import-preview__block ul {
  margin: 0;
  padding-left: 1.1rem;
  color: var(--os-text-body);
  font-size: 0.8125rem;
  line-height: 1.45;
}

.vault-import-preview__fields {
  list-style: none;
  padding: 0;
  display: grid;
  gap: 6px;
}

.vault-import-preview__fields li {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  font-size: 0.8125rem;
}

.vault-import-preview__fields span {
  color: var(--os-text-muted);
}

.vault-import-preview__fields strong {
  color: var(--os-text-title);
  font-weight: 600;
  text-align: right;
  word-break: break-all;
}

.vault-import-preview__notes {
  color: var(--os-text-muted);
  font-size: 0.8125rem;
  line-height: 1.45;
}

@media (min-width: 700px) {
  .vault-import-dialog__edit-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 600px) {
  .vault-import-dialog__mode {
    grid-template-columns: 1fr;
  }

  .vault-import-dialog__step-label {
    display: none;
  }

  .vault-import-dialog__steps,
  .vault-import-dialog__progress {
    justify-items: center;
  }

  .vault-import-dialog__step,
  .vault-import-dialog__progress-item {
    justify-content: center;
  }

  .vault-import-dialog__progress-item span {
    display: none;
  }

  .vault-import-preview__dl {
    grid-template-columns: 1fr;
  }
}
</style>
