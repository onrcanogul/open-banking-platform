export const STATUS = {
  ALINDI: { text: 'Alındı', tone: 'neutral' },
  ONAYLANDI: { text: 'Onaylandı', tone: 'neutral' },
  LIMIT_AYRILDI: { text: 'Limit ayrıldı', tone: 'neutral' },
  MUHASEBELESTI: { text: 'Muhasebeleşti', tone: 'neutral' },
  TAMAMLANDI: { text: 'Tamamlandı', tone: 'positive' },
  TELAFI_EDILIYOR: { text: 'Telafi ediliyor', tone: 'caution' },
  BELIRSIZ: { text: 'Belirsiz', tone: 'caution' },
  BASARISIZ: { text: 'Başarısız', tone: 'negative' },
};

export const FINDING_TYPE = {
  PAID_BUT_FAILED: 'Başarısız bildirildi, ödeme yapılmış',
  COMPLETED_BUT_UNPAID: 'Tamamlandı bildirildi, ödeme yok',
  STALE_UNCERTAIN: 'Uzun süredir belirsiz',
};
