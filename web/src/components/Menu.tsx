import * as React from 'react'
import type { LucideIcon } from 'lucide-react'
import './Menu.css'

export interface MenuItem {
    label: string
    icon: LucideIcon
    danger?: boolean // En rouge (supprimer…)
    onSelect: () => void
}

interface MenuProps {
    items: MenuItem[]
    onClose: () => void
}

/**
 * Petit menu déroulant (« … » d'un groupe, d'un groupe de listes).
 * Le parent le place dans un élément .menu-anchor (position: relative).
 * Un fond invisible sur toute la page le ferme au clic à côté.
 */
export class Menu extends React.Component<MenuProps> {
    render(): React.ReactNode {
        return (
            <>
                <div className="menu-backdrop" onClick={this.props.onClose} />
                <div className="Menu" role="menu">
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
                        </button>
                    ))}
                </div>
            </>
        )
    }
}
