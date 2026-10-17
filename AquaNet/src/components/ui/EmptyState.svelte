<script lang="ts">
  import Icon from "./Icon.svelte"

  export let icon = "solar:box-minimalistic-bold-duotone"
  export let title: string
  export let description = ""
  export let compact = false
</script>

<div class="empty-state" class:compact>
  <span class="empty-icon"><Icon {icon} /></span>
  <strong>{title}</strong>
  {#if description}<p>{description}</p>{/if}
  {#if $$slots.default}<div class="empty-actions"><slot /></div>{/if}
</div>

<style lang="sass">
  .empty-state
    display: flex
    flex-direction: column
    align-items: center
    justify-content: center
    gap: 6px
    padding: 40px 20px
    border: 1px dashed var(--border-strong)
    border-radius: var(--radius-lg)
    color: var(--text-2)
    text-align: center

    &.compact
      padding: 22px 16px

    strong
      color: var(--text)
      font-size: 0.98rem

    p
      max-width: 420px
      margin: 0
      font-size: 0.88rem

  .empty-icon
    display: grid
    place-items: center
    width: 48px
    height: 48px
    margin-bottom: 4px
    border-radius: var(--radius)
    color: var(--primary)
    background: var(--primary-soft)
    font-size: 1.6rem

  .compact .empty-icon
    width: 38px
    height: 38px
    font-size: 1.25rem

  .empty-actions
    display: flex
    gap: 8px
    margin-top: 10px
    flex-wrap: wrap
    justify-content: center
</style>
