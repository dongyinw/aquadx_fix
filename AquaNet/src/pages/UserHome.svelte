<script lang="ts">
  import { onMount, tick } from "svelte";
  import { CHARTJS_OPT, coverNotFound, pfpNotFound, registerChart, renderCal, title, tooltip, pfp } from "../libs/ui";
  import type {
    GenericGamePlaylog,
    GenericGameSummary,
    MusicMeta,
    TrendEntry,
    MikuNetUser,

    AllMusic

  } from "../libs/generalTypes";
  import { DATA_HOST } from "../libs/config";
  import 'cal-heatmap/cal-heatmap.css';
  import moment from "moment";
  import 'chartjs-adapter-moment';
  import { CARD, DATA, GAME, USER } from "../libs/sdk";
  import { type GameName, getMult, roundFloor } from "../libs/scoring";
  import StatusOverlays from "../components/StatusOverlays.svelte";
  import Icon from "../components/ui/Icon.svelte";
  import { countryCodeToEmoji, GAME_TITLE, t } from "../libs/i18n";
  import RankDetails from "../components/RankDetails.svelte";
  import RatingComposition from "../components/RatingComposition.svelte";
  import useLocalStorage from "../libs/hooks/useLocalStorage.svelte";
  import Line from "../components/chart/Line.svelte";
  import ChuniUserboxDisplay from "../components/settings/userbox/ChuniUserboxDisplay.svelte";
  import Dialog from "../components/ui/Dialog.svelte";
  import Spinner from "../components/ui/Spinner.svelte";
  import EmptyState from "../components/ui/EmptyState.svelte";
  import { theme } from "../libs/theme";
  import { toast } from "../libs/toast";

  const TREND_DAYS = 60

  registerChart()

  export let username: string = "";
  export let game: GameName = "" as GameName;
  let calElement: HTMLElement
  let error: string;
  let me: MikuNetUser
  const rounding = useLocalStorage("rounding", true);

  interface MusicAndPlay extends MusicMeta, GenericGamePlaylog {}

  let d: {
    user: GenericGameSummary,
    trend: TrendEntry[]
    recent: MusicAndPlay[],
    validGames: [ string, string ][],
  } | null

  let allMusics: AllMusic
  let showDetailRank = false
  let isLoading = false
  let exportLoading = false
  let showMoreRecent = false
  let selectedScore: MusicAndPlay | null = null
  let detailLoading = false
  let detailError = ""
  let detailRequest = 0

  const judgmentRows = [
    { label: "Tap", prefix: "tap" },
    { label: "Hold", prefix: "hold" },
    { label: "Slide", prefix: "slide" },
    { label: "Touch", prefix: "touch" },
    { label: "Break", prefix: "break" },
  ]

  const safeNumber = (value: unknown) => {
    const number = Number(value)
    return Number.isFinite(number) ? number : 0
  }
  const formatNumber = (value: unknown) => safeNumber(value).toLocaleString()
  const ratingDelta = (score: GenericGamePlaylog) => safeNumber(score.afterRating) - safeNumber(score.beforeRating)
  const scoreAchievement = (score: GenericGamePlaylog) => safeNumber(score.achievement)
  const hasJudgments = (score: GenericGamePlaylog) => score.tapCriticalPerfect !== undefined
  const judgmentValue = (score: GenericGamePlaylog, prefix: string, grade: string) =>
    Number((score as unknown as Record<string, unknown>)[`${prefix}${grade}`] ?? 0)

  function openScore(score: MusicAndPlay) {
    const request = ++detailRequest
    selectedScore = score
    detailLoading = false
    detailError = ""

    if (score.id == null || hasJudgments(score)) return

    detailLoading = true
    GAME.playlog(game, score.id).then(detail => {
      if (request !== detailRequest) return
      selectedScore = { ...score, ...detail } as MusicAndPlay
    }).catch(e => {
      if (request === detailRequest) detailError = e.message
    }).finally(() => {
      if (request === detailRequest) detailLoading = false
    })
  }

  function closeScore() {
    detailRequest += 1
    selectedScore = null
    detailLoading = false
    detailError = ""
  }


  function init() {
    USER.isLoggedIn() && USER.me().then(u => me = u)

    CARD.userGames(username).then(games => {
      if (!game) {
        let targetGames = Object.entries(games)
        .map(d => {
          if (d[1])
          d[1].lastLogin = d[1].lastLogin ? new Date(d[1].lastLogin) : new Date(0);
          return d;
        }).sort((a,b) => {
          return b[1]?.lastLogin - a[1]?.lastLogin;
        });
        game = targetGames[0][0] as GameName;
      }
      if (!games[game]) {
        // Find a valid game
        const valid = Object.entries(games).filter(([g, valid]) => valid)
        if (!valid || !valid[0]) return error = t("UserHome.NoValidGame")
        window.location.href = `/u/${username}/${valid[0][0]}`
      }

      Promise.all([
        GAME.userSummary(username, game),
        GAME.trend(username, game),
        DATA.allMusic(game),
      ]).then(async ([user, trend, music]) => {
        title(` ${user.name} (@${username}) ・ ${GAME_TITLE[game]}`)

        // If game is wacca, divide all ratings by 10
        if (game === 'wacca') {
          user.rating /= 10
          trend.forEach(it => it.rating /= 10)
          user.recent.forEach(it => {
            it.beforeRating /= 10
            it.afterRating /= 10
          })
        }

        // Set beforeRating in recent to the last play's afterRating
        user.recent.forEach((it, i) => {
          if (i < user.recent.length - 1) {
            it.beforeRating = user.recent[i + 1].afterRating
          }
        })

        const minDate = moment().subtract(TREND_DAYS, 'days').format("YYYY-MM-DD")
        d = {user,
          trend: trend.filter(it => it.date >= minDate && it.plays != 0),
          recent: user.recent.map(it => {return {...music[it.musicId], ...it}}),
          validGames: Object.entries(GAME_TITLE).filter(g => games[g[0] as GameName])
        }
        allMusics = music
        calData = trend.map(it => {return {date: it.date, value: it.plays}})
        await tick()
        paintCal($theme)
      }).catch((e) => error = e.message);
    }).catch((e) => { error = e.message; console.error(e) } );
  }

  onMount(() => {
    // Route props can arrive after the component script is initialized.
    const parts = window.location.pathname.split("/").filter(Boolean)
    username = username || decodeURIComponent(parts[1] ?? "")
    game = game || (parts[2] as GameName ?? "")

    if (Object.keys(GAME_TITLE).includes(game) || !game) init()
    else error = t("UserHome.InvalidGame", {game})
  })

  const setRival = (isAdd: boolean) => {
    isLoading = true
    GAME.setRival(game, username, isAdd).then(() => {
      d!.user.rival = isAdd
      toast.success(isAdd ? t("UserHome.AddRival") + " ✓" : t("UserHome.RemoveRival") + " ✓")
    }).catch(e => toast.error(e.message)).finally(() => isLoading = false)
  }

  // Activity heatmap, repainted when the theme changes.
  let calData: { date: any, value: any }[] = []
  let paintedTheme = ""
  function paintCal(current: string) {
    if (!calElement || paintedTheme === current) return
    paintedTheme = current
    calElement.innerHTML = ""
    renderCal(calElement, calData, {
      dark: current === "dark",
      tooltip: (count, date) => count ? t("UserHome.CalTooltip", { count, date }) : t("UserHome.CalNoPlay", { date }),
    }).then(() => {
      // Scroll to the rightmost
      calElement.scrollLeft = calElement.scrollWidth - calElement.clientWidth
    })
  }
  $: if (d && paintedTheme) paintCal($theme)

  const cssVar = (name: string) => getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  const coverUrl = (musicId: number | string) => `${DATA_HOST}/d/${game}/music/00${musicId.toString().padStart(6, '0').substring(2)}.png`
  const levelLabel = (r: MusicAndPlay) => r.notes?.[r.level === 10 ? 0 : r.level]?.lv?.toFixed(1) ?? r.worldsEndTag ?? '-'
  const rankLabel = (achievement: number) => ("" + getMult(achievement, game)[2]).replace("p", "+")
  const formatRating = (value: number, ongekiNew = false) => ongekiNew ? (value / 1000).toFixed(3)
    : game === 'chu3' || game === 'ongeki' ? (value / 100).toFixed(2) : value.toLocaleString()
  $: isSelf = !!me && me.username.toLowerCase() === username.toLowerCase()

  async function exportData() {
    if (exportLoading || !me || me.username.toLowerCase() !== username.toLowerCase()) return
    const exportGame = game
    const exportUsername = me.username
    exportLoading = true
    error = ""
    try {
      const data = await GAME.export(exportGame)
      const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }))
      const link = document.createElement("a")
      link.href = url
      link.download = `MikuNet_${exportGame}_data_${exportUsername}.json`
      document.body.appendChild(link)
      link.click()
      link.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
      toast.success(t("UserHome.ExportData", { game: GAME_TITLE[exportGame] }) + " ✓")
    } catch (e) {
      error = e instanceof Error ? e.message : String(e)
    } finally {
      exportLoading = false
    }
  }
