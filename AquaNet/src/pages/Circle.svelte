<script lang="ts">
  import { onMount } from "svelte";
  import Icon from "@iconify/svelte";
  import { CIRCLE, USER } from "../libs/sdk";
  import type { MikuNetUser, Card } from "../libs/generalTypes";

  type CircleItem = {
    circleId: number;
    circleName: string;
    circleCode: string;
    circleClass: number;
    isPlace: boolean;
    placeId: number;
    isPublic: boolean;
    isAllowAnyoneJoin: boolean;
    comment: string;
    memberCount?: number;
  };
  type Member = {
    userCode: string;
    isOwner?: boolean;
    userProfile: { userName: string; playerRating: number; lastPlayDate?: string };
    userCircleData?: { lastLoginDate?: string } | null;
    userCirclePointData?: { point?: number } | null;
    userCircleChallenge?: { achievement?: number } | null;
  };
  type JoinRequest = { userCode: string; requestTime: string; userProfile: Member["userProfile"] };
  type PageResult<T> = { content?: T[]; totalElements?: number };
  type CircleDraft = Pick<CircleItem, "circleName" | "comment" | "isPublic" | "isAllowAnyoneJoin">;

  let me: MikuNetUser | null = null;
  let cards: Card[] = [];
  let selectedCardId = "";
  let circleInfo: any = null;
  let publicCircles: CircleItem[] = [];
  let members: Member[] = [];
  let requests: JoinRequest[] = [];
  let publicPage = 0;
  let memberPage = 0;
  let requestPage = 0;
  let publicTotal = 0;
  let memberTotal = 0;
  let requestTotal = 0;
  let tab: "discover" | "manage" = "discover";
  let busy = false;
  let error = "";
  let message = "";
  let editor: CircleDraft | null = null;
  let editing = false;
  let circleCode = "";
  let noticeTimer: ReturnType<typeof setTimeout>;
  let currentCircle: CircleItem | null | undefined;
  let isOwner = false;

  const unwrap = <T,>(value: any): T => value?.data ?? value;
  $: currentCircle = circleInfo?.joinedCircle as CircleItem | null | undefined;
  $: isOwner = !!circleInfo?.isCircleOwner;

  onMount(() => { void initialize(); });

  async function initialize() {
    busy = true;
    error = "";
    try {
      me = await USER.me();
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
    busy = true;
    error = "";
    try {
      const [infoResult, listResult] = await Promise.all([
        CIRCLE.info(selectedCardId), CIRCLE.list(selectedCardId, publicPage),
      ]);
      const info = unwrap(infoResult);
      circleInfo = info;
      const publicData = unwrap<PageResult<CircleItem>>(listResult);
      publicCircles = publicData.content ?? [];
      publicTotal = publicData.totalElements ?? 0;
      if (info?.isCircleOwner && info?.joinedCircle) {
        const [memberResult, requestResult] = await Promise.all([
          CIRCLE.members(selectedCardId, memberPage),
          CIRCLE.requests(selectedCardId, requestPage),
        ]);
        const memberData = unwrap<PageResult<Member>>(memberResult);
        const requestData = unwrap<PageResult<JoinRequest>>(requestResult);
        members = memberData.content ?? [];
        memberTotal = memberData.totalElements ?? 0;
        requests = requestData.content ?? [];
        requestTotal = requestData.totalElements ?? 0;
      } else {
        members = [];
        requests = [];
        memberTotal = 0;
        requestTotal = 0;
      }
    } catch (e) {
      error = (e as Error).message || String(e);
    } finally {
      busy = false;
    }
  }

  function notify(text: string, failed = false) {
    message = `${failed ? "! " : "✓ "}${text}`;
    clearTimeout(noticeTimer);
    noticeTimer = setTimeout(() => message = "", 3200);
  }

  async function changeCard(event: Event) {
    selectedCardId = (event.currentTarget as HTMLSelectElement).value;
    publicPage = memberPage = requestPage = 0;
    circleInfo = null;
    await reload();
  }

  async function mutate(action: () => Promise<any>, success: string): Promise<boolean> {
    busy = true;
    try {
      unwrap(await action());
      notify(success);
      await reload();
      return true;
    } catch (e) {
      notify((e as Error).message || String(e), true);
      return false;
    } finally {
      busy = false;
    }
  }

  function openCreate() {
    editing = false;
    editor = { circleName: "", comment: "", isPublic: true, isAllowAnyoneJoin: false };
  }

  function openEdit() {
    if (!currentCircle) return;
    editing = true;
    editor = {
      circleName: currentCircle.circleName,
      comment: currentCircle.comment ?? "",
      isPublic: currentCircle.isPublic,
      isAllowAnyoneJoin: currentCircle.isAllowAnyoneJoin,
    };
  }

  async function saveCircle() {
    if (!editor || !editor.circleName.trim()) {
      notify("请填写圈子名称。", true);
      return;
    }
    const operation = editing ? CIRCLE.update(selectedCardId, editor) : CIRCLE.create(selectedCardId, editor);
    if (await mutate(() => operation, editing ? "圈子资料已更新。" : "圈子已创建。")) editor = null;
  }

  async function join(circle: CircleItem) {
    await mutate(() => CIRCLE.join(selectedCardId, circle.circleId), circle.isAllowAnyoneJoin ? "已加入圈子。" : "加入申请已送出。");
  }

  async function joinWithCode() {
    if (!circleCode.trim()) return;
    await mutate(() => CIRCLE.joinByCode(selectedCardId, circleCode.trim()), "已加入圈子或提交加入申请。");
    circleCode = "";
  }

  async function leave() {
    if (!currentCircle || !window.confirm(`确定退出「${currentCircle.circleName}」吗？`)) return;
    await mutate(() => CIRCLE.leave(selectedCardId), "已退出圈子。");
  }

  async function dissolve() {
    if (!currentCircle || !window.confirm(`解散「${currentCircle.circleName}」后成员和申请记录将一并移除，确定继续吗？`)) return;
    await mutate(() => CIRCLE.dissolve(selectedCardId), "圈子已解散。");
  }

  async function kick(member: Member) {
    if (!window.confirm(`将 ${member.userProfile.userName} 移出圈子？`)) return;
    await mutate(() => CIRCLE.kick(selectedCardId, member.userCode), "成员已移除。");
  }

  async function decide(request: JoinRequest, approve: boolean) {
    await mutate(() => approve
      ? CIRCLE.approve(selectedCardId, request.userCode)
      : CIRCLE.reject(selectedCardId, request.userCode), approve ? "已批准加入申请。" : "已拒绝加入申请。");
  }

  async function copyCode() {
    if (!currentCircle?.circleCode) return;
    try {
      await navigator.clipboard.writeText(currentCircle.circleCode);
      notify("圈子代码已复制。");
    } catch {
      notify("复制失败，请手动选择代码。", true);
    }
  }

  function setPage(which: "public" | "member" | "request", page: number) {
    if (which === "public") publicPage = page;
    if (which === "member") memberPage = page;
    if (which === "request") requestPage = page;
    void reload();
  }

  function pages(total: number) { return Math.max(1, Math.ceil(total / 10)); }
  function fmtDate(value?: string) { return value?.replace("T", " ").slice(0, 16) || "—"; }
</script>

<svelte:head>
  <title>MikuNet 圈子</title>
  <meta name="description" content="管理舞萌圈子、成员与加入申请" />
</svelte:head>

<main class="content circle-page">
  <header class="page-head">
    <div>
      <span class="eyebrow">MAIMAI DX COMMUNITY</span>
      <h1>圈子</h1>
      <p>创建圈子、申请加入，和同好一起游玩。</p>
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
  {#if busy && !circleInfo}<div class="notice">正在读取圈子资料…</div>{/if}

  {#if circleInfo}
    {#if currentCircle}
      <section class="joined-strip">
        <div class="joined-copy">
          <span class="eyebrow">当前圈子</span>
          <h2>{currentCircle.circleName}</h2>
          <p>{currentCircle.comment || "这个圈子还没有简介。"}</p>
        </div>
        <div class="joined-stats">
          <div><span>成员</span><strong>{memberTotal || currentCircle.memberCount || 1}</strong></div>
          <div><span>我的点数</span><strong>{circleInfo.userCirclePointData?.point ?? 0}<small> pt</small></strong></div>
          <div><span>上月排名</span><strong>#{circleInfo.userCirclePointRankingResult?.lastMonthCircleRank || "—"}</strong></div>
        </div>
        <div class="joined-actions">
          {#if currentCircle.circleCode}<code>{currentCircle.circleCode}</code><button class="icon-button" title="复制圈子代码" aria-label="复制圈子代码" on:click={copyCode}><Icon icon="solar:copy-bold-duotone" /></button>{/if}
          {#if isOwner}<button class="secondary-button" on:click={openEdit}><Icon icon="solar:pen-bold-duotone" />编辑圈子</button><button class="danger-button" on:click={dissolve}><Icon icon="solar:trash-bin-trash-bold-duotone" />解散</button>
          {:else}<button class="danger-button" on:click={leave}><Icon icon="solar:logout-2-bold-duotone" />退出圈子</button>{/if}
        </div>
      </section>
    {:else}
      <section class="empty-state">
        <div class="empty-icon"><Icon icon="solar:users-group-rounded-bold-duotone" /></div>
        <div><h2>还没有加入圈子</h2><p>浏览公开圈子，或使用圈子代码申请加入。</p></div>
        <button class="primary-button" on:click={openCreate}><Icon icon="solar:add-circle-bold-duotone" />创建圈子</button>
      </section>
    {/if}

    <div class="tabs" role="tablist" aria-label="圈子页面">
      <button class:active={tab === "discover"} role="tab" aria-selected={tab === "discover"} on:click={() => tab = "discover"}><Icon icon="solar:compass-bold-duotone" />发现圈子</button>
      {#if isOwner}<button class:active={tab === "manage"} role="tab" aria-selected={tab === "manage"} on:click={() => tab = "manage"}><Icon icon="solar:users-group-rounded-bold-duotone" />成员管理 <span class="count">{requestTotal}</span></button>{/if}
    </div>

    {#if tab === "discover"}
      <section class="discover-tools">
        <label class="code-join"><span>使用圈子代码</span><input bind:value={circleCode} maxlength="16" placeholder="输入代码" on:keydown={(e) => e.key === "Enter" && joinWithCode()} /><button class="primary-button" disabled={!circleCode.trim() || busy} on:click={joinWithCode}>申请加入</button></label>
        {#if !currentCircle}<button class="secondary-button create-button" on:click={openCreate}><Icon icon="solar:add-circle-bold-duotone" />创建圈子</button>{/if}
      </section>
      <section class="circle-list" aria-label="公开圈子">
        <div class="section-heading"><div><span class="eyebrow">COMMUNITY</span><h2>公开圈子</h2></div><span class="total">{publicTotal} 个</span></div>
        {#if publicCircles.length}
          <div class="circle-grid">
            {#each publicCircles as circle (circle.circleId)}
              <article class="circle-card">
                <div class="circle-card-head"><div class="circle-mark"><Icon icon="solar:users-group-rounded-bold-duotone" /></div><span class="member-count">{circle.memberCount ?? 0} 人</span></div>
                <h3>{circle.circleName}</h3>
                <p>{circle.comment || "暂无圈子简介。"}</p>
                <div class="circle-meta"><span>{circle.isAllowAnyoneJoin ? "加入即通过" : "需要团长审批"}</span><span>圈子 #{circle.circleId}</span></div>
                {#if !currentCircle}<button class="primary-button join-button" disabled={busy} on:click={() => join(circle)}><Icon icon="solar:user-plus-rounded-bold-duotone" />{circle.isAllowAnyoneJoin ? "加入圈子" : "申请加入"}</button>{/if}
              </article>
            {/each}
          </div>
          {#if pages(publicTotal) > 1}<div class="pager"><button class="icon-button" aria-label="上一页" disabled={publicPage <= 0 || busy} on:click={() => setPage("public", publicPage - 1)}><Icon icon="solar:alt-arrow-left-bold" /></button><span>{publicPage + 1} / {pages(publicTotal)}</span><button class="icon-button" aria-label="下一页" disabled={publicPage + 1 >= pages(publicTotal) || busy} on:click={() => setPage("public", publicPage + 1)}><Icon icon="solar:alt-arrow-right-bold" /></button></div>{/if}
        {:else}<div class="notice">暂时没有公开圈子，创建一个来聚集同好吧。</div>{/if}
      </section>
    {:else if tab === "manage" && isOwner}
      <section class="manage-section">
        <div class="section-heading"><div><span class="eyebrow">CIRCLE ADMIN</span><h2>圈子成员</h2></div><span class="total">{memberTotal} 人</span></div>
        <div class="table-wrap"><table><thead><tr><th>玩家</th><th>Rating</th><th>点数</th><th>上次游玩</th><th>操作</th></tr></thead><tbody>
          {#each members as member (member.userCode)}<tr><td><span class="player-name">{member.userProfile.userName}</span>{#if member.isOwner}<span class="owner-label">团长</span>{/if}</td><td>{member.userProfile.playerRating}</td><td>{member.userCirclePointData?.point ?? 0} pt</td><td>{fmtDate(member.userProfile.lastPlayDate)}</td><td>{#if !member.isOwner}<button class="icon-button danger-icon" title="移出圈子" aria-label="移出 {member.userProfile.userName}" on:click={() => kick(member)}><Icon icon="solar:user-cross-rounded-bold-duotone" /></button>{:else}<span class="muted">—</span>{/if}</td></tr>{/each}
          {#if !members.length}<tr><td colspan="5" class="empty-cell">暂无成员数据</td></tr>{/if}
        </tbody></table></div>
        {#if pages(memberTotal) > 1}<div class="pager"><button class="icon-button" aria-label="上一页" disabled={memberPage <= 0 || busy} on:click={() => setPage("member", memberPage - 1)}><Icon icon="solar:alt-arrow-left-bold" /></button><span>{memberPage + 1} / {pages(memberTotal)}</span><button class="icon-button" aria-label="下一页" disabled={memberPage + 1 >= pages(memberTotal) || busy} on:click={() => setPage("member", memberPage + 1)}><Icon icon="solar:alt-arrow-right-bold" /></button></div>{/if}
      </section>
      <section class="manage-section requests-section">
        <div class="section-heading"><div><span class="eyebrow">PENDING</span><h2>加入申请</h2></div><span class="total">{requestTotal} 项</span></div>
        {#if requests.length}<div class="request-list">{#each requests as request (request.userCode)}<article class="request-row"><div class="request-avatar"><Icon icon="solar:user-rounded-bold-duotone" /></div><div class="request-profile"><strong>{request.userProfile.userName}</strong><span>Rating {request.userProfile.playerRating} · 最近游玩 {fmtDate(request.userProfile.lastPlayDate)}</span></div><time>{fmtDate(request.requestTime)}</time><div class="request-actions"><button class="icon-button approve-icon" title="批准" aria-label="批准申请" on:click={() => decide(request, true)}><Icon icon="solar:check-circle-bold-duotone" /></button><button class="icon-button danger-icon" title="拒绝" aria-label="拒绝申请" on:click={() => decide(request, false)}><Icon icon="solar:close-circle-bold-duotone" /></button></div></article>{/each}</div>
        {:else}<div class="notice">当前没有待处理申请。</div>{/if}
        {#if pages(requestTotal) > 1}<div class="pager"><button class="icon-button" aria-label="上一页" disabled={requestPage <= 0 || busy} on:click={() => setPage("request", requestPage - 1)}><Icon icon="solar:alt-arrow-left-bold" /></button><span>{requestPage + 1} / {pages(requestTotal)}</span><button class="icon-button" aria-label="下一页" disabled={requestPage + 1 >= pages(requestTotal) || busy} on:click={() => setPage("request", requestPage + 1)}><Icon icon="solar:alt-arrow-right-bold" /></button></div>{/if}
      </section>
    {/if}
  {/if}
</main>

{#if editor}
  <div class="modal-backdrop" role="presentation" on:click={(e) => e.target === e.currentTarget && (editor = null)}>
    <div class="editor" role="dialog" aria-modal="true" aria-labelledby="editor-title" tabindex="-1">
      <header><div><span class="eyebrow">CIRCLE PROFILE</span><h2 id="editor-title">{editing ? "编辑圈子" : "创建圈子"}</h2></div><button class="icon-button" title="关闭" aria-label="关闭" on:click={() => editor = null}><Icon icon="solar:close-circle-bold-duotone" /></button></header>
      <label>圈子名称<input bind:value={editor.circleName} maxlength="32" placeholder="例如：周末打机团" /></label>
      <label>圈子简介<textarea bind:value={editor.comment} rows="4" maxlength="500" placeholder="介绍圈子的风格、活动时间或招募要求"></textarea></label>
      <label class="check-row"><input type="checkbox" bind:checked={editor.isPublic} /><span><strong>公开显示</strong><small>允许其他玩家在公开列表中找到圈子</small></span></label>
      <label class="check-row"><input type="checkbox" bind:checked={editor.isAllowAnyoneJoin} /><span><strong>允许直接加入</strong><small>关闭后，加入申请需要团长批准</small></span></label>
      <footer><button class="secondary-button" on:click={() => editor = null}>取消</button><button class="primary-button" disabled={busy || !editor.circleName.trim()} on:click={saveCircle}><Icon icon="solar:check-read-bold-duotone" />保存</button></footer>
    </div>
  </div>
{/if}

<style lang="sass">
  @use "../vars"

  .circle-page
    max-width: 1180px
    margin: 0 auto
    color: vars.$c-text
    padding-bottom: 56px

  .page-head, .section-heading, .joined-strip, .empty-state, .discover-tools
    display: flex
    align-items: center
    justify-content: space-between
    gap: 20px

  .page-head
    margin: 26px 0 22px
    h1
      margin: 4px 0
      font-size: 2rem
    p
      margin: 0
      color: #667b7e
  .eyebrow
    color: #249d97
    font-size: .7rem
    font-weight: 800
    letter-spacing: 0
  .card-picker
    display: grid
    gap: 5px
    min-width: 220px
    color: #607679
    font-size: .8rem
    select
      width: 100%
      border: 1px solid #c8d8d8
      border-radius: 6px
      padding: 10px 12px
      color: #24383b
      background: #fff
      font: inherit
  .joined-strip
    flex-wrap: wrap
    align-items: flex-start
    border: 1px solid #cce4e1
    border-left: 4px solid #39c5bb
    border-radius: 7px
    background: #f4fbfa
    padding: 20px 22px
    margin-bottom: 18px
  .joined-copy
    flex: 1 1 260px
    h2
      margin: 4px 0
      font-size: 1.35rem
    p
      max-width: 520px
      margin: 0
      color: #607679
      overflow-wrap: anywhere
  .joined-stats
    display: flex
    gap: 24px
    > div
      display: grid
      gap: 3px
    span
      color: #708487
      font-size: .76rem
    strong
      font-size: 1.15rem
    small
      font-size: .72rem
  .joined-actions
    display: flex
    flex-wrap: wrap
    align-items: center
    gap: 8px
    code
      padding: 7px 9px
      border-radius: 4px
      background: #e0f4f1
      color: #167f79
      font-weight: 800
  .primary-button, .secondary-button, .danger-button
    display: inline-flex
    align-items: center
    justify-content: center
    gap: 7px
    min-height: 38px
    border-radius: 5px
    padding: 8px 12px
    font: inherit
    font-weight: 700
    cursor: pointer
    transition: background .16s ease, border-color .16s ease
    :global(svg)
      font-size: 1.1rem
    &:disabled
      cursor: not-allowed
      opacity: .55
  .primary-button
    color: #fff
    border: 1px solid #258e88
    background: #258e88
    &:hover:not(:disabled)
      background: #187c77
  .secondary-button
    color: #236f6d
    border: 1px solid #afd3d0
    background: #fff
    &:hover:not(:disabled)
      background: #eaf8f6
  .danger-button
    color: #a23e55
    border: 1px solid #e7bec7
    background: #fff
    &:hover:not(:disabled)
      background: #fff1f3
  .empty-state
    justify-content: flex-start
    margin: 14px 0 22px
    padding: 18px
    border: 1px solid #d9e6e5
    border-radius: 7px
    background: #fff
    > div:nth-child(2)
      flex: 1
    h2
      margin: 0 0 4px
      font-size: 1.05rem
    p
      margin: 0
      color: #667b7e
  .empty-icon, .circle-mark, .request-avatar
    display: grid
    flex: 0 0 auto
    place-items: center
    width: 42px
    aspect-ratio: 1
    border-radius: 6px
    color: #168b85
    background: #def4f1
    :global(svg)
      font-size: 1.45rem
  .tabs
    display: flex
    gap: 6px
    margin: 24px 0 16px
    border-bottom: 1px solid #dce8e7
    button
      display: inline-flex
      align-items: center
      gap: 8px
      padding: 10px 14px
      border: 0
      border-bottom: 2px solid transparent
      color: #64797b
      background: transparent
      font: inherit
      cursor: pointer
      :global(svg)
        font-size: 1.15rem
      &.active
        color: #187f79
        border-bottom-color: #39c5bb
    .count
      border-radius: 999px
      padding: 2px 7px
      color: #287975
      background: #e1f3f1
      font-size: .76rem
  .discover-tools
    flex-wrap: wrap
    margin: 14px 0 26px
    padding: 12px 14px
    border-radius: 6px
    background: #f2f7f6
  .code-join
    display: flex
    flex-wrap: wrap
    align-items: center
    gap: 8px
    span
      color: #5f7578
      font-size: .84rem
      font-weight: 700
    input
      width: min(250px, 60vw)
      border: 1px solid #c8d8d8
      border-radius: 5px
      padding: 9px 10px
      background: #fff
      font: inherit
  .create-button
    margin-left: auto
  .section-heading
    margin-bottom: 12px
    h2
      margin: 3px 0 0
      font-size: 1.2rem
    .total
      color: #607679
      font-size: .84rem
  .circle-grid
    display: grid
    grid-template-columns: repeat(3, minmax(0, 1fr))
    gap: 12px
  .circle-card
    display: flex
    min-width: 0
    flex-direction: column
    padding: 16px
    border: 1px solid #d8e4e3
    border-radius: 6px
    background: #fff
    .circle-card-head
      display: flex
      align-items: center
      justify-content: space-between
    .member-count
      color: #647b7d
      font-size: .78rem
    h3
      margin: 13px 0 5px
      font-size: 1.08rem
      overflow-wrap: anywhere
    p
      flex: 1
      min-height: 42px
      margin: 0
      color: #617679
      white-space: pre-wrap
      overflow-wrap: anywhere
    .circle-meta
      display: flex
      justify-content: space-between
      gap: 8px
      margin: 14px 0 12px
      color: #74888a
      font-size: .75rem
    .join-button
      width: 100%
  .circle-mark
    width: 36px
    :global(svg)
      font-size: 1.2rem
  .pager
    display: flex
    align-items: center
    justify-content: center
    gap: 12px
    margin: 16px 0
    color: #63787a
    font-size: .86rem
  .icon-button
    display: inline-grid
    place-items: center
    width: 36px
    aspect-ratio: 1
    border: 1px solid #d0dfde
    border-radius: 5px
    color: #416467
    background: #fff
    cursor: pointer
    :global(svg)
      font-size: 1.15rem
    &:hover:not(:disabled)
      background: #eef8f6
    &:disabled
      opacity: .42
      cursor: not-allowed
  .manage-section
    margin: 22px 0 30px
  .table-wrap
    overflow-x: auto
    border: 1px solid #d9e4e3
    border-radius: 6px
    background: #fff
    table
      width: 100%
      border-collapse: collapse
      text-align: left
      white-space: nowrap
    th, td
      padding: 11px 13px
      border-bottom: 1px solid #e9efee
    th
      color: #617679
      background: #f5f9f8
      font-size: .78rem
      font-weight: 700
    tbody tr:last-child td
      border-bottom: 0
  .player-name
    font-weight: 700
  .owner-label
    margin-left: 7px
    border-radius: 3px
    padding: 3px 5px
    color: #167f79
    background: #e0f4f1
    font-size: .68rem
  .muted, .empty-cell
    color: #849496
  .empty-cell
    text-align: center
  .danger-icon
    color: #b74d62
    border-color: #efced4
  .approve-icon
    color: #218b70
    border-color: #b9ded1
  .request-list
    border: 1px solid #d9e4e3
    border-radius: 6px
    background: #fff
  .request-row
    display: flex
    align-items: center
    gap: 12px
    padding: 12px
    border-bottom: 1px solid #e9efee
    &:last-child
      border-bottom: 0
  .request-avatar
    width: 36px
  .request-profile
    display: grid
    flex: 1
    gap: 3px
    min-width: 0
    span
      color: #728689
      font-size: .78rem
  .request-row time
    color: #728689
    font-size: .78rem
  .request-actions
    display: flex
    gap: 6px
  .notice
    display: flex
    align-items: center
    gap: 9px
    padding: 13px 15px
    border: 1px solid #dce8e7
    border-radius: 5px
    color: #607679
    background: #f8fbfa
    &.error
      color: #9b3c52
      border-color: #edc8d0
      background: #fff5f6
    :global(svg)
      flex: 0 0 auto
      font-size: 1.15rem
  .toast
    position: fixed
    z-index: 20
    right: 22px
    bottom: 22px
    max-width: min(420px, calc(100vw - 32px))
    padding: 12px 16px
    border: 1px solid #b8ded8
    border-radius: 5px
    color: #1d706b
    background: #fff
    box-shadow: 0 8px 24px #143c3d20
    overflow-wrap: anywhere
  .modal-backdrop
    position: fixed
    z-index: 30
    inset: 0
    display: grid
    place-items: center
    padding: 18px
    background: #18282991
  .editor
    display: grid
    gap: 16px
    width: min(540px, 100%)
    max-height: calc(100vh - 36px)
    overflow: auto
    padding: 20px
    border: 1px solid #d9e5e3
    border-radius: 7px
    background: #fff
    box-shadow: 0 24px 70px #102b2b44
    > header, > footer
      display: flex
      align-items: center
      justify-content: space-between
      gap: 10px
    h2
      margin: 3px 0 0
      font-size: 1.25rem
    > label:not(.check-row)
      display: grid
      gap: 6px
      color: #425d60
      font-size: .86rem
      font-weight: 700
    input:not([type=checkbox]), textarea
      width: 100%
      border: 1px solid #cbd9d8
      border-radius: 5px
      padding: 10px 11px
      color: #24383b
      background: #fff
      font: inherit
      font-weight: 400
    textarea
      resize: vertical
    .check-row
      display: flex
      align-items: flex-start
      gap: 10px
      input
        width: 17px
        height: 17px
        margin-top: 3px
        accent-color: #258e88
      span
        display: grid
        gap: 3px
      small
        color: #708487
        font-size: .78rem
    > footer
      justify-content: flex-end
      padding-top: 8px
      border-top: 1px solid #e5edec

  @media (max-width: 760px)
    .page-head, .joined-strip
      align-items: flex-start
    .page-head
      flex-direction: column
    .card-picker
      width: 100%
    .joined-stats
      width: 100%
      justify-content: space-between
      gap: 12px
    .circle-grid
      grid-template-columns: repeat(2, minmax(0, 1fr))
    .empty-state
      align-items: flex-start
      flex-wrap: wrap
    .empty-state > .primary-button
      margin-left: 54px
  @media (max-width: 520px)
    .circle-grid
      grid-template-columns: 1fr
    .joined-actions
      width: 100%
    .discover-tools
      align-items: stretch
    .code-join
      width: 100%
      input
        flex: 1
        min-width: 120px
    .create-button
      margin-left: 0
    .request-row
      flex-wrap: wrap
    .request-profile
      flex-basis: calc(100% - 60px)
    .request-row time
      margin-left: 48px
    .request-actions
      margin-left: auto
</style>
