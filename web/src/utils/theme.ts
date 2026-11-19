// =============================================================================
// Thème clair / sombre : choix Système / Clair / Sombre, retenu par appareil
// (fonctions pures testées dans theme.test.ts)
//
// Le thème est posé sur <html data-theme="light|dark"> ; index.css redéfinit
// les variables de couleur sous :root[data-theme="dark"].
// =============================================================================

export type ThemeChoice = 'system' | 'light' | 'dark'
export type Theme = 'light' | 'dark'

const THEME_KEY = 'bettertodo.theme' // Dans le navigateur : retenu par appareil

/** Lit le choix enregistré. Une valeur abîmée ou absente donne « system ». */
export function parseThemeChoice(raw: string | null): ThemeChoice {
    return raw === 'light' || raw === 'dark' ? raw : 'system'
}

/** Thème à afficher : le choix de l'utilisateur, ou celui de l'ordinateur en mode « system ». */
export function resolveTheme(choice: ThemeChoice, systemDark: boolean): Theme {
    if (choice === 'system') return systemDark ? 'dark' : 'light'
    return choice
}

/**
 * Applique le thème à la page et suit les changements du mode de l'ordinateur
 * (utile en mode « system » : passer le Mac en sombre change la page tout de suite).
 */
class ThemeManager {
    private _choice: ThemeChoice = 'system'
    private _media: MediaQueryList | null = null

    get choice(): ThemeChoice {
        return this._choice
    }

    /** À appeler une fois au démarrage, avant le premier rendu. */
    start(): void {
        // localStorage peut être indisponible (navigation privée, stockage bloqué) : on suit alors le système
        try {
            this._choice = parseThemeChoice(localStorage.getItem(THEME_KEY))
        } catch {
            this._choice = 'system'
        }
        this._media = window.matchMedia('(prefers-color-scheme: dark)')
        this._media.addEventListener('change', this.onSystemChange)
        this.apply()
    }

    setChoice(choice: ThemeChoice): void {
        this._choice = choice
        try {
            localStorage.setItem(THEME_KEY, choice)
        } catch {
            // Pas de stockage : le choix vaut jusqu'au rechargement de la page
        }
        this.apply()
    }

    private onSystemChange = (): void => {
        this.apply()
    }

    private apply(): void {
        const theme = resolveTheme(this._choice, this._media?.matches ?? false)
        document.documentElement.dataset.theme = theme
    }
}

export const theme = new ThemeManager()
