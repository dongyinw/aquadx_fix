<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../components/ui/Icon.svelte"
  import type { Card, MagicalPassEntry, MagicalPassState, MikuNetUser } from "../libs/generalTypes"
  import { USER } from "../libs/sdk"
  import PageHeader from "../components/ui/PageHeader.svelte"
  import EmptyState from "../components/ui/EmptyState.svelte"
  import Spinner from "../components/ui/Spinner.svelte"
  import { confirmDialog } from "../libs/confirm"
  import { toast } from "../libs/toast"

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

  const thumb = (charaId: number) => `${assetRoot}/passchara/UI_PassChara_${charaId}.png`
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
      cards = user.ghostCard ? [{ ...user.ghostCard, isGhost: true }] : user.cards.filter(card => card.isGhost).slice(0, 1)
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
    if (!await confirmDialog({
      title: `发放${selectedOption.title}`,
      message: `确认给 ${cardName(cards.find(card => card.luid === selectedCardId) as Card)} 发放${selectedOption.title}（Pass ID ${selectedOption.charaId}）吗？\n同时会获得 1 张票券 40001。`,
      confirmText: "确认发放",
    })) return

    purchasing = true
    error = ""
    notice = ""
    try {
      const result = await USER.mai2PassPurchase(selectedCardId, selectedOption.passTypeId, selectedOption.packId, selectedOption.charaId) as MagicalPassState & { success?: boolean }
      state = { ...result, hasProfile: true }
      notice = `${selectedOption.title}已发放，同时获得 1 张票券 40001。`
      toast.success(notice)
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

  const CANVAS_FONT = '"PingFang SC", "Microsoft YaHei UI", "Microsoft YaHei", "Noto Sans CJK SC", "Noto Sans SC", sans-serif'
  const font = (weight: number, size: number) => `${weight} ${size}px ${CANVAS_FONT}`

  // Accent palette per Pass type.
  function accentGradient(ctx: CanvasRenderingContext2D, accent: string, x0: number, y0: number, x1: number, y1: number) {
    const gradient = ctx.createLinearGradient(x0, y0, x1, y1)
    const stops = accent === "gold"
      ? ["#fbe38b", "#f2b93b", "#d98e0b"]
      : ["#ff7eb6", "#ffb86b", "#7ee0c3", "#6aa8ff", "#b28cff"]
    stops.forEach((color, i) => gradient.addColorStop(i / (stops.length - 1), color))
    return gradient
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
      const W = canvas.width
      const H = canvas.height
      const gold = option.accent === "gold"
      ctx.clearRect(0, 0, W, H)
      ctx.imageSmoothingQuality = "high"

      // Card clip with rounded corners.
      ctx.save()
      roundedRect(ctx, 0, 0, W, H, 44)
      ctx.clip()

      // Background: softly blurred to hide upscaling, then tinted.
      ctx.filter = "blur(3px) saturate(1.15)"
      drawCover(ctx, background, -12, -12, W + 24, H + 24)
      ctx.filter = "none"
      const tint = ctx.createLinearGradient(0, 0, 0, H)
      tint.addColorStop(0, gold ? "rgba(255, 236, 190, 0.18)" : "rgba(214, 240, 255, 0.12)")
      tint.addColorStop(0.55, "rgba(255, 255, 255, 0)")
      tint.addColorStop(1, gold ? "rgba(120, 72, 8, 0.38)" : "rgba(28, 52, 110, 0.38)")
      ctx.fillStyle = tint
      ctx.fillRect(0, 0, W, H)

      // Logo
      ctx.shadowColor = "rgba(0, 0, 0, 0.25)"
      ctx.shadowBlur = 18
      drawContain(ctx, logo, W / 2 - 170, 34, 340, 170)
      ctx.shadowBlur = 0

      // Character with a soft halo behind it.
      const charH = 640, charW = charH / 2
      const charX = W / 2 - charW / 2, charY = 168
      const halo = ctx.createRadialGradient(W / 2, charY + charH * 0.42, 20, W / 2, charY + charH * 0.42, charH * 0.55)
      halo.addColorStop(0, "rgba(255, 255, 255, 0.85)")
      halo.addColorStop(0.55, gold ? "rgba(255, 228, 160, 0.35)" : "rgba(200, 225, 255, 0.35)")
      halo.addColorStop(1, "rgba(255, 255, 255, 0)")
      ctx.fillStyle = halo
      ctx.fillRect(0, charY - 40, W, charH + 80)
      // Some character art ships with an opaque backdrop; feather the edges so it blends in.
      const layer = document.createElement("canvas")
      layer.width = charW
      layer.height = charH
      const lctx = layer.getContext("2d")!
      lctx.imageSmoothingQuality = "high"
      drawContain(lctx, character, 0, 0, charW, charH)
      lctx.globalCompositeOperation = "destination-in"
      const fadeX = lctx.createLinearGradient(0, 0, charW, 0)
      fadeX.addColorStop(0, "rgba(0, 0, 0, 0)")
      fadeX.addColorStop(0.14, "#000")
      fadeX.addColorStop(0.86, "#000")
      fadeX.addColorStop(1, "rgba(0, 0, 0, 0)")
      lctx.fillStyle = fadeX
      lctx.fillRect(0, 0, charW, charH)
      const fadeY = lctx.createLinearGradient(0, 0, 0, charH)
      fadeY.addColorStop(0, "rgba(0, 0, 0, 0)")
      fadeY.addColorStop(0.06, "#000")
      fadeY.addColorStop(0.82, "#000")
      fadeY.addColorStop(1, "rgba(0, 0, 0, 0)")
      lctx.fillStyle = fadeY
      lctx.fillRect(0, 0, charW, charH)
      ctx.drawImage(layer, charX, charY)

      // Frosted info panel.
      const px = 40, py = 770, pw = W - 80, ph = H - py - 40
      ctx.shadowColor = "rgba(15, 30, 50, 0.28)"
      ctx.shadowBlur = 40
      ctx.shadowOffsetY = 14
      fillRounded(ctx, px, py, pw, ph, 30, "rgba(255, 255, 255, 0.92)")
      ctx.shadowColor = "transparent"
      ctx.shadowBlur = 0
      ctx.shadowOffsetY = 0
      // Accent strip along the top of the panel.
      ctx.save()
      roundedRect(ctx, px, py, pw, ph, 30)
      ctx.clip()
      ctx.fillStyle = accentGradient(ctx, option.accent, px, 0, px + pw, 0)
      ctx.fillRect(px, py, pw, 8)
      ctx.restore()

      // Title row: type chip + Pass name.
      const chipText = gold ? "GOLD" : "RAINBOW"
      ctx.font = font(800, 20)
      const chipW = ctx.measureText(chipText).width + 32
      fillRounded(ctx, px + 32, py + 36, chipW, 36, 18, "#000")
      ctx.save()
      roundedRect(ctx, px + 32, py + 36, chipW, 36, 18)
      ctx.clip()
      ctx.fillStyle = accentGradient(ctx, option.accent, px + 32, 0, px + 32 + chipW, 0)
      ctx.fillRect(px + 32, py + 36, chipW, 36)
      ctx.restore()
      ctx.fillStyle = gold ? "#5a3a00" : "#ffffff"
      ctx.textBaseline = "middle"
      ctx.fillText(chipText, px + 48, py + 55)

      ctx.fillStyle = "#18262b"
      ctx.font = font(800, 40)
      ctx.fillText(option.title, px + 32 + chipW + 18, py + 55)

      // Validity
      ctx.fillStyle = "#5c6d72"
      ctx.font = font(600, 21)
      const start = pass ? formatDate(pass.startDate).slice(0, 16) : "购买后生效"
      const end = pass ? formatDate(pass.endDate).slice(0, 16) : "购买后计算"
      ctx.fillText(`有效期  ${start}  →  ${end}`, px + 32, py + 112)

      // Feature grid (2 x 2)
      const features: [string, string, string][] = [
        ["自由模式", "游玩时间延长", "#3a95e7"],
        ["DX 分数", "获得额外加成", "#d39a14"],
        ["全难度", "解锁完整游玩", "#a855f7"],
        ["评级对象", "显示更多乐曲", "#e5536b"],
      ]
      const gx = px + 32, gy = py + 148, gap = 14
      const cellW = (pw - 64 - gap) / 2, cellH = 78
      features.forEach(([label, detail, color], i) => {
        const cx = gx + (i % 2) * (cellW + gap)
        const cy = gy + Math.floor(i / 2) * (cellH + gap)
        fillRounded(ctx, cx, cy, cellW, cellH, 16, "rgba(24, 38, 43, 0.045)")
        ctx.fillStyle = color
        ctx.beginPath()
        ctx.arc(cx + 30, cy + cellH / 2, 9, 0, Math.PI * 2)
        ctx.fill()
        ctx.fillStyle = "#18262b"
        ctx.font = font(800, 24)
        ctx.fillText(label, cx + 54, cy + 28)
        ctx.fillStyle = "#66777b"
        ctx.font = font(500, 19)
        ctx.fillText(detail, cx + 54, cy + 55)
      })

      // Footer
      const fy = py + ph - 36
      ctx.fillStyle = "#0a8079"
      ctx.font = font(800, 22)
      ctx.fillText("MikuNet Magical Pass", px + 32, fy)
      ctx.fillStyle = "#829296"
      ctx.font = font(600, 18)
      ctx.textAlign = "right"
      ctx.fillText(`Pass ID ${option.charaId} · 票券 40001 同期有效`, px + pw - 32, fy)
      ctx.textAlign = "left"
      ctx.textBaseline = "alphabetic"

      ctx.restore()
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

<main class="page pass-page">
  <PageHeader eyebrow="maimai DX" title="Magical Pass" description="选择 Pass 样式并发放到账户卡，随后可下载对应的 Pass 合成素材。">
    <span class="badge warning"><Icon icon="solar:ticket-bold-duotone" />票券 40001 · {state?.ticket?.stock ?? 0} 张</span>
  </PageHeader>

  {#if error}
    <div class="alert alert-danger" role="alert"><Icon icon="solar:danger-triangle-bold-duotone" /><p>{error}</p></div>
  {/if}
  {#if notice}
    <div class="alert alert-success" role="status"><Icon icon="solar:check-circle-bold-duotone" /><p>{notice}</p></div>
  {/if}

  {#if !loading && cards.length === 0}
    <EmptyState icon="solar:card-search-bold-duotone" title="还没有可用卡片" description="请先绑定一张卡片，再购买 Magical Pass。">
      <a class="btn btn-primary" href="/cards">前往卡片管理<Icon icon="line-md:chevron-small-right" /></a>
    </EmptyState>
  {:else}
    <div class="pass-layout">
      <section class="card preview">
        <div class="card-header">
          <div><h2>{selectedOption.title}</h2><p>{selectedOption.subtitle} · Pack {selectedOption.packId}</p></div>
          <span class="badge primary mono">ID {selectedOption.charaId}</span>
        </div>
        <div class="canvas-stage"><canvas bind:this={canvas} width="800" height="1200" aria-label="合成后的 Magical Pass 素材预览"></canvas></div>
        <div class="preview-actions">
          <small class="subtle">仅下载上方画布中的合成素材</small>
          <button class="btn-soft" type="button" on:click={downloadComposite} disabled={loading || !state?.hasProfile}>
            <Icon icon="solar:download-minimalistic-bold-duotone" />下载合成图
          </button>
        </div>
      </section>

      <aside class="side">
        <section class="card">
          <div class="card-header"><div><h3>发放到</h3></div><a class="btn btn-ghost btn-sm" href="/cards"><Icon icon="solar:settings-bold-duotone" />管理卡片</a></div>
          <div class="card-list" role="radiogroup" aria-label="选择卡片">
            {#each cards as card}
              <button class="choice" class:selected={selectedCardId === card.luid} type="button" role="radio" aria-checked={selectedCardId === card.luid} on:click={() => loadStatus(card.luid)}>
                <span class="tile-icon tone-teal"><Icon icon={card.isGhost ? "solar:user-circle-bold-duotone" : "solar:card-bold-duotone"} /></span>
                <span class="tile-copy"><strong>{cardName(card)}</strong><small>{card.isGhost ? "账户虚拟卡" : "实体卡"}</small></span>
                {#if selectedCardId === card.luid}<Icon class="check" icon="solar:check-circle-bold" />{/if}
              </button>
            {/each}
          </div>
          <dl class="current-state">
            <div><dt>当前 Pass 到期</dt><dd>{formatDate(currentPass?.endDate)}</dd></div>
            <div><dt>40001 票券</dt><dd class="num">{state?.ticket?.stock ?? 0} 张</dd></div>
            <div><dt>票券到期</dt><dd>{formatDate(ticketExpiry || currentPass?.endDate)}</dd></div>
          </dl>
        </section>

        <section class="card">
          <div class="card-header"><div><h3>选择 Pass 样式</h3><p>{passOptions.length} 种可选</p></div></div>
          <div class="pass-options" role="radiogroup" aria-label="Pass 样式">
            {#each passOptions as option (option.charaId)}
              <button class="pass-option {option.accent}" class:selected={selectedCharaId === option.charaId} type="button" role="radio"
                      aria-checked={selectedCharaId === option.charaId} title="{option.title} · Pass ID {option.charaId}" on:click={() => chooseType(option.charaId)}>
                <img src={thumb(option.charaId)} alt="" loading="lazy" />
                <span class="option-label"><Icon icon={option.passTypeId === 2 ? "solar:medal-star-bold-duotone" : "solar:stars-minimalistic-bold-duotone"} />{option.charaId % 1000}</span>
              </button>
            {/each}
          </div>
        </section>

        <section class="card buy">
          {#if state && !state.hasProfile}
            <div class="alert"><Icon icon="solar:info-circle-bold-duotone" /><p>该卡片还没有舞萌 DX 用户资料，暂时无法购买 Magical Pass。</p></div>
          {/if}
          <button class="btn-primary btn-lg btn-block" type="button" on:click={purchase} disabled={loading || purchasing || !state?.hasProfile}>
            {#if purchasing}<Spinner />{:else}<Icon icon="solar:cart-large-2-bold-duotone" />{/if}
            {purchasing ? "正在发放…" : `发放${selectedOption.title}`}
          </button>
          <p class="subtle note">每次发放 Pass 会同步增加 1 张 40001 票券，票券有效期与本次 Pass 相同。</p>
        </section>
      </aside>
    </div>
  {/if}
</main>

<style lang="sass">
  @use "../vars"

  .pass-layout
    display: grid
    grid-template-columns: minmax(300px, 440px) minmax(0, 1fr)
    align-items: start
    gap: 16px

  .preview
    position: sticky
    top: calc(var(--nav-height) + 16px)

  .canvas-stage
    display: flex
    justify-content: center
    padding: 18px
    border-radius: var(--radius)
    background: radial-gradient(circle at 50% 30%, var(--primary-soft), var(--surface-2) 70%)

  canvas
    display: block
    width: auto
    max-width: 100%
    height: auto
    max-height: calc(100dvh - 300px)
    filter: drop-shadow(0 14px 28px rgba(15, 30, 50, 0.22))

  .preview-actions
    display: flex
    align-items: center
    justify-content: space-between
    gap: 10px
    margin-top: 14px

    small
      font-size: 0.8rem

  .side
    display: grid
    gap: 16px
    min-width: 0

  .card-list
    display: grid
    gap: 8px

  .choice
    justify-content: flex-start
    gap: 12px
    height: auto
    min-height: 60px
    padding: 8px 12px 8px 8px
    border-radius: var(--radius)
    font-weight: 400
    text-align: left

    &.selected
      border-color: var(--primary)
      background: var(--primary-soft)

    :global(.check)
      color: var(--primary)
      font-size: 1.2rem

    .tile-icon
      width: 38px
      height: 38px
      flex-basis: 38px
      font-size: 1.2rem

  .current-state
    display: grid
    gap: 8px
    margin: 16px 0 0
    padding-top: 14px
    border-top: 1px solid var(--border)

    div
      display: flex
      justify-content: space-between
      gap: 8px
      font-size: 0.86rem

    dt
      color: var(--text-2)

    dd
      margin: 0
      font-weight: 600
      text-align: right

  .pass-options
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(72px, 1fr))
    gap: 8px
    max-height: 360px
    overflow-y: auto
    padding: 2px

  .pass-option
    position: relative
    flex-direction: column
    gap: 0
    height: auto
    min-height: 0
    padding: 6px 4px 4px
    border-radius: var(--radius)
    overflow: hidden

    img
      width: 100%
      aspect-ratio: 3 / 4
      object-fit: cover
      object-position: top

    &.gold
      background: linear-gradient(180deg, var(--surface), var(--warning-soft))

    &.rainbow
      background: linear-gradient(180deg, var(--surface), var(--accent-soft))

    &.selected
      border-color: var(--primary)
      box-shadow: 0 0 0 2px var(--primary)

  .option-label
    display: inline-flex
    align-items: center
    gap: 3px
    color: var(--text-2)
    font-size: 0.72rem

    .gold &
      color: var(--warning)

    .rainbow &
      color: var(--accent)

  .buy
    gap: 12px

  .note
    margin: 0
    font-size: 0.8rem
    line-height: 1.6

  @media (max-width: vars.$w-tablet)
    .pass-layout
      grid-template-columns: 1fr

    .preview
      position: static

  @media (max-width: vars.$w-mobile)
    .preview-actions
      flex-direction: column-reverse
      align-items: stretch
</style>
