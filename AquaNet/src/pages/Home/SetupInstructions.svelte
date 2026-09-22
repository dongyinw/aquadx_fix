<!-- Svelte 4.2.11 -->

<script lang="ts">
  import { fade, slide } from "svelte/transition";
  import { USER } from "../../libs/sdk";
  import type { MikuNetUser } from "../../libs/generalTypes";
  import { codeToHtml } from 'shiki'
  import { AQUA_CONNECTION, FADE_IN, FADE_OUT } from "../../libs/config";
  import { t } from "../../libs/i18n";
  import DashboardTabs from "../../components/DashboardTabs.svelte";
  import { patchUserSegatools } from "../../libs/setup";
  import Icon from "@iconify/svelte";

  let user: MikuNetUser
  let keychips: string[] = [];
  let selectedKeychip: string = "";
  let keychipCode: string;

  let exposeKeychip = false;
  let automaticSetupStatus: "none" | "success" | "failure" = "none";
  let isLoading = true;
  let isAdding = false;
  let newKeychip = "";
  let addKeychipError = "";

  function formatKeychipDisplay(k: string): string {
    return `${k.slice(0, 4)}-${k.slice(4)}`;
  }

  function buildManualKeychipLines(): string {
    return `id=${formatKeychipDisplay(selectedKeychip)}`;
  }

  async function buildKeychipCode() {
    exposeKeychip = false;
    const keychipLines = buildManualKeychipLines();
    keychipCode = await codeToHtml(`
[dns]
default=${AQUA_CONNECTION}

[keychip]
enable=1
${keychipLines}`.trim(), {
      lang: 'ini',
      theme: 'rose-pine',
      transformers: []
    });
  }

  async function loadKeychips() {
    isLoading = true;
    keychips = await USER.keychips();
    if (keychips.length > 0) {
      selectedKeychip = keychips[0];
      await buildKeychipCode();
    } else {
      selectedKeychip = "";
      await buildKeychipCode();
    }
    isLoading = false;
  }

  USER.me().then((u) => {
    user = u;
    if (u.canModifyKeychips) loadKeychips();
    else {
      keychips = [];
      selectedKeychip = "";
      isLoading = false;
    }
  });

  async function selectKeychip(k: string) {
    selectedKeychip = k;
    await buildKeychipCode();
  }

  async function addKeychip() {
    const rawKeychipId = newKeychip.trim().toUpperCase();
    const validRawFormat = /^([A-Z\d]{4}-[A-Z\d]{11}|[A-Z\d]{15})$/.test(rawKeychipId);
    if (!validRawFormat) {
      addKeychipError = "Invalid keychip format. Use 15 characters (with optional dash): A12345678901234 or A123-12345678901.";
      return;
    }

    const keychipId = rawKeychipId.replace("-", "");

    addKeychipError = "";
    isAdding = true;
    try {
      const newId = await USER.addKeychip(keychipId);
      keychips = [...keychips, newId];
      selectedKeychip = newId;
      newKeychip = "";
      await buildKeychipCode();
    } catch (error) {
      addKeychipError = error instanceof Error ? error.message : "Failed to add keychip.";
    } finally {
      isAdding = false;
    }
  }

  async function deleteKeychip(k: string) {
    await USER.deleteKeychip(k);
    keychips = keychips.filter(id => id !== k);
    if (selectedKeychip === k) {
      selectedKeychip = keychips[0] ?? "";
      await buildKeychipCode();
    }
  }

  async function patchSegatools() {
    automaticSetupStatus = await patchUserSegatools({ keychip: formatKeychipDisplay(selectedKeychip), dns: AQUA_CONNECTION }) ? "success" : "failure";
  }
</script>

