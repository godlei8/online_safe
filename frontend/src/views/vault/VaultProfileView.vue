<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiRequestError } from '@/api/client'
import { profileApi, type Profile } from '@/api/profile'
import { useOsToast } from '@/composables/useOsToast'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const toast = useOsToast()

const loading = ref(true)
const savingUsername = ref(false)
const uploadingAvatar = ref(false)
const profile = ref<Profile | null>(null)
const username = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const userInitial = computed(() => (
  username.value.trim().slice(0, 1)
  || auth.session.username?.slice(0, 1)
  || '我'
).toUpperCase())
const avatarSrc = computed(() => profile.value?.avatarUrl || auth.session.avatarUrl || '')
const displayName = computed(() => profile.value?.username || auth.session.username || '用户')

const cooldownHint = computed(() => {
  if (!profile.value) return ''
  if (profile.value.canChangeUsername) return '全局唯一；保存后 30 天内不可再改'
  if (!profile.value.usernameChangeAvailableAt) return '当前不可修改用户名'
  const when = new Date(profile.value.usernameChangeAvailableAt).toLocaleString('zh-CN', { hour12: false })
  return `下次可改：${when}`
})

async function load() {
  loading.value = true
  try {
    profile.value = await profileApi.get()
    username.value = profile.value.username
    auth.patchProfile({ username: profile.value.username, avatarUrl: profile.value.avatarUrl })
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '加载个人资料失败')
  } finally {
    loading.value = false
  }
}

async function saveUsername() {
  if (!profile.value?.canChangeUsername) {
    toast.error('30 天内仅可修改一次用户名')
    return
  }
  const next = username.value.trim()
  if (!next) {
    toast.error('请输入用户名')
    return
  }
  if (next === profile.value.username) {
    toast.success('用户名未变更')
    return
  }
  savingUsername.value = true
  try {
    profile.value = await profileApi.changeUsername(next)
    username.value = profile.value.username
    auth.patchProfile({ username: profile.value.username })
    toast.success('用户名已更新，其他设备已退出')
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '用户名更新失败')
  } finally {
    savingUsername.value = false
  }
}

function pickAvatar() {
  fileInput.value?.click()
}

async function onAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    toast.error('头像不能超过 2MB')
    return
  }
  uploadingAvatar.value = true
  try {
    profile.value = await profileApi.uploadAvatar(file)
    auth.patchProfile({ avatarUrl: profile.value.avatarUrl })
    toast.success('头像已更新')
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '头像上传失败')
  } finally {
    uploadingAvatar.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="vault-content vault-profile vault-page--scroll">
    <div class="vault-title-row">
      <div class="vault-title-row__heading">
        <h1>个人中心</h1>
        <span>资料与登录标识</span>
      </div>
    </div>
    <p class="vault-privacy-note" role="note">
      <v-icon icon="mdi-account-circle-outline" size="16" />
      <span>可更换头像与登录用户名；手机号本页只读，暂不支持换绑。</span>
    </p>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <div v-else class="vault-profile__panel">
      <div class="vault-profile__identity">
        <button
          type="button"
          class="vault-profile__avatar-btn"
          :aria-label="uploadingAvatar ? '正在上传头像' : '更换头像'"
          :disabled="uploadingAvatar"
          @click="pickAvatar"
        >
          <v-avatar color="primary" size="88" class="vault-profile__avatar">
            <v-img v-if="avatarSrc" :src="avatarSrc" alt="当前头像" cover />
            <span v-else>{{ userInitial }}</span>
          </v-avatar>
          <span class="vault-profile__avatar-badge">
            <v-progress-circular v-if="uploadingAvatar" indeterminate size="16" width="2" color="white" />
            <v-icon v-else icon="mdi-camera-outline" size="16" />
          </span>
        </button>
        <div class="vault-profile__identity-text">
          <strong>{{ displayName }}</strong>
          <span>{{ profile?.maskedPhone || '—' }}</span>
          <button
            type="button"
            class="vault-profile__link-btn"
            :disabled="uploadingAvatar"
            @click="pickAvatar"
          >
            更换头像
          </button>
          <small>JPEG / PNG / WebP，最大 2MB</small>
        </div>
      </div>

      <div class="vault-profile__fields">
        <v-text-field
          :model-value="profile?.maskedPhone ?? ''"
          label="手机号"
          prepend-inner-icon="mdi-cellphone"
          readonly
          hide-details="auto"
        />
        <v-text-field
          v-model="username"
          label="用户名"
          prepend-inner-icon="mdi-account-outline"
          autocomplete="username"
          :hint="cooldownHint"
          persistent-hint
          :disabled="!profile?.canChangeUsername || savingUsername"
        />
        <div class="vault-profile__actions">
          <v-btn
            class="vault-profile__save"
            color="primary"
            variant="flat"
            size="small"
            prepend-icon="mdi-content-save-outline"
            :loading="savingUsername"
            :disabled="savingUsername || !profile?.canChangeUsername"
            @click="saveUsername"
          >
            保存用户名
          </v-btn>
        </div>
      </div>
    </div>

    <input
      ref="fileInput"
      type="file"
      class="vault-profile__file"
      accept="image/jpeg,image/png,image/webp"
      @change="onAvatarSelected"
    />
  </section>
</template>

<style scoped>
.vault-profile__panel {
  display: grid;
  gap: 22px;
  max-width: 560px;
  padding: 20px;
  border: 1px solid var(--os-border);
  border-radius: var(--os-radius-card);
  background: var(--os-surface);
}

.vault-profile__identity {
  display: flex;
  gap: 16px;
  align-items: center;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--os-border);
}

