<!-- Administrative user, card and keychip console. -->
<script lang="ts">
  import StatusOverlays from "../components/StatusOverlays.svelte"
  import Icon from "../components/ui/Icon.svelte"
  import PageHeader from "../components/ui/PageHeader.svelte"
  import EmptyState from "../components/ui/EmptyState.svelte"
  import Spinner from "../components/ui/Spinner.svelte"
  import { confirmDialog } from "../libs/confirm"
  import { toast } from "../libs/toast"
  import { ADMIN, USER } from "../libs/sdk"
  import type { AdminAnalytics } from "../libs/sdk"
  import type {
    AdminCardDetail,
    AdminCardGame,
    AdminCardSummary,
    AdminUserDetail,
    AdminUserSummary,
    MikuNetUser,
    AdminGameProfile,
  } from "../libs/generalTypes"

  type View = "users" | "cards"
  type Section = "overview" | View
  type ManagedGame = "mai2" | "chu3" | "ongeki"

  USER.ensureLoggedIn()

  let me: MikuNetUser | null = null
  let error = ""
  let notice = ""
  let loading = true
  let query = ""
  let searchRequest = 0
  let regFrom = ""
  let regTo = ""
  let view: View = "users"
  // Top-level console tab, and the sub-tab shown inside a user / card detail.
  let section: Section = "overview"
  let userTab: "account" | "game" | "cards" = "account"
  let cardTab: "info" | "game" | "logs" = "info"
  let users: AdminUserSummary[] = []
  let cards: AdminCardSummary[] = []
  let selectedUser: AdminUserSummary | null = null
  let selectedCard: AdminCardSummary | null = null
  let userDetail: AdminUserDetail | null = null
  let cardDetail: AdminCardDetail | null = null

  let profileField = "displayName"
  let profileValue = ""
  let newPassword = ""
  let confirmPassword = ""
  let passwordResetting = false
  let selectedGame: ManagedGame = "mai2"
  let gameField = "userName"
  let gameValue = ""
  let cardMaiField = "userName"
  let cardMaiValue = ""
  let itemKind = 3
  let itemId = 0
  let itemAmount = 1
  let newKeychip = ""
  let cardsExpanded = false
  let analytics: AdminAnalytics | null = null
  let analyticsLoading = false
  let analyticsError = ""
  let analyticsPeriod = "7"
  let analyticsFrom = ""
  let analyticsTo = ""

  const gameLabels: Record<ManagedGame, string> = { mai2: "舞萌 DX", chu3: "中二节奏", ongeki: "音击" }
  const gameFields: Record<ManagedGame, string[]> = {
    mai2: ["userName", "banState", "iconId", "plateId", "titleId", "partnerId", "frameId"],
    chu3: ["userName", "banState"],
    ongeki: ["userName"]
  }

  const fail = (e: unknown) => {
    toast.error(e instanceof Error ? e.message : String(e))
    notice = ""
  }
  // Mirror success notices into toasts.
  $: if (notice) { toast.success(notice); notice = "" }

  const isSelf = (user: AdminUserDetail["user"]) => user.username.toLowerCase() === me?.username.toLowerCase()

  function formatTime(value: string | number | null | undefined): string {
    if (!value) return "暂无"
    const date = new Date(typeof value === "number" ? value : value)
    if (Number.isNaN(date.getTime())) return String(value)
    return date.toLocaleString("zh-CN", { dateStyle: "medium", timeStyle: "short" })
  }

  function gameBanLabel(game: AdminCardGame | null | undefined): string {
    if (!game) return "无档案"
    return game.banState === 2 ? "已封禁" : game.banState === 1 ? "受限" : "正常"
  }

  async function search() {
    const request = ++searchRequest
    const searchView = view
    loading = true
    error = ""
    try {
      if (searchView === "users") {
        const result = await ADMIN.users(query, regFrom, regTo)
        if (request !== searchRequest || view !== searchView) return
        users = result
        if (selectedUser && !users.some(user => user.auId === selectedUser?.auId)) {
          selectedUser = null
          userDetail = null
          clearPasswords()
        }
      } else {
        const result = await ADMIN.cards(query, regFrom, regTo)
        if (request !== searchRequest || view !== searchView) return
        cards = result
        if (selectedCard && !cards.some(card => card.id === selectedCard?.id)) {
          selectedCard = null
          cardDetail = null
        }
      }
    } catch (e) {
      if (request === searchRequest) fail(e)
    } finally {
      if (request === searchRequest) loading = false
    }
  }

  async function loadAnalytics() {
    analyticsLoading = true
    analyticsError = ""
    const end = new Date()
    const to = end.toISOString().slice(0, 10)
    let from = ""
    if (analyticsPeriod === "custom") {
      from = analyticsFrom
    } else {
      const days = Number(analyticsPeriod)
      const begin = new Date(end)
      begin.setDate(begin.getDate() - Math.max(0, days - 1))
      from = begin.toISOString().slice(0, 10)
    }
    try {
      analytics = await ADMIN.analytics(from, analyticsPeriod === "custom" ? analyticsTo : to)
    } catch (e) {
      analyticsError = e instanceof Error ? e.message : String(e)
    } finally {
      analyticsLoading = false
    }
  }

  function searchAllTime() {
    regFrom = ""
    regTo = ""
    search()
  }

  function clearPasswords() {
    newPassword = ""
    confirmPassword = ""
  }

  async function switchView(next: View) {
    if (view === next) return
    view = next
    selectedUser = null
    selectedCard = null
    userDetail = null
    cardDetail = null
    clearPasswords()
    await search()
  }

  async function switchSection(next: Section) {
    section = next
    if (next !== "overview") await switchView(next)
  }

  async function openUser(user: AdminUserSummary) {
    view = "users"
    section = "users"
    selectedUser = user
    selectedCard = null
    userDetail = null
    cardDetail = null
    cardsExpanded = false
    clearPasswords()
    loading = true
    error = ""
    try {
      userDetail = await ADMIN.user(user.auId)
      profileValue = String(userDetail.user[profileField as keyof typeof userDetail.user] ?? "")
      selectedGame = userDetail.mai2 ? "mai2" : userDetail.chu3 ? "chu3" : "ongeki"
      syncGameProfileValue()
    } catch (e) {
      fail(e)
    } finally {
      loading = false
    }
  }

  async function openCard(card: AdminCardSummary) {
    view = "cards"
    section = "cards"
    selectedCard = card
    selectedUser = null
    cardDetail = null
    userDetail = null
    clearPasswords()
    loading = true
    error = ""
    try {
      cardDetail = await ADMIN.card(card.id)
      cardMaiValue = String(cardDetail.mai2?.[cardMaiField as keyof AdminCardGame] ?? "")
    } catch (e) {
      fail(e)
    } finally {
      loading = false
    }
  }

  function syncProfileValue() {
    if (!userDetail) return
    profileValue = String(userDetail.user[profileField as keyof typeof userDetail.user] ?? "")
  }

  function gameProfile(game: ManagedGame): AdminGameProfile | null {
    return userDetail?.[game] ?? null
  }

  function syncGameProfileValue() {
    const profile = gameProfile(selectedGame)
    gameValue = String(profile?.[gameField as keyof AdminGameProfile] ?? "")
  }

  function changeGame(game: ManagedGame) {
    selectedGame = game
    gameField = "userName"
    syncGameProfileValue()
  }

  function syncCardMaiValue() {
    if (!cardDetail?.mai2) return
    cardMaiValue = String(cardDetail.mai2[cardMaiField as keyof AdminCardGame] ?? "")
  }

  async function saveProfile() {
    if (!userDetail) return
    try {
      await ADMIN.setProfile(userDetail.user.auId, profileField, profileValue)
      await openUser(selectedUser!)
      notice = "账号资料已保存"
    } catch (e) {
      fail(e)
    }
  }

  async function resetUserPassword() {
    if (!userDetail || passwordResetting) return
    if (newPassword.length < 8) return fail(new Error("新密码至少需要 8 个字符"))
    if (newPassword !== confirmPassword) return fail(new Error("两次输入的密码不一致"))
    const target = userDetail.user
    passwordResetting = true
    error = ""
    notice = ""
    try {
      await ADMIN.resetPassword(target.auId, newPassword)
      clearPasswords()
      if (isSelf(target)) {
        USER.logout()
        return
      }
      notice = `用户 ${target.username} 的密码已重置，需要重新登录`
    } catch (e) {
      fail(e)
    } finally {
      passwordResetting = false
    }
  }

  async function setBoolean(field: string, event: Event) {
    if (!userDetail) return
    const checked = (event.currentTarget as HTMLInputElement).checked
    if (field === "isAdmin" && isSelf(userDetail.user) && !checked) {
      notice = "不能取消当前登录账号的管理员权限"
      return
    }
    try {
      await ADMIN.setProfile(userDetail.user.auId, field, checked)
      await openUser(selectedUser!)
      notice = "权限状态已保存"
    } catch (e) {
      fail(e)
    }
  }

  async function saveMaiProfile() {
    if (!userDetail || !gameProfile(selectedGame)) return
    try {
      await ADMIN.setGameProfile(userDetail.user.auId, selectedGame, gameField, gameValue)
      await openUser(selectedUser!)
      notice = `${gameLabels[selectedGame]} 资料已保存`
    } catch (e) {
      fail(e)
    }
  }

  async function saveCardMaiProfile() {
    if (!cardDetail?.mai2) return
    try {
      await ADMIN.setCardGameProfile(cardDetail.card.id, "mai2", cardMaiField, cardMaiValue)
      await openCard(selectedCard!)
      notice = "卡片档案已保存"
    } catch (e) {
      fail(e)
    }
  }

  async function grantItem() {
    if (!userDetail) return
    try {
      await ADMIN.grantItem(userDetail.user.auId, "mai2", Number(itemKind), Number(itemId), Number(itemAmount))
      await openUser(selectedUser!)
      notice = "物品已发放"
    } catch (e) {
      fail(e)
    }
  }

  async function grantCardItem() {
    if (!cardDetail) return
    try {
      await ADMIN.grantCardItem(cardDetail.card.id, "mai2", Number(itemKind), Number(itemId), Number(itemAmount))
      await openCard(selectedCard!)
      notice = "物品已发放"
    } catch (e) {
      fail(e)
    }
  }

  async function toggleKeychip(keychipId: string, event: Event) {
    const enabled = (event.currentTarget as HTMLInputElement).checked
    try {
      await ADMIN.setKeychip(keychipId, enabled)
      if (selectedUser) await openUser(selectedUser)
      notice = enabled ? "keychip 已允许连接" : "keychip 已禁止连接"
    } catch (e) {
      fail(e)
    }
  }

  async function addKeychip() {
    if (!userDetail || !newKeychip.trim()) return
    try {
      await ADMIN.addKeychip(userDetail.user.auId, newKeychip)
      newKeychip = ""
      await openUser(selectedUser!)
      notice = "keychip 已添加"
    } catch (e) {
      fail(e)
    }
  }

  async function deleteKeychip(keychipId: string) {
    if (!userDetail || !await confirmDialog({ title: "删除 keychip", message: `确定删除 keychip ${keychipId} 吗？`, confirmText: "删除", danger: true })) return
    try {
      await ADMIN.deleteKeychip(keychipId)
      await openUser(selectedUser!)
      notice = "keychip 已删除"
    } catch (e) {
      fail(e)
    }
  }

  async function deleteUser() {
    if (!userDetail || !await confirmDialog({ title: "删除用户", message: `确定永久删除用户 ${userDetail.user.computedName} 及其游戏数据吗？此操作无法撤销。`, confirmText: "永久删除", danger: true })) return
    try {
      await ADMIN.deleteUser(userDetail.user.auId)
      selectedUser = null
      userDetail = null
      notice = "用户已删除"
      await search()
    } catch (e) {
      fail(e)
    }
  }

  async function deleteCard() {
    if (!cardDetail || cardDetail.card.isGhost || !await confirmDialog({ title: "删除卡片", message: `确定删除卡片 ${cardDetail.card.luid} 吗？`, confirmText: "删除", danger: true })) return
    try {
      await ADMIN.deleteCard(cardDetail.card.id)
      selectedCard = null
      cardDetail = null
      notice = "卡片已删除"
      await search()
    } catch (e) {
      fail(e)
    }
  }

  async function openCardOwner() {
    if (!cardDetail?.user) return
    const owner = cardDetail.user
    await openUser(owner)
    // Show the owner in the list on the left as well.
    query = owner.username
    await search()
  }

  async function openLinkedCard(card: AdminCardSummary) {
    await openCard(card)
    query = card.luid
    await search()
  }

  USER.me().then(value => {
    me = value
    if (value.isAdmin) void loadAnalytics()
    if (!value.isAdmin) {
      error = "当前账号没有管理员权限"
      loading = false
      return
    }
    search()
  }).catch(e => {
    error = e instanceof Error ? e.message : String(e)
    loading = false
  })

  $: trendMaxPlayers = analytics ? Math.max(1, ...analytics.trend.map(item => item.players)) : 1
  $: trendMaxGuests = analytics ? Math.max(1, ...analytics.trend.map(item => item.guestPlayers)) : 1
