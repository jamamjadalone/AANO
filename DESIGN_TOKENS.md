# AANO Design Tokens & Theme Guardrail
> **Core Mandate**: 100% Theme-Safe, Zero Hardcoded Colors, High-Contrast Readability.  
> **Brand Palette**: Luxury Executive Minimalist (Pure White Surface + Forest/Emerald Green Accents).

---

## 1. Color Palette Tokens

### Surface & Canvas
| Token | Light Theme | Dark Theme (OLED) | Usage |
| :--- | :--- | :--- | :--- |
| `?attr/colorSurface` | `#FFFFFF` | `#0A0E0B` | Activity background, main cards |
| `?attr/colorSurfaceVariant` | `#F4F6F4` | `#161D18` | Inactive chips, search bar container |
| `?attr/colorOnSurface` | `#1C1B1F` (Charcoal) | `#E2E8E4` (Pale Mint) | Primary typography, headers |
| `?attr/colorOnSurfaceVariant`| `#49454F` (Slate) | `#A2AEA6` (Muted Sage) | Snippet previews, timestamps, counts |

### Brand Accents
| Token | Light Theme | Dark Theme | Usage |
| :--- | :--- | :--- | :--- |
| `?attr/colorPrimary` | `#1B5E20` (Forest Green) | `#4CAF50` (Luminous Mint) | Active buttons, FAB, checkmarks |
| `?attr/colorSecondary` | `#2E7D32` (Emerald Green)| `#81C784` (Light Green) | Progress bars, selection borders |
| `?attr/colorError` | `#D32F2F` (Crimson) | `#EF5350` (Soft Coral) | Trash, delete confirmation buttons |

### 9 Note Category Palettes (`R.color.*`)
1. **Red**: `#E53935` (Urgent / Critical)
2. **Orange**: `#FB8C00` (In Progress)
3. **Yellow**: `#FDD835` (Ideas / Brainstorm)
4. **Green**: `#43A047` (Work / Projects)
5. **Blue**: `#1E88E5` (Personal / Family)
6. **Purple**: `#8E24AA` (Creative / Reading)
7. **Magenta**: `#D81B60` (Health / Fitness)
8. **Dark Gray**: `#546E7A` (Archive / Reference)
9. **White / Clean**: `#FAFAFA` (Standard / Minimal)

---

## 2. Typography Hierarchy Tokens

| Style Role | Font Size | Line Spacing | Weight | Usage |
| :--- | :--- | :--- | :--- | :--- |
| `titleLarge` | `22sp` | `28sp` | Bold (700) | Note editor title |
| `titleMedium` | `16sp` | `22sp` | Bold (700) | Card title in feed |
| `bodyMedium` | `15.5sp`| `1.35x` | Regular (400) | Note canvas body text |
| `bodySmall` | `13.5sp`| `18sp` | Regular (400) | Card preview snippet |
| `labelSmall` | `11.5sp`| `14sp` | Medium (500) | Filter chips, timestamps, badges |

---

## 3. Shape & Geometry Tokens

- **Cards Corner Radius**: `16dp` (`app:cardCornerRadius="16dp"`)
- **Action Bottom Sheet Corner**: `24dp` top-left, `24dp` top-right
- **Floating Search Capsule**: `28dp` pill radius
- **Filter Chips**: `20dp` rounded pill
- **Card Stroke Width**: `1dp` outline (`#E2E8E4` Light, `#2A3830` Dark)
- **Top Accent Stripe Height**: `5dp` with matching `16dp` top curved corners

---

## 4. Anti-Slop Styling Rules for AI Agents

1. **NEVER Hardcode `#FFFFFF` or `#000000`**:
   - Always use `?attr/colorSurface`, `?attr/colorOnSurface`, or theme attributes.
2. **NEVER Use Flat `0dp` Elevation with Muddy Drop Shadows**:
   - Use `1dp` crisp outline strokes (`app:strokeWidth="1dp"`) instead of heavy elevations.
3. **Touch Targets Must Be >= `48dp`**:
   - Every clickable icon, chip, and checklist row must maintain at least 48dp touch height for comfortable single-thumb operation.
4. **Always Provide ContentDescription**:
   - Every `ImageView` and `ImageButton` must have `android:contentDescription` for accessibility compliance.
