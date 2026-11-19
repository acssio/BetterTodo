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
    current: number = 0         // Mode mobile : index de la colonne affichée (onglet allumé)
}

/**
 * Vue colonnes : un groupe par colonne, ses tâches en cartes.
 * Quand des colonnes dépassent, le bord concerné s'estompe et un chevron fait avancer d'une colonne.
 *
 * Mode mobile : une colonne pleine largeur à la fois. On passe à la suivante en glissant
 * le doigt (le navigateur s'arrête pile sur une colonne grâce à scroll-snap, voir Board.css)
 * ou en touchant un onglet. Pas de fondus ni de chevrons.
 */
export class Board extends React.Component<TaskViewProps, BoardState> {
    state = new BoardState()
    private scrollRef = React.createRef<HTMLDivElement>()
    private tabsRef = React.createRef<HTMLDivElement>()
    private resizeObserver = new ResizeObserver(() => this.updateEdges()) // La fenêtre ou la barre latérale change de taille

    componentDidMount(): void {
        if (this.scrollRef.current) this.resizeObserver.observe(this.scrollRef.current)
        this.updateEdges()
    }

    componentDidUpdate(previousProps: TaskViewProps, previousState: BoardState): void {
        this.updateEdges() // Un groupe ajouté ou supprimé change la largeur totale

        // Mode mobile : l'onglet allumé reste visible dans la barre d'onglets
        if (this.state.current !== previousState.current) {
            const tab = this.tabsRef.current?.children[this.state.current]
            tab?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
        }
        // Mode mobile : « Nouveau groupe » → on va jusqu'au champ, à la fin
        if (this.props.mobile && this.props.addingGroup && !previousProps.addingGroup) {
            this.scrollToColumn(this.props.board.groups.length)
        }
    }

    componentWillUnmount(): void {
        this.resizeObserver.disconnect()
    }

    /** Recalcule l'état des bords. Ne déclenche un rendu que s'il a changé (sinon boucle avec componentDidUpdate). */
    private updateEdges = (): void => {
        const scroller = this.scrollRef.current
        if (!scroller) return
        const edges = scrollEdges(scroller.scrollLeft, scroller.clientWidth, scroller.scrollWidth)
        const step = this.columnStep()
        const current = step > 0 ? Math.round(scroller.scrollLeft / step) : 0
        this.setState(state => (state.edges === edges && state.current === current ? null : { edges, current }))
    }

    /** Distance d'une colonne à la suivante : sa largeur + l'écart entre deux (0 s'il n'y a pas de colonne). */
    private columnStep(): number {
        const scroller = this.scrollRef.current
        const column = scroller?.querySelector<HTMLElement>('.board-column')
        if (!scroller || !column) return 0
        const gap = parseFloat(getComputedStyle(scroller).columnGap) || 0
        return column.offsetWidth + gap
    }

    private scrollBehavior(): ScrollBehavior {
        return window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'
    }

    /** Fait défiler d'une colonne. direction : -1 gauche, 1 droite. */
    private scrollByColumn(direction: number): void {
        this.scrollRef.current?.scrollBy({ left: direction * this.columnStep(), behavior: this.scrollBehavior() })
    }

    /** Mode mobile : affiche la colonne n° index (onglet touché). */
    private scrollToColumn(index: number): void {
        this.scrollRef.current?.scrollTo({ left: index * this.columnStep(), behavior: this.scrollBehavior() })
    }

    onPreviousClick = (): void => this.scrollByColumn(-1)
    onNextClick = (): void => this.scrollByColumn(1)

    render(): React.ReactNode {
        const { board, urgencyLevels, actions, openTaskId, selectedTaskIds, drag } = this.props
        const { edges } = this.state
        const leftHidden = edges === 'left' || edges === 'both'
        const rightHidden = edges === 'right' || edges === 'both'

        return (
            <div className="Board">
                {this.props.mobile && board.groups.length > 0 && (
                    <div className="board-tabs" role="tablist" ref={this.tabsRef}>
                        {board.groups.map((group, index) => (
                            <button
                                key={group.id}
                                role="tab"
                                aria-selected={index === this.state.current}
                                className={index === this.state.current ? 'board-tab on' : 'board-tab'}
                                onClick={() => this.scrollToColumn(index)}
                            >
                                <span className="board-tab-name">{group.name}</span>
                                <span className="group-count">{group.tasks.length}</span>
                            </button>
                        ))}
                    </div>
                )}

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
                                    opened={task.id === openTaskId}
                                    selected={selectedTaskIds.includes(task.id)}
                                    menuOpen={task.id === this.props.menuTaskId}
                                    dragging={dragging}
                                    tapToOpen={this.props.mobile}
                                    selecting={selectedTaskIds.length > 0}
                                    onTouchDragStart={this.props.mobile ? actions.touchDragStart : undefined}
                                    onTouchDragMove={actions.touchDragMove}
                                    onTouchDragEnd={actions.touchDragEnd}
                                    onToggle={actions.toggleTask}
                                    onOpen={actions.openTask}
                                    onRename={actions.renameTask}
                                    onToggleSelection={actions.toggleSelection}
                                    onSelectRange={actions.selectRange}
                                    onOpenMenu={actions.openTaskMenu}
                                    onStartEditing={actions.startEditing}
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
