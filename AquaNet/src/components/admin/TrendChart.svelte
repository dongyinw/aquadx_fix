<!-- Small dependency-free SVG chart: lines (optionally filled) or bars over a shared category axis, with gridlines, hover tooltip and click to select. -->
<script lang="ts">
  import { createEventDispatcher } from "svelte"

  type ChartSeries = { name: string, color: string, values: number[], area?: boolean }

  export let labels: string[] = []
  export let series: ChartSeries[] = []
  export let mode: "line" | "bar" = "line"
  export let height = 220
  /** Index of the highlighted category, or -1. */
  export let selected = -1
  export let ariaLabel = "趋势图"
  /** Shorten an axis label, e.g. "2026-11-12" -> "11-12". */
  export let formatLabel: (label: string) => string = label => label

  const dispatch = createEventDispatcher<{ select: number }>()
  const pad = { left: 46, right: 14, top: 14, bottom: 30 }

  let width = 640
  let hover = -1

  /** Rounds up to 1, 2 or 5 times a power of ten so the gridlines land on round numbers. */
  function niceMax(value: number) {
    const power = Math.pow(10, Math.floor(Math.log10(Math.max(1, value))))
    const unit = value / power
    return (unit <= 1 ? 1 : unit <= 2 ? 2 : unit <= 5 ? 5 : 10) * power
  }

  $: count = labels.length
  $: innerW = Math.max(10, width - pad.left - pad.right)
  $: innerH = height - pad.top - pad.bottom
  $: top = niceMax(Math.max(1, ...series.flatMap(s => s.values)))
  $: ticks = [0, 1, 2, 3, 4].map(i => (top / 4) * i)
  $: x = (i: number) => mode === "bar" || count <= 1 ? pad.left + innerW * (i + 0.5) / Math.max(1, count) : pad.left + innerW * i / (count - 1)
  $: y = (v: number) => pad.top + innerH * (1 - v / top)
  $: band = innerW / Math.max(1, count)
  $: labelStep = Math.max(1, Math.ceil(count / Math.max(1, Math.floor(innerW / 54))))
  $: path = (s: ChartSeries) => s.values.map((v, i) => `${i ? "L" : "M"}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(" ")
  $: areaPath = (s: ChartSeries) => count ? `${path(s)} L${x(count - 1).toFixed(1)},${y(0).toFixed(1)} L${x(0).toFixed(1)},${y(0).toFixed(1)} Z` : ""
  $: barWidth = Math.max(2, Math.min(28, band / (series.length + 0.6)))
  $: tipLeft = hover < 0 ? 0 : Math.min(Math.max(x(hover), 90), width - 90)
</script>

<div class="chart" bind:clientWidth={width} style={`height:${height}px`}>
  {#if count === 0}
    <p class="empty subtle">所选期间暂无数据</p>
  {:else}
    <svg {width} {height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label={ariaLabel}>
      {#each ticks as tick}
        <line class="grid" x1={pad.left} x2={width - pad.right} y1={y(tick)} y2={y(tick)} />
        <text class="axis" x={pad.left - 8} y={y(tick) + 4} text-anchor="end">{Math.round(tick)}</text>
      {/each}
      {#each labels as label, i}
        {#if i % labelStep === 0}<text class="axis" x={x(i)} y={height - 8} text-anchor="middle">{formatLabel(label)}</text>{/if}
      {/each}

      {#if selected >= 0 && selected < count}
        <rect class="selected" x={x(selected) - band / 2} y={pad.top} width={band} height={innerH} />
      {/if}

      {#if mode === "line"}
        {#each series as s}
          {#if s.area}<path class="area" d={areaPath(s)} style={`fill:${s.color}`} />{/if}
          <path class="line" d={path(s)} style={`stroke:${s.color}`} />
          {#if count <= 45}
            {#each s.values as v, i}<circle cx={x(i)} cy={y(v)} r={hover === i ? 4.5 : 2.6} style={`fill:${s.color}`} />{/each}
          {/if}
        {/each}
      {:else}
        {#each series as s, si}
          {#each s.values as v, i}
            <rect class="bar" x={x(i) - (barWidth * series.length) / 2 + si * barWidth} y={y(v)} width={barWidth - 1} height={Math.max(0, y(0) - y(v))} rx="2" style={`fill:${s.color}`} opacity={hover < 0 || hover === i ? 1 : 0.55} />
          {/each}
        {/each}
      {/if}

      {#if hover >= 0 && mode === "line"}<line class="guide" x1={x(hover)} x2={x(hover)} y1={pad.top} y2={pad.top + innerH} />{/if}

      <!-- One invisible hit area per category for hover and click -->
      {#each labels as label, i}
        <!-- Keyboard users pick a day with the date selector next to the chart. -->
        <rect class="hit" role="button" tabindex="-1" aria-label={label} x={x(i) - band / 2} y={pad.top} width={band} height={innerH}
              on:mouseenter={() => hover = i} on:mouseleave={() => hover = -1} on:click={() => dispatch("select", i)}
              on:keydown={event => { if (event.key === "Enter" || event.key === " ") dispatch("select", i) }} />
      {/each}
    </svg>

    {#if hover >= 0}
      <div class="tip" style={`left:${tipLeft}px`}>
        <strong>{labels[hover]}</strong>
        {#each series as s}<span><i style={`background:${s.color}`}></i>{s.name}<b class="num">{s.values[hover]}</b></span>{/each}
      </div>
    {/if}
  {/if}
</div>

<style lang="sass">
  .chart
    position: relative
    width: 100%
    user-select: none

  svg
    display: block
    overflow: visible

  .empty
    display: grid
    place-items: center
    height: 100%
    margin: 0

  .grid
    stroke: var(--border)
    stroke-width: 1

  .axis
    fill: var(--text-3)
    font-size: 11px
    font-variant-numeric: tabular-nums

  .line
    fill: none
    stroke-width: 2.2
    stroke-linejoin: round
    stroke-linecap: round

  .area
    opacity: 0.12

  .guide
    stroke: var(--border-strong)
    stroke-dasharray: 3 3

  .selected
    fill: var(--primary-soft)

  .hit
    fill: transparent
    cursor: pointer

  .tip
    position: absolute
    top: 4px
    z-index: 2
    display: grid
    gap: 3px
    min-width: 150px
    padding: 8px 10px
    transform: translateX(-50%)
    border: 1px solid var(--border)
    border-radius: 10px
    background: var(--surface)
    box-shadow: var(--shadow-sm)
    font-size: 0.78rem
    pointer-events: none

    strong
      color: var(--text)

    span
      display: flex
      align-items: center
      gap: 6px
      color: var(--text-2)

    b
      margin-left: auto
      color: var(--text)

    i
      width: 8px
      height: 8px
      border-radius: 50%
</style>
