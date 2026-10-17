<script lang="ts">
  import { ts } from "../../libs/i18n";
  import InputWithButton from "./InputWithButton.svelte";

  export let field: {key: string, value: any, type: string, changed?: boolean};
  export let callback: () => Promise<boolean>;
</script>

{#if field.type.toLowerCase() === "boolean"}
  <div class="setting-row">
    <label class="setting-text" for={field.key}>
      <span class="name">{ts(`settings.fields.${field.key}.name`)}</span>
      <span class="desc">{ts(`settings.fields.${field.key}.desc`)}</span>
    </label>
    <input id={field.key} type="checkbox" class="switch" bind:checked={field.value} on:change={callback}/>
  </div>
{:else if field.type.toLowerCase() === "string"}
  <div class="setting-row stacked">
    <label class="setting-text" for={field.key}>
      <span class="name">{ts(`settings.fields.${field.key}.name`)}</span>
      <span class="desc">{ts(`settings.fields.${field.key}.desc`)}</span>
    </label>
    <InputWithButton bind:field={field} callback={callback}/>
  </div>
{/if}
