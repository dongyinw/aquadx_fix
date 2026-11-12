<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../components/ui/Icon.svelte"
  import PageHeader from "../components/ui/PageHeader.svelte"
  import EmptyState from "../components/ui/EmptyState.svelte"
  import Spinner from "../components/ui/Spinner.svelte"
  import type { ClaimStatus } from "../libs/generalTypes"
  import { USER } from "../libs/sdk"
  import { getMaimaiAllMusic } from "../libs/maimai"
  import { confirmDialog } from "../libs/confirm"
  import { errorMessage, toast } from "../libs/toast"

  type Song = { id: number, name: string, levels: number[] }

  const levelNames = ["Basic", "Advanced", "Expert", "Master", "Re:MASTER"]
  const comboNames = ["无", "FC", "FC+", "AP", "AP+"]
  const syncNames = ["无", "FS", "FS+", "FDX", "FDX+"]

  // Only the account's virtual card is used; physical cards are not offered here.
  let selectedCardId = ""
  let noCard = false
  let status: ClaimStatus | null = null
  let loading = true
  let busy = false
  let error = ""
  let agreed = false

  let songs: Song[] = []
  let query = ""
  let picked: Song | null = null
  let level = 3
  let achievementText = ""
  let deluxscore = ""
  let combo = 0
  let sync = 0
  let mileText = ""

  $: results = query.trim() && !picked ? songs.filter(song => song.name.toLowerCase().includes(query.trim().toLowerCase()) || String(song.id) === query.trim()).slice(0, 8) : []
  $: achievement = Math.round(parseFloat(achievementText) * 10000)
  $: achievementOk = Number.isFinite(achievement) && achievement >= 0 && achievement <= 1010000
  $: dxOk = /^\d{1,4}$/.test(deluxscore)
  $: canSubmitScore = !!picked && achievementOk && dxOk && !busy && agreed && !!status?.hasProfile
  $: mile = Number(mileText)
  $: mileOk = mileText.trim() !== "" && Number.isInteger(mile) && mile >= 0 && mile <= 99999
  $: canSubmitMile = mileOk && !busy && agreed && !!status?.hasProfile

  onMount(() => {
    USER.ensureLoggedIn()
    USER.me().then(user => {
      selectedCardId = user.ghostCard?.luid ?? ""
      noCard = !selectedCardId
      if (selectedCardId) void loadStatus(selectedCardId)
    }).catch(e => { error = errorMessage(e) }).finally(() => loading = false)
    getMaimaiAllMusic().then(all => {
      songs = Object.entries(all)
        .filter(([id]) => Number(id) > 0 && Number(id) < 100000)
        .map(([id, music]) => ({
          id: Number(id),
          name: String(music.name ?? id),
          levels: (music.notes ?? []).map((note: { lv: number | null }, index: number) => note?.lv != null ? index : -1).filter((index: number) => index >= 0),
        }))
        .filter(song => song.levels.length > 0)
    }).catch(() => { /* the song search simply stays empty */ })
  })

  async function loadStatus(cardId: string) {
    selectedCardId = cardId
    status = null
    error = ""
    loading = true
    try {
      status = await USER.mai2ClaimStatus(cardId)
      mileText = String(status.point)
    } catch (e) {
      error = errorMessage(e)
    } finally {
      loading = false
    }
  }

  function pick(song: Song) {
    picked = song
    query = song.name
    level = song.levels.includes(3) ? 3 : song.levels[song.levels.length - 1]
  }

  function clearSong() {
    picked = null
    query = ""
  }

  async function submitMile() {
    if (!canSubmitMile || !status) return
    if (!await confirmDialog({
      title: "修改舞里程",
      message: `确认把舞里程从 ${status.point} 改为 ${mile} 吗？\n该操作会被记录，滥用可能导致账号被封禁。`,
      confirmText: "确认修改",
    })) return
    busy = true
    try {
      status = await USER.mai2ClaimMile(selectedCardId, mile)
      mileText = String(status.point)
      toast.success("舞里程已修改")
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      busy = false
    }
  }

  async function submitScore() {
    if (!canSubmitScore || !picked) return
    if (!await confirmDialog({
      title: "补传成绩",
      message: `${picked.name}（${levelNames[level]}）\n达成率 ${(achievement / 10000).toFixed(4)}%，DX 分数 ${deluxscore}，${comboNames[combo]} / ${syncNames[sync]}\n\n请确认与机台结算画面一致。该操作会被记录，滥用可能导致账号被封禁。`,
      confirmText: "确认补传",
    })) return
    busy = true
    try {
      status = await USER.mai2ClaimScore(selectedCardId, picked.id, level, achievement, Number(deluxscore), combo, sync)
      toast.success("成绩已补传，下次进入游戏后生效")
      achievementText = ""
      deluxscore = ""
      combo = 0
      sync = 0
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      busy = false
    }
  }
</script>

