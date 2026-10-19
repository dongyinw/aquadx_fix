<!-- Svelte 4.2.11 -->

<script lang="ts">
  import { fade, slide } from "svelte/transition"
  import type { Card, CardSummary, CardSummaryGame, ConfirmProps, MikuNetUser } from "../../libs/generalTypes"
  import { CARD, USER } from "../../libs/sdk"
  import moment from "moment"
  import Icon from "../../components/ui/Icon.svelte"
  import StatusOverlays from "../../components/StatusOverlays.svelte"
  import { t } from "../../libs/i18n"
  import DashboardTabs from "../../components/DashboardTabs.svelte";
  import PageHeader from "../../components/ui/PageHeader.svelte";
  import EmptyState from "../../components/ui/EmptyState.svelte";
  import Dialog from "../../components/ui/Dialog.svelte";
  import Spinner from "../../components/ui/Spinner.svelte";
  import { toast } from "../../libs/toast";
  import * as acUrl from "../../libs/acUrl"
  import { ACCESSCODE_QKEY } from "../../libs/config";

  // State
  let state: 'ready' | 'linking-AC' | 'linking-SN' | 'loading' = "loading"
  let showConfirm: ConfirmProps | null = null

  let error: string = ""
  let me: MikuNetUser | null = null
  let accountCardSummary: CardSummary | null = null

  // Fetch data for current user
  const updateMe = () => USER.me().then(m => {
    me = m
    m.cards.sort((a, b) => a.registerTime < b.registerTime ? 1 : -1)
    CARD.summary(m.ghostCard.luid).then(s => accountCardSummary = s.summary)

    // Always put the ghost card at the top
    m.cards.sort((a, b) => a.isGhost ? -1 : 1)
    state = "ready"

    linkQR()
  }).catch(e => error = e.message)
  acUrl.check()
  updateMe()

  // Data conflict overlay
  let conflictCardID: string = ""
  let conflictSummary: CardSummary | null = null
  let conflictGame: string = ""
  let conflictNew: CardSummaryGame | null = null
  let conflictOld: CardSummaryGame | null = null
  let conflictToMigrate: string[] = []

  function setError(msg: string, type: 'AC' | 'SN') {
    type === 'AC' ? errorAC = msg : errorSN = msg
  }

  async function doLink(id: string, migrate: string) {
    try {
      await CARD.link({cardId: id, migrate})
      await updateMe()
      if (linkingType === 'AC') inputAC = ""
      if (linkingType === 'SN') inputSN = ""
      toast.success("卡片已绑定")
    } catch (e) {
      setError((e as any).message, linkingType!)
    }
    state = "ready"
  }

  let linkingType: 'AC' | 'SN' | null = null
  async function link(type: 'AC' | 'SN') {
    if (state !== 'ready' || accountCardSummary === null) return
    state = "linking-" + type
    linkingType = type
    const id = type === 'AC' ? inputAC : inputSN

    console.log("linking card", id)

    // Check if this card is already linked in the account
    if (me?.cards?.some(c => formatLUID(c.luid, c.isGhost).toLowerCase() === id.toLowerCase())) {
      setError(t('home.linkcard.linked-own'), type)
      state = "ready"
      return
    }

    // First, lookup the card summary
    const card = (await CARD.summary(id).catch(e => {
      // If card is not found, create a card and link it
      if (e.message === 'Card not found') {
        doLink(id, "")
        return
      }

      setError(e.message, type)
      state = "ready"
      return
    }))!
    const summary = card.summary

    // Check if it's already linked
    if (card.card.linked) {
      setError(t('home.linkcard.linked-another'), type)
      state = "ready"
      return
    }

    // If all games in summary are null or doesn't conflict with the ghost card,
    // we can link the card directly
    if (Object.keys(summary).every(k => summary[k as keyof CardSummary] === null
        || accountCardSummary!![k as keyof CardSummary] === null)) {
      console.log("linking card directly")
      await doLink(id, Object.keys(summary).filter(k => summary[k as keyof CardSummary] !== null).join(","))
    }

    // For each conflicting game, ask the user if they want to migrate the data
    else {
      conflictSummary = summary
      conflictCardID = id
      await linkConflictContinue(null)
    }
  }

  async function linkConflictContinue(choose: "old" | "new" | null) {
    if (accountCardSummary === null || conflictSummary === null) return
    console.log("linking card with migration")

    if (choose) {
      // If old is chosen, nothing needs to be migrated
      // If new is chosen, we need to migrate the data
      if (choose === "new") {
        conflictToMigrate.push(conflictGame)
      }
      // Continue to the next card
      conflictSummary[conflictGame as keyof CardSummary] = null
    }

    let isConflict = false
    for (const k in conflictSummary) {
      conflictNew = conflictSummary[k as keyof CardSummary]
      conflictOld = accountCardSummary[k as keyof CardSummary]
      conflictGame = k
      if (!conflictNew || !conflictOld) continue

      isConflict = true
      break
    }

    // If there are no longer conflicts, we can link the card
    if (!isConflict) {
      await doLink(conflictCardID, conflictToMigrate.join(","))

      // Reset the conflict state
      linkConflictCancel()
    }
  }

  function linkConflictCancel() {
    state = "ready"
    conflictSummary = null
    conflictCardID = ""
    conflictGame = ""
    conflictNew = null
    conflictOld = null
    conflictToMigrate = []
  }

  async function unlink(card: Card) {
    showConfirm = {
      title: t('home.linkcard.unlink'),
      message: t('home.linkcard.unlink-notice'),
      confirm: async () => {
        await CARD.unlink(card.luid)
        await updateMe()
        showConfirm = null
        toast.success("已解除绑定")
      },
      cancel: () => showConfirm = null,
      dangerous: true
    }
  }

  function cursorPositionToCursorIndex(text: string, cursorPosition: number, effectiveCharsRegex: RegExp) {
    const textBeforeCursor = text.slice(0, cursorPosition)
    const ignoredChars = textBeforeCursor.replace(new RegExp(effectiveCharsRegex, "g"), "")
    return textBeforeCursor.length - ignoredChars.length
  }

  function cursorIndexToCursorPosition(text: string, cursorIndex: number, effectiveCharsRegex: RegExp) {
    let i = 0
    while (i < text.length) {
      while (i < text.length && !effectiveCharsRegex.test(text[i])) i++
      if (cursorIndex === 0) break
      cursorIndex--
      i++
    }
    return i
  }

  // Which input is shown. Switching clears the other value, matching the old "one at a time" behaviour.
  let method: 'AC' | 'SN' = 'AC'
  function chooseMethod(next: 'AC' | 'SN') {
    if (method === next) return
    method = next
    if (next === 'AC') { inputSN = ""; errorSN = "" } else { inputAC = ""; errorAC = ""; warningAC = "" }
  }
  $: if (inputSN && !inputAC) method = 'SN'
  $: if (inputAC && !inputSN) method = 'AC'

  // Cards whose number is currently revealed (numbers are partially blurred by default)
  let revealed: string[] = []
  const toggleReveal = (luid: string) => revealed = revealed.includes(luid) ? revealed.filter(l => l !== luid) : [...revealed, luid]
  function idGroups(card: Card) {
    const formatted = formatLUID(card.luid, card.isGhost)
    return formatted.includes(":") ? formatted.split(":").map((v, i, a) => v + (i < a.length - 1 ? ":" : "")) : formatted.split(" ")
  }

  // Access code input
  const inputACRegex = /^(\d{4} ){0,4}\d{0,4}$/
  let elemInputAC: HTMLInputElement
  let inputOldAC = ""
  let inputAC = ""
  let errorAC = ""
  let warningAC = ""

  function inputACChange() {
    // Add spaces to the input
    const cursorIndex = cursorPositionToCursorIndex(inputAC, elemInputAC?.selectionStart ?? inputAC.length, /\d/)
    inputAC = inputAC.replace(/\D/g, '').replace(/(.{4})/g, '$1 ').replace(/ $/, '')
    const cursorPosition = cursorIndexToCursorPosition(inputAC, cursorIndex, /\d/)
    setTimeout(() => { if (elemInputAC) elemInputAC.selectionStart = elemInputAC.selectionEnd = cursorPosition }, 0)
    if (inputAC !== inputOldAC) errorAC = ""
    warningAC = inputAC[0] === "5" ? t('home.linkcard.felica-ac-warning') : ""

    inputOldAC = inputAC
  }

  // Serial number input
  const inputSNRegex = /^([0-9A-Fa-f]{0,2}:){0,7}[0-9A-Fa-f]{0,2}$/
  let inputElemSN: HTMLInputElement
  let inputOldSN = ""
  let inputSN = ""
  let errorSN = ""

  function inputSNChange() {
    // Add colons to the input
    inputSN = inputSN.toUpperCase()
    const cursorIndex = cursorPositionToCursorIndex(inputSN, inputElemSN?.selectionStart ?? inputSN.length, /[0-9A-F]/)
    inputSN = inputSN.replace(/[^0-9A-F]/g, '').replace(/(.{2})/g, '$1:').replace(/:$/, '')
    const cursorPosition = cursorIndexToCursorPosition(inputSN, cursorIndex, /[0-9A-F]/)
    setTimeout(() => { if (inputElemSN) inputElemSN.selectionStart = inputElemSN.selectionEnd = cursorPosition }, 0)
    if (inputSN !== inputOldSN) errorSN = ""

    inputOldSN = inputSN
  }

  function formatLUID(luid: string, ghost: boolean = false) {
    if (ghost) return luid.slice(0, 6) + " " + (luid.slice(6).match(/.{4}/g)?.join(" ") ?? "")
    switch (cardType(luid)) {
      case "FeliCa SN":
        return BigInt(luid).toString(16).toUpperCase().padStart(16, "0").match(/.{1,2}/g)!.join(":")
      case "Access Code":
        return luid.match(/.{4}/g)!.join(" ")
      default:
        return luid
    }
  }

  function cardType(luid: string) {
    if (luid.startsWith("00")) return "FeliCa SN"
    if (luid.length === 20) return "Access Code"
    if (luid.includes(":")) return "FeliCa SN"
    if (luid.includes(" ")) return "Access Code"
    return "Unknown"
  }

  function isInput(e: KeyboardEvent) {
    return e.key.length === 1 && !e.altKey && !e.ctrlKey && !e.metaKey && !e.shiftKey
  }

  async function dropFile(e: DragEvent) {
    e.preventDefault()
    e.stopPropagation()
    const file = e.dataTransfer?.files[0]
    if (!file) return
    switch (file.name.toLowerCase()) {
      case "aime.txt":
        inputSN = ""
        inputAC = await file.text()
        inputACChange()
        break
      case "felica.txt":
        inputAC = ""
        inputSN = await file.text()
        inputSNChange()
        break
    }
  }

  function generateRandom() {
    inputAC = "";
    while (inputAC.length < 20) {
      let digit = Math.floor(Math.random() * 10);
      if (!((digit == 5 || digit == 3) && inputAC.length == 0))
        inputAC += digit;
    };
    inputACChange();
  }

  function linkQR() {
    if (!acUrl.has()) return;

    inputAC = acUrl.get()
    if (inputAC.length !== 20) return inputAC = "";

    inputACChange()
    acUrl.clear()
  }
