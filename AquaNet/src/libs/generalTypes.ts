export type Dict = Record<string, any>

export interface TrendEntry {
  date: string
  rating: number
  plays: number
}

export interface Card {
  luid: string
  registerTime: string
  accessTime: string
  linked: boolean
  isGhost: boolean
}

export interface MagicalPassEntry {
  passTypeId: number
  passPackId: number
  passCharaId: number
  mapId: number
  startDate: string
  endDate: string
}

export interface MagicalPassTicket {
  itemKind: number
  itemId: number
  stock: number
  isValid: boolean
}

export interface MagicalPassState {
  cardId: string
  hasProfile: boolean
  userPassList: MagicalPassEntry[]
  userTicketLimitDateList: { itemId: number, limitDate: string, lastUsedDate: string }[]
  ticket: MagicalPassTicket | null
}

export interface MikuNetUser {
  username: string
  email: string
  displayName: string
  country: string
  region:string
  lastLogin: number
  regTime: number
  profileLocation: string
  profileBio: string
  profilePicture: string
  emailConfirmed: boolean
  ghostCard: Card
  cards: Card[]
  computedName: string,
  password: string,
  optOutOfLeaderboard: boolean,
  canModifyKeychips: boolean,
  isAdmin: boolean,
  hideCountry: boolean,
  displayCandidates: boolean
}

export interface AdminUserSummary {
  auId: number
  username: string
  displayName: string
  email: string
  lastLogin: number
  keychipCount: number
  isAdmin: boolean
  regTime?: number
}

export interface AdminKeychip {
  id: number
  keychipId: string
  enabled: boolean
}

export interface AdminUserDetail {
  user: {
    auId: number
    username: string
    email: string
    displayName: string
    computedName: string
    country: string
    region: string
    profileLocation: string | null
    profileBio: string | null
    emailConfirmed: boolean
    canModifyKeychips: boolean
    isAdmin: boolean
    lastLogin: number
    regTime: number
  }
  cards: AdminCardSummary[]
  keychips: AdminKeychip[]
  mai2: {
    cardExtId: number
    userName: string
    banState: number
    iconId: number
    plateId: number
    titleId: number
    partnerId: number
    frameId: number
    playerRating: number
    highestRating: number
    playCount?: number
    firstPlayDate?: string
    lastPlayDate?: string
    lastClientId?: string
    lastPlaceId?: number
    lastPlaceName?: string
    lastRomVersion: string
    lastDataVersion: string
  } | null
  chu3: AdminGameProfile | null
  ongeki: AdminGameProfile | null
  items: { itemKind: number, itemId: number, stock: number, isValid: boolean }[]
}

export interface AdminGameProfile {
  cardExtId?: number
  userName: string
  banState?: number
  playerRating: number
  highestRating: number
  playCount?: number
  firstPlayDate?: string
  lastPlayDate?: string
  lastClientId?: string
  lastPlaceId?: number
  lastPlaceName?: string
  lastRomVersion?: string
  lastDataVersion?: string
}

export interface AdminCardGame {
  cardExtId?: number
  userName: string
  banState: number
  iconId: number
  plateId: number
  titleId: number
  partnerId: number
  frameId: number
  playerRating: number
  highestRating: number
  playCount?: number
  firstPlayDate?: string
  lastPlayDate?: string
  lastClientId?: string
  lastPlaceId?: number
  lastPlaceName?: string
  lastRomVersion?: string
  lastDataVersion?: string
}

export interface AdminCardSummary {
  id: number
  luid: string
  extId: number
  registerTime?: string
  accessTime?: string
  isGhost: boolean
  rankingBanned?: boolean
  status: string
  owner?: AdminUserSummary | null
  mai2?: AdminCardGame | null
  chu3?: AdminGameProfile | null
  ongeki?: AdminGameProfile | null
}

export interface AdminPlaylog {
  id?: number
  playlogId?: number
  placeId: number
  placeName: string
  loginDate?: number
  playDate: string
  userPlayDate: string
  musicId: number
  level: number
  achievement: number
  afterRating?: number
}

