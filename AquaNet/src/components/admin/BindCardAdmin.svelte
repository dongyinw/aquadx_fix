<!-- Bind a card to a user: enter the card number and the user name, check what is on the card and on the account, then bind. -->
<script lang="ts">
  import Icon from "../ui/Icon.svelte"
  import Spinner from "../ui/Spinner.svelte"
  import { CARD_BIND } from "../../libs/sdk"
  import type { BindGameData, BindPreview } from "../../libs/sdk"
  import { errorMessage, toast } from "../../libs/toast"
  import { confirmDialog } from "../../libs/confirm"

  /** Pre-filled when the admin comes from a user or card page. */
  export let cardId = ""
  export let username = ""

  let preview: BindPreview | null = null
  let migrate: Record<string, boolean> = {}
  let force = false
  let checking = false
  let binding = false

  const describe = (data: BindGameData | null) => data ? `${data.name || "（无名）"} · Rating ${data.rating}` : "无数据"

  $: owner = preview?.card?.owner ?? null
  $: needsForce = !!owner
  $: ready = !!preview && !preview.card?.isGhost && (!needsForce || force)

  async function check() {
    if (!cardId.trim() || !username.trim()) return toast.error("请填写卡号和用户名")
    checking = true
    preview = null
    force = false
    try {
      const result = await CARD_BIND.preview(cardId.trim(), username.trim())
      // Games with data on the card are moved by default, as the user's own "link card" does.
      migrate = Object.fromEntries(result.games.map(item => [item.game, !!item.onCard]))
      preview = result
    } catch (e) { toast.error(errorMessage(e)) } finally { checking = false }
  }

  /** Games whose data would replace data the account already has. */
  $: replaced = preview ? preview.games.filter(item => migrate[item.game] && item.onCard && item.onAccount) : []
  /** Games with data on the card that is left behind, which detaches it from the card. */
  $: detached = preview ? preview.games.filter(item => !migrate[item.game] && item.onCard) : []

  async function bind() {
    if (!preview || !ready) return
    const chosen = Object.keys(migrate).filter(game => migrate[game])
    const lines = [
      preview.card ? `把卡 ${preview.card.luid} 绑定给「${preview.user.username}」。` : `创建新卡 ${preview.newCardId} 并绑定给「${preview.user.username}」。`,
      owner ? `会先从「${owner}」名下解除绑定。` : "",
      replaced.length ? `会替换账户现有的数据：${replaced.map(item => item.label).join("、")}（原数据会被归档）。` : "",
      detached.length ? `不迁移的数据会和这张卡分离：${detached.map(item => item.label).join("、")}。` : "",
    ].filter(Boolean)
    if (!await confirmDialog({ title: "确认绑定", message: lines.join("\n"), confirmText: "确认绑定", danger: replaced.length > 0 || detached.length > 0 })) return
    binding = true
    try {
      const result = await CARD_BIND.bind(cardId.trim(), username.trim(), chosen, force)
      toast.success(result.created ? "已创建新卡并绑定" : `已绑定${result.previousOwner ? `（原属于「${result.previousOwner}」）` : ""}`)
      preview = null
      cardId = ""
    } catch (e) { toast.error(errorMessage(e)) } finally { binding = false }
  }
</script>

