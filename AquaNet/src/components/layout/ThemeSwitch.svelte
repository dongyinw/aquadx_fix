<script lang="ts">
  import Icon from "../ui/Icon.svelte"
  import { setThemePreference, themePreference, type ThemePreference } from "../../libs/theme"
  import { t } from "../../libs/i18n"

  const options: { value: ThemePreference, label: string, icon: string }[] = [
    { value: "light", label: t("theme.light"), icon: "solar:sun-2-bold-duotone" },
    { value: "dark", label: t("theme.dark"), icon: "solar:moon-stars-bold-duotone" },
    { value: "system", label: t("theme.system"), icon: "solar:monitor-bold-duotone" },
  ]
</script>

<div class="segmented theme-switch" role="radiogroup" aria-label={t("theme.toggle")}>
  {#each options as option}
    <button type="button" role="radio" aria-checked={$themePreference === option.value}
            class:active={$themePreference === option.value} on:click={() => setThemePreference(option.value)}>
      <Icon icon={option.icon} />{option.label}
    </button>
  {/each}
</div>

<style lang="sass">
  .theme-switch
    display: flex
    width: 100%

    > button
      flex: 1
      padding: 0 8px
      font-size: 0.8rem
</style>
