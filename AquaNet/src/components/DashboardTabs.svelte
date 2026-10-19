<script lang="ts">
  import Icon from "./ui/Icon.svelte";
  import { t } from "../libs/i18n";

  /** Whether the user may manage keychips (and therefore see connection setup). */
  export let showSetup = false

  $: tabs = [
    { label: t('home.nav.portal'), href: `/home`, icon: "solar:widget-5-bold-duotone" },
    { label: t('home.nav.link-card'), href: `/cards`, icon: "solar:card-bold-duotone" },
    ...(showSetup ? [{ label: t('home.nav.game-setup'), href: `/setup`, icon: "solar:plug-circle-bold-duotone" }] : []),
  ]
  const current = location.pathname
</script>

<nav class="segmented dashboard-tabs" aria-label={t('home.nav.portal')}>
  {#each tabs as tab}
    <a href={tab.href} class:active={current === tab.href} aria-current={current === tab.href ? "page" : undefined}>
      <Icon icon={tab.icon} />{tab.label}
    </a>
  {/each}
</nav>

<style lang="sass">
  @use "../vars"

  .dashboard-tabs
    align-self: flex-start

    @media (max-width: vars.$w-mobile)
      display: flex
      align-self: stretch

      > a
        flex: 1
        padding: 0 8px
</style>
