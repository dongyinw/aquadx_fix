<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../ui/Icon.svelte"
  import Spinner from "../ui/Spinner.svelte"
  import { MAI2_EVENT } from "../../libs/sdk"
  import type { Mai2Event } from "../../libs/sdk"
  import { errorMessage, toast } from "../../libs/toast"

  let events: Mai2Event[] = []
  let query = ""
  let loading = false
  let saving = false
  let form: Mai2Event | null = null
  let creating = false
  const inputDate = (date: string) => date.slice(0, 19).replace(" ", "T")
  const serverDate = (date: string) => (date.length === 16 ? `${date}:00` : date).replace("T", " ")
  $: filtered = events.filter(event => String(event.id).includes(query.trim()))

  async function load() {
    loading = true
    try { events = await MAI2_EVENT.list() } catch (e) { toast.error(errorMessage(e)) } finally { loading = false }
  }
  function edit(event: Mai2Event) {
    creating = false
    form = { ...event, startDate: inputDate(event.startDate), endDate: inputDate(event.endDate) }
  }
  function create() {
    creating = true
    form = { id: 0, type: 0, startDate: "", endDate: "", enabled: true }
  }
  async function submit() {
    if (!form) return
    if (!Number.isInteger(Number(form.id)) || Number(form.id) <= 0) return toast.error("请输入有效活动 ID")
    if (creating && events.some(item => item.id === Number(form?.id))) return toast.error("活动 ID 已存在，请搜索后编辑")
    if (!form.startDate || !form.endDate || form.startDate >= form.endDate) return toast.error("请填写有效的开始和结束时间")
    saving = true
    try {
      await MAI2_EVENT.save({ ...form, id: Number(form.id), type: Number(form.type), startDate: serverDate(form.startDate), endDate: serverDate(form.endDate) })
      toast.success("活动已保存")
      form = null
      await load()
    } catch (e) { toast.error(errorMessage(e)) } finally { saving = false }
  }
  onMount(load)
</script>

<section class="events">
  <div class="toolbar">
    <h2>活动管理</h2>
    <button class="btn-ghost btn-sm" type="button" on:click={load} disabled={loading} title="刷新">{#if loading}<Spinner />{:else}<Icon icon="solar:refresh-bold" />{/if}</button>
    <button class="btn-primary btn-sm" type="button" on:click={create}><Icon icon="solar:add-circle-bold" />新增活动</button>
  </div>
  {#if form}
    <form class="editor" on:submit|preventDefault={submit}>
      <h3>{creating ? "新增活动" : `编辑活动 ${form.id}`}</h3>
      <div class="fields">
        <label>活动 ID<input type="number" min="1" max="2147483647" bind:value={form.id} disabled={!creating} required /></label>
        <label>类型<input type="number" min="0" bind:value={form.type} required /></label>
        <label>开始时间<input type="datetime-local" step="1" bind:value={form.startDate} required /></label>
        <label>结束时间<input type="datetime-local" step="1" bind:value={form.endDate} required /></label>
        <label class="check"><input type="checkbox" bind:checked={form.enabled} />启用</label>
      </div>
      <div class="actions"><button class="btn-primary" type="submit" disabled={saving}>保存</button><button class="btn-ghost" type="button" on:click={() => form = null}>取消</button></div>
    </form>
  {/if}
  <div class="search"><Icon icon="solar:magnifer-bold-duotone" /><input type="search" bind:value={query} placeholder="搜索活动 ID" aria-label="搜索活动 ID" /><span>{filtered.length} 项</span></div>
  <div class="table-wrap"><table class="data-table"><thead><tr><th>活动 ID</th><th>类型</th><th>开始时间</th><th>结束时间</th><th>状态</th><th></th></tr></thead><tbody>
    {#each filtered.slice(0, 100) as event (event.id)}
      <tr><td>{event.id}</td><td>{event.type}</td><td>{event.startDate}</td><td>{event.endDate}</td><td>{event.enabled ? "启用" : "停用"}</td><td><button class="btn-ghost btn-sm" type="button" on:click={() => edit(event)} title="编辑活动" aria-label={`编辑活动 ${event.id}`}><Icon icon="solar:pen-bold" /></button></td></tr>
    {:else}<tr><td colspan="6">{loading ? "加载中" : "没有匹配的活动"}</td></tr>{/each}
  </tbody></table></div>
  {#if filtered.length > 100}<p>显示前 100 项，请使用活动 ID 搜索。</p>{/if}
</section>

<style>
  .events { padding: 20px 0; } .toolbar,.actions,.search { display:flex; align-items:center; gap:12px; } .toolbar h2 { margin:0 auto 0 0; font-size:20px; }
  .editor { padding:16px 0; border-bottom:1px solid var(--border-color, #d8dedf); } .editor h3 { font-size:16px; } .fields { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:12px; }
  label { display:flex; flex-direction:column; gap:6px; } .check { flex-direction:row; align-items:center; } .actions,.search { margin:16px 0; } .search input { flex:1; min-width:0; } .table-wrap { overflow:auto; } table { width:100%; } td,th { white-space:nowrap; }
</style>
