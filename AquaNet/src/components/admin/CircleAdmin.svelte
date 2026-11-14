<!-- Circle management: browse every circle, edit it, hand over ownership, remove members or dissolve it. -->
<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../ui/Icon.svelte"
  import Spinner from "../ui/Spinner.svelte"
  import { CIRCLE_ADMIN } from "../../libs/sdk"
  import type { CircleAdminMember, CircleAdminRow } from "../../libs/sdk"
  import { errorMessage, toast } from "../../libs/toast"
  import { confirmDialog } from "../../libs/confirm"

  const PAGE_SIZE = 20

  let circles: CircleAdminRow[] = []
  let total = 0
  let page = 0
  let query = ""
  let loading = false
  let saving = false

  let selected: CircleAdminRow | null = null
  let members: CircleAdminMember[] = []
  let membersLoading = false
  let form = { name: "", comment: "", allowAnyoneJoin: false }

  $: pages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  async function load() {
    loading = true
    try {
      const result = await CIRCLE_ADMIN.list(query.trim(), page)
      circles = result.content
      total = result.totalElements
      if (selected) selected = circles.find(circle => circle.circleId === selected?.circleId) ?? selected
    } catch (e) { toast.error(errorMessage(e)) } finally { loading = false }
  }

  function search() { page = 0; void load() }
  function go(next: number) { page = next; void load() }

  async function open(circle: CircleAdminRow) {
    selected = circle
    form = { name: circle.circleName, comment: circle.comment, allowAnyoneJoin: circle.isAllowAnyoneJoin }
    await loadMembers()
  }

  async function loadMembers() {
    if (!selected) return
    membersLoading = true
    try { members = await CIRCLE_ADMIN.members(selected.circleId) } catch (e) { toast.error(errorMessage(e)); members = [] } finally { membersLoading = false }
  }

  async function run(action: () => Promise<unknown>, success: string) {
    saving = true
    try {
      await action()
      toast.success(success)
      await load()
      await loadMembers()
    } catch (e) { toast.error(errorMessage(e)) } finally { saving = false }
  }

  async function save() {
    if (!selected) return
    const id = selected.circleId
    await run(() => CIRCLE_ADMIN.update(id, form.name, form.comment, form.allowAnyoneJoin), "圈子信息已保存")
  }

  async function kick(member: CircleAdminMember) {
    if (!selected) return
    const id = selected.circleId
    if (!await confirmDialog({ title: "移出成员", message: `确定把 ${member.userName || member.extId} 移出圈子吗？`, confirmText: "移出", danger: true })) return
    await run(() => CIRCLE_ADMIN.kick(id, member.extId), "已移出成员")
  }

  async function makeOwner(member: CircleAdminMember) {
    if (!selected) return
    const id = selected.circleId
    if (!await confirmDialog({ title: "转让圈主", message: `确定把圈子转让给 ${member.userName || member.extId} 吗？`, confirmText: "转让" })) return
    await run(() => CIRCLE_ADMIN.setOwner(id, member.extId), "圈主已变更")
  }

  async function dissolve() {
    if (!selected) return
    const circle = selected
    if (!await confirmDialog({ title: "解散圈子", message: `确定解散圈子「${circle.circleName}」吗？所有成员和加入申请都会一起删除，无法恢复。`, confirmText: "解散", danger: true })) return
    saving = true
    try {
      await CIRCLE_ADMIN.remove(circle.circleId)
      toast.success("圈子已解散")
      selected = null
      members = []
      await load()
    } catch (e) { toast.error(errorMessage(e)) } finally { saving = false }
  }

  onMount(load)
</script>

