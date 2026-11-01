const amounts = new Intl.NumberFormat('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const dates = new Intl.DateTimeFormat('tr-TR', {
  dateStyle: 'short',
  timeStyle: 'medium',
  timeZone: 'Europe/Istanbul',
});

export function formatAmount(amount) {
  return amounts.format(amount);
}

export function formatDate(value) {
  return dates.format(new Date(value));
}
