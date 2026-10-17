import {
  CategoryScale,
  Chart as ChartJS,
  type ChartOptions,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  TimeScale,
  Title,
  Tooltip,
} from 'chart.js'
import moment from 'moment/moment'
// @ts-expect-error Cal-heatmap does not have proper types
import CalHeatmap from 'cal-heatmap'
// @ts-expect-error Cal-heatmap does not have proper types
import CalTooltip from 'cal-heatmap/plugins/Tooltip'
import { AQUA_HOST, DEFAULT_PFP } from "./config"
import type { MikuNetUser } from "./generalTypes"

export function title(t: string) {
  document.title = `${t} · MikuNet`
}

export function registerChart() {
  ChartJS.register(
    Title,
    Tooltip,
    Legend,
    LineElement,
    LinearScale,
    PointElement,
    CategoryScale,
    TimeScale
  )
}

const dayTemplate = (DateHelper: any) => {
  const ROWS_COUNT = 7
  const ALLOWED_DOMAIN_TYPE = ['month']

  return {
    name: 'ghDayFix',
    allowedDomainType: ALLOWED_DOMAIN_TYPE,
    rowsCount: () => ROWS_COUNT,
    columnsCount: (ts: any) => {
      let count = DateHelper.getWeeksCountInMonth(ts)
      const endOfMonth = moment().endOf('month').toDate()
      const clampEnd = DateHelper.getFirstWeekOfMonth(endOfMonth).toDate()

      if(moment(ts).isSame(new Date(), 'month') && endOfMonth > clampEnd) {
        count++
      }
      return count
    },
    mapping: (startTimestamp: any, endTimestamp: any) => {
      const clampStart = DateHelper.getFirstWeekOfMonth(startTimestamp)
      let clampEnd = DateHelper.getFirstWeekOfMonth(endTimestamp)

      if(moment(startTimestamp).isSame(new Date(), 'month')){
        clampEnd = DateHelper.date().add(1, 'day')
      }

      let x = -1
      const pivotDay = clampStart.weekday()

      return DateHelper.intervals('day', clampStart, clampEnd).map((ts: any) => {
        const weekday = DateHelper.date(ts).weekday()
        if (weekday === pivotDay) {
          x += 1
        }

        return {
          t: ts,
          x,
          y: weekday,
        }
      })
    },
    extractUnit: (ts: any) => DateHelper.date(ts).startOf('day').valueOf(),
  }
}

export function renderCal(el: HTMLElement, d: { date: any, value: any }[], options: { dark?: boolean, tooltip?: (count: number | null, date: string) => string } = {}): Promise<any> {
  const cal = new CalHeatmap()
  cal.addTemplates(dayTemplate)
  return cal.paint({
    itemSelector: el,
    domain: {
      type: 'month',
      label: { text: 'MMM', textAlign: 'start', position: 'top' },
    },
    subDomain: {
      type: 'ghDayFix',
      radius: 2, width: 11, height: 11, gutter: 4
    },
    range: 12,
    data: { source: d.filter(x => x.value > 0), x: 'date', y: 'value' },
    scale: {
      color: {
        type: 'linear',
        range: options.dark ? [ '#123b37', '#3cc5ba' ] : [ '#c6ece8', '#0a8079' ],
        domain: [ 0, d.reduce((a, b) => Math.max(a, b.value), 0) ]
      },
    },
    date: { start: moment().subtract(1, 'year').add(1, 'month').toDate() },
    theme: options.dark ? 'dark' : 'light',
  }, [
    [ CalTooltip, {
      text: (_: Date, v: number, d: any) => options.tooltip
        ? options.tooltip(v ?? null, d.format('YYYY-MM-DD'))
        : `${v ?? 'No'} songs played on ${d.format('MMMM D, YYYY')}`
    } ]
  ])
}


const now = moment()
export const CHARTJS_OPT: ChartOptions<'line'> = {
  responsive: true,
  maintainAspectRatio: false,
  // TODO: Show point on hover
  elements: {
    point: {
      radius: 0
    }
  },
  scales: {
    xAxis: {
      type: 'time',
      display: false
    },
    y: {
      display: false,
    }
  },
  plugins: {
    legend: {
      display: false
    },
    tooltip: {
      mode: 'index',
      intersect: false,
      callbacks: {
        title: (tooltipItems) => {
          const date = tooltipItems[0].parsed.x
          const diff = now.diff(date, 'days')
          return navigator.language.startsWith('zh')
            ? (diff ? `${diff} 天前` : '今天')
            : (diff ? `${diff} days ago` : 'Today')
        }
      }
    }
  },
}

