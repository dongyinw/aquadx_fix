<script lang="ts">
  import {
    type MikuNetUser,
    type GameUserOption,
    type UserBox,
    type UserItem,
  } from "../../libs/generalTypes";
  import { DATA, USER, USERBOX, GAME, SETTING } from "../../libs/sdk";
  import { t, ts } from "../../libs/i18n";
  import { USERBOX_DEFAULT_URL } from "../../libs/config";
  import { fade } from "svelte/transition";
  import Dialog from "../ui/Dialog.svelte";
  import Spinner from "../ui/Spinner.svelte";
  import StatusOverlays from "../StatusOverlays.svelte";
  import Icon from "../ui/Icon.svelte";
  import GameSettingFields from "./GameSettingFields.svelte";

  import { userboxFileProcess, ddsDB, initializeDb } from "../../libs/userbox/userbox"

  import ChuniPenguinComponent from "./userbox/ChuniPenguin.svelte"
  import ChuniUserplateComponent from "./userbox/ChuniUserplate.svelte";

  import useLocalStorage from "../../libs/hooks/useLocalStorage.svelte";
  import { DDS } from "../../libs/userbox/dds";
  import ChuniMatchingSettings from "./ChuniMatchingSettings.svelte";
  import InputField from "../ui/InputField.svelte";
  import UserOptionSlider from "./UserOptionSlider.svelte";

  let user: MikuNetUser
  let [loading, error, submitting, preview] = [true, "", "", ""]
  let changed: string[] = [];

  let userOptions: GameUserOption = {};
  let userIsUsingPreset = false;

  // Available (unlocked) options for each kind of item
  // In allItems: 'namePlate', 'frame', 'trophy', 'mapIcon', 'systemVoice', 'avatarAccessory'
  let allItems: Record<string, Record<string, { name: string }>> = {}
  let iKinds = { namePlate: 1, frame: 2, trophy: 3, trophySub1: 4, trophySub2: 5, mapIcon: 8, systemVoice: 9, avatarAccessory: 11, stage: 13 }
  // In userbox: 'nameplateId', 'frameId', 'trophyId', 'mapIconId', 'voiceId', 'avatar{Wear/Head/Face/Skin/Item/Front/Back}'
  let userbox: UserBox
  let avatarKinds = ['Wear', 'Head', 'Face', 'Skin', 'Item', 'Front', 'Back'] as const
  // iKey should match allItems keys, and ubKey should match userbox keys
  let userItems: { iKey: string, ubKey: keyof UserBox, items: UserItem[] }[] = []
  let userNameField: any

  // Submit changes
  function submit(field: keyof UserBox) {
    let obj = { field, value: userbox[field] }
    if (submitting) return
    submitting = obj.field

    USERBOX.setUserBox(obj)
      .then(() => changed = changed.filter((c) => c !== obj.field))
      .catch(e => error = e.message)
      .finally(() => submitting = "")
  }

  // Fetch data from the server
  async function fetchData() {
    const profile = await USERBOX.getProfile().catch(_ => {
      loading = false
      error = t("userbox.error.nodata")
    })
    if (!profile) return
    userbox = profile.user
    userNameField = {key: "gameUsername", value: userbox.userName, type: "String"}
    userItems = Object.entries(iKinds).flatMap(([iKey, iKind]) => {
      if (iKey != 'avatarAccessory') {
        let ubKey = `${iKey}Id`
        if (iKey.slice('trophy'.length, 'trophy'.length + 3) == "Sub") {
          ubKey = `trophyIdSub${iKey.slice('trophySub'.length, 'trophySub'.length + 1)}`;
          iKey = `trophy`;
        }
        if (ubKey == 'namePlateId') ubKey = 'nameplateId'
        if (ubKey == 'systemVoiceId') ubKey = 'voiceId'
        return [{ iKey, ubKey: ubKey as keyof UserBox,
          items: profile.items.filter(x => x.itemKind === iKind || (iKey == "trophy" && x.itemKind == 3))
        }]
      }

      return avatarKinds.map((aKind, i) => {
        let items = profile.items.filter(x => x.itemKind === iKind && Math.floor(x.itemId / 100000) % 10 === i + 1)
        return { iKey, ubKey: `avatar${aKind}` as keyof UserBox, items }
      })
    })

    allItems = await DATA.allItems('chu3').catch(_ => {
      loading = false
      error = t("userbox.error.nodata")
    }) as typeof allItems

    userOptions = await SETTING.optionGet('chu3').catch(_ => {
      loading = false
      error = t("userbox.error.nodata")
    }) as GameUserOption
    if (userOptions["optionSet"] != 3)
      userIsUsingPreset = true;
    
    loading = false
  }

  USER.me().then(u => {
    if (!u) throw new Error(t("userbox.error.nodata"))
    user = u
    return fetchData()
  }).catch((e) => { loading = false; error = e.message });

  function exportData() {
    submitting = "export"
    GAME.export('chu3')
      .then(data => download(JSON.stringify(data), `MikuNet_chu3_export_${userbox.userName}.json`))
      .catch(e => error = e.message)
      .finally(() => submitting = "")
  }

  async function exportBatchManual() {
    submitting = "batchExport"

    const DIFFICULTY_MAP: Record<number, string> = {
      0: "BASIC",
      1: "ADVANCED",
      2: "EXPERT",
      3: "MASTER",
      4: "ULTIMA"
    } as const // WORLD'S END scores not supported by Tachi
    const DAN_MAP: Record<number, string> = {
      1: "DAN_I",
      2: "DAN_II",
      3: "DAN_III",
      4: "DAN_IV",
      5: "DAN_V",
      6: "DAN_INFINITE"
    } as const
    const SKILL_IDS: Record<number, string> = {
      100009: 'CATASTROPHY',
      102009: 'CATASTROPHY',
      103007: 'CATASTROPHY',

      100008: 'ABSOLUTE',
      101008: 'ABSOLUTE',
      102008: 'ABSOLUTE',
      103006: 'ABSOLUTE',

      100007: 'BRAVE',
      101007: 'BRAVE',
      102007: 'BRAVE',
      103005: 'BRAVE',

      100005: 'HARD',
      100006: 'HARD',
      101004: 'HARD',
      101005: 'HARD',
      101006: 'HARD',
      102004: 'HARD',
      102005: 'HARD',
      102006: 'HARD',
      103002: 'HARD',
      103003: 'HARD',
      103004: 'HARD'
    } as const
    // Shamelessly stolen from https://github.com/beer-psi/saekawa/commit/b3bee13e126df2f4e2a449bdf971debb8c95ba40, needs to be updated every major version :(

    let data: any
    let output: any = {
      "meta": {
        "game": "chunithm",
        "playtype": "Single",
        "service": "MikuNet-Manual"
      },
      "scores": [],
      "classes": {}
    }

    try {
      data = await GAME.export('chu3')
    }
    catch (e: any) {
      error = e.message
      submitting = ""
      return
    }

    if (data && "userPlaylogList" in data) {
      for (let score of data.userPlaylogList) {
        let clearLamp = null
        let noteLamp = null

        if (score.level in DIFFICULTY_MAP) {
          if (score.isClear) {
            clearLamp = score.skillId in SKILL_IDS ? SKILL_IDS[score.skillId] : "CLEAR"
          }
          else {
            clearLamp = "FAILED"
          }

          if (score.score === 1010000) {
            noteLamp = "ALL JUSTICE CRITICAL"
          }
          else if (score.isAllJustice) {
            noteLamp = "ALL JUSTICE"
          }
          else if (score.isFullCombo) {
            noteLamp = "FULL COMBO"
          }
          else {
            noteLamp = "NONE"
          }

          output.scores.push({
            "score": score.score,
            "clearLamp": clearLamp,
            "noteLamp": noteLamp,
            "judgements": {
              "jcrit": score.judgeHeaven + score.judgeCritical,
              "justice": score.judgeJustice,
              "attack": score.judgeAttack,
              "miss": score.judgeGuilty
            },
            "matchType": "inGameID",
            "identifier": score.musicId.toString(),
            "difficulty": DIFFICULTY_MAP[score.level],
            "timeAchieved": score.sortNumber * 1000,
            "optional": {
              "maxCombo": score.maxCombo
            }
          })
        }
      }
    }

    if (data.userData.classEmblemMedal in DAN_MAP) {
      output.classes["dan"] = DAN_MAP[data.userData.classEmblemMedal]
    }

    if (data.userData.classEmblemBase in DAN_MAP) {
      output.classes["emblem"] = DAN_MAP[data.userData.classEmblemBase]
    }

    download(JSON.stringify(output), `MikuNet_chu3_BatchManualExport_${userbox.userName}.json`)
    submitting = ""
  }

  function download(data: string, filename: string) {
    const blob = new Blob([data]);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
  }

  let DDSreader: DDS | undefined;

  let USERBOX_PROGRESS = 0;
  let USERBOX_SETUP_RUN = false;
  let USERBOX_SETUP_MODE = false;
  let USERBOX_SETUP_TEXT = t("userbox.new.setup");

  let USERBOX_ENABLED = useLocalStorage("userboxNew", false);
  let USERBOX_PROFILE_ENABLED = useLocalStorage("userboxNewProfile", false);
  let USERBOX_INSTALLED = false;
  let USERBOX_SUPPORT = "webkitGetAsEntry" in DataTransferItem.prototype;

  type OnlyNumberPropsOf<T extends Record<string, any>> = {[Prop in keyof T as (T[Prop] extends number ? Prop : never)]: T[Prop]}
  let userboxSelected: keyof OnlyNumberPropsOf<UserBox> = "avatarWear";
  const userboxNewOptions = ["systemVoice", "frame", "trophy", "mapIcon", "stage"]

  async function userboxSafeDrop(event: Event & { currentTarget: EventTarget & HTMLInputElement; }) {
    if (!event.target) return null;
    let input = event.target as HTMLInputElement;
    let folder = input.webkitEntries[0];
    error = await userboxFileProcess(folder, (progress: number, progressString: string) => {
      USERBOX_SETUP_TEXT = progressString;
      USERBOX_PROGRESS = progress;
    }) ?? "";
  }

  let USERBOX_URL_STATE = useLocalStorage("userboxURL", USERBOX_DEFAULT_URL);
  function userboxHandleInput(baseURL: string, isSetByServer: boolean = false) {
    if (baseURL != "")
      try {
        // validate url
        new URL(baseURL, location.href);
      } catch(err) {
        if (isSetByServer)
          return;
        return error = t("userbox.new.error.invalidUrl")
      }
    USERBOX_URL_STATE.value = baseURL;
    USERBOX_ENABLED.value = true;
    USERBOX_PROFILE_ENABLED.value = true;
    location.reload();
  }

  if (USERBOX_DEFAULT_URL && !USERBOX_URL_STATE.value)
    userboxHandleInput(USERBOX_DEFAULT_URL, true);

  indexedDB.databases().then(async (dbi) => {
    let databaseExists = dbi.some(db => db.name == "userboxChusanDDS");
    if (USERBOX_URL_STATE.value && databaseExists) {
      indexedDB.deleteDatabase("userboxChusanDDS")
    }
    if (databaseExists) {
      await initializeDb();
    }
    if (databaseExists || USERBOX_URL_STATE.value) {
      DDSreader = new DDS(ddsDB);
      USERBOX_INSTALLED = databaseExists || USERBOX_URL_STATE.value != "";
    }
  })

