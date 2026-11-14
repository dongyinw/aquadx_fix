<!-- Overview tab: play trend charts, per-day player list (with in-game user IDs), hourly distribution and song ranking. -->
<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../ui/Icon.svelte"
  import Spinner from "../ui/Spinner.svelte"
  import TrendChart from "./TrendChart.svelte"
  import { ADMIN } from "../../libs/sdk"
  import type { AdminAnalytics, AnalyticsDayPlayer } from "../../libs/sdk"
  import { errorMessage, toast } from "../../libs/toast"

  let analytics: AdminAnalytics | null = null
  let loading = false
  let error = ""
  let period = "7"
  let customFrom = ""
  let customTo = ""

  let selectedDate = ""
  let players: AnalyticsDayPlayer[] = []
  let playersLoading = false
  let playerQuery = ""

  const ymd = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`

  async function load() {
    loading = true
    error = ""
    let from = ""
    let to = ""
    if (period === "custom") {
      from = customFrom
      to = customTo
    } else {
      const end = new Date()
      const begin = new Date(end)
      begin.setDate(begin.getDate() - Math.max(0, Number(period) - 1))
      from = ymd(begin)
      to = ymd(end)
    }
    try {
      analytics = await ADMIN.analytics(from, to)
      // Keep the chosen day only while it is still inside the range.
      if (!analytics.trend.some(point => point.date === selectedDate)) { selectedDate = ""; players = [] }
    } catch (e) {
      error = errorMessage(e)
    } finally {
      loading = false
    }
  }

  async function selectDate(date: string) {
    selectedDate = date
    playerQuery = ""
    if (!date) { players = []; return }
    playersLoading = true
    try { players = await ADMIN.analyticsDay(date) } catch (e) { toast.error(errorMessage(e)); players = [] } finally { playersLoading = false }
  }

  $: labels = analytics?.trend.map(point => point.date) ?? []
  $: selectedIndex = labels.indexOf(selectedDate)
  $: playSeries = [{ name: "游玩次数", color: "var(--primary)", values: analytics?.trend.map(point => point.plays) ?? [], area: true }]
  $: peopleSeries = [
    { name: "游玩人数", color: "var(--primary)", values: analytics?.trend.map(point => point.players) ?? [] },
    { name: "游客账户", color: "var(--accent)", values: analytics?.trend.map(point => point.guestPlayers) ?? [] },
    { name: "新增玩家", color: "var(--success)", values: analytics?.trend.map(point => point.newPlayers) ?? [] },
  ]
  $: hourSeries = [{ name: "游玩次数", color: "var(--primary)", values: analytics?.hours.map(item => item.plays) ?? [] }]
  $: hourLabels = analytics?.hours.map(item => `${item.hour}`) ?? []
  $: perDay = analytics && analytics.days > 0 ? analytics.totalPlays / analytics.days : 0
  $: peakHour = analytics?.hours.reduce((best, item) => item.plays > best.plays ? item : best, { hour: 0, plays: 0 })
  $: visiblePlayers = players.filter(player => !playerQuery.trim() || `${player.userId} ${player.userName}`.toLowerCase().includes(playerQuery.trim().toLowerCase()))

  onMount(load)
</script>

<section class="card analytics">
  <div class="card-header">
    <div><h2>游玩数据趋势</h2><p>舞萌 DX 游玩记录 · 点击图上的某一天，查看当天玩过的玩家 ID</p></div>
    <div class="tools">
      <select bind:value={period} on:change={load} aria-label="统计时间范围">
        <option value="1">近 1 日</option><option value="7">近 7 日</option><option value="30">近 30 日</option><option value="custom">自定义</option>
      </select>
      {#if period === "custom"}
        <input type="date" bind:value={customFrom} aria-label="开始日期" /><span class="subtle">至</span><input type="date" bind:value={customTo} aria-label="结束日期" />
        <button class="btn-primary" type="button" on:click={load}>查询</button>
      {/if}
      <button class="btn-ghost btn-sm" type="button" on:click={load} disabled={loading} title="刷新">{#if loading}<Spinner />{:else}<Icon icon="solar:refresh-bold" />{/if}</button>
    </div>
  </div>

  {#if error}
    <div class="alert alert-danger"><Icon icon="solar:danger-triangle-bold-duotone" /><p>{error}</p></div>
  {:else if loading && !analytics}
    <div class="kpis">{#each [1, 2, 3, 4, 5, 6] as _}<span class="skeleton" style="height:76px;border-radius:12px"></span>{/each}</div>
  {:else if analytics}
    <div class="kpis">
      <div><small>游玩人数</small><strong class="num">{analytics.players}</strong></div>
      <div><small>游客账户人数</small><strong class="num">{analytics.guestPlayers}</strong></div>
      <div><small>新增玩家</small><strong class="num">{analytics.newPlayers}</strong></div>
      <div><small>游玩曲目总数</small><strong class="num">{analytics.totalPlays}</strong></div>
      <div><small>日均游玩曲目</small><strong class="num">{perDay.toFixed(1)}</strong></div>
      <div><small>游玩总达成率</small><strong class="num">{analytics.achievementRate.toFixed(2)}%</strong></div>
    </div>

    <h3 class="chart-title">每日游玩曲目数</h3>
    <TrendChart {labels} series={playSeries} selected={selectedIndex} ariaLabel="每日游玩曲目数趋势"
                formatLabel={label => label.slice(5)} on:select={event => selectDate(labels[event.detail])} />

    <h3 class="chart-title">每日人数</h3>
    <TrendChart {labels} series={peopleSeries} selected={selectedIndex} ariaLabel="每日游玩人数、游客账户和新增玩家趋势"
                formatLabel={label => label.slice(5)} on:select={event => selectDate(labels[event.detail])} />
    <div class="legend">{#each peopleSeries as item}<span><i style={`background:${item.color}`}></i>{item.name}</span>{/each}</div>

    <div class="day-bar">
      <label class="field"><span class="field-label">查看某一天的玩家</span>
        <select value={selectedDate} on:change={event => selectDate(event.currentTarget.value)}>
          <option value="">选择日期</option>
          {#each [...labels].reverse() as date}<option value={date}>{date}</option>{/each}
        </select>
      </label>
      {#if selectedDate}<span class="subtle">{selectedDate} · 共 {players.length} 位玩家{players.length >= 300 ? "（仅显示游玩最多的 300 位）" : ""}</span>{/if}
    </div>

    {#if selectedDate}
      <div class="search">
        <Icon icon="solar:magnifer-bold-duotone" /><input type="search" bind:value={playerQuery} placeholder="按玩家 ID 或昵称筛选" aria-label="筛选玩家" />
      </div>
      <div class="table-wrap players">
        <table class="data-table stack">
          <thead><tr><th>玩家 ID</th><th>昵称</th><th>类型</th><th class="num">游玩曲目</th><th class="num">平均达成率</th><th>首次游玩</th><th>最后游玩</th></tr></thead>
          <tbody>
            {#each visiblePlayers as player (player.userId)}
              <tr>
                <td data-label="玩家 ID" class="mono">{player.userId}</td>
                <td data-label="昵称">{player.userName}</td>
                <td data-label="类型">{player.guest ? "游客账户" : "玩家"}</td>
                <td data-label="游玩曲目" class="num">{player.plays}</td>
                <td data-label="平均达成率" class="num">{player.averageAchievement.toFixed(2)}%</td>
                <td data-label="首次游玩">{player.firstPlay.slice(11, 19)}</td>
                <td data-label="最后游玩">{player.lastPlay.slice(11, 19)}</td>
              </tr>
            {:else}
              <tr><td colspan="7" data-label="" class="subtle">{playersLoading ? "加载中…" : "这一天没有符合条件的玩家"}</td></tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}

    <h3 class="chart-title">各时段游玩分布{#if peakHour && peakHour.plays > 0}<small class="subtle"> · 最热闹 {peakHour.hour}:00 – {peakHour.hour + 1}:00（机台时间）</small>{/if}</h3>
    <TrendChart labels={hourLabels} series={hourSeries} mode="bar" height={180} ariaLabel="各时段游玩曲目数分布" formatLabel={label => `${label}时`} />

    <details class="ranking-details" open>
      <summary>热门曲目排行（{analytics.ranking.length}）<Icon icon="line-md:chevron-small-down" /></summary>
      <div class="table-wrap ranking">
        <table class="data-table stack">
          <thead><tr><th>排名</th><th>曲目 ID</th><th class="num">游玩次数</th><th class="num">平均达成率</th></tr></thead>
          <tbody>
            {#each analytics.ranking as song}
              <tr><td data-label="排名">{song.rank}</td><td data-label="曲目 ID" class="mono">{song.musicId}</td><td data-label="游玩次数" class="num">{song.plays}</td><td data-label="平均达成率" class="num">{song.averageAchievement.toFixed(2)}%</td></tr>
            {:else}
              <tr><td colspan="4" data-label="" class="subtle">暂无曲目记录</td></tr>
            {/each}
          </tbody>
        </table>
      </div>
    </details>
  {/if}
</section>

<style lang="sass">
  .tools
    display: flex
    align-items: center
    flex-wrap: wrap
    gap: 8px

    select, input
      width: auto
      min-width: 130px

  .kpis
    display: grid
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr))
    gap: 12px
    margin-bottom: 16px

    div
      display: grid
      gap: 4px
      padding: 14px
      border-radius: var(--radius)
      background: var(--surface-2)

    small
      color: var(--text-2)

    strong
      font-size: 1.4rem

  .chart-title
    display: flex
    align-items: baseline
    flex-wrap: wrap
    gap: 4px
    margin: 20px 0 8px
    font-size: 0.95rem

    small
      font-weight: 400

  .legend
    display: flex
    flex-wrap: wrap
    gap: 16px
    margin: 6px 0 12px
    color: var(--text-2)
    font-size: 0.82rem

    span
      display: inline-flex
      align-items: center
      gap: 6px

    i
      width: 9px
      height: 9px
      border-radius: 50%

  .day-bar
    display: flex
    align-items: flex-end
    flex-wrap: wrap
    gap: 12px
    margin: 8px 0 12px

    select
      min-width: 180px

  .search
    position: relative
    display: flex
    align-items: center
    margin-bottom: 10px

    > :global(svg)
      position: absolute
      left: 10px
      color: var(--text-3)

    input
      padding-left: 34px

  .players
    max-height: 420px

  .ranking-details
    margin-top: 20px

    summary
      display: inline-flex
      align-items: center
      gap: 4px
      margin-bottom: 10px
      color: var(--primary)
      font-weight: 600
      cursor: pointer
      list-style: none

      &::-webkit-details-marker
        display: none

  .ranking
    max-height: 320px

  @media (max-width: 760px)
    .players, .ranking
      max-height: none
</style>
