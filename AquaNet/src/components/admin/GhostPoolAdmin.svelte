<!-- Friend battle: size of the ghost pool and a button to refill it from players' recent plays. -->
<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../ui/Icon.svelte"
  import Spinner from "../ui/Spinner.svelte"
  import { GHOST_ADMIN } from "../../libs/sdk"
  import type { GhostPoolStatus } from "../../libs/sdk"
  import { errorMessage, toast } from "../../libs/toast"

  let status: GhostPoolStatus | null = null
  let loading = false
  let seeding = false

  async function load() {
    loading = true
    try { status = await GHOST_ADMIN.status() } catch (e) { toast.error(errorMessage(e)) } finally { loading = false }
  }

  async function seed() {
    seeding = true
    try {
      status = await GHOST_ADMIN.seed()
      toast.success(status.last ? `已补充：${status.last.players} 位玩家，写入 ${status.last.written} 条幽灵` : "补充完成")
    } catch (e) { toast.error(errorMessage(e)) } finally { seeding = false }
  }

  onMount(load)
</script>

<section class="ghosts">
  <div class="toolbar">
    <h2>友人对战</h2>
    <button class="btn-ghost btn-sm" type="button" on:click={load} disabled={loading} title="刷新">{#if loading}<Spinner />{:else}<Icon icon="solar:refresh-bold" />{/if}</button>
  </div>
  <p class="hint">友人对战的对手来自其他玩家的幽灵。玩家打完歌后机台会上传幽灵；这里还会定时用玩家平时的成绩补充幽灵，玩的人少时也能遇到真人。</p>

  {#if status}
    <div class="kpis">
      <div><small>幽灵总数</small><strong class="num">{status.total}</strong></div>
      <div><small>覆盖玩家数</small><strong class="num">{status.players}</strong></div>
      <div><small>机台上传的完整幽灵</small><strong class="num">{status.recorded}</strong></div>
      <div><small>由成绩补充的幽灵</small><strong class="num">{status.fromPlays}</strong></div>
    </div>

    <div class="card panel">
      <div>
        <strong>用平时的成绩补充</strong>
        <p class="subtle">
          {status.enabled ? "已开启" : "已关闭"}：每天凌晨 4:30（北京时间）取每位玩家最近 {status.days} 天里不同歌曲的最新成绩，每人最多 {status.perPlayer} 首。
          被封禁、被排除出排行榜或选择不参与排行榜的玩家不会出现。
        </p>
        <p class="subtle">补充的幽灵带着玩家的昵称、头像、Rating 和真实成绩，但没有逐个音符的记录，机台会按最终成绩模拟，和 NPC 的表现方式一样。机台之后上传了完整幽灵，就会替换它。</p>
        {#if status.last}<small class="subtle">上次补充：{status.last.finishedAt} · {status.last.players} 位玩家 · 写入 {status.last.written} 条 · 清理 {status.last.removed} 条</small>{/if}
      </div>
      <button class="btn-primary" type="button" disabled={seeding || status.running || !status.enabled} on:click={seed}>
        {#if seeding || status.running}<Spinner />{:else}<Icon icon="solar:refresh-circle-bold-duotone" />{/if}立即补充
      </button>
    </div>
  {:else}
    <p class="subtle">{loading ? "加载中…" : "无法读取"}</p>
  {/if}
</section>

<style lang="sass">
  .ghosts
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

  .hint
    margin: 0
    opacity: 0.7

  .kpis
    display: grid
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr))
    gap: 12px

    div
      display: grid
      gap: 4px
      padding: 14px
      border-radius: var(--radius)
      background: var(--surface-2)

    small
      color: var(--text-2)

    strong
      font-size: 1.4rem

  .panel
    display: flex
    align-items: flex-start
    justify-content: space-between
    gap: 16px

    p
      margin: 4px 0 0

    button
      flex: 0 0 auto

  @media (max-width: 760px)
    .panel
      flex-direction: column
</style>
