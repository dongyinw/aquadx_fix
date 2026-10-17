<!-- Accessible modal dialog: portalled to <body>, Esc / backdrop to close, focus trap, scroll lock. -->
<script context="module" lang="ts">
  let openCount = 0
  const stack: symbol[] = []
</script>

<script lang="ts">
  import { createEventDispatcher, onDestroy, onMount, tick } from "svelte"
  import { fade, scale } from "svelte/transition"
  import Icon from "./Icon.svelte"

  export let title = ""
  export let description = ""
  export let size: "sm" | "md" | "lg" = "md"
  /** When true, Esc and backdrop clicks do not close the dialog. */
  export let persistent = false
  export let showClose = true

  const dispatch = createEventDispatcher<{ close: void }>()
  const titleId = `dialog-${Math.random().toString(36).slice(2, 9)}`
  const reduced = typeof matchMedia !== "undefined" && matchMedia("(prefers-reduced-motion: reduce)").matches
  const duration = reduced ? 0 : 160

  let panel: HTMLDivElement
  let previouslyFocused: Element | null = null
  const self = Symbol("dialog")

  function portal(node: HTMLElement) {
    document.body.appendChild(node)
    return { destroy() { node.remove() } }
  }

  function close() {
    if (!persistent) dispatch("close")
  }

  function focusable(): HTMLElement[] {
    return [...panel.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )].filter(el => el.offsetParent !== null)
  }

  function onWindowKeydown(e: KeyboardEvent) {
    // Only the top-most dialog reacts to Escape.
    if (e.key === "Escape" && stack[stack.length - 1] === self) close()
  }

  function onKeydown(e: KeyboardEvent) {
    if (e.key === "Tab" && panel) {
      const items = focusable()
      if (items.length === 0) return e.preventDefault()
      const first = items[0], last = items[items.length - 1]
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus() }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
    }
  }

  onMount(async () => {
    previouslyFocused = document.activeElement
    stack.push(self)
    if (openCount++ === 0) {
      document.body.style.paddingRight = `${window.innerWidth - document.documentElement.clientWidth}px`
      document.body.style.overflow = "hidden"
    }
    await tick()
    const target = panel?.querySelector<HTMLElement>("[autofocus], [data-autofocus]") ?? focusable().find(el => !el.classList.contains("dialog-close")) ?? panel
    target?.focus()
  })

  onDestroy(() => {
    stack.splice(stack.indexOf(self), 1)
    if (--openCount === 0) {
      document.body.style.overflow = ""
      document.body.style.paddingRight = ""
    }
    if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus()
  })
</script>

<svelte:window on:keydown={onWindowKeydown} />

<div class="dialog-root" use:portal>
  <div class="dialog-backdrop" transition:fade={{ duration }} on:click={close} role="presentation"></div>
  <div class="dialog-container" role="presentation" on:click|self={close}>
    <!-- svelte-ignore a11y_no_noninteractive_element_interactions (keydown implements the focus trap) -->
    <div class="dialog-panel size-{size}" bind:this={panel} role="dialog" aria-modal="true"
         aria-labelledby={title ? titleId : undefined} tabindex="-1" on:keydown={onKeydown}
         transition:scale={{ duration, start: 0.96 }}>
      {#if title || showClose}
        <header class="dialog-header">
          <div>
            {#if title}<h2 id={titleId}>{title}</h2>{/if}
            {#if description}<p>{description}</p>{/if}
            <slot name="header" />
          </div>
          {#if showClose && !persistent}
            <button class="btn-ghost btn-icon btn-sm dialog-close" type="button" aria-label="关闭" on:click={close}>
              <Icon icon="line-md:close" />
            </button>
          {/if}
        </header>
      {/if}
      <div class="dialog-body"><slot /></div>
      {#if $$slots.footer}<footer class="dialog-footer"><slot name="footer" /></footer>{/if}
    </div>
  </div>
</div>

<style lang="sass">
  @use "../../vars"

  .dialog-root
    position: fixed
    inset: 0
    z-index: 100

  .dialog-backdrop
    position: absolute
    inset: 0
    background: var(--backdrop)
    backdrop-filter: blur(3px)

  .dialog-container
    position: absolute
    inset: 0
    display: flex
    align-items: center
    justify-content: center
    padding: max(16px, env(safe-area-inset-top)) max(16px, env(safe-area-inset-right)) max(16px, env(safe-area-inset-bottom)) max(16px, env(safe-area-inset-left))
    overflow-y: auto
    overscroll-behavior: contain

  .dialog-panel
    display: flex
    flex-direction: column
    width: 100%
    max-height: calc(100dvh - 32px)
    border: 1px solid var(--border)
    border-radius: var(--radius-lg)
    color: var(--text)
    background: var(--surface)
    box-shadow: var(--shadow-lg)
    overflow: hidden

    &:focus
      box-shadow: var(--shadow-lg)

    &.size-sm
      max-width: 420px
    &.size-md
      max-width: 560px
    &.size-lg
      max-width: 760px

  .dialog-header
    display: flex
    align-items: flex-start
    justify-content: space-between
    gap: 12px
    padding: 20px 20px 0

    h2
      margin: 0
      font-size: 1.12rem

    p
      margin: 4px 0 0
      color: var(--text-2)
      font-size: 0.88rem

  .dialog-close
    margin: -6px -8px 0 0

  .dialog-body
    flex: 1
    min-height: 0
    padding: 16px 20px 20px
    overflow-y: auto

  .dialog-footer
    display: flex
    justify-content: flex-end
    flex-wrap: wrap
    gap: 8px
    padding: 14px 20px
    border-top: 1px solid var(--border)
    background: var(--surface-2)

  @media (max-width: vars.$w-mobile)
    .dialog-container
      align-items: flex-end
      padding: 0

    .dialog-panel
      max-height: 92dvh
      border-radius: var(--radius-xl) var(--radius-xl) 0 0
      border-bottom: 0
      padding-bottom: env(safe-area-inset-bottom)

    .dialog-footer
      :global(> *)
        flex: 1
</style>
