<script lang="ts">
  import { onMount } from "svelte";
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
  import Icon from "@iconify/svelte";
  import { countryCodeToEmoji, GAME_TITLE, t } from "../libs/i18n";
  import RankDetails from "../components/RankDetails.svelte";
  import RatingComposition from "../components/RatingComposition.svelte";
  import useLocalStorage from "../libs/hooks/useLocalStorage.svelte";
  import Line from "../components/chart/Line.svelte";
  import ChuniUserboxDisplay from "../components/settings/userbox/ChuniUserboxDisplay.svelte";

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
  let showMoreRecent = false
  let selectedScore: MusicAndPlay | null = null
  let detailLoading = false
  let detailError = ""

  const judgmentRows = [
    { label: "Tap", prefix: "tap" },
    { label: "Hold", prefix: "hold" },
    { label: "Slide", prefix: "slide" },
    { label: "Touch", prefix: "touch" },
    { label: "Break", prefix: "break" },
  ]

  const hasJudgments = (score: GenericGamePlaylog) => score.tapCriticalPerfect !== undefined
  const judgmentValue = (score: GenericGamePlaylog, prefix: string, grade: string) =>
    Number((score as Record<string, unknown>)[`${prefix}${grade}`] ?? 0)

  function openScore(score: MusicAndPlay) {
    selectedScore = score
    detailError = ""

    if (!score.id || hasJudgments(score)) return

    detailLoading = true
    GAME.playlog(game, score.id).then(detail => {
      selectedScore = { ...score, ...detail } as MusicAndPlay
    }).catch(e => detailError = e.message).finally(() => detailLoading = false)
  }

  function closeScore() {
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
        console.log(user)
        console.log(trend)
        console.log(games)

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
        renderCal(calElement, trend.map(it => {return {date: it.date, value: it.plays}})).then(() => {
          // Scroll to the rightmost
          calElement.scrollLeft = calElement.scrollWidth - calElement.clientWidth
        })
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
    }).catch(e => error = e.message).finally(() => isLoading = false)
  }
</script>

