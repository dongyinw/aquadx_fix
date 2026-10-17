<!-- Blocking loading state: a top progress bar immediately, a spinner card only if loading takes a while. -->
<script lang="ts">
  import { onDestroy, onMount } from "svelte"
  import { fade } from "svelte/transition"
  import Spinner from "./Spinner.svelte"
  import { t } from "../../libs/i18n"

  let showCard = false
  let timer: ReturnType<typeof setTimeout>
  onMount(() => timer = setTimeout(() => showCard = true, 450))
  onDestroy(() => clearTimeout(timer))
</script>

<div class="loading-layer" aria-busy="true">
  <div class="progress"><span></span></div>
  {#if showCard}
    <div class="loading-card" transition:fade={{ duration: 150 }}>
      <Spinner size="20px" />
      <span>{t("loading")}</span>
    </div>
  {/if}
</div>

<style lang="sass">
  .loading-layer
    position: fixed
    inset: 0
    z-index: 90
    display: flex
    align-items: center
    justify-content: center
    cursor: progress

  .progress
    position: absolute
    top: 0
    left: 0
    right: 0
    height: 3px
    overflow: hidden
    background: color-mix(in srgb, var(--primary) 15%, transparent)

    span
      position: absolute
      inset: 0 auto 0 0
      width: 40%
      background: var(--primary)
      animation: indeterminate 1.1s var(--ease) infinite

  .loading-card
    display: inline-flex
    align-items: center
    gap: 10px
    padding: 12px 18px
    border: 1px solid var(--border)
    border-radius: var(--radius-full)
    color: var(--text-2)
    background: var(--surface)
    box-shadow: var(--shadow-lg)
    font-size: 0.9rem
    font-weight: 500

    :global(.spinner)
      color: var(--primary)

  @keyframes indeterminate
    0%
      transform: translateX(-100%)
    100%
      transform: translateX(260%)
</style>
