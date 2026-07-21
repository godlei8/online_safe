import '@mdi/font/css/materialdesignicons.css'
import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import { aliases, mdi } from 'vuetify/iconsets/mdi'

export default createVuetify({
  icons: {
    defaultSet: 'mdi',
    aliases,
    sets: { mdi },
  },
  theme: {
    defaultTheme: 'onlineSafe',
    themes: {
      onlineSafe: {
        dark: false,
        colors: {
          background: '#F8FAFC',
          surface: '#FFFFFF',
          primary: '#155EEF',
          secondary: '#131B2E',
          error: '#B42318',
          warning: '#B54708',
          success: '#027A48',
          'on-surface': '#101828',
        },
      },
    },
  },
  defaults: {
    // 工具栏/筛选区默认紧凑；认证页主按钮通过 .auth-submit 等局部样式保持 48px
    VBtn: { rounded: 'lg', height: 40, elevation: 0 },
    VTextField: {
      variant: 'outlined',
      density: 'compact',
      rounded: 'lg',
      color: 'primary',
      persistentHint: true,
      hideDetails: 'auto',
    },
    VSelect: {
      variant: 'outlined',
      density: 'compact',
      rounded: 'lg',
      color: 'primary',
      hideDetails: 'auto',
    },
    VAutocomplete: {
      variant: 'outlined',
      density: 'compact',
      rounded: 'lg',
      color: 'primary',
      hideDetails: 'auto',
    },
    VCombobox: {
      variant: 'outlined',
      density: 'compact',
      rounded: 'lg',
      color: 'primary',
      hideDetails: 'auto',
    },
    VAlert: { density: 'compact', rounded: 'lg' },
    VPagination: { density: 'compact' },
  },
})
