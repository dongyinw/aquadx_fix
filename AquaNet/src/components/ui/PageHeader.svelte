<!-- Standard page heading: eyebrow, title, description and an optional actions slot. -->
<script lang="ts">
  export let title: string
  export let eyebrow = ""
  export let description = ""
</script>

<header class="page-header">
  <div class="page-header-copy">
    {#if eyebrow}<span class="eyebrow">{eyebrow}</span>{/if}
    <h1>{title}</h1>
    {#if description}<p>{description}</p>{/if}
    <slot name="meta" />
  </div>
  {#if $$slots.default}<div class="page-header-actions"><slot /></div>{/if}
</header>

<style lang="sass">
  @use "../../vars"

  .page-header
    display: flex
    align-items: flex-end
    justify-content: space-between
    gap: 16px
    flex-wrap: wrap

  .page-header-copy
    display: grid
    gap: 4px
    min-width: 0

    h1
      margin: 0

    p
      max-width: 640px
      margin: 0
      color: var(--text-2)
      font-size: 0.95rem

  .page-header-actions
    display: flex
    align-items: center
    gap: 8px
    flex-wrap: wrap

  @media (max-width: vars.$w-mobile)
    .page-header-actions
      width: 100%
</style>
