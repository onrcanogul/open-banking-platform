import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  // The identity server only redirects to this exact port, so another one must fail loudly
  server: { port: 5173, strictPort: true },
});
