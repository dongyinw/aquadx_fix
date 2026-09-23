<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "@iconify/svelte"
  import type { Card, MagicalPassEntry, MagicalPassState, MikuNetUser } from "../libs/generalTypes"
  import { USER } from "../libs/sdk"

  type PassOption = {
    passTypeId: number
    title: string
    subtitle: string
    packId: number
    charaId: number
    accent: string
  }

  const passOptions: PassOption[] = [
    ...[101, 102, 103, 104, 105, 106, 107, 108].map(charaId => ({ passTypeId: 2, title: "金色 Pass", subtitle: "Gold Pass", packId: 7001, charaId: 700000 + charaId, accent: "gold" })),
    ...[201, 202, 203, 204].map(charaId => ({ passTypeId: 3, title: "彩色 Pass", subtitle: "Rainbow Pass", packId: 7002, charaId: 700000 + charaId, accent: "rainbow" })),
    ...[301, 302, 303, 304, 305, 306, 307, 308].map(charaId => ({ passTypeId: 3, title: "彩色 Pass", subtitle: "Rainbow Pass", packId: 7003, charaId: 700000 + charaId, accent: "rainbow" })),
  ]

  const assetRoot = "/assets/mai2/dxpass"
  const backgroundPath = `${assetRoot}/passBG/UI_PassBG_700001.png`
  const logoPath = `${assetRoot}/passverlogo/UI_PassVerLogo_700001.png`

  let me: MikuNetUser | null = null
  let cards: Card[] = []
  let selectedCardId = ""
  let selectedCharaId = 700107
  let state: MagicalPassState | null = null
  let loading = true
  let purchasing = false
  let error = ""
  let notice = ""
  let canvas: HTMLCanvasElement
  let renderId = 0

  $: selectedOption = passOptions.find(option => option.charaId === selectedCharaId) ?? passOptions[0]
  $: currentPass = state?.userPassList?.[0] ?? null
  $: ticketExpiry = state?.userTicketLimitDateList?.find(item => item.itemId === 40001)?.limitDate ?? ""
  $: if (canvas && selectedOption) {
    void renderPreview(selectedOption, currentPass)
  }

  onMount(() => {
    USER.ensureLoggedIn()
    USER.me().then(user => {
      me = user
      cards = [...user.cards].sort((a, b) => Number(b.isGhost) - Number(a.isGhost) || b.registerTime.localeCompare(a.registerTime))
      selectedCardId = cards[0]?.luid ?? ""
      if (selectedCardId) void loadStatus(selectedCardId)
    }).catch(e => {
      error = e instanceof Error ? e.message : String(e)
    }).finally(() => loading = false)
  })

  function formatDate(value: string | undefined | null) {
    if (!value) return "未开通"
    return value.replace("T", " ").replace(/\.\d+$/, "")
  }

  function cardName(card: Card) {
    if (card.isGhost) return "账户虚拟卡"
    return card.luid.length > 12 ? `${card.luid.slice(0, 6)}…${card.luid.slice(-6)}` : card.luid
  }

  async function loadStatus(cardId: string) {
    selectedCardId = cardId
    state = null
    error = ""
    notice = ""
    loading = true
    try {
      state = await USER.mai2PassStatus(cardId)
      const existing = state.userPassList?.[0]
      const matchingOption = passOptions.find(option => option.charaId === existing?.passCharaId)
        ?? passOptions.find(option => option.packId === existing?.passPackId)
      if (matchingOption) selectedCharaId = matchingOption.charaId
    } catch (e) {
      error = e instanceof Error ? e.message : String(e)
    } finally {
      loading = false
    }
  }

  function chooseType(charaId: number) {
    selectedCharaId = charaId
    notice = ""
  }

  async function purchase() {
    if (!selectedCardId || !state?.hasProfile || purchasing) return
    if (!window.confirm(`确认给 ${cardName(cards.find(card => card.luid === selectedCardId) as Card)} 发放${selectedOption.title}吗？`)) return

    purchasing = true
    error = ""
    notice = ""
    try {
      const result = await USER.mai2PassPurchase(selectedCardId, selectedOption.passTypeId, selectedOption.packId, selectedOption.charaId) as MagicalPassState & { success?: boolean }
      state = { ...result, hasProfile: true }
      notice = `${selectedOption.title}已发放，同时获得 1 张票券 40001。`
    } catch (e) {
      error = e instanceof Error ? e.message : String(e)
    } finally {
      purchasing = false
    }
  }

  function loadImage(src: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
      const image = new Image()
      image.onload = () => resolve(image)
      image.onerror = () => reject(new Error(`无法读取素材：${src}`))
      image.src = src
    })
  }

  function roundedRect(ctx: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number) {
    ctx.beginPath()
    ctx.moveTo(x + radius, y)
    ctx.arcTo(x + width, y, x + width, y + height, radius)
    ctx.arcTo(x + width, y + height, x, y + height, radius)
    ctx.arcTo(x, y + height, x, y, radius)
    ctx.arcTo(x, y, x + width, y, radius)
    ctx.closePath()
  }

  function fillRounded(ctx: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number, color: string) {
    roundedRect(ctx, x, y, width, height, radius)
    ctx.fillStyle = color
    ctx.fill()
  }

  function drawCover(ctx: CanvasRenderingContext2D, image: HTMLImageElement, x: number, y: number, width: number, height: number) {
    const scale = Math.max(width / image.width, height / image.height)
    const drawWidth = image.width * scale
    const drawHeight = image.height * scale
    ctx.drawImage(image, x + (width - drawWidth) / 2, y + (height - drawHeight) / 2, drawWidth, drawHeight)
  }

  function drawContain(ctx: CanvasRenderingContext2D, image: HTMLImageElement, x: number, y: number, width: number, height: number) {
    const scale = Math.min(width / image.width, height / image.height)
    const drawWidth = image.width * scale
    const drawHeight = image.height * scale
    ctx.drawImage(image, x + (width - drawWidth) / 2, y + (height - drawHeight) / 2, drawWidth, drawHeight)
  }

  async function renderPreview(option: PassOption, pass: MagicalPassEntry | null) {
    const currentRender = ++renderId
    try {
      const [background, logo, character] = await Promise.all([
        loadImage(backgroundPath),
        loadImage(logoPath),
        loadImage(`${assetRoot}/passchara/UI_PassChara_${option.charaId}.png`),
      ])
      if (currentRender !== renderId || !canvas) return

      const ctx = canvas.getContext("2d")
      if (!ctx) return
      const width = canvas.width
      const height = canvas.height
      ctx.clearRect(0, 0, width, height)
      drawCover(ctx, background, 0, 0, width, height)
      ctx.fillStyle = "rgba(28, 147, 210, 0.48)"
      ctx.fillRect(0, 0, width, height)

      fillRounded(ctx, 44, 24, width - 88, height - 48, 22, "rgba(247, 252, 255, 0.96)")
      ctx.strokeStyle = "rgba(19, 43, 56, 0.9)"
      ctx.lineWidth = 3
      roundedRect(ctx, 44, 24, width - 88, height - 48, 22)
      ctx.stroke()

      drawContain(ctx, logo, 84, 44, 280, 62)
      ctx.fillStyle = option.accent === "gold" ? "#a57816" : "#1787ba"
      ctx.font = "800 30px \"Microsoft YaHei\", sans-serif"
      ctx.fillText(option.title, 84, 150)
      ctx.font = "600 18px \"Microsoft YaHei\", sans-serif"
      ctx.fillStyle = "#53666d"
      ctx.fillText(option.subtitle, 84, 177)

      fillRounded(ctx, 84, 202, width - 168, 382, 15, "rgba(201, 235, 249, 0.88)")
      ctx.strokeStyle = "#17303a"
      ctx.lineWidth = 2
      roundedRect(ctx, 84, 202, width - 168, 382, 15)
      ctx.stroke()

      fillRounded(ctx, 108, 226, 270, 334, 8, "rgba(255, 255, 255, 0.86)")
      drawContain(ctx, character, 118, 236, 250, 314)

      const infoX = 405
      fillRounded(ctx, infoX, 226, 330, 56, 10, "rgba(255, 255, 255, 0.92)")
      ctx.fillStyle = "#132f3a"
      ctx.font = "700 19px \"Microsoft YaHei\", sans-serif"
      ctx.fillText(`有效期限：${pass ? formatDate(pass.startDate) : "购买后生效"}`, infoX + 16, 249)
      ctx.font = "700 18px \"Microsoft YaHei\", sans-serif"
      ctx.fillText(`至 ${pass ? formatDate(pass.endDate) : "购买后计算"}`, infoX + 16, 273)

      const features = [
        ["自由模式", "游玩时间延长"],
        ["DX 分数", "获得额外加成"],
        ["全难度", "解锁完整游玩"],
        ["评级对象", "显示更多乐曲"],
      ]
      features.forEach(([label, detail], index) => {
        const y = 300 + index * 62
        fillRounded(ctx, infoX, y, 330, 49, 9, "rgba(255, 255, 255, 0.93)")
        ctx.fillStyle = ["#3a95e7", "#c8981c", "#ad58cf", "#e55353"][index]
        ctx.font = "800 17px \"Microsoft YaHei\", sans-serif"
        ctx.fillText(label, infoX + 16, y + 21)
        ctx.fillStyle = "#53666d"
        ctx.font = "500 16px \"Microsoft YaHei\", sans-serif"
        ctx.fillText(detail, infoX + 16, y + 42)
      })

      ctx.fillStyle = "#1a7e9f"
      ctx.font = "700 21px \"Microsoft YaHei\", sans-serif"
      ctx.fillText("MikuNet Magical Pass", 84, 628)
      ctx.fillStyle = "#53666d"
      ctx.font = "500 17px \"Microsoft YaHei\", sans-serif"
      ctx.fillText("票券 40001 与 Pass 同期有效", 84, 657)
    } catch (e) {
      if (currentRender === renderId) error = e instanceof Error ? e.message : String(e)
    }
  }

  function downloadComposite() {
    if (!canvas) return
    // The visible canvas is the synthesized Pass artwork on the left. Export only its pixels.
    canvas.toBlob(blob => {
      if (!blob) return
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement("a")
      anchor.href = url
      anchor.download = `MikuNet_${selectedOption.title.replace(/\s/g, "")}_${selectedOption.charaId}_${selectedCardId.slice(-8)}.png`
      anchor.click()
      URL.revokeObjectURL(url)
    }, "image/png")
  }
