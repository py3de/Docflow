import { HashRouter, MemoryRouter, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Overview } from './pages/Overview';
import { Documents } from './pages/Documents';
import { DocumentDetail } from './pages/DocumentDetail';
import { Upload } from './pages/Upload';
import { Evaluation } from './pages/Evaluation';

// The hosted preview runs in a sandboxed frame, so it keeps routes in memory.
const Router = import.meta.env.VITE_MEMORY_ROUTER ? MemoryRouter : HashRouter;

export default function App() {
  return (
    <Router>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Overview />} />
          <Route path="documents" element={<Documents />} />
          <Route path="documents/:id" element={<DocumentDetail />} />
          <Route path="review" element={<Documents reviewOnly />} />
          <Route path="upload" element={<Upload />} />
          <Route path="evaluation" element={<Evaluation />} />
        </Route>
      </Routes>
    </Router>
  );
}
