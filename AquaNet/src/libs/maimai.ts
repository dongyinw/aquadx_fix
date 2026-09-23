import { AQUA_HOST, getDataHost } from './config'


export async function getMaimai(endpoint: string, params: any) {
  return await fetch(`${AQUA_HOST}/Maimai2Servlet/${endpoint}`, {
    method: 'POST',
    body: JSON.stringify(params)
  }).then(res => res.json())
}

export async function getMaimaiAllMusic(): Promise<{ [key: string]: any }> {
  return fetch(`${getDataHost('mai2')}/d/mai2/00/all-music.json`).then(it => it.json())
}
