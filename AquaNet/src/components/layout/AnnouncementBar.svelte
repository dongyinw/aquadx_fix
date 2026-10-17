<script lang="ts">
  import { slide } from "svelte/transition"
  import Icon from "../ui/Icon.svelte"
  import { ANNOUNCEMENT } from "../../libs/announcement"
  import { t } from "../../libs/i18n"

  const KEY = "mikunet-announcement-dismissed"
  let dismissed = localStorage.getItem(KEY) ?? ""

  $: text = $ANNOUNCEMENT.trim()

  function dismiss() {
    dismissed = text
    localStorage.setItem(KEY, text)
  }
</script>

{#if text && dismissed !== text}
  <div class="announcement" role="region" aria-label={t("navigation.notice")} transition:slide={{ duration: 180 }}>
    <div class="inner">
      <Icon icon="solar:bell-bing-bold-duotone" />
      <p><strong>{t("navigation.notice")}</strong>{text}</p>
      <button class="btn-ghost btn-icon btn-sm" type="button" aria-label={t("action.dismiss")} on:click={dismiss}>
        <Icon icon="line-md:close" />
      </button>
    </div>
  </div>
{/if}

<style lang="sass">
  .announcement
    border-bottom: 1px solid color-mix(in srgb, var(--primary) 20%, transparent)
    background: var(--primary-soft)

  .inner
    display: flex
    align-items: center
    gap: 10px
    max-width: var(--page-max)
    margin: 0 auto
    padding: 6px 16px 6px 24px

    > :global(svg)
      flex: 0 0 auto
      color: var(--primary)
      font-size: 1.1rem

  p
    flex: 1
    margin: 0
    color: var(--text)
    font-size: 0.86rem
    white-space: pre-line

    strong
      margin-right: 8px
      color: var(--primary-soft-text)
</style>
