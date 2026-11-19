import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { App } from './App'
import { theme } from './utils/theme'

// Thème clair / sombre posé avant le premier rendu
theme.start()

// StrictMode (en développement seulement) monte les composants deux fois
// pour révéler certains bugs : c'est normal de voir deux appels à /me dans le réseau.
createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <App />
    </StrictMode>,
)