</script>

<main id="user-home" class="page">
  {#if d}
    <section class="card profile-hero">
      <img use:pfp={d.user.aquaUser} alt="" class="avatar" on:error={pfpNotFound}>
      <div class="identity">
        <h1>
          <span class="truncate">{d.user.aquaUser?.displayName || d.user.name}</span>
          {#if d.user.aquaUser?.country}<span class="flag" aria-hidden="true">{countryCodeToEmoji(d.user.aquaUser.country)}</span>{/if}
        </h1>
        <p class="handle">
          {#if d.user.aquaUser}
            {#if d.user.aquaUser.displayName}<span>{d.user.name}</span><span aria-hidden="true">·</span>{/if}
            <span>@{d.user.aquaUser.username}</span>
          {:else}
            <span>{GAME_TITLE[game]}</span>
          {/if}
        </p>
        {#if d.user.aquaUser?.profileBio}
          <p class="bio" title={t("settings.profile.bio")}>{d.user.aquaUser.profileBio}</p>
        {/if}
      </div>
      <div class="hero-actions">
        {#if typeof d.user.rival === 'boolean' && game === 'mai2'}
          <button type="button" class={d.user.rival ? "" : "btn-soft"} disabled={isLoading} on:click={() => setRival(!d?.user.rival)}>
            {#if isLoading}<Spinner />{:else}<Icon icon="solar:cup-star-bold-duotone" />{/if}
            {d.user.rival ? t("UserHome.RemoveRival") : t("UserHome.AddRival")}
          </button>
        {/if}
        {#if isSelf}
          <button type="button" disabled={exportLoading} on:click={exportData}>
            {#if exportLoading}<Spinner />{:else}<Icon icon="solar:download-minimalistic-bold-duotone" />{/if}
            {exportLoading ? t("UserHome.ExportingData") : t("UserHome.ExportData", { game: GAME_TITLE[game] })}
          </button>
          <a class="btn btn-icon" href={`/settings/${game}`} aria-label={t("UserHome.Settings")} use:tooltip={t("UserHome.Settings")}>
            <Icon icon="solar:settings-bold-duotone"/>
          </a>
        {/if}
      </div>
    </section>

    {#if d.validGames.length > 1}
      <nav class="segmented game-tabs" aria-label="游戏">
        {#each d.validGames as [g, name]}
          <a href={`/u/${username}/${g}`} class:active={game === g} aria-current={game === g ? "page" : undefined}>{name}</a>
        {/each}
      </nav>
    {/if}

    <ChuniUserboxDisplay {game} {username} bind:error={error} />

    <section class="section">
      <div class="section-header"><h2>{GAME_TITLE[game] ?? "?"} {t('UserHome.Statistics')}</h2></div>
      <div class="stat-grid">
        <div class="stat primary">
          {#if game == "ongeki" && d.user.ratingNotGeneric}
            <span>{t("UserHome.Rating")}</span><strong class="num">{formatRating(d.user.rating, true)}</strong>
          {:else}
            <span>{game === 'mai2' ? t("UserHome.DXRating") : t("UserHome.Rating")}</span><strong class="num">{formatRating(d.user.rating)}</strong>
          {/if}
          {#if !isNaN(parseInt(d.user.serverRank))}<small>{t('UserHome.ServerRank')} <b class="num">#{(d.user.serverRank).toLocaleString()}</b></small>{/if}
        </div>
        <div class="stat">
          {#if game == "ongeki" && d.user.ratingNotGeneric}
            <span>{t("UserHome.HighestRating")}</span><strong class="num">{formatRating(d.user.ratingHighest, true)}</strong>
          {:else}
            <span>{game === 'mai2' ? t("UserHome.HighestDXRating") : t("UserHome.HighestRating")}</span><strong class="num">{formatRating(d.user.ratingHighest)}</strong>
          {/if}
        </div>
        <div class="stat"><span>{t('UserHome.Accuracy')}</span><strong class="num">{(d.user.accuracy).toFixed(2)}%</strong></div>
        <div class="stat"><span>{t("UserHome.MaxCombo")}</span><strong class="num">{d.user.maxCombo}</strong></div>
        <div class="stat"><span>{t("UserHome.FullCombo")}</span><strong class="num">{d.user.fullCombo}</strong></div>
        <div class="stat"><span>{t("UserHome.AllPerfect")}</span><strong class="num">{d.user.allPerfect}</strong></div>
        <div class="stat"><span>{game === 'mai2' ? t('UserHome.DXScore') : t("UserHome.Score")}</span><strong class="num">{d.user.totalScore.toLocaleString()}</strong></div>
        <div class="stat"><span>{t("UserHome.Plays")}</span><strong class="num">{d.user.plays}</strong></div>
      </div>
    </section>

    <div class="overview-grid">
      <section class="card trend-card">
        <div class="card-header"><div><h3>Rating</h3><p>近 {TREND_DAYS} 天</p></div></div>
        <!-- ChartJS cannot be fully responsive unless there is a parent div that's independent from its size and helps it determine its size -->
        <div class="trend">
          <div class="chartjs-box-reference">
            {#if d.trend.length <= 1}
              <div class="no-data">{t("UserHome.NoData", { days: TREND_DAYS })}</div>
            {:else}
              {#key $theme}
                <Line data={{
                  datasets: [
                    {
                      label: 'Rating',
                      data: d.trend.map(it => {return {x: Date.parse(it.date), y: it.rating}}),
                      borderColor: cssVar('--primary'),
                      backgroundColor: cssVar('--primary-soft'),
                      fill: true,
                      borderWidth: 2,
                      tension: 0.25,
                    }
                  ]
                }} options={CHARTJS_OPT} />
              {/key}
            {/if}
          </div>
        </div>
      </section>

      <section class="card ranks-card">
        <div class="card-header"><div><h3>{t("UserHome.RankDetail.Title")}</h3></div></div>
        <div class="rank-chips">
          {#each d.user.ranks as r}
            <div class="rank-chip"><span>{r.name}</span><strong class="num">{r.count}</strong></div>
          {/each}
        </div>
        {#if Object.entries(d.user.detailedRanks).length > 0}
          <button class="btn-ghost btn-sm rank-toggle" type="button" aria-expanded={showDetailRank} on:click={() => showDetailRank = !showDetailRank}>
            <Icon icon={showDetailRank ? "line-md:chevron-small-up" : "line-md:chevron-small-down"} />{t("UserHome.ShowRanksDetails")}
          </button>
        {/if}
      </section>
    </div>

    {#if showDetailRank}<RankDetails g={d.user}/>{/if}

    <section class="card activity-card">
      <div class="card-header"><div><h3>{t('UserHome.PlayActivity')}</h3></div></div>
      <div class="hide-scrollbar" id="cal-heatmap" bind:this={calElement}></div>
      <dl class="activity-stats">
        <div><dt>{t("UserHome.Plays")}</dt><dd class="num">{d.user.plays}</dd></div>
        <div><dt>{t('UserHome.PlayTime')}</dt><dd class="num">{(d.user.totalPlayTime / 60).toFixed(1)} h</dd></div>
        <div><dt>{t('UserHome.FirstSeen')}</dt><dd class="num">{moment(d.user.joined).format("YYYY-MM-DD")}</dd></div>
        <div><dt>{t('UserHome.LastSeen')}</dt><dd class="num">{moment(d.user.lastSeen).format("YYYY-MM-DD")}</dd></div>
        <div><dt>{t('UserHome.Version')}</dt><dd>{d.user.lastVersion}</dd></div>
      </dl>
    </section>

    <RatingComposition title={t("UserHome.RatingComposition.Best", {n: 50})} comp={d.user.ratingComposition.best50} {allMusics} {game}/>
    <RatingComposition title={t("UserHome.RatingComposition.Best", {n: 35})} comp={d.user.ratingComposition.best35} {allMusics} {game}/>
    <RatingComposition title={t("UserHome.RatingComposition.Best", {n: 30})} comp={d.user.ratingComposition.best30} {allMusics} {game}/>
    <RatingComposition title={t("UserHome.RatingComposition.Best", {n: 15})} comp={d.user.ratingComposition.best15} {allMusics} {game}/>
    <RatingComposition title={t("UserHome.RatingComposition.New", {n : 15})} comp={d.user.ratingComposition.new15} {allMusics} {game}/>
    <RatingComposition title={t("UserHome.RatingComposition.New", {n: 10})} comp={d.user.ratingComposition.new10} {allMusics} {game}/>
    <RatingComposition title={t("UserHome.RatingComposition.Platinum", {n: 50})} comp={d.user.ratingComposition.pscore50} {allMusics} {game}/>

    {#if me && me.displayCandidates && d.user.aquaUser && me.username == d.user.aquaUser.username}
      <RatingComposition title={t("UserHome.RatingComposition.BestCandidates", {n: 20})} comp={d.user.ratingComposition.best20_candidates} {allMusics} {game}/>
      <RatingComposition title={t("UserHome.RatingComposition.NewCandidates", {n: 10})} comp={d.user.ratingComposition.new10_candidates} {allMusics} {game}/>
      <RatingComposition title={t("UserHome.RatingComposition.PlatinumCandidates", {n: 20})} comp={d.user.ratingComposition.pscore20_candidates} {allMusics} {game}/>
    {/if}

     <!-- Chuni -->
    {#if d.user.ratingComposition.new}
      <RatingComposition title={t("UserHome.RatingComposition.New", {n: 20})} comp={d.user.ratingComposition.new} {allMusics} game="chu3"/>
    {:else}
      <RatingComposition title={t("UserHome.RatingComposition.Recent", {n: 10})} comp={d.user.ratingComposition.recent10} {allMusics} {game} top={10}/>
    {/if}

    <section class="section recent">
      <div class="section-header"><h2>{t('UserHome.RecentScores')}</h2><span class="subtle">点击成绩查看判定详情</span></div>
      {#if d.recent.length}
        <div class="card card-flush score-list">
          {#each (showMoreRecent ? d.recent : d.recent.slice(0, 15)) as r}
            <button class="score-row" type="button" on:click={() => openScore(r)}>
              <img src={coverUrl(r.musicId)} alt="" loading="lazy" on:error={coverNotFound} />
              <span class="score-main">
                <span class="score-title truncate">{r.name ?? t("UserHome.UnknownSong")}</span>
                <span class="score-meta">
                  <span class={`lv level-${r.level === 10 ? 5 : r.level}`}>{levelLabel(r)}</span>
                  {#if r.isAllPerfect || r.isAllJustice}
                    <img class="lamp" src="/assets/imgs/All Perfect.png" alt="All Perfect" />
                  {:else if r.isFullCombo}
                    <img class="lamp" src="/assets/imgs/Full Combo.png" alt="Full Combo" />
                  {/if}
                  <span class="subtle num">{moment(r.userPlayDate ?? r.playDate).format("MM-DD HH:mm")}</span>
                </span>
              </span>
              <span class="score-result">
                <span class={`rank-${getMult(r.achievement, game)[2].toString()[0]} rank-text`}>{rankLabel(r.achievement)}</span>
                <span class="num achievement" use:tooltip={(r.achievement / 10000).toFixed(4)}>
                  {rounding.value ? roundFloor(r.achievement, game, 1) : (r.achievement / 10000).toFixed(4)}%
                </span>
              </span>
              <span class="dx-change num" class:increased={r.afterRating - r.beforeRating > 0}>
                {r.afterRating === r.beforeRating ? '–' : (r.afterRating - r.beforeRating > 0 ? "+" : "") + (r.afterRating - r.beforeRating).toFixed(0)}
              </span>
            </button>
          {/each}
        </div>
        {#if !showMoreRecent && d.recent.length > 15}
          <button class="show-more" type="button" on:click={() => showMoreRecent = true}><Icon icon="line-md:chevron-small-down" />{t('UserHome.ShowMoreRecent')}</button>
        {/if}
      {:else}
        <EmptyState compact icon="solar:music-note-bold-duotone" title={t("UserHome.NoData", { days: TREND_DAYS })} />
      {/if}
    </section>

    {#if d.user.favorites != null && d.user.favorites.length > 0}
      <section class="section favorites">
        <div class="section-header"><h2>{t('UserHome.FavoriteSongs')}</h2><span class="badge">{d.user.favorites.length}</span></div>
        <div class="favorite-grid">
          {#each d.user.favorites as favoriteSongId}
            <div class="favorite">
              <img src={coverUrl(favoriteSongId)} alt="" loading="lazy" on:error={coverNotFound} />
              <span class="truncate">{allMusics[favoriteSongId.toString()] ? allMusics[favoriteSongId.toString()].name : t("UserHome.UnknownSong")}</span>
            </div>
          {/each}
        </div>
      </section>
    {/if}
  {:else if !error}
    <div class="profile-skeleton" aria-hidden="true">
      <span class="skeleton" style="height:128px;border-radius:16px"></span>
      <div class="stat-grid">{#each Array(8) as _}<span class="skeleton" style="height:84px;border-radius:14px"></span>{/each}</div>
      <span class="skeleton" style="height:280px;border-radius:16px"></span>
    </div>
  {/if}

  <StatusOverlays {error} />

{#if selectedScore}
  <Dialog title={selectedScore.name ?? t("UserHome.UnknownSong")} description="成绩详情" size="lg" on:close={closeScore}>
    <div class="detail-summary">
      <img class="detail-cover" src={coverUrl(selectedScore.musicId)} alt="" on:error={coverNotFound} />
      <div>
        <strong class="num">{moment(selectedScore.userPlayDate ?? selectedScore.playDate).format("YYYY-MM-DD HH:mm")}</strong>
        <span class="row">
          <span class={`lv level-${selectedScore.level === 10 ? 5 : selectedScore.level}`}>{levelLabel(selectedScore)}</span>
          <span class="muted">{GAME_TITLE[game]}</span>
        </span>
        <span class="badge" class:primary={selectedScore.isAllPerfect || selectedScore.isAllJustice || selectedScore.isFullCombo}>
          {selectedScore.isAllPerfect || selectedScore.isAllJustice ? "ALL PERFECT" : selectedScore.isFullCombo ? "FULL COMBO" : "PLAY RESULT"}
        </span>
      </div>
    </div>

    <div class="detail-stats">
      <div><span>达成率</span><strong class="num">{(scoreAchievement(selectedScore) / 10000).toFixed(4)}%</strong><small>{rankLabel(scoreAchievement(selectedScore))}</small></div>
      <div><span>DX 分</span><strong class="num">{formatNumber(selectedScore.deluxscore ?? selectedScore.totalDxScore)}</strong></div>
      <div><span>最大连击</span><strong class="num">{formatNumber(selectedScore.maxCombo)}<small> / {formatNumber(selectedScore.totalCombo)}</small></strong></div>
      <div><span>Rating 变化</span><strong class="num" class:positive={ratingDelta(selectedScore) > 0}>{ratingDelta(selectedScore) > 0 ? "+" : ""}{ratingDelta(selectedScore)}</strong></div>
    </div>

    {#if detailLoading}
      <div class="detail-message"><Spinner />正在读取逐项判定…</div>
    {:else if detailError}
      <div class="alert alert-danger"><Icon icon="solar:danger-triangle-bold-duotone" /><p>详情读取失败：{detailError}</p></div>
    {:else if hasJudgments(selectedScore)}
      <div class="table-wrap">
        <table class="data-table judgment-table">
          <thead><tr><th>判定</th><th class="num">Critical</th><th class="num">Perfect</th><th class="num">Great</th><th class="num">Good</th><th class="num">Miss</th></tr></thead>
          <tbody>
            {#each judgmentRows as row}
              <tr>
                <td><strong>{row.label}</strong></td>
                <td class="num critical">{judgmentValue(selectedScore, row.prefix, "CriticalPerfect")}</td>
                <td class="num">{judgmentValue(selectedScore, row.prefix, "Perfect")}</td>
                <td class="num">{judgmentValue(selectedScore, row.prefix, "Great")}</td>
                <td class="num">{judgmentValue(selectedScore, row.prefix, "Good")}</td>
                <td class="num miss">{judgmentValue(selectedScore, row.prefix, "Miss")}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
      <div class="timing">
        <span class="badge">FAST {selectedScore.fastCount ?? 0}</span>
        <span class="badge">LATE {selectedScore.lateCount ?? 0}</span>
        <span class="badge primary">{selectedScore.isFreedomMode ? "FREEDOM" : selectedScore.isNewFree ? "NEW FREE" : "STANDARD"}</span>
      </div>
    {:else}
      <div class="detail-message">该成绩没有返回逐项判定数据</div>
    {/if}
  </Dialog>
{/if}
</main>

<style lang="sass">
@use "../vars"

.profile-hero
  flex-direction: row
  align-items: center
  gap: 20px
  flex-wrap: wrap
  background: linear-gradient(120deg, var(--surface) 40%, var(--primary-soft))

  .avatar
    width: 88px
    height: 88px
    flex: 0 0 88px
    border-radius: 50%
    object-fit: cover
    background: var(--surface-2)
    box-shadow: 0 0 0 4px var(--surface), 0 0 0 5px var(--border)

  .identity
    display: grid
    flex: 1
    gap: 2px
    min-width: 200px

  h1
    display: flex
    align-items: center
    gap: 10px
    min-width: 0
    margin: 0
    font-size: clamp(1.4rem, 3vw, 1.9rem)

  .flag
    flex: 0 0 auto
    font-family: TwemojiCountryFlags, "Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif

  .handle
    display: flex
    gap: 6px
    margin: 0
    color: var(--text-2)
    font-size: 0.9rem

  .bio
    max-width: 640px
    max-height: 7.5em
    margin: 8px 0 0
    overflow-y: auto
    color: var(--text)
    font-size: 0.92rem
    white-space: pre-wrap

.hero-actions
  display: flex
  align-items: center
  gap: 8px
  flex-wrap: wrap

.game-tabs
  align-self: flex-start

.stat-grid
  display: grid
  grid-template-columns: repeat(4, minmax(0, 1fr))
  gap: 12px

.stat
  display: grid
  align-content: start
  gap: 2px
  padding: 14px 16px
  border: 1px solid var(--border)
  border-radius: var(--radius-lg)
  background: var(--surface)
  box-shadow: var(--shadow-xs)

  span
    color: var(--text-2)
    font-size: 0.8rem
    font-weight: 500

  strong
    font-size: 1.35rem
    line-height: 1.3

  small
    color: var(--text-2)
    font-size: 0.8rem

  &.primary
    border-color: color-mix(in srgb, var(--primary) 35%, transparent)
    background: var(--primary-soft)

    strong
      color: var(--primary-soft-text)
      font-size: 1.6rem

.overview-grid
  display: grid
  grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr)
  gap: 16px
  align-items: start

.trend
  position: relative
  height: 240px

  > .chartjs-box-reference
    position: absolute
    inset: 0
    display: flex
    align-items: center
    justify-content: center

  .no-data
    color: var(--text-3)
    user-select: none

.rank-chips
  display: grid
  grid-template-columns: repeat(auto-fill, minmax(78px, 1fr))
  gap: 8px

.rank-chip
  display: grid
  gap: 2px
  padding: 10px
  border-radius: var(--radius)
  background: var(--surface-2)
  text-align: center

  span
    background: vars.$grad-special
    -webkit-background-clip: text
    background-clip: text
    color: transparent
    font-size: 0.82rem
    font-weight: 700

  strong
    font-size: 1.1rem

.rank-toggle
  align-self: flex-start
  margin-top: 12px

#cal-heatmap
  overflow-x: auto
  padding-bottom: 4px

  :global(text)
    fill: var(--text-2) !important

.activity-stats
  display: grid
  grid-template-columns: repeat(5, minmax(0, 1fr))
  gap: 12px
  margin: 16px 0 0
  padding-top: 16px
  border-top: 1px solid var(--border)

  div
    display: grid
    gap: 2px

  dt
    color: var(--text-2)
    font-size: 0.8rem

  dd
    margin: 0
    font-weight: 600

.score-list
  display: grid

.score-row
  display: flex
  align-items: center
  gap: 14px
  height: auto
  min-height: 64px
  padding: 8px 16px 8px 8px
  border: 0
  border-bottom: 1px solid var(--border)
  border-radius: 0
  background: transparent
  box-shadow: none
  font-weight: 400
  text-align: left
  white-space: normal

  &:last-child
    border-bottom: 0

  &:hover:not(:disabled)
    border: 0
    border-bottom: 1px solid var(--border)
    background: var(--surface-hover)

  &:focus-visible
    position: relative
    z-index: 1

  > img
    width: 48px
    height: 48px
    flex: 0 0 48px
    border-radius: var(--radius-sm)
    object-fit: cover

.score-main
  display: grid
  flex: 1
  gap: 4px
  min-width: 0

.score-title
  font-weight: 600

.score-meta
  display: flex
  align-items: center
  gap: 8px
  font-size: 0.8rem

  .lamp
    height: 1.4em

.score-result
  display: grid
  justify-items: end
  flex: 0 0 auto

  .rank-text
    font-size: 0.82rem
    font-weight: 700

  .achievement
    font-weight: 600

.dx-change
  min-width: 42px
  color: var(--text-3)
  font-size: 0.85rem
  text-align: right

  &.increased
    color: var(--success)
    font-weight: 600

.show-more
  align-self: center

.favorite-grid
  display: grid
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr))
  gap: 10px

.favorite
  display: flex
  align-items: center
  gap: 12px
  min-width: 0
  padding: 8px
  border: 1px solid var(--border)
  border-radius: var(--radius)
  background: var(--surface)

  img
    width: 44px
    height: 44px
    flex: 0 0 44px
    border-radius: var(--radius-sm)
    object-fit: cover

.profile-skeleton
  display: flex
  flex-direction: column
  gap: 16px

// Score detail dialog
.detail-summary
  display: flex
  align-items: center
  gap: 16px
  margin-bottom: 16px

  .detail-cover
    width: 84px
    height: 84px
    flex: 0 0 84px
    border-radius: var(--radius)
    object-fit: cover

  > div
    display: grid
    justify-items: start
    gap: 6px

.detail-stats
  display: grid
  grid-template-columns: repeat(4, minmax(0, 1fr))
  gap: 8px
  margin-bottom: 16px

  > div
    display: grid
    gap: 2px
    padding: 10px 12px
    border-radius: var(--radius)
    background: var(--surface-2)

  span, small
    color: var(--text-2)
    font-size: 0.75rem

  strong
    font-size: 1.05rem

  .positive
    color: var(--success)

.judgment-table
  td, th
    padding: 9px 12px

  .critical
    color: var(--warning)
    font-weight: 600

  .miss
    color: var(--danger)

.timing
  display: flex
  gap: 8px
  margin-top: 12px

.detail-message
  display: flex
  align-items: center
  justify-content: center
  gap: 10px
  padding: 18px
  border-radius: var(--radius)
  color: var(--text-2)
  background: var(--surface-2)

@media (max-width: vars.$w-tablet)
  .overview-grid
    grid-template-columns: 1fr

  .activity-stats
    grid-template-columns: repeat(3, minmax(0, 1fr))

@media (max-width: vars.$w-mobile)
  .profile-hero
    .avatar
      width: 68px
      height: 68px
      flex-basis: 68px

  .hero-actions
    width: 100%

    > button
      flex: 1

  .stat-grid
    grid-template-columns: repeat(2, minmax(0, 1fr))
    gap: 8px

  .stat
    padding: 12px

    strong
      font-size: 1.15rem

  .trend
    height: 180px

  .activity-stats
    grid-template-columns: repeat(2, minmax(0, 1fr))

  .score-row
    gap: 10px
    padding-right: 12px

    > img
      width: 42px
      height: 42px
      flex-basis: 42px

  .dx-change
    display: none

  .detail-stats
    grid-template-columns: repeat(2, minmax(0, 1fr))
</style>