export const pfpNotFound = (e: Event) => (e.target as HTMLImageElement).src = DEFAULT_PFP
export const coverNotFound = (e: Event) => (e.target as HTMLImageElement).src = "/assets/imgs/no_cover.jpg"
export const removeImg = (e: Event) => (e.target as HTMLImageElement).style.display = 'none'


/**
 * use:tooltip — small text bubble shown on hover and keyboard focus.
 */
export function tooltip(element: HTMLElement, params: { text: string, dom: HTMLElement } | string | HTMLElement) {
  let div = document.querySelector<HTMLDivElement>('.aqua-tooltip')
  if (!div) {
    div = document.createElement('div')
    div.classList.add('aqua-tooltip')
    div.setAttribute('role', 'tooltip')
    div.style.display = 'none'
    document.body.appendChild(div)
  }
  const tip = div
  let current = params
  let visible = false

  function fill() {
    if (typeof current === 'string') tip.textContent = current
    else tip.innerHTML = 'dom' in current ? current.dom.outerHTML : current.outerHTML
  }

  function place(x: number, y: number) {
    const margin = 8
    const w = tip.offsetWidth, h = tip.offsetHeight
    let left = Math.min(Math.max(margin, x - w / 2), window.innerWidth - w - margin)
    let top = y + 14
    if (top + h > window.innerHeight - margin) top = y - h - 14
    tip.style.left = `${left}px`
    tip.style.top = `${Math.max(margin, top)}px`
  }

  function show(x: number, y: number) {
    fill()
    tip.style.display = ''
    visible = true
    place(x, y)
  }

  function hide() {
    if (!visible) return
    visible = false
    tip.style.display = 'none'
  }

  const onEnter = (e: PointerEvent) => { if (e.pointerType === 'mouse') show(e.clientX, e.clientY) }
  const onMove = (e: PointerEvent) => { if (visible) place(e.clientX, e.clientY) }
  const onFocus = () => {
    if (!element.matches(':focus-visible')) return
    const r = element.getBoundingClientRect()
    show(r.left + r.width / 2, r.bottom - 6)
  }

  element.addEventListener('pointerenter', onEnter)
  element.addEventListener('pointermove', onMove)
  element.addEventListener('pointerleave', hide)
  element.addEventListener('focus', onFocus)
  element.addEventListener('blur', hide)
  window.addEventListener('scroll', hide, { passive: true })

  return {
    update(next: typeof params) {
      current = next
      if (visible) fill()
    },
    destroy() {
      hide()
      element.removeEventListener('pointerenter', onEnter)
      element.removeEventListener('pointermove', onMove)
      element.removeEventListener('pointerleave', hide)
      element.removeEventListener('focus', onFocus)
      element.removeEventListener('blur', hide)
      window.removeEventListener('scroll', hide)
    }
  }
}

export const pfpUrl = (me?: MikuNetUser) =>
  me?.profilePicture ? `${AQUA_HOST}/uploads/net/portrait/${me.profilePicture}` : DEFAULT_PFP

export function pfp(node: HTMLImageElement, me?: MikuNetUser) {
  node.src = pfpUrl(me)
  node.onerror = e => pfpNotFound(e as Event)
  return {
    update(next?: MikuNetUser) {
      const src = pfpUrl(next)
      if (node.getAttribute('src') !== src) node.src = src
    }
  }
}

export function download(data: string, filename: string) {
  const blob = new Blob([data]);
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
}

export async function selectJsonFile(): Promise<any> {
  return new Promise((resolve, reject) => {
    // Create a hidden file input element
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json,application/json';
    input.style.display = 'none';

    // Listen for when the user selects a file
    input.addEventListener('change', (event: Event) => {
      const target = event.target as HTMLInputElement;
      if (!target.files || target.files.length === 0) {
        return reject(new Error("No file selected"));
      }
      const file = target.files[0];
      const reader = new FileReader();

      reader.onload = () => {
        try {
          const jsonData = JSON.parse(reader.result as string);
          resolve(jsonData);
        } catch (error) {
          reject(new Error("Error parsing JSON: " + error));
        }
      };

      reader.onerror = () => {
        reject(new Error("Error reading file"));
      };

      reader.readAsText(file);
    });

    // Append the input to the DOM, trigger click, and then remove it
    document.body.appendChild(input);
    input.click();
    document.body.removeChild(input);
  });
}
