import {
    BookOpen, Briefcase, Calendar, Code, Coffee, Gift, GraduationCap, Heart, House,
    Lightbulb, List, Music, ShoppingCart, Star, User, Wallet,
    type LucideIcon,
} from 'lucide-react'

/**
 * Icônes Lucide proposées pour les listes (les mêmes que dans la maquette).
 * La clé est le nom stocké en base (colonne lists.icon).
 *
 * On liste les icônes une par une plutôt que d'importer toute la bibliothèque :
 * le fichier JavaScript final ne contient que celles-ci.
 */
export const LIST_ICONS: Record<string, LucideIcon> = {
    'list': List,
    'user': User,
    'house': House,
    'heart': Heart,
    'briefcase': Briefcase,
    'lightbulb': Lightbulb,
    'graduation-cap': GraduationCap,
    'book-open': BookOpen,
    'shopping-cart': ShoppingCart,
    'star': Star,
    'code': Code,
    'music': Music,
    'wallet': Wallet,
    'gift': Gift,
    'coffee': Coffee,
    'calendar': Calendar,
}
