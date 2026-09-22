<!-- Svelte 4.2.11 -->

<script lang="ts">
  import { fade, slide } from "svelte/transition"
  import type { Card, CardSummary, CardSummaryGame, ConfirmProps, MikuNetUser } from "../../libs/generalTypes"
  import { CARD, USER } from "../../libs/sdk"
  import moment from "moment"
  import Icon from "@iconify/svelte"
  import StatusOverlays from "../../components/StatusOverlays.svelte"
  import { t } from "../../libs/i18n"
  import DashboardTabs from "../../components/DashboardTabs.svelte";
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

  // Access code input
  const inputACRegex = /^(\d{4} ){0,4}\d{0,4}$/
  let elemInputAC: HTMLInputElement
  let inputOldAC = ""
  let inputAC = ""
  let errorAC = ""
  let warningAC = ""

  function inputACChange() {
    // Add spaces to the input
    const cursorIndex = cursorPositionToCursorIndex(inputAC, elemInputAC.selectionStart ?? 0, /\d/)
    inputAC = inputAC.replace(/\D/g, '').replace(/(.{4})/g, '$1 ').replace(/ $/, '')
    const cursorPosition = cursorIndexToCursorPosition(inputAC, cursorIndex, /\d/)
    setTimeout(() => elemInputAC.selectionStart = elemInputAC.selectionEnd = cursorPosition, 0)
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
    const cursorIndex = cursorPositionToCursorIndex(inputSN, inputElemSN.selectionStart ?? 0, /[0-9A-F]/)
    inputSN = inputSN.replace(/[^0-9A-F]/g, '').replace(/(.{2})/g, '$1:').replace(/:$/, '')
    const cursorPosition = cursorIndexToCursorPosition(inputSN, cursorIndex, /[0-9A-F]/)
    setTimeout(() => inputElemSN.selectionStart = inputElemSN.selectionEnd = cursorPosition, 0)
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

<main class="content cards-page">
  <header class="page-heading">
    <div><span class="eyebrow">IDENTITY / CARDS</span><h1>卡片管理</h1><p>管理已绑定的实体卡，并把新的 Access Code 或序列号安全加入账号。</p></div>
    <div class="count-chip"><Icon icon="solar:card-bold-duotone" />{me?.cards?.filter(card => !card.isGhost).length ?? 0} 张实体卡</div>
  </header>
  <DashboardTabs />
  <!-- svelte-ignore a11y-no-static-element-interactions -->
  <div class="cards-layout" on:drop={dropFile} on:dragover={(e) => e.preventDefault()}>
    <section class="glass-panel linked-panel">
      <div class="panel-heading"><div><span class="eyebrow">LINKED</span><h2>已绑定卡片</h2></div><span class="panel-note">悬停卡号可查看完整信息</span></div>
      {#if me && me.cards && me.cards.find(card => !card.isGhost)}
        <div class="existing-cards" transition:slide>
          {#each me.cards as card (card.luid)}
            {#if !card.isGhost}
              <article class={`card-tile ${cardType(card.luid) == "FeliCa SN" ? "sn" : "ac"}`} transition:fade|global>
                <div class="card-tile-top"><span class="card-kind"><Icon icon={cardType(card.luid) == "FeliCa SN" ? "solar:cpu-bolt-bold-duotone" : "solar:card-bold-duotone"} />{cardType(card.luid)}</span><button class="icon error" title="解除绑定" aria-label="解除绑定" on:click={() => unlink(card)}><Icon icon="tabler:trash-x-filled"/></button></div>
                <strong class="card-id">{@html formatLUID(card.luid, card.isGhost).split(" ").map(v => `<i>${v}</i>`).join("").split(":").map((v, i, a) => `<i>${v}${i < a.length - 1 ? ":" : ""}</i>`).join("")}</strong>
                <div class="card-meta"><span>{t('home.linkcard.registered')}<b>{moment(card.registerTime).format("YYYY-MM-DD")}</b></span><span>{t('home.linkcard.lastused')}<b>{moment(card.accessTime).format("YYYY-MM-DD")}</b></span></div>
              </article>
            {/if}
          {/each}
        </div>
      {:else}
        <div class="empty-panel"><Icon icon="solar:card-search-bold-duotone" /><strong>还没有绑定实体卡</strong><span>在右侧输入卡号开始绑定。</span></div>
      {/if}
      <blockquote class="info"><Icon icon="solar:shield-check-bold-duotone" />{t('home.linkcard.card-security-warning')}</blockquote>
    </section>

    <section class="glass-panel link-panel">
      <div class="panel-heading"><div><span class="eyebrow">ADD CARD</span><h2>绑定新卡片</h2></div><span class="drop-hint"><Icon icon="solar:download-minimalistic-bold-duotone" />可拖入卡片文件</span></div>
      <p class="panel-description">{t('home.linkcard.enter-info')}</p>
      <div class="link-methods">
        {#if !inputSN}
          <div class="link-method" out:slide={{ duration: 250 }}><div class="method-title"><span class="method-number">01</span><div><strong>{t('home.linkcard.access-code')}</strong><small>20 位 Access Code</small></div></div><label><!-- DO NOT change the order of bind:value and on:input. --><input bind:this={elemInputAC} placeholder="0008 1234 5678 8765 4321" on:keydown={(e) => { e.key === "Enter" && link('AC'); if (isInput(e) && !/[\d ]/.test(e.key)) e.preventDefault() }} bind:value={inputAC} on:input={inputACChange} class:error={inputAC && (!inputACRegex.test(inputAC) || errorAC)} class:warning={inputAC && warningAC}>{#if inputAC.length > 0}<button class="primary-action" transition:slide={{axis: 'x'}} on:click={() => link('AC')}><Icon icon="solar:link-bold" />{t('home.linkcard.link')}</button>{:else}<button class="secondary-action" on:click={() => generateRandom()}><Icon icon="solar:dice-bold" />生成示例</button>{/if}</label>{#if errorAC}<p class="error" transition:slide>{errorAC}</p>{/if}{#if warningAC}<div class="warning-copy" transition:slide>{#each warningAC.trim().split("\n") as paragraph}<p class="warning">{paragraph}</p>{/each}</div>{/if}</div>
        {/if}
        {#if !inputAC}
          <div class="link-method" out:slide={{ duration: 250 }}><div class="method-title"><span class="method-number">02</span><div><strong>{@html t('home.linkcard.enter-sn')}</strong><small>FeliCa 序列号</small></div></div><label><input bind:this={inputElemSN} placeholder="01:2E:1A:2B:3C:4D:5E:6F" on:keydown={(e) => { e.key === "Enter" && link('SN'); if (isInput(e) && !/[0-9A-Fa-f:]/.test(e.key)) e.preventDefault() }} bind:value={inputSN} on:input={inputSNChange} class:error={inputSN && (!inputSNRegex.test(inputSN) || errorSN)}>{#if inputSN.length > 0}<button class="primary-action" transition:slide={{axis: 'x'}} on:click={() => link('SN')}><Icon icon="solar:link-bold" />{t('home.linkcard.link')}</button>{/if}</label>{#if errorSN}<p class="error" transition:slide>{errorSN}</p>{/if}</div>
        {/if}
      </div>
      <div class="drop-zone"><Icon icon="solar:cloud-upload-bold-duotone" /><span>支持拖入 `aime.txt` 或 `felica.txt` 自动填充</span></div>
    </section>
  </div>

    {#if conflictOld && conflictNew && me}
      <div class="overlay" transition:fade>
        <div>
          <h2>{t('home.linkcard.data-conflict')}</h2>
          <p></p>
          <div class="conflict-cards">
            <div class="old card clickable" on:click={() => linkConflictContinue('old')}
                role="button" tabindex="0" on:keydown={e => e.key === "Enter" && linkConflictContinue('old')}>
              <span class="type">{t('home.linkcard.account-card')}</span>
              <span>{t('home.linkcard.name')}: {conflictOld.name}</span>
              <span>{t('home.linkcard.rating')}: {conflictOld.rating}</span>
              <span>{t('home.linkcard.last-login')}: {moment(conflictOld.lastLogin).format("YYYY MMM DD")}</span>
              <span class="id">{formatLUID(me.ghostCard.luid, true)}</span>
            </div>
            <div class="new card clickable" on:click={() => linkConflictContinue('new')}
                role="button" tabindex="0" on:keydown={e => e.key === "Enter" && linkConflictContinue('new')}>
              <span class="type">{cardType(conflictCardID)}</span>
              <span>{t('home.linkcard.name')}: {conflictNew.name}</span>
              <span>{t('home.linkcard.rating')}: {conflictNew.rating}</span>
              <span>{t('home.linkcard.last-login')}: {moment(conflictNew.lastLogin).format("YYYY MMM DD")}</span>
              <span class="id">{conflictCardID}</span>
            </div>
          </div>
          <button class="error" on:click={linkConflictCancel}>{t('action.cancel')}</button>
        </div>
      </div>
    {/if}

</main>
<StatusOverlays bind:confirm={showConfirm} bind:error={error} loading={!me} />

<style lang="sass">
  @use "../../vars"

  .page-heading, .panel-heading, .card-tile-top, .method-title
    display: flex
    align-items: center
    justify-content: space-between
    gap: 14px

  .page-heading
    h1
      margin: 6px 0 4px

    p
      margin: 0
      color: vars.$c-sub
      font-size: 0.88rem

  .eyebrow
    color: vars.$c-main
    font-size: 0.7rem
    font-weight: 800
    letter-spacing: 0.14em

  .count-chip, .drop-hint
    display: inline-flex
    align-items: center
    gap: 7px
    padding: 8px 11px
    border: 1px solid rgba(7, 143, 136, 0.18)
    border-radius: 999px
    color: vars.$c-main
    background: rgba(7, 143, 136, 0.08)
    font-size: 0.76rem

  .cards-layout
    display: grid
    grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr)
    gap: 16px

  .glass-panel
    min-width: 0
    padding: 22px
    border: 1px solid rgba(255, 255, 255, 0.86)
    border-radius: 18px
    background: rgba(255, 255, 255, 0.64)
    box-shadow: 0 18px 46px rgba(43, 72, 76, 0.08)
    backdrop-filter: blur(18px)

  .panel-heading
    align-items: flex-start
    margin-bottom: 18px

    h2
      margin: 5px 0 0
      font-size: 1.3rem

  .panel-note, .panel-description
    color: vars.$c-sub
    font-size: 0.78rem

  .panel-description
    margin: 0 0 18px

  .existing-cards
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(230px, 1fr))
    gap: 11px

  .card-tile
    display: grid
    gap: 14px
    min-height: 132px
    padding: 15px
    border: 1px solid rgba(7, 143, 136, 0.12)
    border-radius: 15px
    background: linear-gradient(145deg, rgba(255, 255, 255, 0.88), rgba(217, 244, 241, 0.62))
    transition: vars.$transition

    &:hover
      border-color: rgba(7, 143, 136, 0.42)
      transform: translateY(-2px)

    &.sn
      background: linear-gradient(145deg, rgba(255, 255, 255, 0.88), rgba(255, 238, 229, 0.72))

  .card-kind
    display: inline-flex
    align-items: center
    gap: 6px
    color: vars.$c-main
    font-size: 0.76rem
    font-weight: 700

  .card-id
    overflow: hidden
    color: vars.$c-text
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace
    font-size: 0.93rem
    letter-spacing: 0.03em
    text-overflow: ellipsis
    white-space: nowrap

    :global(i)
      display: inline-block
      margin-right: 0.25em
      font-style: normal
      transition: 250ms filter

    :global(i:nth-child(n+4))
      filter: blur(7px)

    &:hover :global(i)
      filter: none

  .card-meta
    display: flex
    flex-wrap: wrap
    gap: 12px
    color: vars.$c-sub
    font-size: 0.7rem

    span
      display: grid
      gap: 2px

    b
      color: vars.$c-text
      font-weight: 600

  .icon
    display: inline-flex
    align-items: center
    justify-content: center
    width: 34px
    height: 34px
    padding: 0
    border-radius: 10px

  .empty-panel
    display: grid
    place-items: center
    gap: 6px
    min-height: 190px
    color: vars.$c-sub
    text-align: center

    :global(svg)
      color: vars.$c-main
      font-size: 2.3rem

    strong
      color: vars.$c-text

  blockquote.info
    display: flex
    align-items: flex-start
    gap: 8px
    margin-bottom: 0

  .link-methods
    display: grid
    gap: 12px

  .link-method
    display: grid
    gap: 13px
    padding: 15px
    border: 1px solid rgba(96, 114, 118, 0.13)
    border-radius: 14px
    background: rgba(255, 255, 255, 0.48)

    label
      display: flex
      align-items: center
      gap: 8px

      input
        min-width: 0

    .error, .warning
      margin: 0
      font-size: 0.76rem

  .method-title
    justify-content: flex-start

    > div
      display: grid
      gap: 1px

    strong
      color: vars.$c-text
      font-size: 0.88rem

    small
      color: vars.$c-sub
      font-size: 0.72rem

  .method-number
    display: grid
    place-items: center
    width: 31px
    height: 31px
    border-radius: 10px
    color: vars.$c-main
    background: vars.$c-main-soft
    font-size: 0.75rem
    font-weight: 800

  .primary-action, .secondary-action
    display: inline-flex
    align-items: center
    gap: 5px
    flex: 0 0 auto
    margin: 0

  .primary-action
    color: #fff
    background: vars.$c-main

    &:hover
      color: #fff
      background: vars.$c-darker

  .drop-zone
    display: flex
    align-items: center
    justify-content: center
    gap: 7px
    margin-top: 16px
    padding: 12px
    border: 1px dashed rgba(7, 143, 136, 0.32)
    border-radius: 12px
    color: vars.$c-sub
    background: rgba(7, 143, 136, 0.045)
    font-size: 0.74rem

  .conflict-cards
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr))
    gap: 1rem

    .card
      span:not(.type)
        font-size: 0.8rem

    .old
      background: rgba(255, 107, 107, 0.12)
      border: 1px solid vars.$c-error

    .new
      background: rgba(100, 108, 255, 0.10)
      border: 1px solid vars.$c-main

  @media (max-width: 820px)
    .cards-layout
      grid-template-columns: 1fr

  @media (max-width: vars.$w-mobile)
    .page-heading
      align-items: flex-start
      flex-direction: column

    .glass-panel
      padding: 17px

    .panel-heading
      align-items: flex-start
      flex-direction: column

    .link-method label
      align-items: stretch
      flex-direction: column
</style>
