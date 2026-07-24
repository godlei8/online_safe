import { requestForm, requestJson } from './client'

export type Profile = {
  userId: string
  username: string
  maskedPhone: string
  avatarUrl: string | null
  canChangeUsername: boolean
  usernameChangeAvailableAt: string | null
}

export const profileApi = {
  get: () => requestJson<Profile>('/api/v1/profile'),
  changeUsername: (username: string) =>
    requestJson<Profile>('/api/v1/profile/username', {
      method: 'PATCH',
      body: JSON.stringify({ username }),
    }),
  uploadAvatar: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return requestForm<Profile>('/api/v1/profile/avatar', form)
  },
}
