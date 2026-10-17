<script lang="ts">
  import { onMount } from "svelte";
  import Icon from "../components/ui/Icon.svelte";
  import { SOCIAL, USER } from "../libs/sdk";
  import type { Card, MikuNetUser } from "../libs/generalTypes";
  import PageHeader from "../components/ui/PageHeader.svelte";
  import EmptyState from "../components/ui/EmptyState.svelte";
  import Spinner from "../components/ui/Spinner.svelte";
  import { toast } from "../libs/toast";
  import { confirmDialog } from "../libs/confirm";

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
    friendCode: string;
    friends: Player[];
    incomingRequests: Player[];
    outgoingRequests: Player[];
    rivals: Player[];
  };

  let cards: Card[] = [];
  let selectedCardId = "";
  let state: SocialState = { friendCode: "", friends: [], incomingRequests: [], outgoingRequests: [], rivals: [] };
  let searchName = "";
  let searchResult: Player | null = null;
  let busy = true;
  let loaded = false;
  let error = "";
  let tab: "friends" | "incoming" | "outgoing" | "rivals" = "friends";

  const formatFriendCode = (code: string) => code ? "MN-" + (code.match(/.{1,4}/g)?.join("-") ?? code) : "加载中";
  const unwrap = <T,>(value: any): T => value?.data ?? value;
  const playerName = (player: Player) => player.playerName || player.username || `玩家 #${player.id}`;
  const rivalLimit = 4;

  onMount(() => { void initialize(); });

  async function initialize() {
    busy = true;
    loaded = false;
    error = "";
    try {
      const me = await USER.me() as MikuNetUser;
      const accountCard = me.ghostCard?.luid ? { ...me.ghostCard, isGhost: true } : null;
      cards = Array.from(new Map([
        ...(accountCard ? [accountCard] : []),
      ].map(card => [card.luid, card])).values())
        .sort((a, b) => Number(b.isGhost) - Number(a.isGhost));
      selectedCardId = accountCard?.luid ?? cards.find(card => card.isGhost)?.luid ?? cards[0]?.luid ?? "";
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
      friendCode: result.friendCode ?? "",
      friends: result.friends ?? [],
      incomingRequests: result.incomingRequests ?? [],
      outgoingRequests: result.outgoingRequests ?? [],
      rivals: result.rivals ?? [],
    };
    loaded = true;
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
    failed ? toast.error(text) : toast.success(text);
  }

  async function copyFriendCode() {
    try {
      await navigator.clipboard.writeText(formatFriendCode(state.friendCode));
      notify("好友码已复制。");
    } catch {
      notify("复制失败，请手动选择好友码。", true);
    }
  }

  async function changeCard(event: Event) {
    selectedCardId = (event.currentTarget as HTMLSelectElement).value;
    searchResult = null;
    loaded = false;
    busy = true;
    error = "";
    try {
      await reload();
    } catch (e) {
      error = (e as Error).message || String(e);
    } finally {
      busy = false;
    }
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
    if (!await confirmDialog({ title: "移除好友", message: `确定移除好友「${playerName(player)}」吗？`, confirmText: "移除", danger: true })) return;
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

  $: tabs = [
    { id: "friends", label: "好友", icon: "solar:users-group-two-rounded-bold-duotone", count: state.friends.length },
    { id: "incoming", label: "收到的申请", icon: "solar:inbox-in-bold-duotone", count: state.incomingRequests.length },
    { id: "outgoing", label: "已发送", icon: "solar:plain-bold-duotone", count: state.outgoingRequests.length },
    { id: "rivals", label: "劲敌", icon: "solar:cup-star-bold-duotone", count: state.rivals.length },
  ] as const
</script>

<main class="page page-narrow social-page">
  <PageHeader eyebrow="maimai DX" title="好友与劲敌" description="网页申请需要对方接受；游戏内双人确认后会立即互加。">
    {#if cards.length > 1}
      <label class="field card-picker">
        <span class="field-label">游戏卡片</span>
        <select value={selectedCardId} on:change={changeCard} disabled={busy}>
          {#each cards as card (card.luid)}
            <option value={card.luid}>{card.isGhost ? "主账号" : "实体卡"} · {card.luid.slice(-8)}</option>
          {/each}
        </select>
      </label>
    {/if}
  </PageHeader>

  {#if error}
    <div class="alert alert-danger" role="alert">
      <Icon icon="solar:danger-triangle-bold-duotone" />
      <p>{error}</p>
      {#if !busy && !loaded}<button class="btn-sm" type="button" on:click={initialize}>重新读取</button>{/if}
    </div>
  {/if}

  {#if busy && !loaded && !error}
    <div class="stack" aria-hidden="true">
      <span class="skeleton" style="height:150px;border-radius:16px"></span>
      <span class="skeleton" style="height:44px;width:60%"></span>
      {#each [1, 2, 3] as _}<span class="skeleton" style="height:64px;border-radius:12px"></span>{/each}
    </div>
  {/if}

  {#if loaded}
    <section class="card add-panel">
      <div class="friend-code">
        <div>
          <span class="field-label">我的好友码</span>
          <strong class="mono">{formatFriendCode(state.friendCode)}</strong>
        </div>
        <button type="button" disabled={busy || !state.friendCode} on:click={copyFriendCode}><Icon icon="solar:copy-bold-duotone" />复制</button>
      </div>
      <form class="search-form" on:submit|preventDefault={search} role="search">
        <label class="search-input">
          <Icon icon="solar:magnifer-bold-duotone" />
          <span class="visually-hidden">搜索玩家</span>
          <input type="search" bind:value={searchName} maxlength="64" placeholder="输入 MikuNet 用户名或好友码（MN-XXXX-XXXX-XXXX-XXXX）" autocomplete="off" />
        </label>
        <button class="btn-primary" type="submit" disabled={busy || !searchName.trim()}>{#if busy}<Spinner />{/if}搜索</button>
      </form>
      {#if searchResult}
        <article class="player-row search-result">
          <div class="avatar"><Icon icon="solar:user-rounded-bold-duotone" /></div>
          <div class="player-info"><strong class="truncate">{playerName(searchResult)}</strong><span class="truncate">@{searchResult.username || "未知账号"} · Rating {searchResult.rating ?? 0}</span></div>
          <div class="row-actions">
            {#if searchResult.direction === "friend"}
              <span class="badge success"><Icon icon="solar:check-circle-bold-duotone" />已是好友</span>
            {:else if searchResult.direction === "incoming"}
              <button class="btn-primary btn-sm" type="button" disabled={busy} on:click={() => decide(searchResult!, true)}><Icon icon="solar:check-circle-bold-duotone" />接受申请</button>
            {:else if searchResult.direction === "outgoing"}
              <span class="badge warning">等待对方接受</span>
              <button class="btn-ghost btn-sm" type="button" disabled={busy} on:click={() => cancelRequest(searchResult!)}>取消申请</button>
            {:else}
              <button class="btn-primary btn-sm" type="button" disabled={busy} on:click={() => request(searchResult!)}><Icon icon="solar:user-plus-rounded-bold-duotone" />发送申请</button>
            {/if}
            {#if searchResult.direction === "friend" || !searchResult.direction}
              <button class="btn-icon btn-sm rival-toggle" class:active={searchResult.isRival} type="button" disabled={busy}
                      title={searchResult.isRival ? "取消劲敌" : "设为劲敌"} aria-label={searchResult.isRival ? "取消劲敌" : "设为劲敌"} aria-pressed={!!searchResult.isRival}
                      on:click={() => toggleRival(searchResult!)}><Icon icon="solar:cup-star-bold-duotone" /></button>
            {/if}
          </div>
        </article>
      {/if}
    </section>

    <div class="tabs" role="tablist" aria-label="好友分类">
      {#each tabs as item}
        <button type="button" role="tab" aria-selected={tab === item.id} on:click={() => tab = item.id}>
          <Icon icon={item.icon} />{item.label}
          {#if item.id === "rivals"}<span class="count">{item.count}/{rivalLimit}</span>
          {:else}<span class="count" class:attention={item.id === "incoming" && item.count > 0}>{item.count}</span>{/if}
        </button>
      {/each}
    </div>

    <section class="tab-panel" role="tabpanel">
      {#if tab === "friends"}
        {#if state.friends.length}
          <div class="card card-flush player-list">
            {#each state.friends as player (player.id)}
              <article class="player-row">
                <div class="avatar"><Icon icon="solar:user-rounded-bold-duotone" /></div>
                <div class="player-info"><strong class="truncate">{playerName(player)}</strong><span class="truncate">@{player.username || "未知账号"} · Rating {player.rating ?? 0}</span></div>
                <time class="subtle">{fmtDate(player.lastPlayDate)}</time>
                <div class="row-actions">
                  <button class="btn-icon btn-sm rival-toggle" class:active={player.isRival} type="button" disabled={busy}
                          title={player.isRival ? "取消劲敌" : "设为劲敌"} aria-label={player.isRival ? "取消劲敌" : "设为劲敌"} aria-pressed={!!player.isRival}
                          on:click={() => toggleRival(player)}><Icon icon="solar:cup-star-bold-duotone" /></button>
                  <button class="btn-ghost btn-icon btn-sm remove" type="button" disabled={busy} title="移除好友" aria-label="移除 {playerName(player)}"
                          on:click={() => remove(player)}><Icon icon="solar:user-cross-rounded-bold-duotone" /></button>
                </div>
              </article>
            {/each}
          </div>
        {:else}
          <EmptyState icon="solar:users-group-two-rounded-bold-duotone" title="还没有好友" description="游戏内与其他玩家双人确认，或通过上方搜索发送网页好友申请即可添加。" />
        {/if}
      {:else if tab === "incoming"}
        {#if state.incomingRequests.length}
          <div class="card card-flush player-list">
            {#each state.incomingRequests as player (player.id)}
              <article class="player-row">
                <div class="avatar"><Icon icon="solar:user-rounded-bold-duotone" /></div>
                <div class="player-info"><strong class="truncate">{playerName(player)}</strong><span class="truncate">@{player.username || "未知账号"} · Rating {player.rating ?? 0}</span></div>
                <time class="subtle">{fmtDate(player.lastPlayDate)}</time>
                <div class="row-actions">
                  <button class="btn-primary btn-sm" type="button" disabled={busy} on:click={() => decide(player, true)}><Icon icon="solar:check-circle-bold-duotone" />接受</button>
                  <button class="btn-ghost btn-sm" type="button" disabled={busy} aria-label="拒绝 {playerName(player)} 的申请" on:click={() => decide(player, false)}>拒绝</button>
                </div>
              </article>
            {/each}
          </div>
        {:else}
          <EmptyState icon="solar:inbox-in-bold-duotone" title="没有待处理的好友申请" />
        {/if}
      {:else if tab === "outgoing"}
        {#if state.outgoingRequests.length}
          <div class="card card-flush player-list">
            {#each state.outgoingRequests as player (player.id)}
              <article class="player-row">
                <div class="avatar"><Icon icon="solar:user-rounded-bold-duotone" /></div>
                <div class="player-info"><strong class="truncate">{playerName(player)}</strong><span class="truncate">@{player.username || "未知账号"} · Rating {player.rating ?? 0}</span></div>
                <div class="row-actions">
                  <span class="badge warning">等待对方接受</span>
                  <button class="btn-ghost btn-sm" type="button" disabled={busy} aria-label="取消对 {playerName(player)} 的申请" on:click={() => cancelRequest(player)}>取消</button>
                </div>
              </article>
            {/each}
          </div>
        {:else}
          <EmptyState icon="solar:plain-bold-duotone" title="没有等待处理的已发送申请" />
        {/if}
      {:else}
        {#if state.rivals.length}
          <div class="card card-flush player-list">
            {#each state.rivals as player (player.id)}
              <article class="player-row">
                <div class="avatar rival"><Icon icon="solar:cup-star-bold-duotone" /></div>
                <div class="player-info"><strong class="truncate">{playerName(player)}</strong><span class="truncate">@{player.username || "未知账号"} · Rating {player.rating ?? 0}</span></div>
                <div class="row-actions">
                  <button class="btn-ghost btn-sm" type="button" disabled={busy} aria-label="取消 {playerName(player)} 的劲敌" on:click={() => toggleRival(player, true)}>取消劲敌</button>
                </div>
              </article>
            {/each}
          </div>
        {:else}
          <EmptyState icon="solar:cup-star-bold-duotone" title="尚未设置劲敌" description="可从搜索结果或好友列表中点击奖杯图标添加，最多 {rivalLimit} 名。" />
        {/if}
      {/if}
    </section>
  {/if}
</main>

<style lang="sass">
  @use "../vars"

  .card-picker
    min-width: 220px

  .add-panel
    gap: 16px

  .friend-code
    display: flex
    align-items: center
    justify-content: space-between
    gap: 12px
    padding-bottom: 16px
    border-bottom: 1px solid var(--border)

    div
      display: grid
      gap: 2px

    strong
      color: var(--primary)
      font-size: 1.15rem
      letter-spacing: 0.04em

  .search-form
    display: flex
    gap: 8px

  .search-input
    position: relative
    flex: 1
    min-width: 0

    > :global(svg)
      position: absolute
      top: 50%
      left: 12px
      z-index: 1
      pointer-events: none
      color: var(--text-3)
      transform: translateY(-50%)

    input
      padding-left: 38px

  .search-result
    border: 1px solid var(--border)
    border-radius: var(--radius)
    background: var(--surface-2)

  .tabs .count.attention
    color: #fff
    background: var(--danger)

  .player-list
    display: grid

  .player-row
    display: flex
    align-items: center
    gap: 12px
    min-height: 64px
    padding: 10px 16px
    border-bottom: 1px solid var(--border)

    &:last-child
      border-bottom: 0

  .avatar
    display: grid
    place-items: center
    width: 40px
    height: 40px
    flex: 0 0 40px
    border-radius: 50%
    color: var(--primary)
    background: var(--primary-soft)
    font-size: 1.25rem

    &.rival
      color: var(--warning)
      background: var(--warning-soft)

  .player-info
    display: grid
    flex: 1
    min-width: 0

    span
      color: var(--text-2)
      font-size: 0.82rem

  time
    font-size: 0.8rem
    white-space: nowrap

  .row-actions
    display: flex
    align-items: center
    gap: 6px
    flex: 0 0 auto

  .rival-toggle
    color: var(--warning)

    &.active
      border-color: var(--warning)
      color: #fff
      background: var(--warning)

      &:hover:not(:disabled)
        color: #fff
        background: color-mix(in srgb, var(--warning) 85%, #000)

  .remove:hover:not(:disabled)
    color: var(--danger)
    background: var(--danger-soft)

  @media (max-width: vars.$w-mobile)
    .card-picker
      width: 100%

    .player-row
      flex-wrap: wrap

      time
        display: none

    .row-actions
      width: 100%
      justify-content: flex-end
      padding-left: 52px

    .friend-code
      flex-wrap: wrap
</style>
