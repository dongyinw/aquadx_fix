<!-- Announcement management: notices shown to players in the bar under the page header. -->
<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../ui/Icon.svelte"
  import Spinner from "../ui/Spinner.svelte"
  import { ANNOUNCEMENTS } from "../../libs/sdk"
  import type { Announcement, AnnouncementInput, AnnouncementLevel } from "../../libs/sdk"
  import { errorMessage, toast } from "../../libs/toast"
  import { confirmDialog } from "../../libs/confirm"

  const levels: { value: AnnouncementLevel, label: string }[] = [
    { value: "info", label: "普通" }, { value: "warning", label: "提醒" }, { value: "danger", label: "重要" },
  ]

  let items: Announcement[] = []
  let loading = false
  let saving = false
  let form: AnnouncementInput | null = null

  // Times are Beijing time, the same clock the server uses to decide what is shown.
  const beijingNow = () => new Date().toLocaleString("sv-SE", { timeZone: "Asia/Shanghai" })
  const inputDate = (date: string) => date.slice(0, 16).replace(" ", "T")
  const serverDate = (date: string) => date ? `${date.replace("T", " ")}${date.length === 16 ? ":00" : ""}` : ""

  function status(item: Announcement) {
    if (!item.enabled) return { text: "已停用", tone: "" }
    if (item.active) return { text: "展示中", tone: "success" }
    return beijingNow() < item.startDate ? { text: "未开始", tone: "warning" } : { text: "已结束", tone: "" }
  }

  async function load() {
    loading = true
    try { items = await ANNOUNCEMENTS.adminList() } catch (e) { toast.error(errorMessage(e)) } finally { loading = false }
  }

  function create() { form = { id: 0, title: "", content: "", level: "info", enabled: true, startDate: "", endDate: "" } }
  function edit(item: Announcement) {
    form = { id: item.id, title: item.title, content: item.content, level: item.level, enabled: item.enabled, startDate: inputDate(item.startDate), endDate: inputDate(item.endDate) }
  }

  async function submit() {
    if (!form) return
    saving = true
    try {
      await ANNOUNCEMENTS.adminSave({ ...form, startDate: serverDate(form.startDate), endDate: serverDate(form.endDate) })
      toast.success("公告已保存")
      form = null
      await load()
    } catch (e) { toast.error(errorMessage(e)) } finally { saving = false }
  }

  async function toggle(item: Announcement) {
    saving = true
    try {
      await ANNOUNCEMENTS.adminSave({ id: item.id, title: item.title, content: item.content, level: item.level, enabled: !item.enabled, startDate: item.startDate, endDate: item.endDate })
      await load()
    } catch (e) { toast.error(errorMessage(e)) } finally { saving = false }
  }

  async function remove(item: Announcement) {
    if (!await confirmDialog({ title: "删除公告", message: `确定删除公告「${item.title}」吗？`, confirmText: "删除", danger: true })) return
    saving = true
    try {
      await ANNOUNCEMENTS.adminDelete(item.id)
      if (form?.id === item.id) form = null
      toast.success("公告已删除")
      await load()
    } catch (e) { toast.error(errorMessage(e)) } finally { saving = false }
  }

  onMount(load)
</script>

