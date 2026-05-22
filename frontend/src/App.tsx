import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Chat } from './pages/Chat';
import { KnowledgeBase } from './pages/KnowledgeBase';
import { Studio } from './pages/Studio';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Chat />} />
          <Route path="kb" element={<KnowledgeBase />} />
          <Route path="chat" element={<Chat />} />
          <Route path="chat/:chatId" element={<Chat />} />
          <Route path="studio" element={<Studio />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}