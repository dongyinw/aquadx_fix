<script lang="ts">
  import { fly } from "svelte/transition"
  import { flip } from "svelte/animate"
  import Icon from "./Icon.svelte"
  import { dismissToast, toasts } from "../../libs/toast"

  const icons = {
    success: "solar:check-circle-bold",
    error: "solar:danger-circle-bold",
    warning: "solar:danger-triangle-bold",
    info: "solar:info-circle-bold",
  }
</script>

<div class="toaster" aria-live="polite" aria-relevant="additions">
  {#each $toasts as item (item.id)}
    <div class="toast {item.kind}" role={item.kind === "error" ? "alert" : "status"}
         in:fly={{ y: 16, duration: 180 }} out:fly={{ x: 40, duration: 160 }} animate:flip={{ duration: 160 }}>
      <Icon icon={icons[item.kind]} />
      <span>{item.message}</span>
      <button class="btn-ghost btn-icon btn-sm" type="button" aria-label="关闭通知" on:click={() => dismissToast(item.id)}>
        <Icon icon="line-md:close" />
      </button>
    </div>
  {/each}
</div>

<style lang="sass">
  @use "../../vars"

  .toaster
    position: fixed
    right: 20px
    bottom: 20px
    z-index: 120
    display: flex
    flex-direction: column
    align-items: flex-end
    gap: 8px
    width: min(400px, calc(100vw - 32px))
    pointer-events: none

  .toast
    display: flex
    align-items: center
    gap: 10px
    width: 100%
    padding: 10px 8px 10px 14px
    border: 1px solid var(--border)
    border-radius: var(--radius)
    color: var(--text)
    background: var(--surface)
    box-shadow: var(--shadow-lg)
    font-size: 0.9rem
    pointer-events: auto

    > :global(svg)
      flex: 0 0 auto
      font-size: 1.25rem

    span
      flex: 1
      min-width: 0
      overflow-wrap: anywhere

    &.success > :global(svg)
      color: var(--success)
    &.error > :global(svg)
      color: var(--danger)
    &.warning > :global(svg)
      color: var(--warning)
    &.info > :global(svg)
      color: var(--info)

  @media (max-width: vars.$w-tablet)
    .toaster
      right: 50%
      bottom: calc(76px + env(safe-area-inset-bottom))
      transform: translateX(50%)
      align-items: center
</style>
