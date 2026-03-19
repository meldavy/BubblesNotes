/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      // Font families
      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'Consolas', 'monospace'],
      },
      
      // Font sizes
      fontSize: {
        xs: ['0.75rem', { lineHeight: '1' }],         // 12px - metadata, captions
        sm: ['0.875rem', { lineHeight: '1.25' }],     // 14px - small text, buttons
        base: ['1rem', { lineHeight: '1.5' }],        // 16px - body text
        lg: ['1.125rem', { lineHeight: '1.375' }],    // 18px - large body, card titles
        xl: ['1.25rem', { lineHeight: '1.375' }],     // 20px - section headings
        '2xl': ['1.5rem', { lineHeight: '1.375' }],   // 24px - page headings
        '3xl': ['1.875rem', { lineHeight: '1.25' }],  // 30px - hero headings
      },
      
      // Font weights
      fontWeight: {
        normal: '400',
        medium: '500',
        semibold: '600',
        bold: '700',
      },
      
      // Line heights
      lineHeight: {
        none: '1',
        tight: '1.25',
        snug: '1.375',
        normal: '1.5',
        relaxed: '1.625',
      },
      
      // Spacing (8px grid system)
      spacing: {
        '0': '0',
        '1': '0.25rem',   // 4px
        '2': '0.5rem',    // 8px
        '3': '0.75rem',   // 12px
        '4': '1rem',      // 16px
        '5': '1.25rem',   // 20px
        '6': '1.5rem',    // 24px
        '8': '2rem',      // 32px
        '10': '2.5rem',   // 40px
        '12': '3rem',     // 48px
        '16': '4rem',     // 64px
        '20': '5rem',     // 80px
        '24': '6rem',     // 96px
      },
      
      // Colors - Primary Brand (Blue)
      colors: {
        primary: {
          50: '#eff6ff',
          100: '#dbeafe',
          200: '#bfdbfe',
          300: '#93c5fd',
          400: '#60a5fa',
          500: '#3b82f6',   // Main brand color
          600: '#2563eb',
          700: '#1d4ed8',
          800: '#1e40af',
          900: '#1e3a8a',
        },
        
        // Accent Purple - AI features, premium content
        'accent-purple': {
          50: '#f5f3ff',
          100: '#ede9fe',
          200: '#ddd6fe',
          300: '#c4b5fd',
          400: '#a78bfa',
          500: '#8b5cf6',
          600: '#7c3aed',
          700: '#6d28d9',
          800: '#5b21b6',
          900: '#4c1d95',
        },
        
        // Accent Teal - URL previews, external links
        'accent-teal': {
          50: '#f0fdfa',
          100: '#ccfbf1',
          200: '#99f6e4',
          300: '#5eead4',
          400: '#2dd4bf',
          500: '#14b8a6',
          600: '#0d9488',
          700: '#0f766e',
          800: '#115e59',
          900: '#134e4a',
        },
        
        // Accent Pink - Notifications, special badges
        'accent-pink': {
          50: '#fdf2f8',
          100: '#fce7f3',
          200: '#fbcfe8',
          300: '#f9a8d4',
          400: '#f472b6',
          500: '#ec4899',
          600: '#db2777',
          700: '#be185d',
          800: '#9d174d',
          900: '#831843',
        },
        
        // Accent Amber - Highlights, featured content
        'accent-amber': {
          50: '#fffbeb',
          100: '#fef3c7',
          200: '#fde68a',
          300: '#fcd34d',
          400: '#fbbf24',
          500: '#f59e0b',
          600: '#d97706',
          700: '#b45309',
          800: '#92400e',
          900: '#78350f',
        },
        
        // Neutral/Grayscale
        neutral: {
          50: '#fafafa',    // Page background
          100: '#f4f4f5',   // Card backgrounds
          200: '#e4e4e7',   // Input borders
          300: '#d4d4d8',   // Dividers
          400: '#a1a1aa',   // Placeholder text
          500: '#71717a',   // Secondary text
          600: '#52525b',   // Body text
          700: '#3f3f46',   // Headings
          800: '#27272a',   // High emphasis
          900: '#18181b',   // Logo, icons
        },
        
        // Semantic/Status Colors
        success: {
          500: '#10b981',
          600: '#059669',
          bg: '#ecfdf5',
        },
        warning: {
          500: '#f59e0b',
          600: '#d97706',
          bg: '#fffbeb',
        },
        error: {
          500: '#ef4444',
          600: '#dc2626',
          bg: '#fef2f2',
        },
        info: {
          500: '#3b82f6',
          600: '#2563eb',
          bg: '#eff6ff',
        },
        
        // Special Purpose Colors
        link: {
          default: '#3b82f6',
          hover: '#2563eb',
          visited: '#7c3aed',
        },
        code: {
          bg: '#1e293b',
          text: '#e2e8f0',
          accent: '#60a5fa',
        },
        selection: {
          bg: 'rgba(59, 130, 246, 0.2)',
          text: '#1e40af',
        },
      },
      
      // Transitions
      transitionDuration: {
        fast: '150ms',
        normal: '250ms',
        slow: '350ms',
      },
      
      // Easing curves
      transitionTimingFunction: {
        easeOut: 'cubic-bezier(0, 0, 0.2, 1)',
        easeIn: 'cubic-bezier(0.4, 0, 1, 1)',
        easeInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
      },
      
      // Animation keyframes
      keyframes: {
        fadeIn: {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        slideUp: {
          from: { 
            opacity: '0',
            transform: 'translateY(8px)',
          },
          to: { 
            opacity: '1',
            transform: 'translateY(0)',
          },
        },
        pulse: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.5' },
        },
        slideIn: {
          from: { transform: 'translateX(-100%)' },
          to: { transform: 'translateX(0)' },
        },
      },
      
      // Animations
      animation: {
        fadeIn: 'fadeIn 250ms ease-out',
        slideUp: 'slideUp 250ms ease-out',
        pulse: 'pulse 1.5s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        slideIn: 'slideIn 250ms ease-out',
      },
      
      // Border radius
      borderRadius: {
        'xs': '0.25rem',    // 4px
        'sm': '0.375rem',   // 6px
        DEFAULT: '0.5rem',  // 8px
        'md': '0.625rem',   // 10px
        'lg': '0.75rem',    // 12px
        'xl': '1rem',       // 16px
      },
      
      // Box shadows
      boxShadow: {
        'sm': '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
        DEFAULT: '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1)',
        'md': '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1)',
        'lg': '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1)',
        'xl': '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
      },
      
      // Responsive breakpoints
      screens: {
        'sm': '640px',    // Mobile landscape
        'md': '768px',    // Tablet
        'lg': '1024px',   // Desktop
        'xl': '1280px',   // Large desktop
        '2xl': '1536px',  // Extra large desktop
      },
    },
  },
  plugins: [],
}
