<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fieldTypeItems, type PrivateTemplatePayload } from '@/domain/vaultPayload'
import { useVaultItemEditor } from '@/composables/useVaultItemEditor'
import { useVaultStore } from '@/stores/vault'

const vault = useVaultStore()
const editor = useVaultItemEditor()
const loading = ref(true)
const dialog = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const form = ref<PrivateTemplatePayload>({
  name: '',
  platform: '',
  channel: '',
  fields: [],
})

onMounted(async () => {
  try {
    await vault.loadTemplates()
  } finally {
    loading.value = false
  }
})

function openCreate() {
  form.value = {
    name: '',
    platform: '',
    channel: '',
    fields: [
      {
        id: crypto.randomUUID(),
        name: '用户名',
        type: 'TEXT',
        required: false,
        sensitive: false,
        copyable: true,
        hint: '',
        order: 0,
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
      },
    ],
  }
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

async function save() {
  errorMessage.value = ''
  if (!form.value.name.trim()) {
    errorMessage.value = '请填写模板名称'
    return
  }
  saving.value = true
  try {
    await vault.saveTemplate(null, form.value)
    dialog.value = false
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function remove(id: string) {
  await vault.deleteTemplate(id)
}

function createFromTemplate(id: string) {
  editor.openCreate({ templateId: id })
}
</script>

<template>
  <section class="vault-content vault-templates-panel" aria-labelledby="templates-title">
    <div class="vault-title-row">
      <div>
        <p class="vault-eyebrow">私人模板</p>
        <div class="vault-title-row__heading">
          <h1 id="templates-title">字段结构复用</h1>
          <span>{{ vault.templates.length }} 个模板</span>
        </div>
      </div>
      <v-btn
        color="primary"
        class="vault-new-record"
        size="small"
        prepend-icon="mdi-plus"
        @click="openCreate"
      >
        新建模板
      </v-btn>
    </div>

    <div class="vault-privacy-note" role="note">
      <v-icon icon="mdi-information-outline" size="16" />
      <span>模板只保存字段名称与配置，不保存真实账密值。</span>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />
    <div v-else-if="vault.templates.length" class="vault-template-list">
      <div v-for="item in vault.templates" :key="item.envelope.id" class="vault-template-card">
        <div>
          <strong>{{ item.payload.name }}</strong>
          <p>{{ item.payload.platform || '未指定平台' }} · {{ item.payload.fields.length }} 个字段</p>
        </div>
        <div class="d-flex ga-2">
          <v-btn
            size="small"
            color="primary"
            variant="tonal"
            @click="createFromTemplate(item.envelope.id)"
          >
            用此创建
          </v-btn>
          <v-btn size="small" variant="text" color="error" @click="remove(item.envelope.id)">
            删除
          </v-btn>
        </div>
      </div>
    </div>
    <v-alert v-else type="info" variant="tonal">
      还没有私人模板。可以先创建一个常用字段组合。
    </v-alert>

    <v-dialog v-model="dialog" max-width="640">
      <v-card class="vault-template-dialog pa-6">
        <h2 class="mb-4">新建私人模板</h2>
        <v-text-field v-model="form.name" label="模板名称" />
        <div class="vault-template-dialog__columns">
          <v-text-field v-model="form.platform" label="默认平台" />
          <v-text-field v-model="form.channel" label="默认渠道" />
        </div>
        <div class="d-flex justify-space-between align-center mb-2">
          <h3>字段</h3>
          <v-btn size="small" variant="tonal" color="primary" @click="addField">添加字段</v-btn>
        </div>
        <div v-for="field in form.fields" :key="field.id" class="mb-3">
          <div class="vault-template-dialog__columns">
            <v-text-field v-model="field.name" label="名称" hide-details />
            <v-select
              v-model="field.type"
              :items="fieldTypeItems"
              label="类型"
              hide-details
            />
          </div>
          <div class="d-flex ga-2">
            <v-checkbox v-model="field.required" label="必填" hide-details density="compact" />
            <v-checkbox v-model="field.sensitive" label="敏感" hide-details density="compact" />
            <v-checkbox v-model="field.copyable" label="可复制" hide-details density="compact" />
          </div>
        </div>
        <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-3">{{ errorMessage }}</v-alert>
        <div class="d-flex justify-end ga-2">
          <v-btn variant="text" @click="dialog = false">取消</v-btn>
          <v-btn color="primary" :loading="saving" @click="save">保存模板</v-btn>
        </div>
      </v-card>
    </v-dialog>
  </section>
</template>

<style scoped>
.vault-template-list {
  display: grid;
  gap: 12px;
}

/* 模板卡：与记录卡同语言（1px 边框 + Level 1 环境阴影） */
.vault-template-card {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-radius: var(--os-radius-card);
  border: 1px solid var(--os-border);
  background: var(--os-surface);
  box-shadow: var(--os-shadow-1);
}

.vault-template-card strong { color: var(--os-text-title); }

.vault-template-card p {
  margin-top: 4px;
  color: var(--os-text-muted);
}

.vault-template-dialog__columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 599px) {
  .vault-template-card {
    align-items: stretch;
    flex-direction: column;
  }

  .vault-template-card > .d-flex {
    justify-content: flex-end;
  }

  .vault-template-card .v-btn {
    min-height: 44px;
  }

  .vault-template-dialog {
    padding: 20px !important;
  }

  .vault-template-dialog__columns {
    grid-template-columns: 1fr;
    gap: 4px;
  }
}
</style>