<section class="bind">
  <h2>绑定卡片</h2>
  <p class="hint">输入 Aime 卡号和用户名，把这张卡绑定给这个用户。绑定前会先显示卡上和账户里已有的游戏数据，由你决定迁移哪些。</p>

  <form class="inputs card" on:submit|preventDefault={check}>
    <label class="field"><span class="field-label">卡号</span>
      <input bind:value={cardId} placeholder="20 位 Aime 卡号，或带冒号的 Felica IDm" autocomplete="off" required />
    </label>
    <label class="field"><span class="field-label">用户名</span>
      <input bind:value={username} placeholder="要绑定到的网站用户名" autocomplete="off" required />
    </label>
    <button class="btn-primary" type="submit" disabled={checking}>{#if checking}<Spinner />{:else}<Icon icon="solar:magnifer-bold-duotone" />{/if}检查</button>
  </form>

  {#if preview}
    <section class="card result">
      <div class="who">
        <div><small>用户</small><strong>{preview.user.displayName || preview.user.username}</strong><span class="subtle">@{preview.user.username} · 已绑定 {preview.user.cardCount} 张卡</span></div>
        <div>
          <small>卡片</small>
          {#if preview.card}
            <strong class="mono">{preview.card.luid}</strong>
            <span class="subtle">{preview.card.isGhost ? "这是账户虚拟卡，不能绑定" : owner ? `已绑定给「${owner}」` : "尚未绑定任何用户"}</span>
          {:else}
            <strong class="mono">{preview.newCardId}</strong>
            <span class="subtle">系统里还没有这张卡，会创建新卡并绑定</span>
          {/if}
        </div>
      </div>

      {#if preview.card?.isGhost}
        <div class="alert alert-danger"><Icon icon="solar:danger-triangle-bold-duotone" /><p>账户虚拟卡属于某个账号，不能绑定给别人。</p></div>
      {:else}
        {#if needsForce}
          <label class="switch-row"><span><strong>强制转移</strong><small>这张卡现在属于「{owner}」，勾选后会先从 TA 名下解除绑定，再绑定给「{preview.user.username}」</small></span><input type="checkbox" class="switch" bind:checked={force} /></label>
        {/if}

        {#if preview.card}
          <h3>迁移游戏数据</h3>
          <div class="table-wrap">
            <table class="data-table table-cards">
              <thead><tr><th>游戏</th><th>卡上的数据</th><th>账户现有数据</th><th>迁移到账户</th></tr></thead>
              <tbody>
                {#each preview.games as item (item.game)}
                  <tr>
                    <td data-label="游戏">{item.label}</td>
                    <td data-label="卡上的数据">{describe(item.onCard)}</td>
                    <td data-label="账户现有数据">{describe(item.onAccount)}</td>
                    <td data-label="迁移到账户"><input type="checkbox" class="switch" bind:checked={migrate[item.game]} disabled={!item.onCard} aria-label={`迁移${item.label}`} /></td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
          {#if replaced.length}
            <div class="alert alert-danger"><Icon icon="solar:danger-triangle-bold-duotone" /><p>{replaced.map(item => item.label).join("、")}：账户里已经有数据，迁移会替换它，原数据会被归档。</p></div>
          {/if}
          {#if detached.length}
            <div class="alert"><Icon icon="solar:info-circle-bold-duotone" /><p>{detached.map(item => item.label).join("、")}：卡上有数据但没有勾选迁移，绑定后这部分数据会和这张卡分离。</p></div>
          {/if}
        {/if}

        <div class="actions">
          <button class="btn-primary" type="button" disabled={!ready || binding} on:click={bind}>{#if binding}<Spinner />{:else}<Icon icon="solar:link-bold" />{/if}确认绑定</button>
          <button class="btn-ghost" type="button" on:click={() => preview = null}>取消</button>
        </div>
      {/if}
    </section>
  {/if}
</section>

<style lang="sass">
  .bind
    display: grid
    gap: 14px
    padding: 20px 0

    h2, h3
      margin: 0

    h3
      font-size: 1rem

  .hint
    margin: 0
    opacity: 0.7

  .inputs
    display: grid
    grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr) auto
    align-items: end
    gap: 12px

  .result
    display: grid
    gap: 14px

  .who
    display: grid
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr))
    gap: 12px

    > div
      display: grid
      gap: 2px
      padding: 12px 14px
      border-radius: var(--radius)
      background: var(--surface-2)

    small
      color: var(--text-2)

  .switch-row
    display: flex
    align-items: center
    justify-content: space-between
    gap: 16px
    padding: 12px 14px
    border: 1px solid var(--border)
    border-radius: var(--radius)

    span
      display: grid
      gap: 2px

    small
      color: var(--text-2)

  .actions
    display: flex
    gap: 8px

  @media (max-width: 760px)
    .inputs
      grid-template-columns: minmax(0, 1fr)
</style>
