<script lang="ts">
  import { fade } from 'svelte/transition';
  import Spinner from "../components/ui/Spinner.svelte";
  import Icon from "../components/ui/Icon.svelte";
  import { USER } from "../libs/sdk";
  import { t } from "../libs/i18n"

  import * as acUrl from "../libs/acUrl"
  const hasAc = acUrl.has()
  let previewAc = ""

  if (hasAc) {
    previewAc = acUrl.preview() || ""
  }

  let params = new URLSearchParams(window.location.search)

  let state = "login"
  $: isSignup = state === "signup"
  let submitting = false
  let showPassword = false

  function switchState(next: string) {
    state = next
    error = ""
    showPassword = false
  }

  let email = ""
  let password = ""
  let username = ""

  let error = ""
  let verifyMsg = ""
  let token = ""

  if (USER.isLoggedIn()) {
    USER.accessStatus().then(status => {
      if (status.banState === 2) {
        localStorage.removeItem('token')
        error = t('welcome.account-banned')
        state = 'login'
        return
      }
      window.location.href = "/home"
    }).catch(() => window.location.href = "/home")
  }
  if (params.get('code')) {
    token = params.get('code')!
    if (location.pathname === '/verify') {
      state = 'verify'
      verifyMsg = t("welcome.verifying")
      submitting = true

      // Send request to server
      USER.confirmEmail(token)
        .then(() => {
          verifyMsg = t('welcome.verified')
          submitting = false

        // Clear the query param
        window.history.replaceState({}, document.title, window.location.pathname)
      })
      .catch(e => {
        verifyMsg = t('welcome.verification-failed', { message: e.message })
        submitting = false
      })
    }
    else if (location.pathname === '/reset-password') {
      state = 'reset'
    }
  }
  async function submit(): Promise<any> {
    if (submitting) return
    submitting = true
    error = ""

    // Check if username and password are valid
    if (email === "" || password === "") {
      error = t("welcome.email-password-missing")
      return submitting = false
    }

    // Signup
    if (isSignup) {
      if (username === "") {
        error = t("welcome.username-missing")
        return submitting = false
      }

      // Send request to server
      await USER.register({ username, email, password, turnstile: "" })
        .then(() => {
          // Show verify email message
          state = 'verify'
          verifyMsg = t("welcome.verification-sent", { email })
        })
        .catch(e => {
          error = e.message
          submitting = false
        })
    }
    else {
      // Send request to server
      await USER.login({ email, password, turnstile: "" })
        .then(async () => {
          const status = await USER.accessStatus()
          if (status.banState === 2) {
            localStorage.removeItem('token')
            error = t('welcome.account-banned')
            state = 'login'
            return
          }
          window.location.href = "/home"
        })
        .catch(e => {
          if (e.message === 'Email not verified - STATE_0') {
            state = 'verify'
            verifyMsg = t("welcome.verify-state-0")
          }
          else if (e.message === 'Email not verified - STATE_1') {
            state = 'verify'
            verifyMsg = t("welcome.verify-state-1")
          }
          else if (e.message === 'Email not verified - STATE_2') {
            state = 'verify'
            verifyMsg = t("welcome.verify-state-2")
          }
          else {
            error = e.message
            submitting = false // unnecessary? see line 113, same for both reset functions
          }
        })
    }

    submitting = false
  }

  async function resetPassword(): Promise<any> {
    if (submitting) return
    submitting = true;
    error = ""

    if (email === "") {
      error = t("welcome.email-missing")
      return submitting = false
    }

    // Send request to server
    await USER.resetPassword({ email, turnstile: "" })
      .then(() => {
          // Show email sent message, reusing email verify page
          state = 'verify'
          verifyMsg = t("welcome.reset-password-sent", { email })
        })
      .catch(e => {
          if (e.message === "Reset request rejected - STATE_0") {
            state = 'verify'
            verifyMsg = t("welcome.reset-state-0")
          }
          else if (e.message === "Reset request rejected - STATE_1") {
            state = 'verify'
            verifyMsg = t("welcome.reset-state-1")
          }
          else {
            error = e.message
            submitting = false
          }
        })

    submitting = false
  }

  async function changePassword(): Promise<any> {
    if (submitting) return
    submitting = true
    error = ""

    if (password === "") {
      error = t("welcome.password-missing")
      return submitting = false
    }

    // Send request to server
    await USER.changePassword({ token, password })
      .then(() => {
        state = 'verify'
        verifyMsg = t("welcome.password-reset-done")
      })
      .catch(e => {
        error = e.message
        submitting = false
      })

    submitting = false
  }

</script>

