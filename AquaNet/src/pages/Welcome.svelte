<script lang="ts">
  import { slide } from 'svelte/transition';
  import Icon from "@iconify/svelte";
  import { USER } from "../libs/sdk";
  import { t } from "../libs/i18n"

  import * as acUrl from "../libs/acUrl"
  const hasAc = acUrl.has()
  let previewAc = ""

  if (hasAc) {
    previewAc = acUrl.preview() || ""
  }

  let params = new URLSearchParams(window.location.search)

  let state = "home"
  $: isSignup = state === "signup"
  let submitting = false

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
      .catch(e => verifyMsg = t('welcome.verification-failed', { message: e.message }))
    }
    else if (location.pathname === '/reset-password') {
      state = 'reset'
    }
  }
  async function submit(): Promise<any> {
    submitting = true

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
    submitting = true;

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
    submitting = true

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

<main id="welcome-page" class="no-margin">
  <section class="welcome-intro">
    <div class="brand-mark"><img src="/assets/icons/android-chrome-192x192.png" alt="MikuNet" /></div>
    <span class="kicker">MikuNet / ONLINE SERVICE</span>
    <h1>让每一次游玩，都有自己的位置。</h1>
    <p>管理卡片、同步成绩，继续你的街机记录。</p>
    {#if hasAc}<div class="ac-hint"><span>{t('welcome.login_link')}</span><strong>{previewAc}</strong></div>{/if}
    <div class="intro-notes"><span><Icon icon="solar:shield-check-bold-duotone" />数据随时可控</span><span><Icon icon="solar:card-bold-duotone" />多卡片管理</span></div>
  </section>

  <section class="auth-surface">
    <div class="auth-heading">
      <span class="eyebrow">{state === "signup" ? "CREATE ACCOUNT" : "WELCOME BACK"}</span>
      <h2>{state === "home" ? "进入 MikuNet" : state === "signup" ? "创建账号" : state === "verify" ? "检查邮箱" : "登录 MikuNet"}</h2>
    </div>

    {#if state === "home"}
      <div class="auth-actions" transition:slide>
        <button class="primary" on:click={() => state = 'login'}><Icon icon="solar:login-2-bold" />{t('welcome.btn-login')}</button>
        <button on:click={() => state = 'signup'}><Icon icon="solar:user-plus-bold" />{t('welcome.btn-signup')}</button>
      </div>
    {:else if state === "login" || state === "signup"}
      <div class="login-form" transition:slide>
        {#if error}<span class="error">{error}</span>{/if}
        <button class="back-button" on:click={() => state = 'home'}><Icon icon="line-md:chevron-small-left" />{t('back')}</button>
        {#if isSignup}<label>{t('username')}<input type="text" placeholder="your-name" bind:value={username}></label>{/if}
        <label>{isSignup ? t('email') : "用户名或邮箱"}<input type={isSignup ? "email" : "text"} placeholder={isSignup ? "you@example.com" : "name@example.com"} bind:value={email}></label>
        <label>{t('password')}<input type="password" placeholder="••••••••" bind:value={password}></label>
        <button class="primary submit-button" on:click={submit}>{#if submitting}<Icon icon="line-md:loading-twotone-loop"/>{:else}{isSignup ? t('welcome.btn-signup') : t('welcome.btn-login')}{/if}</button>
        {#if state === "login" && !submitting}<button class="text-button" on:click={() => state = 'submitreset'}>{t('welcome.btn-reset-password')}</button>{/if}
      </div>
    {:else if state === "submitreset"}
      <div class="login-form" transition:slide>
        {#if error}<span class="error">{error}</span>{/if}
        <button class="back-button" on:click={() => state = 'login'}><Icon icon="line-md:chevron-small-left" />{t('back')}</button>
        <label>{t('email')}<input type="email" placeholder="you@example.com" bind:value={email}></label>
        <button class="primary" on:click={resetPassword}>{#if submitting}<Icon icon="line-md:loading-twotone-loop"/>{:else}{t('welcome.btn-submit-reset-password')}{/if}</button>
      </div>
    {:else if state === "verify"}
      <div class="message-state" transition:slide><Icon icon="solar:letter-opened-bold-duotone" /><span>{verifyMsg}</span>{#if !submitting}<button on:click={() => state = 'home'}>{t('back')}</button>{/if}</div>
    {:else if state === "reset"}
      <div class="login-form" transition:slide>
        {#if error}<span class="error">{error}</span>{/if}
        <label>{t('new-password')}<input type="password" placeholder="••••••••" bind:value={password}></label>
        <button class="primary" on:click={changePassword}>{#if submitting}<Icon icon="line-md:loading-twotone-loop"/>{:else}{t('welcome.btn-submit-new-password')}{/if}</button>
      </div>
    {/if}
  </section>
</main>

<style lang="sass">
  @use "../vars"

  #welcome-page
    display: grid
    grid-template-columns: minmax(0, 1fr) minmax(340px, 430px)
    align-items: center
    gap: clamp(38px, 8vw, 120px)
    width: min(1120px, 100%)
    min-height: 100vh
    margin: 0 auto
    padding: 70px 34px
    box-sizing: border-box

  .welcome-intro
    max-width: 570px

  .brand-mark
    display: grid
    place-items: center
    width: 58px
    height: 58px
    margin-bottom: 24px
    border: 1px solid rgba(255, 255, 255, 0.9)
    border-radius: 17px
    background: rgba(255, 255, 255, 0.72)
    box-shadow: 0 16px 42px rgba(43, 72, 76, 0.12)
    backdrop-filter: blur(18px)

    img
      width: 40px
      height: 40px
      border-radius: 12px

  .kicker, .eyebrow
    color: vars.$c-main
    font-size: 0.72rem
    font-weight: 800
    letter-spacing: 0.14em

  .welcome-intro h1
    max-width: 560px
    margin: 12px 0 16px
    color: vars.$c-text
    font-size: clamp(2.2rem, 6vw, 4.8rem)
    letter-spacing: -0.02em

  .welcome-intro p
    max-width: 420px
    margin: 0
    color: vars.$c-sub
    font-size: 1.06rem

  .ac-hint
    display: inline-flex
    flex-direction: column
    gap: 2px
    margin-top: 28px
    padding: 10px 13px
    border-left: 3px solid vars.$c-main
    border-radius: 0 10px 10px 0
    color: vars.$c-sub
    background: rgba(255, 255, 255, 0.48)

    strong
      color: vars.$c-text
      font-size: 0.9rem

  .intro-notes
    display: flex
    flex-wrap: wrap
    gap: 16px
    margin-top: 34px
    color: vars.$c-sub
    font-size: 0.82rem

    span
      display: inline-flex
      align-items: center
      gap: 6px

      :global(svg)
        color: vars.$c-main

  .auth-surface
    width: 100%
    padding: 28px
    border: 1px solid rgba(255, 255, 255, 0.9)
    border-radius: 22px
    background: rgba(255, 255, 255, 0.70)
    box-shadow: 0 28px 80px rgba(43, 72, 76, 0.15)
    backdrop-filter: blur(22px)
    box-sizing: border-box

  .auth-heading
    margin-bottom: 23px

    h2
      margin: 7px 0 0
      color: vars.$c-text
      font-size: 1.65rem

  .auth-actions, .login-form, .message-state
    display: flex
    flex-direction: column
    gap: 10px

  .auth-actions button, .submit-button
    display: flex
    align-items: center
    justify-content: center
    gap: 8px

  .primary
    border-color: rgba(7, 143, 136, 0.34)
    color: #fff
    background: vars.$c-main
    box-shadow: 0 9px 20px rgba(7, 143, 136, 0.18)

    &:hover
      color: #fff
      background: vars.$c-darker

  .back-button, .text-button
    align-self: flex-start
    padding: 4px 0
    border: 0
    color: vars.$c-sub
    background: transparent
    box-shadow: none

    &:hover
      color: vars.$c-main
      background: transparent
      transform: none

  .login-form label
    display: grid
    gap: 6px
    color: vars.$c-sub
    font-size: 0.8rem

  .message-state
    align-items: center
    text-align: center
    color: vars.$c-sub

    :global(svg)
      color: vars.$c-main
      font-size: 2.4rem

  @media (max-width: vars.$w-mobile)
    #welcome-page
      grid-template-columns: 1fr
      align-content: center
      gap: 32px
      min-height: 100vh
      padding: 42px 18px

    .welcome-intro h1
      font-size: clamp(2rem, 12vw, 3.5rem)

    .auth-surface
      padding: 22px
</style>
