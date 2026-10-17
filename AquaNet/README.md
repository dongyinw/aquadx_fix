# AquaNet

This is the codebase for the new frontend of AquaDX. 
This project is also heavily WIP, so more details will be added later on.

## Development

This project uses Svelte (NOT SvelteKit) + TypeScript + Sass, built using Vite. The preferred editor is VSCode.

### Running locally

You will need to have Node.js (or equivalent) and `pnpm` installed, Start by installing dependencies by running:
```
pnpm install
```

Then, you would need to start your testing AquaDX server and configure the `VITE_AQUA_HOST` in `.env` (copy `.env.example` file to `.env`) to use your URL.
Please leave `VITE_DATA_HOST` unchanged if you're not sure what it is.

Finally, run:
```
pnpm run dev
```
### Running locally (with bun)

Alternatively, If you prefer [Bun](https://bun.sh), You can do the same as above.
Start by configuring the `VITE_AQUA_HOST` in `.env` (copy `.env.example` file to `.env`) to use your URL.

and finally:
```
bun install
bun run dev
```

### Local frontend assets

UI icons use `src/components/ui/Icon.svelte`, the offline Iconify component, and
`src/libs/icons/collections.json`. The checked-in subset contains all icons used by
the frontend; collection sources and licenses are recorded in `licenses.json`.
New icons must also be added to this local subset instead of using the online component:
write the icon name as a plain string literal (e.g. `icon="solar:home-2-bold-duotone"`) and run
`pnpm run icons`, which regenerates `collections.json` from every icon referenced under `src/`.

### UI conventions

- Theme colours, radii and shadows are CSS custom properties in `src/app.sass` (light + dark);
  `src/vars.sass` aliases them for Sass. Avoid hard-coded colours so both themes keep working.
- Shared building blocks live in `src/components/ui/` (`Dialog`, `PageHeader`, `EmptyState`,
  `Spinner`, `Toaster`) and global classes in `app.sass` (`.page`, `.card`, `.btn-*`, `.segmented`,
  `.tabs`, `.setting-group`, `.data-table`, `.alert`, `.badge`, `.skeleton`).
- Use `toast` from `libs/toast.ts` for action feedback and `confirmDialog` from `libs/confirm.ts`
  instead of `window.alert` / `window.confirm`.
- Internal links are handled client-side (`libs/router.ts`); the routed page is remounted on every
  navigation, so pages can keep reading `window.location` on init.
- UnoCSS runs in svelte-scoped mode: avoid class names that are also UnoCSS utilities
  (`table`, `m1`, `hidden`, …) for custom styles.

Day.js 1.11.13 and its locale scripts are vendored in `public/assets/vendor/dayjs`.
The Vite transform redirects cal-heatmap locale loading to these same-origin files
and normalizes locale global names (for example, `zh-cn` to `zh_cn`). The local core
script must remain before the application's module script in `index.html`.
Both development and production builds use the local assets.
