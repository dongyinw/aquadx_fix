<script lang="ts">
  import { t } from "../../libs/i18n";
  import { EN_REF } from "../../libs/i18n/en_ref";
  import type { GameName } from "../../libs/scoring";
  import { SETTING } from "../../libs/sdk";
  import Icon from "../ui/Icon.svelte";
  import Spinner from "../ui/Spinner.svelte";
  import { toast, errorMessage } from "../../libs/toast";

  export let getTextFunction: (value: number) => string
  export let defaultValue: number = 0

  export let minValue: number = 0
  export let maxValue: number = 0
  export let type: string = "speed";
  export let game: GameName;

  export let disabled: boolean = false;

  let value = defaultValue;
  let status: "idle" | "saving" | "saved" = "idle"
  let savedTimer: ReturnType<typeof setTimeout>
  const id = `opt-${game}-${type}`

  async function submit() {
    status = "saving"
    try {
      await SETTING.optionSet(game, type, value);
      status = "saved"
      clearTimeout(savedTimer)
      savedTimer = setTimeout(() => status = "idle", 1600)
    } catch (e) {
      status = "idle"
      toast.error(errorMessage(e))
    }
  }

  $: percent = maxValue > minValue ? ((value - minValue) / (maxValue - minValue)) * 100 : 0
</script>

<div class="option">
  <div class="option-head">
    <label for={id}>{t(`settings.options.${game}.${type}` as keyof typeof EN_REF)}</label>
    <span class="value">
      {#if status === "saving"}<Spinner size="0.9em" />{:else if status === "saved"}<Icon icon="solar:check-circle-bold" />{/if}
      <span class="num">{getTextFunction(value)}</span>
    </span>
  </div>
  <input id={id} type="range" min={minValue} max={maxValue} {disabled} style:--pct="{percent}%"
         on:change={submit} bind:value={value}>
</div>

<style lang="sass">
  .option
    display: grid
    gap: 8px
    padding: 14px 16px
    border: 1px solid var(--border)
    border-radius: var(--radius)
    background: var(--surface)

  .option-head
    display: flex
    align-items: center
    justify-content: space-between
    gap: 10px

    label
      font-size: 0.9rem
      font-weight: 600

  .value
    display: inline-flex
    align-items: center
    gap: 6px
    min-width: 3.5rem
    justify-content: center
    padding: 2px 10px
    border-radius: var(--radius-full)
    color: var(--primary-soft-text)
    background: var(--primary-soft)
    font-size: 0.85rem
    font-weight: 700

    :global(svg)
      color: var(--success)

  input[type="range"]
    appearance: none
    height: 6px
    border-radius: var(--radius-full)
    background: linear-gradient(to right, var(--primary) var(--pct), var(--surface-3) var(--pct))

    &::-webkit-slider-thumb
      appearance: none
      width: 20px
      height: 20px
      border: 3px solid var(--surface)
      border-radius: 50%
      background: var(--primary)
      box-shadow: var(--shadow-sm)
      cursor: pointer

    &::-moz-range-thumb
      width: 16px
      height: 16px
      border: 3px solid var(--surface)
      border-radius: 50%
      background: var(--primary)
      box-shadow: var(--shadow-sm)
      cursor: pointer

    &:focus-visible
      box-shadow: var(--focus-ring)
</style>