</script>

<main class="content pass-page">
  <header class="pass-heading">
    <div>
      <span class="eyebrow">MAIMAI DX / MAGICAL PASS</span>
      <h1>Magical Pass</h1>
      <p>选择卡片购买通行证，并下载对应的 Pass 素材。</p>
    </div>
    <div class="heading-badge"><Icon icon="solar:ticket-bold-duotone" /><span>票券 40001</span></div>
  </header>

  {#if error}
    <div class="notice error"><Icon icon="solar:danger-triangle-bold-duotone" />{error}</div>
  {/if}
  {#if notice}
    <div class="notice success"><Icon icon="solar:check-circle-bold-duotone" />{notice}</div>
  {/if}

  {#if !loading && cards.length === 0}
    <section class="empty-panel"><Icon icon="solar:card-search-bold-duotone" /><h2>还没有可用卡片</h2><p>请先绑定一张卡片，再购买 Magical Pass。</p><a href="/cards">前往卡片管理 <Icon icon="line-md:arrow-right" /></a></section>
  {:else}
    <section class="pass-layout">
      <aside class="card-panel">
        <div class="panel-title"><span class="eyebrow">SELECT CARD</span><h2>选择卡片</h2></div>
        <div class="card-list">
          {#each cards as card}
            <button class="card-choice" class:selected={selectedCardId === card.luid} type="button" on:click={() => loadStatus(card.luid)}>
              <span class="card-choice-icon"><Icon icon={card.isGhost ? "solar:user-circle-bold-duotone" : "solar:card-bold-duotone"} /></span>
              <span class="card-choice-copy"><strong>{cardName(card)}</strong><small>{card.isGhost ? "账户虚拟卡" : "实体卡"}</small></span>
              <Icon class="card-choice-arrow" icon="line-md:chevron-small-right" />
            </button>
          {/each}
        </div>
        <a class="manage-link" href="/cards"><Icon icon="solar:settings-bold-duotone" />管理卡片</a>
      </aside>

      <section class="preview-panel">
        <div class="panel-title preview-title"><div><span class="eyebrow">LOCAL ASSET PREVIEW</span><h2>{selectedOption.title}</h2></div><span class="asset-tag">ID {selectedOption.charaId}</span></div>
        <canvas bind:this={canvas} width="840" height="720" aria-label="左侧合成后的 Magical Pass 素材预览"></canvas>
        <div class="preview-actions">
          <button class="primary-action" type="button" on:click={downloadComposite} disabled={loading || !state?.hasProfile}><Icon icon="solar:download-minimalistic-bold-duotone" />下载左侧合成图</button>
          <small>只下载左侧画布中的合成后素材，不会下载网页界面</small>
        </div>
      </section>

      <section class="purchase-panel">
        <div class="panel-title"><span class="eyebrow">PASS STORE</span><h2>选择 Pass ID</h2></div>
        <div class="pass-options">
          {#each passOptions as option}
            <button class="pass-option {option.accent}" class:selected={selectedCharaId === option.charaId} type="button" on:click={() => chooseType(option.charaId)}>
              <span class="pass-option-mark"><Icon icon={option.passTypeId === 2 ? "solar:medal-star-bold-duotone" : "solar:stars-minimalistic-bold-duotone"} /></span>
              <span><strong>{option.title}</strong><small>{option.subtitle} · Pass ID {option.charaId} · Pack {option.packId}</small></span>
              {#if selectedCharaId === option.charaId}<Icon class="option-check" icon="solar:check-circle-bold" />{/if}
            </button>
          {/each}
        </div>

        <div class="current-state">
          <div><span>当前 Pass 到期</span><strong>{formatDate(currentPass?.endDate)}</strong></div>
          <div><span>40001 票券</span><strong>{state?.ticket?.stock ?? 0} 张</strong></div>
          <div><span>票券到期</span><strong>{formatDate(ticketExpiry || currentPass?.endDate)}</strong></div>
        </div>

        {#if state && !state.hasProfile}
          <div class="profile-warning"><Icon icon="solar:info-circle-bold-duotone" /><span>该卡片还没有舞萌 DX 用户资料，暂时无法购买 Magical Pass。</span></div>
        {/if}
        <button class="buy-button" type="button" on:click={purchase} disabled={loading || purchasing || !state?.hasProfile}>
          <Icon icon={purchasing ? "line-md:loading-twotone-loop" : "solar:cart-large-2-bold-duotone"} />
          {purchasing ? "正在发放" : `发放${selectedOption.title}`}
        </button>
        <p class="purchase-note">每次发放 Pass 会同步增加 1 张 40001 票券，票券有效期与本次 Pass 相同。</p>
      </section>
    </section>
  {/if}
</main>

<style lang="sass">
  @use "../vars"

  .pass-page
    gap: 20px

  .pass-heading, .panel-title, .preview-title, .preview-actions, .heading-badge, .notice, .manage-link, .buy-button, .profile-warning
    display: flex
    align-items: center

  .pass-heading
    justify-content: space-between
    gap: 20px

    h1
      margin: 6px 0 4px
      color: vars.$c-text

    p
      margin: 0
      color: vars.$c-sub

  .eyebrow
    color: vars.$c-main
    font-size: 0.7rem
    font-weight: 800
    letter-spacing: 0.13em

  .heading-badge
    gap: 7px
    padding: 10px 13px
    border: 1px solid rgba(255, 255, 255, 0.88)
    border-radius: 12px
    color: #a57816
    background: rgba(255, 255, 255, 0.62)
    font-size: 0.82rem
    font-weight: 700

  .notice
    gap: 8px
    padding: 11px 14px
    border: 1px solid rgba(7, 143, 136, 0.16)
    border-radius: 11px
    color: vars.$c-main
    background: rgba(255, 255, 255, 0.62)

    &.error
      border-color: rgba(196, 73, 94, 0.18)
      color: vars.$c-error

    &.success
      border-color: rgba(19, 134, 111, 0.18)
      color: vars.$c-good

  .pass-layout
    display: grid
    grid-template-columns: 220px minmax(0, 1fr) 270px
    gap: 16px
    align-items: start

  .card-panel, .preview-panel, .purchase-panel
    min-width: 0
    padding: 18px
    border: 1px solid rgba(255, 255, 255, 0.82)
    border-radius: 16px
    background: rgba(255, 255, 255, 0.58)
    box-shadow: 0 16px 38px rgba(43, 72, 76, 0.08)
    backdrop-filter: blur(16px)

  .panel-title
    justify-content: space-between
    gap: 8px
    margin-bottom: 14px

    h2
      margin: 4px 0 0
      color: vars.$c-text
      font-size: 1.28rem

  .card-list, .pass-options
    display: grid
    gap: 8px

  .pass-options
    max-height: 430px
    overflow-y: auto
    padding-right: 4px

  .card-choice, .pass-option
    display: flex
    align-items: center
    gap: 9px
    width: 100%
    min-width: 0
    padding: 10px
    border: 1px solid rgba(96, 114, 118, 0.13)
    border-radius: 11px
    color: vars.$c-text
    background: rgba(255, 255, 255, 0.52)
    text-align: left

    &:hover, &.selected
      border-color: rgba(7, 143, 136, 0.45)
      background: rgba(217, 244, 241, 0.82)

  .card-choice-icon, .pass-option-mark
    display: grid
    place-items: center
    width: 32px
    height: 32px
    flex: 0 0 32px
    border-radius: 9px
    color: vars.$c-main
    background: vars.$c-main-soft
    font-size: 1.15rem

  .card-choice-copy, .pass-option > span:nth-child(2)
    display: grid
    min-width: 0
    flex: 1
    gap: 2px

    strong, small
      overflow: hidden
      text-overflow: ellipsis
      white-space: nowrap

    strong
      font-size: 0.78rem

    small
      color: vars.$c-sub
      font-size: 0.68rem

  .card-choice-arrow, .option-check
    flex: 0 0 auto
    color: vars.$c-muted

  .manage-link
    gap: 5px
    margin-top: 14px
    font-size: 0.78rem

  canvas
    display: block
    width: 100%
    height: auto
    border-radius: 13px
    box-shadow: 0 16px 30px rgba(43, 72, 76, 0.13)

  .preview-title
    align-items: flex-end

  .asset-tag
    padding: 4px 8px
    border-radius: 999px
    color: vars.$c-main
    background: vars.$c-main-soft
    font-size: 0.7rem
    font-weight: 700

  .preview-actions
    justify-content: space-between
    gap: 10px
    margin-top: 13px

    small
      color: vars.$c-sub
      font-size: 0.7rem

  .primary-action, .buy-button
    justify-content: center
    gap: 6px
    color: #fff
    border-color: vars.$c-main
    background: vars.$c-main

    &:hover
      color: #fff
      background: vars.$c-darker

  .primary-action
    padding: 9px 12px
    font-size: 0.8rem

  .pass-option.gold .pass-option-mark
    color: #a57816
    background: rgba(229, 171, 67, 0.17)

  .pass-option.rainbow .pass-option-mark
    color: #b34d88
    background: rgba(239, 135, 181, 0.17)

  .current-state
    display: grid
    gap: 9px
    margin: 18px 0
    padding-top: 15px
    border-top: 1px solid rgba(96, 114, 118, 0.13)

    div
      display: flex
      justify-content: space-between
      gap: 8px

    span
      color: vars.$c-sub
      font-size: 0.73rem

    strong
      color: vars.$c-text
      font-size: 0.76rem
      text-align: right

  .profile-warning
    align-items: flex-start
    gap: 7px
    margin: 12px 0
    padding: 10px
    border-radius: 10px
    color: vars.$c-warning
    background: rgba(229, 171, 67, 0.13)
    font-size: 0.75rem

  .buy-button
    width: 100%
    padding: 11px 12px
    font-size: 0.84rem

    &:disabled, .primary-action:disabled
      cursor: wait
      opacity: 0.52

  .purchase-note
    margin: 10px 0 0
    color: vars.$c-sub
    font-size: 0.7rem
    line-height: 1.6

  .empty-panel
    display: flex
    flex-direction: column
    align-items: center
    padding: 70px 20px
    border: 1px solid rgba(255, 255, 255, 0.82)
    border-radius: 16px
    background: rgba(255, 255, 255, 0.58)
    text-align: center

    > :global(svg)
      color: vars.$c-main
      font-size: 3rem

    h2
      margin: 12px 0 5px

    p
      margin: 0 0 16px
      color: vars.$c-sub

    a
      display: inline-flex
      align-items: center
      gap: 5px

  @media (max-width: 1050px)
    .pass-layout
      grid-template-columns: 190px minmax(0, 1fr)

    .purchase-panel
      grid-column: 1 / -1

    .pass-options
      grid-template-columns: repeat(2, minmax(0, 1fr))

  @media (max-width: vars.$w-mobile)
    .pass-heading
      align-items: flex-start
      flex-direction: column

    .pass-layout
      grid-template-columns: 1fr

    .purchase-panel
      grid-column: auto

    .pass-options
      grid-template-columns: 1fr

    .preview-actions
      align-items: flex-start
      flex-direction: column

  :global(:root[data-theme="dark"])
    .card-panel, .preview-panel, .purchase-panel, .empty-panel, .notice, .heading-badge
      border-color: rgba(214, 250, 245, 0.14)
      background: rgba(21, 34, 36, 0.78)

    .card-choice, .pass-option
      border-color: rgba(214, 250, 245, 0.14)
      color: #e6f3f1
      background: rgba(24, 38, 40, 0.72)

      &:hover, &.selected
        background: rgba(7, 143, 136, 0.18)

    .panel-title h2, .current-state strong
      color: #e6f3f1

    .heading-badge
      color: #e8bf53

    .purchase-note, .current-state span, .card-choice-copy small, .pass-option small
      color: #9fbbb9
</style>
