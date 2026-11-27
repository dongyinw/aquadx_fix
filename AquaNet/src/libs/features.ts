import { writable } from "svelte/store"
import { FEATURES } from "./sdk"

/** Server-wide switches an admin controls. Everything is on until the server says otherwise. */
export interface FeatureFlags {
  rename: boolean
  customAvatar: boolean
  claim: boolean
  export: boolean
  nameReview: boolean
}

export const FEATURE_DEFAULTS: FeatureFlags = { rename: true, customAvatar: true, claim: true, export: true, nameReview: false }

export const FEATURE_FLAGS = writable<FeatureFlags>({ ...FEATURE_DEFAULTS })

// Loaded in the background; the pages work with the defaults until it arrives, and the server refuses a switched-off
// feature anyway.
void FEATURES.list().then(flags => FEATURE_FLAGS.set({ ...FEATURE_DEFAULTS, ...flags })).catch(() => {})
