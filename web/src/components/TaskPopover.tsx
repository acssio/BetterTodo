import * as React from 'react'
import { Calendar, Check, Plus, Trash2, X } from 'lucide-react'
import type { Group, Task, TaskChanges, UrgencyLevel } from '../api/types'
import { KeyboardListener } from '../utils/mobileLayout'
import { DismissListener, popoverPosition } from '../utils/popover'
import { confirmDialog } from './confirm'
import './TaskPopover.css'

const POPOVER_WIDTH = 360
const NOTES_SAVE_DELAY = 800 // ms sans frapper avant d'enregistrer les notes

interface TaskPopoverProps {
    task: Task
    groups: Group[]           // Pour la liste déroulante « Groupe »
    urgencyLevels: UrgencyLevel[]
    anchor: DOMRect           // Position de la carte : l'overlay s'ouvre à côté
    sheet?: boolean           // Mode mobile : panneau qui monte du bas de l'écran (anchor ignoré)
    onUpdate: (changes: TaskChanges) => Promise<void>
    onDelete: () => void
    onClose: () => void
}

class TaskPopoverState {
    title: string = ''   // Titre et notes sont modifiés localement,
    notes: string = ''   // puis envoyés à l'API (voir saveTitle / saveNotes)
    dueTime: string = '' // Heure tapée ("14:30", '' = aucune), envoyée en quittant le champ (saveDueTime)
    tagInput: string = ''
    pending: number = 0  // Nombre d'enregistrements en cours
    keyboard: number = 0 // Panneau du bas : hauteur cachée par le clavier du téléphone
}

/**
 * Petit overlay d'édition d'une tâche, ouvert à côté de sa carte.
 * Pas de bouton « Enregistrer » : chaque changement part tout seul à l'API.
 *   - échéance, groupe, urgence, tags : dès qu'on les change ;
 *   - titre et heure : en quittant le champ ou avec Entrée ;
 *   - notes : 0,8 s après la dernière frappe, ou en quittant le champ.
 * Se ferme avec Échap, la croix, ou un clic en dehors.
 */
export class TaskPopover extends React.Component<TaskPopoverProps, TaskPopoverState> {
    private rootRef = React.createRef<HTMLDivElement>()
    private notesTimer: number | undefined
    // Échap ou clic en dehors → fermer (sauf clic sur sa propre carte)
    private dismiss = new DismissListener(this.rootRef, () => this.props.onClose(), `[data-task-id="${this.props.task.id}"]`)

    constructor(props: TaskPopoverProps) {
        super(props)
        this.state = {
            ...new TaskPopoverState(),
            title: props.task.title,
            notes: props.task.notes,
            dueTime: props.task.due_time ?? '',
        }
    }

    // Panneau du bas : on le remonte au-dessus du clavier du téléphone
    private keyboard = new KeyboardListener(keyboard => this.setState(state => (state.keyboard === keyboard ? null : { keyboard })))

    componentDidMount(): void {
        this.dismiss.start()
        if (this.props.sheet) this.keyboard.start()
    }

    componentWillUnmount(): void {
        this.dismiss.stop()
        this.keyboard.stop()
        // Enregistre ce qui n'est pas encore parti (fermeture pendant la frappe)
        window.clearTimeout(this.notesTimer)
        this.saveTitle()
        this.saveNotes()
        this.saveDueTime()
    }

    // --- Enregistrement --------------------------------------------------------

    /** Envoie un changement à l'API, en comptant les envois en cours (pour le texte du bas). */
    private async update(changes: TaskChanges): Promise<void> {
        this.setState(state => ({ pending: state.pending + 1 }))
        await this.props.onUpdate(changes)
        // Le composant peut avoir été fermé entre-temps
        if (this.rootRef.current) {
            this.setState(state => ({ pending: state.pending - 1 }))
        }
    }

    private saveTitle(): void {
        const title = this.state.title.trim()
        if (title === '') {
            this.setState({ title: this.props.task.title }) // Titre vide refusé : on remet l'ancien
        } else if (title !== this.props.task.title) {
            void this.update({ title })
        }
    }

