<!-- Svelte 4.2.11 -->

<script lang="ts">
  import { slide } from "svelte/transition";
  import { USER } from "../../libs/sdk";
  import type { MikuNetUser } from "../../libs/generalTypes";
  import { codeToHtml } from 'shiki'
  import { AQUA_CONNECTION, FADE_IN, FADE_OUT } from "../../libs/config";
  import { t } from "../../libs/i18n";
  import DashboardTabs from "../../components/DashboardTabs.svelte";
  import { patchUserSegatools } from "../../libs/setup";
  import Icon from "../../components/ui/Icon.svelte";
  import PageHeader from "../../components/ui/PageHeader.svelte";
  import Spinner from "../../components/ui/Spinner.svelte";
  import { confirmDialog } from "../../libs/confirm";
  import { toast, errorMessage } from "../../libs/toast";

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

  let keychipText = "";

  async function buildKeychipCode() {
    exposeKeychip = false;
    const keychipLines = buildManualKeychipLines();
    keychipText = `
[dns]
default=${AQUA_CONNECTION}

[keychip]
enable=1
${keychipLines}`.trim();
    keychipCode = await codeToHtml(keychipText, {
      lang: 'ini',
      themes: { light: 'github-light', dark: 'github-dark' },
      transformers: []
    });
  }

  async function copyConfig() {
    try {
      await navigator.clipboard.writeText(keychipText);
      toast.success("配置已复制到剪贴板");
    } catch {
      toast.error("复制失败，请先显示配置后手动选择复制");
    }
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
    if (!await confirmDialog({ title: t('setup.keychip.delete'), message: `删除后使用 ${formatKeychipDisplay(k)} 的游戏端将无法连接，确定继续吗？`, danger: true, confirmText: t('setup.keychip.delete') })) return;
    try {
      await USER.deleteKeychip(k);
    } catch (e) {
      toast.error(errorMessage(e));
      return;
    }
    toast.success("Keychip 已删除");
    keychips = keychips.filter(id => id !== k);
    if (selectedKeychip === k) {
      selectedKeychip = keychips[0] ?? "";
      await buildKeychipCode();
    }
  }

  async function patchSegatools() {
    automaticSetupStatus = await patchUserSegatools({ keychip: formatKeychipDisplay(selectedKeychip), dns: AQUA_CONNECTION }) ? "success" : "failure";
  }

  // Step progress for the side rail
  $: step = !selectedKeychip ? 1 : automaticSetupStatus === "success" ? 3 : 2;
</script>

