<!-- Shared page status: confirm dialog (legacy ConfirmProps), fatal error dialog and loading state. -->
<script lang="ts">
  import type { ConfirmProps } from "../libs/generalTypes"
  import { t } from "../libs/i18n"
  import Loading from "./ui/Loading.svelte"
  import Error from "./ui/Error.svelte"
  import Dialog from "./ui/Dialog.svelte"

  export let confirm: ConfirmProps | null = null
  export let error: string | null = null
  export let loading: boolean = false

  // After a dismissed error the page usually never finishes loading; don't trap the user behind the spinner.
  let suppressLoading = false
  $: if (!loading) suppressLoading = false

  function closeError() {
    error = null
    suppressLoading = true
  }

  function doConfirm(fn?: () => void) {
    confirm = null
    fn && fn()
  }
</script>

{#if confirm}
  <Dialog title={confirm.title} size="sm" on:close={() => doConfirm(confirm?.cancel)}>
    <p class="confirm-message">{confirm.message}</p>
    <svelte:fragment slot="footer">
      {#if confirm.cancel}
        <button type="button" on:click={() => doConfirm(confirm?.cancel)}>{t("action.cancel")}</button>
      {/if}
      <button type="button" class={confirm.dangerous ? "btn-danger-solid" : "btn-primary"} data-autofocus
              on:click={() => doConfirm(confirm?.confirm)}>{t("action.confirm")}</button>
    </svelte:fragment>
  </Dialog>
{/if}

{#if error}
  <Error {error} on:close={closeError} />
{/if}

{#if loading && !error && !suppressLoading}
  <Loading />
{/if}

<style lang="sass">
  .confirm-message
    margin: 0
    color: var(--text-2)
    white-space: pre-line
</style>
