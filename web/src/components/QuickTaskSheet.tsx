import * as React from 'react'
import { CalendarDays, X } from 'lucide-react'
import type { Folder, Group, TodoList } from '../api/types'
import { formatDueDate, todayIso, tomorrowIso } from '../utils/dates'
import { KeyboardListener } from '../utils/mobileLayout'
import { DismissListener } from '../utils/popover'
import { chooseList, readQuickTaskMemory, writeQuickTaskMemory, type QuickTaskMemory } from '../utils/quickTask'
import { ListGroupPicker, type PickedPlace } from './ListGroupPicker'
import './QuickTaskSheet.css'

interface QuickTaskSheetProps {
    folders: Folder[]
    lists: TodoList[]
    onLoadGroups: (listId: string) => Promise<Group[]>
    // where : « Liste › Groupe », pour le message de confirmation. Renvoie true si la tâche est créée.
    onCreate: (groupId: string, title: string, dueDate: string | null, where: string) => Promise<boolean>
    onClose: () => void
}

class QuickTaskSheetState {
    title: string = ''
    place: PickedPlace | null = null    // Liste et groupe choisis (null pendant le chargement)
    dueDate: string | null = todayIso() // « Aujourd'hui » proposé par défaut
    busy: boolean = false               // Création en cours : évite un double envoi
    keyboard: number = 0                // Hauteur cachée par le clavier du téléphone
}

/**
 * Quick task : créer une tâche depuis l'accueil mobile, sans ouvrir de liste.
 * Panneau du bas avec le titre, la liste et le groupe (ListGroupPicker, préremplis avec
 * ceux de la dernière Quick task, voir utils/quickTask.ts) et l'échéance.
 *
 *   Entrée ou « Créer la tâche » → création, mémoire mise à jour, panneau fermé
 *   Échap, la croix ou toucher le fond → fermé sans rien créer
 *
 * Ne parle pas à l'API : Workspace charge les groupes (onLoadGroups) et crée la tâche (onCreate).
 */
export class QuickTaskSheet extends React.Component<QuickTaskSheetProps, QuickTaskSheetState> {
    private rootRef = React.createRef<HTMLFormElement>()
    private memory: QuickTaskMemory | null = readQuickTaskMemory()
    private dismiss = new DismissListener(this.rootRef, () => this.props.onClose())
    private keyboard = new KeyboardListener(keyboard => this.setState(state => (state.keyboard === keyboard ? null : { keyboard })))

    constructor(props: QuickTaskSheetProps) {
        super(props)
        this.state = new QuickTaskSheetState()
    }

    componentDidMount(): void {
        this.dismiss.start()
        this.keyboard.start()
    }

    componentWillUnmount(): void {
        this.dismiss.stop()
        this.keyboard.stop()
    }

    onTitleChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        this.setState({ title: event.target.value })
    }

    onPlaceChanged = (place: PickedPlace | null): void => {
        this.setState({ place })
    }

    onDateChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        this.setState({ dueDate: event.target.value || null }) // Champ vidé → sans échéance
    }

    onSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault()
        const { title, place, dueDate, busy } = this.state
        const trimmed = title.trim()
        if (trimmed === '' || !place || busy) return

        this.setState({ busy: true })
        const ok = await this.props.onCreate(place.groupId, trimmed, dueDate, place.where)
        if (ok) {
            writeQuickTaskMemory({ listId: place.listId, groupId: place.groupId })
            this.props.onClose()
        } else if (this.rootRef.current) {
            this.setState({ busy: false }) // Erreur affichée par Workspace : on peut réessayer
        }
    }

    render(): React.ReactNode {
        const { title, place, dueDate, busy } = this.state
        const today = todayIso()
        const tomorrow = tomorrowIso()
        const otherDate = dueDate !== null && dueDate !== today && dueDate !== tomorrow
        const canCreate = title.trim() !== '' && place !== null && !busy
        const fromMemory = this.memory !== null && place?.listId === this.memory.listId && place.groupId === this.memory.groupId

        return (
            <>
                {/* Fond grisé (style dans TaskPopover.css) ; le toucher ferme (clic en dehors, DismissListener) */}
                <div className="sheet-backdrop" />
                <form className="QuickTaskSheet" ref={this.rootRef} style={{ bottom: this.state.keyboard }} onSubmit={this.onSubmit}>
                    <div className="sheet-handle" />

                    <div className="qt-head">
                        <h2>Quick task</h2>
                        <button type="button" className="qt-close" onClick={this.props.onClose} aria-label="Fermer">
                            <X size={20} />
                        </button>
                    </div>

                    <input
                        className="qt-title"
                        value={title}
                        placeholder="Titre de la tâche"
                        aria-label="Titre de la tâche"
                        enterKeyHint="done"
                        autoFocus
                        onChange={this.onTitleChanged}
                    />

                    <ListGroupPicker
                        folders={this.props.folders}
                        lists={this.props.lists}
                        initialListId={chooseList(this.memory?.listId ?? null, this.props.lists)}
                        preferredGroupId={this.memory?.groupId ?? null}
                        onLoadGroups={this.props.onLoadGroups}
                        onChange={this.onPlaceChanged}
                    />
                    {fromMemory && <span className="qt-hint">Dernier endroit utilisé</span>}

                    <div className="qt-field">
                        <span className="qt-label"><CalendarDays size={15} />Échéance</span>
                        <div className="qt-chips">
                            <button type="button" className={dueDate === today ? 'qt-chip on' : 'qt-chip'} onClick={() => this.setState({ dueDate: today })}>
                                Aujourd'hui
                            </button>
                            <button type="button" className={dueDate === tomorrow ? 'qt-chip on' : 'qt-chip'} onClick={() => this.setState({ dueDate: tomorrow })}>
                                Demain
                            </button>
                            {/* Le champ date, invisible, couvre la pastille : le toucher ouvre le calendrier du téléphone */}
                            <label className={otherDate ? 'qt-chip qt-date on' : 'qt-chip qt-date'}>
                                <CalendarDays size={16} />
                                {otherDate ? formatDueDate(dueDate) : 'Date'}
                                <input
                                    type="date"
                                    value={dueDate ?? ''}
                                    onChange={this.onDateChanged}
                                    onClick={event => event.currentTarget.showPicker?.()} // Ordinateur : ouvre aussi le calendrier
                                    aria-label="Choisir une date"
                                />
                            </label>
                            <button type="button" className={dueDate === null ? 'qt-chip on' : 'qt-chip'} onClick={() => this.setState({ dueDate: null })}>
                                Aucune
                            </button>
                        </div>
                    </div>

                    <button type="submit" className="btn-primary qt-create" disabled={!canCreate}>
                        {busy ? 'Création…' : 'Créer la tâche'}
                    </button>
                </form>
            </>
        )
    }
}
