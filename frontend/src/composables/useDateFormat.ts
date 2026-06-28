import dayjs from 'dayjs'

export function useDateFormat() {
  const formatDate = (date: string | Date | null | undefined): string => {
    if (!date) return '-'
    return dayjs(date).format('YYYY.MM.DD')
  }

  const formatDateDash = (date: string | Date | null | undefined): string => {
    if (!date) return '-'
    return dayjs(date).format('YYYY-MM-DD')
  }

  const formatDateTime = (date: string | Date | null | undefined): string => {
    if (!date) return '-'
    return dayjs(date).format('YYYY-MM-DD HH:mm')
  }

  const formatMonth = (date: string | Date | null | undefined): string => {
    if (!date) return '-'
    return dayjs(date).format('YYYY-MM')
  }

  const formatTime = (date: string | Date | null | undefined): string => {
    if (!date) return '-'
    return dayjs(date).format('HH:mm')
  }

  const formatMessageDate = (date: string | Date | null | undefined): string => {
    if (!date) return '-'
    const d = dayjs(date)
    const now = dayjs()
    const diffDays = now.diff(d, 'day')
    
    if (diffDays === 0) {
      return d.format('HH:mm')
    } else if (diffDays === 1) {
      return '昨天 ' + d.format('HH:mm')
    } else if (diffDays < 7) {
      const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
      return weekdays[d.day()] + ' ' + d.format('HH:mm')
    } else if (d.year() === now.year()) {
      return d.format('MM-DD HH:mm')
    } else {
      return d.format('YYYY-MM-DD HH:mm')
    }
  }

  const formatRelativeTime = (date: string | Date | null | undefined): string => {
    if (!date) return '-'
    const d = dayjs(date)
    const now = dayjs()
    const diffMinutes = now.diff(d, 'minute')
    const diffHours = now.diff(d, 'hour')
    const diffDays = now.diff(d, 'day')

    if (diffMinutes < 1) {
      return '刚刚'
    } else if (diffMinutes < 60) {
      return `${diffMinutes}分钟前`
    } else if (diffHours < 24) {
      return `${diffHours}小时前`
    } else if (diffDays < 7) {
      return `${diffDays}天前`
    } else if (d.year() === now.year()) {
      return d.format('MM-DD')
    } else {
      return d.format('YYYY-MM-DD')
    }
  }

  return {
    formatDate,
    formatDateDash,
    formatDateTime,
    formatMonth,
    formatTime,
    formatMessageDate,
    formatRelativeTime
  }
}
