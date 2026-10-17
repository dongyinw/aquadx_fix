import { writable } from "svelte/store"
import { listen, navigate } from "svelte5-router"

/**
 * Current location (pathname + search). Updated on client-side navigation and browser back/forward.
 * Pages read window.location directly, so App remounts the routed page whenever this changes.
 */
export const currentLocation = writable(window.location.pathname + window.location.search)

listen(({ location, action, preserveScroll }) => {
  currentLocation.set(location.pathname + location.search)
  if (action === "PUSH" && !preserveScroll) window.scrollTo({ top: 0, behavior: "instant" })
})

export function go(to: string, replace = false) {
  navigate(to, { replace })
}

/** Whether `path` belongs to the section rooted at `prefix` (exact match or sub-path). */
export function inSection(path: string, prefix: string) {
  const p = path.split("?")[0].replace(/\/+$/, "") || "/"
  return p === prefix || p.startsWith(prefix + "/")
}

/**
 * Turn same-origin <a href> clicks anywhere under `root` into client-side navigation.
 * Skips modified clicks, new-tab targets, downloads, hash links and anchors marked `noroute`.
 */
export function interceptLinks(root: HTMLElement | Document = document) {
  const onClick = (event: Event) => {
    const e = event as MouseEvent
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
    const anchor = (e.target as Element | null)?.closest?.("a")
    if (!anchor || !anchor.href) return
    if ((anchor.target && anchor.target !== "_self") || anchor.hasAttribute("download") || anchor.hasAttribute("noroute")) return
    const url = new URL(anchor.href)
    if (url.origin !== location.origin) return
    if (url.pathname === location.pathname && url.search === location.search && url.hash) return
    e.preventDefault()
    navigate(url.pathname + url.search, { replace: anchor.hasAttribute("replace") })
  }
  root.addEventListener("click", onClick)
  return () => root.removeEventListener("click", onClick)
}