<section class="circles">
  <div class="toolbar">
    <h2>圈子管理</h2>
    <button class="btn-ghost btn-sm" type="button" on:click={load} disabled={loading} title="刷新">{#if loading}<Spinner />{:else}<Icon icon="solar:refresh-bold" />{/if}</button>
  </div>
  <p class="hint">所有圈子都会显示在网站的圈子页面上，不论是否公开。在这里可以修改圈子资料、转让圈主、移出成员或解散圈子。</p>
  <form class="search" on:submit|preventDefault={search}>
    <Icon icon="solar:magnifer-bold-duotone" /><input type="search" bind:value={query} placeholder="搜索圈子名称或圈子代码" aria-label="搜索圈子" />
    <button class="btn-primary" type="submit">搜索</button>
    <span class="subtle">{total} 个圈子</span>
  </form>

  <div class="layout">
    <div class="list">
      {#each circles as circle (circle.circleId)}
        <button type="button" class="circle" class:selected={selected?.circleId === circle.circleId} on:click={() => open(circle)}>
          <span class="head"><strong>{circle.circleName}</strong><small class="mono">{circle.circleCode}</small></span>
          <span class="meta">圈主 {circle.ownerName || "未知"} · {circle.memberCount} 人{#if circle.requestCount} · {circle.requestCount} 个申请{/if}</span>
          <span class="badges">
            <span class="badge" class:success={circle.isAllowAnyoneJoin}>{circle.isAllowAnyoneJoin ? "加入即通过" : "需要审批"}</span>
            {#if !circle.isPublic}<span class="badge">未公开</span>{/if}
            <small class="subtle">{circle.createdAt.slice(0, 10)}</small>
          </span>
        </button>
      {:else}
        <p class="subtle">{loading ? "加载中…" : query.trim() ? "没有匹配的圈子" : "还没有圈子"}</p>
      {/each}
      {#if pages > 1}
        <nav class="pager" aria-label="分页">
          <button class="btn-icon" type="button" aria-label="上一页" disabled={page <= 0 || loading} on:click={() => go(page - 1)}><Icon icon="solar:alt-arrow-left-bold" /></button>
          <span class="num">{page + 1} / {pages}</span>
          <button class="btn-icon" type="button" aria-label="下一页" disabled={page + 1 >= pages || loading} on:click={() => go(page + 1)}><Icon icon="solar:alt-arrow-right-bold" /></button>
        </nav>
      {/if}
    </div>

    {#if selected}
      <div class="detail card">
        <div class="card-header"><div><h3>{selected.circleName}</h3><p>圈子 #{selected.circleId} · 代码 {selected.circleCode} · 圈主 {selected.ownerName || "未知"}（{selected.ownerExtId}）</p></div></div>
        <form class="editor" on:submit|preventDefault={save}>
          <label class="field"><span class="field-label">圈子名称</span><input required maxlength="32" bind:value={form.name} /></label>
          <label class="field"><span class="field-label">圈子简介</span><textarea rows="3" maxlength="500" bind:value={form.comment}></textarea><small class="field-hint">{form.comment.length} / 500</small></label>
          <label class="switch-row"><span><strong>允许直接加入</strong><small>关闭后，加入申请需要圈主批准</small></span><input type="checkbox" class="switch" bind:checked={form.allowAnyoneJoin} /></label>
          <div class="actions"><button class="btn-primary" type="submit" disabled={saving}>保存修改</button></div>
        </form>

        <h4>成员（{members.length}）</h4>
        <div class="table-wrap">
          <table class="data-table table-cards">
            <thead><tr><th>玩家 ID</th><th>昵称</th><th class="num">Rating</th><th class="num">圈子点数</th><th>操作</th></tr></thead>
            <tbody>
              {#each members as member (member.extId)}
                <tr>
                  <td data-label="玩家 ID" class="mono">{member.extId}</td>
                  <td data-label="昵称">{member.userName || "—"}{#if member.isOwner} <span class="badge primary">圈主</span>{/if}</td>
                  <td data-label="Rating" class="num">{member.playerRating}</td>
                  <td data-label="圈子点数" class="num">{member.point}</td>
                  <td data-label="">
                    {#if !member.isOwner}
                      <span class="row-actions">
                        <button class="btn-ghost btn-sm" type="button" disabled={saving} on:click={() => makeOwner(member)}>设为圈主</button>
                        <button class="btn-ghost btn-sm" type="button" disabled={saving} on:click={() => kick(member)}>移出</button>
                      </span>
                    {/if}
                  </td>
                </tr>
              {:else}
                <tr><td colspan="5" data-label="" class="subtle">{membersLoading ? "加载中…" : "暂无成员"}</td></tr>
              {/each}
            </tbody>
          </table>
        </div>

        <div class="danger-zone">
          <div><strong>解散圈子</strong><small>成员和加入申请会一起删除，无法恢复。</small></div>
          <button class="btn-danger" type="button" disabled={saving} on:click={dissolve}>解散圈子</button>
        </div>
      </div>
    {/if}
  </div>
</section>

<style lang="sass">
  .circles
    padding: 20px 0

  .toolbar, .search
    display: flex
    align-items: center
    gap: 12px

  .toolbar h2
    margin: 0 auto 0 0
    font-size: 20px

  .hint
    margin: 4px 0 0
    opacity: 0.7

  .search
    position: relative
    flex-wrap: wrap
    margin: 16px 0

    > :global(svg)
      position: absolute
      left: 10px
      color: var(--text-3)

    input
      flex: 1
      min-width: 180px
      padding-left: 34px

  .layout
    display: grid
    grid-template-columns: minmax(240px, 300px) minmax(0, 1fr)
    align-items: start
    gap: 16px

  .list
    display: grid
    gap: 8px

  .circle
    display: grid
    justify-content: stretch
    justify-items: stretch
    gap: 6px
    width: 100%
    padding: 12px 14px
    text-align: left
    border: 1px solid var(--border)
    border-radius: var(--radius)
    background: var(--surface)

    &.selected
      border-color: var(--primary)
      background: var(--primary-soft)

    .head
      display: flex
      align-items: baseline
      justify-content: space-between
      gap: 8px

    .meta
      display: block
      color: var(--text-2)
      font-size: 0.86rem

    .badges
      display: flex
      align-items: center
      flex-wrap: wrap
      gap: 6px

  .pager
    display: flex
    align-items: center
    justify-content: center
    gap: 12px

  .detail
    display: grid
    gap: 14px
    min-width: 0

    h4
      margin: 6px 0 0

  .editor
    display: grid
    gap: 12px

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

  .row-actions
    display: flex
    flex-wrap: wrap
    gap: 4px

  .danger-zone
    display: flex
    align-items: center
    justify-content: space-between
    gap: 12px
    padding-top: 14px
    border-top: 1px solid var(--border)

    div
      display: grid
      gap: 2px

    small
      color: var(--text-2)

  @media (max-width: 900px)
    .layout
      grid-template-columns: minmax(0, 1fr)
</style>
