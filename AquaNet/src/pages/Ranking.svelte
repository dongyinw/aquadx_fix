<script lang="ts">
  import { onMount } from "svelte";
  import { title } from "../libs/ui";
  import { GAME } from "../libs/sdk";
  import type { GenericRanking } from "../libs/generalTypes";
  import type { GameName } from "../libs/scoring";
  import { GAME_TITLE } from "../libs/i18n";
  import { t } from "../libs/i18n";
  import UserCard from "../components/UserCard.svelte";
  import Tooltip from "../components/Tooltip.svelte";
  import Cap from "./Ranking/Cap.svelte";
  import { DEFAULT_GAME } from "../libs/config";

  export let game: GameName = DEFAULT_GAME;

  title(`Ranking`);

  let loadedPages: GenericRanking[][];
  let error: string | null;

  let earliestPage = 0
  //const perPage = 50

  onMount(() => {
    const url = new URL(window.location.toString())
    const pageParam = url.searchParams.get('page')
    if (pageParam)
      earliestPage = parseInt(pageParam, 10) || 0
    Promise.all([GAME.ranking(game, earliestPage)])
      .then(([users]) => {
        loadedPages = [ users ]
      })
      .catch((e) => error = e.message);
  })

  let hoveringUser = "";
  let hoverLoading = false;
</script>

<main class="content leaderboard">
  <div class="outer-title-options">
    <h2>{t("Leaderboard.Title")}</h2>
    <nav>
      {#each Object.entries(GAME_TITLE) as [k, v]}
        <a href="/ranking/{k}" class:active={k === game}>{v}</a>
      {/each}
    </nav>
  </div>

  {#if loadedPages}
    <div class="leaderboard-container">
      <div class="lb-user" on:mouseenter={() => hoveringUser = ""} role="heading" aria-level="2">
        <span class="rank">{t("Leaderboard.Rank")}</span>
        <span class="name"></span>
        <span class="rating">{t("Leaderboard.Rating")}</span>
        <span class="accuracy">{t("Leaderboard.Accuracy")}</span>
        <span class="fc">{t("Leaderboard.FC")}</span>
        <span class="ap">{t("Leaderboard.AP")}</span>
      </div>
      <Cap bind:loadedPages bind:earliestPage {game} addOffset={-1} />
      {#each loadedPages.flat() as user, i (user.rank)}
        <div class="lb-user" class:alternate={i % 2 === 1} role="listitem"
          on:mouseover={() => hoveringUser = user.username} on:focus={() => {}}>

          <span class="rank">#{user.rank}</span>
          <span class="name">
            {#if user.username !== ""}
              <a href="/u/{user.username}/{game}" class:registered={!(/user\d+/.test(user.username))}>{user.name}</a>
            {:else}
              <span>{user.name}</span>
            {/if}
          </span>
          {#if game == 'ongeki'}
            <span class="rating">{
              (user.rating / 1000).toFixed(3)
            }</span>
          {:else}
            <span class="rating">{
              game === 'chu3' ?
                (user.rating / 100).toFixed(2) :
                user.rating.toLocaleString()
            }</span>
          {/if}
          
          <span class="accuracy">{(+user.accuracy).toFixed(2)}%</span>
          <span class="fc">{user.fullCombo}</span>
          <span class="ap">{user.allPerfect}</span>
        </div>
      {/each}
    <Cap bind:loadedPages bind:earliestPage {game} addOffset={1} />
    </div>

    <Tooltip triggeredBy=".name" loading={hoverLoading}>
      <UserCard username={hoveringUser} {game} setLoading={l => hoverLoading = l} />
    </Tooltip>
  {:else}
    <p>{error}</p>
  {/if}
</main>

<style lang="sass">
  @use "../vars"

  .leaderboard-container
    display: flex
    flex-direction: column

  .outer-title-options
    display: flex
    align-items: center
    justify-content: space-between
    gap: 18px
    margin-bottom: 22px
    padding: 10px 12px
    border: 1px solid rgba(255, 255, 255, 0.78)
    border-radius: 16px
    background: rgba(255, 255, 255, 0.52)
    box-shadow: 0 12px 32px rgba(43, 72, 76, 0.08)
    backdrop-filter: blur(18px)

    h2
      flex: 0 0 auto
      margin: 0 4px
      font-size: 1.35rem

    nav
      display: flex
      flex: 1 1 auto
      justify-content: flex-end
      gap: 5px
      min-width: 0

      a
        flex: 0 0 auto
        padding: 8px 13px
        border: 1px solid transparent
        border-radius: 10px
        color: vars.$c-sub
        font-size: 0.82rem
        font-weight: 700
        white-space: nowrap

        &:hover
          color: vars.$c-main
          background: rgba(7, 143, 136, 0.08)

        &.active
          color: vars.$c-main
          border-color: rgba(7, 143, 136, 0.18)
          background: vars.$c-main-soft
          box-shadow: 0 5px 14px rgba(7, 143, 136, 0.12)

    :global(:root[data-theme="dark"]) &
      border-color: rgba(214, 250, 245, 0.14)
      background: rgba(21, 36, 38, 0.76)
      box-shadow: 0 16px 42px rgba(0, 0, 0, 0.18)

      nav a
        color: #b5e3de

        &:hover
          color: #e6f3f1
          background: rgba(86, 211, 198, 0.12)

        &.active
          color: #e6f3f1
          border-color: rgba(86, 211, 198, 0.26)
          background: rgba(7, 143, 136, 0.32)

    @media (max-width: vars.$w-mobile)
      align-items: stretch
      flex-direction: column
      gap: 8px
      padding: 12px

      nav
        justify-content: flex-start
        overflow-x: auto
        padding-bottom: 2px

        &::-webkit-scrollbar
          display: none

        scrollbar-width: none

  .lb-user
    display: flex
    align-items: center
    justify-content: space-between
    width: 100%
    gap: 12px
    border-radius: vars.$border-radius
    padding: 6px 12px
    box-sizing: border-box

    > *:not(.name)
      text-align: center

    .name
      min-width: 100px
      flex: 1

      > a
        color: unset

      .registered
        background: vars.$grad-special
        color: transparent
        -webkit-background-clip: text
        background-clip: text

    .accuracy, .rating
      width: 15%
      min-width: 45px

    .rating
      font-weight: bold
      color: white

    .fc, .ap
      width: 5%
      min-width: 20px

    @media (max-width: vars.$w-mobile)
      font-size: 0.9rem

      .accuracy
        display: none
      .modern-rating
        display: none
      .legacy-rating
        /* this is officially the worst css i've ever written dear god */
        white-space: nowrap
        overflow: hidden
        text-overflow: clip
        min-width: 3.5em !important

    &.alternate
      background-color: vars.$ov-light

      :global(:root[data-theme="dark"]) &
        background-color: rgba(255, 255, 255, 0.12)

</style>
