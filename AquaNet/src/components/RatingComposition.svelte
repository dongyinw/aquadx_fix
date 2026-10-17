<script lang="ts">
  import { slide } from "svelte/transition";
  import RatingCompSong from "./RatingCompSong.svelte";
  import { parseComposition, type GameName } from "../libs/scoring";
  import { type MusicMeta } from "../libs/generalTypes";
  import { t } from "../libs/i18n";
  import Icon from "./ui/Icon.svelte";

  export let title: string;
  export let comp: string | undefined;
  export let allMusics: Record<string, MusicMeta>;
  export let game: GameName;
  export let top: number | undefined = undefined;

  let split = comp?.split(",")?.filter(it => it.split(":")[0] !== '0')
    ?.map(it => parseComposition(it, allMusics, game))

  if (top) split = split?.toSorted((a, b) => b.score - a.score).slice(0, top)

  const PREVIEW = 9
  let mustResize = split && split.length > PREVIEW
  let expanded = false
  let collapsed = false

  $: visible = split ? (mustResize && !expanded ? split.slice(0, PREVIEW) : split) : []
  $: total = split?.reduce((sum, p) => sum + (Number(p.ratingChange) || 0), 0) ?? 0
</script>

{#if split && split.length > 0 && comp}
  <section class="section rating-composition">
    <div class="section-header">
      <button class="heading-toggle btn-ghost" type="button" aria-expanded={!collapsed} on:click={() => collapsed = !collapsed}>
        <h2>{title}</h2>
        <span class="badge">{split.length}</span>
        {#if game === "mai2" && total > 0}<span class="badge primary num">Σ {Number.isInteger(total) ? total : total.toFixed(2)}</span>{/if}
        <Icon class="chevron" icon={collapsed ? "line-md:chevron-small-down" : "line-md:chevron-small-up"} />
      </button>
    </div>
    {#if !collapsed}
      <div class="song-grid" transition:slide={{ duration: 180 }}>
        {#each visible as p}
          <RatingCompSong {p} {game}/>
        {/each}
      </div>
      {#if mustResize}
        <button class="toggle" type="button" aria-expanded={expanded} on:click={() => expanded = !expanded}>
          <Icon icon={expanded ? "line-md:chevron-small-up" : "line-md:chevron-small-down"} />
          {expanded ? t("UserHome.RatingComposition.ShowLess") : `${t("UserHome.RatingComposition.ShowMore")} (${split.length - PREVIEW})`}
        </button>
      {/if}
    {/if}
  </section>
{/if}

<style lang="sass">
  .heading-toggle
    gap: 10px
    min-height: 36px
    margin-left: -8px
    padding: 0 8px
    color: var(--text)

    h2
      margin: 0
      font-size: 1.2rem

    :global(.chevron)
      color: var(--text-3)

  .song-grid
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(270px, 1fr))
    gap: 10px

  .toggle
    align-self: center
</style>
