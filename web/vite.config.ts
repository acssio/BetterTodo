import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
    plugins: [react()],
    server: {
        // En développement (npm run dev), les appels à /api partent vers le serveur Go.
        // En production, c'est nginx qui fait ce travail (voir nginx.conf).
        proxy: {
            '/api': 'http://localhost:8080',
        },
    },
})
