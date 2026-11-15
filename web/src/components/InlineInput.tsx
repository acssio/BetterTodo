import * as React from 'react'
import './InlineInput.css'

interface InlineInputProps {
    placeholder: string
    initialValue?: string
    keepOpen?: boolean // true = reste ouvert après Entrée (pour enchaîner plusieurs ajouts)
    className?: string
    onSubmit: (value: string) => Promise<void> | void
    onCancel: () => void
}

class InlineInputState {
    value: string = ''
    busy: boolean = false // Pendant l'appel à l'API : évite un double envoi
}

/**
 * Petit champ de saisie « sur place » : nouvelle tâche, nouveau groupe, renommage…
 *   Entrée → valide   ·   Échap → annule   ·   clic ailleurs → valide si non vide, sinon annule
 */
export class InlineInput extends React.Component<InlineInputProps, InlineInputState> {
    constructor(props: InlineInputProps) {
        super(props)
        this.state = { ...new InlineInputState(), value: props.initialValue ?? '' }
    }

    onChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
        this.setState({ value: event.target.value })
    }

    onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>): void => {
        if (event.key === 'Enter') {
            event.preventDefault()
            void this.submit()
        } else if (event.key === 'Escape') {
            event.preventDefault() // Signale à DismissListener que cette touche est déjà traitée
            this.props.onCancel()
        }
    }

    onBlur = (): void => {
        if (this.state.value.trim() === '') {
            this.props.onCancel()
        } else {
            void this.submit(true)
        }
    }

    /** Envoie la valeur. closing = true quand on quitte le champ (on ne le garde pas ouvert). */
    private async submit(closing: boolean = false): Promise<void> {
        const value = this.state.value.trim()
        if (this.state.busy) return
        if (value === '' || value === this.props.initialValue) {
            this.props.onCancel()
            return
        }

        this.setState({ busy: true })
        await this.props.onSubmit(value)

        if (this.props.keepOpen && !closing) {
            this.setState({ value: '', busy: false }) // Prêt pour la suivante
        } else if (closing) {
            this.props.onCancel()
        }
    }

    render(): React.ReactNode {
        return (
            <input
                className={'InlineInput ' + (this.props.className ?? '')}
                value={this.state.value}
                placeholder={this.props.placeholder}
                onChange={this.onChanged}
                onKeyDown={this.onKeyDown}
                onBlur={this.onBlur}
                onFocus={event => event.currentTarget.select()} // Renommage : le texte est sélectionné, on tape par-dessus
                disabled={this.state.busy && !this.props.keepOpen}
                autoFocus
            />
        )
    }
}
