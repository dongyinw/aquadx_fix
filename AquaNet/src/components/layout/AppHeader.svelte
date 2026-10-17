<script lang="ts">
  import Icon from "../ui/Icon.svelte"
  import UserMenu from "./UserMenu.svelte"
  import { t } from "../../libs/i18n"
  import { theme, toggleTheme } from "../../libs/theme"
  import { currentLocation } from "../../libs/router"
  import type { MikuNetUser } from "../../libs/generalTypes"
  import { isActive, type NavItem } from "./nav"

  export let me: MikuNetUser | undefined
  export let loggedIn: boolean
  export let items: NavItem[]
  /** Auth pages only show the brand and theme toggle. */
  export let minimal = false
</script>

<header class="app-header">
  <div class="inner">
    <a class="brand" href={loggedIn ? "/home" : "/"} aria-label="MikuNet">
      <img src="/assets/branding/mikunet-icon.webp" alt="" width="32" height="32" />
      <span>MikuNet</span>
    </a>

    {#if !minimal}
      <nav class="primary-nav" aria-label={t("navigation.main")}>
        {#each items as item (item.href)}
          {@const active = isActive($currentLocation, item)}
          <a href={item.href} class:active aria-current={active ? "page" : undefined}>{item.label}</a>
        {/each}
      </nav>
    {/if}

    <div class="actions">
      <button class="btn-ghost btn-icon theme-btn" type="button" on:click={toggleTheme}
              aria-label={t("theme.toggle")} title={$theme === "dark" ? t("theme.light") : t("theme.dark")}>
        <Icon icon={$theme === "dark" ? "solar:sun-2-bold-duotone" : "solar:moon-stars-bold-duotone"} />
      </button>
      {#if !minimal}
        {#if me}
          <div class="desktop-only"><UserMenu {me} /></div>
        {:else if !loggedIn}
          <a class="btn btn-primary btn-sm" href="/">{t("navigation.login")}</a>
        {:else}
          <span class="avatar-skeleton skeleton desktop-only"></span>
        {/if}
      {/if}
    </div>
  </div>
</header>

<style lang="sass">
  @use "../../vars"

  .app-header
    position: sticky
    top: 0
    z-index: 50
    border-bottom: 1px solid var(--border)
    background: var(--glass)
    backdrop-filter: saturate(1.6) blur(14px)
    -webkit-backdrop-filter: saturate(1.6) blur(14px)

  .inner
    display: flex
    align-items: center
    gap: 24px
    max-width: var(--page-max)
    height: var(--nav-height)
    margin: 0 auto
    padding: 0 24px

  .brand
    display: inline-flex
    align-items: center
    gap: 9px
    flex: 0 0 auto
    color: var(--text)
    font-family: var(--font-brand)
    font-size: 1.08rem
    font-weight: 700
    letter-spacing: 0.02em
    text-decoration: none

    img
      width: 32px
      height: 32px
      border-radius: 50%
      object-fit: contain

    &:hover
      color: var(--text)
      text-decoration: none

  .primary-nav
    display: flex
    align-items: center
    gap: 2px
    min-width: 0
    overflow-x: auto
    scrollbar-width: none

    &::-webkit-scrollbar
      display: none

    a
      position: relative
      flex: 0 0 auto
      padding: 7px 12px
      border-radius: var(--radius-sm)
      color: var(--text-2)
      font-size: 0.9rem
      font-weight: 500
      text-decoration: none
      white-space: nowrap
      transition: vars.$transition

      &:hover
        color: var(--text)
        background: var(--surface-2)

      &.active
        color: var(--text)
        font-weight: 600

        &::after
          content: ""
          position: absolute
          right: 12px
          bottom: -11px
          left: 12px
          height: 2px
          border-radius: 2px
          background: var(--primary)

  .actions
    display: flex
    align-items: center
    gap: 6px
    margin-left: auto

  .theme-btn
    color: var(--text-2)

  .avatar-skeleton
    width: 120px
    height: 32px
    border-radius: var(--radius-full)

  @media (max-width: vars.$w-tablet)
    .inner
      padding: 0 14px

    .primary-nav, .desktop-only
      display: none
</style>
