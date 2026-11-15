// =============================================================================
// Dates d'échéance : l'API envoie "AAAA-MM-JJ", sans heure ni fuseau.
// On les lit comme des dates LOCALES, sinon minuit UTC peut tomber la veille.
// =============================================================================

const MS_PER_DAY = 24 * 60 * 60 * 1000

/** Date du jour au format de l'API ("2026-11-15"), dans le fuseau horaire LOCAL. */
export function todayIso(today: Date = new Date()): string {
    const month = String(today.getMonth() + 1).padStart(2, '0')
    const day = String(today.getDate()).padStart(2, '0')
    return `${today.getFullYear()}-${month}-${day}`
}

/** "2026-11-18" → Date au 18 novembre 2026, 00:00 heure locale. */
export function parseDate(iso: string): Date {
    const [year, month, day] = iso.split('-').map(Number)
    return new Date(year, month - 1, day)
}

/** Nombre de jours entre aujourd'hui et la date (0 = aujourd'hui, -1 = hier, 1 = demain). */
export function daysFromToday(iso: string, today: Date = new Date()): number {
    const start = new Date(today.getFullYear(), today.getMonth(), today.getDate())
    return Math.round((parseDate(iso).getTime() - start.getTime()) / MS_PER_DAY)
}

/** Texte court pour une carte : "Aujourd'hui", "Demain", "Hier", "18 nov." ou "18 nov. 2027". */
export function formatDueDate(iso: string, today: Date = new Date()): string {
    const days = daysFromToday(iso, today)
    if (days === 0) return "Aujourd'hui"
    if (days === 1) return 'Demain'
    if (days === -1) return 'Hier'

    const date = parseDate(iso)
    const options: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short' }
    if (date.getFullYear() !== today.getFullYear()) {
        options.year = 'numeric'
    }
    return new Intl.DateTimeFormat('fr-FR', options).format(date)
}

/** Vrai si l'échéance est aujourd'hui ou déjà passée (affichée en rouge). */
export function isDueOrLate(iso: string, today: Date = new Date()): boolean {
    return daysFromToday(iso, today) <= 0
}
