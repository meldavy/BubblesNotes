---
description: UI Design Phase for BubblesNotes application
---

# UI Design Phase Specification

## Overview

This phase establishes the visual design system, component library foundation, and consistent styling across the BubblesNotes application before implementing user story-specific features.

**Position in Timeline**: After Phase 3 (User Story 1 - Quick Note Creation), Before Phase 4 (Google OAuth Authentication)

**Purpose**: Create a polished, professional UI that provides excellent user experience while maintaining development efficiency through reusable components.

---

## Design Goals

### Visual Identity
- **Modern, clean aesthetic**: Minimalist design with focus on content
- **Consistent spacing**: 8px grid system for all margins/padding
- **Professional color palette**: Neutral tones with accent colors for actions
- **Typography hierarchy**: Clear visual distinction between headings, body text, and metadata

### User Experience
- **Responsive design**: Mobile-first approach, works on tablets and desktops
- **Accessibility**: WCAG 2.1 AA compliance (contrast ratios, keyboard navigation)
- **Performance**: Optimized animations, lazy-loaded components
- **Feedback**: Clear loading states, error messages, success indicators

### Technical Foundation
- **Component library**: Reusable UI components with consistent APIs
- **Design tokens**: Centralized color, spacing, typography values
- **Theme support**: Light/dark mode ready architecture
- **Animation system**: Consistent transition timings and easing curves

---

## Theme Color System

> **Note**: All colors are defined as CSS custom properties. To change the theme, update the values in `frontend/src/styles/tokens.css`.

### Primary Brand Colors
*Main brand color used for primary actions, links, and key UI elements.*

```css
--primary-50:  #eff6ff    /* Lightest - Background hover states */
--primary-100: #dbeafe   /* Very light - Subtle backgrounds */
--primary-200: #bfdbfe   /* Light - Borders, dividers */
--primary-300: #93c5fd   /* Medium-light - Disabled states */
--primary-400: #60a5fa   /* Medium - Secondary actions */
--primary-500: #3b82f6   /* **Main** - Primary buttons, active links */
--primary-600: #2563eb   /* Medium-dark - Button hover states */
--primary-700: #1d4ed8   /* Dark - Active/pressed states */
--primary-800: #1e40af   /* Very dark - High emphasis text */
--primary-900: #1e3a8a   /* Darkest - Logo, headers */
```

### Secondary/Accent Colors
*Used for special features, highlights, and visual variety.*

```css
/* Accent Purple - AI features, premium content */
--accent-purple-500: #8b5cf6
--accent-purple-600: #7c3aed

/* Accent Teal - URL previews, external links */
--accent-teal-500: #14b8a6
--accent-teal-600: #0d9488

/* Accent Pink - Notifications, special badges */
--accent-pink-500: #ec4899
--accent-pink-600: #db2777

/* Accent Amber - Highlights, featured content */
--accent-amber-500: #f59e0b
--accent-amber-600: #d97706
```

### Neutral/Grayscale Colors
*Used for text, backgrounds, borders, and structural elements.*

```css
--neutral-50:  #fafafa   /* Lightest - Page background */
--neutral-100: #f4f4f5   /* Very light - Card backgrounds */
--neutral-200: #e4e4e7   /* Light - Input borders */
--neutral-300: #d4d4d8   /* Medium-light - Dividers */
--neutral-400: #a1a1aa   /* Medium - Placeholder text */
--neutral-500: #71717a   /* Medium-dark - Secondary text */
--neutral-600: #52525b   /* Dark - Body text */
--neutral-700: #3f3f46   /* Very dark - Headings */
--neutral-800: #27272a   /* Near black - High emphasis */
--neutral-900: #18181b   /* Darkest - Logo, icons */
```

### Semantic/Status Colors
*Used for feedback messages, status indicators, and conditional styling.*

```css
/* Success - Confirmations, completed states */
--success-500: #10b981
--success-600: #059669
--success-bg:  #ecfdf5

/* Warning - Caution, pending states */
--warning-500: #f59e0b
--warning-600: #d97706
--warning-bg:  #fffbeb

/* Error/Danger - Errors, deletions, critical alerts */
--error-500: #ef4444
--error-600: #dc2626
--error-bg:  #fef2f2

/* Info - Informational messages */
--info-500: #3b82f6
--info-600: #2563eb
--info-bg:  #eff6ff
```

### Special Purpose Colors
*Used for specific UI patterns and components.*

