// =============================================================================
// Confirmations dessinées par l'application, à la place de window.confirm / alert.
//
// Pourquoi : certains navigateurs intégrés (dont celui de l'application Claude)
// désactivent les boîtes de dialogue natives. window.confirm y répond « non » tout
// de suite, sans rien afficher : les suppressions échouaient en silence.
//
// Utilisation, depuis n'importe quel composant :
//     if (!(await confirmDialog.ask('Supprimer la tâche ?'))) return
//
// L'affichage est fait par <ConfirmHost /> (ConfirmDialog.tsx), placé une seule fois dans App.
// =============================================================================

export interface ConfirmRequest {
    message: string
    confirmLabel: string // Texte du bouton principal (« Supprimer », « OK »…)
    danger: boolean      // Bouton principal en rouge
    cancelable: boolean  // false = simple information, un seul bouton
    resolve: (confirmed: boolean) => void
}

interface ConfirmOptions {
    confirmLabel?: string
    danger?: boolean
}

/** Fait le lien entre les composants qui demandent une confirmation et ConfirmHost qui l'affiche. */
class ConfirmService {
    private show: ((request: ConfirmRequest) => void) | null = null

    /** Appelée par ConfirmHost à son montage (null au démontage). */
    setHost(show: ((request: ConfirmRequest) => void) | null): void {
        this.show = show
    }

    /** Demande une confirmation. Résout true si l'utilisateur confirme. */
    ask(message: string, options: ConfirmOptions = {}): Promise<boolean> {
        return this.open(message, options.confirmLabel ?? 'Supprimer', options.danger ?? true, true)
    }

    /** Simple information, avec un bouton « OK ». */
    async inform(message: string): Promise<void> {
        await this.open(message, 'OK', false, false)
    }

    private open(message: string, confirmLabel: string, danger: boolean, cancelable: boolean): Promise<boolean> {
        return new Promise(resolve => {
            if (this.show === null) {
                resolve(false) // Pas d'hôte monté : on ne supprime rien par prudence
                return
            }
            this.show({ message, confirmLabel, danger, cancelable, resolve })
        })
    }
}

/** Instance unique, utilisée par tous les composants. */
export const confirmDialog = new ConfirmService()
