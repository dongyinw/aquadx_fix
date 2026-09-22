<script lang="ts">
    import { t } from "../libs/i18n";

    const tabs: Record<string, string> = {
        [t('home.nav.portal')]: `/home`,
        [t('home.nav.link-card')]: `/cards`,
        [t('home.nav.game-setup')]: `/setup`
    }
</script>

<nav class="tabs">
    {#each Object.entries(tabs) as t, i}
        <a class="clickable tab-button"
            href={t[1]}
            class:active={
                new URL(location.href).pathname === t[1]
            }>
            {t[0]}
        </a>
    {/each}
</nav>

<style lang="sass">
  @use "../vars"
  .tabs
    display: flex
    gap: 6px
    width: max-content
    max-width: 100%
    padding: 4px
    border: 1px solid rgba(96, 114, 118, 0.13)
    border-radius: 12px
    background: rgba(255, 255, 255, 0.42)

    @media (max-width: vars.$w-mobile)
      width: 100%
      overflow-x: auto

    .tab-button
      padding: 8px 12px
      border-radius: 9px
      color: vars.$c-sub
      font-size: 0.78rem
      font-weight: 700
      white-space: nowrap

      &:hover
        color: vars.$c-main
        background: rgba(7, 143, 136, 0.07)

    a
      &.active
        color: vars.$c-main
        background: vars.$c-main-soft
</style>
