import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import UnoCSS from '@unocss/svelte-scoped/vite'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    {
      name: 'local-cal-heatmap-locales',
      enforce: 'pre',
      transform(code, id) {
        if (!id.replace(/\\/g, '/').includes('/cal-heatmap/dist/')) return;
        return code.replaceAll(
          'https://cdn.jsdelivr.net/npm/dayjs@1/locale/',
          '/assets/vendor/dayjs/locale/',
        ).replaceAll(
          'window["dayjs_locale_".concat(userLocale)]',
          'window["dayjs_locale_".concat(userLocale.replace(/-/g, "_"))]',
        );
      },
    },
    UnoCSS(),
    svelte()
  ],
  optimizeDeps: { exclude: ['cal-heatmap'] },
  define: {
    APP_VERSION: JSON.stringify(process.env.npm_package_version),
  },
})
