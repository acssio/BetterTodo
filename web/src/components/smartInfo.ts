import { CalendarDays, Sun } from 'lucide-react'

/** Titre, icône et couleur de chaque liste intelligente. */
export const SMART_INFO = {
    'smart:today': { title: "Aujourd'hui", icon: Sun, color: '#E8A33D', empty: "Rien d'urgent : aucune tâche pour aujourd'hui." },
    'smart:scheduled': { title: 'Planifié', icon: CalendarDays, color: '#2F6FEB', empty: 'Aucune tâche avec une échéance.' },
}
