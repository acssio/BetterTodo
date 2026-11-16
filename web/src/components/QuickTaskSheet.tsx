import * as React from 'react'
import { CalendarDays, ChevronDown, Layers, List, X } from 'lucide-react'
import type { Folder, Group, TodoList } from '../api/types'
import { formatDueDate, todayIso, tomorrowIso } from '../utils/dates'
import { KeyboardListener } from '../utils/mobileLayout'
import { DismissListener } from '../utils/popover'
import { chooseGroup, chooseList, readQuickTaskMemory, writeQuickTaskMemory, type QuickTaskMemory } from '../utils/quickTask'
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
    listId: string | null = null
    groupId: string | null = null
    groups: Group[] = []              // Groupes de la liste choisie
    loadingGroups: boolean = false
    dueDate: string | null = todayIso() // « Aujourd'hui » proposé par défaut
    busy: boolean = false             // Création en cours : évite un double envoi
    keyboard: number = 0              // Hauteur cachée par le clavier du téléphone
}

/**
 * Quick task : créer une tâche depuis l'accueil mobile, sans ouvrir de liste.
 * Panneau du bas avec le titre, la liste et le groupe (préremplis avec ceux de la
 * dernière Quick task, voir utils/quickTask.ts) et l'échéance.
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
        this.state = { ...new QuickTaskSheetState(), listId: chooseList(this.memory?.listId ?? null, props.lists) }
    }

    componentDidMount(): void {
        this.dismiss.start()
        this.keyboard.start()
        if (this.state.listId) void this.loadGroups(this.state.listId)
    }

    componentWillUnmount(): void {
        this.dismiss.stop()
        this.keyboard.stop()
    }

    /** Charge les groupes d'une liste, puis choisit le groupe retenu (ou le premier). */
    private async loadGroups(listId: string): Promise<void> {
        this.setState({ loadingGroups: true })
        const groups = await this.props.onLoadGroups(listId)
        // Fermé entre-temps, ou une autre liste a été choisie pendant le chargement : on ignore
        if (!this.rootRef.current) return
        this.setState(state => (
            state.listId === listId
                ? { groups, groupId: chooseGroup(this.memory?.groupId ?? null, groups), loadingGroups: false }
                : null
        ))
    }

    onTitleChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        this.setState({ title: event.target.value })
    }

    onListChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
        const listId = event.target.value
        this.setState({ listId, groups: [], groupId: null })
        void this.loadGroups(listId)
    }

    onGroupChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
        this.setState({ groupId: event.target.value })
    }

    onDateChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        this.setState({ dueDate: event.target.value || null }) // Champ vidé → sans échéance
    }

    onSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault()
        const { title, listId, groupId, dueDate, busy } = this.state
        const trimmed = title.trim()
        if (trimmed === '' || !listId || !groupId || busy) return

        this.setState({ busy: true })
        const ok = await this.props.onCreate(groupId, trimmed, dueDate, this.whereLabel())
        if (ok) {
            writeQuickTaskMemory({ listId, groupId })
            this.props.onClose()
        } else if (this.rootRef.current) {
            this.setState({ busy: false }) // Erreur affichée par Workspace : on peut réessayer
        }
    }

    /** « Travail › À faire » */
    private whereLabel(): string {
        const list = this.props.lists.find(l => l.id === this.state.listId)
        const group = this.state.groups.find(g => g.id === this.state.groupId)
        return `${list?.name ?? ''} › ${group?.name ?? ''}`
    }

    render(): React.ReactNode {
        const { title, listId, groupId, groups, loadingGroups, dueDate, busy } = this.state
        const today = todayIso()
        const tomorrow = tomorrowIso()
        const otherDate = dueDate !== null && dueDate !== today && dueDate !== tomorrow
        const canCreate = title.trim() !== '' && groupId !== null && !busy

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

                    <div className="qt-row">
                        <label className="qt-field">
                            <span className="qt-label"><List size={15} />Liste</span>
                            <span className="qt-select-box">
                                <select value={listId ?? ''} onChange={this.onListChanged}>
                                    {this.renderListOptions()}
                                </select>
                                <ChevronDown size={16} />
                            </span>
                        </label>
                        <label className="qt-field">
                            <span className="qt-label"><Layers size={15} />Groupe</span>
                            <span className="qt-select-box">
                                <select value={groupId ?? ''} onChange={this.onGroupChanged} disabled={loadingGroups || groups.length === 0}>
                                    {loadingGroups && <option value="">Chargement…</option>}
                                    {!loadingGroups && groups.length === 0 && <option value="">Aucun groupe</option>}
                                    {groups.map(group => <option key={group.id} value={group.id}>{group.name}</option>)}
                                </select>
                                <ChevronDown size={16} />
                            </span>
                        </label>
                    </div>
                    {this.memory && listId === this.memory.listId && groupId === this.memory.groupId && (
                        <span className="qt-hint">Dernier endroit utilisé</span>
                    )}

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

    /** Listes hors groupe d'abord, puis une rubrique par groupe de listes (comme sur l'accueil). */
    private renderListOptions(): React.ReactNode {
        const { folders, lists } = this.props
        return (
            <>
                {lists.filter(list => list.folder_id === null).map(list => (
                    <option key={list.id} value={list.id}>{list.name}</option>
                ))}
                {folders.map(folder => {
                    const folderLists = lists.filter(list => list.folder_id === folder.id)
                    if (folderLists.length === 0) return null
                    return (
                        <optgroup key={folder.id} label={folder.name}>
                            {folderLists.map(list => <option key={list.id} value={list.id}>{list.name}</option>)}
                        </optgroup>
                    )
                })}
            </>
        )
    }
}
