<script lang="ts">
  import { Route, Router } from "svelte5-router";
  import Welcome from "./pages/Welcome.svelte";
  import UserHome from "./pages/UserHome.svelte";
  import Home from "./pages/Home.svelte";
  import Ranking from "./pages/Ranking.svelte";
  import { CARD, USER } from "./libs/sdk";
  import type { MikuNetUser } from "./libs/generalTypes";
  import Settings from "./pages/User/Settings.svelte";
  import MaiPhoto from "./pages/MaiPhoto.svelte";
  import { pfp, tooltip } from "./libs/ui"
  import { ANNOUNCEMENT } from "./libs/announcement";
  import { DEFAULT_GAME } from "./libs/config"
  import { t } from "./libs/i18n";
  import Transfer from "./pages/Transfer/Transfer.svelte";
  import { link } from "d3";
  import LinkCard from "./pages/Home/LinkCard.svelte";
  import SetupInstructions from "./pages/Home/SetupInstructions.svelte";
  import PageNotFound from "./pages/PageNotFound.svelte";
  import Admin from "./pages/Admin.svelte";
  import Icon from "@iconify/svelte";
  import { initTheme, toggleTheme, type MikuNetTheme } from "./libs/theme";

  console.log(`%c
┏━┓         ┳━┓━┓┏━
┣━┫┏━┓┓ ┏┏━┓┃ ┃ ┣┫
┛ ┗┗━┫┗━┻┗━┻┻━┛━┛┗━
     ┗       v${APP_VERSION}`, `
     background: linear-gradient(-45deg, rgba(57,197,187,1) 0%, rgba(129,230,217,1) 50%, rgba(255,102,153,1) 100%);
     font-size: 2em;
     font-family: Monospace;
     unicode-bidi: isolate;
     -webkit-background-clip: text;
     -webkit-text-fill-color: transparent;`)

  export let url = "";
  let me: MikuNetUser
  let playedMai = false

  let recentGame: string = DEFAULT_GAME;
  let theme: MikuNetTheme = initTheme();

  function switchTheme() {
    theme = toggleTheme(theme);
  }

  function logOut() {
    USER.logout();
  }

  if (USER.isLoggedIn())
  {
    USER.accessStatus().then(status => {
      if (status.banState === 2) {
        localStorage.removeItem('token')
        window.location.href = "/"
        return
      }
      USER.me().then(m => {
        me = m
        CARD.userGames(me.username).then(game => {
          playedMai = !!game.mai2
          recentGame = Object.keys(game)
            .filter(k => !!game[k])
            .sort((a, b) => {
              return (new Date(game[b].lastLogin)) - (new Date(game[a].lastLogin))
            })[0] ?? "mai2"
        })
      })
    }).catch(e => console.error(e))
  }
  let path = window.location.pathname;
</script>

