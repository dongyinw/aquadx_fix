<!-- Administrative user, card and keychip console. -->
<script lang="ts">
  import StatusOverlays from "../components/StatusOverlays.svelte"
  import Icon from "@iconify/svelte"
  import { ADMIN, USER } from "../libs/sdk"
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
  type ManagedGame = "mai2" | "chu3" | "ongeki"

  USER.ensureLoggedIn()

  let me: MikuNetUser | null = null
  let error = ""
  let notice = ""
  let loading = true
  let query = ""
  let regFrom = ""
  let regTo = ""
  let view: View = "users"
  let users: AdminUserSummary[] = []
  let cards: AdminCardSummary[] = []
  let selectedUser: AdminUserSummary | null = null
  let selectedCard: AdminCardSummary | null = null
  let userDetail: AdminUserDetail | null = null
  let cardDetail: AdminCardDetail | null = null

  let profileField = "displayName"
  let profileValue = ""
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

  const gameLabels: Record<ManagedGame, string> = { mai2: "舞萌 DX", chu3: "中二节奏", ongeki: "音击" }
  const gameFields: Record<ManagedGame, string[]> = {
    mai2: ["userName", "banState", "iconId", "plateId", "titleId", "partnerId", "frameId"],
    chu3: ["userName", "banState"],
    ongeki: ["userName"]
  }

  const fail = (e: unknown) => {
    error = e instanceof Error ? e.message : String(e)
    notice = ""
  }

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
    loading = true
    error = ""
    try {
      if (view === "users") {
        users = await ADMIN.users(query, regFrom, regTo)
        if (selectedUser && !users.some(user => user.auId === selectedUser?.auId)) {
          selectedUser = null
          userDetail = null
        }
      } else {
        cards = await ADMIN.cards(query, regFrom, regTo)
        if (selectedCard && !cards.some(card => card.id === selectedCard?.id)) {
          selectedCard = null
          cardDetail = null
        }
      }
    } catch (e) {
      fail(e)
    } finally {
      loading = false
    }
  }

  async function switchView(next: View) {
    if (view === next) return
    view = next
    selectedUser = null
    selectedCard = null
    userDetail = null
    cardDetail = null
    await search()
  }

  async function openUser(user: AdminUserSummary) {
    view = "users"
    selectedUser = user
    selectedCard = null
    userDetail = null
    cardDetail = null
    cardsExpanded = false
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
    selectedCard = card
    selectedUser = null
    cardDetail = null
    userDetail = null
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

  async function setBoolean(field: string, event: Event) {
    if (!userDetail) return
    const checked = (event.currentTarget as HTMLInputElement).checked
    if (field === "isAdmin" && userDetail.user.auId === me?.auId && !checked) {
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
    if (!userDetail || !confirm(`确定删除 keychip ${keychipId} 吗？`)) return
    try {
      await ADMIN.deleteKeychip(keychipId)
      await openUser(selectedUser!)
      notice = "keychip 已删除"
    } catch (e) {
      fail(e)
    }
  }

  async function deleteUser() {
    if (!userDetail || !confirm(`确定永久删除用户 ${userDetail.user.computedName} 及其游戏数据吗？`)) return
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
    if (!cardDetail || cardDetail.card.isGhost || !confirm(`确定删除卡片 ${cardDetail.card.luid} 吗？`)) return
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
    await openUser(cardDetail.user)
  }

  USER.me().then(value => {
    me = value
    if (!value.isAdmin) {
      error = "当前账号没有管理员权限"
      loading = false
      return
    }
    search()
  }).catch(e => {
    fail(e)
    loading = false
  })
</script>

<main class="content admin-page">
  <header class="page-header">
    <div>
      <span class="eyebrow">MIKUNET CONSOLE</span>
      <h1>用户管理</h1>
      <p>账号、卡片与连接权限</p>
    </div>
    <div class="header-actions">
      <div class="view-switch" role="tablist" aria-label="管理视图">
        <button class:active={view === "users"} on:click={() => switchView("users")}>
          <Icon icon="solar:user-id-bold-duotone" /> 用户
        </button>
        <button class:active={view === "cards"} on:click={() => switchView("cards")}>
          <Icon icon="solar:card-bold-duotone" /> 卡片
        </button>
      </div>
      <button class="icon" title="刷新列表" aria-label="刷新列表" on:click={search}>
        <Icon icon="line-md:rotate-270" />
      </button>
    </div>
  </header>

  {#if me?.isAdmin}
    <section class="workspace">
      <aside class="sidebar panel">
        <form class="search" on:submit|preventDefault={search}>
          <Icon icon="line-md:search" />
          <input bind:value={query} placeholder={view === "users" ? "搜索用户名、邮箱、AU ID" : "搜索卡号、LUID、用户"} aria-label="搜索" />
          {#if query}<button class="clear" type="button" title="清除搜索" aria-label="清除搜索" on:click={() => { query = ""; search() }}><Icon icon="line-md:close" /></button>{/if}
        </form>
        <div class="date-filters">
          <label>注册时间从<input type="date" bind:value={regFrom} /></label>
          <label>到<input type="date" bind:value={regTo} /></label>
          <button class="filter-submit" type="button" on:click={search}><Icon icon="solar:filter-bold-duotone" />筛选</button>
        </div>
        <div class="list-meta"><span>{view === "users" ? "Net 用户" : "卡片账户"}</span><strong>{view === "users" ? users.length : cards.length}</strong></div>

        {#if view === "users"}
          <div class="result-list">
            {#each users as user (user.auId)}
              <button class="result-row" class:selected={selectedUser?.auId === user.auId} on:click={() => openUser(user)}>
                <span class="result-icon"><Icon icon="solar:user-circle-bold-duotone" /></span>
                <span class="result-copy">
                  <strong>{user.displayName || user.username}</strong>
                  <small>#{user.auId} · {user.username}</small>
                  <small>{user.email}</small>
                  <small>注册 {formatTime(user.regTime)}</small>
                </span>
                {#if user.isAdmin}<span class="status-dot admin-dot" title="管理员"></span>{/if}
              </button>
            {:else}
              <div class="empty">没有匹配的用户</div>
            {/each}
          </div>
        {:else}
          <div class="result-list">
            {#each cards as card (card.id)}
              <button class="result-row" class:selected={selectedCard?.id === card.id} on:click={() => openCard(card)}>
                <span class="result-icon card-icon"><Icon icon="solar:card-bold-duotone" /></span>
                <span class="result-copy">
                  <strong>{card.luid}</strong>
                  <small>卡片 #{card.id} · ext {card.extId}</small>
                  <small>{card.owner?.displayName || card.owner?.username || "未绑定用户"}</small>
                  <small>注册 {formatTime(card.registerTime)}</small>
                </span>
                <span class:danger={card.status === "DELETED"} class="mini-status">{card.status === "NORMAL" ? gameBanLabel(card.mai2) : card.status}</span>
              </button>
            {:else}
              <div class="empty">没有匹配的卡片</div>
            {/each}
          </div>
        {/if}
      </aside>

      <section class="details">
        {#if view === "users" && userDetail}
          <div class="detail-title">
            <div>
              <span class="eyebrow">AU ID {userDetail.user.auId}</span>
              <h2>{userDetail.user.computedName}</h2>
              <span class="muted">{userDetail.user.email} · @{userDetail.user.username}</span>
            </div>
            <div class="title-actions">
              <div class="flags">
                {#if userDetail.user.isAdmin}<span class="tag accent">ADMIN</span>{/if}
                {#if userDetail.mai2}<span class="tag">MAI2</span>{/if}
              </div>
              <button class="icon danger" title="删除用户" aria-label="删除用户" on:click={deleteUser}><Icon icon="tabler:trash-x-filled" /></button>
            </div>
          </div>

          <section class="stat-strip">
            <div><small>注册时间</small><strong>{formatTime(userDetail.user.regTime)}</strong></div>
            <div><small>最近登录</small><strong>{formatTime(userDetail.user.lastLogin)}</strong></div>
            <div><small>卡片</small><strong>{userDetail.cards.length}</strong></div>
            <div><small>Keychip</small><strong>{userDetail.keychips.length}</strong></div>
          </section>

          <section class="panel">
            <div class="section-heading"><h3>账号设置</h3><span>资料与权限</span></div>
            <div class="form-grid">
              <label>修改字段
                <select bind:value={profileField} on:change={syncProfileValue}>
                  <option value="displayName">显示名称</option>
                  <option value="profileLocation">所在地</option>
                  <option value="profileBio">个人简介</option>
                  <option value="country">国家代码</option>
                  <option value="region">区域代码</option>
                </select>
              </label>
              <label>字段值<input bind:value={profileValue} /></label>
              <button on:click={saveProfile}><Icon icon="solar:check-circle-bold" />保存资料</button>
            </div>
            <div class="toggle-grid">
              <label><input type="checkbox" checked={userDetail.user.emailConfirmed} on:change={(e) => setBoolean("emailConfirmed", e)} /> 邮箱已验证</label>
              <label><input type="checkbox" checked={userDetail.user.canModifyKeychips} on:change={(e) => setBoolean("canModifyKeychips", e)} /> 允许用户管理 keychip</label>
              <label title={userDetail.user.auId === me?.auId ? "不能取消当前登录账号的管理员权限" : "管理员权限"}><input type="checkbox" checked={userDetail.user.isAdmin} disabled={userDetail.user.auId === me?.auId} on:change={(e) => setBoolean("isAdmin", e)} /> 管理员权限{#if userDetail.user.auId === me?.auId}<small class="self-admin-lock">当前账号</small>{/if}</label>
            </div>
          </section>

          <section class="panel game-profile-panel">
            <div class="section-heading"><div><h3>游戏档案管理</h3><span>支持舞萌、中二和音击</span></div><div class="game-switcher">{#each Object.keys(gameLabels) as game}<button class:active={selectedGame === game} disabled={!gameProfile(game as ManagedGame)} on:click={() => changeGame(game as ManagedGame)}>{gameLabels[game as ManagedGame]}</button>{/each}</div></div>
            {#if gameProfile(selectedGame)}
              {@const currentProfile = gameProfile(selectedGame)}
              <div class="form-grid">
                <label>修改字段<select bind:value={gameField} on:change={syncGameProfileValue}>{#each gameFields[selectedGame] as field}<option value={field}>{field === "userName" ? "游戏名称" : field === "banState" ? "banState" : field}</option>{/each}</select></label>
                <label>字段值{#if gameField === "banState"}<select bind:value={gameValue}><option value="0">0 · 正常</option><option value="1">1 · 限制</option><option value="2">2 · 封禁</option></select>{:else}<input bind:value={gameValue} />{/if}</label>
                <button on:click={saveMaiProfile}><Icon icon="solar:check-circle-bold" />保存档案</button>
              </div>
              <div class="stats"><span>Rating {currentProfile?.playerRating ?? 0}</span><span>最高 {currentProfile?.highestRating ?? 0}</span><span>游玩 {currentProfile?.playCount ?? 0} 次</span><span>最近机台 {currentProfile?.lastClientId || "暂无"}</span></div>
            {:else}<p class="empty">该用户还没有 {gameLabels[selectedGame]} 用户资料。</p>{/if}
          </section>

          <section class="panel">
            <div class="section-heading"><h3>发放收藏品 / Ticket</h3><span>itemKind 12 为 Ticket</span></div>
            <form class="grant-grid" on:submit|preventDefault={grantItem}>
              <label>类型<select bind:value={itemKind}><option value={1}>1 · Plate</option><option value={2}>2 · Title</option><option value={3}>3 · Icon</option><option value={11}>11 · Frame</option><option value={12}>12 · Ticket</option></select></label>
              <label>物品 ID<input type="number" min="0" bind:value={itemId} /></label>
              <label>数量<input type="number" min="1" max="1000000" bind:value={itemAmount} /></label>
              <button type="submit"><Icon icon="solar:gift-bold-duotone" />发放</button>
            </form>
            <div class="item-list">{#each userDetail.items.slice(0, 40) as item}<span class="item-chip">{item.itemKind}/{item.itemId} × {item.stock}</span>{:else}<span class="empty">暂无物品记录</span>{/each}</div>
          </section>

          <section class="panel">
            <div class="section-heading"><div><h3>关联卡片</h3><span>{userDetail.cards.length} 张卡片 · 点击查看卡片档案</span></div>{#if userDetail.cards.length > 2}<button class="collapse-toggle" on:click={() => cardsExpanded = !cardsExpanded}><Icon icon={cardsExpanded ? "line-md:chevron-small-up" : "line-md:chevron-small-down"} />{cardsExpanded ? "收起" : `展开全部 ${userDetail.cards.length} 张`}</button>{/if}</div>
            <div class="linked-cards">
              {#each userDetail.cards as card, index (card.id)}
                {#if cardsExpanded || index < 2}<button class="linked-card" on:click={() => openCard(card)}><span><strong>{card.luid}</strong><small>#{card.id} · ext {card.extId}</small></span><span><small>{card.isGhost ? "Ghost" : "实体卡"}</small><small>{card.mai2?.playCount ?? 0} 次游玩</small></span><Icon icon="line-md:chevron-small-right" /></button>{/if}
              {:else}<span class="empty">暂无关联卡片</span>{/each}
            </div>
          </section>

          <section class="panel">
            <div class="section-heading"><h3>Keychip 管理</h3><span>停用会立即清理在线会话</span></div>
            <form class="keychip-add" on:submit|preventDefault={addKeychip}><input bind:value={newKeychip} placeholder="15 位 keychip，可带短横线" /><button type="submit">添加</button></form>
            <div class="keychip-list">
              {#each userDetail.keychips as keychip (keychip.id)}
                <div class="keychip-row"><code>{keychip.keychipId}</code><label class="switch"><input type="checkbox" checked={keychip.enabled} on:change={(e) => toggleKeychip(keychip.keychipId, e)} /><span>允许连接</span></label><button class="icon error" title="删除 keychip" aria-label="删除 keychip" on:click={() => deleteKeychip(keychip.keychipId)}><Icon icon="tabler:trash-x-filled" /></button></div>
              {:else}<span class="empty">暂无 keychip</span>{/each}
            </div>
          </section>
        {:else if view === "cards" && cardDetail}
          <div class="detail-title">
            <div><span class="eyebrow">CARD ID {cardDetail.card.id}</span><h2>{cardDetail.card.luid}</h2><span class="muted">ext {cardDetail.card.extId} · {cardDetail.card.status}</span></div>
            <div class="title-actions"><span class="tag" class:danger={cardDetail.mai2?.banState === 2}>{gameBanLabel(cardDetail.mai2)}</span><button class="icon danger" disabled={cardDetail.card.isGhost} title={cardDetail.card.isGhost ? "Ghost 卡片随用户删除" : "删除卡片"} aria-label="删除卡片" on:click={deleteCard}><Icon icon="tabler:trash-x-filled" /></button></div>
          </div>

          <section class="stat-strip">
            <div><small>注册时间</small><strong>{formatTime(cardDetail.card.registerTime)}</strong></div>
            <div><small>最后访问</small><strong>{formatTime(cardDetail.card.accessTime)}</strong></div>
            <div><small>游玩次数</small><strong>{cardDetail.mai2?.playCount ?? 0}</strong></div>
            <div><small>最近机台</small><strong>{cardDetail.mai2?.lastClientId || "暂无"}</strong></div>
          </section>

          <section class="panel">
            <div class="section-heading"><h3>卡片信息</h3><span>{cardDetail.card.isGhost ? "Ghost 卡片" : "实体卡片"}</span></div>
            <div class="info-grid"><div><small>LUID</small><strong>{cardDetail.card.luid}</strong></div><div><small>ext ID</small><strong>{cardDetail.card.extId}</strong></div><div><small>注册时间</small><strong>{formatTime(cardDetail.card.registerTime)}</strong></div><div><small>最后访问</small><strong>{formatTime(cardDetail.card.accessTime)}</strong></div></div>
            {#if cardDetail.user}<button class="owner-link" on:click={openCardOwner}><Icon icon="solar:user-circle-bold-duotone" />绑定用户：{cardDetail.user.displayName || cardDetail.user.username} <Icon icon="line-md:chevron-small-right" /></button>{:else}<p class="empty">未绑定 Net 用户</p>{/if}
          </section>

          <section class="panel">
            <div class="section-heading"><h3>maimai2 卡片档案</h3><span>{gameBanLabel(cardDetail.mai2)}</span></div>
            {#if cardDetail.mai2}
              <div class="form-grid"><label>修改字段<select bind:value={cardMaiField} on:change={syncCardMaiValue}><option value="userName">游戏名称</option><option value="banState">banState</option><option value="iconId">头像</option><option value="plateId">称号框</option><option value="titleId">称号</option><option value="partnerId">搭档</option><option value="frameId">背景框</option></select></label><label>字段值{#if cardMaiField === "banState"}<select bind:value={cardMaiValue}><option value="0">0 · 正常</option><option value="1">1 · 限制</option><option value="2">2 · 封禁</option></select>{:else}<input bind:value={cardMaiValue} />{/if}</label><button on:click={saveCardMaiProfile}><Icon icon="solar:check-circle-bold" />保存档案</button></div>
              <div class="stats"><span>Rating {cardDetail.mai2.playerRating}</span><span>最高 {cardDetail.mai2.highestRating}</span><span>最近游玩 {cardDetail.mai2.lastPlayDate || "暂无"}</span><span>机台号 {cardDetail.mai2.lastClientId || "暂无"}</span></div>
            {:else}<p class="empty">该卡片还没有 maimai2 用户资料。</p>{/if}
          </section>

          <section class="panel">
            <div class="section-heading"><h3>发放收藏品 / Ticket</h3><span>按当前卡片发放</span></div>
            <form class="grant-grid" on:submit|preventDefault={grantCardItem}><label>类型<select bind:value={itemKind}><option value={1}>1 · Plate</option><option value={2}>2 · Title</option><option value={3}>3 · Icon</option><option value={11}>11 · Frame</option><option value={12}>12 · Ticket</option></select></label><label>物品 ID<input type="number" min="0" bind:value={itemId} /></label><label>数量<input type="number" min="1" max="1000000" bind:value={itemAmount} /></label><button type="submit"><Icon icon="solar:gift-bold-duotone" />发放</button></form>
            <div class="item-list">{#each cardDetail.items.slice(0, 40) as item}<span class="item-chip">{item.itemKind}/{item.itemId} × {item.stock}</span>{:else}<span class="empty">暂无物品记录</span>{/each}</div>
          </section>

          <section class="panel log-panel">
            <div class="section-heading"><h3>最近游玩日志</h3><span>{cardDetail.playlogs.length} 条</span></div>
            <div class="table-scroll"><table><thead><tr><th>时间</th><th>机台</th><th>歌曲</th><th>难度</th><th>达成率</th></tr></thead><tbody>{#each cardDetail.playlogs as playlog}<tr><td>{playlog.userPlayDate || playlog.playDate || "暂无"}</td><td>{playlog.placeId} · {playlog.placeName || "未知机台"}</td><td>{playlog.musicId}</td><td>{playlog.level}</td><td>{(playlog.achievement / 10000).toFixed(4)}%</td></tr>{:else}<tr><td colspan="5" class="empty">暂无游玩日志</td></tr>{/each}</tbody></table></div>
          </section>
        {:else}
          <div class="empty-state panel"><Icon icon={view === "users" ? "solar:user-id-bold-duotone" : "solar:card-bold-duotone"} /><strong>从左侧选择{view === "users" ? "用户" : "卡片"}</strong><span>详情会显示在这里</span></div>
        {/if}
      </section>
    </section>
  {/if}
</main>

<StatusOverlays {error} {loading} />
{#if notice}<div class="notice"><Icon icon="solar:check-circle-bold" />{notice}</div>{/if}

<style lang="sass">
  @use "../vars"

  .admin-page
    max-width: 1440px

  .page-header, .detail-title, .section-heading, .keychip-row
    display: flex
    align-items: center
    justify-content: space-between
    gap: 16px

  .page-header
    margin-bottom: 22px

    h1, h2, h3, p
      margin: 0

    h1
      margin-top: 2px
      font-size: clamp(1.65rem, 3vw, 2.3rem)

    p
      margin-top: 4px
      color: vars.$c-sub
      font-size: 0.88rem

  .header-actions, .view-switch, .title-actions, .flags
    display: flex
    align-items: center
    gap: 8px

  .view-switch
    padding: 4px
    border: 1px solid rgba(255, 255, 255, 0.08)
    border-radius: 10px
    background: rgba(34, 51, 54, 0.06)

    button
      display: inline-flex
      align-items: center
      gap: 6px
      padding: 7px 11px
      color: vars.$c-sub
      background: transparent

      &.active
        color: vars.$c-main
        background: rgba(57, 197, 187, 0.14)
        border-color: rgba(57, 197, 187, 0.3)

  .workspace
    display: grid
    grid-template-columns: minmax(280px, 340px) minmax(0, 1fr)
    gap: 22px
    align-items: start

  .panel
    border: 1px solid rgba(255, 255, 255, 0.09)
    border-radius: 14px
    padding: 20px
    background: rgba(255, 255, 255, 0.72)
    box-shadow: 0 18px 50px rgba(43, 72, 76, 0.10)
    backdrop-filter: blur(18px)

  .sidebar
    position: sticky
    top: 76px
    padding: 12px

  .search
    display: flex
    align-items: center
    gap: 8px
    padding: 4px 9px
    border: 1px solid rgba(255, 255, 255, 0.08)
    border-radius: 10px
    background: rgba(34, 51, 54, 0.045)
    color: vars.$c-sub

    input
      min-width: 0
      flex: 1
      padding: 7px 0
      border: 0
      background: transparent

      &:focus
        border: 0

    .clear
      display: flex
      padding: 4px
      border: 0
      background: transparent

  .date-filters
    display: grid
    grid-template-columns: 1fr 1fr auto
    gap: 7px
    margin-top: 9px

    label
      display: grid
      gap: 4px
      color: vars.$c-sub
      font-size: 0.68rem

    input
      padding: 7px 6px
      font-size: 0.72rem

  .filter-submit
    display: inline-flex
    align-items: center
    align-self: end
    gap: 4px
    padding: 7px 9px
    color: vars.$c-main
    background: rgba(7, 143, 136, 0.08)
    font-size: 0.72rem

  .list-meta
    display: flex
    justify-content: space-between
    padding: 16px 6px 9px
    color: vars.$c-sub
    font-size: 0.78rem

    strong
      color: vars.$c-main

  .result-list
    display: grid
    gap: 4px
    max-height: calc(100vh - 230px)
    overflow-y: auto

  .result-row
    display: flex
    align-items: center
    gap: 10px
    width: 100%
    min-width: 0
    padding: 10px
    border: 1px solid transparent
    border-radius: 10px
    text-align: left
    background: transparent

    &:hover, &.selected
      border-color: rgba(57, 197, 187, 0.46)
      background: rgba(57, 197, 187, 0.10)

  .result-icon
    display: grid
    place-items: center
    width: 34px
    height: 34px
    flex: 0 0 34px
    border-radius: 10px
    color: vars.$c-main
    background: rgba(57, 197, 187, 0.12)
    font-size: 1.2rem

    &.card-icon
      color: #ffd166
      background: rgba(255, 209, 102, 0.12)

  .result-copy
    display: grid
    min-width: 0
    flex: 1
    gap: 1px

    strong, small
      overflow: hidden
      text-overflow: ellipsis
      white-space: nowrap

    strong
      color: vars.$c-text
      font-size: 0.9rem

    small
      color: vars.$c-sub
      font-size: 0.72rem

  .status-dot
    width: 7px
    height: 7px
    flex: 0 0 7px
    border-radius: 50%
    background: vars.$c-good
    box-shadow: 0 0 12px vars.$c-good

  .mini-status
    flex: 0 0 auto
    color: vars.$c-good
    font-size: 0.7rem

    &.danger
      color: vars.$c-error

  .details
    display: grid
    gap: 16px
    min-width: 0

  .detail-title
    padding: 3px 2px

    h2
      margin: 4px 0 2px
      overflow: hidden
      text-overflow: ellipsis
      white-space: nowrap

  .eyebrow
    color: vars.$c-main
    font-size: 0.7rem
    font-weight: 700
    letter-spacing: 0.12em

  .muted, .section-heading > span, .empty
    color: vars.$c-sub
    font-size: 0.82rem

  .stat-strip
    display: grid
    grid-template-columns: repeat(4, 1fr)
    gap: 1px
    overflow: hidden
    border: 1px solid rgba(255, 255, 255, 0.08)
    border-radius: 14px
    background: rgba(255, 255, 255, 0.70)

    div
      display: grid
      gap: 4px
      min-width: 0
      padding: 13px 15px
      background: rgba(255, 255, 255, 0.44)

    small
      color: vars.$c-sub
      font-size: 0.7rem

    strong
      overflow: hidden
      color: vars.$c-text
      font-size: 0.82rem
      text-overflow: ellipsis
      white-space: nowrap

  .section-heading
    margin-bottom: 16px

    h3
      margin: 0
      font-size: 1.02rem

  .game-switcher
    display: flex
    flex-wrap: wrap
    gap: 4px
    padding: 3px
    border-radius: 10px
    background: rgba(96, 114, 118, 0.07)

    button
      padding: 6px 8px
      border: 0
      border-radius: 8px
      box-shadow: none
      color: vars.$c-sub
      background: transparent
      font-size: 0.72rem

      &.active
        color: vars.$c-main
        background: rgba(7, 143, 136, 0.12)

      &:disabled
        cursor: not-allowed
        opacity: 0.4

  .collapse-toggle
    display: inline-flex
    align-items: center
    gap: 4px
    padding: 6px 8px
    color: vars.$c-main
    background: rgba(7, 143, 136, 0.07)
    font-size: 0.72rem

  .form-grid, .grant-grid, .info-grid
    display: grid
    gap: 10px

  .form-grid
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto
    align-items: end

  .grant-grid
    grid-template-columns: 1.2fr 1fr 0.8fr auto
    align-items: end

  label
    display: grid
    gap: 5px
    min-width: 0
    color: vars.$c-sub
    font-size: 0.78rem

  .form-grid label, .grant-grid label
    input, select
      width: 100%

  .toggle-grid
    display: flex
    flex-wrap: wrap
    gap: 12px 18px
    margin-top: 16px

    label
      display: flex
      align-items: center
      gap: 7px

    .self-admin-lock
      color: vars.$c-main
      font-size: 0.68rem

  .stats, .flags, .item-list
    display: flex
    flex-wrap: wrap
    gap: 8px

  .stats
    margin-top: 15px
    color: vars.$c-sub
    font-size: 0.78rem

    span
      padding-right: 10px
      border-right: 1px solid rgba(255, 255, 255, 0.12)

      &:last-child
        border-right: 0

  .tag, .item-chip
    display: inline-flex
    align-items: center
    gap: 4px
    padding: 4px 8px
    border: 1px solid rgba(255, 255, 255, 0.14)
    border-radius: 999px
    color: vars.$c-sub
    font-size: 0.72rem

    &.accent
      color: vars.$c-main
      border-color: rgba(57, 197, 187, 0.4)

    &.danger
      color: vars.$c-error
      border-color: rgba(255, 107, 129, 0.38)

  .item-list
    margin-top: 14px

  .item-chip
    color: vars.$c-sub

  .linked-cards
    display: grid
    gap: 6px

  .linked-card
    display: flex
    align-items: center
    gap: 12px
    width: 100%
    padding: 11px 12px
    border: 1px solid rgba(255, 255, 255, 0.08)
    border-radius: 10px
    text-align: left
    background: rgba(255, 255, 255, 0.42)

    &:hover
      border-color: rgba(57, 197, 187, 0.4)

    > span
      display: grid
      min-width: 0
      flex: 1
      gap: 2px

      &:last-of-type
        flex: 0 0 auto
        text-align: right

    small
      color: vars.$c-sub
      font-size: 0.7rem

  .keychip-add
    display: grid
    grid-template-columns: minmax(0, 1fr) auto
    gap: 10px

  .keychip-list
    display: grid
    margin-top: 12px

  .keychip-row
    min-height: 46px
    border-bottom: 1px solid rgba(255, 255, 255, 0.08)

    &:last-child
      border-bottom: 0

    code
      color: vars.$c-sub
      font-size: 0.85rem

  .switch
    display: flex
    align-items: center
    gap: 7px

  .info-grid
    grid-template-columns: repeat(4, 1fr)
    margin-bottom: 16px

    div
      display: grid
      gap: 4px
      min-width: 0

    small
      color: vars.$c-sub
      font-size: 0.7rem

    strong
      overflow: hidden
      text-overflow: ellipsis
      white-space: nowrap

  .owner-link
    display: inline-flex
    align-items: center
    gap: 7px
    padding: 7px 10px
    color: vars.$c-main
    background: rgba(57, 197, 187, 0.08)

  .table-scroll
    overflow-x: auto

  table
    width: 100%
    border-collapse: collapse
    font-size: 0.78rem

  th, td
    padding: 10px 8px
    border-bottom: 1px solid rgba(255, 255, 255, 0.08)
    text-align: left
    white-space: nowrap

  th
    color: vars.$c-sub
    font-size: 0.7rem
    font-weight: 500

  td
    color: vars.$c-text

  .empty-state
    display: grid
    place-items: center
    gap: 7px
    min-height: 240px
    color: vars.$c-sub

    :global(svg)
      color: vars.$c-main
      font-size: 2rem

  .notice
    position: fixed
    right: 24px
    bottom: 24px
    z-index: 20
    display: flex
    align-items: center
    gap: 8px
    padding: 11px 14px
    border: 1px solid rgba(126, 230, 198, 0.35)
    border-radius: 10px
    background: rgba(255, 255, 255, 0.88)
    color: vars.$c-good
    box-shadow: 0 12px 35px rgba(0, 0, 0, 0.24)
    backdrop-filter: blur(14px)

  .danger
    color: vars.$c-error

  button:disabled
    cursor: not-allowed
    opacity: 0.35

  @media (max-width: vars.$w-mobile)
    .page-header, .detail-title
      align-items: flex-start
      flex-direction: column

    .header-actions
      width: 100%
      justify-content: space-between

    .workspace
      grid-template-columns: 1fr

    .sidebar
      position: static

    .result-list
      max-height: 270px

    .form-grid, .grant-grid
      grid-template-columns: 1fr

    .stat-strip, .info-grid
      grid-template-columns: 1fr 1fr

    .title-actions
      width: 100%
      justify-content: space-between

    .date-filters
      grid-template-columns: 1fr 1fr

      .filter-submit
        grid-column: 1 / -1
        justify-content: center

    .game-profile-panel .section-heading
      align-items: flex-start
      flex-direction: column
</style>
