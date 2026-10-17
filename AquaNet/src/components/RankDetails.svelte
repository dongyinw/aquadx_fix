<script lang="ts">
  import { slide } from "svelte/transition";
  import { t } from "../libs/i18n";
  import type { GenericGameSummary } from "../libs/generalTypes";

  export let g: GenericGameSummary

  const detail = Object.entries(g.detailedRanks).toSorted((a, b) => +b[0] - +a[0])
</script>

<section class="card card-flush" transition:slide={{ duration: 180 }}>
  <div class="head"><h3>{t("UserHome.RankDetail.Title")}</h3></div>
  <div class="table-wrap">
    <!-- rankDetails: { Level : { Rank : Count } }; rows are levels, columns are ranks -->
    <table class="data-table">
      <thead>
        <tr>
          <th>{t("UserHome.RankDetail.Level")}</th>
          {#each Object.values(g.ranks) as rankMap}<th class="num">{rankMap.name}</th>{/each}
        </tr>
      </thead>
      <tbody>
        {#each detail as [level, rankMap]}
          <tr>
            <td><strong>{level}</strong></td>
            {#each Object.values(rankMap) as count}<td class="num" class:zero={!count}>{count}</td>{/each}
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
</section>

<style lang="sass">
  .head
    padding: 16px 20px 12px

    h3
      margin: 0

  .table-wrap
    max-height: 420px
    border: 0
    border-top: 1px solid var(--border)
    border-radius: 0

  .zero
    color: var(--text-3)
</style>
