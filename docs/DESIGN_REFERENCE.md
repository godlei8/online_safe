# Stitch design references

The authentication UI was retrieved from the existing private Stitch project **Online Security Vault**. It was not generated during this implementation.

| Screen | Stitch screen ID | Implementation use |
| --- | --- | --- |
| Login: 安全登录 (v3) - 统一风格版 | `7762b4ad9ac04f01bff0ddd6b412fb36` | `/login` |
| Registration: 创建账户 (v2) | `1aaa6f930bc54addb8e930eb4dd32e42` | `/register` |

## Sentinel Slate design tokens

- Background: `#F8FAFC`; cards: `#FFFFFF`; subtle border: `#E2E8F0`.
- Left security introduction panel: Sentinel Navy; primary action: Action Blue `#0051D5`.
- Typography: Inter with platform Chinese fallback; JetBrains Mono is reserved for sensitive values later.
- Desktop is a 50/50 two-column authentication layout. The left panel is hidden below 768px.
- Cards and inputs use 8px rounding and a restrained ambient shadow. Keep the certification footer and page-level security framing.

用户自有的 Stitch 参考图位于 [`../frontend/design-reference/`](../frontend/design-reference/)。应用使用 Vuetify 组件复现视觉规则，不嵌入生成的 HTML。

## Vault homepage optimization

The existing homepage was optimized in the same private Stitch project using the product requirements document and the repository `Design.md`.

| Screen | Stitch screen ID | Local reference |
| --- | --- | --- |
| 来源：保险箱首页 | `4f0ecf185e974a07b7fb216362900c54` | `../frontend/design-reference/stitch-vault-dashboard-current.png` |
| 优化后桌面端：保险箱首页 | `3bc78f70199b4793b6184bee023880f8` | `../frontend/design-reference/stitch-vault-dashboard-optimized.png` |
| 优化后移动端：保险箱首页 | `7717a41a92a34c4a91c1cf475bed0fd1` | `../frontend/design-reference/stitch-vault-dashboard-mobile.png` |

The homepage direction prioritizes local search, platform/channel/status/tag filtering, sorting, card/list switching, a clear “新增记录” action, metadata-only cards, and explicit hidden-sensitive-field treatment. The mobile screen collapses navigation and filters while keeping search, add, and the first record visible in the 390px first viewport.

## Admin console overview

The admin overview was generated in the same private Stitch project from the administrator requirements and the repository `Design.md`.

| Screen | Stitch screen ID | Local reference |
| --- | --- | --- |
| 桌面端：管理后台首页（系统概览） | `e81db235f42a4b13b88152960ae0f62a` | `../frontend/design-reference/stitch-admin-dashboard.png` |
| 移动端：管理后台首页 | `61624571ebd3464886055f90d676a3c8` | `../frontend/design-reference/stitch-admin-dashboard-mobile.png` |

The admin overview uses aggregate metrics, service health, security reminders, redacted security events, and module shortcuts. It intentionally contains no account/password/2FA values or other user vault plaintext. The mobile screen uses a compact top bar, 2×2 metrics, stacked alerts, quick actions, and a system-health list.
