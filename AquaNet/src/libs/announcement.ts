import { writable } from "svelte/store"
import { ANNOUNCEMENTS } from "./sdk"
import type { Announcement } from "./sdk"

/** Legacy single-line notice read from /announcement.txt, still shown when the file has text. */
export const ANNOUNCEMENT = writable('')

/** Notices managed in the admin console that are currently active. */
export const ANNOUNCEMENT_LIST = writable<Announcement[]>([])

// These tasks must not bother the existing one. Keep them async in the background instead
void (async () => {
  let r = await fetch("/announcement.txt")
  if (!r.ok) return;

  ANNOUNCEMENT.set(await r.text())
})()

void ANNOUNCEMENTS.list().then(list => ANNOUNCEMENT_LIST.set(list)).catch(() => {})
