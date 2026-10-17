<script lang="ts">
  import { fade } from "svelte/transition";
  import type { MikuNetUser } from "../../libs/generalTypes";
  import { USER } from "../../libs/sdk";
  import StatusOverlays from "../../components/StatusOverlays.svelte";
  import Icon from "../ui/Icon.svelte";
  import Dialog from "../ui/Dialog.svelte";
  import Spinner from "../ui/Spinner.svelte";
  import { pfpNotFound, pfpUrl } from "../../libs/ui";
  import { t, ts } from "../../libs/i18n";
  import { toast, errorMessage } from "../../libs/toast";
  import { confirmDialog } from "../../libs/confirm";
  import Cropper from "svelte-easy-crop";

  let me: MikuNetUser;
  let error: string;
  let submitting = ""
  let pfpVersion = 0

  const profileFields = [
    [ 'displayName', t('settings.profile.name') ],
    [ 'username', t('settings.profile.username') ],
    [ 'password', t('settings.profile.password') ],
    [ 'profileBio', t('settings.profile.bio') ],
  ] as const

  // Fetch user data
  const getMe = () => {
    USER.me().then((m) => {
      if (pfpCropURL != null) {
        URL.revokeObjectURL(pfpCropURL);
        pfpField.value = "";
        pfpCropURL = null;
      }; me = m;
    }).catch(e => error = e.message)
  }
  getMe();

  let changed: string[] = []
  let pfpField: HTMLInputElement
  let pfpCropURL: string | null = null;
  let pfpCrop = { width: 0, height: 0, x: 0, y: 0 };

  $: avatarSrc = me ? `${pfpUrl(me)}${me.profilePicture && pfpVersion ? `?v=${pfpVersion}` : ""}` : ""

  function submit(field: string, value: string) {
    if (submitting) return
    submitting = field

    USER.setting(field, value).then(() => {
      changed = changed.filter(c => c !== field)
      if (field === 'password') (me as any).password = ""
      toast.success(t('settings.profile.save') + " ✓")
    }).catch(e => toast.error(errorMessage(e))).finally(() => submitting = "")
  }

  function onUploaded() {
    me.profilePicture = me.username
    pfpVersion = Date.now()
    toast.success(t('settings.profile.picture') + " ✓")
    setTimeout(getMe, 200);
  }

  function uploadPfp() {
    if (submitting) return
    let canvas = document.createElement("canvas");
    let ctx = canvas.getContext("2d");
    const size = Math.round(Math.min(pfpCrop.width, pfpCrop.height, 1024));
    canvas.width = size;
    canvas.height = size;
    let img = document.createElement("img");
    img.onload = () => {
      ctx?.drawImage(img, pfpCrop.x, pfpCrop.y, pfpCrop.width, pfpCrop.height, 0, 0, size, size);
      canvas.toBlob(blob => {
        if (!blob) return;
        submitting = 'profilePicture'
        USER.uploadPfp(blob as File).then(onUploaded)
          .catch(e => toast.error(errorMessage(e))).finally(() => submitting = "")
      });
    }
    img.src = pfpCropURL ?? "";
  }

  function cancelCrop() {
    if (pfpCropURL) URL.revokeObjectURL(pfpCropURL)
    pfpCropURL = null
    pfpField.value = ""
  }

  function handlePfpUpload(e: Event & { currentTarget: HTMLInputElement }) {
    if (!e.target) return;
    let files = e?.currentTarget?.files;
    if (!files || files.length <= 0) return;
    let file = files[0];
    switch (file.type) {
      case "image/gif":
        submitting = 'profilePicture'
        USER.uploadPfp(file).then(onUploaded)
          .catch(e => toast.error(errorMessage(e))).finally(() => { submitting = ""; pfpField.value = "" })
        break;
      case "image/png":
      case "image/jpeg":
      case "image/webp":
        pfpCropURL = URL.createObjectURL(file);
        break;
      default:
        pfpField.value = ""
        toast.error(t("settings.profile.bad-format"));
    }
  };

  async function logOut() {
    if (await confirmDialog({ title: ts(`settings.profile.logout`), message: "确定要退出当前账号吗？", confirmText: ts(`settings.profile.logout`), danger: true }))
      USER.logout();
  }

  function markChanged(field: string) {
    if (!changed.includes(field)) changed = [...changed, field]
  }
</script>

<StatusOverlays {error} />
<input id="profile-upload" type="file" accept="image/gif,image/png,image/jpeg,image/webp" hidden bind:this={pfpField} on:change={handlePfpUpload} />

