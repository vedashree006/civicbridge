import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ConsentProvider } from './consent/ConsentContext';
import ProtectedRoute from './components/ProtectedRoute';
import RoleRoute from './components/RoleRoute';
import Navbar from './components/Navbar';
import SparkleBackground from './components/SparkleBackground';
import PageTransition from './components/PageTransition';
import { CookieBanner, CookieSettings, Footer } from './components/CookieBanner';
import { PageViewTracker, SessionHeartbeat } from './analytics/tracker';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Cases from './pages/Cases';
import CaseDetail from './pages/CaseDetail';
import AdminAnalytics from './pages/AdminAnalytics';
import Privacy from './pages/Privacy';

export default function App() {
  return (
    <ConsentProvider>
      <AuthProvider>
        <BrowserRouter>
          <SparkleBackground />
          <Navbar />
          <SessionHeartbeat />
          <PageViewTracker />
          <main>
            <PageTransition>
              <Routes>
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Register />} />
                <Route path="/privacy" element={<Privacy />} />

                <Route element={<ProtectedRoute />}>
                  <Route path="/dashboard" element={<Dashboard />} />
                  <Route path="/cases" element={<Cases />} />
                  <Route path="/cases/:id" element={<CaseDetail />} />
                  <Route element={<RoleRoute role="ADMIN" />}>
                    <Route path="/admin/analytics" element={<AdminAnalytics />} />
                  </Route>
                </Route>

                <Route path="*" element={<Navigate to="/dashboard" replace />} />
              </Routes>
            </PageTransition>
          </main>
          <Footer />
          <CookieBanner />
          <CookieSettings />
        </BrowserRouter>
      </AuthProvider>
    </ConsentProvider>
  );
}
