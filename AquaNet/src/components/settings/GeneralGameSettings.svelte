<script lang="ts">
  import { t, ts } from "../../libs/i18n";
  import useLocalStorage from "../../libs/hooks/useLocalStorage.svelte";
  import RegionSelector from "./RegionSelector.svelte";
  import { USER } from "../../libs/sdk";
  import type { MikuNetUser } from "../../libs/generalTypes";
  import StatusOverlays from "../StatusOverlays.svelte";
  import Icon from "../ui/Icon.svelte";
  import { toast, errorMessage } from "../../libs/toast";

  const rounding = useLocalStorage("rounding", true);
  // Local mirror so the switch reflects the stored value reactively.
  let roundingValue = rounding.value;
  $: rounding.value = roundingValue;

  let me: MikuNetUser;
  let submitting = "";
  let error = "";
  let loading = true;

  USER.me().then((m) => {
    me = m;
    loading = false;
  }).catch(e => error = e.message)

  function submit(field: string, value: string) {
    if (submitting) return
    submitting = field

    USER.setting(field, value).then(() => {
      toast.success(t("settings.profile.save") + " ✓")
    }).catch(e => toast.error(errorMessage(e))).finally(() => submitting = "")
  }
</script>

{#if !loading}
  <div class="general">
    <div class="setting-section">
      <h3>网站显示</h3>
      <div class="alert alert-info"><Icon icon="solar:info-circle-bold-duotone" /><p>{ts("settings.siteNotice")}</p></div>
      <div class="setting-group">
        <div class="setting-row">
          <label class="setting-text" for="rounding">
            <span class="name">{ts(`settings.fields.rounding.name`)}</span>
            <span class="desc">{ts(`settings.fields.rounding.desc`)}</span>
          </label>
          <input id="rounding" type="checkbox" class="switch" bind:checked={roundingValue}/>
        </div>
        <div class="setting-row">
          <label class="setting-text" for="displayCandidates">
            <span class="name">{ts(`settings.fields.displayCandidates.name`)}</span>
            <span class="desc">{ts(`settings.fields.displayCandidates.desc`)}</span>
          </label>
          <input id="displayCandidates" type="checkbox" class="switch" bind:checked={me.displayCandidates} disabled={submitting === 'displayCandidates'}
            on:change={() => submit('displayCandidates', me.displayCandidates.toString())}/>
        </div>
      </div>
    </div>

    <div class="setting-section">
      <h3>游戏地区</h3>
      <div class="alert alert-info"><Icon icon="solar:info-circle-bold-duotone" /><p>{ts("settings.regionNotice")}</p></div>
      <RegionSelector/>
    </div>
  </div>
{:else if !error}
  <div class="stack" aria-hidden="true"><span class="skeleton" style="height:200px;border-radius:12px"></span></div>
{/if}

<StatusOverlays {error} />

<style lang="sass">
  .general
    display: grid
    gap: 24px
</style>
