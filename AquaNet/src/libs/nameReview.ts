import { writable } from "svelte/store"
import { NAME_REVIEW } from "./sdk"
import type { NameRequestMine } from "./sdk"

/** The signed-in player's latest name request per game. Empty while name review is off or nobody is signed in. */
export const NAME_REQUESTS = writable<NameRequestMine[]>([])

export function refreshNameRequests() {
  return NAME_REVIEW.status().then(s => NAME_REQUESTS.set(s.requests)).catch(() => {})
}
