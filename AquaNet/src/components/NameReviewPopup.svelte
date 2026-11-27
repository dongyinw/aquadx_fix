<!-- Tells the player, once, how their name change requests were decided. -->
<script lang="ts">
  import { onMount } from "svelte"
  import Dialog from "./ui/Dialog.svelte"
  import Icon from "./ui/Icon.svelte"
  import { NAME_REVIEW } from "../libs/sdk"
  import type { NameNotice } from "../libs/sdk"
  import { refreshNameRequests } from "../libs/nameReview"

  const gameLabels: Record<string, string> = { mai2: "舞萌DX", chu3: "中二节奏", ongeki: "音击", wacca: "WACCA", diva: "DIVA" }

  let notices: NameNotice[] = []

  const time = (ms: number) => new Date(ms).toLocaleString("zh-CN", { hour12: false, year: "numeric", month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" })

  onMount(async () => {
    notices = await NAME_REVIEW.notices().catch(() => [])
  })

  function close() {
    const ids = notices.map(n => n.id)
    notices = []
    // Failing to record it only means the popup shows again next time.
    NAME_REVIEW.ack(ids).catch(() => {}).finally(refreshNameRequests)
  }
</script>

{#if notices.length}
  <Dialog title="改名申请结果" on:close={close}>
    <div class="results">
      {#each notices as n (n.id)}
        <div class="result" class:ok={n.approved}>
          <Icon icon={n.approved ? "solar:check-circle-bold-duotone" : "solar:close-circle-bold-duotone"} />
          <p>
            您在 {time(n.submittedAt)} 提交的{gameLabels[n.game] ?? n.game}更改名字申请（「{n.newName}」）{#if n.approved}<strong>已通过</strong>，新名字已生效。{:else}<strong>未通过</strong>{#if n.rejectReason}，原因：{n.rejectReason}{:else}。{/if}{/if}
          </p>
        </div>
      {/each}
    </div>
    <div class="actions"><button type="button" on:click={close}>知道了</button></div>
  </Dialog>
{/if}

<style lang="sass">
  .results
    display: grid
    gap: 12px

  .result
    display: flex
    gap: 10px
    align-items: flex-start
    color: var(--danger, #d33)

    &.ok
      color: var(--success, #2a9d5c)

    p
      margin: 0
      color: var(--text, inherit)

  .actions
    display: flex
    justify-content: flex-end
    margin-top: 16px
</style>
