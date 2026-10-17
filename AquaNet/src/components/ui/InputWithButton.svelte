<script lang="ts">
  import { fade } from "svelte/transition";
  import { ts } from "../../libs/i18n";
  import Spinner from "./Spinner.svelte";
  import { toast, errorMessage } from "../../libs/toast";

  export let field: {key: string, value: string, changed?: boolean};
  export let callback: () => Promise<boolean>;
  export let placeholder = "";

  let saving = false

  async function save() {
    if (saving || !field.changed) return
    saving = true
    try {
      if (await callback()) field.changed = false
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      saving = false
    }
  }
</script>

<form class="input-group" on:submit|preventDefault={save}>
  <input id={field.key} type="text" bind:value={field.value} {placeholder}
         on:input={() => field.changed = true}/>
  {#if field.changed}
    <button class="btn-primary" type="submit" disabled={saving} transition:fade={{ duration: 120 }}>
      {#if saving}<Spinner />{/if}{ts('settings.profile.save')}
    </button>
  {/if}
</form>
