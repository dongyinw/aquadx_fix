<!-- Svelte 4.2.11 -->

<script lang="ts">
  import { slide, fade } from "svelte/transition";
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
  import { EN_REF } from "../../libs/i18n/en_ref";
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
  
  USER.me().then(m => {
    me = m
    CARD.userGames(m.username).then(cards => userGames = Object.keys(cards).filter(k => !!cards[k as GameName]) as GameName[])
  })
    .catch(e => error = e.message)

  
</script>

<main class="content">
  <div class="settings-heading">
    <div><span class="eyebrow">ACCOUNT / SETTINGS</span><h1>{t('settings.title')}</h1><p>管理个人资料与各游戏档案。</p></div>
    <nav class="settings-tabs">
      {#each Object.entries(pages).filter(v => v[0] == "profile" || v[0] == "global" || userGames.includes(v[0] as GameName)) as tab}
        <a href={`/settings/${tab[0] != "profile" ? tab[0] : ""}`} transition:slide={{axis: 'x'}} 
          class:active={tab[0] == page || (tab[0] == "profile" && !page)} role="button" tabindex="0">
          <span>{ts(`settings.tabs.${tab[0]}`)}</span>
        </a>
      {/each}
    </nav>
  </div>

  <h2 class="header">
    {t('settings.page-title', {page: ts(`settings.tabs.${page}`)})}
  </h2>

  {#if pages[page]}
    <svelte:component this={pages[page]} />
  {/if}
</main>
<StatusOverlays {error} />
<style lang="sass">
  @use "../../vars"

  .settings-heading
    display: flex
    align-items: flex-end
    justify-content: space-between
    gap: 20px

    h1
      margin: 6px 0 4px

    p
      margin: 0
      color: vars.$c-sub
      font-size: 0.84rem

  .eyebrow
    color: vars.$c-main
    font-size: 0.7rem
    font-weight: 800
    letter-spacing: 0.14em

  .settings-tabs
    display: flex
    flex-wrap: wrap
    gap: 5px
    padding: 4px
    border: 1px solid rgba(96, 114, 118, 0.13)
    border-radius: 13px
    background: rgba(255, 255, 255, 0.42)

    a
      padding: 8px 11px
      border-radius: 9px
      color: vars.$c-sub
      font-size: 0.78rem
      font-weight: 700

      &:hover, &.active
        color: vars.$c-main
        background: vars.$c-main-soft

  h2.header
    margin: 12px 0 0
    color: vars.$c-text
    font-size: 1.25rem

  @media (max-width: vars.$w-mobile)
    .settings-heading
      align-items: flex-start
      flex-direction: column

    .settings-tabs
      width: 100%
      overflow-x: auto
      flex-wrap: nowrap
</style>