<main class="content setup-page">
  <header class="page-heading">
    <div><span class="eyebrow">CONNECTION / SETUP</span><h1>{t('home.setup')}</h1><p>为街机端准备连接信息和 Keychip，敏感内容默认保持隐藏。</p></div>
    <span class="secure-chip"><span></span>连接配置</span>
  </header>
  <DashboardTabs />

  {#if isLoading}
    <div class="loading-panel"><span class="loader"></span><strong>{t('loading')}</strong></div>
  {:else}
    <div class="setup-layout">
      <aside class="step-rail glass-panel">
        <span class="eyebrow">WORKFLOW</span>
        <h2>三步完成连接</h2>
        <div class="rail-step active"><span>01</span><div><strong>准备 Keychip</strong><small>选择用于连接的凭证</small></div></div>
        <div class="rail-step"><span>02</span><div><strong>写入客户端</strong><small>自动或手动配置 segatools</small></div></div>
        <div class="rail-step"><span>03</span><div><strong>启动并检查</strong><small>确认 DNS 与版本信息</small></div></div>
        <blockquote class="info"><Icon icon="solar:shield-check-bold-duotone" />{t('setup.keychip-warning')}</blockquote>
      </aside>

      <section class="setup-content">
        {#if user.canModifyKeychips}
          <section class="glass-panel setup-card">
            <div class="card-heading"><div><span class="eyebrow">STEP 01</span><h2>{t('setup.keychip')}</h2><p>{t('setup.keychip.warning')}</p></div><Icon class="heading-icon" icon="solar:key-minimalistic-square-3-bold-duotone" /></div>
            <div class="keychip-list">
              {#each keychips as k}
                <div class="keychip-item" class:selected={k === selectedKeychip}><button class="keychip-select" on:click={() => selectKeychip(k)}><span class="keychip-led"></span><code>{formatKeychipDisplay(k)}</code>{#if k === selectedKeychip}<span class="selected-mark">当前使用</span>{/if}</button><button class="icon danger" title={t('setup.keychip.delete')} aria-label={t('setup.keychip.delete')} on:click={() => deleteKeychip(k)}><Icon icon="tabler:trash-x-filled" /></button></div>
              {:else}<div class="empty-keychip"><Icon icon="solar:key-minimalistic-square-3-bold-duotone" />还没有 Keychip</div>{/each}
            </div>
            <form class="add-keychip-form" on:submit|preventDefault={addKeychip}><input type="text" placeholder={t('setup.keychip.placeholder')} maxlength="16" bind:value={newKeychip} required /><button class="primary-action" type="submit" disabled={isAdding}><Icon icon="solar:add-circle-bold" />{isAdding ? t('loading') : t('setup.keychip.add')}</button></form>
            {#if addKeychipError}<p class="danger form-error">{addKeychipError}</p>{/if}
          </section>
        {:else}
          <section class="glass-panel permission-card"><Icon icon="solar:lock-keyhole-minimalistic-bold-duotone" /><div><strong>连接设置由管理员托管</strong><p>当前账号没有 Keychip 管理权限。如需新增或切换凭证，请联系管理员。</p></div></section>
        {/if}

        <section class="glass-panel setup-card">
          <div class="card-heading"><div><span class="eyebrow">STEP 02</span><h2>写入游戏端配置</h2><p>{@html t('setup.steps.one')}</p></div><Icon class="heading-icon" icon="solar:code-square-bold-duotone" /></div>
          {#if selectedKeychip}
            {#if !!window.showOpenFilePicker}
              <details class="setup-option"><summary><span><Icon icon="solar:magic-stick-3-bold-duotone" />{t('setup.type.automatic')}</span><Icon class="chevron" icon="line-md:chevron-small-down" /></summary><div class="option-body">{@html t('setup.automatic')}{#if automaticSetupStatus != "none"}<blockquote class={`keychip-status ${automaticSetupStatus}`}>{t(`setup.automatic.${automaticSetupStatus}`)}</blockquote>{/if}<button class="primary-action" on:click={patchSegatools}><Icon icon="solar:folder-with-files-bold-duotone" />{t('setup.automatic.select')}</button></div></details>
            {/if}
            <details class="setup-option" open><summary><span><Icon icon="solar:terminal-2-bold-duotone" />{t('setup.type.manual')}</span><Icon class="chevron" icon="line-md:chevron-small-down" /></summary><div class="option-body">{@html t('setup.manual')}<div class="code-container"><div class="code" class:revealed={exposeKeychip}>{@html keychipCode}</div>{#if !exposeKeychip}<button class="reveal-btn" on:click={() => exposeKeychip = true}><Icon icon="solar:eye-bold-duotone" />{t('setup.reveal-keychip')}</button>{/if}</div></div></details>
          {:else}<div class="empty-config"><Icon icon="solar:key-minimalistic-square-3-bold-duotone" /><strong>先添加或选择一个 Keychip</strong><span>选择凭证后，这里会生成对应的连接配置。</span></div>{/if}
        </section>

        <section class="glass-panel setup-card checklist-card">
          <div class="card-heading"><div><span class="eyebrow">STEP 03</span><h2>启动前检查</h2><p>完成以下检查后再启动游戏端。</p></div><Icon class="heading-icon" icon="solar:checklist-minimalistic-bold-duotone" /></div>
          <div class="check-row"><span>1</span><div>{@html t('setup.steps.two')}</div></div><div class="check-row"><span>2</span><div>{@html t('setup.steps.three')}</div></div>
          <details class="troubleshooting"><summary>{t('setup.troubleshooting.header')}<Icon icon="line-md:chevron-small-down" /></summary><ul><li>{@html t('setup.troubleshooting.items.one')}</li><li>{@html t('setup.troubleshooting.items.two')}</li><li>{@html t('setup.troubleshooting.items.three')}</li></ul></details>
        </section>
      </section>
    </div>
  {/if}
</main>

<style lang="sass">
  @use "../../vars"
  .page-heading, .card-heading, .rail-step, .check-row
    display: flex
    align-items: center
    gap: 14px

  .page-heading
    justify-content: space-between

    h1
      margin: 6px 0 4px

    p, .card-heading p
      margin: 0
      color: vars.$c-sub
      font-size: 0.86rem

  .eyebrow
    color: vars.$c-main
    font-size: 0.7rem
    font-weight: 800
    letter-spacing: 0.14em

  .secure-chip
    display: inline-flex
    align-items: center
    gap: 7px
    padding: 8px 11px
    border: 1px solid rgba(19, 134, 111, 0.18)
    border-radius: 999px
    color: vars.$c-good
    background: rgba(19, 134, 111, 0.08)
    font-size: 0.76rem

    span
      width: 7px
      height: 7px
      border-radius: 50%
      background: vars.$c-good

  .setup-layout
    display: grid
    grid-template-columns: minmax(220px, 0.35fr) minmax(0, 1fr)
    gap: 16px

  .glass-panel
    padding: 22px
    border: 1px solid rgba(255, 255, 255, 0.86)
    border-radius: 18px
    background: rgba(255, 255, 255, 0.64)
    box-shadow: 0 18px 46px rgba(43, 72, 76, 0.08)
    backdrop-filter: blur(18px)

  .step-rail
    position: sticky
    top: 84px
    align-self: start

    h2
      margin: 6px 0 22px
      font-size: 1.35rem

    blockquote.info
      display: flex
      gap: 8px
      margin-bottom: 0
      font-size: 0.75rem

  .rail-step
    align-items: flex-start
    padding: 13px 0
    border-top: 1px solid rgba(96, 114, 118, 0.12)

    > span
      display: grid
      place-items: center
      width: 30px
      height: 30px
      flex: 0 0 30px
      border-radius: 9px
      color: vars.$c-sub
      background: rgba(96, 114, 118, 0.10)
      font-size: 0.72rem
      font-weight: 800

    div
      display: grid
      gap: 2px

    strong
      color: vars.$c-text
      font-size: 0.82rem

    small
      color: vars.$c-sub
      font-size: 0.7rem

    &.active > span
      color: vars.$c-main
      background: vars.$c-main-soft

  .setup-content
    display: grid
    gap: 16px

  .setup-card
    display: grid
    gap: 18px

  .card-heading
    justify-content: space-between
    align-items: flex-start

    h2
      margin: 5px 0 3px
      font-size: 1.24rem

    p
      max-width: 680px

    .heading-icon
      flex: 0 0 auto
      color: vars.$c-main
      font-size: 2rem

  .keychip-list
    display: grid
    gap: 8px

  .keychip-item
    display: flex
    align-items: center
    gap: 8px
    padding: 5px
    border: 1px solid transparent
    border-radius: 12px

    &.selected
      border-color: rgba(7, 143, 136, 0.32)
      background: rgba(7, 143, 136, 0.07)

  .keychip-select
    display: flex
    align-items: center
    gap: 9px
    min-width: 0
    flex: 1
    padding: 8px
    border: 0
    box-shadow: none
    text-align: left
    background: transparent

    code
      color: vars.$c-text
      font-size: 0.9rem

  .keychip-led
    width: 8px
    height: 8px
    flex: 0 0 8px
    border-radius: 50%
    background: vars.$c-muted

  .selected .keychip-led
    background: vars.$c-good
    box-shadow: 0 0 0 4px rgba(19, 134, 111, 0.12)

  .selected-mark
    margin-left: auto
    color: vars.$c-good
    font-size: 0.7rem

  .icon
    display: inline-flex
    align-items: center
    justify-content: center
    width: 34px
    height: 34px
    padding: 0
    border-radius: 10px

  .add-keychip-form
    display: flex
    gap: 9px

    input
      min-width: 0
      flex: 1

  .primary-action
    display: inline-flex
    align-items: center
    justify-content: center
    gap: 6px
    color: #fff
    background: vars.$c-main

    &:hover
      color: #fff
      background: vars.$c-darker

  .empty-keychip, .empty-config, .permission-card
    display: flex
    align-items: center
    gap: 10px
    padding: 14px
    border-radius: 12px
    color: vars.$c-sub
    background: rgba(96, 114, 118, 0.06)

    :global(svg)
      color: vars.$c-main
      font-size: 1.6rem

  .permission-card
    strong
      color: vars.$c-text

    p
      margin: 3px 0 0
      color: vars.$c-sub
      font-size: 0.78rem

  .form-error
    margin: 0
    font-size: 0.76rem

  .setup-option, .troubleshooting
    overflow: hidden
    border: 1px solid rgba(96, 114, 118, 0.13)
    border-radius: 13px
    background: rgba(255, 255, 255, 0.42)

    summary
      display: flex
      align-items: center
      justify-content: space-between
      gap: 8px
      padding: 13px 15px
      cursor: pointer
      list-style: none
      color: vars.$c-text
      font-weight: 700

      &::-webkit-details-marker
        display: none

      span
        display: inline-flex
        align-items: center
        gap: 8px

      .chevron
        color: vars.$c-muted

  .option-body
    display: grid
    gap: 12px
    padding: 0 15px 15px
    color: vars.$c-sub
    font-size: 0.8rem

  .code-container
    position: relative
    overflow: hidden
    min-height: 120px
    padding: 12px
    border-radius: 12px
    background: rgba(34, 51, 54, 0.07)

    .code
      overflow-x: auto
      filter: blur(4px)
      transition: 250ms filter

      &.revealed
        filter: none

    .reveal-btn
      position: absolute
      top: 50%
      left: 50%
      display: inline-flex
      align-items: center
      gap: 6px
      transform: translate(-50%, -50%)

  :global(pre.shiki)
    background-color: transparent !important

  .checklist-card
    gap: 10px

  .check-row
    align-items: flex-start
    padding: 10px 0
    color: vars.$c-sub
    font-size: 0.82rem

    > span
      display: grid
      place-items: center
      width: 23px
      height: 23px
      flex: 0 0 23px
      border-radius: 50%
      color: vars.$c-main
      background: vars.$c-main-soft
      font-size: 0.7rem
      font-weight: 800

  .troubleshooting
    margin-top: 8px

    ul
      margin: 0
      padding: 0 28px 15px 34px

      li
        margin: 0.65em 0
        color: vars.$c-sub
        font-size: 0.8rem

  .loading-panel
    display: grid
    place-items: center
    gap: 10px
    min-height: 300px
    color: vars.$c-sub

  .loader
    width: 30px
    height: 30px
    border: 3px solid rgba(7, 143, 136, 0.16)
    border-top-color: vars.$c-main
    border-radius: 50%
    animation: spin 0.8s linear infinite

  @keyframes spin
    to
      transform: rotate(360deg)

  @media (max-width: 820px)
    .setup-layout
      grid-template-columns: 1fr

    .step-rail
      position: static

  @media (max-width: vars.$w-mobile)
    .page-heading
      align-items: flex-start
      flex-direction: column

    .glass-panel
      padding: 17px

    .add-keychip-form
      align-items: stretch
      flex-direction: column
</style>

