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
    VBtn: { rounded: 'lg', height: 48, elevation: 0 },
    VTextField: { variant: 'outlined', density: 'comfortable', rounded: 'lg', color: 'primary', persistentHint: true },
  },
})
