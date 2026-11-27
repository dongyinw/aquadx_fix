<!-- Server-wide switches: turn a feature on or off for every player. -->
<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../ui/Icon.svelte"
  import Spinner from "../ui/Spinner.svelte"
  import { FEATURES } from "../../libs/sdk"
  import type { FeatureSwitch } from "../../libs/sdk"
  import { errorMessage, toast } from "../../libs/toast"
  import { confirmDialog } from "../../libs/confirm"

  // What each switch does, in the words an admin needs.
  const details: Record<string, { icon: string, on: string, off: string }> = {
    rename: {
      icon: "solar:pen-new-square-bold-duotone",
      on: "玩家可以在网站设置里，或在机台上修改游戏昵称。",
      off: "玩家不能再改昵称：网站上的修改会被拒绝，机台上改的名字也不会被保存。管理员仍可在用户管理里修改。",
    },
    nameReview: {
      icon: "solar:user-check-rounded-bold-duotone",
      on: "玩家提交的新游戏名字需要管理员在「名称审核」里通过后才会生效；机台上改的名字不会被保存。",
      off: "玩家改名立即生效，不需要审核。已经提交、还没处理的申请仍可在「名称审核」里处理。",
    },
    customAvatar: {
      icon: "solar:user-circle-bold-duotone",
      on: "玩家可以上传自定义头像，并在游戏里把头像设为自定义头像。",
      off: "不能上传头像；游戏里不再显示自定义头像，也不能再选用（已选用的会被换回默认头像）。",
    },
    claim: {
      icon: "solar:hand-money-bold-duotone",
      on: "玩家可以使用「创哥理赔」补传成绩、修改舞里程。",
      off: "「创哥理赔」暂停使用：入口隐藏，提交会被拒绝。",
    },
    export: {
      icon: "solar:file-download-bold-duotone",
      on: "玩家可以导出自己的游戏存档。",
      off: "数据导出暂停使用：所有游戏的导出都会被拒绝。导入不受影响。",
    },
  }

  let items: FeatureSwitch[] = []
  let loading = false
  let saving = ""

  async function load() {
    loading = true
    try { items = await FEATURES.adminList() } catch (e) { toast.error(errorMessage(e)) } finally { loading = false }
  }

  async function toggle(item: FeatureSwitch) {
    // Turning something off affects every player at once, so ask first.
    if (item.key === "nameReview") {
      if (!item.enabled && !await confirmDialog({ title: `打开「${item.label}」`, message: "打开后，全服玩家改名都要经过审核才会生效。随时可以重新关闭。", confirmText: "打开" })) return
    } else if (item.enabled && !await confirmDialog({ title: `关闭「${item.label}」`, message: `关闭后，全服玩家都不能再使用「${item.label}」。随时可以重新打开。`, confirmText: "关闭", danger: true })) return
    saving = item.key
    try {
      items = await FEATURES.adminSet(item.key, !item.enabled)
      toast.success(item.enabled ? `已关闭「${item.label}」` : `已打开「${item.label}」`)
    } catch (e) { toast.error(errorMessage(e)) } finally { saving = "" }
  }

  onMount(load)
</script>

<section class="switches">
  <div class="toolbar">
    <h2>功能开关</h2>
    <button class="btn-ghost btn-sm" type="button" on:click={load} disabled={loading} title="刷新">{#if loading}<Spinner />{:else}<Icon icon="solar:refresh-bold" />{/if}</button>
  </div>
  <p class="hint">这些开关对全服玩家生效，保存后立刻起作用，不需要重启。</p>

  <div class="list">
    {#each items as item (item.key)}
      <div class="card item" class:off={!item.enabled}>
        <span class="tile-icon tone-blue"><Icon icon={details[item.key]?.icon ?? "solar:settings-bold-duotone"} /></span>
        <div class="copy">
          <strong>{item.label}</strong>
          <small>{item.enabled ? details[item.key]?.on : details[item.key]?.off}</small>
        </div>
        <label class="toggle">
          <span class="state">{item.enabled ? "已开启" : "已关闭"}</span>
          {#if saving === item.key}<Spinner />{:else}<input type="checkbox" class="switch" checked={item.enabled} disabled={!!saving} on:click|preventDefault={() => toggle(item)} aria-label={`${item.label}开关`} />{/if}
        </label>
      </div>
    {:else}
      <p class="subtle">{loading ? "加载中…" : "无法读取"}</p>
    {/each}
  </div>
</section>

<style lang="sass">
  .switches
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

  .list
    display: grid
    gap: 12px

  .item
    display: flex
    flex-direction: row
    align-items: center
    gap: 14px
    text-align: left

    &.off
      background: var(--surface-2)

    .copy
      display: grid
      gap: 3px
      flex: 1
      min-width: 0

      small
        color: var(--text-2)

  .toggle
    display: flex
    align-items: center
    gap: 10px
    flex: 0 0 auto

    .state
      color: var(--text-2)
      font-size: 0.86rem

  @media (max-width: 760px)
    .item
      align-items: flex-start
      flex-wrap: wrap

      .toggle
        margin-left: auto
</style>
