import '@mdi/font/css/materialdesignicons.css'
import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import { aliases, mdi } from 'vuetify/iconsets/mdi'

// 主题令牌与 docs/Design.md v3.0「金融级信任感」保持一致：
// 深海军蓝标题、单一信任蓝、蓝味浅灰背景；圆角与阴影在 main.scss 中统一覆盖。
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
          background: '#F6F9FC', // 页面底色：带轻微蓝味的浅灰
          surface: '#FFFFFF',
          primary: '#155EEF', // 信任蓝：唯一交互强调色
          'primary-darken-1': '#0E4FD1', // 主按钮 hover
          secondary: '#0A2540', // 深海军蓝：标题与深色品牌区
          error: '#B42318',
          warning: '#B54708',
          success: '#108C3D',
          info: '#155EEF',
          'on-background': '#425466',
          'on-surface': '#0A2540',
        },
      },
    },
  },
  defaults: {
    // 工作区默认按钮高 30px（相对旧 40 -10）；认证页主按钮见 .auth-submit（38px）
    VBtn: { height: 30, elevation: 0 },
    VTextField: {
      variant: 'outlined',
      density: 'compact',
      color: 'primary',
      persistentHint: true,
      hideDetails: 'auto',
    },
    // 下拉：字段 compact；菜单项用 os-select-menu 收紧（见 main.scss / Design.md §3.4）
    VSelect: {
      variant: 'outlined',
      density: 'compact',
      color: 'primary',
      hideDetails: 'auto',
      menuProps: { contentClass: 'os-select-menu' },
    },
    VAutocomplete: {
      variant: 'outlined',
      density: 'compact',
      color: 'primary',
      hideDetails: 'auto',
      menuProps: { contentClass: 'os-select-menu' },
    },
    VCombobox: {
      variant: 'outlined',
      density: 'compact',
      color: 'primary',
      hideDetails: 'auto',
      menuProps: { contentClass: 'os-select-menu' },
    },
    VAlert: { density: 'compact' },
    VPagination: { density: 'compact' },
  },
})
