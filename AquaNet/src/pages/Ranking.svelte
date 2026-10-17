<script lang="ts">
  import { onMount } from "svelte";
  import { GAME } from "../libs/sdk";
  import type { GenericRanking } from "../libs/generalTypes";
  import type { GameName } from "../libs/scoring";
  import { GAME_TITLE } from "../libs/i18n";
  import { t } from "../libs/i18n";
  import UserCard from "../components/UserCard.svelte";
  import Cap from "./Ranking/Cap.svelte";
  import { DEFAULT_GAME } from "../libs/config";
  import { USER } from "../libs/sdk";
  import PageHeader from "../components/ui/PageHeader.svelte";
  import EmptyState from "../components/ui/EmptyState.svelte";
  import Icon from "../components/ui/Icon.svelte";

  export let game: GameName = DEFAULT_GAME;


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

  // Highlight the signed-in player's row.
  let myUsername = ""
  if (USER.isLoggedIn()) USER.me().then(m => myUsername = m.username).catch(() => {})

  const formatRating = (rating: number) => game == 'ongeki' ? (rating / 1000).toFixed(3)
    : game === 'chu3' ? (rating / 100).toFixed(2) : rating.toLocaleString()

  // Hover preview card (mouse only), shown after a short delay.
  let hoveringUser = ""
  let hoverPos = { x: 0, y: 0 }
  let hoverTimer: ReturnType<typeof setTimeout>
  const canHover = matchMedia("(hover: hover) and (pointer: fine)").matches

  function hoverStart(e: PointerEvent, username: string) {
    if (!canHover || e.pointerType !== "mouse" || !username) return
    clearTimeout(hoverTimer)
    const x = e.clientX, y = e.clientY
    hoverTimer = setTimeout(() => {
      hoverPos = { x: Math.min(x + 16, window.innerWidth - 320), y: Math.min(y + 16, window.innerHeight - 110) }
      hoveringUser = username
    }, 350)
  }

  function hoverEnd() {
    clearTimeout(hoverTimer)
    hoveringUser = ""
  }
</script>

<svelte:window on:scroll={hoverEnd} />