```css
/* Link colors */
--link-default: #3b82f6
--link-hover:   #2563eb
--link-visited: #7c3aed

/* Code/Technical */
--code-bg:      #1e293b
--code-text:    #e2e8f0
--code-accent:  #60a5fa

/* Selection */
--selection-bg: rgba(59, 130, 246, 0.2)
--selection-text: #1e40af
```

### Color Usage Guidelines

| Element | Recommended Color |
|---------|-------------------|
| Primary buttons | `--primary-500` (hover: `--primary-600`) |
| Secondary buttons | `--neutral-200` (hover: `--neutral-300`) |
| Danger/delete actions | `--error-500` (hover: `--error-600`) |
| Links | `--link-default` (hover: `--link-hover`) |
| Headings | `--neutral-800` |
| Body text | `--neutral-600` |
| Muted/secondary text | `--neutral-500` |
| Page background | `--neutral-50` |
| Card backgrounds | `--neutral-100` or white with shadow |
| Borders/dividers | `--neutral-200` |
| Success indicators | `--success-500` + `--success-bg` |
| Warning indicators | `--warning-500` + `--warning-bg` |
| Error indicators | `--error-500` + `--error-bg` |
| AI/Enhancement features | `--accent-purple-500` |
| External links/previews | `--accent-teal-500` |

### Dark Mode Overrides

```css
[data-theme='dark'] {
  --primary-500: #60a5fa;      /* Lighter for dark mode */
  --neutral-50: #0f172a;       /* Dark background */
  --neutral-100: #1e293b;      /* Card backgrounds */
  --neutral-600: #cbd5e1;      /* Body text (lighter) */
  --neutral-800: #f1f5f9;      /* Headings (white) */
}
```

### Quick Theme Switching

To create a custom theme, define a new CSS class:

```css
/* Example: Forest Theme */
[data-theme='forest'] {
  --primary-500: #22c55e;      /* Green instead of blue */
  --accent-purple-500: #84cc16; /* Lime accent */
}

/* Example: Sunset Theme */
[data-theme='sunset'] {
  --primary-500: #f97316;      /* Orange instead of blue */
  --accent-pink-500: #fb7185;  /* Rose accent */
}
```

> **Tip**: Use `data-theme` attribute on `<html>` element to switch themes dynamically.

---

## Typography System

### Font Families
```css
--font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif
--font-mono: 'JetBrains Mono', 'Fira Code', Consolas, monospace
```

### Font Sizes (rem)
```css
--text-xs: 0.75    /* 12px - metadata, captions */
--text-sm: 0.875   /* 14px - small text, buttons */
--text-base: 1     /* 16px - body text */
--text-lg: 1.125   /* 18px - large body, card titles */
--text-xl: 1.25    /* 20px - section headings */
--text-2xl: 1.5    /* 24px - page headings */
--text-3xl: 1.875  /* 30px - hero headings */
```

### Font Weights
```css
--font-normal: 400
--font-medium: 500
--font-semibold: 600
--font-bold: 700
```

### Line Heights
```css
--leading-none: 1
--leading-tight: 1.25
--leading-snug: 1.375
--leading-normal: 1.5
--leading-relaxed: 1.625
```

---

## Spacing System

### Scale (rem, based on 8px grid)
```css
--space-0: 0
--space-1: 0.25    /* 4px */
--space-2: 0.5     /* 8px */
--space-3: 0.75    /* 12px */
--space-4: 1       /* 16px */
--space-5: 1.25    /* 20px */
--space-6: 1.5     /* 24px */
--space-8: 2       /* 32px */
--space-10: 2.5    /* 40px */
--space-12: 3      /* 48px */
--space-16: 4      /* 64px */
--space-20: 5      /* 80px */
--space-24: 6      /* 96px */
```

---

## Component Library Foundation

### Core Components to Create

#### 1. Button (`frontend/src/components/ui/Button.tsx`)
```typescript
interface ButtonProps {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
  disabled?: boolean;
  icon?: React.ReactNode;
  children: React.ReactNode;
  onClick?: () => void;
}
```

#### 2. Input (`frontend/src/components/ui/Input.tsx`)
```typescript
interface InputProps {
  label?: string;
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
  type?: 'text' | 'email' | 'password';
}
```

#### 3. Card (`frontend/src/components/ui/Card.tsx`)
```typescript
interface CardProps {
  title?: string;
  subtitle?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
  hoverable?: boolean;
}
```

#### 4. Modal (`frontend/src/components/ui/Modal.tsx`)
```typescript
interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  size?: 'sm' | 'md' | 'lg';
}
```

