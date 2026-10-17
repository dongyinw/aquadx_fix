<script lang="ts">
  import Spinner from "../ui/Spinner.svelte";
  import GameSettingFields from "./GameSettingFields.svelte";
  import { GAME, SETTING, USER } from "../../libs/sdk";
  import { download } from "../../libs/ui";
  import InputField from "../ui/InputField.svelte";
  import Icon from "../ui/Icon.svelte";
  import { t } from "../../libs/i18n";
  import StatusOverlays from "../StatusOverlays.svelte";

  let userNameField: any

  let submitting = "";
  let error: string
  let loading = true;

  USER.me().then(me =>
    GAME.userSummary(me.username, 'ongeki').then(async ({name}) => {
      userNameField = {key: "gameUsername", value: name, type: "String"}
      loading = false;
    }))
  

  function exportData() {
      submitting = "export"
      GAME.export('ongeki')
        .then(data => download(JSON.stringify(data), `MikuNet_ongeki_export_${userNameField.value}.json`))
        .catch(e => error = e.message)
        .finally(() => submitting = "")
    }
</script>

{#if !loading}
  <div class="game-settings">
    <div class="setting-group">
      <InputField bind:field={userNameField}
        callback={() => SETTING.detailSet('ongeki', 'userName', userNameField.value).then(() => true)}/>
    </div>
    <GameSettingFields game="ongeki"/>
    <div class="setting-section">
      <h3>数据导出</h3>
      <div class="row">
        <button type="button" on:click={exportData} disabled={!!submitting}>
          {#if submitting === "export"}<Spinner />{:else}<Icon icon="bxs:file-export"/>{/if}
          {t('settings.export')}
        </button>
      </div>
    </div>
  </div>
{:else if !error}
  <div class="stack" aria-hidden="true"><span class="skeleton" style="height:80px;border-radius:12px"></span></div>
{/if}

<StatusOverlays {error} />

<style lang="sass">
  .game-settings
    display: grid
    gap: 24px
</style>