</script>

<StatusOverlays {error} loading={loading} />
{#if !loading && !error}
<div class="game-settings">
  <GameSettingFields game="chu3"/>

  <div class="setting-group">
    <InputField bind:field={userNameField}
      callback={() => USERBOX.setUserBox({ field: "userName", value: userNameField.value }).then(() => true)}/>
  </div>

  <div class="setting-section">
    <h3>游戏选项</h3>
    {#if userIsUsingPreset}
      <div class="alert alert-info"><Icon icon="solar:info-circle-bold-duotone" /><p>{t('settings.options.all.preset-warning')}</p></div>
    {/if}
    <div class="option-grid">
      <UserOptionSlider
        game="chu3" type="headphone"
        minValue={0} maxValue={60}
        defaultValue={userOptions["headphone"]}
        getTextFunction={(value: number) => `${((value / 60) * 100).toFixed(0)}%`}
       />
      <UserOptionSlider
        game="chu3" type="trackSkip"
        minValue={0} maxValue={7}
        defaultValue={userOptions["trackSkip"]}
        getTextFunction={(value: number) => [t('settings.options.all.none'), `S`, `S+`, `SS`, `SS+`, `SSS`, `SSS+`, t('settings.options.all.personal-best')][value]}
      />
      <UserOptionSlider
        game="chu3" type="speed"
        minValue={0} maxValue={61}
        defaultValue={userOptions["speed"]}
        getTextFunction={(value: number) => value >= 61 ? "SONIC" : value < 55 ? `${(value / 4) + 1}` : `${value - 40}`}
      />
      <UserOptionSlider
        game="chu3" type="speed_120"
        minValue={0} maxValue={61}
        defaultValue={userOptions["speed_120"]}
        getTextFunction={(value: number) => value >= 61 ? "SONIC" : value < 55 ? `${(value / 4) + 1}` : `${value - 40}`}
      />
    </div>
  </div>

  <div class="setting-section">
    <h3>{t("userbox.header.userbox")}</h3>
    {#if !USERBOX_ENABLED.value || !USERBOX_INSTALLED}
      <div class="setting-group">
        {#each userItems as { iKey, ubKey, items }}
          {#if items.length > 0}
            <div class="setting-row stacked">
              <label class="setting-text" for={ubKey}><span class="name">{ts(`userbox.${ubKey}`)}</span></label>
              <div class="input-group">
                <select bind:value={userbox[ubKey]} id={ubKey} on:change={() => changed = [...changed, ubKey]}>
                  {#each items as option}
                    <option value={option.itemId}>{allItems[iKey][option.itemId]?.name || `(unknown ${option.itemId})`}</option>
                  {/each}
                </select>
                {#if changed.includes(ubKey)}
                  <button class="btn-primary" type="button" transition:fade={{ duration: 120 }} on:click={() => submit(ubKey)} disabled={!!submitting}>
                    {#if submitting === ubKey}<Spinner />{/if}{t("settings.profile.save")}
                  </button>
                {/if}
              </div>
            </div>
          {/if}
        {/each}
      </div>
    {:else}
      <div class="chuni-userbox-container">
        <ChuniUserplateComponent chuniIsUserbox={true} on:click={() => userboxSelected = "nameplateId"} chuniCharacter={userbox.characterId} chuniLevel={userbox.level.toString()} chuniRating={userbox.playerRating / 100}
          chuniNameplate={userbox.nameplateId} chuniName={userbox.userName} chuniTrophyName={allItems.trophy[userbox.trophyId].name}></ChuniUserplateComponent>
        <ChuniPenguinComponent chuniWear={userbox.avatarWear} chuniHead={userbox.avatarHead} chuniBack={userbox.avatarBack}
          chuniFront={userbox.avatarFront} chuniFace={userbox.avatarFace} chuniItem={userbox.avatarItem}
          chuniSkin={userbox.avatarSkin}></ChuniPenguinComponent>
      </div>
      <div class="chuni-userbox-row" role="tablist" aria-label={t("userbox.header.userbox")}>
        {#each avatarKinds as avatarKind}
          {#await DDSreader?.getFile(`avatarAccessoryThumbnail:${userbox[`avatar${avatarKind}`].toString().padStart(8, "0")}`) then imageURL}
            <button type="button" role="tab" aria-selected={userboxSelected == `avatar${avatarKind}`} class:focused={userboxSelected == `avatar${avatarKind}`} on:click={() => userboxSelected = `avatar${avatarKind}`}>
              <img src={imageURL} alt={allItems.avatarAccessory[userbox[`avatar${avatarKind}`]].name} title={allItems.avatarAccessory[userbox[`avatar${avatarKind}`]].name}>
            </button>
          {/await}
        {/each}
      </div>
      <div class="chuni-userbox">
        {#if userboxSelected == "nameplateId"}
          {#each userItems.find(f => f.ubKey == "nameplateId")?.items ?? [] as item}
            {#await DDSreader?.getFile(`nameplate:${item.itemId.toString().padStart(8, "0")}`) then imageURL}
              <button type="button" class="nameplate" class:current={userbox[userboxSelected] === item.itemId} on:click={() => {userbox[userboxSelected] = item.itemId; submit(userboxSelected)}}>
                <img src={imageURL} alt={allItems.namePlate[item.itemId].name} title={allItems.namePlate[item.itemId].name}>
              </button>
            {/await}
          {/each}
        {:else}
          {#each userItems.find(f => f.ubKey == userboxSelected)?.items ?? [] as item}
            {#await DDSreader?.getFile(`avatarAccessoryThumbnail:${item.itemId.toString().padStart(8, "0")}`) then imageURL}
              <button type="button" class:current={userbox[userboxSelected] === item.itemId} on:click={() => {userbox[userboxSelected] = item.itemId; submit(userboxSelected)}}>
                <img src={imageURL} alt={allItems.avatarAccessory[item.itemId].name} title={allItems.avatarAccessory[item.itemId].name}>
              </button>
            {/await}
          {/each}
        {/if}
      </div>
      <div class="setting-group">
        {#each userItems.filter(i => userboxNewOptions.includes(i.iKey)) as { iKey, ubKey, items }}
          {#if items.length > 0}
            <div class="setting-row stacked">
              <label class="setting-text" for={ubKey}><span class="name">{ts(`userbox.${ubKey}`)}</span></label>
              <div class="input-group">
                <select bind:value={userbox[ubKey]} id={ubKey} on:change={() => changed = [...changed, ubKey]}>
                  {#each items as option}
                    <option value={option.itemId}>{allItems[iKey][option.itemId]?.name || `(unknown ${option.itemId})`}</option>
                  {/each}
                </select>
                {#if changed.includes(ubKey)}
                  <button class="btn-primary" type="button" transition:fade={{ duration: 120 }} on:click={() => submit(ubKey)} disabled={!!submitting}>
                    {#if submitting === ubKey}<Spinner />{/if}{t("settings.profile.save")}
                  </button>
                {/if}
              </div>
            </div>
          {/if}
        {/each}
      </div>
    {/if}

    {#if USERBOX_INSTALLED}
      <div class="setting-group">
        <div class="setting-row">
          <label class="setting-text" for="newUserbox">
            <span class="name">{t("userbox.new.activate")}</span>
            <span class="desc">{t(`userbox.new.activate_desc`)}</span>
          </label>
          <input type="checkbox" class="switch" bind:checked={USERBOX_ENABLED.value} id="newUserbox">
        </div>
        <div class="setting-row">
          <label class="setting-text" for="newUserboxProfile">
            <span class="name">{t("userbox.new.activate_profile")}</span>
            <span class="desc">{t(`userbox.new.activate_profile_desc`)}</span>
          </label>
          <input type="checkbox" class="switch" bind:checked={USERBOX_PROFILE_ENABLED.value} id="newUserboxProfile">
        </div>
      </div>
    {/if}
    {#if USERBOX_SUPPORT && !USERBOX_DEFAULT_URL}
      <div class="row">
        <button type="button" on:click={() => USERBOX_SETUP_RUN = !USERBOX_SETUP_RUN}><Icon icon="solar:download-minimalistic-bold-duotone" />{t(!USERBOX_INSTALLED ? `userbox.new.activate_first` : `userbox.new.activate_update`)}</button>
      </div>
    {/if}
  </div>

  <ChuniMatchingSettings/>

  <div class="setting-section">
    <h3>数据导出</h3>
    <div class="export-actions">
      <button type="button" on:click={exportData} disabled={!!submitting}>
        {#if submitting === "export"}<Spinner />{:else}<Icon icon="bxs:file-export"/>{/if}
        {t('settings.export')}
      </button>
      <button type="button" on:click={exportBatchManual} disabled={!!submitting}>
        {#if submitting === "batchExport"}<Spinner />{:else}<Icon icon="bxs:file-export"/>{/if}
        {t('settings.batchManualExport')}
      </button>
    </div>
  </div>
</div>
{/if}

{#if USERBOX_SETUP_RUN && !error}
  <Dialog title={t('userbox.new.name')} persistent={USERBOX_PROGRESS != 0} on:close={() => USERBOX_SETUP_RUN = false}>
    <div class="setup">
      <p class="muted">{USERBOX_SETUP_MODE ? t('userbox.new.url_warning') : USERBOX_SETUP_TEXT}</p>
      {#if USERBOX_SETUP_MODE}
        <input type="url" on:keyup={e => {if (e.key == "Enter") { userboxHandleInput((e.target as HTMLInputElement).value) }}} placeholder="Base URL（按 Enter 确认）">
      {:else if USERBOX_PROGRESS != 0}
        <div class="progress" role="progressbar" aria-valuenow={USERBOX_PROGRESS} aria-valuemin={0} aria-valuemax={100}>
          <div class="progress-bar" style="width: {USERBOX_PROGRESS}%"></div>
        </div>
      {:else}
        <p class="subtle">{t('userbox.new.setup.notice')}</p>
        <label class="drop-btn">
          <input type="file" on:input={userboxSafeDrop} on:click={e => e.preventDefault()}>
          <Icon icon="solar:folder-with-files-bold-duotone" />
          <span>{t('userbox.new.drop')}</span>
        </label>
      {/if}
    </div>
    <svelte:fragment slot="footer">
      {#if USERBOX_PROGRESS == 0}
        <button type="button" on:click={() => USERBOX_SETUP_MODE = !USERBOX_SETUP_MODE}>
          {t(USERBOX_SETUP_MODE ? 'userbox.new.switch.to_drop' : 'userbox.new.switch.to_url')}
        </button>
        <button type="button" on:click={() => USERBOX_SETUP_RUN = false}>{t('back')}</button>
      {/if}
    </svelte:fragment>
  </Dialog>
{/if}

<style lang="sass">
@use "../../vars"

.game-settings
  display: grid
  gap: 24px

.option-grid
  display: grid
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr))
  gap: 10px

.export-actions
  display: flex
  flex-wrap: wrap
  gap: 8px

  button
    white-space: normal
    text-align: left

.setup
  display: grid
  gap: 12px

  p
    margin: 0

.progress
  width: 100%
  height: 8px
  border-radius: var(--radius-full)
  background: var(--surface-3)
  overflow: hidden

  .progress-bar
    height: 100%
    border-radius: var(--radius-full)
    background: var(--primary)
    transition: width 0.2s ease

.drop-btn
  position: relative
  display: flex
  flex-direction: column
  align-items: center
  justify-content: center
  gap: 8px
  min-height: 140px
  border: 2px dashed var(--border-strong)
  border-radius: var(--radius)
  color: var(--text-2)
  text-align: center

  :global(svg)
    color: var(--primary)
    font-size: 2rem

  &:hover
    border-color: var(--primary)
    background: var(--primary-soft)

  > input
    position: absolute
    inset: 0
    width: 100%
    height: 100%
    opacity: 0
    cursor: pointer

/* AquaBox */

.chuni-userbox-row
  display: flex
  gap: 6px
  width: 100%

  button
    flex: 1 1 0
    height: auto
    min-width: 0
    aspect-ratio: 1
    padding: 0
    overflow: hidden
    background: var(--surface-2)

    img
      width: 100%
      filter: brightness(60%)
      transition: filter 0.15s ease

    &.focused
      border-color: var(--primary)
      box-shadow: 0 0 0 2px var(--primary)

      img
        filter: none

.chuni-userbox
  display: grid
  grid-template-columns: repeat(auto-fill, minmax(84px, 1fr))
  gap: 6px
  max-height: 360px
  padding: 8px
  overflow-y: auto
  border: 1px solid var(--border)
  border-radius: var(--radius)
  background: var(--surface-2)

  button
    height: auto
    aspect-ratio: 1
    padding: 0
    overflow: hidden
    background: var(--surface)

    img
      width: 100%

    &.current
      border-color: var(--primary)
      box-shadow: 0 0 0 2px var(--primary)

    &.nameplate
      grid-column: span 2
      aspect-ratio: auto

.chuni-userbox-container
  display: flex
  align-items: center
  justify-content: center

@media (max-width: 1000px)
  .chuni-userbox-container
    flex-wrap: wrap
</style>