<section class="announcements">
  <div class="toolbar">
    <h2>公告管理</h2>
    <button class="btn-ghost btn-sm" type="button" on:click={load} disabled={loading} title="刷新">{#if loading}<Spinner />{:else}<Icon icon="solar:refresh-bold" />{/if}</button>
    <button class="btn-primary btn-sm" type="button" on:click={create}><Icon icon="solar:add-circle-bold" />发布公告</button>
  </div>
  <p class="hint">公告会显示在网站每个页面顶部（最多同时展示最新的 5 条），玩家可以关闭看过的公告。</p>

  {#if form}
    <form class="editor card" on:submit|preventDefault={submit}>
      <h3>{form.id ? "编辑公告" : "发布公告"}</h3>
      <label class="field"><span class="field-label">标题</span><input required maxlength="100" bind:value={form.title} placeholder="例如：服务器维护通知" /></label>
      <label class="field"><span class="field-label">内容</span><textarea required rows="5" maxlength="2000" bind:value={form.content} placeholder="公告正文，可以换行"></textarea><small class="field-hint">{form.content.length} / 2000</small></label>
      <div class="fields">
        <label class="field"><span class="field-label">级别</span>
          <select bind:value={form.level}>{#each levels as level}<option value={level.value}>{level.label}</option>{/each}</select>
        </label>
        <label class="field"><span class="field-label">开始展示（可不填）</span><input type="datetime-local" bind:value={form.startDate} /></label>
        <label class="field"><span class="field-label">结束展示（可不填）</span><input type="datetime-local" bind:value={form.endDate} /></label>
      </div>
      <small class="field-hint">时间按北京时间计算；不填表示立即展示、长期展示。</small>
      <label class="switch-row"><span><strong>启用</strong><small>关闭后公告不再展示，但会保留在这里</small></span><input type="checkbox" class="switch" bind:checked={form.enabled} /></label>
      <div class="preview notice-{form.level}" aria-label="展示效果预览">
        <Icon icon="solar:bell-bing-bold-duotone" /><p><strong>{form.title || "标题"}</strong>{form.content || "公告内容"}</p>
      </div>
      <div class="actions"><button class="btn-primary" type="submit" disabled={saving}>{#if saving}<Spinner />{/if}保存</button><button class="btn-ghost" type="button" on:click={() => form = null}>取消</button></div>
    </form>
  {/if}

  <div class="list">
    {#each items as item (item.id)}
      {@const state = status(item)}
      <article class="card item">
        <div class="head">
          <strong>{item.title}</strong>
          <span class="badge" class:success={state.tone === "success"} class:warning={state.tone === "warning"}>{state.text}</span>
          <span class="badge level-{item.level}">{levels.find(level => level.value === item.level)?.label}</span>
        </div>
        <p class="content">{item.content}</p>
        <small class="subtle">展示时间：{item.startDate || "立即"} 至 {item.endDate || "长期"} · 更新于 {item.updatedAt}</small>
        <div class="actions">
          <button class="btn-ghost btn-sm" type="button" disabled={saving} on:click={() => edit(item)}><Icon icon="solar:pen-bold-duotone" />编辑</button>
          <button class="btn-ghost btn-sm" type="button" disabled={saving} on:click={() => toggle(item)}>{item.enabled ? "停用" : "启用"}</button>
          <button class="btn-ghost btn-sm" type="button" disabled={saving} on:click={() => remove(item)}><Icon icon="solar:trash-bin-trash-bold-duotone" />删除</button>
        </div>
      </article>
    {:else}
      <p class="subtle">{loading ? "加载中…" : "还没有公告，点击右上角「发布公告」创建一条。"}</p>
    {/each}
  </div>
</section>

<style lang="sass">
  .announcements
    padding: 20px 0

  .toolbar, .actions
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
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr))
    gap: 12px

  .preview
    display: flex
    align-items: center
    gap: 10px
    padding: 8px 14px
    border-radius: var(--radius)
    background: var(--primary-soft)

    p
      flex: 1
      margin: 0
      font-size: 0.86rem
      white-space: pre-line

    strong
      margin-right: 8px

    &.notice-warning
      background: var(--warning-soft)

    &.notice-danger
      background: color-mix(in srgb, var(--danger) 12%, transparent)

  .list
    display: grid
    gap: 12px

  .item
    display: grid
    gap: 8px

    .head
      display: flex
      align-items: center
      flex-wrap: wrap
      gap: 8px

      strong
        margin-right: auto

    .content
      margin: 0
      white-space: pre-line
      overflow-wrap: anywhere

    .actions
      flex-wrap: wrap
      gap: 8px
</style>
