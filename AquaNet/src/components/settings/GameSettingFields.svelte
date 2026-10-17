<script lang="ts">
  import { SETTING } from "../../libs/sdk";
  import type { GameOption } from "../../libs/generalTypes";
  import InputField from "../ui/InputField.svelte";
  import { toast, errorMessage } from "../../libs/toast";
  import { t } from "../../libs/i18n";

  export let game: string;
  let gameFields: GameOption[] = []
  let loaded = false
  let submitting = ""

  SETTING.get().then(s => {
    gameFields = s.filter(it => it.game === game)
  }).catch(e => toast.error(errorMessage(e))).finally(() => loaded = true)

  async function submitGameOption(field: string, value: any) {
    if (submitting) return false
    submitting = field
    try {
      await SETTING.set(field, value)
      toast.success(t("settings.profile.save") + " ✓")
      return true
    } catch (e) {
      toast.error(errorMessage(e))
      return false
    } finally {
      submitting = ""
    }
  }
</script>

{#if !loaded}
  <div class="setting-group" aria-hidden="true">
    {#each [1, 2] as _}<div class="setting-row"><span class="skeleton" style="height:18px;width:60%"></span></div>{/each}
  </div>
{:else if gameFields.length}
  <div class="setting-group">
    {#each gameFields as field}
      <InputField field={field} callback={() => submitGameOption(field.key, field.value)}/>
    {/each}
  </div>
{/if}
