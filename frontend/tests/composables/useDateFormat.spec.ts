import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useDateFormat } from '@/composables/useDateFormat'

describe('useDateFormat', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-02T12:30:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('formatDate', () => {
    it('should format date as YYYY.MM.DD', () => {
      const { formatDate } = useDateFormat()
      
      expect(formatDate('2026-03-02')).toBe('2026.03.02')
      expect(formatDate('2026-01-15')).toBe('2026.01.15')
      expect(formatDate(new Date('2026-12-25'))).toBe('2026.12.25')
    })

    it('should return "-" for null or undefined', () => {
      const { formatDate } = useDateFormat()
      
      expect(formatDate(null)).toBe('-')
      expect(formatDate(undefined)).toBe('-')
      expect(formatDate('')).toBe('-')
    })
  })

  describe('formatDateDash', () => {
    it('should format date as YYYY-MM-DD', () => {
      const { formatDateDash } = useDateFormat()
      
      expect(formatDateDash('2026-03-02T12:30:00')).toBe('2026-03-02')
      expect(formatDateDash(new Date('2026-01-15'))).toBe('2026-01-15')
    })

    it('should return "-" for null or undefined', () => {
      const { formatDateDash } = useDateFormat()
      
      expect(formatDateDash(null)).toBe('-')
      expect(formatDateDash(undefined)).toBe('-')
    })
  })

  describe('formatDateTime', () => {
    it('should format date as YYYY-MM-DD HH:mm', () => {
      const { formatDateTime } = useDateFormat()
      
      expect(formatDateTime('2026-03-02T12:30:00')).toBe('2026-03-02 12:30')
      expect(formatDateTime(new Date('2026-01-15T08:05:00'))).toBe('2026-01-15 08:05')
    })

    it('should return "-" for null or undefined', () => {
      const { formatDateTime } = useDateFormat()
      
      expect(formatDateTime(null)).toBe('-')
      expect(formatDateTime(undefined)).toBe('-')
    })
  })

  describe('formatMonth', () => {
    it('should format date as YYYY-MM', () => {
      const { formatMonth } = useDateFormat()
      
      expect(formatMonth('2026-03-02')).toBe('2026-03')
      expect(formatMonth(new Date('2026-12-25'))).toBe('2026-12')
    })

    it('should return "-" for null or undefined', () => {
      const { formatMonth } = useDateFormat()
      
      expect(formatMonth(null)).toBe('-')
      expect(formatMonth(undefined)).toBe('-')
    })
  })

  describe('formatTime', () => {
    it('should format date as HH:mm', () => {
      const { formatTime } = useDateFormat()
      
      expect(formatTime('2026-03-02T12:30:00')).toBe('12:30')
      expect(formatTime(new Date('2026-01-15T08:05:00'))).toBe('08:05')
    })

    it('should return "-" for null or undefined', () => {
      const { formatTime } = useDateFormat()
      
      expect(formatTime(null)).toBe('-')
      expect(formatTime(undefined)).toBe('-')
    })
  })

  describe('formatMessageDate', () => {
    it('should return time only for today', () => {
      const { formatMessageDate } = useDateFormat()
      
      expect(formatMessageDate('2026-03-02T08:30:00')).toBe('08:30')
      expect(formatMessageDate('2026-03-02T15:45:00')).toBe('15:45')
    })

    it('should return "昨天 HH:mm" for yesterday', () => {
      const { formatMessageDate } = useDateFormat()
      
      expect(formatMessageDate('2026-03-01T10:00:00')).toBe('昨天 10:00')
    })

    it('should return weekday for dates within 7 days', () => {
      const { formatMessageDate } = useDateFormat()
      
      // 2026-03-02 is Monday, so 2026-02-28 is Saturday
      expect(formatMessageDate('2026-02-28T10:00:00')).toBe('周六 10:00')
      expect(formatMessageDate('2026-02-27T10:00:00')).toBe('周五 10:00')
    })

    it('should return MM-DD HH:mm for dates in same year but older than 7 days', () => {
      const { formatMessageDate } = useDateFormat()
      
      expect(formatMessageDate('2026-02-15T10:00:00')).toBe('02-15 10:00')
      expect(formatMessageDate('2026-01-01T00:00:00')).toBe('01-01 00:00')
    })

    it('should return YYYY-MM-DD HH:mm for dates in different year', () => {
      const { formatMessageDate } = useDateFormat()
      
      expect(formatMessageDate('2025-12-25T10:00:00')).toBe('2025-12-25 10:00')
      expect(formatMessageDate('2024-01-01T00:00:00')).toBe('2024-01-01 00:00')
    })

    it('should return "-" for null or undefined', () => {
      const { formatMessageDate } = useDateFormat()
      
      expect(formatMessageDate(null)).toBe('-')
      expect(formatMessageDate(undefined)).toBe('-')
    })
  })

  describe('formatRelativeTime', () => {
    it('should return "刚刚" for less than 1 minute', () => {
      const { formatRelativeTime } = useDateFormat()
      
      expect(formatRelativeTime('2026-03-02T12:29:30')).toBe('刚刚')
      expect(formatRelativeTime('2026-03-02T12:29:59')).toBe('刚刚')
    })

    it('should return "X分钟前" for minutes', () => {
      const { formatRelativeTime } = useDateFormat()
      
      expect(formatRelativeTime('2026-03-02T12:25:00')).toBe('5分钟前')
      expect(formatRelativeTime('2026-03-02T12:00:00')).toBe('30分钟前')
    })

    it('should return "X小时前" for hours', () => {
      const { formatRelativeTime } = useDateFormat()
      
      expect(formatRelativeTime('2026-03-02T10:30:00')).toBe('2小时前')
      expect(formatRelativeTime('2026-03-02T06:30:00')).toBe('6小时前')
    })

    it('should return "X天前" for days within 7 days', () => {
      const { formatRelativeTime } = useDateFormat()
      
      expect(formatRelativeTime('2026-03-01T12:30:00')).toBe('1天前')
      expect(formatRelativeTime('2026-02-28T12:30:00')).toBe('2天前')
    })

    it('should return MM-DD for dates older than 7 days in same year', () => {
      const { formatRelativeTime } = useDateFormat()
      
      expect(formatRelativeTime('2026-02-15T12:30:00')).toBe('02-15')
    })

    it('should return YYYY-MM-DD for dates in different year', () => {
      const { formatRelativeTime } = useDateFormat()
      
      expect(formatRelativeTime('2025-12-25T12:30:00')).toBe('2025-12-25')
    })

    it('should return "-" for null or undefined', () => {
      const { formatRelativeTime } = useDateFormat()
      
      expect(formatRelativeTime(null)).toBe('-')
      expect(formatRelativeTime(undefined)).toBe('-')
    })
  })
})
