import { Navigate, Route, Routes } from 'react-router-dom'
import Login from './pages/Login'
import Patients from './pages/Patients'
import PatientDetail from './pages/PatientDetail'
import ImportReport from './pages/ImportReport'
import FollowupPlans from './pages/FollowupPlans'
import Suggestions from './pages/Suggestions'
import Dashboard from './pages/Dashboard'
import Users from './pages/Users'
import Layout from './components/Layout'
import { TOKEN_KEY } from './api/client'

function RequireAuth({ children }: { children: JSX.Element }) {
  const token = localStorage.getItem(TOKEN_KEY)
  return token ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/patients" replace />} />
        <Route path="patients" element={<Patients />} />
        <Route path="patients/:patientId" element={<PatientDetail />} />
        <Route path="import" element={<ImportReport />} />
        <Route path="followup" element={<FollowupPlans />} />
        <Route path="suggestions" element={<Suggestions />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="users" element={<Users />} />
      </Route>
      <Route path="*" element={<Navigate to="/patients" replace />} />
    </Routes>
  )
}