<main id="user-home" class="content">
  {#if d}
    <div class="user-pfp">
      <img use:pfp={d.user.aquaUser} alt="" class="pfp" on:error={pfpNotFound}>
      <div class="name-box">
        <div class="name-left">

          {#if d.user.aquaUser}
            {#if d.user.aquaUser.displayName}
              <h2>{d.user.aquaUser?.displayName}</h2>
            {:else}
              <h2>{d.user.name}</h2>
            {/if}
            <div class="game-name">
              {#if d.user.aquaUser.displayName}
                {d.user.name}
              {/if}
              (@{d.user.aquaUser.username})
            </div>
            <div class="country">{countryCodeToEmoji(d.user.aquaUser?.country)}</div>
          {:else}
            <h2>{d.user.name}</h2>
          {/if}
        </div>
        {#if typeof d.user.rival === 'boolean' && game === 'mai2'}
          <span class="clickable" on:click={() => setRival(!d?.user.rival)} role="button" tabindex="0"
             on:keydown={e => e.key === "Enter" && setRival(!d?.user.rival)}>
            {d.user.rival ? t("UserHome.RemoveRival") : t("UserHome.AddRival")}
          </span>
        {/if}
      </div>
      <nav>
        {#each d.validGames as [g, name]}
          <a href={`/u/${username}/${g}`} class:active={game === g}>{name}</a>
        {/each}

        {#if me && me.username.toLowerCase() === username.toLowerCase()}
          <a class="setting-icon clickable" use:tooltip={t("UserHome.Settings")} href={`/settings/${game}`}>
            <Icon icon="eos-icons:rotating-gear"/>
          </a>
        {/if}
      </nav>
    </div>

    {#if d.user.aquaUser?.profileBio}
      <div class="activity-info">
        <div class="info-bottom profile-bio-container">
          <div class="profile-bio">
            <span>{t("settings.profile.bio")}</span>
            <span class="profile-bio-text">{d.user.aquaUser?.profileBio}</span>
          </div>
        </div>
      </div>
    {/if}

    <ChuniUserboxDisplay {game} {username} bind:error={error} />

    <div>
      <h2>{GAME_TITLE[game] ?? "?"} {t('UserHome.Statistics')}</h2>
      <div class="scoring-info">
        <div class="chart">
          <div class="info-top">


            {#if game == "ongeki" && d.user.ratingNotGeneric}
              <div class="rating">
                <span>{t("UserHome.Rating")}</span>
                <span>{(d.user.rating / 1000).toFixed(3)}</span>
              </div>
            {:else}
              <div class="rating">
                <span>{game === 'mai2' ? t("UserHome.DXRating"): t("UserHome.Rating")}</span>
                <!-- NOTE: this is for legacy ongeki display, so 1.45 profiles are at least usable -->
                <span>{
                  game === 'chu3' || game === 'ongeki' ?
                    (d.user.rating / 100).toFixed(2) :
                    d.user.rating.toLocaleString()
                }</span>
              </div>
            {/if}

            {#if !isNaN(parseInt(d.user.serverRank))}
              <div class="rank">
                <span>{t('UserHome.ServerRank')}</span>
                <span>#{(d.user.serverRank).toLocaleString()}</span>
              </div>
            {/if}
          </div>

          <div class="trend">
            <!-- ChartJS cannot be fully responsive unless there is a parent div that's independent from its size and helps it determine its size -->
            <div class="chartjs-box-reference">
              {#if d.trend.length <= 1}
                <div class="no-data">{t("UserHome.NoData", { days: TREND_DAYS })}</div>
              {:else}
                <Line data={{
                  datasets: [
                    {
                      label: 'Rating',
                      data: d.trend.map(it => {return {x: Date.parse(it.date), y: it.rating}}),
                      borderColor: '#646cff',
                      tension: 0.1,

                      // TODO: Set X axis span to 3 months
                    }
                  ]
                }} options={CHARTJS_OPT} />
              {/if}
            </div>
          </div>

          {#if Object.entries(d.user.detailedRanks).length > 0}
            <div class="info-bottom clickable" use:tooltip={t("UserHome.ShowRanksDetails")}
                 on:click={() => showDetailRank = !showDetailRank} role="button" tabindex="0"
                 on:keydown={e => e.key === "Enter" && (showDetailRank = !showDetailRank)}>
              {#each d.user.ranks as r}
                <div><span>{r.name}</span><span>{r.count}</span></div>
              {/each}
            </div>
          {:else}
            <div class="info-bottom">
              {#each d.user.ranks as r}
                <div><span>{r.name}</span><span>{r.count}</span></div>
              {/each}
            </div>
          {/if}
        </div>

        <div class="other-info">
          <div class="accuracy">
            <span>{t('UserHome.Accuracy')}</span>
            <span>{(d.user.accuracy).toFixed(2)}%</span>
          </div>

          {#if game == "ongeki" && d.user.ratingNotGeneric}
            <div class="rating">
              <span>{t("UserHome.HighestRating")}</span>
              <span>{(d.user.ratingHighest / 1000).toFixed(3)}</span>
            </div>
          {:else}
            <div class="rating">
              <span>{game === 'mai2' ? t("UserHome.HighestDXRating"): t("UserHome.HighestRating")}</span>
              <!-- NOTE: this is for legacy ongeki display, so 1.45 profiles are at least usable -->
              <span>{
                game === 'chu3' || game === 'ongeki' ?
                  (d.user.ratingHighest / 100).toFixed(2) :
                  d.user.ratingHighest.toLocaleString()
              }</span>
            </div>
          {/if}

          <div class="max-combo">
            <span>{t("UserHome.MaxCombo")}</span>
            <span>{d.user.maxCombo}</span>
          </div>

          <div class="full-combo">
            <span>{t("UserHome.FullCombo")}</span>
            <span>{d.user.fullCombo}</span>
          </div>

          <div class="all-perfect">
            <span>{t("UserHome.AllPerfect")}</span>
            <span>{d.user.allPerfect}</span>
          </div>

          <div class="total-dx-score">
            <span>{game === 'mai2' ? t('UserHome.DXScore') : t("UserHome.Score")}</span>
            <span>{d.user.totalScore.toLocaleString()}</span>
          </div>
        </div>
      </div>
    </div>

    {#if showDetailRank}<RankDetails g={d.user}/>{/if}

    <div>
      <h2>{t('UserHome.PlayActivity')}</h2>
      <div class="activity-info">
        <div class="hide-scrollbar" id="cal-heatmap" bind:this={calElement}></div>

        <div class="info-bottom">
          <div class="plays">
            <span>{t("UserHome.Plays")}</span>
            <span>{d.user.plays}</span>
          </div>

          <div class="time">
            <span>{t('UserHome.PlayTime')}</span>
            <span>{(d.user.totalPlayTime / 60).toFixed(1)} hr</span>
          </div>

          <div class="first-play">
            <span>{t('UserHome.FirstSeen')}</span>
            <span>{moment(d.user.joined).format("YYYY-MM-DD")}</span>
          </div>

          <div class="last-play">
            <span>{t('UserHome.LastSeen')}</span>
            <span>{moment(d.user.lastSeen).format("YYYY-MM-DD")}</span>
          </div>

          <div class="last-version">
            <span>{t('UserHome.Version')}</span>
            <span>{d.user.lastVersion}</span>
          </div>
        </div>
      </div>
    </div>

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

    <div class="recent">
      <h2>{t('UserHome.RecentScores')}</h2>
      <div class="scores">
        {#each (showMoreRecent ? d.recent : d.recent.slice(0, 15)) as r, i}
          <div class:alt={i % 2 === 0} class="score-entry" role="button" tabindex="0"
               on:click={() => openScore(r)}
               on:keydown={e => (e.key === "Enter" || e.key === " ") && openScore(r)}>
            <img src={`${DATA_HOST}/d/${game}/music/00${r.musicId.toString().padStart(6, '0').substring(2)}.png`} alt="" on:error={coverNotFound} />
            <div class="info">
              <div>{r.name ?? t("UserHome.UnknownSong")}</div>
              <div>
                {#if r.isAllPerfect || r.isAllJustice}
                  <img src="/assets/imgs/All Perfect.png" alt="All Perfect" />
                {:else if r.isFullCombo}
                  <img src="/assets/imgs/Full Combo.png" alt="Full Combo" />
                {/if}
                <span class={`lv level-${r.level === 10 ? 5 : r.level}`}>
                  <span>
                    {r.notes?.[r.level === 10 ? 0 : r.level]?.lv?.toFixed(1) ?? r.worldsEndTag ?? '-'}
                  </span>
                </span>
                <span class={`rank-${getMult(r.achievement, game)[2].toString()[0]}`}>
                  <span class="rank-text">{("" + getMult(r.achievement, game)[2]).replace("p", "+")}</span>
                  <span class="rank-num" use:tooltip={(r.achievement / 10000).toFixed(4)}>
                    {
                      rounding.value ?
                        roundFloor(r.achievement, game, 1) :
                        (r.achievement / 10000).toFixed(4)
                    }%
                  </span>
                </span>
                <span class:increased={r.afterRating - r.beforeRating > 0} class="dx-change">
                  {r.afterRating === r.beforeRating ? '-' : (r.afterRating - r.beforeRating).toFixed(0)}
                </span>
              </div>
            </div>
          </div>
        {/each}

        {#if !showMoreRecent}
          <button class="clickable" on:click={() => showMoreRecent = true}>{t('UserHome.ShowMoreRecent')}</button>
        {/if}
      </div>
    </div>

    {#if d.user.favorites != null && d.user.favorites.length > 0}
      <div class="favorites">
        <h2>{t('UserHome.FavoriteSongs')}</h2>
        <div class="scores">
          {#each d.user.favorites as favoriteSongId, i}
            <div>
              <img src={`${DATA_HOST}/d/${game}/music/00${favoriteSongId.toString().padStart(6, '0').substring(2)}.png`} alt="" on:error={coverNotFound} />
              <div class="info">
                <div class="song-title">{allMusics[favoriteSongId.toString()] ? allMusics[favoriteSongId.toString()].name : t("UserHome.UnknownSong")}</div>
              </div>
            </div>
          {/each}
        </div>
      </div>
    {/if}
  {/if}

  <StatusOverlays {error} loading={!d || isLoading} />

{#if selectedScore}
  <div class="score-detail-overlay" role="presentation" on:click={(e) => e.currentTarget === e.target && closeScore()}>
    <div class="score-detail" role="dialog" aria-modal="true" aria-label="成绩详情">
      <div class="detail-header">
        <div>
          <span>成绩详情</span>
          <h2>{selectedScore.name ?? t("UserHome.UnknownSong")}</h2>
        </div>
        <button class="icon" type="button" on:click={closeScore} aria-label="关闭"><Icon icon="line-md:close" /></button>
      </div>

      <div class="detail-summary">
        <img src={`${DATA_HOST}/d/${game}/music/00${selectedScore.musicId.toString().padStart(6, '0').substring(2)}.png`} alt="" on:error={coverNotFound} />
        <div>
          <strong>{moment(selectedScore.userPlayDate ?? selectedScore.playDate).format("YYYY-MM-DD HH:mm")}</strong>
          <span>{GAME_TITLE[game]} · {selectedScore.notes?.[selectedScore.level === 10 ? 0 : selectedScore.level]?.lv?.toFixed(1) ?? selectedScore.worldsEndTag ?? '-'}</span>
          <span>{selectedScore.isAllPerfect || selectedScore.isAllJustice ? "ALL PERFECT" : selectedScore.isFullCombo ? "FULL COMBO" : "PLAY RESULT"}</span>
        </div>
      </div>

      <div class="detail-stats">
        <div><span>达成率</span><strong>{(selectedScore.achievement / 10000).toFixed(4)}%</strong><small>{("" + getMult(selectedScore.achievement, game)[2]).replace("p", "+")}</small></div>
        <div><span>DX 分</span><strong>{(selectedScore.deluxscore ?? selectedScore.totalDxScore ?? 0).toLocaleString()}</strong></div>
        <div><span>最大连击</span><strong>{selectedScore.maxCombo.toLocaleString()}<small> / {selectedScore.totalCombo.toLocaleString()}</small></strong></div>
        <div><span>Rating 变化</span><strong class:positive={selectedScore.afterRating > selectedScore.beforeRating}>{selectedScore.afterRating > selectedScore.beforeRating ? "+" : ""}{selectedScore.afterRating - selectedScore.beforeRating}</strong></div>
      </div>

      {#if detailLoading}
        <div class="detail-message"><Icon icon="line-md:loading-twotone-loop" />正在读取逐项判定…</div>
      {:else if detailError}
        <div class="detail-message error">详情读取失败：{detailError}</div>
      {:else if hasJudgments(selectedScore)}
        <div class="judgment-table">
          <div class="judgment-row header"><span>判定</span><span>Critical</span><span>Perfect</span><span>Great</span><span>Good</span><span>Miss</span></div>
          {#each judgmentRows as row}
            <div class="judgment-row"><strong>{row.label}</strong><span class="critical">{judgmentValue(selectedScore, row.prefix, "CriticalPerfect")}</span><span>{judgmentValue(selectedScore, row.prefix, "Perfect")}</span><span>{judgmentValue(selectedScore, row.prefix, "Great")}</span><span>{judgmentValue(selectedScore, row.prefix, "Good")}</span><span class="miss">{judgmentValue(selectedScore, row.prefix, "Miss")}</span></div>
          {/each}
        </div>
        <div class="timing"><span>FAST {selectedScore.fastCount ?? 0}</span><span>LATE {selectedScore.lateCount ?? 0}</span><span>{selectedScore.isFreedomMode ? "FREEDOM" : selectedScore.isNewFree ? "NEW FREE" : "STANDARD"}</span></div>
      {:else}
        <div class="detail-message">该成绩没有返回逐项判定数据</div>
      {/if}
    </div>
  </div>
{/if}
</main>

<style lang="sass">
@use "../vars"

#user-home
  .user-pfp
    display: flex
    align-items: flex-end
    gap: vars.$gap
    margin-top: -72px
    position: relative

    h2
      font-size: 2rem
      margin: 0
      white-space: nowrap

    nav
      position: absolute
      display: flex
      flex-direction: row
      gap: 10px
      top: 4px
      right: 0

    .setting-icon
      font-size: 1.5rem
      color: vars.$c-main
      cursor: pointer
      display: flex
      align-items: center

      position: relative
      z-index: 20

    .name-box
      flex: 1
      display: flex
      align-items: center
      justify-content: space-between
      gap: 10px

      .name-left
        display: flex
        gap: 1em
        position: relative

        .game-name
          position: absolute
          left: 0.5em
          bottom: 0
          transform: translate(0, 75%)
          opacity: 50%
          white-space: nowrap
          max-width: 50%

  .pfp
    width: 100px
    height: 100px
    border-radius: vars.$border-radius
    object-fit: cover

  @media (max-width: vars.$w-mobile)
    .user-pfp
      margin-top: -68px
      h2
        font-size: 1.5rem

    .pfp
      width: 80px
      height: 80px

  .info-bottom, .info-top, .other-info
    display: flex
    gap: vars.$gap

    > div
      display: flex
      flex-direction: column

      > span:first-child
        font-weight: bold
        font-size: 0.8rem

        // character spacing
        letter-spacing: 0.1em
        color: vars.$c-main

  .info-bottom
    width: max-content

    &.profile-bio-container,
    &.profile-bio-container div
      width: 100%

    .profile-bio-text
      white-space: pre
      max-height: 10em
      overflow-y: auto
      flex: 1

  .info-top > div > span:last-child
    font-size: 1.5rem

  .info-bottom
    max-width: 100%
    flex-wrap: wrap
    row-gap: 0.5rem

  .scoring-info
    display: flex
    gap: vars.$gap
    max-height: 300px

    .chart
      flex: 0 1 790px
      display: flex
      flex-direction: column

    .other-info
      flex: 1 0 100px
      flex-direction: column
      gap: 0
      justify-content: space-between

    .trend
      height: 300px
      width: 100%
      max-width: 790px

      position: relative

      > .chartjs-box-reference
        position: absolute
        inset: 0
        display: flex
        align-items: center
        justify-content: center

        .no-data
          opacity: 0.5
          user-select: none

    @media (max-width: vars.$w-mobile)
      flex-direction: column
      max-height: unset

      .chart
        flex: 0

        .trend
          max-height: 130px

      .other-info
        > div
          flex-direction: row
          justify-content: space-between

      .info-bottom
        justify-content: space-between

  .activity-info
    display: flex
    flex-direction: column
    gap: vars.$gap

    #cal-heatmap
      overflow-x: auto

    @media (max-width: vars.$w-mobile)
      #cal-heatmap
        width: 100%

      .info-bottom
        flex-direction: column
        gap: 0
        width: 100%

        > div
          flex-direction: row
          justify-content: space-between

  .favorites
    .scores
      display: grid
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr))
      gap: 20px

      // Image and song info
      > div
        display: flex
        align-items: center
        gap: 20px

        background-color: rgba(white, 0.03)
        border-radius: vars.$border-radius

        img
          width: 50px
          height: 50px
          border-radius: vars.$border-radius
          object-fit: cover

        // Song info and score
        > div.info
          flex: 1
          display: flex
          justify-content: space-between
          overflow: hidden
          flex-direction: column

          // Limit song name to one line
          .song-title
            max-width: 90%
            overflow: hidden
            text-overflow: ellipsis
            white-space: nowrap

          // Make song score and rank not wrap
          > div:last-child
            white-space: nowrap

          @media (max-width: vars.$w-mobile)
            flex-direction: column
            gap: 0

  // Recent Scores section
  .recent
    .scores
      display: flex
      flex-direction: column
      flex-wrap: wrap
      gap: vars.$gap

      > div.alt
        background-color: rgba(white, 0.03)
        border-radius: vars.$border-radius

      // Image and song info
      > div
        display: flex
        align-items: center
        gap: vars.$gap
        padding-right: 16px
        max-width: 100%
        box-sizing: border-box

        img
          width: 50px
          height: 50px
          border-radius: vars.$border-radius
          object-fit: cover

        // Song info and score
        > div.info
          flex: 1
          display: flex
          justify-content: space-between
          align-items: center
          overflow: hidden

          // Limit song name to one line
          > div:first-child
            flex: 1
            min-width: 0
            overflow: hidden
            text-overflow: ellipsis
            white-space: nowrap

          // Make song score and rank not wrap
          > div:last-child
            white-space: nowrap
            display: flex
            align-items: center
            gap: 10px

            img
              height: 1.5em
              width: 1.5em

          @media (max-width: vars.$w-mobile)
            flex-direction: column
            gap: 0

            .rank-text
              text-align: left

            // Score and rank should be space-between on mobile
            > div:last-child
              display: flex
              justify-content: space-between
              gap: 10px

              .lv
                margin-right: auto

        .rank-S
          // Gold green gradient on text
          background: vars.$grad-special
          -webkit-background-clip: text
          color: transparent

        .rank-A
          color: #ff8a8a

        .rank-B
          color: #6ba6ff

        .lv
          min-width: 30px
          text-align: center
          background: rgba(var(--lv-color), 0.6)
          padding: 0 6px
          border-radius: vars.$border-radius

        .lv.level-5 > span
          color: transparent
          background: var(--lv-text-clip)
          background-clip: text
          -webkit-background-clip: text
          font-weight: bold
          font-size: 1em
          font-family: 'Arial Black', sans-serif

        span
          display: inline-block
          text-align: right

        // Vertical table-like alignment
        span.rank-text
          min-width: 38px
        span.rank-num
          min-width: 60px
        span.dx-change
          min-width: 30px

      span.increased
        &:before
          content: "+"
        color: vars.$c-good

  .score-entry
    cursor: pointer
    transition: vars.$transition

    &:hover, &:focus-visible
      background-color: rgba(vars.$c-main, 0.1) !important
      outline: none

  .score-detail-overlay
    position: fixed
    inset: 0
    z-index: 1000
    display: flex
    align-items: center
    justify-content: center
    padding: 1rem
    background: rgba(0, 0, 0, 0.72)
    backdrop-filter: blur(5px)

  .score-detail
    width: min(680px, 100%)
    max-height: calc(100vh - 2rem)
    overflow-y: auto
    padding: 1.5rem
    box-sizing: border-box
    border-radius: vars.$border-radius
    background: vars.$c-bg
    box-shadow: 0 12px 45px vars.$c-shadow

  .detail-header
    display: flex
    align-items: flex-start
    justify-content: space-between
    gap: 1rem

    > div > span
      color: vars.$c-main
      font-size: 0.8rem
      letter-spacing: 0.08em

    h2
      margin: 0.2rem 0 0
      font-size: 1.4rem

  .detail-summary
    display: flex
    align-items: center
    gap: 1rem
    margin: 1.25rem 0
    padding-bottom: 1.25rem
    border-bottom: 1px solid vars.$ov-light

    img
      width: 80px
      height: 80px
      border-radius: vars.$border-radius
      object-fit: cover

    div
      display: flex
      flex-direction: column
      gap: 0.25rem

      span
        color: vars.$c-main
        font-size: 0.8rem

      span:first-of-type
        color: vars.$c-sub

  .detail-stats
    display: grid
    grid-template-columns: repeat(4, 1fr)
    gap: 0.6rem
    margin-bottom: 1.25rem

    > div
      padding: 0.65rem
      border-radius: vars.$border-radius
      background: vars.$ov-light

      span, small
        display: block
        color: vars.$c-sub
        font-size: 0.72rem

      strong
        display: block
        margin-top: 0.25rem
        color: vars.$c-main
        font-size: 1.05rem

      small
        margin-top: 0.1rem

    .positive
      color: vars.$c-good

  .judgment-table
    overflow: hidden
    border: 1px solid vars.$c-main
    border-radius: vars.$border-radius

  .judgment-row
    display: grid
    grid-template-columns: 1.2fr repeat(5, 1fr)
    border-top: 1px solid rgba(vars.$c-main, 0.35)
    text-align: center

    &:first-child
      border-top: 0

    > *
      padding: 0.55rem 0.2rem

    strong
      text-align: left
      padding-left: 0.7rem

    .critical
      color: vars.$c-warning

    .miss
      color: vars.$c-error

  .judgment-row.header
    color: vars.$c-main
    background: vars.$ov-light
    font-size: 0.75rem

  .timing
    display: flex
    gap: 1rem
    margin-top: 0.8rem
    color: vars.$c-sub
    font-size: 0.8rem

  .detail-message
    padding: 1rem
    border-radius: vars.$border-radius
    color: vars.$c-sub
    background: vars.$ov-light
    text-align: center

    &.error
      color: vars.$c-error

  @media (max-width: vars.$w-mobile)
    .score-detail
      padding: 1rem

    .detail-stats
      grid-template-columns: repeat(2, 1fr)

    .judgment-row
      font-size: 0.72rem

      > *
        padding: 0.45rem 0.1rem


</style>
