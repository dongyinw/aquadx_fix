<!-- Game event management: every event the cabinets receive, built-in and custom, with filters and quick switches. -->
<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../ui/Icon.svelte"
  import Spinner from "../ui/Spinner.svelte"
  import { MAI2_EVENT } from "../../libs/sdk"
  import type { Mai2Event, Mai2EventStatus, Mai2EventView } from "../../libs/sdk"
  import { errorMessage, toast } from "../../libs/toast"
  import { confirmDialog } from "../../libs/confirm"

  type Filter = "all" | Mai2EventStatus | "custom"

  const PAGE_SIZE = 20
  const statusText: Record<Mai2EventStatus, string> = { active: "进行中", upcoming: "未开始", ended: "已结束", permanent: "长期", disabled: "已停用" }
  const statusOrder: Record<Mai2EventStatus, number> = { active: 0, upcoming: 1, permanent: 2, disabled: 3, ended: 4 }
  const filters: { value: Filter, label: string }[] = [
    { value: "all", label: "全部" }, { value: "active", label: "进行中" }, { value: "upcoming", label: "未开始" },
    { value: "permanent", label: "长期" }, { value: "ended", label: "已结束" }, { value: "disabled", label: "已停用" }, { value: "custom", label: "自定义" },
  ]

  let events: Mai2EventView[] = []
  let filter: Filter = "all"
  let query = ""
  let page = 0
  let loading = false
  let saving = false
  let form: Mai2Event | null = null
  let creating = false

  // Cabinets use Beijing time, and so does this page.
  const beijing = (offsetDays = 0) => new Date(Date.now() + offsetDays * 86_400_000).toLocaleString("sv-SE", { timeZone: "Asia/Shanghai" }).replace(" ", "T")
  const inputDate = (date: string) => date.slice(0, 19).replace(" ", "T")
  const serverDate = (date: string) => (date.length === 16 ? `${date}:00` : date).replace("T", " ")

  $: counts = events.reduce((acc, event) => {
    acc.all++
    acc[event.status]++
    if (event.custom) acc.custom++
    return acc
  }, { all: 0, active: 0, upcoming: 0, ended: 0, permanent: 0, disabled: 0, custom: 0 } as Record<Filter, number>)
  $: filtered = events
    .filter(event => (filter === "all" || (filter === "custom" ? event.custom : event.status === filter)) && (!query.trim() || String(event.id).includes(query.trim())))
    .sort((a, b) => statusOrder[a.status] - statusOrder[b.status] || b.id - a.id)
  $: pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  $: if (page >= pages) page = pages - 1
  $: shown = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  async function load() {
    loading = true
    try { events = await MAI2_EVENT.overview() } catch (e) { toast.error(errorMessage(e)) } finally { loading = false }
  }

  function create() {
    creating = true
    form = { id: 0, type: 0, startDate: beijing(), endDate: beijing(14), enabled: true }
  }
  function edit(event: Mai2EventView) {
    creating = false
    form = { id: event.id, type: event.type, startDate: inputDate(event.startDate), endDate: inputDate(event.endDate), enabled: event.enabled }
  }
  /** Quick end dates: N days after the chosen start. */
  function endAfter(days: number) {
    if (!form) return
    const start = form.startDate ? new Date(`${form.startDate}${form.startDate.length === 16 ? ":00" : ""}Z`) : new Date()
    form.endDate = new Date(start.getTime() + days * 86_400_000).toISOString().slice(0, 19)
  }

  async function submit() {
    if (!form) return
    if (!Number.isInteger(Number(form.id)) || Number(form.id) <= 0) return toast.error("请输入有效活动 ID")
    if (creating && events.some(item => item.id === Number(form?.id))) return toast.error("活动 ID 已存在，请在列表中搜索后编辑")
    if (!form.startDate || !form.endDate || form.startDate >= form.endDate) return toast.error("请填写有效的开始和结束时间，结束必须晚于开始")
    saving = true
    try {
      await MAI2_EVENT.save({ ...form, id: Number(form.id), type: Number(form.type), startDate: serverDate(form.startDate), endDate: serverDate(form.endDate) })
      toast.success("活动已保存")
      form = null
      await load()
    } catch (e) { toast.error(errorMessage(e)) } finally { saving = false }
  }

  async function toggle(event: Mai2EventView) {
    saving = true
    try {
      await MAI2_EVENT.toggle(event.id, !event.enabled)
      toast.success(event.enabled ? "活动已停用，游戏不再收到它" : "活动已启用")
      await load()
    } catch (e) { toast.error(errorMessage(e)) } finally { saving = false }
  }

  async function remove(event: Mai2EventView) {
    if (!await confirmDialog({ title: "删除活动", message: `确定删除活动 ${event.id} 吗？删除后游戏将不再收到此活动。`, confirmText: "删除", danger: true })) return
    saving = true
    try {
      await MAI2_EVENT.delete(event.id)
      if (form?.id === event.id) form = null
      toast.success("活动已删除")
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
  <p class="hint">这里列出游戏会收到的全部活动，包括随游戏数据内置的和自己新增的。停用只对游戏生效，不会删除数据；活动时间按北京时间。</p>

  {#if form}
    <form class="editor card" on:submit|preventDefault={submit}>
      <h3>{creating ? "新增活动" : `编辑活动 ${form.id}`}</h3>
      <div class="fields">
        <label class="field"><span class="field-label">活动 ID</span><input type="number" min="1" max="2147483647" bind:value={form.id} disabled={!creating} required /></label>
        <label class="field"><span class="field-label">类型</span><input type="number" min="0" bind:value={form.type} required /><small class="field-hint">一般保持 0</small></label>
        <label class="field"><span class="field-label">开始时间</span><input type="datetime-local" step="1" bind:value={form.startDate} required /></label>
        <label class="field"><span class="field-label">结束时间</span><input type="datetime-local" step="1" bind:value={form.endDate} required /></label>
      </div>
      <div class="presets"><span class="subtle">结束时间：</span>
        {#each [[7, "7 天后"], [14, "14 天后"], [30, "30 天后"], [90, "90 天后"], [365, "1 年后"]] as [days, label]}
          <button class="btn-soft btn-sm" type="button" on:click={() => endAfter(Number(days))}>{label}</button>
        {/each}
      </div>
      <div class="actions"><button class="btn-primary" type="submit" disabled={saving}>{#if saving}<Spinner />{/if}保存</button><button class="btn-ghost" type="button" on:click={() => form = null}>取消</button></div>
    </form>
  {/if}

  <div class="chips" role="tablist" aria-label="活动状态">
    {#each filters as item}
      <button class="chip" class:selected={filter === item.value} type="button" role="tab" aria-selected={filter === item.value} on:click={() => { filter = item.value; page = 0 }}>
        {item.label}<span class="num">{counts[item.value] ?? 0}</span>
      </button>
    {/each}
  </div>
  <div class="search"><Icon icon="solar:magnifer-bold-duotone" /><input type="search" bind:value={query} on:input={() => page = 0} placeholder="搜索活动 ID" aria-label="搜索活动 ID" /><span class="subtle">{filtered.length} 项</span></div>

  <div class="list">
    {#each shown as event (event.id)}
      <article class="card item" class:off={!event.enabled}>
        <div class="head">
          <strong class="mono">{event.id}</strong>
          <span class="badge" class:success={event.status === "active"} class:warning={event.status === "upcoming"}>{statusText[event.status]}</span>
          <span class="badge" class:primary={event.custom}>{event.custom ? "自定义" : "内置"}</span>
          <small class="subtle">类型 {event.type}</small>
        </div>
        <span class="period">{event.status === "permanent" ? "长期有效" : `${event.startDate} → ${event.endDate}`}</span>
        <div class="actions">
          <label class="toggle" title={event.enabled ? "点击停用" : "点击启用"}>
            <input type="checkbox" class="switch" checked={event.enabled} disabled={saving} on:change={() => toggle(event)} />
            <span>{event.enabled ? "已启用" : "已停用"}</span>
          </label>
          <button class="btn-ghost btn-sm" type="button" disabled={saving} on:click={() => edit(event)} title="编辑活动" aria-label={`编辑活动 ${event.id}`}><Icon icon="solar:pen-bold-duotone" />编辑</button>
          {#if event.custom}
            <button class="btn-ghost btn-sm" type="button" disabled={saving} on:click={() => remove(event)} title="删除活动" aria-label={`删除活动 ${event.id}`}><Icon icon="solar:trash-bin-trash-bold-duotone" />删除</button>
          {/if}
        </div>
      </article>
    {:else}
      <p class="subtle">{loading ? "加载中…" : "没有符合条件的活动"}</p>
    {/each}
  </div>
  {#if pages > 1}
    <nav class="pager" aria-label="分页">
      <button class="btn-icon" type="button" aria-label="上一页" disabled={page <= 0} on:click={() => page--}><Icon icon="solar:alt-arrow-left-bold" /></button>
      <span class="num">{page + 1} / {pages}</span>
      <button class="btn-icon" type="button" aria-label="下一页" disabled={page + 1 >= pages} on:click={() => page++}><Icon icon="solar:alt-arrow-right-bold" /></button>
    </nav>
  {/if}
</section>

<style lang="sass">
  .events
    padding: 20px 0

  .toolbar, .search, .actions, .presets, .pager
    display: flex
    align-items: center
    gap: 12px

  .toolbar h2
    margin: 0 auto 0 0
    font-size: 20px

  .hint
    margin: 4px 0 16px
    opacity: 0.7

  .editor
    display: grid
    gap: 12px
    margin-bottom: 16px

    h3
      margin: 0

  .fields
    display: grid
    grid-template-columns: repeat(auto-fit, minmax(190px, 1fr))
    gap: 12px

  .presets
    flex-wrap: wrap
    gap: 8px

  .chips
    display: flex
    flex-wrap: wrap
    gap: 8px
    margin-bottom: 12px

  .chip
    display: inline-flex
    align-items: center
    gap: 6px
    padding: 6px 12px
    border: 1px solid var(--border)
    border-radius: 999px
    background: var(--surface)
    font-size: 0.86rem

    .num
      color: var(--text-3)
      font-size: 0.78rem

    &.selected
      border-color: var(--primary)
      color: var(--primary-soft-text)
      background: var(--primary-soft)

  .search
    margin: 0 0 14px

    input
      flex: 1
      min-width: 0

  .list
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr))
    gap: 10px

  .item
    display: grid
    gap: 8px
    padding: 12px 14px

    &.off
      opacity: 0.65

    .head
      display: flex
      align-items: center
      flex-wrap: wrap
      gap: 8px

      strong
        margin-right: auto
        font-size: 1.05rem

    .period
      color: var(--text-2)
      font-size: 0.86rem
      font-variant-numeric: tabular-nums

    .actions
      flex-wrap: wrap
      gap: 8px

  .toggle
    display: inline-flex
    align-items: center
    gap: 8px
    margin-right: auto
    font-size: 0.86rem

  .pager
    justify-content: center
    margin-top: 14px
</style>
