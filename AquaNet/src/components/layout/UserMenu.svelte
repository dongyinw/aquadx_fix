<!-- Desktop account dropdown (disclosure pattern). -->
<script lang="ts">
  import { fly } from "svelte/transition"
  import Icon from "../ui/Icon.svelte"
  import ThemeSwitch from "./ThemeSwitch.svelte"
  import { pfp } from "../../libs/ui"
  import { t } from "../../libs/i18n"
  import { USER } from "../../libs/sdk"
  import { currentLocation } from "../../libs/router"
  import type { MikuNetUser } from "../../libs/generalTypes"
  import { accountNav, isActive } from "./nav"

  export let me: MikuNetUser

  let open = false
  let root: HTMLDivElement
  let button: HTMLButtonElement

  $: items = accountNav(me)
  // Close when the route changes.
  $: $currentLocation, open = false

  function onWindowClick(e: MouseEvent) {
    if (open && !root.contains(e.target as Node)) open = false
  }

  function onKeydown(e: KeyboardEvent) {
    if (e.key === "Escape" && open) {
      open = false
      button.focus()
    }
  }
</script>

<svelte:window on:click={onWindowClick} on:keydown={onKeydown} />

<div class="user-menu" bind:this={root}>
  <button class="trigger btn-ghost" bind:this={button} type="button" aria-expanded={open} aria-haspopup="true"
          aria-controls="user-menu-panel" on:click={() => open = !open}>
    <img class="avatar" alt="" use:pfp={me} />
    <span class="name truncate">{me.computedName}</span>
    <Icon icon="line-md:chevron-small-down" />
  </button>

  {#if open}
    <div id="user-menu-panel" class="panel-pop" transition:fly={{ y: -6, duration: 140 }}>
      <div class="identity">
        <img class="avatar large" alt="" use:pfp={me} />
        <div class="truncate">
          <strong class="truncate">{me.computedName}</strong>
          <small class="truncate">@{me.username}</small>
        </div>
      </div>
      <nav aria-label={t("navigation.account")}>
        {#each items as item}
          <a href={item.href} class:active={isActive($currentLocation, item)} aria-current={isActive($currentLocation, item) ? "page" : undefined}>
            <Icon icon={item.icon} />{item.label}
          </a>
        {/each}
      </nav>
      <div class="menu-section">
        <span class="menu-label">{t("theme.toggle")}</span>
        <ThemeSwitch />
      </div>
      <button class="logout btn-ghost" type="button" on:click={() => USER.logout()}>
        <Icon icon="solar:logout-2-bold-duotone" />{t("settings.profile.logout")}
      </button>
    </div>
  {/if}
</div>

<style lang="sass">
  .user-menu
    position: relative

  .trigger
    gap: 8px
    max-width: 220px
    padding: 0 8px 0 4px
    color: var(--text)

    :global(svg)
      color: var(--text-3)

  .avatar
    width: 30px
    height: 30px
    flex: 0 0 auto
    border-radius: 50%
    object-fit: cover
    background: var(--surface-2)

    &.large
      width: 40px
      height: 40px

  .name
    max-width: 130px
    font-size: 0.88rem

  .panel-pop
    position: absolute
    top: calc(100% + 8px)
    right: 0
    z-index: 60
    display: flex
    flex-direction: column
    gap: 4px
    width: 280px
    padding: 8px
    border: 1px solid var(--border)
    border-radius: var(--radius-lg)
    background: var(--surface)
    box-shadow: var(--shadow-lg)

  .identity
    display: flex
    align-items: center
    gap: 10px
    padding: 8px 8px 12px
    border-bottom: 1px solid var(--border)

    div
      display: grid
      min-width: 0

    small
      color: var(--text-3)

  nav
    display: grid
    padding: 4px 0
    border-bottom: 1px solid var(--border)

    a
      display: flex
      align-items: center
      gap: 10px
      min-height: 38px
      padding: 0 10px
      border-radius: var(--radius-sm)
      color: var(--text)
      font-size: 0.9rem
      text-decoration: none

      :global(svg)
        color: var(--text-3)
        font-size: 1.15rem

      &:hover, &.active
        background: var(--surface-2)

      &.active :global(svg)
        color: var(--primary)

  .menu-section
    display: grid
    gap: 6px
    padding: 8px 4px
    border-bottom: 1px solid var(--border)

  .menu-label
    padding: 0 6px
    color: var(--text-3)
    font-size: 0.75rem
    font-weight: 600

  .logout
    justify-content: flex-start
    gap: 10px
    color: var(--danger)

    &:hover:not(:disabled)
      color: var(--danger)
      background: var(--danger-soft)
</style>
