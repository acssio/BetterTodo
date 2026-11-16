import * as React from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { scrollEdges, type ScrollEdges } from '../utils/scrollEdges'
import type { TaskViewProps } from './actions'
import { AddTask } from './AddTask'
import { GroupHeader } from './GroupHeader'
import { NewGroup } from './NewGroup'
import { TaskCard } from './TaskCard'
import { TaskDropZone } from './TaskDropZone'
import './Board.css'

class BoardState {
    edges: ScrollEdges = 'none' // Côtés où des colonnes sont cachées (fondu + chevron)
}

/**
 * Vue colonnes : un groupe par colonne, ses tâches en cartes.
 * Quand des colonnes dépassent, le bord concerné s'estompe et un chevron fait avancer d'une colonne.
 */
export class Board extends React.Component<TaskViewProps, BoardState> {
    state = new BoardState()
    private scrollRef = React.createRef<HTMLDivElement>()
    private resizeObserver = new ResizeObserver(() => this.updateEdges()) // La fenêtre ou la barre latérale change de taille

    componentDidMount(): void {
        if (this.scrollRef.current) this.resizeObserver.observe(this.scrollRef.current)
        this.updateEdges()
    }

    componentDidUpdate(): void {
        this.updateEdges() // Un groupe ajouté ou supprimé change la largeur totale
    }

    componentWillUnmount(): void {
        this.resizeObserver.disconnect()
    }

    /** Recalcule l'état des bords. Ne déclenche un rendu que s'il a changé (sinon boucle avec componentDidUpdate). */
    private updateEdges = (): void => {
        const scroller = this.scrollRef.current
        if (!scroller) return
        const edges = scrollEdges(scroller.scrollLeft, scroller.clientWidth, scroller.scrollWidth)
        this.setState(state => (state.edges === edges ? null : { edges }))
    }

    /** Fait défiler d'une colonne (largeur d'une colonne + écart entre deux). direction : -1 gauche, 1 droite. */
    private scrollByColumn(direction: number): void {
        const scroller = this.scrollRef.current
        const column = scroller?.querySelector<HTMLElement>('.board-column')
        if (!scroller || !column) return
        const gap = parseFloat(getComputedStyle(scroller).columnGap) || 0
        const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
        scroller.scrollBy({ left: direction * (column.offsetWidth + gap), behavior: reduceMotion ? 'auto' : 'smooth' })
    }

    onPreviousClick = (): void => this.scrollByColumn(-1)
    onNextClick = (): void => this.scrollByColumn(1)

    render(): React.ReactNode {
        const { board, urgencyLevels, actions, openTaskId, drag } = this.props
        const { edges } = this.state
        const leftHidden = edges === 'left' || edges === 'both'
        const rightHidden = edges === 'right' || edges === 'both'

        return (
            <div className="Board">
                <div className="board-scroll" ref={this.scrollRef} onScroll={this.updateEdges}>
                    {board.groups.map(group => (
                        <TaskDropZone
                            key={group.id}
                            className="board-column"
                            groupId={group.id}
                            tasks={group.tasks}
                            drag={drag}
                            actions={actions}
                            header={<GroupHeader group={group} actions={actions} />}
                            footer={<AddTask groupId={group.id} actions={actions} />}
                            renderTask={(task, dragging) => (
                                <TaskCard
                                    key={task.id}
                                    task={task}
                                    urgency={urgencyLevels.find(level => level.id === task.urgency_id)}
                                    selected={task.id === openTaskId}
                                    dragging={dragging}
                                    onToggle={actions.toggleTask}
                                    onOpen={actions.openTask}
                                    onDragStart={actions.startTaskDrag}
                                    onDragEnd={actions.endTaskDrag}
                                />
                            )}
                        />
                    ))}

                    <NewGroup view={this.props} className="board-column" />
                </div>

                <div className={leftHidden ? 'board-edge left visible' : 'board-edge left'}>
                    <button onClick={this.onPreviousClick} aria-label="Colonnes précédentes" tabIndex={leftHidden ? 0 : -1}>
                        <ChevronLeft size={18} />
                    </button>
                </div>
                <div className={rightHidden ? 'board-edge right visible' : 'board-edge right'}>
                    <button onClick={this.onNextClick} aria-label="Colonnes suivantes" tabIndex={rightHidden ? 0 : -1}>
                        <ChevronRight size={18} />
                    </button>
                </div>
            </div>
        )
    }
}