</script>

<main class="page page-wide admin-page">
  <PageHeader eyebrow="Console" title="管理控制台" description="查看服务器游玩数据，管理用户账号、卡片与连接权限" />

  {#if error && !me?.isAdmin}
    <EmptyState icon="solar:lock-keyhole-minimalistic-bold-duotone" title={error}>
      <a class="btn" href="/home">返回首页</a>
    </EmptyState>
  {/if}

  {#if me?.isAdmin}
    <div class="console-bar">
      <div class="tabs" role="tablist" aria-label="管理分类">
        <button type="button" role="tab" aria-selected={section === "overview"} on:click={() => switchSection("overview")}><Icon icon="solar:chart-2-bold-duotone" />数据概览</button>
        <button type="button" role="tab" aria-selected={section === "users"} on:click={() => switchSection("users")}><Icon icon="solar:user-id-bold-duotone" />用户管理{#if users.length}<span class="count">{users.length}</span>{/if}</button>
        <button type="button" role="tab" aria-selected={section === "cards"} on:click={() => switchSection("cards")}><Icon icon="solar:card-bold-duotone" />卡片管理{#if cards.length}<span class="count">{cards.length}</span>{/if}</button>
      </div>
      {#if section === "overview"}
        <button class="btn-ghost btn-sm" type="button" on:click={loadAnalytics} disabled={analyticsLoading}>{#if analyticsLoading}<Spinner />{:else}<Icon icon="solar:refresh-bold" />{/if}刷新数据</button>
      {:else}
        <button class="btn-ghost btn-sm" type="button" on:click={search} disabled={loading}>{#if loading}<Spinner />{:else}<Icon icon="solar:refresh-bold" />{/if}刷新列表</button>
      {/if}
    </div>

    {#if section === "overview"}
    <section class="card analytics">
      <div class="card-header">
        <div><h2>游玩数据趋势</h2><p>舞萌 DX 游玩记录 · 达成率按成绩平均值计算</p></div>
        <div class="analytics-tools">
          <select bind:value={analyticsPeriod} on:change={loadAnalytics} aria-label="统计时间范围">
            <option value="1">近 1 日</option><option value="7">近 7 日</option><option value="30">近 30 日</option><option value="custom">自定义</option>
          </select>
          {#if analyticsPeriod === "custom"}
            <input type="date" bind:value={analyticsFrom} aria-label="开始日期" /><span class="subtle">至</span><input type="date" bind:value={analyticsTo} aria-label="结束日期" />
            <button class="btn-primary" type="button" on:click={loadAnalytics}>查询</button>
          {/if}
        </div>
      </div>
      {#if analyticsError}
        <div class="alert alert-danger"><Icon icon="solar:danger-triangle-bold-duotone" /><p>{analyticsError}</p></div>
      {:else if analyticsLoading && !analytics}
        <div class="kpis">{#each [1, 2, 3, 4] as _}<span class="skeleton" style="height:76px;border-radius:12px"></span>{/each}</div>
      {:else if analytics}
        <div class="kpis">
          <div><small>游玩人数</small><strong class="num">{analytics.players}</strong></div>
          <div><small>游客账户人数</small><strong class="num">{analytics.guestPlayers}</strong></div>
          <div><small>游玩曲目总数</small><strong class="num">{analytics.totalPlays}</strong></div>
          <div><small>游玩总达成率</small><strong class="num">{analytics.achievementRate.toFixed(2)}%</strong></div>
        </div>
        <div class="trend-chart" role="img" aria-label="每日游玩人数和游客人数趋势">
          {#each analytics.trend as point (point.date)}
            <div class="trend-day" title={`${point.date} · 游玩 ${point.plays} 次 · 玩家 ${point.players} · 游客 ${point.guestPlayers}`}>
              <div class="trend-bars">
                <i style={`height:${Math.max(3, point.players / trendMaxPlayers * 100)}%`}></i>
                <i class="guest-bar" style={`height:${Math.max(2, point.guestPlayers / trendMaxGuests * 100)}%`}></i>
              </div>
              <small class="num">{point.date.slice(5)}</small>
            </div>
          {:else}
            <p class="subtle">所选期间暂无游玩记录</p>
          {/each}
        </div>
        <div class="legend"><span><i></i>游玩人数</span><span><i class="guest-dot"></i>游客人数</span></div>
        <details class="ranking-details" open>
          <summary>热门曲目排行（{analytics.ranking.length}）<Icon icon="line-md:chevron-small-down" /></summary>
          <div class="table-wrap">
            <table class="data-table">
              <thead><tr><th>排名</th><th>曲目 ID</th><th class="num">游玩次数</th><th class="num">平均达成率</th></tr></thead>
              <tbody>
                {#each analytics.ranking as song}
                  <tr><td>{song.rank}</td><td class="mono">{song.musicId}</td><td class="num">{song.plays}</td><td class="num">{song.averageAchievement.toFixed(2)}%</td></tr>
                {:else}
                  <tr><td colspan="4" class="subtle">暂无曲目记录</td></tr>
                {/each}
              </tbody>
            </table>
          </div>
        </details>
      {/if}
    </section>
    {:else}
    <section class="workspace">
      <aside class="card sidebar">
        <form class="search" on:submit|preventDefault={search} role="search">
          <Icon icon="solar:magnifer-bold-duotone" />
          <input type="search" bind:value={query} placeholder={view === "users" ? "用户名、邮箱或 AU ID" : "卡号、LUID 或用户名"} aria-label="搜索" />
          <button class="btn-primary btn-sm" type="submit">搜索</button>
        </form>
        <details class="filters" open={!!(regFrom || regTo)}>
          <summary><Icon icon="solar:filter-bold-duotone" />按注册时间筛选{#if regFrom || regTo}<span class="badge primary">已启用</span>{/if}</summary>
          <div class="date-filters">
            <label class="field"><span class="field-label">从</span><input type="date" bind:value={regFrom} /></label>
            <label class="field"><span class="field-label">到</span><input type="date" bind:value={regTo} /></label>
            <div class="filter-actions">
              <button class="btn-sm" type="button" on:click={searchAllTime}>全部时间</button>
              <button class="btn-soft btn-sm" type="button" on:click={search}>应用筛选</button>
            </div>
          </div>
        </details>
        <div class="list-meta"><span>{view === "users" ? "Net 用户" : "卡片账户"}</span><span class="badge">{view === "users" ? users.length : cards.length}</span></div>

        <div class="result-list">
          {#if view === "users"}
            {#each users as user (user.auId)}
              <button class="result-row" class:selected={selectedUser?.auId === user.auId} type="button" on:click={() => openUser(user)}>
                <span class="result-icon"><Icon icon="solar:user-circle-bold-duotone" /></span>
                <span class="result-copy">
                  <strong class="truncate">{user.displayName || user.username}{#if user.isAdmin}<span class="badge primary admin-badge">ADMIN</span>{/if}</strong>
                  <small class="truncate">#{user.auId} · {user.username}</small>
                  <small class="truncate">{user.email}</small>
                  <small class="truncate">注册 {formatTime(user.regTime)}</small>
                </span>
              </button>
            {:else}
              <EmptyState compact icon="solar:user-id-bold-duotone" title={loading ? "正在搜索…" : "没有匹配的用户"} />
            {/each}
          {:else}
            {#each cards as card (card.id)}
              <button class="result-row" class:selected={selectedCard?.id === card.id} type="button" on:click={() => openCard(card)}>
                <span class="result-icon card-icon"><Icon icon="solar:card-bold-duotone" /></span>
                <span class="result-copy">
                  <strong class="truncate mono">{card.luid}</strong>
                  <small class="truncate">卡片 #{card.id} · ext {card.extId}</small>
                  <small class="truncate">{card.owner?.displayName || card.owner?.username || "未绑定用户"}</small>
                  <small class="truncate">注册 {formatTime(card.registerTime)}</small>
                </span>
                <span class="badge" class:danger={card.status === "DELETED" || card.mai2?.banState === 2} class:success={card.status === "NORMAL" && card.mai2?.banState === 0}>{card.status === "NORMAL" ? gameBanLabel(card.mai2) : card.status}</span>
              </button>
            {:else}
              <EmptyState compact icon="solar:card-bold-duotone" title={loading ? "正在搜索…" : "没有匹配的卡片"} />
            {/each}
          {/if}
        </div>
      </aside>

      <section class="details">
        {#if view === "users" && userDetail}
          <div class="card detail-head">
            <div class="detail-title">
              <span class="eyebrow">AU ID {userDetail.user.auId}</span>
              <h2 class="truncate">{userDetail.user.computedName}</h2>
              <span class="muted truncate">{userDetail.user.email} · @{userDetail.user.username}</span>
              <div class="row tags">
                {#if userDetail.user.isAdmin}<span class="badge primary">ADMIN</span>{/if}
                {#if userDetail.mai2}<span class="badge">MAI2</span>{/if}
                {#if userDetail.chu3}<span class="badge">CHU3</span>{/if}
                {#if userDetail.ongeki}<span class="badge">ONGEKI</span>{/if}
              </div>
            </div>
          </div>

          <dl class="stat-strip">
            <div><dt>注册时间</dt><dd>{formatTime(userDetail.user.regTime)}</dd></div>
            <div><dt>最近登录</dt><dd>{formatTime(userDetail.user.lastLogin)}</dd></div>
            <div><dt>卡片</dt><dd class="num">{userDetail.cards.length}</dd></div>
            <div><dt>Keychip</dt><dd class="num">{userDetail.keychips.length}</dd></div>
          </dl>

          <div class="segmented detail-tabs" role="tablist" aria-label="用户详情分类">
            <button type="button" role="tab" aria-selected={userTab === "account"} on:click={() => userTab = "account"}><Icon icon="solar:shield-user-bold-duotone" />账号与权限</button>
            <button type="button" role="tab" aria-selected={userTab === "game"} on:click={() => userTab = "game"}><Icon icon="solar:gamepad-bold-duotone" />游戏档案</button>
            <button type="button" role="tab" aria-selected={userTab === "cards"} on:click={() => userTab = "cards"}><Icon icon="solar:card-bold-duotone" />卡片与 Keychip</button>
          </div>

          {#if userTab === "account"}

          <section class="card">
            <div class="card-header"><div><h3>账号设置</h3><p>资料与权限</p></div></div>
            <form class="form-grid" on:submit|preventDefault={saveProfile}>
              <label class="field"><span class="field-label">修改字段</span>
                <select bind:value={profileField} on:change={syncProfileValue}>
                  <option value="displayName">显示名称</option>
                  <option value="profileLocation">所在地</option>
                  <option value="profileBio">个人简介</option>
                  <option value="country">国家代码</option>
                  <option value="region">区域代码</option>
                </select>
              </label>
              <label class="field"><span class="field-label">字段值</span><input bind:value={profileValue} /></label>
              <button class="btn-primary" type="submit"><Icon icon="solar:check-circle-bold" />保存资料</button>
            </form>
            <div class="setting-group toggles">
              <div class="setting-row">
                <label class="setting-text" for="adm-email"><span class="name">邮箱已验证</span></label>
                <input id="adm-email" type="checkbox" class="switch" checked={userDetail.user.emailConfirmed} on:change={(e) => setBoolean("emailConfirmed", e)} />
              </div>
              <div class="setting-row">
                <label class="setting-text" for="adm-keychip"><span class="name">允许用户管理 keychip</span></label>
                <input id="adm-keychip" type="checkbox" class="switch" checked={userDetail.user.canModifyKeychips} on:change={(e) => setBoolean("canModifyKeychips", e)} />
              </div>
              <div class="setting-row">
                <label class="setting-text" for="adm-admin">
                  <span class="name">管理员权限</span>
                  {#if isSelf(userDetail.user)}<span class="desc">不能取消当前登录账号的管理员权限</span>{/if}
                </label>
                <input id="adm-admin" type="checkbox" class="switch" checked={userDetail.user.isAdmin} disabled={isSelf(userDetail.user)} on:change={(e) => setBoolean("isAdmin", e)} />
              </div>
            </div>
          </section>

          <section class="card">
            <div class="card-header"><div><h3>重置用户密码</h3><p>重置后该用户需要重新登录</p></div></div>
            <form class="form-grid" on:submit|preventDefault={resetUserPassword}>
              <label class="field"><span class="field-label">新密码</span><input type="password" bind:value={newPassword} autocomplete="new-password" minlength="8" required disabled={passwordResetting} placeholder="至少 8 个字符" /></label>
              <label class="field"><span class="field-label">确认新密码</span><input type="password" bind:value={confirmPassword} autocomplete="new-password" minlength="8" required disabled={passwordResetting} aria-invalid={!!confirmPassword && confirmPassword !== newPassword} /></label>
              <button class="btn-primary" type="submit" disabled={passwordResetting}>{#if passwordResetting}<Spinner />{:else}<Icon icon="solar:lock-password-bold-duotone" />{/if}{passwordResetting ? "正在重置…" : "重置密码"}</button>
            </form>
          </section>

          <section class="card danger-zone">
            <div class="card-header"><div><h3>危险操作</h3><p>以下操作不可撤销，请谨慎执行</p></div></div>
            <div class="setting-row">
              <div class="setting-text"><span class="name">删除用户</span><span class="desc">永久删除该用户账号及其全部游戏数据</span></div>
              <button class="btn-danger" type="button" on:click={deleteUser}><Icon icon="solar:trash-bin-trash-bold-duotone" />删除用户</button>
            </div>
          </section>
          {:else if userTab === "game"}
          <section class="card">
            <div class="card-header">
              <div><h3>游戏档案管理</h3><p>支持舞萌、中二和音击</p></div>
              <div class="segmented">
                {#each Object.keys(gameLabels) as game}
                  <button type="button" class:active={selectedGame === game} disabled={!gameProfile(game as ManagedGame)} on:click={() => changeGame(game as ManagedGame)}>{gameLabels[game as ManagedGame]}</button>
                {/each}
              </div>
            </div>
            {#if gameProfile(selectedGame)}
              {@const currentProfile = gameProfile(selectedGame)}
              <form class="form-grid" on:submit|preventDefault={saveMaiProfile}>
                <label class="field"><span class="field-label">修改字段</span><select bind:value={gameField} on:change={syncGameProfileValue}>{#each gameFields[selectedGame] as field}<option value={field}>{field === "userName" ? "游戏名称" : field === "banState" ? "banState" : field}</option>{/each}</select></label>
                <label class="field"><span class="field-label">字段值</span>{#if gameField === "banState"}<select bind:value={gameValue}><option value="0">0 · 正常</option><option value="1">1 · 限制</option><option value="2">2 · 封禁</option></select>{:else}<input bind:value={gameValue} />{/if}</label>
                <button class="btn-primary" type="submit"><Icon icon="solar:check-circle-bold" />保存档案</button>
              </form>
              <div class="stats"><span class="badge">Rating {currentProfile?.playerRating ?? 0}</span><span class="badge">最高 {currentProfile?.highestRating ?? 0}</span><span class="badge">游玩 {currentProfile?.playCount ?? 0} 次</span><span class="badge">最近机台 {currentProfile?.lastClientId || "暂无"}</span></div>
            {:else}
              <p class="subtle">该用户还没有 {gameLabels[selectedGame]} 用户资料。</p>
            {/if}
          </section>

          <section class="card">
            <div class="card-header"><div><h3>发放收藏品 / Ticket</h3><p>itemKind 12 为 Ticket</p></div></div>
            <form class="grant-grid" on:submit|preventDefault={grantItem}>
              <label class="field"><span class="field-label">类型</span><select bind:value={itemKind}><option value={1}>1 · Plate</option><option value={2}>2 · Title</option><option value={3}>3 · Icon</option><option value={11}>11 · Frame</option><option value={12}>12 · Ticket</option></select></label>
              <label class="field"><span class="field-label">物品 ID</span><input type="number" min="0" bind:value={itemId} /></label>
              <label class="field"><span class="field-label">数量</span><input type="number" min="1" max="1000000" bind:value={itemAmount} /></label>
              <button class="btn-primary" type="submit"><Icon icon="solar:gift-bold-duotone" />发放</button>
            </form>
            <div class="item-list">{#each userDetail.items.slice(0, 40) as item}<span class="badge mono">{item.itemKind}/{item.itemId} × {item.stock}</span>{:else}<span class="subtle">暂无物品记录</span>{/each}</div>
          </section>

          {:else}
          <section class="card">
            <div class="card-header">
              <div><h3>关联卡片</h3><p>{userDetail.cards.length} 张卡片 · 点击查看卡片档案</p></div>
              {#if userDetail.cards.length > 2}<button class="btn-ghost btn-sm" type="button" on:click={() => cardsExpanded = !cardsExpanded}><Icon icon={cardsExpanded ? "line-md:chevron-small-up" : "line-md:chevron-small-down"} />{cardsExpanded ? "收起" : `展开全部 ${userDetail.cards.length} 张`}</button>{/if}
            </div>
            <div class="linked-cards">
              {#each userDetail.cards as card, index (card.id)}
                {#if cardsExpanded || index < 2}
                  <button class="linked-card" type="button" on:click={() => openLinkedCard(card)}>
                    <span class="result-copy"><strong class="mono truncate">{card.luid}</strong><small>#{card.id} · ext {card.extId}</small></span>
                    <span class="linked-meta"><span class="badge">{card.isGhost ? "Ghost" : "实体卡"}</span><small>{card.mai2?.playCount ?? 0} 次游玩</small></span>
                    <Icon icon="line-md:chevron-small-right" />
                  </button>
                {/if}
              {:else}
                <span class="subtle">暂无关联卡片</span>
              {/each}
            </div>
          </section>

          <section class="card">
            <div class="card-header"><div><h3>Keychip 管理</h3><p>停用会立即清理在线会话</p></div></div>
            <form class="input-group" on:submit|preventDefault={addKeychip}>
              <input class="mono" bind:value={newKeychip} placeholder="15 位 keychip，可带短横线" aria-label="新 keychip" />
              <button class="btn-primary" type="submit" disabled={!newKeychip.trim()}><Icon icon="solar:add-circle-bold" />添加</button>
            </form>
            <div class="setting-group keychips">
              {#each userDetail.keychips as keychip (keychip.id)}
                <div class="setting-row">
                  <code class="mono">{keychip.keychipId}</code>
                  <div class="row">
                    <label class="row switch-label"><span class="subtle">允许连接</span><input type="checkbox" class="switch" checked={keychip.enabled} on:change={(e) => toggleKeychip(keychip.keychipId, e)} /></label>
                    <button class="btn-ghost btn-icon btn-sm delete" type="button" title="删除 keychip" aria-label="删除 keychip {keychip.keychipId}" on:click={() => deleteKeychip(keychip.keychipId)}><Icon icon="solar:trash-bin-trash-bold-duotone" /></button>
                  </div>
                </div>
              {:else}
                <div class="setting-row"><span class="subtle">暂无 keychip</span></div>
              {/each}
            </div>
          </section>
          {/if}
        {:else if view === "cards" && cardDetail}
          <div class="card detail-head">
            <div class="detail-title">
              <span class="eyebrow">Card ID {cardDetail.card.id}</span>
              <h2 class="truncate mono">{cardDetail.card.luid}</h2>
              <span class="muted">ext {cardDetail.card.extId} · {cardDetail.card.status}</span>
              <div class="row tags"><span class="badge" class:danger={cardDetail.mai2?.banState === 2}>{gameBanLabel(cardDetail.mai2)}</span><span class="badge">{cardDetail.card.isGhost ? "Ghost 卡片" : "实体卡片"}</span></div>
            </div>
          </div>

          <dl class="stat-strip">
            <div><dt>注册时间</dt><dd>{formatTime(cardDetail.card.registerTime)}</dd></div>
            <div><dt>最后访问</dt><dd>{formatTime(cardDetail.card.accessTime)}</dd></div>
            <div><dt>游玩次数</dt><dd class="num">{cardDetail.mai2?.playCount ?? 0}</dd></div>
            <div><dt>最近机台</dt><dd>{cardDetail.mai2?.lastClientId || "暂无"}</dd></div>
          </dl>

          <div class="segmented detail-tabs" role="tablist" aria-label="卡片详情分类">
            <button type="button" role="tab" aria-selected={cardTab === "info"} on:click={() => cardTab = "info"}><Icon icon="solar:card-bold-duotone" />卡片信息</button>
            <button type="button" role="tab" aria-selected={cardTab === "game"} on:click={() => cardTab = "game"}><Icon icon="solar:gamepad-bold-duotone" />游戏档案</button>
            <button type="button" role="tab" aria-selected={cardTab === "logs"} on:click={() => cardTab = "logs"}><Icon icon="solar:history-bold-duotone" />游玩日志<span class="count">{cardDetail.playlogs.length}</span></button>
          </div>

          {#if cardTab === "info"}

          <section class="card">
            <div class="card-header"><div><h3>卡片信息</h3></div></div>
            <dl class="info-grid">
              <div><dt>LUID</dt><dd class="mono">{cardDetail.card.luid}</dd></div>
              <div><dt>ext ID</dt><dd class="mono">{cardDetail.card.extId}</dd></div>
              <div><dt>注册时间</dt><dd>{formatTime(cardDetail.card.registerTime)}</dd></div>
              <div><dt>最后访问</dt><dd>{formatTime(cardDetail.card.accessTime)}</dd></div>
            </dl>
            {#if cardDetail.user}
              <button class="btn-soft owner-link" type="button" on:click={openCardOwner}><Icon icon="solar:user-circle-bold-duotone" />绑定用户：{cardDetail.user.displayName || cardDetail.user.username}<Icon icon="line-md:chevron-small-right" /></button>
            {:else}<p class="subtle">未绑定 Net 用户</p>{/if}
          </section>

          <section class="card danger-zone">
            <div class="card-header"><div><h3>危险操作</h3><p>以下操作不可撤销，请谨慎执行</p></div></div>
            <div class="setting-row">
              <div class="setting-text"><span class="name">删除卡片</span><span class="desc">{cardDetail.card.isGhost ? "Ghost 卡片随所属用户一起删除，不能单独删除" : "从系统中删除这张卡片"}</span></div>
              <button class="btn-danger" type="button" disabled={cardDetail.card.isGhost} on:click={deleteCard}><Icon icon="solar:trash-bin-trash-bold-duotone" />删除卡片</button>
            </div>
          </section>
          {:else if cardTab === "game"}
          <section class="card">
            <div class="card-header"><div><h3>maimai2 卡片档案</h3><p>{gameBanLabel(cardDetail.mai2)}</p></div></div>
            {#if cardDetail.mai2}
              <form class="form-grid" on:submit|preventDefault={saveCardMaiProfile}>
                <label class="field"><span class="field-label">修改字段</span><select bind:value={cardMaiField} on:change={syncCardMaiValue}><option value="userName">游戏名称</option><option value="banState">banState</option><option value="iconId">头像</option><option value="plateId">称号框</option><option value="titleId">称号</option><option value="partnerId">搭档</option><option value="frameId">背景框</option></select></label>
                <label class="field"><span class="field-label">字段值</span>{#if cardMaiField === "banState"}<select bind:value={cardMaiValue}><option value="0">0 · 正常</option><option value="1">1 · 限制</option><option value="2">2 · 封禁</option></select>{:else}<input bind:value={cardMaiValue} />{/if}</label>
                <button class="btn-primary" type="submit"><Icon icon="solar:check-circle-bold" />保存档案</button>
              </form>
              <div class="stats"><span class="badge">Rating {cardDetail.mai2.playerRating}</span><span class="badge">最高 {cardDetail.mai2.highestRating}</span><span class="badge">最近游玩 {cardDetail.mai2.lastPlayDate || "暂无"}</span><span class="badge">机台号 {cardDetail.mai2.lastClientId || "暂无"}</span></div>
            {:else}<p class="subtle">该卡片还没有 maimai2 用户资料。</p>{/if}
          </section>

          <section class="card">
            <div class="card-header"><div><h3>发放收藏品 / Ticket</h3><p>按当前卡片发放</p></div></div>
            <form class="grant-grid" on:submit|preventDefault={grantCardItem}>
              <label class="field"><span class="field-label">类型</span><select bind:value={itemKind}><option value={1}>1 · Plate</option><option value={2}>2 · Title</option><option value={3}>3 · Icon</option><option value={11}>11 · Frame</option><option value={12}>12 · Ticket</option></select></label>
              <label class="field"><span class="field-label">物品 ID</span><input type="number" min="0" bind:value={itemId} /></label>
              <label class="field"><span class="field-label">数量</span><input type="number" min="1" max="1000000" bind:value={itemAmount} /></label>
              <button class="btn-primary" type="submit"><Icon icon="solar:gift-bold-duotone" />发放</button>
            </form>
            <div class="item-list">{#each cardDetail.items.slice(0, 40) as item}<span class="badge mono">{item.itemKind}/{item.itemId} × {item.stock}</span>{:else}<span class="subtle">暂无物品记录</span>{/each}</div>
          </section>

          {:else}
          <section class="card card-flush">
            <div class="card-header padded"><div><h3>最近游玩日志</h3><p>{cardDetail.playlogs.length} 条</p></div></div>
            <div class="table-wrap flush">
              <table class="data-table">
                <thead><tr><th>时间</th><th>机台</th><th>歌曲</th><th class="num">难度</th><th class="num">达成率</th></tr></thead>
                <tbody>
                  {#each cardDetail.playlogs as playlog}
                    <tr><td class="num">{playlog.userPlayDate || playlog.playDate || "暂无"}</td><td>{playlog.placeId} · {playlog.placeName || "未知机台"}</td><td class="mono">{playlog.musicId}</td><td class="num">{playlog.level}</td><td class="num">{(playlog.achievement / 10000).toFixed(4)}%</td></tr>
                  {:else}
                    <tr><td colspan="5" class="subtle">暂无游玩日志</td></tr>
                  {/each}
                </tbody>
              </table>
            </div>
          </section>
          {/if}
        {:else}
          <EmptyState icon={view === "users" ? "solar:user-id-bold-duotone" : "solar:card-bold-duotone"} title={`从左侧选择${view === "users" ? "用户" : "卡片"}`} description="详情会显示在这里" />
        {/if}
      </section>
    </section>
    {/if}
  {/if}
</main>

<StatusOverlays error={me?.isAdmin ? error : null} loading={loading && !!(selectedUser || selectedCard) && !(userDetail || cardDetail)} />

<style lang="sass">
  @use "../vars"

  .console-bar
    display: flex
    align-items: flex-end
    justify-content: space-between
    gap: 12px
    border-bottom: 1px solid var(--border)

    .tabs
      border-bottom: 0

    > button
      margin-bottom: 6px

  .detail-tabs
    display: flex
    align-self: flex-start

  .danger-zone
    border-color: color-mix(in srgb, var(--danger) 30%, transparent)

    h3
      color: var(--danger)

    .setting-row
      padding: 0
      border: 0

  .analytics-tools
    display: flex
    align-items: center
    gap: 8px
    flex-wrap: wrap

    select, input
      width: auto
      min-width: 130px

  .kpis
    display: grid
    grid-template-columns: repeat(4, minmax(0, 1fr))
    gap: 12px
    margin-bottom: 16px

    div
      display: grid
      gap: 4px
      padding: 14px
      border-radius: var(--radius)
      background: var(--surface-2)

    small
      color: var(--text-2)

    strong
      font-size: 1.4rem

  .trend-chart
    display: flex
    align-items: stretch
    gap: 6px
    height: 170px
    padding: 6px 0
    overflow-x: auto

  .trend-day
    display: flex
    flex: 1
    flex-direction: column
    align-items: center
    justify-content: flex-end
    gap: 6px
    min-width: 30px

    .trend-bars
      display: flex
      align-items: flex-end
      justify-content: center
      gap: 3px
      width: 100%
      height: 130px

    i
      width: 9px
      min-height: 2px
      border-radius: 4px 4px 0 0
      background: var(--primary)

    .guest-bar
      background: var(--accent)

    small
      color: var(--text-3)
      font-size: 0.68rem
      white-space: nowrap

  .legend
    display: flex
    gap: 16px
    margin: 6px 0 12px
    color: var(--text-2)
    font-size: 0.82rem

    span
      display: inline-flex
      align-items: center
      gap: 6px

    i
      width: 9px
      height: 9px
      border-radius: 50%
      background: var(--primary)

    .guest-dot
      background: var(--accent)

  .ranking-details
    summary
      display: inline-flex
      align-items: center
      gap: 4px
      margin-bottom: 10px
      color: var(--primary)
      font-weight: 600
      cursor: pointer
      list-style: none

      &::-webkit-details-marker
        display: none

    .table-wrap
      max-height: 320px

  .workspace
    display: grid
    grid-template-columns: minmax(280px, 340px) minmax(0, 1fr)
    align-items: start
    gap: 16px

  .sidebar
    position: sticky
    top: calc(var(--nav-height) + 16px)
    gap: 10px
    padding: 12px

  .search
    position: relative
    display: flex
    align-items: center
    gap: 6px

    > :global(svg)
      position: absolute
      left: 10px
      z-index: 1
      pointer-events: none
      color: var(--text-3)

    input
      flex: 1
      padding-left: 34px

  .filters
    summary
      display: flex
      align-items: center
      gap: 6px
      min-height: 32px
      color: var(--text-2)
      font-size: 0.84rem
      font-weight: 600
      cursor: pointer
      list-style: none

      &::-webkit-details-marker
        display: none

  .date-filters
    display: grid
    grid-template-columns: 1fr 1fr
    gap: 8px
    margin-top: 6px

    input
      min-height: 34px
      font-size: 0.82rem

  .filter-actions
    display: flex
    grid-column: 1 / -1
    justify-content: flex-end
    gap: 6px

  .list-meta
    display: flex
    align-items: center
    justify-content: space-between
    padding: 4px 4px 0
    color: var(--text-2)
    font-size: 0.82rem

  .result-list
    display: grid
    gap: 2px
    max-height: calc(100dvh - 330px)
    min-height: 200px
    overflow-y: auto

  .result-row
    justify-content: flex-start
    gap: 10px
    height: auto
    padding: 10px
    border-color: transparent
    background: transparent
    box-shadow: none
    font-weight: 400
    text-align: left
    white-space: normal

    &:hover:not(:disabled)
      border-color: transparent
      background: var(--surface-2)

    &.selected
      border-color: color-mix(in srgb, var(--primary) 40%, transparent)
      background: var(--primary-soft)

  .result-icon
    display: grid
    place-items: center
    width: 34px
    height: 34px
    flex: 0 0 34px
    border-radius: var(--radius-sm)
    color: var(--primary)
    background: var(--primary-soft)
    font-size: 1.2rem

    &.card-icon
      color: var(--warning)
      background: var(--warning-soft)

  .result-copy
    display: grid
    flex: 1
    gap: 1px
    min-width: 0

    strong
      font-size: 0.9rem

    small
      color: var(--text-2)
      font-size: 0.75rem

  .admin-badge
    margin-left: 6px
    vertical-align: middle

  .details
    display: grid
    gap: 16px
    min-width: 0

  .detail-head
    flex-direction: row
    align-items: flex-start
    justify-content: space-between
    gap: 16px

  .detail-title
    display: grid
    gap: 2px
    min-width: 0

    h2
      margin: 2px 0
      font-size: 1.4rem

  .tags
    gap: 6px
    margin-top: 6px

  .stat-strip, .info-grid
    display: grid
    grid-template-columns: repeat(4, minmax(0, 1fr))
    gap: 12px
    margin: 0

    div
      display: grid
      gap: 2px
      min-width: 0

    dt
      color: var(--text-2)
      font-size: 0.78rem

    dd
      margin: 0
      overflow: hidden
      font-weight: 600
      text-overflow: ellipsis
      white-space: nowrap

  .stat-strip div
    padding: 12px 14px
    border: 1px solid var(--border)
    border-radius: var(--radius)
    background: var(--surface)

  .info-grid
    margin-bottom: 14px

  .form-grid
    display: grid
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto
    align-items: end
    gap: 10px

  .grant-grid
    display: grid
    grid-template-columns: 1.2fr 1fr 0.8fr auto
    align-items: end
    gap: 10px

  .toggles
    margin-top: 16px

  .stats, .item-list
    display: flex
    flex-wrap: wrap
    gap: 6px
    margin-top: 14px

  .linked-cards
    display: grid
    gap: 6px

  .linked-card
    justify-content: flex-start
    gap: 12px
    height: auto
    padding: 10px 12px
    font-weight: 400
    text-align: left

  .linked-meta
    display: grid
    justify-items: end
    gap: 2px

    small
      color: var(--text-2)
      font-size: 0.75rem

  .keychips
    margin-top: 12px

    code
      font-size: 0.9rem

  .switch-label
    gap: 8px
    font-size: 0.82rem

  .delete:hover:not(:disabled)
    color: var(--danger)
    background: var(--danger-soft)

  .owner-link
    align-self: flex-start

  .padded
    padding: 20px 20px 0

  .flush
    border: 0
    border-top: 1px solid var(--border)
    border-radius: 0

  @media (max-width: vars.$w-tablet)
    .workspace
      grid-template-columns: 1fr

    .sidebar
      position: static

    .result-list
      max-height: 320px

  @media (max-width: vars.$w-mobile)
    .kpis, .stat-strip, .info-grid
      grid-template-columns: 1fr 1fr

    .form-grid, .grant-grid
      grid-template-columns: 1fr

    .detail-head
      flex-direction: column
</style>
