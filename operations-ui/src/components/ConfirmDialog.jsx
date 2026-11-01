/** Focus starts on cancel, so a stray Enter cannot start the action. */
export default function ConfirmDialog({ title, children, confirmText, busy, onConfirm, onCancel }) {
  return (
    <div className="dialog-backdrop">
      <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title">
        <h2 id="dialog-title">{title}</h2>
        <div className="dialog-body">{children}</div>
        <div className="dialog-actions">
          <button type="button" className="button" onClick={onCancel} disabled={busy} autoFocus>Vazgeç</button>
          <button type="button" className="button button-primary" onClick={onConfirm} disabled={busy}>
            {busy ? 'İşleniyor…' : confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
