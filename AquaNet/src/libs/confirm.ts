import { writable } from "svelte/store"

export interface ConfirmRequest {
  title: string
  message?: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
  resolve: (ok: boolean) => void
}

export const confirmRequest = writable<ConfirmRequest | null>(null)

/** In-app replacement for window.confirm(). Resolves true when the user confirms. */
export function confirmDialog(options: Omit<ConfirmRequest, "resolve"> | string): Promise<boolean> {
  const opts = typeof options === "string" ? { title: options } : options
  return new Promise(resolve => confirmRequest.set({ ...opts, resolve }))
}