<main class="page leaderboard">
  <PageHeader eyebrow="Leaderboard" title={t("Leaderboard.Title")}>
    <nav class="segmented" aria-label="游戏">
      {#each Object.entries(GAME_TITLE) as [k, v]}
        <a href="/ranking/{k}" class:active={k === game} aria-current={k === game ? "page" : undefined}>{v}</a>
      {/each}
    </nav>
  </PageHeader>

  {#if loadedPages}
    {#if loadedPages.flat().length === 0}
      <EmptyState icon="solar:ranking-bold-duotone" title="暂无排行数据" description="这个游戏还没有玩家上榜。" />
    {:else}
      <div class="card card-flush board" role="table" aria-label={t("Leaderboard.Title")}>
        <div class="lb-row lb-head" role="row">
          <span class="rank" role="columnheader">{t("Leaderboard.Rank")}</span>
          <span class="name" role="columnheader">玩家</span>
          <span class="rating" role="columnheader">{t("Leaderboard.Rating")}</span>
          <span class="accuracy" role="columnheader">{t("Leaderboard.Accuracy")}</span>
          <span class="fc" role="columnheader">{t("Leaderboard.FC")}</span>
          <span class="ap" role="columnheader">{t("Leaderboard.AP")}</span>
        </div>
        <Cap bind:loadedPages bind:earliestPage {game} addOffset={-1} />
        {#each loadedPages.flat() as user (user.rank)}
          <div class="lb-row" role="row" class:me={!!myUsername && user.username === myUsername}>
            <span class="rank" role="cell">
              {#if user.rank <= 3}<span class="medal medal-{user.rank}">{user.rank}</span>{:else}<span class="num">#{user.rank}</span>{/if}
            </span>
            <span class="name" role="cell">
              {#if user.username !== ""}
                <a href="/u/{user.username}/{game}" class:registered={!(/user\d+/.test(user.username))}
                   on:pointerenter={(e) => hoverStart(e, user.username)} on:pointerleave={hoverEnd}>{user.name}</a>
              {:else}
                <span>{user.name}</span>
              {/if}
              {#if !!myUsername && user.username === myUsername}<span class="badge primary">我</span>{/if}
            </span>
            <span class="rating num" role="cell">{formatRating(user.rating)}</span>
            <span class="accuracy num" role="cell">{(+user.accuracy).toFixed(2)}%</span>
            <span class="fc num" role="cell">{user.fullCombo}</span>
            <span class="ap num" role="cell">{user.allPerfect}</span>
          </div>
        {/each}
        <Cap bind:loadedPages bind:earliestPage {game} addOffset={1} />
      </div>
    {/if}

    {#if hoveringUser}
      <div class="hover-card" style:left="{hoverPos.x}px" style:top="{hoverPos.y}px" role="tooltip">
        <UserCard username={hoveringUser} {game} />
      </div>
    {/if}
  {:else if error}
    <div class="alert alert-danger"><Icon icon="solar:danger-triangle-bold-duotone" /><p>{error}</p></div>
  {:else}
    <div class="card card-flush board" aria-hidden="true">
      {#each Array(10) as _}<div class="lb-row"><span class="skeleton" style="height:18px;width:100%"></span></div>{/each}
    </div>
  {/if}
</main>

<style lang="sass">
  @use "../vars"

  .board
    overflow: visible

  .lb-row
    display: grid
    grid-template-columns: 72px minmax(0, 1fr) 110px 100px 64px 64px
    align-items: center
    gap: 12px
    min-height: 52px
    padding: 6px 20px
    border-bottom: 1px solid var(--border)

    &:last-of-type
      border-bottom: 0

    &:not(.lb-head):hover
      background: var(--surface-hover)

    &.me
      background: var(--primary-soft)
      box-shadow: inset 3px 0 0 var(--primary)

    > :not(.name)
      text-align: right

    .rank
      text-align: left

  .lb-head
    position: sticky
    top: var(--nav-height)
    z-index: 2
    min-height: 42px
    border-radius: var(--radius-lg) var(--radius-lg) 0 0
    color: var(--text-2)
    background: var(--surface-2)
    font-size: 0.78rem
    font-weight: 600

  .medal
    display: inline-grid
    place-items: center
    width: 28px
    height: 28px
    border-radius: 50%
    color: #fff
    font-size: 0.82rem
    font-weight: 700

    &.medal-1
      background: linear-gradient(135deg, #f5c542, #d99a0b)
    &.medal-2
      background: linear-gradient(135deg, #c3ccd4, #8d99a5)
    &.medal-3
      background: linear-gradient(135deg, #e3a06d, #b86a32)

  .name
    display: flex
    align-items: center
    gap: 8px
    min-width: 0

    a, > span:first-child
      overflow: hidden
      color: var(--text)
      font-weight: 600
      text-overflow: ellipsis
      white-space: nowrap

    .registered
      background: vars.$grad-special
      -webkit-background-clip: text
      background-clip: text
      color: transparent

  .rating
    color: var(--primary)
    font-weight: 700

  .accuracy, .fc, .ap
    color: var(--text-2)
    font-size: 0.88rem

  .hover-card
    position: fixed
    z-index: 110
    padding: 12px 14px
    border: 1px solid var(--border)
    border-radius: var(--radius)
    background: var(--surface)
    box-shadow: var(--shadow-lg)
    pointer-events: none

  @media (max-width: vars.$w-mobile)
    .lb-row
      grid-template-columns: 44px minmax(0, 1fr) 84px 40px 40px
      gap: 8px
      padding: 6px 12px
      font-size: 0.9rem

    .accuracy
      display: none

    .lb-head
      top: var(--nav-height)
</style>