<main class="page setup-page">
  <PageHeader eyebrow="Setup" title={t('home.setup')} description="为街机端准备连接信息和 Keychip，敏感内容默认保持隐藏。" />
  <DashboardTabs />

  {#if isLoading}
    <div class="setup-layout" aria-hidden="true">
      <span class="skeleton" style="height:320px;border-radius:16px"></span>
      <div class="stack"><span class="skeleton" style="height:200px;border-radius:16px"></span><span class="skeleton" style="height:260px;border-radius:16px"></span></div>
    </div>
  {:else}
    <div class="setup-layout">
      <aside class="card step-rail">
        <h2>三步完成连接</h2>
        <ol>
          <li class:done={step > 1} class:current={step === 1}><span class="step-dot">{#if step > 1}<Icon icon="solar:check-circle-bold" />{:else}1{/if}</span><div><strong>准备 Keychip</strong><small>选择用于连接的凭证</small></div></li>
          <li class:done={step > 2} class:current={step === 2}><span class="step-dot">{#if step > 2}<Icon icon="solar:check-circle-bold" />{:else}2{/if}</span><div><strong>写入客户端</strong><small>自动或手动配置 segatools</small></div></li>
          <li class:current={step === 3}><span class="step-dot">3</span><div><strong>启动并检查</strong><small>确认 DNS 与版本信息</small></div></li>
        </ol>
        <div class="alert alert-info"><Icon icon="solar:shield-check-bold-duotone" /><p>{t('setup.keychip-warning')}</p></div>
      </aside>

      <div class="setup-content">
        {#if user.canModifyKeychips}
          <section class="card">
            <div class="card-header"><div><span class="eyebrow">Step 1</span><h2>{t('setup.keychip')}</h2><p>{t('setup.keychip.warning')}</p></div></div>
            <div class="keychip-list" role="radiogroup" aria-label={t('setup.keychip')}>
              {#each keychips as k (k)}
                <div class="keychip-item" class:selected={k === selectedKeychip} transition:slide={{ duration: 160 }}>
                  <button class="keychip-select" type="button" role="radio" aria-checked={k === selectedKeychip} on:click={() => selectKeychip(k)}>
                    <span class="radio" aria-hidden="true"></span>
                    <code class="mono">{formatKeychipDisplay(k)}</code>
                    {#if k === selectedKeychip}<span class="badge success">当前使用</span>{/if}
                  </button>
                  <button class="btn-ghost btn-icon btn-sm delete" type="button" title={t('setup.keychip.delete')} aria-label={t('setup.keychip.delete')} on:click={() => deleteKeychip(k)}>
                    <Icon icon="solar:trash-bin-trash-bold-duotone" />
                  </button>
                </div>
              {:else}
                <div class="empty-inline"><Icon icon="solar:key-minimalistic-square-3-bold-duotone" />还没有 Keychip，请在下方添加。</div>
              {/each}
            </div>
            <form class="add-keychip" on:submit|preventDefault={addKeychip}>
              <label class="field">
                <span class="field-label">添加 Keychip</span>
                <div class="input-group">
                  <input type="text" class="mono" placeholder={t('setup.keychip.placeholder')} maxlength="16" autocomplete="off" autocapitalize="characters"
                         bind:value={newKeychip} aria-invalid={!!addKeychipError} on:input={() => addKeychipError = ""} />
                  <button class="btn-primary" type="submit" disabled={isAdding || !newKeychip.trim()}>
                    {#if isAdding}<Spinner />{:else}<Icon icon="solar:add-circle-bold" />{/if}{t('setup.keychip.add')}
                  </button>
                </div>
              </label>
              {#if addKeychipError}<p class="field-error" role="alert">{addKeychipError}</p>{/if}
            </form>
          </section>
        {:else}
          <section class="card permission-card">
            <span class="tile-icon tone-gold"><Icon icon="solar:lock-keyhole-minimalistic-bold-duotone" /></span>
            <div><strong>连接设置由管理员托管</strong><p>当前账号没有 Keychip 管理权限。如需新增或切换凭证，请联系管理员。</p></div>
          </section>
        {/if}

        <section class="card">
          <div class="card-header"><div><span class="eyebrow">Step 2</span><h2>写入游戏端配置</h2><p class="rich">{@html t('setup.steps.one')}</p></div></div>
          {#if selectedKeychip}
            <div class="stack">
              {#if !!window.showOpenFilePicker}
                <details class="disclosure">
                  <summary><span><Icon icon="solar:magic-stick-3-bold-duotone" />{t('setup.type.automatic')}</span><Icon class="chevron" icon="line-md:chevron-small-down" /></summary>
                  <div class="disclosure-body rich">
                    {@html t('setup.automatic')}
                    {#if automaticSetupStatus != "none"}<div class="alert" class:alert-success={automaticSetupStatus === "success"} class:alert-danger={automaticSetupStatus === "failure"}><Icon icon={automaticSetupStatus === "success" ? "solar:check-circle-bold-duotone" : "solar:danger-triangle-bold-duotone"} /><p>{t(`setup.automatic.${automaticSetupStatus}`)}</p></div>{/if}
                    <button class="btn-primary" type="button" on:click={patchSegatools}><Icon icon="solar:folder-with-files-bold-duotone" />{t('setup.automatic.select')}</button>
                  </div>
                </details>
              {/if}
              <details class="disclosure" open>
                <summary><span><Icon icon="solar:file-terminal-bold-duotone" />{t('setup.type.manual')}</span><Icon class="chevron" icon="line-md:chevron-small-down" /></summary>
                <div class="disclosure-body rich">
                  {@html t('setup.manual')}
                  <div class="code-block">
                    <div class="code-toolbar">
                      <span class="mono">segatools.ini</span>
                      <div class="row">
                        <button class="btn-ghost btn-sm" type="button" on:click={() => exposeKeychip = !exposeKeychip}>
                          <Icon icon={exposeKeychip ? "solar:eye-closed-bold-duotone" : "solar:eye-bold-duotone"} />{exposeKeychip ? "隐藏" : t('setup.reveal-keychip')}
                        </button>
                        <button class="btn-ghost btn-sm" type="button" on:click={copyConfig}><Icon icon="solar:copy-bold-duotone" />{t('action.copy')}</button>
                      </div>
                    </div>
                    <div class="code" class:revealed={exposeKeychip}>{@html keychipCode}</div>
                  </div>
                </div>
              </details>
            </div>
          {:else}
            <div class="empty-inline"><Icon icon="solar:key-minimalistic-square-3-bold-duotone" /><span><strong>先添加或选择一个 Keychip</strong> · 选择凭证后，这里会生成对应的连接配置。</span></div>
          {/if}
        </section>

        <section class="card">
          <div class="card-header"><div><span class="eyebrow">Step 3</span><h2>启动前检查</h2><p>完成以下检查后再启动游戏端。</p></div></div>
          <ol class="checklist rich">
            <li>{@html t('setup.steps.two')}</li>
            <li>{@html t('setup.steps.three')}</li>
          </ol>
          <details class="disclosure troubleshooting">
            <summary><span><Icon icon="solar:info-circle-bold-duotone" />{t('setup.troubleshooting.header')}</span><Icon class="chevron" icon="line-md:chevron-small-down" /></summary>
            <ul class="disclosure-body rich">
              <li>{@html t('setup.troubleshooting.items.one')}</li>
              <li>{@html t('setup.troubleshooting.items.two')}</li>
              <li>{@html t('setup.troubleshooting.items.three')}</li>
            </ul>
          </details>
        </section>
      </div>
    </div>
  {/if}
</main>

<style lang="sass">
  @use "../../vars"

  .setup-layout
    display: grid
    grid-template-columns: minmax(240px, 300px) minmax(0, 1fr)
    align-items: start
    gap: 16px

  .step-rail
    position: sticky
    top: calc(var(--nav-height) + 16px)
    gap: 16px

    h2
      margin: 0
      font-size: 1.05rem

    ol
      display: grid
      gap: 4px
      margin: 0
      padding: 0
      list-style: none

    li
      display: flex
      align-items: flex-start
      gap: 12px
      padding: 10px 0

      div
        display: grid
        gap: 1px

      strong
        font-size: 0.9rem

      small
        color: var(--text-2)
        font-size: 0.8rem

  .step-dot
    display: grid
    place-items: center
    width: 28px
    height: 28px
    flex: 0 0 28px
    border-radius: 50%
    color: var(--text-2)
    background: var(--surface-2)
    font-size: 0.8rem
    font-weight: 700

    .current &
      color: var(--on-primary)
      background: var(--primary)

    .done &
      color: var(--success)
      background: var(--success-soft)
      font-size: 1.05rem

  .setup-content
    display: grid
    gap: 16px
    min-width: 0

  .rich :global(code)
    padding: 1px 5px
    border-radius: 4px
    background: var(--surface-2)
    font-size: 0.85em

  .keychip-list
    display: grid
    gap: 6px
    margin-bottom: 16px

  .keychip-item
    display: flex
    align-items: center
    gap: 4px
    padding: 4px 6px 4px 4px
    border: 1px solid var(--border)
    border-radius: var(--radius)
    transition: vars.$transition

    &.selected
      border-color: var(--primary)
      background: var(--primary-soft)

  .keychip-select
    flex: 1
    justify-content: flex-start
    gap: 12px
    min-width: 0
    min-height: 42px
    border: 0
    background: transparent
    box-shadow: none

    &:hover:not(:disabled)
      border: 0
      background: transparent

    code
      font-size: 0.95rem
      letter-spacing: 0.04em

  .radio
    width: 18px
    height: 18px
    flex: 0 0 18px
    border: 2px solid var(--border-strong)
    border-radius: 50%

    .selected &
      border: 5px solid var(--primary)

  .delete:hover:not(:disabled)
    color: var(--danger)
    background: var(--danger-soft)

  .empty-inline
    display: flex
    align-items: center
    gap: 10px
    padding: 14px
    border: 1px dashed var(--border-strong)
    border-radius: var(--radius)
    color: var(--text-2)
    font-size: 0.88rem

    :global(svg)
      flex: 0 0 auto
      color: var(--primary)
      font-size: 1.4rem

  .add-keychip
    display: grid
    gap: 6px

  .permission-card
    flex-direction: row
    align-items: center
    gap: 14px

    p
      margin: 2px 0 0
      color: var(--text-2)
      font-size: 0.88rem

  .disclosure
    border: 1px solid var(--border)
    border-radius: var(--radius)
    background: var(--surface)

    summary
      display: flex
      align-items: center
      justify-content: space-between
      gap: 8px
      min-height: 48px
      padding: 0 14px
      cursor: pointer
      list-style: none
      font-weight: 600

      &::-webkit-details-marker
        display: none

      span
        display: inline-flex
        align-items: center
        gap: 8px

        :global(svg)
          color: var(--primary)

      :global(.chevron)
        color: var(--text-3)
        transition: transform 0.2s ease

    &[open] summary :global(.chevron)
      transform: rotate(180deg)

  .disclosure-body
    display: grid
    gap: 12px
    margin: 0
    padding: 0 14px 14px
    color: var(--text-2)
    font-size: 0.9rem

    :global(p)
      margin: 0

    > button
      justify-self: start

  ul.disclosure-body
    padding-left: 34px

  .code-block
    overflow: hidden
    border: 1px solid var(--border)
    border-radius: var(--radius)
    background: var(--surface-2)

  .code-toolbar
    display: flex
    align-items: center
    justify-content: space-between
    gap: 8px
    padding: 4px 6px 4px 12px
    border-bottom: 1px solid var(--border)
    color: var(--text-3)
    font-size: 0.8rem

    .row
      gap: 2px

  .code
    padding: 12px 14px
    overflow-x: auto
    filter: blur(5px)
    user-select: none
    transition: filter 0.2s ease

    &.revealed
      filter: none
      user-select: text

    :global(pre.shiki)
      margin: 0
      background-color: transparent !important
      font-size: 0.88rem

  :global(:root[data-theme="dark"] .shiki), :global(:root[data-theme="dark"] .shiki span)
    color: var(--shiki-dark) !important

  .checklist
    display: grid
    gap: 10px
    margin: 0 0 14px
    padding-left: 22px
    color: var(--text-2)
    font-size: 0.9rem

    li::marker
      color: var(--primary)
      font-weight: 700

  @media (max-width: vars.$w-tablet)
    .setup-layout
      grid-template-columns: 1fr

    .step-rail
      position: static
</style>
