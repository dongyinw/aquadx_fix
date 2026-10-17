<script lang="ts">
  import { USER } from "../../libs/sdk";
  import { ts, t } from "../../libs/i18n";
  import Spinner from "../ui/Spinner.svelte";
  import { toast, errorMessage } from "../../libs/toast";
  let regionId = $state(0);
  let submitting = $state("")

  const prefectures = ["None","Aichi","Aomori","Akita","Ishikawa","Ibaraki","Iwate","Ehime","Oita","Osaka","Okayama","Okinawa","Kagawa","Kagoshima","Kanagawa","Gifu","Kyoto","Kumamoto","Gunma","Kochi","Saitama","Saga","Shiga","Shizuoka","Shimane","Chiba","Tokyo","Tokushima","Tochigi","Tottori","Toyama","Nagasaki","Nagano","Nara","Niigata","Hyogo","Hiroshima","Fukui","Fukuoka","Fukushima","Hokkaido","Mie","Miyagi","Miyazaki","Yamagata","Yamaguchi","Yamanashi","Wakayama"]

  USER.me().then(user => {
    const parsedRegion = parseInt(user.region);
    if (!isNaN(parsedRegion) && parsedRegion > 0) {
      regionId = parsedRegion - 1;
    } else {
      regionId = 0;
    }
  })

  async function saveNewRegion() {
    if (submitting) return false
    submitting = "region"

    await USER.changeRegion(regionId+1)
      .then(() => toast.success(t("settings.profile.save") + " ✓"))
      .catch(e => toast.error(errorMessage(e)))
      .finally(() => submitting = "")
    return true
  }
</script>

<div class="setting-group">
  <div class="setting-row stacked">
    <label class="setting-text" for="region-select">
      <span class="name">{ts(`settings.regionSelector.title`)}</span>
      <span class="desc">{ts(`settings.regionSelector.desc`)}</span>
    </label>
    <div class="input-group">
      <select id="region-select" bind:value={regionId} onchange={saveNewRegion} disabled={!!submitting}>
        <option value={0} disabled selected>{ts("settings.regionSelector.select")}</option>
        {#each prefectures.slice(1) as prefecture, index}
          <option value={index}>{prefecture}</option>
        {/each}
      </select>
      {#if submitting}<span class="saving"><Spinner /></span>{/if}
    </div>
  </div>
</div>

<style lang="sass">
  .saving
    display: grid
    place-items: center
    width: 40px
    color: var(--primary)
</style>
