<script lang="ts">
  import { fade } from "svelte/transition";
  import Dialog from "../ui/Dialog.svelte";
  import Icon from "../ui/Icon.svelte";
  import Spinner from "../ui/Spinner.svelte";
  import { CHU3_MATCHINGS } from "../../libs/config.js";
  import type { ChusanMatchingOption, GameOption } from "../../libs/generalTypes.js";
  import { t, ts } from "../../libs/i18n.js";
  import { DATA, GAME, SETTING, USER } from "../../libs/sdk.js";
  import StatusOverlays from "../StatusOverlays.svelte";
  import GameSettingFields from "./GameSettingFields.svelte";
  import { EN_REF } from "../../libs/i18n/en_ref.js";

  let custom = false
  let overlay = false
  let loading = false
  let error = ""

  let changed: string[] = [];
  let symbols: Record<number, number> = {};
  let allItems: Record<string, Record<string, { name: string }>> = {}
  let submitting: string | undefined | null;
  let settings: Record<string, GameOption> = {};

  let existingUrl = "";
  SETTING.get().then(s => {
    existingUrl = s.filter(it => it.key === 'chusanMatchingServer')[0]?.value

    if (existingUrl && !CHU3_MATCHINGS.some(it => it.matching === existingUrl)) {
      custom = true
    }

    const symbolKey = "chusanSymbolChat"
    s.forEach(opt => {
      if (opt.key.substring(0, symbolKey.length) == symbolKey && opt.value)
        symbols[parseInt(opt.key.substring(symbolKey.length))] = opt.value;
    })
    settings = Object.fromEntries(s.map(v => [v.key, v]))
  })

  async function fetchSymbolData() {
    allItems = await DATA.allItems('chu3').catch(_ => {
      loading = false
      error = t("userbox.error.nodata")
    }) as typeof allItems
  }

  async function submitSymbol(id: number) {
    if (submitting) return false
    const field = `chusanSymbolChat${id + 1}`;
    submitting = field

    await SETTING.set(field, symbols[id + 1]).catch(e => error = e.message).finally(() => submitting = null);
    changed = changed.filter(v => v != `chusanSymbolChat${id}`)
    return true
  }

  async function updateLvDifficulty() {
    if (submitting) return false
    submitting = `chusanLvDifficulty`
    await SETTING.set("chusanLvDifficulty", settings["chusanLvDifficulty"].value).catch(e => error = e.message).finally(() => submitting = null);
    changed = changed.filter(v => v != `chusanLvDifficulty`)
    return true
  }

  // Click on "Custom" option"
  function clickCustom() {
    custom = true
    overlay = false
  }

  // Click on a matching option, set the reflector and matching server
  function clickOption(opt: ChusanMatchingOption) {
    Promise.all([
      SETTING.set('chusanMatchingReflector', opt.reflector),
      SETTING.set('chusanMatchingServer', opt.matching),
    ]).then(() => {
      overlay = false
      custom = false
      existingUrl = opt.matching
    }).catch(e => error = e.message)
  }

  let linkedVerseAvailable = false;
  USER.me().then(async me => {
    let summary = await GAME.userSummary(me.username, "chu3")
    let version = summary.lastVersion.split(".");
    if (version[0] == "2" && parseInt(version[1]) >= 40)
      linkedVerseAvailable = true;
  })
</script>

<StatusOverlays {error} {loading}/>