<main id="welcome-page">
  <section class="intro">
    <img class="brand-mark" src="/assets/branding/mikunet-logo.webp" alt="MikuNet" width="960" height="640" fetchpriority="high" />
    <span class="eyebrow">舞萌 DX · 中二节奏 · 音击</span>
    <h1>每一次出勤，<br />都值得被记住。</h1>
    <p class="lead">绑定卡片后，成绩、Rating 与收藏会自动同步到 MikuNet，离开机台也能随时回顾自己的进步。</p>
    <ul class="features">
      <li><span><Icon icon="solar:card-bold-duotone" /></span><div><strong>一个账号，多张卡片</strong><small>Access Code 与 FeliCa 卡都能绑定到同一账号</small></div></li>
      <li><span><Icon icon="solar:chart-2-bold-duotone" /></span><div><strong>成绩一目了然</strong><small>Rating 趋势、B50、出勤热力图与服务器排行</small></div></li>
      <li><span><Icon icon="solar:users-group-two-rounded-bold-duotone" /></span><div><strong>和好友一起玩</strong><small>添加好友、设置劲敌，加入同好圈子</small></div></li>
      <li><span><Icon icon="solar:shield-check-bold-duotone" /></span><div><strong>存档自己掌握</strong><small>随时导入、导出，换服换机都不丢数据</small></div></li>
    </ul>
  </section>

  <section class="auth-card" aria-labelledby="auth-title">
    {#if hasAc}
      <div class="alert alert-info ac-hint"><Icon icon="solar:card-bold-duotone" /><div><p>{t('welcome.login_link')}</p><strong class="mono">{previewAc}</strong></div></div>
    {/if}

    {#if state === "login" || state === "signup"}
      <div class="segmented auth-tabs" role="tablist" aria-label="登录或注册">
        <button type="button" role="tab" aria-selected={state === "login"} on:click={() => switchState('login')}>{t('welcome.btn-login')}</button>
        <button type="button" role="tab" aria-selected={state === "signup"} on:click={() => switchState('signup')}>{t('welcome.btn-signup')}</button>
      </div>
      <div class="auth-heading">
        <h2 id="auth-title">{isSignup ? "注册 MikuNet 账号" : "登录 MikuNet"}</h2>
        <p>{isSignup ? "注册后请前往邮箱点击验证链接，验证完成即可登录。" : "欢迎回来！登录后即可查看成绩、管理卡片。"}</p>
      </div>

      <form class="auth-form" on:submit|preventDefault={submit} novalidate>
        {#if error}<div class="alert alert-danger" role="alert" in:fade={{ duration: 120 }}><Icon icon="solar:danger-triangle-bold-duotone" /><p>{error}</p></div>{/if}
        {#if isSignup}
          <label class="field"><span class="field-label">{t('username')}</span>
            <input type="text" name="username" autocomplete="username" placeholder="字母、数字或下划线" bind:value={username} disabled={submitting} />
          </label>
        {/if}
        <label class="field"><span class="field-label">{isSignup ? t('email') : "用户名或邮箱"}</span>
          {#if isSignup}
            <input type="email" name="email" autocomplete="email" inputmode="email" placeholder="you@example.com" bind:value={email} disabled={submitting} />
          {:else}
            <input type="text" name="username" autocomplete="username" placeholder="输入用户名或注册邮箱" bind:value={email} disabled={submitting} />
          {/if}
        </label>
        <div class="field">
          <div class="label-row">
            <label class="field-label" for="auth-password">{t('password')}</label>
            {#if state === "login"}<button class="btn-link forgot" type="button" on:click={() => switchState('submitreset')}>{t('welcome.btn-reset-password')}</button>{/if}
          </div>
          <div class="password-input">
            {#if showPassword}
              <input id="auth-password" type="text" autocomplete={isSignup ? "new-password" : "current-password"} bind:value={password} disabled={submitting} />
            {:else}
              <input id="auth-password" type="password" autocomplete={isSignup ? "new-password" : "current-password"} placeholder="••••••••" bind:value={password} disabled={submitting} />
            {/if}
            <button class="btn-ghost btn-icon btn-sm reveal" type="button" aria-label={showPassword ? "隐藏密码" : "显示密码"} aria-pressed={showPassword} on:click={() => showPassword = !showPassword}>
              <Icon icon={showPassword ? "solar:eye-closed-bold-duotone" : "solar:eye-bold-duotone"} />
            </button>
          </div>
        </div>
        <button class="btn-primary btn-lg btn-block" type="submit" disabled={submitting}>
          {#if submitting}<Spinner />{:else}<Icon icon={isSignup ? "solar:user-plus-bold" : "solar:login-2-bold"} />{/if}
          {isSignup ? t('welcome.btn-signup') : t('welcome.btn-login')}
        </button>
      </form>
    {:else if state === "submitreset"}
      <button class="btn-ghost btn-sm back" type="button" on:click={() => switchState('login')}><Icon icon="line-md:chevron-small-left" />{t('back')}</button>
      <div class="auth-heading">
        <h2 id="auth-title">找回密码</h2>
        <p>输入注册时使用的邮箱，我们会发送一封重置密码的邮件。</p>
      </div>
      <form class="auth-form" on:submit|preventDefault={resetPassword} novalidate>
        {#if error}<div class="alert alert-danger" role="alert"><Icon icon="solar:danger-triangle-bold-duotone" /><p>{error}</p></div>{/if}
        <label class="field"><span class="field-label">{t('email')}</span>
          <input type="email" name="email" autocomplete="email" inputmode="email" placeholder="you@example.com" bind:value={email} disabled={submitting} />
        </label>
        <button class="btn-primary btn-lg btn-block" type="submit" disabled={submitting}>
          {#if submitting}<Spinner />{/if}{t('welcome.btn-submit-reset-password')}
        </button>
      </form>
    {:else if state === "verify"}
      <div class="message-state" role="status">
        <span class="message-icon">{#if submitting}<Spinner size="28px" />{:else}<Icon icon="solar:letter-opened-bold-duotone" />{/if}</span>
        <h2 id="auth-title">{submitting ? "正在处理…" : "提示"}</h2>
        <p>{verifyMsg}</p>
        {#if !submitting}<button class="btn-primary btn-block" type="button" on:click={() => switchState('login')}>{t('welcome.btn-login')}</button>{/if}
      </div>
    {:else if state === "reset"}
      <div class="auth-heading">
        <h2 id="auth-title">设置新密码</h2>
        <p>请输入新的密码，设置成功后使用新密码重新登录。</p>
      </div>
      <form class="auth-form" on:submit|preventDefault={changePassword} novalidate>
        {#if error}<div class="alert alert-danger" role="alert"><Icon icon="solar:danger-triangle-bold-duotone" /><p>{error}</p></div>{/if}
        <label class="field"><span class="field-label">{t('new-password')}</span>
          <input type="password" name="new-password" autocomplete="new-password" placeholder="••••••••" bind:value={password} disabled={submitting} />
        </label>
        <button class="btn-primary btn-lg btn-block" type="submit" disabled={submitting}>
          {#if submitting}<Spinner />{/if}{t('welcome.btn-submit-new-password')}
        </button>
      </form>
    {/if}
  </section>
</main>

<style lang="sass">
  @use "../vars"

  #welcome-page
    display: grid
    grid-template-columns: minmax(0, 1fr) minmax(360px, 420px)
    align-items: center
    gap: clamp(32px, 7vw, 96px)
    width: min(1120px, 100%)
    min-height: calc(100dvh - var(--nav-height))
    margin: 0 auto
    padding: 48px 28px

  .intro
    max-width: 560px

  .intro .eyebrow
    display: block
    margin-bottom: 10px

  .brand-mark
    display: block
    width: min(300px, 70%)
    height: auto
    margin-bottom: 20px

  h1
    margin: 0 0 14px
    text-wrap: balance
    font-size: clamp(2rem, 4.6vw, 3.4rem)
    line-height: 1.12
    letter-spacing: -0.02em

  .lead
    margin: 0
    text-wrap: pretty
    color: var(--text-2)
    font-size: 1.08rem

  .features
    display: grid
    gap: 14px
    margin: 32px 0 0
    padding: 0
    list-style: none

    li
      display: flex
      align-items: center
      gap: 12px

    li > span
      display: grid
      place-items: center
      width: 40px
      height: 40px
      flex: 0 0 40px
      border-radius: var(--radius)
      color: var(--primary)
      background: var(--primary-soft)
      font-size: 1.3rem

    div
      display: grid

    small
      color: var(--text-2)
      font-size: 0.85rem

  .auth-card
    display: flex
    flex-direction: column
    gap: 18px
    width: 100%
    padding: 28px
    border: 1px solid var(--border)
    border-radius: var(--radius-xl)
    background: var(--surface)
    box-shadow: var(--shadow-lg)

  .ac-hint
    p
      margin: 0 0 2px

  .auth-tabs
    display: flex

    > button
      flex: 1

  .auth-heading
    h2
      margin: 0
      font-size: 1.4rem

    p
      margin: 4px 0 0
      color: var(--text-2)
      font-size: 0.9rem

  .auth-form
    display: flex
    flex-direction: column
    gap: 14px

    .btn-lg
      margin-top: 4px

  .label-row
    display: flex
    align-items: center
    justify-content: space-between

  .forgot
    font-size: 0.82rem

  .password-input
    position: relative

    input
      padding-right: 44px

    .reveal
      position: absolute
      top: 50%
      right: 4px
      transform: translateY(-50%)

      &:active:not(:disabled)
        transform: translateY(-50%)

  .back
    align-self: flex-start
    margin: -8px 0 -6px -8px

  .message-state
    display: flex
    flex-direction: column
    align-items: center
    gap: 8px
    padding: 12px 0 4px
    text-align: center

    h2
      margin: 6px 0 0
      font-size: 1.25rem

    p
      margin: 0 0 12px
      color: var(--text-2)

  .message-icon
    display: grid
    place-items: center
    width: 60px
    height: 60px
    border-radius: 50%
    color: var(--primary)
    background: var(--primary-soft)
    font-size: 1.9rem

  @media (max-width: vars.$w-tablet)
    #welcome-page
      grid-template-columns: 1fr
      justify-items: center
      gap: 28px
      padding: 28px 16px 40px

    .intro
      text-align: center

    .brand-mark
      width: min(220px, 60%)
      margin: 0 auto 14px

    .features
      display: none

    .auth-card
      max-width: 440px

  @media (max-width: vars.$w-mobile)
    .auth-card
      padding: 22px 18px
</style>
