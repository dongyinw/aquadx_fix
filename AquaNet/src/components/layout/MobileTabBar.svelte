<!-- Bottom tab bar for small screens; "More" opens a sheet with the full navigation. -->
<script lang="ts">
  import Icon from "../ui/Icon.svelte"
  import Dialog from "../ui/Dialog.svelte"
  import ThemeSwitch from "./ThemeSwitch.svelte"
  import { pfp } from "../../libs/ui"
  import { t } from "../../libs/i18n"
  import { USER } from "../../libs/sdk"
  import { currentLocation } from "../../libs/router"
  import type { MikuNetUser } from "../../libs/generalTypes"
  import { accountNav, isActive, type NavItem } from "./nav"

  export let me: MikuNetUser | undefined
  export let items: NavItem[]

  let sheetOpen = false
  $: $currentLocation, sheetOpen = false

  // Home and rankings are always tabs; the profile tab needs the user to be loaded.
  $: tabs = [
    ...items.slice(0, 2),
    ...(me ? [{ href: `/u/${me.username}`, label: t("navigation.me"), icon: "solar:user-circle-bold-duotone", match: [`/u/${me.username}`] }] : []),
  ] as NavItem[]
  $: extra = items.slice(2)
  $: account = me ? accountNav(me).slice(1) : []
  $: moreActive = [...extra, ...account].some(item => isActive($currentLocation, item))
</script>

<nav class="tabbar" aria-label={t("navigation.main")}>
  {#each tabs as item (item.href)}
    {@const active = isActive($currentLocation, item)}
    <a href={item.href} class:active aria-current={active ? "page" : undefined}>
      <Icon icon={item.icon} /><span>{item.label}</span>
    </a>
  {/each}
  <button type="button" class:active={moreActive} aria-haspopup="dialog" on:click={() => sheetOpen = true}>
    <Icon icon="solar:hamburger-menu-bold-duotone" /><span>{t("navigation.more")}</span>
  </button>
</nav>

{#if sheetOpen}
  <Dialog title={t("navigation.menu")} on:close={() => sheetOpen = false}>
    {#if me}
      <a class="identity" href={`/u/${me.username}`}>
        <img alt="" use:pfp={me} />
        <span class="truncate"><strong class="truncate">{me.computedName}</strong><small>@{me.username}</small></span>
        <Icon icon="line-md:chevron-small-right" />
      </a>
    {/if}
    {#if extra.length}
      <div class="sheet-grid">
        {#each extra as item (item.href)}
          <a href={item.href} class:active={isActive($currentLocation, item)}><Icon icon={item.icon} /><span>{item.label}</span></a>
        {/each}
      </div>
    {/if}
    <div class="sheet-list">
      {#each account as item (item.href)}
        <a href={item.href} class:active={isActive($currentLocation, item)}><Icon icon={item.icon} />{item.label}<Icon class="chev" icon="line-md:chevron-small-right" /></a>
      {/each}
    </div>
    <div class="sheet-theme"><span>{t("theme.toggle")}</span><ThemeSwitch /></div>
    <button class="btn-danger btn-block" type="button" on:click={() => USER.logout()}>
      <Icon icon="solar:logout-2-bold-duotone" />{t("settings.profile.logout")}
    </button>
  </Dialog>
{/if}

<style lang="sass">
  @use "../../vars"

  .tabbar
    position: fixed
    right: 0
    bottom: 0
    left: 0
    z-index: 50
    display: none
    padding: 4px 6px env(safe-area-inset-bottom)
    border-top: 1px solid var(--border)
    background: var(--glass)
    backdrop-filter: saturate(1.6) blur(14px)
    -webkit-backdrop-filter: saturate(1.6) blur(14px)

    a, button
      display: flex
      flex: 1
      flex-direction: column
      align-items: center
      justify-content: center
      gap: 2px
      min-height: 56px
      padding: 4px 2px
      border: 0
      border-radius: var(--radius)
      color: var(--text-3)
      background: none
      box-shadow: none
      font-size: 0.7rem
      font-weight: 600
      text-decoration: none

      :global(svg)
        font-size: 1.45rem

      &:hover:not(:disabled)
        border: 0
        color: var(--text-2)
        background: none

      &.active
        color: var(--primary)

  @media (max-width: vars.$w-tablet)
    .tabbar
      display: flex

  .identity
    display: flex
    align-items: center
    gap: 12px
    margin-bottom: 14px
    padding: 12px
    border-radius: var(--radius)
    color: var(--text)
    background: var(--surface-2)
    text-decoration: none

    img
      width: 44px
      height: 44px
      border-radius: 50%
      object-fit: cover

    span
      display: grid
      flex: 1

    small
      color: var(--text-3)

  .sheet-grid
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(96px, 1fr))
    gap: 8px
    margin-bottom: 14px

    a
      display: flex
      flex-direction: column
      align-items: center
      gap: 6px
      padding: 14px 6px
      border: 1px solid var(--border)
      border-radius: var(--radius)
      color: var(--text)
      font-size: 0.82rem
      text-decoration: none

      :global(svg)
        color: var(--primary)
        font-size: 1.5rem

      &.active
        border-color: var(--primary)
        background: var(--primary-soft)

  .sheet-list
    display: grid
    margin-bottom: 14px
    border: 1px solid var(--border)
    border-radius: var(--radius)
    overflow: hidden

    a
      display: flex
      align-items: center
      gap: 12px
      min-height: 50px
      padding: 0 14px
      border-bottom: 1px solid var(--border)
      color: var(--text)
      text-decoration: none

      &:last-child
        border-bottom: 0

      :global(svg)
        color: var(--text-3)
        font-size: 1.2rem

      :global(.chev)
        margin-left: auto

      &.active
        color: var(--primary)

  .sheet-theme
    display: grid
    gap: 8px
    margin-bottom: 14px

    span
      color: var(--text-3)
      font-size: 0.78rem
      font-weight: 600
</style>
