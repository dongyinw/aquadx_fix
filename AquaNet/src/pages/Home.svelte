<script lang="ts">
  import Icon from "@iconify/svelte";
  import { CARD, GAME, USER } from "../libs/sdk.js";
  import type { CardSummary, MikuNetUser } from "../libs/generalTypes";
  import type { GameName } from "../libs/scoring";
  import StatusOverlays from "../components/StatusOverlays.svelte";
  import { t } from "../libs/i18n";
  import ImportDataAction from "./Home/ImportDataAction.svelte";
  import * as acUrl from "../libs/acUrl";

  USER.ensureLoggedIn();

  let me: MikuNetUser;
  let error = "";
  let gameProfiles: CardSummary | null = null;
  let exportLoading = false;

  const gameLabels: Record<GameName, string> = {
    mai2: "舞萌DX",
    chu3: "中二节奏",
    ongeki: "音击",
    wacca: "WACCA"
  };

  USER.me().then(async (m) => {
    me = m;
    try {
      gameProfiles = await CARD.userGames(m.username);
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }).catch(e => error = e.message)

  if (acUrl.has()) location.href = "/cards"

  function gameStatus(game: GameName) {
    const profile = gameProfiles?.[game];
    return profile ? `${gameLabels[game]} · 已有资料` : "尚未建立资料";
  }

  async function exportData() {
    if (!gameProfiles || exportLoading) return;
    exportLoading = true;
    try {
      const games = (Object.keys(gameProfiles) as GameName[]).filter(game => !!gameProfiles?.[game]);
      const exported: Record<string, unknown> = {};
      for (const game of games) exported[game] = await GAME.export(game);
      const blob = new Blob([JSON.stringify({ exportedAt: new Date().toISOString(), games: exported }, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `MikuNet_data_${me.username}.json`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      exportLoading = false;
    }
  }
</script>

<main class="content dashboard-page">
  {#if me}
    <header class="dashboard-header">
      <div>
        <span class="eyebrow">MIKUNET / DASHBOARD</span>
        <h1>你好，{me.computedName}</h1>
        <p>你的街机资料都在这里。</p>
      </div>
      <a class="profile-summary" href={`/u/${me.username}`}>
        <img src="/assets/icons/android-chrome-192x192.png" alt="" />
        <span><strong>@{me.username}</strong><small>{me.cards.length} 张已绑定卡片</small></span>
        <Icon icon="line-md:chevron-small-right" />
      </a>
    </header>

    <section class="overview-grid">
      <div class="overview-card accent-teal"><span class="overview-icon"><Icon icon="solar:card-bold-duotone" /></span><small>已绑定卡片</small><strong>{me.cards.length}</strong><a href="/cards">管理卡片 <Icon icon="line-md:arrow-right" /></a></div>
      <div class="overview-card accent-coral"><span class="overview-icon"><Icon icon="solar:gameboy-bold-duotone" /></span><small>游玩资料</small><strong>{gameProfiles ? `${Object.values(gameProfiles).filter(Boolean).length} 项` : "读取中"}</strong><a href={`/u/${me.username}`}>查看档案 <Icon icon="line-md:arrow-right" /></a></div>
      <div class="overview-card accent-gold"><span class="overview-icon"><Icon icon="solar:settings-bold-duotone" /></span><small>账号状态</small><strong>{me.emailConfirmed ? "已验证" : "待验证"}</strong><a href="/settings">账号设置 <Icon icon="line-md:arrow-right" /></a></div>
    </section>

    <section class="dashboard-section">
      <div class="section-heading"><div><span class="eyebrow">QUICK ACCESS</span><h2>常用入口</h2></div><span>选择一个操作继续</span></div>
      <div class="quick-grid">
        <a class="quick-card" href={`/u/${me.username}`}><span class="quick-icon teal"><Icon icon="solar:user-circle-bold-duotone" /></span><span><strong>个人档案</strong><small>{gameProfiles ? (Object.keys(gameProfiles) as GameName[]).filter(game => gameProfiles?.[game]).map(game => gameLabels[game]).join("、") || "尚未建立游戏资料" : "正在读取游戏资料"}</small></span><Icon class="arrow" icon="line-md:arrow-right" /></a>
        <a class="quick-card" href="/cards"><span class="quick-icon coral"><Icon icon="solar:card-bold-duotone" /></span><span><strong>卡片管理</strong><small>绑定新的 Aime 卡或查看已绑定卡片</small></span><Icon class="arrow" icon="line-md:arrow-right" /></a>
        <a class="quick-card" href="/setup"><span class="quick-icon gold"><Icon icon="solar:plug-circle-bold-duotone" /></span><span><strong>连接设置</strong><small>查看游戏端连接配置</small></span><Icon class="arrow" icon="line-md:arrow-right" /></a>
        <a class="quick-card" href="/ranking"><span class="quick-icon blue"><Icon icon="solar:chart-2-bold-duotone" /></span><span><strong>排行榜</strong><small>浏览服务器上的成绩排名</small></span><Icon class="arrow" icon="line-md:arrow-right" /></a>
        <a class="quick-card" href="/pass"><span class="quick-icon rainbow"><Icon icon="solar:ticket-bold-duotone" /></span><span><strong>Magical Pass</strong><small>购买通行证并下载专属素材</small></span><Icon class="arrow" icon="line-md:arrow-right" /></a>
      </div>
    </section>

    <section class="dashboard-section import-section">
      <div class="section-heading"><div><span class="eyebrow">TOOLS</span><h2>数据工具</h2></div><span>备份与恢复你的游戏资料</span></div>
      <div class="tool-grid">
        <ImportDataAction />
        <button class="export-card" type="button" on:click={exportData} disabled={!gameProfiles || exportLoading}>
          <span class="tool-icon"><Icon icon="solar:file-download-bold-duotone" /></span>
          <span><strong>{exportLoading ? "正在导出" : "导出玩家数据"}</strong><small>{gameProfiles ? (Object.keys(gameProfiles) as GameName[]).filter(game => gameProfiles?.[game]).map(game => gameLabels[game]).join("、") || "暂无可导出的资料" : "正在读取资料"}</small></span>
          <Icon class="arrow" icon="line-md:arrow-right" />
        </button>
      </div>
      <div class="profile-status-list">
        {#each Object.keys(gameLabels) as game}
          <span class:ready={!!gameProfiles?.[game as GameName]}><i></i>{gameStatus(game as GameName)}</span>
        {/each}
      </div>
    </section>
  {/if}
</main>

<StatusOverlays {error} loading={!me} />

<style lang="sass">
  @use "../vars"

  .dashboard-page
    gap: 30px

  .dashboard-header, .section-heading, .profile-summary, .quick-card
    display: flex
    align-items: center

  .dashboard-header
    justify-content: space-between
    gap: 20px

    h1
      margin: 8px 0 4px
      color: vars.$c-text

    p
      margin: 0
      color: vars.$c-sub

  .eyebrow
    color: vars.$c-main
    font-size: 0.7rem
    font-weight: 800
    letter-spacing: 0.13em

  .profile-summary
    gap: 10px
    padding: 8px 11px 8px 8px
    border: 1px solid rgba(255, 255, 255, 0.88)
    border-radius: 13px
    color: vars.$c-text
    background: rgba(255, 255, 255, 0.64)
    box-shadow: 0 12px 26px rgba(43, 72, 76, 0.08)

    &:hover
      color: vars.$c-main

    img
      width: 34px
      height: 34px
      border-radius: 10px

    span
      display: grid
      gap: 1px

    strong
      font-size: 0.82rem

    small
      color: vars.$c-sub
      font-size: 0.7rem

  .overview-grid
    display: grid
    grid-template-columns: repeat(3, 1fr)
    gap: 14px

  .overview-card
    display: grid
    grid-template-columns: auto 1fr
    gap: 2px 11px
    padding: 18px
    border: 1px solid rgba(255, 255, 255, 0.84)
    border-radius: 15px
    background: rgba(255, 255, 255, 0.62)
    box-shadow: 0 14px 35px rgba(43, 72, 76, 0.08)
    backdrop-filter: blur(16px)

    .overview-icon
      grid-row: span 3
      display: grid
      place-items: center
      width: 42px
      height: 42px
      border-radius: 12px
      font-size: 1.45rem

    small
      align-self: end
      color: vars.$c-sub
      font-size: 0.75rem

    strong
      color: vars.$c-text
      font-size: 1.35rem

    a
      display: inline-flex
      align-items: center
      gap: 5px
      font-size: 0.75rem

  .accent-teal .overview-icon, .teal
    color: vars.$c-main
    background: vars.$c-main-soft

  .accent-coral .overview-icon, .coral
    color: vars.$c-error
    background: rgba(196, 73, 94, 0.10)

  .accent-gold .overview-icon, .gold
    color: #af7e16
    background: rgba(229, 171, 67, 0.16)

  .blue
    color: #3876a7
    background: rgba(56, 118, 167, 0.11)

  .rainbow
    color: #b34d88
    background: linear-gradient(135deg, rgba(112, 198, 255, 0.22), rgba(239, 135, 181, 0.22))

  .dashboard-section
    display: grid
    gap: 14px

  .section-heading
    justify-content: space-between
    gap: 16px

    h2
      margin: 4px 0 0
      color: vars.$c-text
      font-size: 1.3rem

    > span
      color: vars.$c-sub
      font-size: 0.78rem

  .quick-grid
    display: grid
    grid-template-columns: repeat(2, 1fr)
    gap: 10px

  .tool-grid
    display: grid
    grid-template-columns: repeat(2, 1fr)
    gap: 12px

  .export-card
    display: flex
    align-items: center
    gap: 12px
    min-width: 0
    padding: 16px
    border: 1px solid rgba(255, 255, 255, 0.82)
    border-radius: 13px
    color: vars.$c-text
    background: rgba(255, 255, 255, 0.62)
    text-align: left

    &:disabled
      cursor: wait
      opacity: 0.7

    > span:nth-child(2)
      display: grid
      min-width: 0
      flex: 1
      gap: 2px

    strong, small
      overflow: hidden
      text-overflow: ellipsis
      white-space: nowrap

    strong
      font-size: 0.92rem

    small
      color: vars.$c-sub
      font-size: 0.73rem

  .tool-icon
    display: grid
    place-items: center
    width: 38px
    height: 38px
    flex: 0 0 38px
    border-radius: 11px
    color: #3876a7
    background: rgba(56, 118, 167, 0.11)
    font-size: 1.3rem

  .profile-status-list
    display: flex
    flex-wrap: wrap
    gap: 8px

    span
      display: inline-flex
      align-items: center
      gap: 6px
      padding: 6px 9px
      border: 1px solid rgba(96, 114, 118, 0.14)
      border-radius: 999px
      color: vars.$c-sub
      font-size: 0.72rem

    i
      width: 7px
      height: 7px
      border-radius: 50%
      background: vars.$c-muted

    .ready
      color: vars.$c-good

      i
        background: vars.$c-good

  .quick-card
    gap: 12px
    min-width: 0
    padding: 15px
    border: 1px solid rgba(255, 255, 255, 0.82)
    border-radius: 13px
    color: vars.$c-text
    background: rgba(255, 255, 255, 0.56)
    transition: vars.$transition

    &:hover
      color: vars.$c-text
      background: rgba(255, 255, 255, 0.88)
      box-shadow: 0 13px 30px rgba(43, 72, 76, 0.10)
      transform: translateY(-2px)

    > span:nth-child(2)
      display: grid
      min-width: 0
      flex: 1
      gap: 2px

    strong, small
      overflow: hidden
      text-overflow: ellipsis
      white-space: nowrap

    strong
      font-size: 0.92rem

    small
      color: vars.$c-sub
      font-size: 0.73rem

    .arrow
      color: vars.$c-muted

  .quick-icon
    display: grid
    place-items: center
    width: 38px
    height: 38px
    flex: 0 0 38px
    border-radius: 11px
    font-size: 1.3rem

  @media (max-width: vars.$w-mobile)
    .dashboard-header
      align-items: flex-start
      flex-direction: column

    .profile-summary
      align-self: stretch

    .overview-grid, .quick-grid, .tool-grid
      grid-template-columns: 1fr

    .section-heading
      align-items: flex-start
      flex-direction: column
</style>
