import * as React from 'react'
import { Search } from 'lucide-react'
import { LIST_COLORS, LIST_ICONS } from './listIcons'
import './TaskPopover.css'     // .pop-field, .pop-label, .pop-input
import './ListEditPopover.css' // .list-colors, .list-color, .list-icons, .list-icon

interface ListLookPickerProps {
    color: string
    icon: string
    onColor: (color: string) => void
    onIcon: (icon: string) => void
}

class ListLookPickerState {
    iconQuery: string = '' // Recherche dans les icônes
}

/**
 * Choix de la couleur et de l'icône (palette commune) : utilisé par « Modifier une liste »
 * (ListEditPopover) et par la fenêtre d'une liste intelligente (SmartListDialog).
 */
export class ListLookPicker extends React.Component<ListLookPickerProps, ListLookPickerState> {
    state = new ListLookPickerState()

    /** Icônes dont le nom (anglais) ou le libellé (français) contient la recherche. */
    private filteredIcons(): string[] {
        const query = this.state.iconQuery.trim().toLowerCase()
        return Object.entries(LIST_ICONS)
            .filter(([name, { label }]) => query === '' || name.includes(query) || label.includes(query))
            .map(([name]) => name)
    }

    render(): React.ReactNode {
        const { color, icon } = this.props
        const icons = this.filteredIcons()

        return (
            <>
                <div className="pop-field">
                    <span className="pop-label">Couleur</span>
                    <div className="list-colors">
                        {LIST_COLORS.map(c => (
                            <button
                                key={c}
                                type="button"
                                className={c === color ? 'list-color on' : 'list-color'}
                                style={{ background: c, color: c }}
                                aria-label={`Couleur ${c}`}
                                onClick={() => this.props.onColor(c)}
                            />
                        ))}
                    </div>
                </div>

                <div className="pop-field">
                    <span className="pop-label">Icône</span>
                    <span className="pop-input">
                        <Search size={14} />
                        <input
                            value={this.state.iconQuery}
                            placeholder="Rechercher une icône…"
                            onChange={event => this.setState({ iconQuery: event.target.value })}
                            onKeyDown={event => { if (event.key === 'Enter') event.preventDefault() }} // Ne valide pas la fenêtre
                        />
                    </span>
                    <div className="list-icons">
                        {icons.map(name => {
                            const Icon = LIST_ICONS[name].icon
                            const selected = name === icon
                            return (
                                <button
                                    key={name}
                                    type="button"
                                    className={selected ? 'list-icon on' : 'list-icon'}
                                    style={selected ? { borderColor: color, background: color + '1A' } : undefined}
                                    title={LIST_ICONS[name].label}
                                    onClick={() => this.props.onIcon(name)}
                                >
                                    <Icon size={18} color={selected ? color : 'currentColor'} />
                                </button>
                            )
                        })}
                        {icons.length === 0 && <span className="pop-status">Aucune icône trouvée</span>}
                    </div>
                </div>
            </>
        )
    }
}
