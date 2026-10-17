<script lang="ts">
  import Icon from "../../components/ui/Icon.svelte";
  import PageHeader from "../../components/ui/PageHeader.svelte";
  import type { MikuNetUser } from "../../libs/generalTypes";
  import { CARD, USER } from "../../libs/sdk";
  import StatusOverlays from "../../components/StatusOverlays.svelte";
  import { t, ts } from "../../libs/i18n";
  import ChuniSettings from "../../components/settings/ChuniSettings.svelte";
  import Mai2Settings from "../../components/settings/Mai2Settings.svelte";
  import WaccaSettings from "../../components/settings/WaccaSettings.svelte";
  import GeneralGameSettings from "../../components/settings/GeneralGameSettings.svelte";
  import OngekiSettings from "../../components/settings/OngekiSettings.svelte";
  import UserSettings from "../../components/settings/UserSettings.svelte";
  import type { Component } from "svelte";
  import type { GameName } from "../../libs/scoring";

  USER.ensureLoggedIn()

  let me: MikuNetUser;
  let error: string;
  export let page: string = "profile";

  const pages: Record<string, Component> = {
    "profile": UserSettings, "chu3": ChuniSettings,
    "mai2": Mai2Settings, "wacca": WaccaSettings, 
    "ongeki": OngekiSettings, "global": GeneralGameSettings
  }

  if (!pages[page] && page)
    error = t("404", {pathname: new URL(location.href).pathname});

  let userGames: GameName[] = [];
  
  const icons: Record<string, string> = {
    profile: "solar:user-circle-bold-duotone", global: "solar:global-bold-duotone",
    mai2: "solar:gamepad-bold-duotone", chu3: "solar:gamepad-bold-duotone",
    ongeki: "solar:gamepad-bold-duotone", wacca: "solar:gamepad-bold-duotone",
  }
  $: visibleTabs = Object.keys(pages).filter(k => k == "profile" || k == "global" || userGames.includes(k as GameName))
  $: activePage = page || "profile"

  USER.me().then(m => {
    me = m
    CARD.userGames(m.username).then(cards => userGames = Object.keys(cards).filter(k => !!cards[k as GameName]) as GameName[])
  })
    .catch(e => error = e.message)

  
</script>

<main class="page settings-page">
  <PageHeader eyebrow="Settings" title={t('settings.title')} description="管理个人资料、网站偏好与各游戏档案。" />

  <div class="settings-layout">
    <nav class="settings-nav" aria-label={t('settings.title')}>
      {#each visibleTabs as tab (tab)}
        <a href={`/settings/${tab != "profile" ? tab : ""}`} class:active={tab == activePage} aria-current={tab == activePage ? "page" : undefined}>
          <Icon icon={icons[tab]} /><span>{ts(`settings.tabs.${tab}`)}</span>
        </a>
      {/each}
    </nav>

    <section class="settings-content card">
      <h2 class="content-title">{t('settings.page-title', {page: ts(`settings.tabs.${activePage}`)})}</h2>
      {#if pages[page]}
        <svelte:component this={pages[page]} />
      {/if}
    </section>
  </div>
</main>
<StatusOverlays {error} />

<style lang="sass">
  @use "../../vars"

  .settings-layout
    display: grid
    grid-template-columns: 220px minmax(0, 1fr)
    align-items: start
    gap: 20px

  .settings-nav
    position: sticky
    top: calc(var(--nav-height) + 16px)
    display: grid
    gap: 2px

    a
      display: flex
      align-items: center
      gap: 10px
      min-height: 42px
      padding: 0 12px
      border-radius: var(--radius-sm)
      color: var(--text-2)
      font-weight: 500
      text-decoration: none

      :global(svg)
        font-size: 1.2rem

      &:hover
        color: var(--text)
        background: var(--surface-2)

      &.active
        color: var(--primary-soft-text)
        background: var(--primary-soft)
        font-weight: 600

  .settings-content
    gap: 20px
    min-height: 320px

  .content-title
    margin: 0
    padding-bottom: 14px
    border-bottom: 1px solid var(--border)

  @media (max-width: vars.$w-tablet)
    .settings-layout
      grid-template-columns: 1fr

    .settings-nav
      position: static
      display: flex
      overflow-x: auto
      scrollbar-width: none

      &::-webkit-scrollbar
        display: none

      a
        flex: 0 0 auto
</style>
