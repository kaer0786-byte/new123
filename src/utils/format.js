export function fmtTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const p = n => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${d.getDate()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

export function fmtDuration(sec) {
  if (sec == null) return ''
  return sec < 60 ? `${sec}s` : `${Math.floor(sec / 60)}m${sec % 60}s`
}

export const fmtPrice = v => '¥' + Number(v).toFixed(2)
