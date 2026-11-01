<!-- Admin console tab: create, edit, toggle and delete maimai DX tournaments (KOP). -->
<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../ui/Icon.svelte"
  import Spinner from "../ui/Spinner.svelte"
  import EmptyState from "../ui/EmptyState.svelte"
  import { confirmDialog } from "../../libs/confirm"
  import { errorMessage, toast } from "../../libs/toast"
  import { DATA, TOURNAMENT } from "../../libs/sdk"
  import type { Tournament, TournamentInput } from "../../libs/sdk"
  import type { AllMusic } from "../../libs/generalTypes"

  interface Form {
    isNew: boolean
    id: string
    name: string
    enabled: boolean
    rankingKind: number
    scoreType: number
    noticeStartDate: string
    noticeEndDate: string
    startDate: string
    endDate: string
    entryStartDate: string
    entryEndDate: string
    musicIds: string
    lockedMusicIds: string
  }

  let list: Tournament[] = []
  let music: AllMusic = {}
  let loading = true
  let saving = false
  let form: Form | null = null

  // Server dates are "yyyy-MM-dd HH:mm:ss"; <input type="datetime-local"> uses "yyyy-MM-ddTHH:mm[:ss]".
  const toInput = (s: string) => s ? s.replace(" ", "T") : ""
  const fromInput = (s: string) => s ? (s.length === 16 ? `${s}:00` : s).replace("T", " ") : ""
  const parseIds = (s: string) => s.split(/[\s,，]+/).filter(Boolean).map(Number)
  const songName = (id: number) => music[id]?.name ?? `#${id}`

  function status(t: Tournament): [string, string] {
    if (!t.enabled) return ["已停用", ""]
    if (t.expired) return ["已结束", "warning"]
    return ["进行中", "success"]
  }

  async function load() {
    loading = true
    try { list = await TOURNAMENT.adminList() }
    catch (e) { toast.error(errorMessage(e)) }
    finally { loading = false }
  }

  function create() {
    form = {
      isNew: true, id: String(Math.max(0, ...list.map(t => t.id)) + 1), name: "", enabled: true, rankingKind: 0, scoreType: 0,
      noticeStartDate: "", noticeEndDate: "", startDate: "", endDate: "", entryStartDate: "", entryEndDate: "",
      musicIds: "", lockedMusicIds: "",
    }
  }

  function edit(t: Tournament) {
    form = {
      ...t, isNew: false, id: String(t.id),
      noticeStartDate: toInput(t.noticeStartDate), noticeEndDate: toInput(t.noticeEndDate),
      startDate: toInput(t.startDate), endDate: toInput(t.endDate),
      entryStartDate: toInput(t.entryStartDate), entryEndDate: toInput(t.entryEndDate),
      musicIds: t.musicIds.join(", "), lockedMusicIds: t.lockedMusicIds.join(", "),
    }
  }

  const strip = ({ active: _a, expired: _e, ...t }: Tournament): TournamentInput => t

  async function save(input: TournamentInput, message: string) {
    saving = true
    try {
      await TOURNAMENT.adminSave(input)
      toast.success(message)
      await load()
      return true
    } catch (e) {
      toast.error(errorMessage(e))
      return false
    } finally {
      saving = false
    }
  }

  async function submit() {
    if (!form) return
    const id = Number(form.id)
    if (form.isNew && list.some(t => t.id === id) &&
      !await confirmDialog({ title: "覆盖大赛", message: `大赛 ID ${id} 已存在，保存会覆盖原有设置，继续吗？`, confirmText: "覆盖", danger: true })) return
    const input: TournamentInput = {
      id, name: form.name, enabled: form.enabled, rankingKind: form.rankingKind, scoreType: form.scoreType,
      noticeStartDate: fromInput(form.noticeStartDate), noticeEndDate: fromInput(form.noticeEndDate),
      startDate: fromInput(form.startDate), endDate: fromInput(form.endDate),
      entryStartDate: fromInput(form.entryStartDate), entryEndDate: fromInput(form.entryEndDate),
      musicIds: parseIds(form.musicIds), lockedMusicIds: parseIds(form.lockedMusicIds),
    }
    if (input.musicIds.some(n => !Number.isInteger(n)) || input.lockedMusicIds.some(n => !Number.isInteger(n)))
      return toast.error("曲目 ID 只能填写数字，用逗号分隔")
    if (await save(input, `已保存大赛「${input.name}」`)) form = null
  }

  const toggle = (t: Tournament) =>
    save({ ...strip(t), enabled: !t.enabled }, t.enabled ? `已停用「${t.name}」` : `已启用「${t.name}」`)

  async function remove(t: Tournament) {
    if (!await confirmDialog({ title: "删除大赛", message: `确定删除大赛「${t.name}」吗？游玩记录不会受影响，但排行榜将无法再查看。`, confirmText: "删除", danger: true })) return
    try {
      await TOURNAMENT.adminDelete(t.id)
      toast.success(`已删除「${t.name}」`)
      if (form && Number(form.id) === t.id) form = null
      await load()
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  onMount(() => {
    DATA.allMusic("mai2").then(m => music = m).catch(() => {})
    load()
  })

  $: preview = form ? parseIds(form.musicIds).filter(Number.isInteger) : []
</script>

<section class="card tournaments">
  <div class="card-header">
    <div>
      <h2>大赛管理</h2>
      <p>启用且未过期的大赛会下发到机台，并在网站导航中显示排行榜入口；公告结束时间（未填则为比赛结束时间）过后自动隐藏。</p>
    </div>
    <div class="row">
      <button class="btn-ghost btn-sm" type="button" on:click={load} disabled={loading}>{#if loading}<Spinner />{:else}<Icon icon="solar:refresh-bold" />{/if}刷新</button>
      <button class="btn-primary btn-sm" type="button" on:click={create}><Icon icon="solar:add-circle-bold" />新建大赛</button>
    </div>
  </div>

  {#if loading && !list.length}
    <span class="skeleton" style="height:120px;border-radius:12px"></span>
  {:else if !list.length}
    <EmptyState compact icon="solar:cup-star-bold-duotone" title="还没有大赛" description="点击「新建大赛」创建第一个大赛。" />
  {:else}
    <div class="table-wrap">
      <table class="data-table">
        <thead><tr><th>ID</th><th>名称</th><th>状态</th><th>比赛时间</th><th>课题曲</th><th>启用</th><th></th></tr></thead>
        <tbody>
          {#each list as t (t.id)}
            {@const [label, kind] = status(t)}
            <tr>
              <td class="num">{t.id}</td>
              <td><strong>{t.name}</strong></td>
              <td><span class="badge {kind}">{label}</span></td>
              <td class="mono dates">{t.startDate}<br />{t.endDate}</td>
              <td class="songs">{#each t.musicIds as id}<span class="truncate" title="#{id}">{songName(id)}</span>{/each}</td>
              <td><input class="switch" type="checkbox" checked={t.enabled} disabled={saving} on:click|preventDefault={() => toggle(t)} aria-label="启用 {t.name}" /></td>
              <td class="actions">
                <a class="btn btn-ghost btn-sm" href="/tournament?id={t.id}" title="查看排行榜"><Icon icon="solar:ranking-bold-duotone" /></a>
                <button class="btn-ghost btn-sm" type="button" title="编辑" on:click={() => edit(t)}><Icon icon="solar:pen-bold-duotone" /></button>
                <button class="btn-ghost btn-sm" type="button" title="删除" on:click={() => remove(t)}><Icon icon="solar:trash-bin-trash-bold-duotone" /></button>
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  {/if}
</section>

{#if form}
  <section class="card">
    <div class="card-header">
      <div><h2>{form.isNew ? "新建大赛" : `编辑大赛 #${form.id}`}</h2><p>时间按服务器时间计算；同一个 credit 内打完全部课题曲才计入成绩。</p></div>
    </div>
    <form class="tournament-form" on:submit|preventDefault={submit}>
      <label class="field"><span class="field-label">大赛 ID</span>
        <input type="number" min="1" required bind:value={form.id} disabled={!form.isNew} />
        <small class="field-hint">机台用来区分大赛，创建后不可修改</small>
      </label>
      <label class="field"><span class="field-label">名称</span><input required maxlength="255" bind:value={form.name} /></label>
      <label class="field"><span class="field-label">比赛开始</span><input type="datetime-local" step="1" required bind:value={form.startDate} /></label>
      <label class="field"><span class="field-label">比赛结束</span><input type="datetime-local" step="1" required bind:value={form.endDate} /></label>
      <label class="field"><span class="field-label">公告开始（可选）</span><input type="datetime-local" step="1" bind:value={form.noticeStartDate} /></label>
      <label class="field"><span class="field-label">公告结束（可选）</span><input type="datetime-local" step="1" bind:value={form.noticeEndDate} />
        <small class="field-hint">过后大赛从机台和网站隐藏；未填则使用比赛结束时间</small>
      </label>
      <label class="field"><span class="field-label">报名开始（可选）</span><input type="datetime-local" step="1" bind:value={form.entryStartDate} /></label>
      <label class="field"><span class="field-label">报名结束（可选）</span><input type="datetime-local" step="1" bind:value={form.entryEndDate} /></label>
      <label class="field wide"><span class="field-label">课题曲 ID</span>
        <input required placeholder="例如 11745, 11810, 12025" bind:value={form.musicIds} />
        {#if preview.length}<small class="field-hint">{preview.map(songName).join(" / ")}</small>{/if}
      </label>
      <label class="field wide"><span class="field-label">隐藏曲 ID（可选）</span>
        <input placeholder="需为课题曲之一" bind:value={form.lockedMusicIds} />
      </label>
      <label class="enable"><input class="switch" type="checkbox" bind:checked={form.enabled} />启用</label>
      <div class="form-actions">
        <button class="btn-ghost" type="button" on:click={() => form = null}>取消</button>
        <button class="btn-primary" type="submit" disabled={saving}>{#if saving}<Spinner />{:else}<Icon icon="solar:check-circle-bold" />{/if}保存</button>
      </div>
    </form>
  </section>
{/if}

<style lang="sass">
  @use "../../vars"

  .dates
    font-size: 0.8rem
    white-space: nowrap

  .songs
    max-width: 260px

    span
      display: block

  .actions
    white-space: nowrap
    text-align: right

  .tournament-form
    display: grid
    grid-template-columns: repeat(2, minmax(0, 1fr))
    gap: 14px 18px

    .wide
      grid-column: 1 / -1

  .enable
    display: flex
    align-items: center
    gap: 10px
    font-weight: 600

  .form-actions
    display: flex
    justify-content: flex-end
    gap: 8px

  @media (max-width: vars.$w-tablet)
    .tournament-form
      grid-template-columns: 1fr
</style>
