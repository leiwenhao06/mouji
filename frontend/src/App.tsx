import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import Login from './pages/Login'
import Layout from './components/Layout'
import { TOKEN_KEY } from './api/client'

// 路由级懒加载：登录页保持同步加载，其余页面按需分包
const Patients = lazy(() => import('./pages/Patients'))
const PatientDetail = lazy(() => import('./pages/PatientDetail'))
const ImportReport = lazy(() => import('./pages/ImportReport'))
const FollowupPlans = lazy(() => import('./pages/FollowupPlans'))
const Suggestions = lazy(() => import('./pages/Suggestions'))
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Users = lazy(() => import('./pages/Users'))
const AuditLogs = lazy(() => import('./pages/AuditLogs'))

function RequireAuth({ children }: { children: JSX.Element }) {
  const token = localStorage.getItem(TOKEN_KEY)
  return token ? children : <Navigate to="/login" replace />
}

function PageLoading() {
  return (
    <div className="route-loading">
      <span className="route-loading-dot" />
      加载中…
    </div>
  )
}

export default function App() {
  return (
    <Suspense fallback={<PageLoading />}>
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
          <Route path="audit" element={<AuditLogs />} />
        </Route>
        <Route path="*" element={<Navigate to="/patients" replace />} />
      </Routes>
    </Suspense>
  )
}
