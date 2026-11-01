import { useState } from 'react';
import { useApi } from '../api/useApi.js';
import { usePagedList } from '../api/usePagedList.js';
import { useAuth } from '../auth/AuthContext.jsx';
import ConfirmDialog from '../components/ConfirmDialog.jsx';
import ListPanel from '../components/ListPanel.jsx';
import { formatDate } from '../format.js';
import { FINDING_TYPE, STATUS } from '../labels.js';

export default function FindingsPage() {
  const list = usePagedList('/operations/findings');
  const request = useApi();
  const { hasRole } = useAuth();
  const [confirming, setConfirming] = useState(false);
  const [running, setRunning] = useState(false);
  const [outcome, setOutcome] = useState(null);

  async function reconcile() {
    setRunning(true);
    try {
      const status = await request('/operations/reconciliation', { method: 'POST' });
      setOutcome(status === 'COMPLETED'
        ? { tone: 'positive', text: 'Mutabakat tamamlandı.' }
        : { tone: 'negative', text: 'Mutabakat tamamlanamadı. Sistem kayıtlarını kontrol edin.' });
      list.reload();
    } catch (failure) {
      setOutcome({ tone: 'negative', text: failure.message });
    } finally {
      setRunning(false);
      setConfirming(false);
    }
  }

  // Hiding the button is a courtesy. The gateway refuses the call for anyone without this role
  const runButton = hasRole('RECONCILER') && (
    <button type="button" className="button button-primary" onClick={() => setConfirming(true)}>
      Mutabakat Başlat
    </button>
  );

  return (
    <>
      {outcome && <div className={`message message-${outcome.tone}`} role="status">{outcome.text}</div>}
      <ListPanel path="Mutabakat" title="Bulgular" list={list} actions={runButton}
        emptyText="İnceleme bekleyen bulgu bulunmuyor.">
        <table>
          <thead>
            <tr>
              <th>Kullandırım No</th>
              <th>Bulgu</th>
              <th>Sistem Durumu</th>
              <th>Çekirdek Transfer</th>
              <th>Bulunma Zamanı</th>
              <th>İzleme No</th>
            </tr>
          </thead>
          <tbody>
            {list.rows.map((row) => (
              <tr key={row.disbursementNo}>
                <td className="code">{row.disbursementNo}</td>
                <td>{FINDING_TYPE[row.type]}</td>
                <td>{STATUS[row.ourStatus].text}</td>
                <td>{row.coreTransfer ? 'Var' : 'Yok'}</td>
                <td className="date">{formatDate(row.foundAt)}</td>
                <td className="code">{row.correlationId}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </ListPanel>
      {confirming && (
        <ConfirmDialog title="Mutabakat başlatılsın mı?" confirmText="Başlat" busy={running}
          onConfirm={reconcile} onCancel={() => setConfirming(false)}>
          Sonuçlanan kullandırımlar çekirdek bankacılık kayıtlarıyla karşılaştırılacak. Bu işlem yalnızca
          rapor üretir, para hareketi yapmaz.
        </ConfirmDialog>
      )}
    </>
  );
}
