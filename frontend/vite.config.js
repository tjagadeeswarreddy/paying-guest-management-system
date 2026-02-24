import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/paying-guest-management-system/',
  server: {
    port: 5173
  }
});
