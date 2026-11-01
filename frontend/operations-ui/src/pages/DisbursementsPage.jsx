import { usePagedList } from '../api/usePagedList.js';
import ListPanel from '../components/ListPanel.jsx';
import { formatAmount, formatDate } from '../format.js';
import { STATUS } from '../labels.js';

export default function DisbursementsPage() {
  const list = usePagedList('/operations/disbursements');

  return (
    <ListPanel path="Kredi Kullandırım" title="Kullandırımlar" list={list} emptyText="Kayıt bulunmuyor.">
      <table>
        <thead>
          <tr>
            <th>Kullandırım No</th>
            <th>Partner</th>
            <th className="number">Tutar</th>
            <th>Döviz</th>
            <th>Durum</th>
            <th>Oluşturma</th>
            <th>Son İşlem</th>
            <th>Açıklama</th>
          </tr>
        </thead>
        <tbody>
          {list.rows.map((row) => (
            <tr key={row.disbursementNo}>
              <td className="code">{row.disbursementNo}</td>
              <td>{row.partnerCode}</td>
              <td className="number">{formatAmount(row.amount)}</td>
              <td>{row.currency}</td>
              <td><span className={`status status-${STATUS[row.status].tone}`}>{STATUS[row.status].text}</span></td>
              <td className="date">{formatDate(row.createdAt)}</td>
              <td className="date">{formatDate(row.updatedAt)}</td>
              <td className="reason" title={row.failureReason ?? undefined}>{row.failureReason}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </ListPanel>
  );
}
