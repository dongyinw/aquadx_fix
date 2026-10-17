<script lang="ts">
  import Icon from "../components/ui/Icon.svelte";
  import { t } from "../libs/i18n";
  import { USER } from "../libs/sdk";

  const pathname = new URL(location.href).pathname
</script>

<main class="page not-found">
  <span class="code" aria-hidden="true">404</span>
  <h1>{t("404.title")}</h1>
  <p class="muted">{t("404.desc", { pathname })}</p>
  <div class="row">
    <button type="button" on:click={() => history.back()}><Icon icon="line-md:chevron-small-left" />{t("action.back")}</button>
    <a class="btn btn-primary" href={USER.isLoggedIn() ? "/home" : "/"}><Icon icon="solar:home-2-bold-duotone" />{t("action.home")}</a>
  </div>
</main>

<style lang="sass">
  .not-found
    align-items: center
    justify-content: center
    min-height: 60dvh
    text-align: center

    h1
      margin: 0

    p
      max-width: 460px
      margin: 0
      overflow-wrap: anywhere

    .row
      justify-content: center
      margin-top: 8px

  .code
    font-family: var(--font-brand)
    font-size: clamp(4rem, 14vw, 7rem)
    font-weight: 700
    line-height: 1
    background: linear-gradient(90deg, #12a69c, #39c5bb, #e4518a)
    -webkit-background-clip: text
    background-clip: text
    color: transparent
</style>