{#if me}
  <div class="user-settings">
    <div class="avatar-row">
      <button class="avatar-btn" type="button" on:click={() => pfpField.click()} aria-label={t('settings.profile.upload-new')} disabled={!!submitting}>
        <img src={avatarSrc} alt="" on:error={pfpNotFound} />
        <span class="avatar-overlay">{#if submitting === 'profilePicture'}<Spinner />{:else}<Icon icon="solar:camera-bold-duotone" />{/if}</span>
      </button>
      <div class="avatar-copy">
        <strong>{t('settings.profile.picture')}</strong>
        <small>支持 PNG / JPEG / WebP（可裁剪）与 GIF</small>
        <button class="btn-sm" type="button" on:click={() => pfpField.click()} disabled={!!submitting}><Icon icon="solar:upload-minimalistic-bold-duotone" />{t('settings.profile.upload-new')}</button>
      </div>
    </div>

    <div class="setting-group">
      {#each profileFields as [field, name] (field)}
        <form class="setting-row stacked" on:submit|preventDefault={() => submit(field, me[field])}>
          <label class="setting-text" for={field}><span class="name">{name}</span></label>
          <div class="input-group">
            {#if field == "profileBio"}
              <textarea id={field} bind:value={me[field]} on:input={() => markChanged(field)} maxlength=255 rows="3" placeholder={t('settings.profile.unset')}></textarea>
            {:else if field == "password"}
              <input id={field} type="password" autocomplete="new-password" bind:value={me[field]} on:input={() => markChanged(field)} placeholder={t('settings.profile.unchanged')}/>
            {:else}
              <input id={field} type="text" autocomplete={field === "username" ? "username" : "nickname"} bind:value={me[field]} on:input={() => markChanged(field)} placeholder={t('settings.profile.unset')}/>
            {/if}
            {#if changed.includes(field) && me[field]}
              <button class="btn-primary" type="submit" disabled={!!submitting} transition:fade={{ duration: 120 }}>
                {#if submitting === field}<Spinner />{/if}{t('settings.profile.save')}
              </button>
            {/if}
          </div>
          {#if field == "profileBio"}<small class="field-hint counter">{(me[field] ?? "").length} / 255</small>{/if}
        </form>
      {/each}
    </div>

    <div class="setting-section">
      <h3>隐私</h3>
      <div class="setting-group">
        <div class="setting-row">
          <label class="setting-text" for="optOutOfLeaderboard">
            <span class="name">{ts(`settings.fields.optOutOfLeaderboard.name`)}</span>
            <span class="desc">{ts(`settings.fields.optOutOfLeaderboard.desc`)}</span>
          </label>
          <input id="optOutOfLeaderboard" type="checkbox" class="switch" bind:checked={me.optOutOfLeaderboard}
            on:change={() => submit('optOutOfLeaderboard', me.optOutOfLeaderboard.toString())}/>
        </div>
        <div class="setting-row">
          <label class="setting-text" for="hideCountry">
            <span class="name">{ts(`settings.fields.hideCountry.name`)}</span>
            <span class="desc">{ts(`settings.fields.hideCountry.desc`)}</span>
          </label>
          <input id="hideCountry" type="checkbox" class="switch" bind:checked={me.hideCountry}
            on:change={() => submit('hideCountry', me.hideCountry.toString())}/>
        </div>
      </div>
    </div>

    <div class="setting-section">
      <h3>账号</h3>
      <div class="setting-group">
        <div class="setting-row">
          <div class="setting-text"><span class="name">{ts(`settings.profile.logout`)}</span><span class="desc">在此设备上退出当前账号</span></div>
          <button class="btn-danger" type="button" on:click={logOut}><Icon icon="solar:logout-2-bold-duotone" />{ts(`settings.profile.logout`)}</button>
        </div>
      </div>
    </div>
  </div>
{:else if !error}
  <div class="stack" aria-hidden="true">
    <div class="row"><span class="skeleton" style="width:80px;height:80px;border-radius:50%"></span><span class="skeleton" style="width:160px;height:20px"></span></div>
    <span class="skeleton" style="height:260px;border-radius:12px"></span>
  </div>
{/if}

{#if pfpCropURL != null}
  <Dialog title={t('settings.profile.picture')} description="拖动和缩放以调整头像范围" on:close={cancelCrop}>
    <div class="cropper-container">
      <Cropper maxZoom={1e9} oncropcomplete={(e) => pfpCrop = e.pixels} image={pfpCropURL ?? "assets/imgs/no_profile.png"} aspect={1} cropShape="round"></Cropper>
    </div>
    <svelte:fragment slot="footer">
      <button type="button" on:click={cancelCrop}>{t("action.cancel")}</button>
      <button class="btn-primary" type="button" on:click={uploadPfp} disabled={!!submitting}>
        {#if submitting === 'profilePicture'}<Spinner />{/if}{t("settings.profile.save")}
      </button>
    </svelte:fragment>
  </Dialog>
{/if}

<style lang="sass">
  .user-settings
    display: grid
    gap: 20px

  .avatar-row
    display: flex
    align-items: center
    gap: 18px

  .avatar-btn
    position: relative
    width: 84px
    height: 84px
    min-width: 84px
    padding: 0
    overflow: hidden
    border-radius: 50%

    img
      width: 100%
      height: 100%
      object-fit: cover

  .avatar-overlay
    position: absolute
    inset: 0
    display: grid
    place-items: center
    color: #fff
    background: rgba(0, 0, 0, 0.45)
    font-size: 1.5rem
    opacity: 0
    transition: opacity 0.15s ease

  .avatar-btn:hover .avatar-overlay, .avatar-btn:focus-visible .avatar-overlay, .avatar-btn:disabled .avatar-overlay
    opacity: 1

  .avatar-copy
    display: grid
    justify-items: start
    gap: 4px

    small
      color: var(--text-2)
      font-size: 0.82rem

    button
      margin-top: 4px

  .counter
    justify-self: end

  .cropper-container
    position: relative
    width: 100%
    aspect-ratio: 1
    max-height: 60dvh
    overflow: hidden
    border-radius: var(--radius)
    background: #000
</style>
