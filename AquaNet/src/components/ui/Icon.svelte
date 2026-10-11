<script context="module" lang="ts">
  import OfflineIcon, { addCollection, type IconifyJSON } from "@iconify/svelte/dist/OfflineIcon.svelte";
  import collections from "../../libs/icons/collections.json";
  for (const collection of collections) addCollection(collection as unknown as IconifyJSON);
</script>

<script lang="ts">
  export let icon: string;
</script>

<OfflineIcon {icon} {...$$restProps} />
