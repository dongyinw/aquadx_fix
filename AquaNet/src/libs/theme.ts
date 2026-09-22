export type MikuNetTheme = "light" | "dark"

const THEME_KEY = "mikunet-theme"

function isTheme(value: string | null): value is MikuNetTheme {
  return value === "light" || value === "dark"
}

export function getTheme(): MikuNetTheme {
  if (typeof window === "undefined") return "light"
  const stored = localStorage.getItem(THEME_KEY)
  if (isTheme(stored)) return stored
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light"
}

export function applyTheme(theme: MikuNetTheme): MikuNetTheme {
  if (typeof document === "undefined") return theme
  document.documentElement.dataset.theme = theme
  localStorage.setItem(THEME_KEY, theme)
  return theme
}

export function initTheme(): MikuNetTheme {
  return applyTheme(getTheme())
}

export function toggleTheme(current: MikuNetTheme): MikuNetTheme {
  return applyTheme(current === "light" ? "dark" : "light")
}
