<script lang="ts">
  import { onMount } from "svelte";
  import Icon from "@iconify/svelte";
  import { SOCIAL, USER } from "../libs/sdk";
  import type { Card, MikuNetUser } from "../libs/generalTypes";

  type Player = {
    id: number;
    username?: string;
    playerName?: string;
    rating?: number;
    lastPlayDate?: string;
    status?: string | null;
    direction?: "friend" | "incoming" | "outgoing";
    isRival?: boolean;
  };
  type SocialState = {
    friends: Player[];
    incomingRequests: Player[];
    outgoingRequests: Player[];
    rivals: Player[];
  };

  let cards: Card[] = [];
  let selectedCardId = "";
  let state: SocialState = { friends: [], incomingRequests: [], outgoingRequests: [], rivals: [] };
  let searchName = "";
  let searchResult: Player | null = null;
  let busy = false;
  let error = "";
  let message = "";
  let noticeTimer: ReturnType<typeof setTimeout>;

  const unwrap = <T,>(value: any): T => value?.data ?? value;
  const playerName = (player: Player) => player.playerName || player.username || `玩家 #${player.id}`;
  const rivalLimit = 4;

  onMount(() => { void initialize(); });

  async function initialize() {
    busy = true;
    error = "";
    try {
      const me = await USER.me() as MikuNetUser;
      cards = [...(me.cards ?? [])].sort((a, b) => Number(b.isGhost) - Number(a.isGhost));
      selectedCardId = cards.find(card => card.isGhost)?.luid ?? cards[0]?.luid ?? "";
      if (!selectedCardId) throw new Error("账号没有可用的舞萌卡片，请先绑定卡片。");
      await reload();
    } catch (e) {
      error = (e as Error).message || String(e);
    } finally {
      busy = false;
    }
  }

  async function reload() {
    if (!selectedCardId) return;
    const result = unwrap<SocialState>(await SOCIAL.state(selectedCardId));
    state = {
      friends: result.friends ?? [],
      incomingRequests: result.incomingRequests ?? [],
      outgoingRequests: result.outgoingRequests ?? [],
      rivals: result.rivals ?? [],
    };
    if (searchResult) {
      searchResult = state.friends.find(player => player.id === searchResult?.id)
        ? { ...searchResult, direction: "friend", status: "ACCEPTED", isRival: state.rivals.some(player => player.id === searchResult?.id) }
        : state.incomingRequests.find(player => player.id === searchResult?.id)
          ? { ...searchResult, direction: "incoming", status: "PENDING" }
          : state.outgoingRequests.find(player => player.id === searchResult?.id)
            ? { ...searchResult, direction: "outgoing", status: "PENDING" }
            : { ...searchResult, direction: undefined, status: null };
    }
  }

  function notify(text: string, failed = false) {
    message = `${failed ? "! " : "✓ "}${text}`;
    clearTimeout(noticeTimer);
    noticeTimer = setTimeout(() => message = "", 3200);
  }

  async function changeCard(event: Event) {
    selectedCardId = (event.currentTarget as HTMLSelectElement).value;
    searchResult = null;
    await run(reload, "已切换游戏卡片。");
  }

  async function run(action: () => Promise<unknown>, success: string) {
    busy = true;
    error = "";
    try {
      unwrap(await action());
      notify(success);
      await reload();
    } catch (e) {
      notify((e as Error).message || String(e), true);
    } finally {
      busy = false;
    }
  }

  async function search() {
    if (!searchName.trim()) return;
    busy = true;
    error = "";
    try {
      searchResult = unwrap<Player>(await SOCIAL.search(selectedCardId, searchName.trim()));
      const friend = state.friends.find(player => player.id === searchResult?.id);
      const incoming = state.incomingRequests.find(player => player.id === searchResult?.id);
      const outgoing = state.outgoingRequests.find(player => player.id === searchResult?.id);
      if (friend) searchResult = { ...searchResult, direction: "friend", status: "ACCEPTED", isRival: friend.isRival };
      else if (incoming) searchResult = { ...searchResult, direction: "incoming", status: "PENDING" };
      else if (outgoing) searchResult = { ...searchResult, direction: "outgoing", status: "PENDING" };
    } catch (e) {
      searchResult = null;
      notify((e as Error).message || String(e), true);
    } finally {
      busy = false;
    }
  }

  async function request(player: Player) {
    await run(() => SOCIAL.request(selectedCardId, player.username || searchName.trim()), "好友关系已更新。");
  }

  async function decide(player: Player, accept: boolean) {
    await run(() => accept
      ? SOCIAL.accept(selectedCardId, player.id)
      : SOCIAL.reject(selectedCardId, player.id), accept ? "已接受好友申请。" : "已拒绝好友申请。");
  }

  async function remove(player: Player) {
    if (!window.confirm(`确定移除好友「${playerName(player)}」吗？`)) return;
    await run(() => SOCIAL.remove(selectedCardId, player.id), "好友已移除。");
  }

  async function cancelRequest(player: Player) {
    await run(() => SOCIAL.remove(selectedCardId, player.id), "已取消好友申请。");
  }

  async function toggleRival(player: Player, currentlyRival = player.isRival ?? state.rivals.some(item => item.id === player.id)) {
    if (!currentlyRival && state.rivals.length >= rivalLimit) {
      notify("游戏最多只能设置 4 名劲敌。", true);
      return;
    }
    await run(() => SOCIAL.rival(selectedCardId, player.id, !currentlyRival), currentlyRival ? "已取消劲敌。" : "已设为劲敌。");
  }

  function fmtDate(value?: string) { return value?.replace("T", " ").slice(0, 16) || "—"; }
