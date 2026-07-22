import { defineStore } from 'pinia'
import {
  announcementsApi,
  type AnnouncementInbox,
  type UserAnnouncement,
} from '@/api/announcements'

const emptyInbox: AnnouncementInbox = {
  unreadCount: 0,
  latestUnread: null,
  pinned: null,
}

export const useAnnouncementsStore = defineStore('announcements', {
  state: () => ({
    inbox: emptyInbox as AnnouncementInbox,
    items: [] as UserAnnouncement[],
    forceOpen: false,
    listOpen: false,
    loadingInbox: false,
    loadingList: false,
    acknowledging: false,
    ready: false,
  }),
  getters: {
    hasUnread: (state) => state.inbox.unreadCount > 0,
    pinnedBanner: (state) => state.inbox.pinned,
    latestUnread: (state) => state.inbox.latestUnread,
  },
  actions: {
    clear() {
      this.inbox = { ...emptyInbox }
      this.items = []
      this.forceOpen = false
      this.listOpen = false
      this.ready = false
    },
    async refreshInbox() {
      this.loadingInbox = true
      try {
        this.inbox = await announcementsApi.inbox()
        this.forceOpen = Boolean(this.inbox.latestUnread)
        this.ready = true
      } finally {
        this.loadingInbox = false
      }
    },
    async openList() {
      this.listOpen = true
      this.loadingList = true
      try {
        this.items = await announcementsApi.list()
        await this.refreshInbox()
      } finally {
        this.loadingList = false
      }
    },
    async acknowledgeLatestUnread() {
      const target = this.inbox.latestUnread
      if (!target) {
        this.forceOpen = false
        return
      }
      this.acknowledging = true
      try {
        await announcementsApi.markRead(target.id)
        await this.refreshInbox()
        if (this.listOpen) {
          this.items = await announcementsApi.list()
        }
      } finally {
        this.acknowledging = false
      }
    },
    async markRead(id: string) {
      await announcementsApi.markRead(id)
      await this.refreshInbox()
      if (this.listOpen) {
        this.items = await announcementsApi.list()
      }
    },
  },
})
