import { get, writable } from "svelte/store"

export type MikuNetTheme = "light" | "dark"
export type ThemePreference = MikuNetTheme | "system"

// index.html applies the stored theme before first paint using the same key.
const THEME_KEY = "mikunet-theme"
const media = window.matchMedia?.("(prefers-color-scheme: dark)")

function readPreference(): ThemePreference {
  const stored = localStorage.getItem(THEME_KEY)
  return stored === "light" || stored === "dark" || stored === "system" ? stored : "system"
}

const resolve = (pref: ThemePreference): MikuNetTheme =>
  pref === "system" ? (media?.matches ? "dark" : "light") : pref

/** What the user picked (light / dark / follow system). */
export const themePreference = writable<ThemePreference>(readPreference())
/** The theme actually in effect. */
export const theme = writable<MikuNetTheme>(resolve(readPreference()))

function apply(pref: ThemePreference) {
  const resolved = resolve(pref)
  document.documentElement.dataset.theme = resolved
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", resolved === "dark" ? "#0c1416" : "#f5f8f8")
  theme.set(resolved)
}

themePreference.subscribe(pref => {
  localStorage.setItem(THEME_KEY, pref)
  apply(pref)
})

media?.addEventListener?.("change", () => {
  if (get(themePreference) === "system") apply("system")
})

export function setThemePreference(pref: ThemePreference) {
  themePreference.set(pref)
}

/** Flip between light and dark regardless of the current preference. */
export function toggleTheme() {
  themePreference.set(get(theme) === "dark" ? "light" : "dark")
}
