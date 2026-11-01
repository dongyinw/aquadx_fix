<!-- maimai DX tournament (KOP) leaderboard. -->
<script lang="ts">
  import { onMount } from "svelte"
  import PageHeader from "../components/ui/PageHeader.svelte"
  import EmptyState from "../components/ui/EmptyState.svelte"
  import Icon from "../components/ui/Icon.svelte"
  import { DATA, TOURNAMENT, USER } from "../libs/sdk"
  import type { Tournament, TournamentRow } from "../libs/sdk"
  import type { AllMusic } from "../libs/generalTypes"
  import { DATA_HOST } from "../libs/config"
  import { coverNotFound } from "../libs/ui"

  const levelNames = ["BASIC", "ADVANCED", "EXPERT", "MASTER", "Re:MASTER"]

  let tournaments: Tournament[] = []
  let current: Tournament | null = null
  let ranking: TournamentRow[] | null = null
  let music: AllMusic = {}
  let error = ""
  let loading = true
  let myUsername = ""

  if (USER.isLoggedIn()) USER.me().then(m => myUsername = m.username).catch(() => {})

  const coverUrl = (musicId: number) => `${DATA_HOST}/d/mai2/music/00${musicId.toString().padStart(6, "0").substring(2)}.png`
  const songName = (id: number) => music[id]?.name ?? `#${id}`
  const pct = (v: number) => `${(v / 10000).toFixed(4)}%`
  const day = (s: string) => s ? s.slice(0, 16) : "—"

  async function open(id: number) {
    ranking = null
    error = ""
    try {
      const r = await TOURNAMENT.ranking(id)
      current = r.tournament
      ranking = r.ranking
      const url = new URL(window.location.toString())
      if (url.searchParams.get("id") !== String(id)) {
        url.searchParams.set("id", String(id))
        history.replaceState(history.state, "", url)
      }
    } catch (e) {
      error = e instanceof Error ? e.message : String(e)
    }
  }

  onMount(async () => {
    DATA.allMusic("mai2").then(m => music = m).catch(() => {})
    try {
      tournaments = await TOURNAMENT.list()
      const wanted = Number(new URL(window.location.toString()).searchParams.get("id"))
      // A specific id may be requested even when it isn't listed (e.g. admins previewing a disabled tournament).
      const id = wanted || tournaments[0]?.id
      if (id) await open(id)
    } catch (e) {
      error = e instanceof Error ? e.message : String(e)
    } finally {
      loading = false
    }
  })
</script>

