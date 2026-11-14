<script lang="ts">
  import { slide } from "svelte/transition"
  import Icon from "../ui/Icon.svelte"
  import { ANNOUNCEMENT, ANNOUNCEMENT_LIST } from "../../libs/announcement"
  import { t } from "../../libs/i18n"

  // A notice is hidden once dismissed; editing it in the admin console (new updatedAt) shows it again.
  const KEY = "mikunet-announcement-dismissed"
  const LIST_KEY = "mikunet-announcements-dismissed"
  let dismissed = localStorage.getItem(KEY) ?? ""
  let dismissedList: string[] = (() => { try { return JSON.parse(localStorage.getItem(LIST_KEY) ?? "[]") } catch { return [] } })()

  $: text = $ANNOUNCEMENT.trim()
  $: notices = $ANNOUNCEMENT_LIST.filter(item => !dismissedList.includes(`${item.id}:${item.updatedAt}`))

  function dismiss() {
    dismissed = text
    localStorage.setItem(KEY, text)
  }

  function dismissNotice(id: number, updatedAt: string) {
    dismissedList = [...dismissedList, `${id}:${updatedAt}`].slice(-50)
    localStorage.setItem(LIST_KEY, JSON.stringify(dismissedList))
  }

  const icons = { info: "solar:bell-bing-bold-duotone", warning: "solar:danger-triangle-bold-duotone", danger: "solar:danger-circle-bold-duotone" }
</script>

{#each notices as notice (notice.id)}
  <div class="announcement {notice.level}" role="region" aria-label={t("navigation.notice")} transition:slide={{ duration: 180 }}>
    <div class="inner">
      <Icon icon={icons[notice.level] ?? icons.info} />
      <p><strong>{notice.title}</strong>{notice.content}</p>
      <button class="btn-ghost btn-icon btn-sm" type="button" aria-label={t("action.dismiss")} on:click={() => dismissNotice(notice.id, notice.updatedAt)}>
        <Icon icon="line-md:close" />
      </button>
    </div>
  </div>
{/each}

{#if text && dismissed !== text}
  <div class="announcement info" role="region" aria-label={t("navigation.notice")} transition:slide={{ duration: 180 }}>
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

    &.warning
      border-bottom-color: color-mix(in srgb, var(--warning) 30%, transparent)
      background: var(--warning-soft)

      .inner > :global(svg)
        color: var(--warning)

    &.danger
      border-bottom-color: color-mix(in srgb, var(--danger) 30%, transparent)
      background: color-mix(in srgb, var(--danger) 12%, var(--surface))

      .inner > :global(svg)
        color: var(--danger)

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
    overflow-wrap: anywhere

    strong
      margin-right: 8px
      color: var(--primary-soft-text)

  .warning p strong
    color: var(--warning)

  .danger p strong
    color: var(--danger)
</style>
