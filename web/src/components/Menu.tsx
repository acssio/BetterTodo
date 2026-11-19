import * as React from 'react'
import type { LucideIcon } from 'lucide-react'
import './Menu.css'

export interface MenuItem {
    label: string
    icon: LucideIcon
    danger?: boolean // En rouge (supprimer…)
    kbd?: string     // Raccourci affiché à droite (« Entrée », « ⌫ »…)
    onSelect: () => void
}

interface MenuProps {
    items: MenuItem[]
    onClose: () => void
    at?: { x: number, y: number } // Menu du clic droit : position à l'écran (sinon sous le bouton « … »)
}

// Taille approximative du menu, pour qu'il ne sorte pas de la fenêtre
const MENU_WIDTH = 240
const MENU_ITEM_HEIGHT = 34

/**
 * Petit menu déroulant (« … » d'un groupe, d'un groupe de listes, clic droit sur une tâche).
 * Sans `at`, le parent le place dans un élément .menu-anchor (position: relative).
 * Avec `at`, il s'affiche à cette position (près de la souris), sans sortir de la fenêtre.
 * Un fond invisible sur toute la page le ferme au clic à côté (clic droit compris).
 */
export class Menu extends React.Component<MenuProps> {
    onBackdropContextMenu = (event: React.MouseEvent<HTMLDivElement>): void => {
        event.preventDefault()
        this.props.onClose()
    }

    render(): React.ReactNode {
        const at = this.props.at
        let style: React.CSSProperties | undefined
        if (at) {
            const height = this.props.items.length * MENU_ITEM_HEIGHT + 8
            style = {
                left: Math.max(8, Math.min(at.x, window.innerWidth - MENU_WIDTH - 8)),
                top: Math.max(8, Math.min(at.y, window.innerHeight - height - 8)),
            }
        }

        return (
            <>
                <div className="menu-backdrop" onClick={this.props.onClose} onContextMenu={this.onBackdropContextMenu} />
                <div className={at ? 'Menu at' : 'Menu'} role="menu" style={style}>
                    {this.props.items.map(item => (
                        <button
                            key={item.label}
                            role="menuitem"
                            className={item.danger ? 'danger' : ''}
                            onClick={() => {
                                this.props.onClose()
                                item.onSelect()
                            }}
                        >
                            <item.icon size={14} />{item.label}
                            {item.kbd && <span className="menu-kbd">{item.kbd}</span>}
                        </button>
                    ))}
                </div>
            </>
        )
    }
}
