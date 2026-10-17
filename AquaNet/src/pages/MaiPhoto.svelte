<script lang="ts">
  import {GAME} from "../libs/sdk";
  import {AQUA_HOST} from "../libs/config";
  import Error from "../components/ui/Error.svelte";
  import PageHeader from "../components/ui/PageHeader.svelte";
  import EmptyState from "../components/ui/EmptyState.svelte";
  import Dialog from "../components/ui/Dialog.svelte";
  import Icon from "../components/ui/Icon.svelte";
  import { t } from "../libs/i18n";

  const photoUrl = (photo: string) => `${AQUA_HOST}/api/v2/game/mai2/my-photo/${photo}`
  const request = GAME.photos()
  let photos: string[] = []
  request.then(p => photos = p).catch(() => {})

  let viewing: number | null = null

  function step(delta: number) {
    if (viewing === null || photos.length === 0) return
    viewing = (viewing + delta + photos.length) % photos.length
  }

  function onKeydown(e: KeyboardEvent) {
    if (viewing === null) return
    if (e.key === "ArrowRight") step(1)
    if (e.key === "ArrowLeft") step(-1)
  }
</script>

<svelte:window on:keydown={onKeydown} />

<main class="page">
  <PageHeader eyebrow="maimai DX" title={t("maiphoto.title")}>
    {#if photos.length}<span class="badge">{photos.length} 张</span>{/if}
  </PageHeader>

  {#await request}
    <div class="gallery" aria-hidden="true">
      {#each Array(6) as _}<span class="skeleton" style="aspect-ratio:4/3;border-radius:14px"></span>{/each}
    </div>
  {:then}
    {#if photos.length === 0}
      <EmptyState icon="solar:gallery-bold-duotone" title={t('maiphoto.none')} />
    {:else}
      <div class="gallery">
        {#each photos as photo, i}
          <button class="photo" type="button" on:click={() => viewing = i} aria-label="查看第 {i + 1} 张照片">
            <img src={photoUrl(photo)} alt="Mai Photo {i + 1}" loading="lazy" />
          </button>
        {/each}
      </div>
    {/if}
  {:catch error}
    <Error {error}/>
  {/await}
</main>

{#if viewing !== null}
  <Dialog title="{viewing + 1} / {photos.length}" size="lg" on:close={() => viewing = null}>
    <div class="viewer">
      <img src={photoUrl(photos[viewing])} alt="Mai Photo {viewing + 1}" />
    </div>
    <svelte:fragment slot="footer">
      {#if photos.length > 1}
        <button type="button" on:click={() => step(-1)}><Icon icon="solar:alt-arrow-left-bold" />上一张</button>
        <button type="button" on:click={() => step(1)}>下一张<Icon icon="solar:alt-arrow-right-bold" /></button>
      {/if}
      <a class="btn btn-primary" href={photoUrl(photos[viewing])} target="_blank" rel="noopener"><Icon icon="solar:download-minimalistic-bold-duotone" />打开原图</a>
    </svelte:fragment>
  </Dialog>
{/if}

<style lang="sass">
  @use "../vars"

  .gallery
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr))
    gap: 14px

    @media (max-width: vars.$w-mobile)
      grid-template-columns: 1fr 1fr
      gap: 8px

  .photo
    display: block
    height: auto
    padding: 0
    overflow: hidden
    border-radius: var(--radius-lg)
    background: var(--surface-2)

    img
      display: block
      width: 100%
      aspect-ratio: 4 / 3
      object-fit: cover
      transition: transform 0.3s var(--ease)

    &:hover:not(:disabled)
      box-shadow: var(--shadow-md)

      img
        transform: scale(1.03)

  .viewer
    display: flex
    justify-content: center

    img
      max-height: 70dvh
      border-radius: var(--radius)
      object-fit: contain
</style>