<main class="page tournament">
  <PageHeader eyebrow="Tournament" title={current?.name ?? "大赛排行榜"}
              description={current ? `比赛时间 ${day(current.startDate)} ~ ${day(current.endDate)}` : "舞萌 DX 大赛成绩排行"}>
    {#if tournaments.length > 1}
      <nav class="segmented" aria-label="大赛">
        {#each tournaments as t (t.id)}
          <button type="button" class:active={t.id === current?.id} on:click={() => open(t.id)}>{t.name}</button>
        {/each}
      </nav>
    {/if}
  </PageHeader>

  {#if error}
    <div class="alert alert-danger"><Icon icon="solar:danger-triangle-bold-duotone" /><p>{error}</p></div>
  {:else if !loading && !current}
    <EmptyState icon="solar:cup-star-bold-duotone" title="当前没有进行中的大赛" description="大赛开始后会在这里显示排行榜。" />
  {/if}

  {#if current}
    {#if !current.active}
      <div class="alert alert-info"><Icon icon="solar:eye-closed-bold-duotone" /><p>{current.enabled ? "该大赛已结束，排行榜入口已隐藏。" : "该大赛已停用，只有管理员能看到。"}</p></div>
    {/if}

    <section class="songs">
      {#each current.musicIds as id, i (id)}
        <div class="card song">
          <img src={coverUrl(id)} alt="" loading="lazy" on:error={coverNotFound} />
          <div class="song-copy">
            <small>课题曲 {i + 1}{#if current.lockedMusicIds.includes(id)}<span class="badge">隐藏曲</span>{/if}</small>
            <strong class="truncate" title={songName(id)}>{songName(id)}</strong>
          </div>
        </div>
      {/each}
    </section>

    <p class="rules subtle">
      在同一个 credit 内打完全部课题曲才计入成绩；总分为各曲达成率之和，BASIC / ADVANCED / EXPERT 分别扣 3% / 2% / 1%。
      每位玩家取最好的一次，同分按 DX 分数、再按更早游玩排序。排行榜每分钟更新一次。
    </p>

    {#if ranking === null}
      <div class="card card-flush board" aria-hidden="true">
        {#each Array(6) as _}<div class="lb-row"><span class="skeleton" style="height:18px;width:100%"></span></div>{/each}
      </div>
    {:else if ranking.length === 0}
      <EmptyState icon="solar:ranking-bold-duotone" title="还没有人上榜" description="在一次游戏中打完全部课题曲即可参赛。" />
    {:else}
      <div class="card card-flush board" role="table" aria-label="大赛排行榜" style:--songs={current.musicIds.length}>
        <div class="lb-row lb-head" role="row">
          <span class="rank" role="columnheader">名次</span>
          <span class="name" role="columnheader">玩家</span>
          {#each current.musicIds as _, i}<span class="song-score" role="columnheader">曲 {i + 1}</span>{/each}
          <span class="total" role="columnheader">总分</span>
        </div>
        {#each ranking as row (row.rank)}
          <div class="lb-row" role="row" class:me={!!myUsername && row.username === myUsername}>
            <span class="rank" role="cell">
              {#if row.rank <= 3}<span class="medal medal-{row.rank}">{row.rank}</span>{:else}<span class="num">#{row.rank}</span>{/if}
            </span>
            <span class="name" role="cell">
              {#if row.username}<a href="/u/{row.username}/mai2">{row.name}</a>{:else}<span>{row.name}</span>{/if}
              {#if !!myUsername && row.username === myUsername}<span class="badge primary">我</span>{/if}
              <small class="subtle">{day(row.playDate)}</small>
            </span>
            {#each row.songs as s}
              <span class="song-score num" role="cell" title="{songName(s.musicId)} · {levelNames[s.level] ?? s.level} · DX {s.deluxscore}">
                {pct(s.achievement)}{#if s.level < 3}<small class="lv lv-{s.level}">{levelNames[s.level]}</small>{/if}
              </span>
            {/each}
            <span class="total num" role="cell">
              <strong>{pct(row.totalScore)}</strong>
              <small class="subtle">DX {row.totalDxScore}</small>
            </span>
          </div>
        {/each}
      </div>
    {/if}
  {/if}
</main>

<style lang="sass">
  @use "../vars"

  .songs
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr))
    gap: 12px

  .song
    display: flex
    flex-direction: row
    align-items: center
    gap: 12px
    padding: 10px

    img
      width: 56px
      height: 56px
      flex-shrink: 0
      border-radius: var(--radius-sm)
      object-fit: cover
      background: var(--surface-2)

  .song-copy
    display: grid
    gap: 2px
    min-width: 0

    small
      display: flex
      align-items: center
      gap: 6px
      color: var(--text-2)

  .rules
    font-size: 0.84rem

  .board
    overflow: visible

  .lb-row
    display: grid
    grid-template-columns: 64px minmax(0, 1fr) repeat(var(--songs, 3), 104px) 132px
    align-items: center
    gap: 12px
    min-height: 56px
    padding: 6px 20px
    border-bottom: 1px solid var(--border)

    &:last-of-type
      border-bottom: 0

    &:not(.lb-head):hover
      background: var(--surface-hover)

    &.me
      background: var(--primary-soft)
      box-shadow: inset 3px 0 0 var(--primary)

    .song-score, .total
      display: grid
      justify-items: end
      text-align: right

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
    flex-wrap: wrap
    column-gap: 8px
    min-width: 0

    a, > span:first-child
      overflow: hidden
      color: var(--text)
      font-weight: 600
      text-overflow: ellipsis
      white-space: nowrap

    small
      flex-basis: 100%
      font-size: 0.74rem

  .song-score
    color: var(--text-2)
    font-size: 0.88rem

  .lv
    font-size: 0.66rem
    font-weight: 700

    &.lv-0
      color: #3fae5a
    &.lv-1
      color: #e0a21a
    &.lv-2
      color: #e0466a

  .total
    strong
      color: var(--primary)
      font-weight: 700

    small
      font-size: 0.74rem

  @media (max-width: vars.$w-tablet)
    .lb-row
      grid-template-columns: 44px minmax(0, 1fr) 120px
      gap: 8px
      padding: 6px 12px

    .song-score
      display: none !important
</style>
