import * as React from 'react'
import { ArrowRightLeft, Trash2 } from 'lucide-react'
import './MobileSelection.css'

interface SelectionHeaderProps {
    count: number
    onCancel: () => void    // Vide la sélection
    onSelectAll: () => void // Toutes les tâches affichées de la liste
}

/**
 * Mode mobile, pendant une sélection : remplace l'en-tête de la liste.
 * « Annuler · N sélectionnées · Tout »
 */
export class SelectionHeader extends React.Component<SelectionHeaderProps> {
    render(): React.ReactNode {
        const { count } = this.props
        return (
            <header className="SelectionHeader">
                <button onClick={this.props.onCancel}>Annuler</button>
                <span className="selhead-count">{count > 1 ? `${count} sélectionnées` : '1 sélectionnée'}</span>
                <button className="selhead-all" onClick={this.props.onSelectAll}>Tout</button>
            </header>
        )
    }
}

interface SelectionActionsProps {
    canMove: boolean // false s'il n'y a aucune liste où déplacer
    onMove: () => void
    onDelete: () => void
}

/** Mode mobile, pendant une sélection : barre d'actions en bas de l'écran. */
export class SelectionActions extends React.Component<SelectionActionsProps> {
    render(): React.ReactNode {
        return (
            <div className="SelectionActions" role="toolbar" aria-label="Actions sur la sélection">
                <button onClick={this.props.onMove} disabled={!this.props.canMove}>
                    <ArrowRightLeft size={20} />Déplacer
                </button>
                <button className="danger" onClick={this.props.onDelete}>
                    <Trash2 size={20} />Supprimer
                </button>
            </div>
        )
    }
}