</script>

<main class="page cards-page">
  <PageHeader eyebrow="Cards" title="卡片管理" description="管理已绑定的实体卡，并把新的 Access Code 或 FeliCa 序列号加入账号。">
    <span class="badge primary"><Icon icon="solar:card-bold-duotone" />{me?.cards?.filter(card => !card.isGhost).length ?? 0} 张实体卡</span>
  </PageHeader>
  <DashboardTabs showSetup={!!me?.canModifyKeychips} />

  <!-- svelte-ignore a11y-no-static-element-interactions -->
  <div class="cards-layout" on:drop={dropFile} on:dragover={(e) => e.preventDefault()}>
    <section class="card">
      <div class="card-header"><div><h2>已绑定卡片</h2><p>卡号默认部分隐藏，点击眼睛图标可查看完整卡号</p></div></div>
      {#if me && me.cards && me.cards.find(card => !card.isGhost)}
        <div class="card-list" transition:slide>
          {#each me.cards as card (card.luid)}
            {#if !card.isGhost}
              <article class="card-tile" class:sn={cardType(card.luid) == "FeliCa SN"} transition:fade|global>
                <div class="tile-top">
                  <span class="badge" class:primary={cardType(card.luid) != "FeliCa SN"} class:accent={cardType(card.luid) == "FeliCa SN"}>
                    <Icon icon={cardType(card.luid) == "FeliCa SN" ? "solar:cpu-bolt-bold-duotone" : "solar:card-bold-duotone"} />{cardType(card.luid)}
                  </span>
                  <div class="tile-actions">
                    <button class="btn-ghost btn-icon btn-sm" type="button" aria-pressed={revealed.includes(card.luid)}
                            aria-label={revealed.includes(card.luid) ? "隐藏卡号" : "显示完整卡号"} on:click={() => toggleReveal(card.luid)}>
                      <Icon icon={revealed.includes(card.luid) ? "solar:eye-closed-bold-duotone" : "solar:eye-bold-duotone"} />
                    </button>
                    <button class="btn-ghost btn-icon btn-sm unlink" type="button" aria-label="解除绑定" title="解除绑定" on:click={() => unlink(card)}>
                      <Icon icon="solar:trash-bin-trash-bold-duotone"/>
                    </button>
                  </div>
                </div>
                <strong class="card-id mono" class:revealed={revealed.includes(card.luid)}>
                  {#each idGroups(card) as group, i}<i class:masked={i >= 2}>{group}</i>{/each}
                </strong>
                <dl class="card-meta">
                  <div><dt>{t('home.linkcard.registered')}</dt><dd>{moment(card.registerTime).format("YYYY-MM-DD")}</dd></div>
                  <div><dt>{t('home.linkcard.lastused')}</dt><dd>{moment(card.accessTime).format("YYYY-MM-DD")}</dd></div>
                </dl>
              </article>
            {/if}
          {/each}
        </div>
      {:else if me}
        <EmptyState icon="solar:card-search-bold-duotone" title="还没有绑定实体卡" description="在「绑定新卡片」中输入卡号开始绑定。" />
      {:else}
        <div class="card-list">{#each [1, 2] as _}<span class="skeleton" style="height:140px;border-radius:14px"></span>{/each}</div>
      {/if}
      <div class="alert alert-info security-note"><Icon icon="solar:shield-check-bold-duotone" /><p>{t('home.linkcard.card-security-warning')}</p></div>
    </section>

    <section class="card link-panel">
      <div class="card-header"><div><h2>绑定新卡片</h2><p>{t('home.linkcard.enter-info')}</p></div></div>

      <div class="segmented method-switch" role="tablist" aria-label="卡号类型">
        <button type="button" role="tab" aria-selected={method === 'AC'} on:click={() => chooseMethod('AC')}><Icon icon="solar:card-bold-duotone" />Access Code</button>
        <button type="button" role="tab" aria-selected={method === 'SN'} on:click={() => chooseMethod('SN')}><Icon icon="solar:cpu-bolt-bold-duotone" />FeliCa 序列号</button>
      </div>

      {#if method === 'AC'}
        <form class="link-form" on:submit|preventDefault={() => link('AC')}>
          <p class="field-hint">{t('home.linkcard.access-code')}</p>
          <label class="field">
            <span class="field-label">20 位 Access Code</span>
            <!-- DO NOT change the order of bind:value and on:input. -->
            <input bind:this={elemInputAC} class="mono code-input" inputmode="numeric" autocomplete="off" placeholder="0008 1234 5678 8765 4321"
                   on:keydown={(e) => { if (isInput(e) && !/[\d ]/.test(e.key)) e.preventDefault() }}
                   bind:value={inputAC} on:input={inputACChange}
                   class:error={inputAC && (!inputACRegex.test(inputAC) || errorAC)} class:warning={inputAC && warningAC}
                   aria-invalid={!!(inputAC && (!inputACRegex.test(inputAC) || errorAC))}>
          </label>
          {#if errorAC}<p class="field-error" role="alert" transition:slide>{errorAC}</p>{/if}
          {#if warningAC}<div class="alert" transition:slide><Icon icon="solar:danger-triangle-bold-duotone" /><div>{#each warningAC.trim().split("\n") as paragraph}<p>{paragraph}</p>{/each}</div></div>{/if}
          <div class="form-actions">
            <button class="btn-ghost" type="button" on:click={() => generateRandom()}><Icon icon="tabler:dice" />随机生成</button>
            <button class="btn-primary" type="submit" disabled={!inputAC || state !== 'ready'}>
              {#if state === 'linking-AC'}<Spinner />{:else}<Icon icon="solar:link-bold" />{/if}{t('home.linkcard.link')}
            </button>
          </div>
        </form>
      {:else}
        <form class="link-form" on:submit|preventDefault={() => link('SN')}>
          <p class="field-hint">{@html t('home.linkcard.enter-sn')}</p>
          <label class="field">
            <span class="field-label">FeliCa 序列号</span>
            <input bind:this={inputElemSN} class="mono code-input" autocomplete="off" autocapitalize="characters" placeholder="01:2E:1A:2B:3C:4D:5E:6F"
                   on:keydown={(e) => { if (isInput(e) && !/[0-9A-Fa-f:]/.test(e.key)) e.preventDefault() }}
                   bind:value={inputSN} on:input={inputSNChange}
                   class:error={inputSN && (!inputSNRegex.test(inputSN) || errorSN)}
                   aria-invalid={!!(inputSN && (!inputSNRegex.test(inputSN) || errorSN))}>
          </label>
          {#if errorSN}<p class="field-error" role="alert" transition:slide>{errorSN}</p>{/if}
          <div class="form-actions">
            <button class="btn-primary" type="submit" disabled={!inputSN || state !== 'ready'}>
              {#if state === 'linking-SN'}<Spinner />{:else}<Icon icon="solar:link-bold" />{/if}{t('home.linkcard.link')}
            </button>
          </div>
        </form>
      {/if}

      <div class="drop-zone"><Icon icon="solar:cloud-upload-bold-duotone" /><span>也可以把 <code>aime.txt</code> 或 <code>felica.txt</code> 拖到此页面自动填充</span></div>
    </section>
  </div>

  {#if conflictOld && conflictNew && me}
    <Dialog title={t('home.linkcard.data-conflict')} description="两张卡都有该游戏的资料，请选择要保留的一份。" size="lg" on:close={linkConflictCancel}>
      <div class="conflict-grid">
        <button class="conflict-option" type="button" on:click={() => linkConflictContinue('old')}>
          <span class="badge primary">{t('home.linkcard.account-card')}</span>
          <dl>
            <dt>{t('home.linkcard.name')}</dt><dd>{conflictOld.name}</dd>
            <dt>{t('home.linkcard.rating')}</dt><dd class="num">{conflictOld.rating}</dd>
            <dt>{t('home.linkcard.last-login')}</dt><dd>{moment(conflictOld.lastLogin).format("YYYY-MM-DD")}</dd>
          </dl>
          <code class="mono">{formatLUID(me.ghostCard.luid, true)}</code>
          <span class="choose">保留这份资料<Icon icon="line-md:chevron-small-right" /></span>
        </button>
        <button class="conflict-option" type="button" on:click={() => linkConflictContinue('new')}>
          <span class="badge accent">{cardType(conflictCardID)}</span>
          <dl>
            <dt>{t('home.linkcard.name')}</dt><dd>{conflictNew.name}</dd>
            <dt>{t('home.linkcard.rating')}</dt><dd class="num">{conflictNew.rating}</dd>
            <dt>{t('home.linkcard.last-login')}</dt><dd>{moment(conflictNew.lastLogin).format("YYYY-MM-DD")}</dd>
          </dl>
          <code class="mono">{conflictCardID}</code>
          <span class="choose">使用新卡资料<Icon icon="line-md:chevron-small-right" /></span>
        </button>
      </div>
      <svelte:fragment slot="footer">
        <button type="button" on:click={linkConflictCancel}>{t('action.cancel')}</button>
      </svelte:fragment>
    </Dialog>
  {/if}
</main>
<StatusOverlays bind:confirm={showConfirm} bind:error={error} />

<style lang="sass">
  @use "../../vars"

  .cards-layout
    display: grid
    grid-template-columns: minmax(0, 1.15fr) minmax(340px, 0.85fr)
    align-items: start
    gap: 16px

  .card-list
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr))
    gap: 12px

  .card-tile
    display: grid
    gap: 14px
    padding: 16px
    border: 1px solid var(--border)
    border-radius: var(--radius-lg)
    background: linear-gradient(145deg, var(--surface), var(--primary-soft))
    transition: vars.$transition

    &.sn
      background: linear-gradient(145deg, var(--surface), var(--accent-soft))

    &:hover
      border-color: var(--border-strong)
      box-shadow: var(--shadow-sm)

  .tile-top
    display: flex
    align-items: center
    justify-content: space-between
    gap: 8px

  .tile-actions
    display: flex
    gap: 2px
    margin: -4px -6px -4px 0

  .unlink:hover:not(:disabled)
    color: var(--danger)
    background: var(--danger-soft)

  .card-id
    display: flex
    flex-wrap: wrap
    gap: 0 0.4em
    color: var(--text)
    font-size: 1rem
    letter-spacing: 0.04em

    i
      font-style: normal
      transition: filter 0.2s ease

    i.masked
      filter: blur(6px)
      user-select: none

    &.revealed i.masked
      filter: none
      user-select: text

  .card-meta
    display: flex
    flex-wrap: wrap
    gap: 18px
    margin: 0
    font-size: 0.8rem

    div
      display: grid
      gap: 1px

    dt
      color: var(--text-3)

    dd
      margin: 0
      color: var(--text)
      font-weight: 600

  .security-note
    margin-top: 16px

  .link-panel
    position: sticky
    top: calc(var(--nav-height) + 16px)

  .method-switch
    display: flex
    margin-bottom: 16px

    > button
      flex: 1

  .link-form
    display: grid
    gap: 12px

    .field-hint
      margin: 0

    :global(.field-hint a)
      font-weight: 600

  .code-input
    font-size: 1.05rem
    letter-spacing: 0.06em

  .form-actions
    display: flex
    justify-content: flex-end
    gap: 8px

  .drop-zone
    display: flex
    align-items: center
    justify-content: center
    gap: 8px
    margin-top: 16px
    padding: 12px
    border: 1px dashed var(--border-strong)
    border-radius: var(--radius)
    color: var(--text-2)
    font-size: 0.82rem
    text-align: center

    :global(svg)
      flex: 0 0 auto
      color: var(--primary)
      font-size: 1.2rem

    code
      padding: 1px 5px
      border-radius: 4px
      background: var(--surface-2)

  .conflict-grid
    display: grid
    grid-template-columns: 1fr 1fr
    gap: 12px

  .conflict-option
    display: grid
    justify-items: start
    gap: 12px
    height: auto
    padding: 16px
    border-radius: var(--radius-lg)
    font-weight: 400
    text-align: left
    white-space: normal

    &:hover:not(:disabled)
      border-color: var(--primary)
      background: var(--primary-soft)

    dl
      display: grid
      grid-template-columns: auto 1fr
      gap: 4px 14px
      margin: 0
      font-size: 0.88rem

    dt
      color: var(--text-2)

    dd
      margin: 0
      font-weight: 600

    code
      color: var(--text-3)
      font-size: 0.8rem
      overflow-wrap: anywhere

  .choose
    display: inline-flex
    align-items: center
    color: var(--primary)
    font-weight: 600

  @media (max-width: vars.$w-tablet)
    .cards-layout
      grid-template-columns: 1fr

    .link-panel
      position: static
      order: -1

  @media (max-width: vars.$w-mobile)
    .conflict-grid
      grid-template-columns: 1fr
</style>
