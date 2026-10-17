<script lang="ts">
  import { t, ts } from "../../libs/i18n";
  import TransferServer from "./TransferServer.svelte";
  import Icon from "../../components/ui/Icon.svelte";
  import PageHeader from "../../components/ui/PageHeader.svelte";
  import Spinner from "../../components/ui/Spinner.svelte";
  import { toast } from "../../libs/toast";
  import type { ConfirmProps } from "../../libs/generalTypes";
  import StatusOverlays from "../../components/StatusOverlays.svelte";


  let tabs = ['chu3', 'mai2', 'ongeki']
  let game: Record<string, { game: string, version: string }> = {
    'chu3': { game: "SDHD", version: "2.30" },
    'mai2': { game: "SDGA", version: "1.50" },
    'ongeki': { game: "SDDT", version: "1.45" }
  }
  let tab = 0

  let src = JSON.parse(localStorage.getItem('src') ?? `{"dns": "", "card": "", "keychip": ""}`)
  let dst = JSON.parse(localStorage.getItem('dst') ?? `{"dns": "", "card": "", "keychip": ""}`)
  let [srcTested, dstTested] = [false, false]
  let gameInfo = JSON.parse(localStorage.getItem('gameInfo') ?? `{"game": "", "version": ""}`)

  let srcEl: TransferServer, dstEl: TransferServer
  let srcExportedData: string
  let [error, loading] = ["", false]
  let confirm: ConfirmProps | null = null

  function defaultGame() {
    gameInfo.game = game[tabs[tab]].game
    gameInfo.version = game[tabs[tab]].version
  }

  function onChange() {
    localStorage.setItem('src', JSON.stringify(src))
    localStorage.setItem('dst', JSON.stringify(dst))
    localStorage.setItem('gameInfo', JSON.stringify(gameInfo))
  }

  function actuallyStartTransfer() {
    srcEl.pull()
      .then(() => dstEl.push(srcExportedData))
      .then(() => confirm = {
        title: t('trans.confirm.done.title'),
        message: t('trans.confirm.done.msg', { src: src.dns, dst: dst.dns })
      })
      .catch(e => error = e)
      .finally(() => loading = false)
  }

  function startTransfer() {
    if (!(srcTested && dstTested)) return confirm = {
      title: t('trans.confirm.untested.title'),
      message: t('trans.confirm.untested.msg')
    }
    if (loading) return toast.info(t('trans.alert.in-progress'))
    console.log("Starting transfer...")
    loading = true

    if (dstEl.exportedData) return actuallyStartTransfer()

    // Ask user to make sure to backup their data
    confirm = {
      title: t('trans.confirm.unbackuped.title'),
      message: t('trans.confirm.unbackuped.msg'),
      dangerous: true,
      confirm: actuallyStartTransfer,
      cancel: () => { loading = false }
    }
  }

  defaultGame()

  // Picking a game fills in its default game code and version.
  function selectTab(i: number) {
    tab = i
    defaultGame()
    onChange()
  }
</script>

<StatusOverlays bind:confirm={confirm} {error} />

<main class="page page-narrow">
  <PageHeader eyebrow="MikuTrans" title={t('trans.title')}>
    <div class="segmented" role="tablist" aria-label={t('trans.field.game')}>
      {#each tabs as tabName, i}
        <button type="button" role="tab" aria-selected={tab === i} on:click={() => selectTab(i)}>{ts(`settings.tabs.${tabName}`)}</button>
      {/each}
    </div>
  </PageHeader>

  <div class="alert alert-info prompt"><Icon icon="solar:info-circle-bold-duotone" /><div>{@html t("trans.prompt-html")}</div></div>

  <TransferServer bind:src={src} bind:gameInfo={gameInfo} on:change={onChange}
    bind:tested={srcTested} bind:this={srcEl} bind:exportedData={srcExportedData} />

  <div class="transfer-action">
    <span class="line" aria-hidden="true"></span>
    <button class="btn-primary btn-lg" type="button" on:click={startTransfer} disabled={loading} aria-describedby="transfer-hint">
      {#if loading}<Spinner />{:else}<Icon icon="solar:alt-arrow-down-bold" />{/if}
      开始迁移
    </button>
    <small id="transfer-hint" class="subtle">{srcTested && dstTested ? "两端连接均已测试通过" : "请先测试源服务器和目标服务器的连接"}</small>
    <span class="line" aria-hidden="true"></span>
  </div>

  <TransferServer bind:src={dst} bind:gameInfo={gameInfo} on:change={onChange}
    bind:tested={dstTested} bind:this={dstEl} isSrc={false} />
</main>

<style lang="sass">
  .prompt
    :global(p)
      margin: 0 0 4px

  .transfer-action
    display: flex
    flex-direction: column
    align-items: center
    gap: 8px
    margin: -8px 0

    .line
      width: 2px
      height: 18px
      border-radius: 2px
      background: var(--border-strong)

    small
      font-size: 0.8rem
</style>
