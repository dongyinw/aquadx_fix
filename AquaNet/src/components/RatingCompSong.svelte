<script lang="ts">
  import { t } from "../libs/i18n";
  import { type GameName, type ParsedComposition, roundFloor } from "../libs/scoring";
  import { coverNotFound } from "../libs/ui";
  import { tooltip } from "../libs/ui";
  import useLocalStorage from "../libs/hooks/useLocalStorage.svelte";

  export let game: GameName
  export let p: ParsedComposition
  const rounding = useLocalStorage("rounding", true)
</script>

<div class="song">
  <img src={p.img} alt="" loading="lazy" on:error={coverNotFound} />
  <div class="info">
    <div class="first-line">
      <span class="title truncate" title={p.name ?? ""}>{p.name ?? t("UserHome.UnknownSong")}</span>
      <span class={`lv level-${p.diffId === 10 ? 3 : p.diffId}`}>{p.difficulty ?? '-'}</span>
    </div>
    <div class="second-line">
      <span class="score">
        <span class={`rank-${p.rank[0]} rank-text`}>{p.rank.replace("p", "+")}</span>
        <span class="num" use:tooltip={(p.score / 10000).toFixed(4)}>
          {rounding.value ? roundFloor(p.score, game, 1) : (p.score / 10000).toFixed(4)}%
        </span>
      </span>
      {#if p.ratingChange !== undefined}
        <span class="rating num">{p.ratingChange}</span>
      {/if}
    </div>
  </div>
</div>

<style lang="sass">
  .song
    display: flex
    align-items: center
    gap: 12px
    min-width: 0
    padding: 8px
    border: 1px solid var(--border)
    border-radius: var(--radius)
    background: var(--surface)
    transition: border-color 0.15s ease, box-shadow 0.15s ease

    &:hover
      border-color: var(--border-strong)
      box-shadow: var(--shadow-sm)

    img
      width: 52px
      height: 52px
      flex: 0 0 52px
      border-radius: var(--radius-sm)
      object-fit: cover

  .info
    display: grid
    flex: 1
    gap: 4px
    min-width: 0

  .first-line, .second-line
    display: flex
    align-items: center
    justify-content: space-between
    gap: 8px

  .title
    font-size: 0.9rem
    font-weight: 600

  .score
    display: flex
    align-items: baseline
    gap: 8px
    font-size: 0.88rem

  .rank-text
    min-width: 34px
    font-weight: 700

  .rating
    padding: 1px 8px
    border-radius: var(--radius-full)
    color: var(--primary-soft-text)
    background: var(--primary-soft)
    font-size: 0.8rem
    font-weight: 700
</style>
