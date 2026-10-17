<!-- Renders the dialog requested through libs/confirm.ts -->
<script lang="ts">
  import Dialog from "./Dialog.svelte"
  import { confirmRequest } from "../../libs/confirm"
  import { t } from "../../libs/i18n"

  function answer(ok: boolean) {
    $confirmRequest?.resolve(ok)
    confirmRequest.set(null)
  }
</script>

{#if $confirmRequest}
  <Dialog title={$confirmRequest.title} size="sm" on:close={() => answer(false)}>
    {#if $confirmRequest.message}<p class="confirm-message">{$confirmRequest.message}</p>{/if}
    <svelte:fragment slot="footer">
      <button type="button" on:click={() => answer(false)}>{$confirmRequest.cancelText ?? t("action.cancel")}</button>
      <button type="button" class={$confirmRequest.danger ? "btn-danger-solid" : "btn-primary"} data-autofocus
              on:click={() => answer(true)}>{$confirmRequest.confirmText ?? t("action.confirm")}</button>
    </svelte:fragment>
  </Dialog>
{/if}

<style lang="sass">
  .confirm-message
    margin: 0
    color: var(--text-2)
    white-space: pre-line
</style>
