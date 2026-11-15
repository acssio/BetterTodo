import * as React from 'react'
import { confirmDialog, type ConfirmRequest } from './confirm'
import './ConfirmDialog.css'

class ConfirmHostState {
    request: ConfirmRequest | null = null
}

/**
 * Affiche les confirmations demandées par confirmDialog.ask(…).
 * Placé une seule fois, dans App. Entrée = confirmer, Échap = annuler.
 */
export class ConfirmHost extends React.Component<object, ConfirmHostState> {
    private confirmRef = React.createRef<HTMLButtonElement>()

    constructor(props: object) {
        super(props)
        this.state = new ConfirmHostState()
    }

    componentDidMount(): void {
        confirmDialog.setHost(request => {
            this.state.request?.resolve(false) // Une demande déjà ouverte est annulée
            this.setState({ request })
        })
        // Phase de capture (true) : on traite Échap AVANT les overlays, pour qu'ils ne se ferment pas aussi
        document.addEventListener('keydown', this.onKeyDown, true)
    }

    componentDidUpdate(_props: object, previous: ConfirmHostState): void {
        if (this.state.request && !previous.request) {
            this.confirmRef.current?.focus() // Entrée confirme directement
        }
    }

    componentWillUnmount(): void {
        confirmDialog.setHost(null)
        document.removeEventListener('keydown', this.onKeyDown, true)
    }

    private answer(confirmed: boolean): void {
        this.state.request?.resolve(confirmed)
        this.setState({ request: null })
    }

    onKeyDown = (event: KeyboardEvent): void => {
        if (this.state.request && event.key === 'Escape') {
            event.preventDefault()
            event.stopPropagation()
            this.answer(false)
        }
    }

    render(): React.ReactNode {
        const request = this.state.request
        if (!request) return null

        return (
            // data-keep-popovers : un clic ici ne ferme pas l'overlay ouvert derrière (voir DismissListener)
            <div className="ConfirmDialog-backdrop" data-keep-popovers onMouseDown={() => this.answer(false)}>
                <div className="ConfirmDialog" role="alertdialog" aria-modal="true" onMouseDown={event => event.stopPropagation()}>
                    <p>{request.message}</p>
                    <div className="confirm-buttons">
                        {request.cancelable && (
                            <button className="btn-ghost" onClick={() => this.answer(false)}>Annuler</button>
                        )}
                        <button
                            ref={this.confirmRef}
                            className={request.danger ? 'btn-primary confirm-danger' : 'btn-primary'}
                            onClick={() => this.answer(true)}
                        >
                            {request.confirmLabel}
                        </button>
                    </div>
                </div>
            </div>
        )
    }
}
