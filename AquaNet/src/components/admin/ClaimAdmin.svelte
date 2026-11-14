<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../ui/Icon.svelte"
  import Spinner from "../ui/Spinner.svelte"
  import { ADMIN } from "../../libs/sdk"
  import type { ClaimLogEntry } from "../../libs/generalTypes"
  import { errorMessage, toast } from "../../libs/toast"

  let entries: ClaimLogEntry[] = []
  let query = ""
  let kind: "" | "score" | "mile" = ""
  let loading = false

  $: filtered = entries.filter(entry => (!kind || entry.kind === kind)
    && (!query.trim() || `${entry.userName} ${entry.userId ?? ""} ${entry.detail}`.toLowerCase().includes(query.trim().toLowerCase())))

  async function load() {
    loading = true
    try { entries = await ADMIN.claimLog() } catch (e) { toast.error(errorMessage(e)) } finally { loading = false }
  }

  onMount(load)
</script>

<section class="claims">
  <div class="toolbar">
    <h2>理赔记录</h2>
    <button class="btn-ghost btn-sm" type="button" on:click={load} disabled={loading} title="刷新">{#if loading}<Spinner />{:else}<Icon icon="solar:refresh-bold" />{/if}</button>
  </div>
  <p class="hint">玩家通过「创哥理赔」补传成绩、修改舞里程的最近 300 条记录。发现滥用可在用户管理里封禁对应账号。</p>
  <div class="search">
    <Icon icon="solar:magnifer-bold-duotone" /><input type="search" bind:value={query} placeholder="搜索玩家名、用户 ID 或内容" aria-label="搜索理赔记录" />
    <select bind:value={kind} aria-label="类型"><option value="">全部</option><option value="score">补传成绩</option><option value="mile">舞里程</option></select>
    <span>{filtered.length} 条</span>
  </div>
  <div class="table-wrap"><table class="data-table stack"><thead><tr><th>时间</th><th>玩家</th><th>类型</th><th>内容</th><th>修改前</th><th>修改后</th></tr></thead><tbody>
    {#each filtered as entry (entry.id)}
      <tr><td data-label="时间">{entry.createdAt}</td><td data-label="玩家">{entry.userName}{#if entry.userId}<small> #{entry.userId}</small>{/if}</td><td data-label="类型">{entry.kind === "score" ? "补传成绩" : "舞里程"}</td><td data-label="内容" class="detail">{entry.detail}</td><td data-label="修改前">{entry.oldValue}</td><td data-label="修改后">{entry.newValue}</td></tr>
    {:else}<tr><td colspan="6" data-label="">{loading ? "加载中" : "暂无记录"}</td></tr>{/each}
  </tbody></table></div>
</section>

<style>
  .claims { padding: 20px 0; } .toolbar,.search { display:flex; align-items:center; gap:12px; } .toolbar h2 { margin:0 auto 0 0; font-size:20px; }
  .hint { margin: 4px 0 0; opacity: .7; } .search { margin:16px 0; } .search input { flex:1; min-width:0; } .table-wrap { overflow:auto; } table { width:100%; } td,th { white-space:nowrap; } .detail { white-space: normal; min-width: 240px; }
</style>
