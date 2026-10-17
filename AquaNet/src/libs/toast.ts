import { writable } from "svelte/store"

export type ToastKind = "success" | "error" | "info" | "warning"
export interface ToastItem { id: number, kind: ToastKind, message: string }

export const toasts = writable<ToastItem[]>([])
let nextId = 1

export function dismissToast(id: number) {
  toasts.update(list => list.filter(item => item.id !== id))
}

function push(kind: ToastKind, message: string, duration = kind === "error" ? 6000 : 3500) {
  const id = nextId++
  toasts.update(list => [...list.slice(-3), { id, kind, message }])
  if (duration > 0) setTimeout(() => dismissToast(id), duration)
  return id
}

export const toast = {
  success: (message: string) => push("success", message),
  error: (message: string) => push("error", message),
  info: (message: string) => push("info", message),
  warning: (message: string) => push("warning", message),
}

export const errorMessage = (e: unknown) => e instanceof Error ? e.message : String(e)
