<!-- Shown above the name field while name changes need an admin's approval. -->
<script lang="ts">
  import { onMount } from "svelte"
  import Icon from "../ui/Icon.svelte"
  import { FEATURE_FLAGS } from "../../libs/features"
  import { NAME_REQUESTS, refreshNameRequests } from "../../libs/nameReview"

  export let game: string

  onMount(() => { refreshNameRequests() })

  $: request = $NAME_REQUESTS.find(r => r.game === game)
</script>

{#if $FEATURE_FLAGS.nameReview}
  <div class="alert" role="status">
    <Icon icon="solar:info-circle-bold-duotone" />
    <p>修改名字需要管理员审核，通过后才会生效。</p>
  </div>
  {#if request?.status === "PENDING"}
    <div class="alert alert-info" role="status">
      <Icon icon="solar:clock-circle-bold-duotone" />
      <p>名字「{request.newName}」正在审核中。</p>
    </div>
  {:else if request?.status === "REJECTED"}
    <div class="alert alert-danger" role="alert">
      <Icon icon="solar:close-circle-bold-duotone" />
      <p>名字「{request.newName}」未通过审核{request.rejectReason ? `：${request.rejectReason}` : ""}。</p>
    </div>
  {/if}
{/if}
