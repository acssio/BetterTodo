// =============================================================================
// Largeur et mode d'affichage de la barre latérale (testé dans sidebarLayout.test.ts)
// =============================================================================

export const SIDEBAR = {
    min: 60,            // Largeur du mode « icônes »
    compactBelow: 150,  // En dessous : on n'affiche plus que les icônes
    max: 420,
    default: 240,
    narrowWindow: 760,  // Fenêtre plus étroite : la barre passe d'office en icônes
}

/**
 * expanded : barre normale (icônes + textes)
 * compact  : icônes seules, parce que l'utilisateur l'a rétrécie
 * narrow   : icônes seules, parce que la fenêtre est trop étroite (la barre peut alors
 *            s'ouvrir par-dessus le contenu, voir Workspace.sidebarPeek)
 */
export type SidebarMode = 'expanded' | 'compact' | 'narrow'

export function sidebarMode(windowWidth: number, sidebarWidth: number): SidebarMode {
    if (windowWidth < SIDEBAR.narrowWindow) return 'narrow'
    return sidebarWidth < SIDEBAR.compactBelow ? 'compact' : 'expanded'
}

/** Garde la largeur entre min et max (pendant qu'on tire la poignée). */
export function clampSidebarWidth(width: number): number {
    return Math.round(Math.max(SIDEBAR.min, Math.min(SIDEBAR.max, width)))
}

/** Au relâchement : une largeur « entre deux » est aimantée sur le mode icônes. */
export function snapSidebarWidth(width: number): number {
    const clamped = clampSidebarWidth(width)
    return clamped < SIDEBAR.compactBelow ? SIDEBAR.min : clamped
}
