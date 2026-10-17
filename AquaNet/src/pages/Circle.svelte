<script lang="ts">
  import { onMount } from "svelte";
  import Icon from "../components/ui/Icon.svelte";
  import { CIRCLE, USER } from "../libs/sdk";
  import type { MikuNetUser, Card } from "../libs/generalTypes";
  import PageHeader from "../components/ui/PageHeader.svelte";
  import EmptyState from "../components/ui/EmptyState.svelte";
  import Dialog from "../components/ui/Dialog.svelte";
  import Spinner from "../components/ui/Spinner.svelte";
  import { toast } from "../libs/toast";
  import { confirmDialog } from "../libs/confirm";

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
  let editor: CircleDraft | null = null;
  let editing = false;
  let circleCode = "";
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
      const info = unwrap<any>(infoResult);
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
    failed ? toast.error(text) : toast.success(text);
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
    if (!currentCircle || !await confirmDialog({ title: "退出圈子", message: `确定退出「${currentCircle.circleName}」吗？`, confirmText: "退出", danger: true })) return;
    await mutate(() => CIRCLE.leave(selectedCardId), "已退出圈子。");
  }

  async function dissolve() {
    if (!currentCircle || !await confirmDialog({ title: "解散圈子", message: `解散「${currentCircle.circleName}」后成员和申请记录将一并移除，此操作无法撤销。`, confirmText: "解散", danger: true })) return;
    await mutate(() => CIRCLE.dissolve(selectedCardId), "圈子已解散。");
  }

  async function kick(member: Member) {
    if (!await confirmDialog({ title: "移出成员", message: `将 ${member.userProfile.userName} 移出圈子？`, confirmText: "移出", danger: true })) return;
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

<main class="page circle-page">
  <PageHeader eyebrow="maimai DX" title="圈子" description="创建圈子、申请加入，和同好一起游玩。">
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
    <div class="alert alert-danger" role="alert"><Icon icon="solar:danger-triangle-bold-duotone" /><p>{error}</p>
      {#if !busy}<button class="btn-sm" type="button" on:click={initialize}>重新读取</button>{/if}
    </div>
  {/if}
  {#if busy && !circleInfo && !error}
    <div class="stack" aria-hidden="true">
      <span class="skeleton" style="height:130px;border-radius:16px"></span>
      <div class="circle-grid">{#each [1, 2, 3] as _}<span class="skeleton" style="height:190px;border-radius:16px"></span>{/each}</div>
    </div>
  {/if}

  {#if circleInfo}
    {#if currentCircle}
      <section class="card joined">
        <div class="joined-main">
          <span class="circle-mark large"><Icon icon="solar:users-group-rounded-bold-duotone" /></span>
          <div class="joined-copy">
            <span class="badge primary">{isOwner ? "我是团长" : "当前圈子"}</span>
            <h2>{currentCircle.circleName}</h2>
            <p>{currentCircle.comment || "这个圈子还没有简介。"}</p>
          </div>
        </div>
        <dl class="joined-stats">
          <div><dt>成员</dt><dd class="num">{memberTotal || currentCircle.memberCount || 1}</dd></div>
          <div><dt>我的点数</dt><dd class="num">{circleInfo.userCirclePointData?.point ?? 0}<small> pt</small></dd></div>
          <div><dt>上月排名</dt><dd class="num">#{circleInfo.userCirclePointRankingResult?.lastMonthCircleRank || "—"}</dd></div>
        </dl>
        <div class="joined-actions">
          {#if currentCircle.circleCode}
            <button class="code-chip" type="button" on:click={copyCode} title="复制圈子代码" aria-label="复制圈子代码 {currentCircle.circleCode}">
              <span class="mono">{currentCircle.circleCode}</span><Icon icon="solar:copy-bold-duotone" />
            </button>
          {/if}
          <span class="spacer"></span>
          {#if isOwner}
            <button type="button" on:click={openEdit}><Icon icon="solar:pen-bold-duotone" />编辑</button>
            <button class="btn-danger" type="button" on:click={dissolve}><Icon icon="solar:trash-bin-trash-bold-duotone" />解散</button>
          {:else}
            <button class="btn-danger" type="button" on:click={leave}><Icon icon="solar:logout-2-bold-duotone" />退出圈子</button>
          {/if}
        </div>
      </section>
    {/if}

    {#if isOwner}
      <div class="tabs" role="tablist" aria-label="圈子页面">
        <button type="button" role="tab" aria-selected={tab === "discover"} on:click={() => tab = "discover"}><Icon icon="solar:compass-bold-duotone" />发现圈子</button>
        <button type="button" role="tab" aria-selected={tab === "manage"} on:click={() => tab = "manage"}><Icon icon="solar:users-group-rounded-bold-duotone" />成员管理<span class="count" class:attention={requestTotal > 0}>{requestTotal}</span></button>
      </div>
    {/if}

    {#if tab === "discover"}
      <section class="toolbar">
        <form class="code-join" on:submit|preventDefault={joinWithCode}>
          <label class="visually-hidden" for="circle-code">圈子代码</label>
          <input id="circle-code" class="mono" bind:value={circleCode} maxlength="16" placeholder="输入圈子代码" autocomplete="off" />
          <button class="btn-primary" type="submit" disabled={!circleCode.trim() || busy}>使用代码加入</button>
        </form>
        {#if !currentCircle}<button type="button" on:click={openCreate}><Icon icon="solar:add-circle-bold-duotone" />创建圈子</button>{/if}
      </section>

      <section class="section" aria-label="公开圈子">
        <div class="section-header"><div><h2>公开圈子</h2><p>{publicTotal} 个圈子正在招募</p></div></div>
        {#if publicCircles.length}
          <div class="circle-grid">
            {#each publicCircles as circle (circle.circleId)}
              <article class="card circle-card">
                <div class="circle-card-head">
                  <span class="circle-mark"><Icon icon="solar:users-group-rounded-bold-duotone" /></span>
                  <span class="badge"><Icon icon="solar:user-rounded-bold-duotone" />{circle.memberCount ?? 0}</span>
                </div>
                <h3>{circle.circleName}</h3>
                <p>{circle.comment || "暂无圈子简介。"}</p>
                <div class="circle-meta">
                  <span class="badge" class:success={circle.isAllowAnyoneJoin}>{circle.isAllowAnyoneJoin ? "加入即通过" : "需要团长审批"}</span>
                  <span class="subtle num">#{circle.circleId}</span>
                </div>
                {#if !currentCircle}
                  <button class="btn-soft btn-block" type="button" disabled={busy} on:click={() => join(circle)}>
                    <Icon icon="solar:user-plus-rounded-bold-duotone" />{circle.isAllowAnyoneJoin ? "加入圈子" : "申请加入"}
                  </button>
                {/if}
              </article>
            {/each}
          </div>
          {#if pages(publicTotal) > 1}
            <nav class="pager" aria-label="分页">
              <button class="btn-icon" type="button" aria-label="上一页" disabled={publicPage <= 0 || busy} on:click={() => setPage("public", publicPage - 1)}><Icon icon="solar:alt-arrow-left-bold" /></button>
              <span class="num">{publicPage + 1} / {pages(publicTotal)}</span>
              <button class="btn-icon" type="button" aria-label="下一页" disabled={publicPage + 1 >= pages(publicTotal) || busy} on:click={() => setPage("public", publicPage + 1)}><Icon icon="solar:alt-arrow-right-bold" /></button>
            </nav>
          {/if}
        {:else}
          <EmptyState icon="solar:users-group-rounded-bold-duotone" title="暂时没有公开圈子" description="创建一个来聚集同好吧。">
            {#if !currentCircle}<button class="btn-primary" type="button" on:click={openCreate}><Icon icon="solar:add-circle-bold-duotone" />创建圈子</button>{/if}
          </EmptyState>
        {/if}
      </section>
    {:else if tab === "manage" && isOwner}
      <section class="section">
        <div class="section-header"><div><h2>加入申请</h2><p>{requestTotal} 项待处理</p></div></div>
        {#if requests.length}
          <div class="card card-flush">
            {#each requests as request (request.userCode)}
              <article class="request-row">
                <span class="circle-mark"><Icon icon="solar:user-rounded-bold-duotone" /></span>
                <div class="request-profile"><strong class="truncate">{request.userProfile.userName}</strong><span class="truncate">Rating {request.userProfile.playerRating} · 最近游玩 {fmtDate(request.userProfile.lastPlayDate)}</span></div>
                <time class="subtle num">{fmtDate(request.requestTime)}</time>
                <div class="request-actions">
                  <button class="btn-primary btn-sm" type="button" disabled={busy} on:click={() => decide(request, true)}><Icon icon="solar:check-circle-bold-duotone" />批准</button>
                  <button class="btn-ghost btn-sm" type="button" disabled={busy} on:click={() => decide(request, false)}>拒绝</button>
                </div>
              </article>
            {/each}
          </div>
        {:else}
          <EmptyState compact icon="solar:inbox-in-bold-duotone" title="当前没有待处理申请" />
        {/if}
        {#if pages(requestTotal) > 1}
          <nav class="pager" aria-label="申请分页">
            <button class="btn-icon" type="button" aria-label="上一页" disabled={requestPage <= 0 || busy} on:click={() => setPage("request", requestPage - 1)}><Icon icon="solar:alt-arrow-left-bold" /></button>
            <span class="num">{requestPage + 1} / {pages(requestTotal)}</span>
            <button class="btn-icon" type="button" aria-label="下一页" disabled={requestPage + 1 >= pages(requestTotal) || busy} on:click={() => setPage("request", requestPage + 1)}><Icon icon="solar:alt-arrow-right-bold" /></button>
          </nav>
        {/if}
      </section>

      <section class="section">
        <div class="section-header"><div><h2>圈子成员</h2><p>{memberTotal} 人</p></div></div>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>玩家</th><th class="num">Rating</th><th class="num">点数</th><th>上次游玩</th><th class="num">操作</th></tr></thead>
            <tbody>
              {#each members as member (member.userCode)}
                <tr>
                  <td><strong>{member.userProfile.userName}</strong>{#if member.isOwner}<span class="badge primary owner">团长</span>{/if}</td>
                  <td class="num">{member.userProfile.playerRating}</td>
                  <td class="num">{member.userCirclePointData?.point ?? 0} pt</td>
                  <td class="num muted">{fmtDate(member.userProfile.lastPlayDate)}</td>
                  <td class="num">
                    {#if !member.isOwner}
                      <button class="btn-ghost btn-sm kick" type="button" aria-label="移出 {member.userProfile.userName}" on:click={() => kick(member)}><Icon icon="solar:user-cross-rounded-bold-duotone" />移出</button>
                    {:else}<span class="subtle">—</span>{/if}
                  </td>
                </tr>
              {:else}
                <tr><td colspan="5" class="empty-cell">暂无成员数据</td></tr>
              {/each}
            </tbody>
          </table>
        </div>
        {#if pages(memberTotal) > 1}
          <nav class="pager" aria-label="成员分页">
            <button class="btn-icon" type="button" aria-label="上一页" disabled={memberPage <= 0 || busy} on:click={() => setPage("member", memberPage - 1)}><Icon icon="solar:alt-arrow-left-bold" /></button>
            <span class="num">{memberPage + 1} / {pages(memberTotal)}</span>
            <button class="btn-icon" type="button" aria-label="下一页" disabled={memberPage + 1 >= pages(memberTotal) || busy} on:click={() => setPage("member", memberPage + 1)}><Icon icon="solar:alt-arrow-right-bold" /></button>
          </nav>
        {/if}
      </section>
    {/if}
  {/if}
</main>

{#if editor}
  <Dialog title={editing ? "编辑圈子" : "创建圈子"} on:close={() => editor = null}>
    <form id="circle-editor" class="editor" on:submit|preventDefault={saveCircle}>
      <label class="field"><span class="field-label">圈子名称</span>
        <input bind:value={editor.circleName} maxlength="32" placeholder="例如：周末打机团" required data-autofocus />
        <small class="field-hint">{editor.circleName.length} / 32</small>
      </label>
      <label class="field"><span class="field-label">圈子简介</span>
        <textarea bind:value={editor.comment} rows="4" maxlength="500" placeholder="介绍圈子的风格、活动时间或招募要求"></textarea>
        <small class="field-hint">{editor.comment.length} / 500</small>
      </label>
      <label class="switch-row"><span><strong>公开显示</strong><small>允许其他玩家在公开列表中找到圈子</small></span><input type="checkbox" class="switch" bind:checked={editor.isPublic} /></label>
      <label class="switch-row"><span><strong>允许直接加入</strong><small>关闭后，加入申请需要团长批准</small></span><input type="checkbox" class="switch" bind:checked={editor.isAllowAnyoneJoin} /></label>
    </form>
    <svelte:fragment slot="footer">
      <button type="button" on:click={() => editor = null}>取消</button>
      <button class="btn-primary" type="submit" form="circle-editor" disabled={busy || !editor.circleName.trim()}>
        {#if busy}<Spinner />{:else}<Icon icon="solar:check-read-bold-duotone" />{/if}保存
      </button>
    </svelte:fragment>
  </Dialog>
{/if}

<style lang="sass">
  @use "../vars"

  .card-picker
    min-width: 220px

  .joined
    gap: 18px
    background: linear-gradient(120deg, var(--surface) 45%, var(--primary-soft))

  .joined-main
    display: flex
    align-items: flex-start
    gap: 16px

  .joined-copy
    display: grid
    justify-items: start
    gap: 6px
    min-width: 0

    h2
      margin: 0
      font-size: 1.4rem
      overflow-wrap: anywhere

    p
      max-width: 640px
      margin: 0
      color: var(--text-2)
      white-space: pre-wrap
      overflow-wrap: anywhere

  .joined-stats
    display: grid
    grid-template-columns: repeat(3, minmax(0, 1fr))
    gap: 12px
    margin: 0

    div
      display: grid
      gap: 2px
      padding: 12px 14px
      border-radius: var(--radius)
      background: var(--surface)

    dt
      color: var(--text-2)
      font-size: 0.8rem

    dd
      margin: 0
      font-size: 1.25rem
      font-weight: 700

      small
        color: var(--text-3)
        font-size: 0.75rem

  .joined-actions
    display: flex
    align-items: center
    gap: 8px
    flex-wrap: wrap

    .spacer
      flex: 1

  .code-chip
    border-style: dashed
    color: var(--primary)
    font-weight: 700
    letter-spacing: 0.06em

  .circle-mark
    display: grid
    place-items: center
    width: 40px
    height: 40px
    flex: 0 0 40px
    border-radius: var(--radius)
    color: var(--primary)
    background: var(--primary-soft)
    font-size: 1.3rem

    &.large
      width: 56px
      height: 56px
      flex-basis: 56px
      font-size: 1.8rem

  .tabs .count.attention
    color: #fff
    background: var(--danger)

  .toolbar
    display: flex
    align-items: center
    justify-content: space-between
    gap: 12px
    flex-wrap: wrap

  .code-join
    display: flex
    gap: 8px
    flex: 1
    max-width: 460px

    input
      flex: 1
      min-width: 0

  .circle-grid
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr))
    gap: 14px

  .circle-card
    gap: 10px

    h3
      margin: 4px 0 0
      font-size: 1.05rem
      overflow-wrap: anywhere

    p
      flex: 1
      min-height: 2.8em
      margin: 0
      color: var(--text-2)
      font-size: 0.88rem
      white-space: pre-wrap
      overflow-wrap: anywhere
      display: -webkit-box
      -webkit-line-clamp: 3
      -webkit-box-orient: vertical
      overflow: hidden

  .circle-card-head, .circle-meta
    display: flex
    align-items: center
    justify-content: space-between
    gap: 8px

  .circle-meta
    font-size: 0.8rem

  .pager
    display: flex
    align-items: center
    justify-content: center
    gap: 12px
    color: var(--text-2)

  .request-row
    display: flex
    align-items: center
    gap: 12px
    min-height: 64px
    padding: 10px 16px
    border-bottom: 1px solid var(--border)

    &:last-child
      border-bottom: 0

  .request-profile
    display: grid
    flex: 1
    min-width: 0

    span
      color: var(--text-2)
      font-size: 0.82rem

  .request-row time
    font-size: 0.8rem

  .request-actions
    display: flex
    gap: 6px

  .owner
    margin-left: 8px

  .kick:hover:not(:disabled)
    color: var(--danger)
    background: var(--danger-soft)

  .empty-cell
    color: var(--text-3)
    text-align: center !important

  .editor
    display: grid
    gap: 16px

  .switch-row
    display: flex
    align-items: center
    justify-content: space-between
    gap: 16px
    padding: 12px 14px
    border: 1px solid var(--border)
    border-radius: var(--radius)

    span
      display: grid
      gap: 2px

    small
      color: var(--text-2)
      font-size: 0.82rem

  @media (max-width: vars.$w-mobile)
    .card-picker, .code-join
      width: 100%
      max-width: none

    .joined-stats
      gap: 8px

      div
        padding: 10px

      dd
        font-size: 1.05rem

    .request-row
      flex-wrap: wrap

      time
        display: none

    .request-actions
      width: 100%
      justify-content: flex-end
</style>
