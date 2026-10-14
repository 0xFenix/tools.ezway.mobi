/** Tailwind CSS v3 (pinned: v4 output breaks old browsers, e.g. WS2012). */
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js}'],
  theme: {
    extend: {
      colors: {
        ink: '#0F172A',
        card: '#1B2336',
        mutedbox: '#272F42',
        mutetext: '#94A3B8',
        line: '#475569',
        accent: '#22C55E',
        onaccent: '#0F172A',
        danger: '#EF4444',
        warn: '#F59E0B'
      },
      fontFamily: {
        sans: ['"IBM Plex Sans"', 'system-ui', 'Segoe UI', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'Consolas', 'monospace']
      }
    }
  },
  plugins: []
}
