<!-- Svelte 4.2.11 -->

<script lang="ts">
  import RatingCompSong from "./RatingCompSong.svelte";
  import { parseComposition, type GameName } from "../libs/scoring";
  import { type MusicMeta } from "../libs/generalTypes";
  import { t } from "../libs/i18n";
  import Icon from "@iconify/svelte";

  export let title: string;
  export let comp: string | undefined;
  export let allMusics: Record<string, MusicMeta>;
  export let game: GameName;
  export let top: number | undefined = undefined;

  let split = comp?.split(",")?.filter(it => it.split(":")[0] !== '0')
    ?.map(it => parseComposition(it, allMusics, game))

  if (top) split = split?.toSorted((a, b) => b.score - a.score).slice(0, top)

  let mustResize = split && split.length > 12
  let expanded = false
</script>

{#if split && split.length > 0 && comp}
  <div class="rating-composition-container" class:must-resize={mustResize}>
    <div class="rating-heading">
      <h2>{title}</h2>
    </div>
    <div class="rating-composition" class:expanded={expanded}>
      {#each split as p}
        <div>
          <RatingCompSong {p} {game}/>
        </div>
      {/each}
    </div>
    {#if mustResize}
      <button
        class="rating-toggle"
        type="button"
        aria-label={expanded ? t("UserHome.RatingComposition.ShowLess") : t("UserHome.RatingComposition.ShowMore")}
        aria-expanded={expanded}
        on:click={() => expanded = !expanded}
      >
        <span>{expanded ? t("UserHome.RatingComposition.ShowLess") : t("UserHome.RatingComposition.ShowMore")}</span>
        <Icon icon={expanded ? "line-md:chevron-small-up" : "line-md:chevron-small-down"} aria-hidden="true" />
      </button>
    {/if}
  </div>
{/if}

<style lang="sass">
  @use "../vars"

  .rating-composition
    display: grid
    // 3 columns
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr))
    gap: vars.$gap

    overflow: hidden
    transition: max-height 250ms

  .rating-composition-container
    position: relative

  .rating-heading
    display: flex
    align-items: center
    gap: 0.2rem

    h2
      margin: 20px 0

  .rating-toggle
    display: flex
    align-items: center
    justify-content: center
    gap: 0.35rem
    min-height: 2.25rem
    margin: vars.$gap auto 0
    padding: 0.35rem 0.9rem
    border: 1px solid rgba(vars.$c-main, 0.45)
    border-radius: vars.$border-radius
    color: vars.$c-main
    background: rgba(vars.$c-main, 0.08)
    cursor: pointer
    transition: background-color 150ms, border-color 150ms

    &:hover, &:focus-visible
      border-color: vars.$c-main
      background: vars.$c-main-soft

    :global(svg)
      font-size: 1.1rem

  .rating-composition-container.must-resize
    .rating-composition
      max-height: 250px

      &.expanded
        max-height: 3000px
        
    
</style>
