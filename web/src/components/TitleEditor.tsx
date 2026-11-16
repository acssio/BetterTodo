import * as React from 'react'
import './TitleEditor.css'

interface TitleEditorProps {
    value: string
    caret: number | null  // Où placer le curseur (null = à la fin)
    multiline: boolean    // true : carte (le titre passe à la ligne) ; false : ligne de la vue sections
    onSave: (title: string) => void
    onCancel: () => void
}

class TitleEditorState {
    value: string = ''
}

/**
 * Titre d'une tâche modifié sur place, à la place du texte.
 *   Entrée ou clic ailleurs → enregistre   ·   Échap → annule
 * Une textarea qui grandit avec le texte : un titre n'a pas de retour à la ligne,
 * mais il peut être long et passer sur plusieurs lignes dans une carte.
 */
export class TitleEditor extends React.Component<TitleEditorProps, TitleEditorState> {
    private inputRef = React.createRef<HTMLTextAreaElement>()
    private done: boolean = false // Déjà enregistré ou annulé (le blur arrive juste après Entrée / Échap)

    constructor(props: TitleEditorProps) {
        super(props)
        this.state = { ...new TitleEditorState(), value: props.value }
    }

    componentDidMount(): void {
        const input = this.inputRef.current
        if (!input) return
        const caret = this.props.caret ?? input.value.length
        input.focus()
        input.setSelectionRange(caret, caret)
        this.fitHeight()
    }

    /** Hauteur = hauteur du texte (sinon une textarea garde une taille fixe). */
    private fitHeight(): void {
        const input = this.inputRef.current
        if (!input) return
        input.style.height = 'auto'
        input.style.height = input.scrollHeight + 'px'
    }

    private save(): void {
        if (this.done) return
        this.done = true
        const title = this.state.value.trim()
        if (title !== '' && title !== this.props.value) {
            this.props.onSave(title)
        } else {
            this.props.onCancel() // Vide ou inchangé : on garde l'ancien titre
        }
    }

    onChanged = (event: React.ChangeEvent<HTMLTextAreaElement>): void => {
        this.setState({ value: event.target.value.replace(/\n/g, ' ') }) // Un collage peut contenir des retours à la ligne
        this.fitHeight()
    }

    onKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>): void => {
        event.stopPropagation() // Suppr / ⌫ servent au texte, pas à supprimer la sélection
        if (event.key === 'Enter') {
            event.preventDefault() // Pas de retour à la ligne
            this.save()
        } else if (event.key === 'Escape') {
            event.preventDefault() // Signale à DismissListener / Workspace que cette touche est déjà traitée
            this.done = true
            this.props.onCancel()
        }
    }

    onBlur = (): void => {
        this.save()
    }

    render(): React.ReactNode {
        return (
            <textarea
                ref={this.inputRef}
                className={this.props.multiline ? 'TitleEditor' : 'TitleEditor single'}
                value={this.state.value}
                rows={1}
                wrap={this.props.multiline ? 'soft' : 'off'}
                onChange={this.onChanged}
                onKeyDown={this.onKeyDown}
                onBlur={this.onBlur}
                onClick={event => event.stopPropagation()} // Ne pas relancer le clic de la carte
                aria-label="Titre de la tâche"
            />
        )
    }
}
