<script lang="ts">
  import { onMount } from "svelte";
  import { Route, Router } from "svelte5-router";
  import Welcome from "./pages/Welcome.svelte";
  import UserHome from "./pages/UserHome.svelte";
  import Home from "./pages/Home.svelte";
  import Ranking from "./pages/Ranking.svelte";
  import { CARD, USER } from "./libs/sdk";
  import type { MikuNetUser } from "./libs/generalTypes";
  import Settings from "./pages/User/Settings.svelte";
  import MaiPhoto from "./pages/MaiPhoto.svelte";
  import { DEFAULT_GAME } from "./libs/config"
  import { t } from "./libs/i18n";
  import Transfer from "./pages/Transfer/Transfer.svelte";
  import LinkCard from "./pages/Home/LinkCard.svelte";
  import SetupInstructions from "./pages/Home/SetupInstructions.svelte";
  import PageNotFound from "./pages/PageNotFound.svelte";
  import Admin from "./pages/Admin.svelte";
  import MagicalPass from "./pages/MagicalPass.svelte";
  import Circle from "./pages/Circle.svelte";
  import Social from "./pages/Social.svelte";
  import AppHeader from "./components/layout/AppHeader.svelte";
  import MobileTabBar from "./components/layout/MobileTabBar.svelte";
  import AnnouncementBar from "./components/layout/AnnouncementBar.svelte";
  import Toaster from "./components/ui/Toaster.svelte";
  import ConfirmHost from "./components/ui/ConfirmHost.svelte";
  import { primaryNav } from "./components/layout/nav";
  import { currentLocation, inSection, interceptLinks } from "./libs/router";
  import "./libs/theme";

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
  let me: MikuNetUser | undefined
  let playedMai = false
  let recentGame: string = DEFAULT_GAME;
  const loggedIn = USER.isLoggedIn()

  if (loggedIn)
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
            .filter(k => !!game[k as keyof typeof game])
            .sort((a, b) => {
              const ga = game[a as keyof typeof game]!, gb = game[b as keyof typeof game]!
              return new Date(gb.lastLogin).getTime() - new Date(ga.lastLogin).getTime()
            })[0] ?? "mai2"
        })
      })
    }).catch(e => console.error(e))
  }

  // Default document titles; pages may refine them after loading data.
  const titles: [string, () => string][] = [
    ["/home", () => t("navigation.home")],
    ["/cards", () => t("navigation.cards")],
    ["/setup", () => t("navigation.setup")],
    ["/ranking", () => t("navigation.rankings")],
    ["/friends", () => t("navigation.friends")],
    ["/circle", () => t("navigation.circle")],
    ["/pass", () => t("navigation.pass")],
    ["/pictures", () => t("navigation.photos")],
    ["/settings", () => t("navigation.settings")],
    ["/admin", () => t("navigation.admin")],
    ["/transfer", () => t("trans.title")],
  ]
  const unsubscribeTitle = currentLocation.subscribe(path => {
    const userMatch = path.match(/^\/u\/([^/?]+)/)
    const page = userMatch ? decodeURIComponent(userMatch[1]) : titles.find(([prefix]) => inSection(path, prefix))?.[1]()
    document.title = page ? `${page} · MikuNet` : "MikuNet"
  })

  onMount(() => {
    const stop = interceptLinks(document)
    return () => { stop(); unsubscribeTitle() }
  })

  $: path = $currentLocation.split("?")[0]
  $: authPage = path === "/" || path === "/verify" || path === "/reset-password"
  $: navItems = primaryNav(me, playedMai, recentGame)
  $: showTabBar = loggedIn && !authPage
</script>

<a class="skip-link" href="#main">{t("navigation.skip")}</a>

<div class="app-shell" class:has-tabbar={showTabBar}>
  <AppHeader {me} {loggedIn} items={navItems} minimal={authPage} />
  {#if !authPage}<AnnouncementBar />{/if}

  <div id="main" class="app-main" tabindex="-1">
    <!-- Pages read window.location on init, so remount them on every navigation. -->
    {#key $currentLocation}
      <Router {url}>
        <Route path="/" component={Welcome} />
        <Route path="/verify" component={Welcome} /> <!-- For email verification only, backwards compatibility with MikuNet2 in the future -->
        <Route path="/reset-password" component={Welcome} />
        <Route path="/home" component={Home} />
        <Route path="/cards" component={LinkCard} />
        <Route path="/setup" component={SetupInstructions} />
        <Route path="/pass" component={MagicalPass} />
        <Route path="/circle" component={Circle} />
        <Route path="/friends" component={Social} />
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
    {/key}
  </div>

  {#if !authPage}
    <footer class="app-footer">
      <span>MikuNet</span><span aria-hidden="true">·</span><span>v{APP_VERSION}</span>
    </footer>
  {/if}

  {#if showTabBar}<MobileTabBar {me} items={navItems} />{/if}
</div>

<Toaster />
<ConfirmHost />

<style lang="sass">
  @use "vars"

  .skip-link
    position: fixed
    top: 8px
    left: 8px
    z-index: 200
    padding: 8px 14px
    border-radius: var(--radius-sm)
    color: var(--on-primary)
    background: var(--primary)
    transform: translateY(-200%)
    transition: transform 0.15s ease

    &:focus
      color: var(--on-primary)
      transform: none

  .app-shell
    display: flex
    flex-direction: column
    min-height: 100vh
    min-height: 100dvh

  .app-main
    flex: 1
    outline: none

  .app-footer
    display: flex
    justify-content: center
    gap: 8px
    padding: 20px 16px 28px
    color: var(--text-3)
    font-size: 0.78rem

  @media (max-width: vars.$w-tablet)
    .has-tabbar
      padding-bottom: calc(66px + env(safe-area-inset-bottom))
</style>