<div class="setting-section">
  <h3>{t("userbox.header.matching")}{linkedVerseAvailable ? ` & ${t("userbox.header.linkedVerse")}` : ""}</h3>
  <div class="alert alert-info">
    <Icon icon="solar:info-circle-bold-duotone" />
    <p>{t("settings.cabNotice")}{#if linkedVerseAvailable}&nbsp;{t("userbox.lv.diffnotice")}{/if}</p>
  </div>

  {#if linkedVerseAvailable}
    <GameSettingFields game="chu3-linked-verse" />
    {#if settings["chusanLvDifficulty"]}
      <div class="setting-group">
        <div class="setting-row stacked">
          <label class="setting-text" for="chusanLvDifficulty">
            <span class="name">{ts(`userbox.lv.difficulty`)}</span>
            <a class="desc" target="_blank" rel="noopener" href="https://silentblue.remywiki.com/CHUNITHM:Linked_VERSE">{t(`userbox.lv.help`)}</a>
          </label>
          <div class="input-group">
            <select bind:value={settings["chusanLvDifficulty"].value} id="chusanLvDifficulty" on:change={() => {changed = [...changed, `chusanLvDifficulty`];}}>
              {#each {length: 6}, i}
                <option value={i}>{t(`userbox.lv.difficulty.${i}` as keyof typeof EN_REF)}</option>
              {/each}
            </select>
            {#if changed.includes(`chusanLvDifficulty`)}
              <button class="btn-primary" type="button" transition:fade={{ duration: 120 }} disabled={!!submitting} on:click={updateLvDifficulty}>
                {#if submitting === "chusanLvDifficulty"}<Spinner />{/if}{t("settings.profile.save")}
              </button>
            {/if}
          </div>
        </div>
      </div>
    {/if}
  {/if}

  <div class="setting-group">
    <div class="setting-row">
      <div class="setting-text">
        <span class="name">{t('userbox.matching.select')}</span>
        <span class="desc">{custom ? t('userbox.matching.custom.name') : (CHU3_MATCHINGS.find(o => o.matching === existingUrl)?.name ?? "—")}</span>
      </div>
      <button type="button" on:click={_ => overlay = true}><Icon icon="solar:server-bold-duotone" />{t('userbox.matching.select')}</button>
    </div>
  </div>

  {#if custom}
    <GameSettingFields game="chu3-matching"/>
  {/if}
</div>

<div class="setting-section">
  <h3>{t("userbox.header.matching.symbolChat")}</h3>
  {#await fetchSymbolData()}
    <span class="skeleton" style="height:120px;border-radius:12px"></span>
  {:then}
    <div class="setting-group">
      {#each {length: 4}, i}
        <div class="setting-row stacked">
          <label class="setting-text" for={`chusanSymbolChat${i}`}><span class="name">{ts(`userbox.matching.symbolChat`) + ` #${i + 1}`}</span></label>
          <div class="input-group">
            <select bind:value={symbols[i + 1]} id={`chusanSymbolChat${i}`} on:change={() => {changed = [...changed, `chusanSymbolChat${i}`];}}>
              <option value={null}>{ts(`userbox.matching.symbolChat.default`)}</option>
              {#each Object.entries(allItems.symbolChat).filter((f) => parseInt(f[0]) !== 0) as [id, option]}
                <option value={parseInt(id)}>{option?.name || `(unknown ${id})`}</option>
              {/each}
            </select>
            {#if changed.includes(`chusanSymbolChat${i}`)}
              <button class="btn-primary" type="button" transition:fade={{ duration: 120 }} disabled={!!submitting} on:click={() => submitSymbol(i)}>
                {#if submitting === `chusanSymbolChat${i + 1}`}<Spinner />{/if}{t("settings.profile.save")}
              </button>
            {/if}
          </div>
        </div>
      {/each}
    </div>
  {/await}
</div>

{#if overlay}
  <Dialog title={t('userbox.header.matching')} description={t('userbox.matching.select.sub')} size="lg" on:close={() => overlay = false}>
    <div class="options">
      <!-- Selectable options -->
      {#each CHU3_MATCHINGS as option}
        <div class="option" class:selected={!custom && existingUrl === option.matching}>
          <button class="option-main" type="button" on:click={() => clickOption(option)} aria-pressed={!custom && existingUrl === option.matching}>
            <span class="name">{option.name}</span>
            {#if !custom && existingUrl === option.matching}<span class="badge primary">当前</span>{/if}
          </button>
          <div class="links">
            <a href={option.ui} target="_blank" rel="noopener">{t('userbox.matching.option.ui')}</a>
            <span aria-hidden="true">·</span>
            <a href={option.guide} target="_blank" rel="noopener">{t('userbox.matching.option.guide')}</a>
          </div>
          <div class="coop">
            <span>{t('userbox.matching.option.collab')}</span>
            <div>{#each option.coop as coop}<span class="badge">{coop}</span>{/each}</div>
          </div>
        </div>
      {/each}

      <!-- Placeholder option for "Custom" -->
      <div class="option" class:selected={custom}>
        <button class="option-main" type="button" on:click={clickCustom} aria-pressed={custom}>
          <span class="name">{t('userbox.matching.custom.name')}</span>
          {#if custom}<span class="badge primary">当前</span>{/if}
        </button>
        <p class="subtle">{t('userbox.matching.custom.sub')}</p>
      </div>
    </div>
  </Dialog>
{/if}

<style lang="sass">
  .options
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr))
    gap: 12px

  .option
    display: grid
    align-content: start
    gap: 10px
    padding: 14px
    border: 1px solid var(--border)
    border-radius: var(--radius)

    &.selected
      border-color: var(--primary)
      background: var(--primary-soft)

    p
      margin: 0
      font-size: 0.85rem

  .option-main
    justify-content: space-between
    width: 100%

    .name
      font-size: 1rem
      font-weight: 700

  .links
    display: flex
    gap: 6px
    font-size: 0.85rem

  .coop
    display: grid
    gap: 6px
    color: var(--text-2)
    font-size: 0.8rem

    div
      display: flex
      flex-wrap: wrap
      gap: 4px
</style>
