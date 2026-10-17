<script lang="ts">
  import Dialog from "./Dialog.svelte"
  import Icon from "./Icon.svelte"
  import { t } from "../../libs/i18n"
  import { createEventDispatcher } from "svelte"

  export let error: unknown

  const dispatch = createEventDispatcher<{ close: void }>()
  let dismissed = false
  let last: unknown
  $: if (error !== last) { last = error; dismissed = false }
  $: message = error instanceof Error ? error.message : String(error ?? "")

  function close() {
    dismissed = true
    dispatch("close")
  }
</script>

{#if !dismissed}
  <Dialog size="sm" showClose={false} on:close={close}>
    <div class="error-body">
      <span class="error-icon"><Icon icon="solar:danger-triangle-bold-duotone" /></span>
      <h2>{t("status.error")}</h2>
      <p>{message}</p>
    </div>
    <svelte:fragment slot="footer">
      <button type="button" class="btn-ghost" on:click={close}>{t("action.close")}</button>
      <a class="btn" href="/home">{t("action.home")}</a>
      <button type="button" class="btn-primary" data-autofocus on:click={() => location.reload()}>
        <Icon icon="solar:refresh-bold" />{t("action.refresh")}
      </button>
    </svelte:fragment>
  </Dialog>
{/if}

<style lang="sass">
  .error-body
    display: grid
    justify-items: center
    gap: 6px
    padding-top: 8px
    text-align: center

    h2
      margin: 4px 0 0
      font-size: 1.1rem

    p
      margin: 0
      color: var(--text-2)
      white-space: pre-line
      overflow-wrap: anywhere

  .error-icon
    display: grid
    place-items: center
    width: 52px
    height: 52px
    border-radius: 50%
    color: var(--danger)
    background: var(--danger-soft)
    font-size: 1.7rem
</style>