#### 5. Loading States (`frontend/src/components/ui/Loading.tsx`)
- Spinner component
- Skeleton loader for cards/lists
- Inline loading indicator

#### 6. Feedback Components
- Toast notifications (`Toast.tsx`)
- Error boundaries (`ErrorBoundary.tsx`)
- Empty states (`EmptyState.tsx`)

---

## Animation System

### Transition Timings
```css
--transition-fast: 150ms;
--transition-normal: 250ms;
--transition-slow: 350ms;
```

### Easing Curves
```css
--ease-out: cubic-bezier(0, 0, 0.2, 1);
--ease-in: cubic-bezier(0.4, 0, 1, 1);
--ease-in-out: cubic-bezier(0.4, 0, 0.2, 1);
```

### Key Animations
```css
/* Fade In */
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* Slide Up */
@keyframes slideUp {
  from { 
    opacity: 0;
    transform: translateY(8px);
  }
  to { 
    opacity: 1;
    transform: translateY(0);
  }
}

/* Pulse (loading) */
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
```

---

## Responsive Breakpoints

```css
--breakpoint-sm: 640px   /* Mobile landscape */
--breakpoint-md: 768px   /* Tablet */
--breakpoint-lg: 1024px  /* Desktop */
--breakpoint-xl: 1280px  /* Large desktop */
```

### Layout Rules
- **Mobile (< 640px)**: Single column, full-width components
- **Tablet (640-1023px)**: Two columns where appropriate
- **Desktop (>= 1024px)**: Multi-column layouts, sidebars available

---

## Dark Mode Foundation

### Color Tokens for Dark Theme
```css
[data-theme='dark'] {
  --bg-primary: #0f172a;
  --bg-secondary: #1e293b;
  --text-primary: #f8fafc;
  --text-secondary: #cbd5e1;
  --border-color: #334155;
}
```

---

## Implementation Tasks

### Phase Tasks (to add to tasks.md)

#### UI Foundation Setup
- [ ] Install and configure Tailwind CSS with custom theme
- [ ] Create design tokens file (`frontend/src/styles/tokens.css`)
- [ ] Set up global styles and CSS variables
- [ ] Configure typography plugin for prose styling

#### Core UI Components
- [ ] Create Button component with variants and sizes
- [ ] Create Input component with validation states
- [ ] Create Card component with hover effects
- [ ] Create Modal/Dialog component with animations
- [ ] Create Loading spinner and skeleton components
- [ ] Create Toast notification system

#### Layout Components
- [ ] Create Dashboard layout wrapper with responsive grid
- [ ] Create Navigation header component
- [ ] Create Sidebar component (for future use)
- [ ] Create responsive container component

#### Component Styling
- [ ] Style NoteEditor with focus states and auto-save indicator
- [ ] Style Dashboard note cards with hover effects
- [ ] Style tag chips with remove functionality
- [ ] Style search bar with autocomplete dropdown
- [ ] Style file upload area with drag-and-drop feedback

#### Accessibility & Polish
- [ ] Add keyboard navigation support
- [ ] Implement focus management in modals
- [ ] Add ARIA labels to interactive elements
- [ ] Ensure color contrast ratios meet WCAG AA
- [ ] Test responsive breakpoints on multiple devices

---

## Acceptance Criteria

1. **Visual Consistency**: All components follow the design system with consistent spacing, colors, and typography
2. **Responsive Design**: Application looks good on mobile (375px), tablet (768px), and desktop (1440px)
3. **Accessibility**: All interactive elements are keyboard navigable with proper focus states
4. **Performance**: No layout shifts during loading, smooth 60fps animations
5. **Component Reusability**: Core UI components can be used across all user stories without modification

---

## Dependencies

- **Requires**: Phase 3 completion (basic NoteEditor and Dashboard exist)
- **Enables**: All subsequent phases (provides polished UI foundation)

---

## Technical Notes

### Tailwind Configuration
The `tailwind.config.js` should be extended with:
```javascript
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: { /* custom palette */ },
      spacing: { /* custom scale */ },
      typography: { /* custom settings */ }
    }
  },
  plugins: [require('@tailwindcss/typography')]
}
```

### Recommended Dependencies
- `@headlessui/react` - Accessible UI components
- `@heroicons/react` - Icon library
- `framer-motion` - Animation library (optional)
- `clsx` / `classnames` - Conditional class management

---

*This phase ensures the application has a professional, polished appearance before implementing complex user stories.*
