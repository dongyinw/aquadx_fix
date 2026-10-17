<script lang="ts">
  import { t } from "../../libs/i18n";
  import Dialog from "../../components/ui/Dialog.svelte";
  import Spinner from "../../components/ui/Spinner.svelte";
  import StatusOverlays from "../../components/StatusOverlays.svelte";
  import { CARD, GAME, USER } from "../../libs/sdk";
  import Icon from "../../components/ui/Icon.svelte";

  let load = false;
  let error = "";
  let conflict: {
    oldName: string,
    oldRating: number,
    newName: string,
    newRating: number
  } | null;
  let confirmAction: (override: boolean) => void;

  let fileInput: HTMLInputElement;
  const startImport = async (e: Event & { currentTarget: EventTarget & HTMLInputElement; }) => {
    const file = e.currentTarget.files?.[0]

    if (!file) return;
    load = true;

    try {
      const data = JSON.parse(await file.text()) as any;
      const me = await USER.me();

      const maybeUserMusicList = data?.userMusicList || data;
      if (Array.isArray(maybeUserMusicList) && maybeUserMusicList.every(it => Array.isArray(it?.userMusicDetailList))) {
        // Is music list array
        await GAME.importMusicDetail("mai2", maybeUserMusicList.flatMap(it => it.userMusicDetailList));
        location.href = `/u/${me.username}/mai2`;
        return;
      }

      const game = getGameByCode(data.gameId);
      const userGames = await CARD.userGames(me.username);

      const existed = userGames[game];
      if (existed) {
        conflict = {
          oldName: existed.name,
          oldRating: existed.rating,
          newName: data.userData.userName,
          newRating: data.userData.playerRating
        };
        if (!await new Promise<boolean>(resolve => confirmAction = resolve)) {
          return;
        }
        conflict = null;
      }

      await GAME.import(game, data);
      location.href = `/u/${me.username}/${game}`;
    } catch (e: any) {
      error = e.message;
      console.error(e);
    } finally {
      conflict = null;
      load = false;
      // Allow picking the same file again after a failure.
      if (fileInput) fileInput.value = "";
    }
  }

  const getGameByCode = (code: string) => {
    switch (code?.toUpperCase()) {
      case 'SDGA':
      case 'SDEZ':
        return 'mai2';
      case 'SDHD':
        return 'chu3';
      case 'SDDT':
        return 'ongeki';
      default:
        throw new Error(t('home.import.unknown-game'));
    }
  }
</script>

<button class="tile" type="button" on:click={() => fileInput.click()} disabled={load}>
  <span class="tile-icon tone-pink">{#if load}<Spinner />{:else}<Icon icon="bxs:file-import" />{/if}</span>
  <span class="tile-copy"><strong>{t('home.import')}</strong><small>{t('home.import-description')}</small></span>
  <Icon class="arrow" icon="solar:cloud-upload-bold-duotone" />
</button>
<input type="file" accept=".json,application/json" bind:this={fileInput} hidden on:change={startImport}/>

<StatusOverlays {error} loading={load && !conflict}/>

{#if conflict}
  <Dialog title={t('home.import.data-conflict')} persistent>
    <p class="muted conflict-hint">导入的数据会覆盖账号中现有的游戏资料，此操作无法撤销。</p>
    <div class="conflict">
      <div class="side old">
        <span class="badge danger"><Icon icon="ph:trash-duotone"/>{t('home.linkcard.account-card')}</span>
        <dl><dt>{t('home.linkcard.name')}</dt><dd>{conflict.oldName}</dd><dt>{t('home.linkcard.rating')}</dt><dd class="num">{conflict.oldRating}</dd></dl>
      </div>
      <span class="arrow" aria-hidden="true"><Icon icon="icon-park-outline:down"/></span>
      <div class="side new">
        <span class="badge success">{t('home.import.new-data')}</span>
        <dl><dt>{t('home.linkcard.name')}</dt><dd>{conflict.newName}</dd><dt>{t('home.linkcard.rating')}</dt><dd class="num">{conflict.newRating}</dd></dl>
      </div>
    </div>
    <svelte:fragment slot="footer">
      <button type="button" on:click={() => confirmAction(false)}>{t('action.cancel')}</button>
      <button type="button" class="btn-danger-solid" on:click={() => confirmAction(true)}>{t('action.confirm')}</button>
    </svelte:fragment>
  </Dialog>
{/if}

<style lang="sass">
  .conflict-hint
    margin: 0 0 14px
    font-size: 0.9rem

  .conflict
    display: grid
    gap: 6px

  .side
    display: grid
    gap: 10px
    padding: 14px
    border: 1px solid var(--border)
    border-radius: var(--radius)

    &.old
      border-color: color-mix(in srgb, var(--danger) 35%, transparent)
      background: var(--danger-soft)

    &.new
      border-color: color-mix(in srgb, var(--success) 35%, transparent)
      background: var(--success-soft)

    .badge
      justify-self: start

  dl
    display: grid
    grid-template-columns: auto 1fr
    gap: 4px 14px
    margin: 0
    font-size: 0.9rem

  dt
    color: var(--text-2)

  dd
    margin: 0
    font-weight: 600

  .arrow
    display: flex
    justify-content: center
    color: var(--text-3)
    font-size: 1.4rem
</style>
