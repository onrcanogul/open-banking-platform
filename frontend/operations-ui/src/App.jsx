import { Navigate, Route, Routes } from 'react-router-dom';
import CallbackPage from './auth/CallbackPage.jsx';
import RequireAuth from './auth/RequireAuth.jsx';
import Shell from './layout/Shell.jsx';
import DisbursementsPage from './pages/DisbursementsPage.jsx';
import FindingsPage from './pages/FindingsPage.jsx';

export default function App() {
  return (
    <Routes>
      <Route path="/callback" element={<CallbackPage />} />
      <Route element={<RequireAuth><Shell /></RequireAuth>}>
        <Route path="/disbursements" element={<DisbursementsPage />} />
        <Route path="/findings" element={<FindingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/disbursements" replace />} />
    </Routes>
  );
}