    private saveNotes(): void {
        if (this.state.notes !== this.props.task.notes) {
            void this.update({ notes: this.state.notes })
        }
    }

    /**
     * L'heure n'est envoyée qu'en quittant le champ : si on l'enregistrait à chaque chiffre,
     * la réponse de l'API réécrirait le champ pendant la frappe et le navigateur perdrait la saisie.
     */
    private saveDueTime(): void {
        if (this.props.task.due_date === null) return // Pas d'échéance : pas d'heure possible
        const time = this.state.dueTime === '' ? null : this.state.dueTime
        if (time !== this.props.task.due_time) {
            void this.update({ due_time: time })
        }
    }

    onTitleChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        this.setState({ title: event.target.value })
    }

    onTitleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>): void => {
        if (event.key === 'Enter') event.currentTarget.blur() // → onBlur → saveTitle
    }

    onNotesChanged = (event: React.ChangeEvent<HTMLTextAreaElement>): void => {
        this.setState({ notes: event.target.value })
        window.clearTimeout(this.notesTimer)
        this.notesTimer = window.setTimeout(() => this.saveNotes(), NOTES_SAVE_DELAY)
    }

    onNotesBlur = (): void => {
        window.clearTimeout(this.notesTimer)
        this.saveNotes()
    }

    onDueDateChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        const date = event.target.value
        if (date) {
            void this.update({ due_date: date })
        } else {
            // Champ vidé → plus d'échéance, et donc plus d'heure (une heure demande une date)
            this.setState({ dueTime: '' })
            void this.update({ due_date: null, due_time: null })
        }
    }

    /** Heure tapée directement (HH:MM). Le navigateur donne '' tant que l'heure n'est pas complète. */
    onDueTimeChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        this.setState({ dueTime: event.target.value })
    }

    onDueTimeKeyDown = (event: React.KeyboardEvent<HTMLInputElement>): void => {
        if (event.key === 'Enter') event.currentTarget.blur() // → onBlur → saveDueTime
    }

    onGroupChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
        void this.update({ group_id: event.target.value })
    }

    onTagInputChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        this.setState({ tagInput: event.target.value })
    }

    onTagKeyDown = (event: React.KeyboardEvent<HTMLInputElement>): void => {
        if (event.key === 'Enter' || event.key === ',') {
            event.preventDefault()
            this.addTag()
        }
    }

    private addTag(): void {
        const name = this.state.tagInput.trim().replace(/^#/, '')
        this.setState({ tagInput: '' })
        if (name !== '' && !this.props.task.tags.includes(name)) {
            void this.update({ tags: [...this.props.task.tags, name] })
        }
    }

    private removeTag(name: string): void {
        void this.update({ tags: this.props.task.tags.filter(tag => tag !== name) })
    }

    onDeleteClick = async (): Promise<void> => {
        if (await confirmDialog.ask(`Supprimer la tâche « ${this.props.task.title} » ?`)) {
            this.props.onDelete()
        }
    }

    // --- Rendu -----------------------------------------------------------------

    render(): React.ReactNode {
        const { task, groups, urgencyLevels, sheet } = this.props
        // Panneau du bas : collé en bas de l'écran, ou au-dessus du clavier
        const style = sheet ? { bottom: this.state.keyboard } : popoverPosition(this.props.anchor, POPOVER_WIDTH, 540)

        return (
            <>
                {/* Fond grisé derrière le panneau du bas ; le toucher ferme (clic en dehors, DismissListener) */}
                {sheet && <div className="sheet-backdrop" />}
                <div className={sheet ? 'TaskPopover sheet' : 'TaskPopover'} ref={this.rootRef} style={style}>
                    {sheet && <div className="sheet-handle" />}
                    <div className="pop-title">
                        <button
                            className={task.done ? 'pop-check checked' : 'pop-check'}
                            onClick={() => void this.update({ done: !task.done })}
                            aria-label={task.done ? 'Marquer comme à faire' : 'Marquer comme faite'}
                        >
                            {task.done && <Check size={10} color="#FFFFFF" strokeWidth={3.5} />}
                        </button>
                        <input
                            className="pop-title-input"
                            value={this.state.title}
                            onChange={this.onTitleChanged}
                            onKeyDown={this.onTitleKeyDown}
                            onBlur={() => this.saveTitle()}
                            aria-label="Titre"
                        />
                        <button className="icon-btn" onClick={this.props.onClose} aria-label="Fermer">
                            <X size={14} />
                        </button>
                    </div>

                    <div className="pop-row">
                        <label className="pop-field">
                            <span className="pop-label">Échéance</span>
                            <span className="pop-input">
                                <Calendar size={14} />
                                <input type="date" value={task.due_date ?? ''} onChange={this.onDueDateChanged} />
                            </span>
                        </label>
                        <label className="pop-field pop-time">
                            <span className="pop-label">Heure</span>
                            <span className="pop-input">
                                <input
                                    type="time"
                                    value={this.state.dueTime}
                                    disabled={task.due_date === null}
                                    title={task.due_date === null ? "Choisir d'abord une échéance" : undefined}
                                    onChange={this.onDueTimeChanged}
                                    onKeyDown={this.onDueTimeKeyDown}
                                    onBlur={() => this.saveDueTime()}
                                />
                            </span>
                        </label>
                    </div>

                    {/* Groupe sur sa propre ligne : avec l'heure, trois champs ne tiennent pas en 360 px */}
                    <label className="pop-field">
                        <span className="pop-label">Groupe</span>
                        <select className="pop-input" value={task.group_id} onChange={this.onGroupChanged}>
                            {groups.map(group => <option key={group.id} value={group.id}>{group.name}</option>)}
                        </select>
                    </label>

                    <div className="pop-field">
                        <span className="pop-label">Urgence</span>
                        <div className="pop-urgency">
                            <button
                                className={task.urgency_id === null ? 'on' : ''}
                                onClick={() => void this.update({ urgency_id: null })}
                            >
                                Aucune
                            </button>
                            {urgencyLevels.map(level => (
                                <button
                                    key={level.id}
                                    className={task.urgency_id === level.id ? 'on' : ''}
                                    style={task.urgency_id === level.id
                                        ? { color: level.color, borderColor: level.color, background: level.color + '1F' }
                                        : undefined}
                                    onClick={() => void this.update({ urgency_id: level.id })}
                                >
                                    {level.name}
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="pop-field">
                        <span className="pop-label">Tags</span>
                        <div className="pop-tags">
                            {task.tags.map(tag => (
                                <span key={tag} className="pop-tag">
                                    #{tag}
                                    <button onClick={() => this.removeTag(tag)} aria-label={`Enlever ${tag}`}>
                                        <X size={11} />
                                    </button>
                                </span>
                            ))}
                            <span className="pop-tag-add">
                                <Plus size={11} />
                                <input
                                    value={this.state.tagInput}
                                    placeholder="Ajouter"
                                    onChange={this.onTagInputChanged}
                                    onKeyDown={this.onTagKeyDown}
                                    onBlur={() => this.addTag()}
                                />
                            </span>
                        </div>
                    </div>

                    <label className="pop-field">
                        <span className="pop-label">Notes</span>
                        <textarea
                            className="pop-notes"
                            value={this.state.notes}
                            placeholder="Ajouter une note…"
                            onChange={this.onNotesChanged}
                            onBlur={this.onNotesBlur}
                        />
                    </label>

                    <div className="pop-foot">
                        <button className="pop-delete" onClick={this.onDeleteClick}>
                            <Trash2 size={14} />Supprimer
                        </button>
                        <span className="pop-status">
                            {this.state.pending > 0 ? 'Enregistrement…' : 'Enregistré automatiquement'}
                        </span>
                    </div>
                </div>
            </>
        )
    }
}
