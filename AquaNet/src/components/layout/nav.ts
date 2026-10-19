import type { MikuNetUser } from "../../libs/generalTypes"
import { t } from "../../libs/i18n"
import { inSection } from "../../libs/router"

export interface NavItem {
  href: string
  label: string
  icon: string
  /** Path prefixes that mark this item as the current section. */
  match: string[]
}

export function primaryNav(me: MikuNetUser | undefined, playedMai: boolean, recentGame: string): NavItem[] {
  const items: (NavItem | false)[] = [
    { href: "/home", label: t("navigation.home"), icon: "solar:home-2-bold-duotone", match: ["/home", "/cards", "/setup"] },
    { href: `/ranking/${recentGame}`, label: t("navigation.rankings"), icon: "solar:ranking-bold-duotone", match: ["/ranking"] },
    playedMai && { href: "/friends", label: t("navigation.friends"), icon: "solar:users-group-two-rounded-bold-duotone", match: ["/friends"] },
    playedMai && { href: "/circle", label: t("navigation.circle"), icon: "solar:users-group-rounded-bold-duotone", match: ["/circle"] },
    !!me && { href: "/pass", label: t("navigation.pass"), icon: "solar:ticket-bold-duotone", match: ["/pass"] },
    playedMai && { href: "/pictures", label: t("navigation.photos"), icon: "solar:gallery-bold-duotone", match: ["/pictures"] },
    !!me?.isAdmin && { href: "/admin", label: t("navigation.admin"), icon: "solar:shield-user-bold-duotone", match: ["/admin"] },
  ]
  return items.filter(Boolean) as NavItem[]
}

export function accountNav(me: MikuNetUser): NavItem[] {
  const items: (NavItem | false)[] = [
    { href: `/u/${me.username}`, label: t("navigation.profile"), icon: "solar:user-circle-bold-duotone", match: [`/u/${me.username}`] },
    { href: "/cards", label: t("navigation.cards"), icon: "solar:card-bold-duotone", match: ["/cards"] },
    // Connection setup manages keychips, so it is only offered to users allowed to modify them.
    me.canModifyKeychips && { href: "/setup", label: t("navigation.setup"), icon: "solar:plug-circle-bold-duotone", match: ["/setup"] },
    { href: "/settings", label: t("navigation.settings"), icon: "solar:settings-bold-duotone", match: ["/settings"] },
  ]
  return items.filter(Boolean) as NavItem[]
}

export const isActive = (path: string, item: NavItem) => item.match.some(prefix => inSection(path, prefix))