</script>

<svelte:head>
  <title>MikuNet 好友与劲敌</title>
  <meta name="description" content="管理舞萌好友申请和游戏劲敌" />
</svelte:head>

<main class="content social-page">
  <header class="page-head">
    <div>
      <span class="eyebrow">MAIMAI DX SOCIAL</span>
      <h1>好友与劲敌</h1>
      <p>网页申请需要对方接受；游戏内双人确认后会立即互加。</p>
    </div>
    {#if cards.length}
      <label class="card-picker">
        <span>游戏卡片</span>
        <select value={selectedCardId} on:change={changeCard} disabled={busy}>
          {#each cards as card (card.luid)}
            <option value={card.luid}>{card.isGhost ? "主账号" : "实体卡"} · {card.luid.slice(-8)}</option>
          {/each}
        </select>
      </label>
    {/if}
  </header>

  {#if message}<div class="toast" role="status">{message}</div>{/if}
  {#if error}<div class="notice error"><Icon icon="solar:danger-triangle-bold-duotone" /><span>{error}</span></div>{/if}
  {#if busy && !state}<div class="notice">正在读取好友资料…</div>{/if}

  <section class="search-section">
    <div class="section-heading"><div><span class="eyebrow">FIND PLAYER</span><h2>添加好友</h2></div></div>
    <form class="search-form" on:submit|preventDefault={search}>
      <label class="search-input"><Icon icon="solar:magnifer-bold-duotone" /><input bind:value={searchName} maxlength="64" placeholder="输入 MikuNet 用户名" autocomplete="off" /></label>
      <button class="primary-button" disabled={busy || !searchName.trim()}><Icon icon="solar:magnifer-bold-duotone" />搜索</button>
    </form>
    {#if searchResult}
      <article class="player-row search-result">
        <div class="player-icon"><Icon icon="solar:user-rounded-bold-duotone" /></div>
        <div class="player-info"><strong>{playerName(searchResult)}</strong><span>@{searchResult.username || "未知账号"} · Rating {searchResult.rating ?? 0}</span></div>
        <div class="row-actions">
          {#if searchResult.direction === "friend"}
            <span class="status accepted"><Icon icon="solar:check-circle-bold-duotone" />已是好友</span>
          {:else if searchResult.direction === "incoming"}
            <button class="primary-button" disabled={busy} on:click={() => decide(searchResult!, true)}><Icon icon="solar:check-circle-bold-duotone" />接受申请</button>
          {:else if searchResult.direction === "outgoing"}
            <span class="status pending">等待对方接受</span><button class="icon-button danger-icon" title="取消申请" aria-label="取消好友申请" disabled={busy} on:click={() => cancelRequest(searchResult!)}><Icon icon="solar:close-circle-bold-duotone" /></button>
          {:else}
            <button class="primary-button" disabled={busy} on:click={() => request(searchResult!)}><Icon icon="solar:user-plus-rounded-bold-duotone" />发送申请</button>
          {/if}
          {#if searchResult.direction === "friend" || !searchResult.direction}
            <button class:active={searchResult.isRival} class="icon-button rival-toggle" title={searchResult.isRival ? "取消劲敌" : "设为劲敌"} aria-label={searchResult.isRival ? "取消劲敌" : "设为劲敌"} disabled={busy} on:click={() => toggleRival(searchResult!)}><Icon icon="solar:cup-star-bold-duotone" /></button>
          {/if}
        </div>
      </article>
    {/if}
  </section>

  <section class="social-section">
    <div class="section-heading"><div><span class="eyebrow">OUTGOING</span><h2>已发送申请</h2></div><span class="total">{state.outgoingRequests.length} 项</span></div>
    {#if state.outgoingRequests.length}
      <div class="player-list">
        {#each state.outgoingRequests as player (player.id)}
          <article class="player-row"><div class="player-icon"><Icon icon="solar:user-rounded-bold-duotone" /></div><div class="player-info"><strong>{playerName(player)}</strong><span>@{player.username || "未知账号"} · Rating {player.rating ?? 0}</span></div><div class="row-actions"><span class="status pending">等待对方接受</span><button class="icon-button danger-icon" title="取消申请" aria-label="取消对 {playerName(player)} 的申请" disabled={busy} on:click={() => cancelRequest(player)}><Icon icon="solar:close-circle-bold-duotone" /></button></div></article>
        {/each}
      </div>
    {:else}<div class="notice">没有等待处理的已发送申请。</div>{/if}
  </section>

  <section class="social-section">
    <div class="section-heading"><div><span class="eyebrow">INCOMING</span><h2>好友申请</h2></div><span class="total">{state.incomingRequests.length} 项</span></div>
    {#if state.incomingRequests.length}
      <div class="player-list">
        {#each state.incomingRequests as player (player.id)}
          <article class="player-row"><div class="player-icon"><Icon icon="solar:user-rounded-bold-duotone" /></div><div class="player-info"><strong>{playerName(player)}</strong><span>@{player.username || "未知账号"} · Rating {player.rating ?? 0}</span></div><time>{fmtDate(player.lastPlayDate)}</time><div class="row-actions"><button class="primary-button" disabled={busy} on:click={() => decide(player, true)}><Icon icon="solar:check-circle-bold-duotone" />接受</button><button class="icon-button danger-icon" title="拒绝申请" aria-label="拒绝 {playerName(player)} 的申请" disabled={busy} on:click={() => decide(player, false)}><Icon icon="solar:close-circle-bold-duotone" /></button></div></article>
        {/each}
      </div>
    {:else}<div class="notice">没有待处理的好友申请。</div>{/if}
  </section>

  <section class="social-section">
    <div class="section-heading"><div><span class="eyebrow">FRIENDS</span><h2>好友</h2></div><span class="total">{state.friends.length} 人</span></div>
    {#if state.friends.length}
      <div class="player-list">
        {#each state.friends as player (player.id)}
          <article class="player-row"><div class="player-icon"><Icon icon="solar:user-rounded-bold-duotone" /></div><div class="player-info"><strong>{playerName(player)}</strong><span>@{player.username || "未知账号"} · Rating {player.rating ?? 0}</span></div><time>{fmtDate(player.lastPlayDate)}</time><div class="row-actions"><button class:active={player.isRival} class="icon-button rival-toggle" title={player.isRival ? "取消劲敌" : "设为劲敌"} aria-label={player.isRival ? "取消劲敌" : "设为劲敌"} disabled={busy} on:click={() => toggleRival(player)}><Icon icon="solar:cup-star-bold-duotone" /></button><button class="icon-button danger-icon" title="移除好友" aria-label="移除 {playerName(player)}" disabled={busy} on:click={() => remove(player)}><Icon icon="solar:user-cross-rounded-bold-duotone" /></button></div></article>
        {/each}
      </div>
    {:else}<div class="notice">还没有好友。游戏内与其他玩家双人确认，或发送网页好友申请即可添加。</div>{/if}
  </section>

  <section class="social-section rivals-section">
    <div class="section-heading"><div><span class="eyebrow">RIVALS</span><h2>劲敌</h2></div><span class="total">{state.rivals.length} / {rivalLimit}</span></div>
    {#if state.rivals.length}
      <div class="player-list">
        {#each state.rivals as player (player.id)}
          <article class="player-row"><div class="player-icon rival-icon"><Icon icon="solar:cup-star-bold-duotone" /></div><div class="player-info"><strong>{playerName(player)}</strong><span>@{player.username || "未知账号"} · Rating {player.rating ?? 0}</span></div><button class="icon-button danger-icon" title="取消劲敌" aria-label="取消 {playerName(player)} 的劲敌" disabled={busy} on:click={() => toggleRival(player, true)}><Icon icon="solar:close-circle-bold-duotone" /></button></article>
        {/each}
      </div>
    {:else}<div class="notice">尚未设置劲敌。可从搜索结果或好友列表中添加。</div>{/if}
  </section>
</main>

<style lang="sass">
  @use "../vars" as v

  .social-page
    max-width: 1180px
    margin: 0 auto
    padding-bottom: 56px
    color: v.$c-text

  .page-head, .section-heading, .player-row, .search-form, .row-actions
    display: flex
    align-items: center
    justify-content: space-between
    gap: 16px

  .page-head
    margin: 26px 0 22px
    h1
      margin: 4px 0
      font-size: 2rem
    p
      margin: 0
      color: v.$c-sub

  .eyebrow
    color: v.$c-main
    font-size: .7rem
    font-weight: 800

  .card-picker
    display: grid
    gap: 5px
    min-width: 220px
    color: v.$c-sub
    font-size: .8rem
    select
      width: 100%
      border: 1px solid #c8d8d8
      border-radius: 6px
      padding: 10px 12px
      color: v.$c-text
      background: #fff
      font: inherit

  .search-section, .social-section
    margin-top: 24px

  .section-heading
    margin-bottom: 12px
    h2
      margin: 3px 0
      font-size: 1.2rem

  .total
    color: v.$c-muted
    font-size: .86rem

  .search-form
    justify-content: flex-start
    max-width: 680px
    margin-bottom: 12px

  .search-input
    display: flex
    align-items: center
    gap: 10px
    flex: 1
    min-width: 0
    border: 1px solid #c8d8d8
    border-radius: 6px
    background: white
    padding: 0 12px
    color: v.$c-muted
    input
      width: 100%
      min-width: 0
      height: 42px
      border: 0
      outline: none
      color: v.$c-text
      background: transparent
      font: inherit

  .player-list
    border-top: 1px solid #d5e3e2

  .player-row
    min-height: 68px
    justify-content: flex-start
    border-bottom: 1px solid #d5e3e2
    padding: 9px 4px

  .search-result
    border: 1px solid #cce4e1
    border-radius: 6px
    background: rgba(255, 255, 255, .76)
    padding: 10px 12px

  .player-icon
    display: grid
    place-items: center
    flex: 0 0 42px
    width: 42px
    aspect-ratio: 1
    border-radius: 6px
    color: v.$c-main
    background: v.$c-main-soft
    font-size: 1.35rem

  .rival-icon
    color: #a87813
    background: #fff1cb

  .player-info
    display: grid
    flex: 1
    min-width: 0
    gap: 4px
    strong, span
      overflow: hidden
      text-overflow: ellipsis
      white-space: nowrap
    span
      color: v.$c-muted
      font-size: .82rem

  time
    color: v.$c-muted
    font-size: .78rem
    white-space: nowrap

  .row-actions
    justify-content: flex-end

  .primary-button, .icon-button
    display: inline-flex
    align-items: center
    justify-content: center
    gap: 7px
    border: 1px solid transparent
    border-radius: 6px
    cursor: pointer
    font: inherit
    transition: background .16s ease, color .16s ease, border-color .16s ease
    &:disabled
      cursor: not-allowed
      opacity: .55

  .primary-button
    min-height: 40px
    padding: 0 14px
    color: white
    background: v.$c-main
    &:hover:not(:disabled)
      background: v.$c-darker

  .icon-button
    width: 38px
    height: 38px
    color: v.$c-sub
    background: rgba(255, 255, 255, .72)
    border-color: #d5e3e2
    font-size: 1.15rem
    &:hover:not(:disabled)
      color: v.$c-main
      border-color: v.$c-main

  .danger-icon
    color: v.$c-error
    &:hover:not(:disabled)
      color: white
      background: v.$c-error
      border-color: v.$c-error

  .rival-toggle
    color: #a87813
    &.active
      color: #fff
      background: #c39029
      border-color: #c39029

  .status
    display: inline-flex
    align-items: center
    gap: 6px
    color: v.$c-muted
    font-size: .85rem
    white-space: nowrap
    &.accepted
      color: v.$c-good
    &.pending
      color: v.$c-warning

  .notice, .toast
    display: flex
    align-items: center
    gap: 9px
    border-radius: 6px
    padding: 13px 15px
    color: v.$c-sub
    background: rgba(255, 255, 255, .62)
  .notice.error
    color: v.$c-error
    background: #fff1f2
  .toast
    margin: 10px 0
    color: v.$c-main

  @media (max-width: 640px)
    .page-head
      align-items: flex-start
      flex-direction: column
    .card-picker
      width: 100%
    .player-row
      flex-wrap: wrap
      column-gap: 10px
    .player-info
      flex-basis: calc(100% - 60px)
    .player-row time
      display: none
    .row-actions
      width: 100%
      justify-content: flex-end
      margin-left: 52px
    .search-form
      align-items: stretch
    .search-form .primary-button
      flex: 0 0 auto
</style>
