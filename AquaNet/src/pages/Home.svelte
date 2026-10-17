<script lang="ts">
  import Icon from "../components/ui/Icon.svelte";
  import { CARD, GAME, USER } from "../libs/sdk.js";
  import type { CardSummary, MikuNetUser } from "../libs/generalTypes";
  import type { GameName } from "../libs/scoring";
  import StatusOverlays from "../components/StatusOverlays.svelte";
  import { t } from "../libs/i18n";
  import ImportDataAction from "./Home/ImportDataAction.svelte";
  import DashboardTabs from "../components/DashboardTabs.svelte";
  import Spinner from "../components/ui/Spinner.svelte";
  import { pfp } from "../libs/ui";
  import { toast } from "../libs/toast";
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

  $: playedGames = gameProfiles ? (Object.keys(gameLabels) as GameName[]).filter(game => gameProfiles?.[game]) : [];
  $: physicalCards = me ? me.cards.filter(card => !card.isGhost).length : 0;

  function greeting() {
    const hour = new Date().getHours();
    return hour < 6 ? "夜深了" : hour < 12 ? "早上好" : hour < 18 ? "下午好" : "晚上好";
  }

  async function exportData() {
    if (!gameProfiles?.mai2 || exportLoading) return;
    exportLoading = true;
    try {
      const exported = await GAME.export("mai2");
      const blob = new Blob([JSON.stringify(exported, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `MikuNet_data_${me.username}.json`;
      anchor.click();
      URL.revokeObjectURL(url);
      toast.success("玩家数据已开始下载");
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      exportLoading = false;
    }
  }
</script>

<main class="page dashboard-page">
  {#if me}
    <header class="dashboard-header">
      <a class="identity" href={`/u/${me.username}`}>
        <img alt="" use:pfp={me} />
        <div>
          <span class="muted">{greeting()}</span>
          <h1>{me.computedName}</h1>
          <span class="subtle">@{me.username}</span>
        </div>
      </a>
      <div class="header-actions">
        <a class="btn" href={`/u/${me.username}`}><Icon icon="solar:user-circle-bold-duotone" />个人档案</a>
        <a class="btn btn-icon" href="/settings" aria-label="账号设置" title="账号设置"><Icon icon="solar:settings-bold-duotone" /></a>
      </div>
    </header>

    <DashboardTabs />

    {#if !me.emailConfirmed}
      <div class="alert"><Icon icon="solar:letter-opened-bold-duotone" /><p>你的邮箱尚未验证，部分功能可能受限。请检查注册邮箱中的验证邮件。</p></div>
    {/if}

    <section class="stats" aria-label="账号概览">
      <a class="stat" href="/cards">
        <span class="stat-icon tone-teal"><Icon icon="solar:card-bold-duotone" /></span>
        <span class="stat-label">已绑定实体卡</span>
        <strong class="num">{physicalCards}</strong>
        <span class="stat-link">管理卡片<Icon icon="line-md:chevron-small-right" /></span>
      </a>
      <a class="stat" href={`/u/${me.username}`}>
        <span class="stat-icon tone-pink"><Icon icon="solar:gameboy-bold-duotone" /></span>
        <span class="stat-label">游戏资料</span>
        <strong class="num">{#if gameProfiles}{playedGames.length}<small> / 4</small>{:else}<span class="skeleton stat-skeleton"></span>{/if}</strong>
        <span class="stat-link">查看档案<Icon icon="line-md:chevron-small-right" /></span>
      </a>
      <a class="stat" href="/settings">
        <span class="stat-icon tone-gold"><Icon icon="solar:shield-check-bold-duotone" /></span>
        <span class="stat-label">账号状态</span>
        <strong class:success={me.emailConfirmed} class:warning={!me.emailConfirmed}>{me.emailConfirmed ? "已验证" : "待验证"}</strong>
        <span class="stat-link">账号设置<Icon icon="line-md:chevron-small-right" /></span>
      </a>
    </section>

    <section class="section">
      <div class="section-header"><div><h2>常用入口</h2><p>快速前往常用功能</p></div></div>
      <div class="tile-grid">
        <a class="tile" href={`/u/${me.username}`}><span class="tile-icon tone-teal"><Icon icon="solar:user-circle-bold-duotone" /></span><span class="tile-copy"><strong>个人档案</strong><small>{gameProfiles ? playedGames.map(game => gameLabels[game]).join("、") || "尚未建立游戏资料" : "正在读取游戏资料…"}</small></span><Icon class="arrow" icon="line-md:chevron-small-right" /></a>
        <a class="tile" href="/cards"><span class="tile-icon tone-pink"><Icon icon="solar:card-bold-duotone" /></span><span class="tile-copy"><strong>卡片管理</strong><small>绑定新的 Aime 卡或查看已绑定卡片</small></span><Icon class="arrow" icon="line-md:chevron-small-right" /></a>
        <a class="tile" href="/setup"><span class="tile-icon tone-gold"><Icon icon="solar:plug-circle-bold-duotone" /></span><span class="tile-copy"><strong>连接设置</strong><small>查看游戏端连接配置与 Keychip</small></span><Icon class="arrow" icon="line-md:chevron-small-right" /></a>
        <a class="tile" href="/ranking"><span class="tile-icon tone-blue"><Icon icon="solar:ranking-bold-duotone" /></span><span class="tile-copy"><strong>排行榜</strong><small>浏览服务器上的成绩排名</small></span><Icon class="arrow" icon="line-md:chevron-small-right" /></a>
        <a class="tile" href="/pass"><span class="tile-icon tone-violet"><Icon icon="solar:ticket-bold-duotone" /></span><span class="tile-copy"><strong>Magical Pass</strong><small>发放通行证并下载专属素材</small></span><Icon class="arrow" icon="line-md:chevron-small-right" /></a>
      </div>
    </section>

    <section class="section">
      <div class="section-header"><div><h2>数据工具</h2><p>备份与恢复你的游戏资料</p></div></div>
      <div class="tile-grid">
        <ImportDataAction />
        <button class="tile" type="button" on:click={exportData} disabled={!gameProfiles?.mai2 || exportLoading}>
          <span class="tile-icon tone-blue">{#if exportLoading}<Spinner />{:else}<Icon icon="solar:file-download-bold-duotone" />{/if}</span>
          <span class="tile-copy"><strong>{exportLoading ? "正在导出…" : "导出玩家数据"}</strong><small>{gameProfiles ? (gameProfiles.mai2 ? "导出舞萌 DX 资料为 JSON 文件" : "暂无舞萌 DX 资料可导出") : "正在读取资料…"}</small></span>
          <Icon class="arrow" icon="solar:download-minimalistic-bold-duotone" />
        </button>
      </div>
      <div class="profile-status" aria-label="游戏资料状态">
        {#each Object.keys(gameLabels) as game}
          <span class="badge" class:success={!!gameProfiles?.[game as GameName]}>
            <i class="dot" class:success={!!gameProfiles?.[game as GameName]}></i>{gameLabels[game as GameName]} · {gameProfiles?.[game as GameName] ? "已有资料" : "未建立"}
          </span>
        {/each}
      </div>
    </section>
  {:else if !error}
    <div class="dashboard-skeleton" aria-hidden="true">
      <div class="row"><span class="skeleton" style="width:64px;height:64px;border-radius:50%"></span><span class="stack" style="gap:8px"><span class="skeleton" style="width:90px;height:14px"></span><span class="skeleton" style="width:200px;height:28px"></span></span></div>
      <div class="stats">{#each [1, 2, 3] as _}<span class="skeleton" style="height:132px;border-radius:16px"></span>{/each}</div>
      <div class="tile-grid">{#each [1, 2, 3, 4] as _}<span class="skeleton" style="height:72px;border-radius:14px"></span>{/each}</div>
    </div>
  {/if}
</main>

<StatusOverlays {error} />

<style lang="sass">
  @use "../vars"

  .dashboard-header
    display: flex
    align-items: center
    justify-content: space-between
    gap: 16px
    flex-wrap: wrap

  .identity
    display: flex
    align-items: center
    gap: 16px
    min-width: 0
    color: var(--text)
    text-decoration: none

    &:hover
      color: var(--text)
      text-decoration: none

    img
      width: 64px
      height: 64px
      flex: 0 0 64px
      border-radius: 50%
      object-fit: cover
      background: var(--surface-2)
      box-shadow: 0 0 0 3px var(--surface), 0 0 0 4px var(--border)

    div
      display: grid
      min-width: 0

    h1
      margin: 0
      overflow: hidden
      text-overflow: ellipsis
      white-space: nowrap

    span
      font-size: 0.88rem

  .header-actions
    display: flex
    gap: 8px

  .stats
    display: grid
    grid-template-columns: repeat(3, minmax(0, 1fr))
    gap: 14px

  .stat
    display: grid
    grid-template-columns: auto 1fr
    align-items: center
    gap: 4px 12px
    padding: 18px
    border: 1px solid var(--border)
    border-radius: var(--radius-lg)
    color: var(--text)
    background: var(--surface)
    box-shadow: var(--shadow-sm)
    text-decoration: none
    transition: vars.$transition

    &:hover
      border-color: var(--border-strong)
      color: var(--text)
      box-shadow: var(--shadow-md)
      text-decoration: none
      transform: translateY(-1px)

    .stat-icon
      grid-row: span 2

    strong
      font-size: 1.6rem
      line-height: 1.2

      small
        color: var(--text-3)
        font-size: 0.9rem
        font-weight: 500

  .stat-label
    align-self: end
    color: var(--text-2)
    font-size: 0.84rem

  .stat-link
    display: inline-flex
    align-items: center
    grid-column: 1 / -1
    margin-top: 10px
    padding-top: 10px
    border-top: 1px solid var(--border)
    color: var(--primary)
    font-size: 0.84rem
    font-weight: 600

  .stat-skeleton
    display: inline-block
    width: 48px
    height: 26px

  .stat-icon
    display: grid
    place-items: center
    width: 42px
    height: 42px
    flex: 0 0 42px
    border-radius: var(--radius)
    font-size: 1.35rem

  .tile-grid
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr))
    gap: 12px

  .profile-status
    display: flex
    flex-wrap: wrap
    gap: 8px

  .dashboard-skeleton
    display: flex
    flex-direction: column
    gap: 24px

  @media (max-width: vars.$w-tablet)
    .stats
      grid-template-columns: 1fr 1fr 1fr
      gap: 10px

    .stat
      grid-template-columns: 1fr
      padding: 14px

      .stat-icon
        grid-row: auto
        width: 36px
        height: 36px
        font-size: 1.15rem

      .stat-link
        display: none

      strong
        font-size: 1.3rem

  @media (max-width: vars.$w-mobile)
    .header-actions
      display: none

    .identity img
      width: 54px
      height: 54px
      flex-basis: 54px

    .tile-grid
      grid-template-columns: 1fr
</style>
