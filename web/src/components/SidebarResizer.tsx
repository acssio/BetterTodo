import * as React from 'react'
import { clampSidebarWidth, snapSidebarWidth } from '../utils/sidebarLayout'
import './SidebarResizer.css'

const KEYBOARD_STEP = 20 // px par appui sur ← / →

interface SidebarResizerProps {
    width: number
    onResize: (width: number) => void    // Pendant qu'on tire
    onResizeEnd: (width: number) => void // Au relâchement (largeur aimantée)
    onToggle: () => void                 // Double-clic : icônes ↔ normale
}

/**
 * Poignée verticale au bord droit de la barre latérale.
 * On l'attrape (mousedown), on tire (mousemove sur tout le document, pour ne pas
 * la « perdre » si la souris va vite), on relâche (mouseup).
 */
export class SidebarResizer extends React.Component<SidebarResizerProps> {
    private startX = 0
    private startWidth = 0

    componentWillUnmount(): void {
        this.stopListening()
    }

    onMouseDown = (event: React.MouseEvent<HTMLDivElement>): void => {
        event.preventDefault() // Évite de sélectionner du texte pendant qu'on tire
        this.startX = event.clientX
        this.startWidth = this.props.width
        document.addEventListener('mousemove', this.onMouseMove)
        document.addEventListener('mouseup', this.onMouseUp)
        document.body.classList.add('is-resizing') // Curseur ↔ partout pendant le redimensionnement
    }

    onMouseMove = (event: MouseEvent): void => {
        this.props.onResize(clampSidebarWidth(this.startWidth + event.clientX - this.startX))
    }

    onMouseUp = (event: MouseEvent): void => {
        this.stopListening()
        this.props.onResizeEnd(snapSidebarWidth(this.startWidth + event.clientX - this.startX))
    }

    onKeyDown = (event: React.KeyboardEvent<HTMLDivElement>): void => {
        if (event.key === 'ArrowLeft') this.props.onResizeEnd(snapSidebarWidth(this.props.width - KEYBOARD_STEP))
        if (event.key === 'ArrowRight') this.props.onResizeEnd(snapSidebarWidth(this.props.width + KEYBOARD_STEP))
    }

    private stopListening(): void {
        document.removeEventListener('mousemove', this.onMouseMove)
        document.removeEventListener('mouseup', this.onMouseUp)
        document.body.classList.remove('is-resizing')
    }

    render(): React.ReactNode {
        return (
            <div
                className="SidebarResizer"
                role="separator"
                aria-orientation="vertical"
                aria-label="Largeur de la barre latérale"
                aria-valuenow={this.props.width}
                tabIndex={0}
                title="Tirer pour changer la largeur · double-clic : icônes seules"
                onMouseDown={this.onMouseDown}
                onDoubleClick={this.props.onToggle}
                onKeyDown={this.onKeyDown}
            />
        )
    }
}