<main class="page claim-page">
  <PageHeader eyebrow="maimai DX" title="创哥理赔" description="服务不可用导致成绩或舞里程没有保存时，可以在这里自行补上。" />

  <div class="alert alert-danger notice" role="note">
    <Icon icon="solar:danger-triangle-bold-duotone" />
    <div>
      <strong>使用须知，请先阅读</strong>
      <ul>
        <li>本功能只用于<b>服务不可用期间</b>没能保存的成绩和舞里程，请按机台结算画面上的真实结果如实填写。</li>
        <li>每一次使用都会被记录（账号、时间、歌曲、修改前后的数值）。</li>
        <li><b>滥用本功能，包括虚报成绩或舞里程，可能会触发账号封禁。</b></li>
      </ul>
      <label class="agree"><input type="checkbox" bind:checked={agreed} />我已阅读以上内容，并保证如实填写</label>
    </div>
  </div>

  {#if error}
    <div class="alert alert-danger" role="alert"><Icon icon="solar:danger-triangle-bold-duotone" /><p>{error}</p></div>
  {/if}

  {#if noCard}
    <EmptyState icon="solar:card-search-bold-duotone" title="没有找到账户虚拟卡" description="请先游玩一次舞萌 DX，生成游戏数据后再来。">
      <a class="btn btn-primary" href="/home">返回首页</a>
    </EmptyState>
  {:else}
    {#if loading}<p class="subtle"><Spinner /> 正在读取…</p>{/if}

    <fieldset class="tools" disabled={!agreed || !status?.hasProfile}>
      <section class="card">
        <div class="card-header"><div><h3>修改舞里程</h3><p>当前 {status?.point ?? "—"} · 今天已用 {status?.mileUsed ?? 0}/{status?.mileLimit ?? 0} 次</p></div></div>
        <form class="row" on:submit|preventDefault={submitMile}>
          <label class="field"><span class="field-label">舞里程（0 – 99999）</span>
            <input type="number" min="0" max="99999" step="1" inputmode="numeric" bind:value={mileText} />
          </label>
          <button class="btn-primary" type="submit" disabled={!canSubmitMile}>{#if busy}<Spinner />{/if}修改舞里程</button>
        </form>
        <p class="subtle note">请不要在机台上游玩时修改，否则会被本局结算时上传的数据覆盖。</p>
      </section>

      <section class="card">
        <div class="card-header"><div><h3>补传成绩</h3><p>今天已用 {status?.scoreUsed ?? 0}/{status?.scoreLimit ?? 0} 次</p></div></div>
        <form class="stack" on:submit|preventDefault={submitScore}>
          <label class="field"><span class="field-label">歌曲</span>
            <span class="song-input">
              <input type="search" placeholder="输入歌名或歌曲 ID 搜索" bind:value={query} on:input={() => { if (picked && query !== picked.name) picked = null }} autocomplete="off" />
              {#if picked}<button class="btn-ghost btn-sm" type="button" on:click={clearSong}>重选</button>{/if}
            </span>
            {#if songs.length === 0}<small class="field-hint">正在加载歌曲列表…</small>{/if}
          </label>
          {#if results.length}
            <ul class="results" role="listbox" aria-label="搜索结果">
              {#each results as song (song.id)}
                <li><button type="button" class="result" on:click={() => pick(song)}>{song.name}<small>ID {song.id}</small></button></li>
              {/each}
            </ul>
          {/if}

          {#if picked}
            <div class="fields">
              <label class="field"><span class="field-label">难度</span>
                <select bind:value={level}>{#each picked.levels as index}<option value={index}>{levelNames[index]}</option>{/each}</select>
              </label>
              <label class="field"><span class="field-label">达成率（%）</span>
                <input inputmode="decimal" placeholder="例如 100.5000" bind:value={achievementText} />
                {#if achievementText && !achievementOk}<small class="field-hint danger">达成率需在 0 到 101 之间</small>{/if}
              </label>
              <label class="field"><span class="field-label">DX 分数</span>
                <input inputmode="numeric" placeholder="例如 2527" bind:value={deluxscore} />
                {#if deluxscore && !dxOk}<small class="field-hint danger">请输入 0 – 9999 的整数</small>{/if}
              </label>
              <label class="field"><span class="field-label">FC / AP</span>
                <select bind:value={combo}>{#each comboNames as name, index}<option value={index}>{name}</option>{/each}</select>
              </label>
              <label class="field"><span class="field-label">FS / FDX</span>
                <select bind:value={sync}>{#each syncNames as name, index}<option value={index}>{name}</option>{/each}</select>
              </label>
            </div>
          {/if}
          <button class="btn-primary" type="submit" disabled={!canSubmitScore}>{#if busy}<Spinner />{/if}补传成绩</button>
        </form>
        <p class="subtle note">补传只会提高这首歌的最佳成绩，不会降低已有记录，也不会生成游玩记录，不计入大赛。Rating 会在你下次游玩后更新。</p>
      </section>
    </fieldset>
  {/if}
</main>

<style lang="sass">
  .claim-page
    display: grid
    gap: 16px

  .notice
    align-items: flex-start

    ul
      margin: 6px 0 10px
      padding-left: 20px

  .agree
    display: flex
    align-items: center
    gap: 8px
    font-weight: 600

  .tools
    display: grid
    gap: 16px
    min-width: 0
    margin: 0
    padding: 0
    border: 0

    &:disabled
      opacity: 0.55

  .row
    display: flex
    align-items: flex-end
    flex-wrap: wrap
    gap: 12px

  .stack
    display: grid
    gap: 12px

  .fields
    display: grid
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr))
    gap: 12px

  .song-input
    display: flex
    gap: 8px

    input
      flex: 1
      min-width: 0

  .results
    list-style: none
    margin: 0
    padding: 0
    display: grid
    gap: 4px

  .result
    display: flex
    justify-content: space-between
    align-items: center
    gap: 12px
    width: 100%
    text-align: left

    small
      opacity: 0.65

  .danger
    color: var(--danger, #c0392b)

  .note
    margin-top: 10px
</style>
