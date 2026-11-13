import * as React from 'react'
import { List } from 'lucide-react'
import { LIST_ICONS } from './listIcons'
import './ListIcon.css'

interface ListIconProps {
    icon: string
    color: string
    size?: number
    boxed?: boolean // true = sur un carré teinté (en-tête de liste)
}

/** Icône d'une liste, dans sa couleur. Une icône inconnue devient l'icône "list". */
export class ListIcon extends React.Component<ListIconProps> {
    render(): React.ReactNode {
        const { icon, color, size = 16, boxed = false } = this.props
        const Icon = LIST_ICONS[icon]?.icon ?? List
        const svg = <Icon size={size} color={color} strokeWidth={2} />

        if (!boxed) {
            return svg
        }
        // Fond = la couleur de la liste avec 10 % d'opacité (1A en hexadécimal)
        return <span className="ListIcon-box" style={{ background: color + '1A' }}>{svg}</span>
    }
}
