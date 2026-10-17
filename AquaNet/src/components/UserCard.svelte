<!-- Compact player preview used by the leaderboard hover card. Summaries are cached per player. -->
<script context="module" lang="ts">
  import type { GenericGameSummary } from "../libs/generalTypes";
  const cache = new Map<string, Promise<GenericGameSummary>>()
</script>

<script lang="ts">
  import { GAME } from "../libs/sdk";
  import type { GameName } from "../libs/scoring";
  import { pfp } from "../libs/ui";
  import Spinner from "./ui/Spinner.svelte";

  export let username: string
  export let game: GameName

  $: key = `${game}:${username}`
  $: if (!cache.has(key)) {
    const request = GAME.userSummary(username, game)
    request.catch(() => cache.delete(key))
    cache.set(key, request)
  }
  $: summary = cache.get(key)!
</script>

{#await summary}
  <div class="user-card loading"><Spinner /><span>@{username}</span></div>
{:then data}
  <div class="user-card">
    <img use:pfp={data.aquaUser} alt="" />
    <div class="details">
      <strong class="truncate">{data.aquaUser?.displayName || data.name}</strong>
      <span class="truncate">{data.name} · @{username}</span>
      <span class="truncate">{data.lastVersion ?? ""}</span>
    </div>
  </div>
{:catch e}
  <div class="user-card error">{e?.message ?? e}</div>
{/await}

<style lang="sass">
  .user-card
    display: flex
    align-items: center
    gap: 12px
    min-width: 220px
    max-width: 300px

    &.loading
      color: var(--text-2)

    &.error
      color: var(--danger)
      font-size: 0.85rem

  img
    width: 46px
    height: 46px
    flex: 0 0 46px
    border-radius: 50%
    object-fit: cover
    background: var(--surface-2)

  .details
    display: grid
    min-width: 0

    span
      color: var(--text-2)
      font-size: 0.8rem
</style>
