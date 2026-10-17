<script lang="ts">
  import StatusOverlays from "../../components/StatusOverlays.svelte";
  import { t } from "../../libs/i18n";
  import { TRANSFER } from "../../libs/sdk";
  import { download, selectJsonFile } from "../../libs/ui";
  import InputTextShort from "./InputTextShort.svelte";
  import Icon from "../../components/ui/Icon.svelte";
  import Spinner from "../../components/ui/Spinner.svelte";
  import { createEventDispatcher } from 'svelte';

  const dispatch = createEventDispatcher()

  export let src: AllNetSrc
  export let gameInfo: AllNetGame
  export let isSrc: boolean = true

  export let tested: boolean = false
  let [loading, error] = [false, ""]

  const blacklist = ['amime.missless.net']

  function testConnection() {
    if (loading || isBlacklist) return

    // Preliminiary checks
    if (!src.dns || !src.keychip || !src.card || !gameInfo.game || !gameInfo.version) {
      error = t('trans.error.empty')
      return
    }

    loading = true
    error = ""
    console.log("Testing connection...")
    return TRANSFER.check({...src, ...gameInfo}).then(res => {
      console.log("Connection test result:", res)
      tested = true
    }).catch(err => error = err.message).finally(() => loading = false)
  }

  let messages: string[] = []
  export let exportedData: string = ""

  export function pull(): Promise<string> {
    return new Promise<string>((resolve, reject) => {
      if (loading || !tested) return reject(t('trans.error.untested'))
      if (exportedData) return resolve(exportedData)
      console.log("Exporting data...")
      error = ""

      TRANSFER.pull({...src, ...gameInfo}, (msg: TrStreamMessage) => {
        console.log("Export progress: ", JSON.stringify(msg))

        if ('message' in msg) messages = [...messages, msg.message]

        if ('error' in msg) {
          error = msg.error
          reject(msg.error)
        }

        if ('data' in msg) {
          // file name: Export YYYY-MM-DD {server host} {game} {card last 6}.json
          let date = new Date().toISOString().split('T')[0]
          let host = new URL(src.dns).hostname
          download(msg.data, `Export ${date} ${host} ${gameInfo.game} ${src.card.slice(-6)}.json`)
          exportedData = msg.data
          resolve(msg.data)
        }
      }).catch(err => { error = err; reject(err) })
    })
  }

  function pushBtn() {
    if (loading || !tested) return
    selectJsonFile().then(obj => push(JSON.stringify(obj)))
  }

  export function push(data: string) {
    if (loading || !tested) return
    console.log("Import data...")
    loading = true
    error = ""

    return TRANSFER.push({...src, ...gameInfo}, data).then(() => {
      console.log("Data imported successfully")
      messages = [t('trans.success.import')]
    }).catch(err => error = err.message).finally(() => loading = false)
  }

  $: isBlacklist = blacklist.filter(x => src.dns.includes(x)).length > 0
</script>

<StatusOverlays {loading} />

<section class="card server" class:tested class:has-error={!!error}>
  <div class="card-header">
    <div class="title">
      <span class="tile-icon" class:tone-teal={isSrc} class:tone-pink={!isSrc}><Icon icon={isSrc ? "solar:server-bold-duotone" : "solar:server-square-bold-duotone"} /></span>
      <h2>{t(`trans.${isSrc ? "source" : "target"}.title`)}</h2>
    </div>
    {#if tested}<span class="badge success"><Icon icon="solar:check-circle-bold" />已连接</span>{:else if error}<span class="badge danger">连接失败</span>{:else}<span class="badge">未测试</span>{/if}
  </div>

  <div class="server-body">
    {#if !isSrc && isBlacklist}
      <div class="alert"><Icon icon="solar:danger-triangle-bold-duotone" /><p>{t('trans.blacklist')}</p></div>
    {/if}

    {#if error}
      <div class="alert alert-danger" role="alert"><Icon icon="solar:danger-triangle-bold-duotone" /><p class="error-msg">{error}</p></div>
    {/if}

    <div class="inputs">
      <InputTextShort desc={t('trans.field.addr')} placeholder="e.g. https://mikunet.example"
        bind:value={src.dns} on:change disabled={tested}
        validate={v => /^https?:\/\/[a-z0-9.-]+(:\d+)?$/i.test(v)} />
      <InputTextShort desc={t('trans.field.keychip')} placeholder="e.g. A0299792458"
        bind:value={src.keychip} on:change disabled={tested}
        validate={v => /^([A-Z\d]{15}|[A-Z\d]{4}-[A-Z\d]{11})$/.test(v)} />
    </div>

    <div class="inputs">
      <div class="game-version">
        <InputTextShort desc={t('trans.field.game')} placeholder="e.g. SDHD"
          bind:value={gameInfo.game} on:change disabled={tested} />
        <InputTextShort desc={t('trans.field.version')} placeholder="e.g. 2.30"
          bind:value={gameInfo.version} on:change disabled={tested} />
      </div>
      <InputTextShort desc={t('trans.field.card')} placeholder="e.g. 27182818284590452353"
        bind:value={src.card} disabled={tested} on:change={value => {
          src.card = src.card.replaceAll(' ', '')
          dispatch('change', { value });
        }} />
    </div>

    {#if messages.length > 0}
      <div class="stream-messages" aria-live="polite">
        {#each messages.slice(Math.max(messages.length - 5, 0), undefined) as msg}
          <p>{msg}</p>
        {/each}
      </div>
    {/if}

    <div class="buttons">
      {#if !tested}
        <button class="btn-soft" type="button" on:click={testConnection} disabled={loading}>{#if loading}<Spinner />{:else}<Icon icon="solar:plug-circle-bold-duotone" />{/if}{t('trans.btn.test')}</button>
      {:else}
        <button type="button" on:click={pull}><Icon icon="solar:download-minimalistic-bold-duotone" />{t('trans.btn.export')}</button>
        <button type="button" on:click={pushBtn}><Icon icon="solar:upload-minimalistic-bold-duotone" />{t('trans.btn.import')}</button>
      {/if}
    </div>
  </div>
</section>

<style lang="sass">
  .server
    &.tested
      border-color: color-mix(in srgb, var(--success) 45%, transparent)

    &.has-error
      border-color: color-mix(in srgb, var(--danger) 45%, transparent)

  .title
    display: flex
    align-items: center
    gap: 12px

    h2
      margin: 0
      font-size: 1.1rem

  .server-body
    display: grid
    gap: 14px

  .error-msg
    white-space: pre-wrap

  .inputs
    display: flex
    flex-wrap: wrap
    gap: 12px

    .game-version
      display: flex
      flex: 60
      gap: 12px

  .stream-messages
    padding: 10px 12px
    border-radius: var(--radius-sm)
    color: var(--text-2)
    background: var(--surface-2)
    font-family: var(--font-mono)
    font-size: 0.8rem

    p
      margin: 0

  .buttons
    display: flex
    justify-content: flex-end
    gap: 8px
</style>
