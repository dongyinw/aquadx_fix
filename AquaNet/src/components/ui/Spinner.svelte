<script lang="ts">
  export let size = "1.2em"
  export let label = "加载中"
</script>

<span class="spinner" style:width={size} style:height={size} role="status" aria-label={label}></span>

<style lang="sass">
  .spinner
    display: inline-block
    flex: 0 0 auto
    border: 2px solid color-mix(in srgb, currentColor 22%, transparent)
    border-top-color: currentColor
    border-radius: 50%
    animation: spin 0.7s linear infinite
</style>