<nav>
  <div class="nav-inner">
    {#if path !== "/"}
      <a class="logo" href={USER.isLoggedIn() ? "/home" : "/"}>
        <img src="/assets/icons/android-chrome-192x192.png" alt="MikuNet"/>
        <span>MikuNet</span>
      </a>
    {:else}
      <div class="logo-placeholder"></div>
    {/if}
    {#if $ANNOUNCEMENT}
      <div class="announcement">
        <strong>{t('navigation.notice')}</strong><span>{$ANNOUNCEMENT}</span>
      </div>
    {/if}
    <div class="nav-links">
      <a href="/home">{t('navigation.home')}</a>
      <a href={`/ranking/${recentGame}`}>{t('navigation.rankings')}</a>
      {#if playedMai}<a href="/pictures">photo</a>{/if}
      {#if me?.isAdmin}<a href="/admin">admin</a>{/if}
    </div>
    <div class="nav-actions">
      <button class="theme-toggle" type="button" on:click={switchTheme}
        title={theme === "light" ? "切换到深色主题" : "切换到浅色主题"}
        aria-label={theme === "light" ? "切换到深色主题" : "切换到浅色主题"}>
        <Icon icon={theme === "light" ? "solar:moon-stars-bold-duotone" : "solar:sun-2-bold-duotone"} />
      </button>
      {#if me}
        <a class="profile-link" href="/u/{me.username}" use:tooltip={t('navigation.profile')}>
          <img alt="profile" class="pfp" use:pfp={me}/>
          <span>{me.computedName}</span>
        </a>
        <button class="logout-button" type="button" on:click={logOut}
          title={t('settings.profile.logout')} aria-label={t('settings.profile.logout')}>
          <Icon icon="solar:logout-2-bold-duotone" />
          <span>{t('settings.profile.logout')}</span>
        </button>
      {/if}
    </div>
  </div>
</nav>

<Router {url}>
  <Route path="/" component={Welcome} />
  <Route path="/verify" component={Welcome} /> <!-- For email verification only, backwards compatibility with MikuNet2 in the future -->
  <Route path="/reset-password" component={Welcome} />
  <Route path="/home" component={Home} />
  <Route path="/cards" component={LinkCard} />
  <Route path="/setup" component={SetupInstructions} />
  <Route path="/ranking" component={Ranking} />
  <Route path="/ranking/:game" component={Ranking} />
  <Route path="/u/:username" component={UserHome} />
  <Route path="/u/:username/:game" component={UserHome} />
  <Route path="/settings" component={Settings} />
  <Route path="/settings/:page" component={Settings} />
  <Route path="/pictures" component={MaiPhoto} />
  <Route path="/transfer" component={Transfer} />
  <Route path="/admin" component={Admin} />
  <Route component={PageNotFound} />
</Router>

<style lang="sass">
  @use "vars"

  nav
    position: sticky
    top: 0
    z-index: 10
    padding: 12px 24px

    .nav-inner
      display: flex
      align-items: center
      gap: 20px
      max-width: 1280px
      min-height: 48px
      margin: 0 auto
      padding: 0 10px
      border: 1px solid rgba(255, 255, 255, 0.85)
      border-radius: 16px
      background: rgba(255, 255, 255, 0.72)
      box-shadow: 0 12px 36px rgba(43, 72, 76, 0.10)
      backdrop-filter: blur(22px)

    img
      width: 1.75rem
      height: 1.75rem
      border-radius: 10px
      object-fit: cover

    .logo, .logo-placeholder
      display: flex
      align-items: center
      min-width: 150px
    
    .logo
      gap: 9px
      color: vars.$c-text
      font-weight: 800
      letter-spacing: 0.12em

    .logo-placeholder
      flex: 1

    .nav-links
      display: flex
      align-items: center
      gap: 6px

      a
        padding: 8px 11px
        border-radius: 9px
        font-size: 0.9rem

        &:hover
          background: vars.$c-main-soft

    .nav-actions
      display: flex
      align-items: center
      gap: 6px
      justify-content: flex-end
      min-width: 150px
      margin-left: auto

    .theme-toggle
      display: inline-flex
      align-items: center
      justify-content: center
      width: 36px
      height: 36px
      padding: 0
      border-radius: 11px
      color: vars.$c-main
      background: rgba(255, 255, 255, 0.52)

      :global(svg)
        font-size: 1.15rem

    .profile-link
      display: flex
      align-items: center
      gap: 8px
      padding: 4px 8px 4px 4px
      border-radius: 11px
      color: vars.$c-text

      &:hover
        background: vars.$c-main-soft

      span
        max-width: 110px
        overflow: hidden
        text-overflow: ellipsis
        white-space: nowrap
        font-size: 0.82rem

    .logout-button
      display: inline-flex
      align-items: center
      justify-content: center
      gap: 5px
      min-height: 34px
      padding: 5px 9px
      border-color: rgba(196, 73, 94, 0.18)
      border-radius: 10px
      color: vars.$c-error
      background: rgba(196, 73, 94, 0.06)

      &:hover
        border-color: rgba(196, 73, 94, 0.34)
        color: vars.$c-error
        background: rgba(196, 73, 94, 0.12)

      :global(svg)
        font-size: 1rem

      span
        font-size: 0.78rem


    .pfp
      width: 2rem
      height: 2rem
      border-radius: 10px

    .announcement
      position: absolute
      left: 50%
      top: 50%
      display: flex
      align-items: center
      gap: 7px
      max-width: 34%
      transform: translate(-50%, -50%)
      overflow: hidden
      color: vars.$c-sub
      font-size: 0.78rem
      white-space: nowrap

      span
        overflow: hidden
        text-overflow: ellipsis

      strong
        color: vars.$c-main

    @media (max-width: vars.$w-mobile)
      padding: 8px 10px

      .nav-inner
        gap: 4px
        padding: 0 6px

      .logo, .logo-placeholder
        min-width: auto

      .logo > span, .profile-link > span, .logout-button > span, .announcement
        display: none

      .nav-links
        gap: 0
        margin-left: auto

        a
          padding: 8px 7px
          font-size: 0.78rem

      .nav-actions
        min-width: auto

      .theme-toggle
        width: 32px
        height: 32px
</style>
