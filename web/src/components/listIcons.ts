import {
    BookOpen, Briefcase, Calendar, Code, Coffee, Gift, GraduationCap, Heart, House,
    Lightbulb, List, Music, ShoppingCart, Star, User, Wallet,
    type LucideIcon,
} from 'lucide-react'

/**
 * Icônes Lucide proposées pour les listes (les mêmes que dans la maquette).
 * La clé est le nom stocké en base (colonne lists.icon) ; label sert à la recherche en français.
 *
 * On liste les icônes une par une plutôt que d'importer toute la bibliothèque :
 * le fichier JavaScript final ne contient que celles-ci.
 */
export const LIST_ICONS: Record<string, { icon: LucideIcon, label: string }> = {
    'list': { icon: List, label: 'liste' },
    'user': { icon: User, label: 'personne perso' },
    'house': { icon: House, label: 'maison' },
    'heart': { icon: Heart, label: 'cœur santé' },
    'briefcase': { icon: Briefcase, label: 'travail mallette' },
    'lightbulb': { icon: Lightbulb, label: 'idée ampoule projet' },
    'graduation-cap': { icon: GraduationCap, label: 'études école cours' },
    'book-open': { icon: BookOpen, label: 'livre lecture' },
    'shopping-cart': { icon: ShoppingCart, label: 'courses achats panier' },
    'star': { icon: Star, label: 'étoile favori' },
    'code': { icon: Code, label: 'code programmation dev' },
    'music': { icon: Music, label: 'musique' },
    'wallet': { icon: Wallet, label: 'argent budget portefeuille' },
    'gift': { icon: Gift, label: 'cadeau' },
    'coffee': { icon: Coffee, label: 'café pause' },
    'calendar': { icon: Calendar, label: 'calendrier agenda' },
}

/** Couleurs proposées pour une liste (celles de la maquette). */
export const LIST_COLORS = [
    '#E5484D', '#E8A33D', '#3FA66B', '#0E9F9A', '#2F6FEB', '#8E4EC6', '#D6409F', '#6B6B6B',
]