.vault-profile__avatar-btn {
  position: relative;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  border-radius: 50%;
}

.vault-profile__avatar-btn:disabled {
  cursor: wait;
}

.vault-profile__avatar {
  font-size: 1.75rem;
  font-weight: 700;
  box-shadow: 0 0 0 3px rgb(21 94 239 / 12%);
}

.vault-profile__avatar-badge {
  position: absolute;
  right: 2px;
  bottom: 2px;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 2px solid var(--os-surface);
  border-radius: 50%;
  color: #fff;
  background: var(--os-primary);
}

.vault-profile__identity-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.vault-profile__identity-text strong {
  font-size: 1.125rem;
  font-weight: 700;
  line-height: 1.3;
}

.vault-profile__identity-text > span {
  color: var(--os-text-muted);
  font-size: 0.875rem;
  font-variant-numeric: tabular-nums;
}

.vault-profile__link-btn {
  justify-self: start;
  margin-top: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--os-primary);
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
}

.vault-profile__link-btn:hover:not(:disabled) {
  text-decoration: underline;
}

.vault-profile__link-btn:disabled {
  opacity: 0.5;
  cursor: wait;
}

.vault-profile__identity-text small {
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.4;
}

.vault-profile__fields {
  display: grid;
  gap: 4px;
}

.vault-profile__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.vault-profile__save {
  min-width: 0 !important;
  min-height: 30px !important;
  height: 30px !important;
  padding-inline: 12px 14px !important;
  border-radius: 8px !important;
  box-shadow: none !important;
  font-size: 0.8125rem !important;
  font-weight: 600;
  letter-spacing: 0;
}

.vault-profile__save :deep(.v-btn__content) {
  letter-spacing: 0;
}

.vault-profile__save :deep(.v-icon) {
  font-size: 16px !important;
  margin-inline-end: 2px;
}

.vault-profile__save:hover:not(:disabled) {
  background: var(--os-primary-hover) !important;
}

.vault-profile__save:disabled {
  opacity: 0.45;
}

.vault-profile__file {
  display: none;
}

@media (max-width: 599px) {
  .vault-profile__panel {
    padding: 16px;
  }

  .vault-profile__identity {
    align-items: flex-start;
  }

  .vault-profile__actions .vault-profile__save {
    width: 100%;
  }
}
</style>
