<!-- Game name review: approve or reject the names players asked for. -->
<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../ui/Icon.svelte"
  import Spinner from "../ui/Spinner.svelte"
  import { NAME_REVIEW } from "../../libs/sdk"
  import type { NameRequestAdmin } from "../../libs/sdk"
  import { errorMessage, toast } from "../../libs/toast"

  const gameLabels: Record<string, string> = { mai2: "舞萌 DX", chu3: "中二节奏", ongeki: "音击", wacca: "WACCA", diva: "DIVA" }
  const statusLabels = { PENDING: "待审核", APPROVED: "已通过", REJECTED: "已拒绝" }

  let items: NameRequestAdmin[] = []
  let enabled = false
  let showAll = false
  let loading = false
  let busy = 0
  // The request whose rejection reason is being typed.
  let rejecting = 0
  let reason = ""

  const time = (ms: number | null) => ms ? new Date(ms).toLocaleString("zh-CN", { hour12: false }) : ""

  async function load() {
    loading = true
    try {
      const res = await NAME_REVIEW.adminList(showAll)
      items = res.requests
      enabled = res.enabled
    } catch (e) { toast.error(errorMessage(e)) } finally { loading = false }
  }

  async function approve(item: NameRequestAdmin) {
    busy = item.id
    try {
      await NAME_REVIEW.adminApprove(item.id)
      toast.success(`已通过「${item.newName}」`)
    } catch (e) { toast.error(errorMessage(e)) } finally { busy = 0 }
    await load()
  }

  async function reject(item: NameRequestAdmin) {
    busy = item.id
    try {
      await NAME_REVIEW.adminReject(item.id, reason.trim())
      toast.success(`已拒绝「${item.newName}」`)
      rejecting = 0
      reason = ""
    } catch (e) { toast.error(errorMessage(e)) } finally { busy = 0 }
    await load()
  }

  onMount(load)
</script>

<section class="review">
  <div class="toolbar">
    <h2>名称审核</h2>
    <label class="toggle"><input type="checkbox" class="switch" bind:checked={showAll} on:change={load} /><span>显示全部记录</span></label>
    <button class="btn-ghost btn-sm" type="button" on:click={load} disabled={loading} title="刷新">{#if loading}<Spinner />{:else}<Icon icon="solar:refresh-bold" />{/if}</button>
  </div>
  <p class="hint">
    {#if enabled}名称审核已开启：玩家提交的新名字在这里通过后才会生效。{:else}名称审核当前已关闭（可在「功能开关」里打开），玩家改名立即生效；下面只有关闭前遗留的申请。{/if}
  </p>

  <div class="list">
    {#each items as item (item.id)}
      <div class="card item" class:done={item.status !== "PENDING"}>
        <div class="copy">
          <strong>{item.oldName || "（无）"} <Icon icon="solar:arrow-right-linear" /> {item.newName}</strong>
          <small>{item.username ?? `用户 ${item.auId}`} · {gameLabels[item.game] ?? item.game} · 提交于 {time(item.submittedAt)}</small>
          {#if item.status !== "PENDING"}
            <small>
              {statusLabels[item.status]} · {time(item.reviewedAt)}
              {#if item.autoApproved}· 超时自动通过{:else if item.reviewer}· 审核人 {item.reviewer}{/if}
              {#if item.rejectReason}· 理由：{item.rejectReason}{/if}
            </small>
          {/if}
          {#if rejecting === item.id}
            <div class="reject-row">
              <input type="text" maxlength="255" placeholder="拒绝理由（会显示给玩家，可留空）" bind:value={reason} />
              <button class="btn-sm danger" type="button" disabled={busy === item.id} on:click={() => reject(item)}>确认拒绝</button>
              <button class="btn-ghost btn-sm" type="button" on:click={() => { rejecting = 0; reason = "" }}>取消</button>
            </div>
          {/if}
        </div>
        {#if item.status === "PENDING" && rejecting !== item.id}
          <div class="actions">
            {#if busy === item.id}<Spinner />{:else}
              <button class="btn-sm" type="button" on:click={() => approve(item)}><Icon icon="solar:check-circle-bold" />通过</button>
              <button class="btn-ghost btn-sm" type="button" on:click={() => { rejecting = item.id; reason = "" }}><Icon icon="solar:close-circle-bold" />拒绝</button>
            {/if}
          </div>
        {/if}
      </div>
    {:else}
      <p class="subtle">{loading ? "加载中…" : showAll ? "还没有任何申请" : "没有待审核的名字"}</p>
    {/each}
  </div>
</section>

<style lang="sass">
  .review
    display: grid
    gap: 14px
    padding: 20px 0

  .toolbar
    display: flex
    align-items: center
    gap: 12px

    h2
      margin: 0 auto 0 0
      font-size: 20px

    .toggle
      display: flex
      align-items: center
      gap: 8px
      color: var(--text-2)
      font-size: 0.86rem

  .hint
    margin: 0
    opacity: 0.7

  .list
    display: grid
    gap: 12px

  .item
    display: flex
    flex-direction: row
    align-items: center
    gap: 14px
    text-align: left

    &.done
      background: var(--surface-2)

    .copy
      display: grid
      gap: 4px
      flex: 1
      min-width: 0

      small
        color: var(--text-2)

    .actions
      display: flex
      gap: 8px
      flex: 0 0 auto

  .reject-row
    display: flex
    gap: 8px
    flex-wrap: wrap

    input
      flex: 1
      min-width: 180px

  @media (max-width: 760px)
    .item
      flex-wrap: wrap

      .actions
        margin-left: auto
</style>