export interface AdminCardDetail {
  card: AdminCardSummary
  user: AdminUserSummary | null
  keychips: AdminKeychip[]
  mai2: AdminCardGame | null
  chu3: AdminGameProfile | null
  ongeki: AdminGameProfile | null
  items: { itemKind: number, itemId: number, stock: number, isValid: boolean }[]
  playlogs: AdminPlaylog[]
}

export interface CardSummaryGame {
  name: string
  rating: number
  lastLogin: string
}

export interface CardSummary {
  mai2: CardSummaryGame | null
  chu3: CardSummaryGame | null
  ongeki: CardSummaryGame | null
  diva: CardSummaryGame | null
  wacca: CardSummaryGame | null
}


export interface ConfirmProps {
  title: string
  message: string
  confirm?: () => void
  cancel?: () => void
  dangerous?: boolean
}

export interface GenericGamePlaylog {
  id?: number
  musicId: number
  level: number
  playDate: string
  userPlayDate?: string
  achievement: number
  maxCombo: number
  totalCombo: number
  deluxscore?: number
  totalDxScore?: number
  afterRating: number
  beforeRating: number
  isFullCombo?: boolean
  isAllPerfect?: boolean
  isAllJustice?: boolean
  tapCriticalPerfect?: number
  tapPerfect?: number
  tapGreat?: number
  tapGood?: number
  tapMiss?: number
  holdCriticalPerfect?: number
  holdPerfect?: number
  holdGreat?: number
  holdGood?: number
  holdMiss?: number
  slideCriticalPerfect?: number
  slidePerfect?: number
  slideGreat?: number
  slideGood?: number
  slideMiss?: number
  touchCriticalPerfect?: number
  touchPerfect?: number
  touchGreat?: number
  touchGood?: number
  touchMiss?: number
  breakCriticalPerfect?: number
  breakPerfect?: number
  breakGreat?: number
  breakGood?: number
  breakMiss?: number
  fastCount?: number
  lateCount?: number
  isFreedomMode?: boolean
  isNewFree?: boolean
}

export interface GenericRanking {
  name: string
  username: string
  rank: number
  accuracy: number
  rating: number
  fullCombo: number
  allPerfect: number
  lastSeen: string
}

export interface RankCount {
  name: string
  count: number
}

export interface GenericGameSummary {
  name: string
  iconId: number
  aquaUser?: MikuNetUser
  serverRank: string
  accuracy: number
  rating: number
  ratingHighest: number,
  ratingNotGeneric: boolean,
  ranks: RankCount[]
  detailedRanks: { [key: number]: { [key: string]: number } }
  maxCombo: number
  fullCombo: number
  allPerfect: number
  totalScore: number
  plays: number
  totalPlayTime: number
  joined: string
  lastSeen: string
  lastVersion: string
  ratingComposition: { [key: string]: any }
  recent: GenericGamePlaylog[]
  rival?: boolean,
  favorites?: number[]
}

export interface MusicMeta {
  name: string,
  composer: string,
  bpm: number,
  ver: number,
  notes: {
    lv: number
    designer: string
    lv_id: number
    notes: number
  }[],
  worldsEndTag?: string
  worldsEndStars?: number
}

export type AllMusic = { [key: string]: MusicMeta }

export interface GameOption {
  key: string
  value: any
  type: 'Boolean' | 'String'
  game: string

  changed?: boolean
}

export type GameUserOption = Record<string, number>

export interface UserItem { itemKind: number, itemId: number, stock: number }
export interface UserBox {
  userName: string,
  nameplateId: number,
  frameId: number,
  characterId: number,
  trophyId: number,
  trophyIdSub1: number,
  trophyIdSub2: number,
  mapIconId: number,
  voiceId: number,
  avatarWear: number,
  avatarHead: number,
  avatarFace: number,
  avatarSkin: number,
  avatarItem: number,
  avatarFront: number,
  avatarBack: number,

  level: number
  playerRating: number
}

export interface ChusanMatchingOption {
  name: string
  ui: string
  guide: string
  matching: string
  reflector: string
  coop: string[]
}
