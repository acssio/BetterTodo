// =============================================================================
// Mode mobile : quand l'afficher, et la place prise par le clavier
// (testé dans mobileLayout.test.ts)
// =============================================================================

export const MOBILE = {
    maxWidth: 600,           // Fenêtre plus étroite → mode mobile
    landscapeMaxHeight: 500, // Écran tactile moins haut → téléphone tenu à l'horizontale
}
// Les mêmes valeurs sont dans la media query de index.css (champs en 16 px) : les changer ensemble.

/**
 * Mode mobile (écrans Accueil → Liste, overlay en panneau du bas) ?
 *   - fenêtre de moins de 600 px de large (téléphone en portrait) ;
 *   - ou écran tactile de moins de 500 px de haut (téléphone en paysage).
 * Une tablette ou un ordinateur gardent la barre latérale.
 */
export function isMobileLayout(width: number, height: number, coarsePointer: boolean): boolean {
    if (width < MOBILE.maxWidth) return true
    return coarsePointer && height < MOBILE.landscapeMaxHeight
}

/** Mode mobile pour la fenêtre actuelle du navigateur. */
export function detectMobileLayout(): boolean {
    const coarse = window.matchMedia('(pointer: coarse)').matches // Doigt plutôt que souris
    return isMobileLayout(window.innerWidth, window.innerHeight, coarse)
}

/**
 * Hauteur cachée en bas de l'écran par le clavier du téléphone.
 * Le clavier ne rétrécit pas la fenêtre (innerHeight), seulement la partie visible
 * (visualViewport) : la différence, c'est le clavier.
 */
export function keyboardInset(windowHeight: number, visibleHeight: number, visibleTop: number): number {
    return Math.max(0, Math.round(windowHeight - visibleHeight - visibleTop))
}
