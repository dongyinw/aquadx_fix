<script lang="ts">
  export let desc: string
  export let value: string
  export let placeholder: string
  export let flex: number = 60

  export let disabled: boolean = false

  export let validate: (value: string) => boolean = () => true

  const id = `tr-${Math.random().toString(36).slice(2, 8)}`
  $: invalid = !!value && !validate(value)
</script>

<label class="field" style="flex: {flex}" for={id}>
  <span class="field-label">{desc}</span>
  <input {id} type="text" placeholder={placeholder} bind:value={value} on:change
    class:error={invalid} aria-invalid={invalid} autocomplete="off" {disabled}/>
</label>

<style lang="sass">
  .field
    min-width: 140px
</style>
