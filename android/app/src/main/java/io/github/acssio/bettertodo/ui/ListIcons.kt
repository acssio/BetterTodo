package io.github.acssio.bettertodo.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.composables.icons.lucide.R // Icônes de la bibliothèque icons-lucide-android

/**
 * Icônes et couleurs des listes, comme web/src/components/listIcons.ts.
 *
 * La clé est le nom stocké en base (colonne lists.icon) ; la valeur est l'icône Lucide
 * fournie par la bibliothèque icons-lucide-android (un drawable par icône : R.drawable.lucide_ic_xxx).
 * La liste doit rester identique à celle du site : ListIconsTest le vérifie.
 * Pour en ajouter une : l'ajouter d'abord dans listIcons.ts (et mcp/palette.go), puis ici.
 */
object ListIcons {

    private val icons: Map<String, Int> = mapOf(
        // --- Général ---
        "list" to R.drawable.lucide_ic_list,
        "list-checks" to R.drawable.lucide_ic_list_checks,
        "check" to R.drawable.lucide_ic_check,
        "star" to R.drawable.lucide_ic_star,
        "flag" to R.drawable.lucide_ic_flag,
        "bookmark" to R.drawable.lucide_ic_bookmark,
        "inbox" to R.drawable.lucide_ic_inbox,
        "archive" to R.drawable.lucide_ic_archive,
        "pin" to R.drawable.lucide_ic_pin,
        "zap" to R.drawable.lucide_ic_zap,
        "target" to R.drawable.lucide_ic_target,
        "trophy" to R.drawable.lucide_ic_trophy,
        "rocket" to R.drawable.lucide_ic_rocket,
        "sparkles" to R.drawable.lucide_ic_sparkles,
        "bell" to R.drawable.lucide_ic_bell,
        "clock" to R.drawable.lucide_ic_clock,
        "calendar" to R.drawable.lucide_ic_calendar,
        "hourglass" to R.drawable.lucide_ic_hourglass,
        "tag" to R.drawable.lucide_ic_tag,
        "folder" to R.drawable.lucide_ic_folder,
        // --- Personnel ---
        "user" to R.drawable.lucide_ic_user,
        "users" to R.drawable.lucide_ic_users,
        "baby" to R.drawable.lucide_ic_baby,
        "heart" to R.drawable.lucide_ic_heart,
        "smile" to R.drawable.lucide_ic_smile,
        "gift" to R.drawable.lucide_ic_gift,
        "cake" to R.drawable.lucide_ic_cake,
        "party-popper" to R.drawable.lucide_ic_party_popper,
        "dog" to R.drawable.lucide_ic_dog,
        "cat" to R.drawable.lucide_ic_cat,
        "paw-print" to R.drawable.lucide_ic_paw_print,
        "flower-2" to R.drawable.lucide_ic_flower_2,
        // --- Maison ---
        "house" to R.drawable.lucide_ic_house,
        "sofa" to R.drawable.lucide_ic_sofa,
        "bed" to R.drawable.lucide_ic_bed,
        "bath" to R.drawable.lucide_ic_bath,
        "lamp" to R.drawable.lucide_ic_lamp,
        "key" to R.drawable.lucide_ic_key,
        "wrench" to R.drawable.lucide_ic_wrench,
        "hammer" to R.drawable.lucide_ic_hammer,
        "paintbrush" to R.drawable.lucide_ic_paintbrush,
        "shovel" to R.drawable.lucide_ic_shovel,
        "sprout" to R.drawable.lucide_ic_sprout,
        "trash-2" to R.drawable.lucide_ic_trash_2,
        "washing-machine" to R.drawable.lucide_ic_washing_machine,
        "plug" to R.drawable.lucide_ic_plug,
        // --- Travail et études ---
        "briefcase" to R.drawable.lucide_ic_briefcase,
        "building-2" to R.drawable.lucide_ic_building_2,
        "presentation" to R.drawable.lucide_ic_presentation,
        "chart-line" to R.drawable.lucide_ic_chart_line,
        "chart-pie" to R.drawable.lucide_ic_chart_pie,
        "file-text" to R.drawable.lucide_ic_file_text,
        "clipboard-list" to R.drawable.lucide_ic_clipboard_list,
        "mail" to R.drawable.lucide_ic_mail,
        "phone" to R.drawable.lucide_ic_phone,
        "printer" to R.drawable.lucide_ic_printer,
        "lightbulb" to R.drawable.lucide_ic_lightbulb,
        "graduation-cap" to R.drawable.lucide_ic_graduation_cap,
        "book-open" to R.drawable.lucide_ic_book_open,
        "library" to R.drawable.lucide_ic_library,
        "pencil" to R.drawable.lucide_ic_pencil,
        "notebook-pen" to R.drawable.lucide_ic_notebook_pen,
        "languages" to R.drawable.lucide_ic_languages,
        "calculator" to R.drawable.lucide_ic_calculator,
        // --- Tech ---
        "code" to R.drawable.lucide_ic_code,
        "terminal" to R.drawable.lucide_ic_terminal,
        "bug" to R.drawable.lucide_ic_bug,
        "git-branch" to R.drawable.lucide_ic_git_branch,
        "server" to R.drawable.lucide_ic_server,
        "database" to R.drawable.lucide_ic_database,
        "cpu" to R.drawable.lucide_ic_cpu,
        "laptop" to R.drawable.lucide_ic_laptop,
        "monitor" to R.drawable.lucide_ic_monitor,
        "smartphone" to R.drawable.lucide_ic_smartphone,
        "wifi" to R.drawable.lucide_ic_wifi,
        "cloud" to R.drawable.lucide_ic_cloud,
        "shield" to R.drawable.lucide_ic_shield,
        "settings" to R.drawable.lucide_ic_settings,
        // --- Argent et courses ---
        "wallet" to R.drawable.lucide_ic_wallet,
        "piggy-bank" to R.drawable.lucide_ic_piggy_bank,
        "credit-card" to R.drawable.lucide_ic_credit_card,
        "banknote" to R.drawable.lucide_ic_banknote,
        "receipt" to R.drawable.lucide_ic_receipt,
        "landmark" to R.drawable.lucide_ic_landmark,
        "shopping-cart" to R.drawable.lucide_ic_shopping_cart,
        "shopping-bag" to R.drawable.lucide_ic_shopping_bag,
        "store" to R.drawable.lucide_ic_store,
        "package" to R.drawable.lucide_ic_package,
        // --- Santé et sport ---
        "heart-pulse" to R.drawable.lucide_ic_heart_pulse,
        "pill" to R.drawable.lucide_ic_pill,
        "stethoscope" to R.drawable.lucide_ic_stethoscope,
        "activity" to R.drawable.lucide_ic_activity,
        "dumbbell" to R.drawable.lucide_ic_dumbbell,
        "bike" to R.drawable.lucide_ic_bike,
        "footprints" to R.drawable.lucide_ic_footprints,
        "volleyball" to R.drawable.lucide_ic_volleyball,
        "brain" to R.drawable.lucide_ic_brain,
        "apple" to R.drawable.lucide_ic_apple,
        // --- Cuisine ---
        "utensils" to R.drawable.lucide_ic_utensils,
        "chef-hat" to R.drawable.lucide_ic_chef_hat,
        "cooking-pot" to R.drawable.lucide_ic_cooking_pot,
        "coffee" to R.drawable.lucide_ic_coffee,
        "pizza" to R.drawable.lucide_ic_pizza,
        "wine" to R.drawable.lucide_ic_wine,
        "beer" to R.drawable.lucide_ic_beer,
        "carrot" to R.drawable.lucide_ic_carrot,
        // --- Voyage et transport ---
        "plane" to R.drawable.lucide_ic_plane,
        "car" to R.drawable.lucide_ic_car,
        "train-front" to R.drawable.lucide_ic_train_front,
        "bus" to R.drawable.lucide_ic_bus,
        "ship" to R.drawable.lucide_ic_ship,
        "fuel" to R.drawable.lucide_ic_fuel,
        "map" to R.drawable.lucide_ic_map,
        "map-pin" to R.drawable.lucide_ic_map_pin,
        "compass" to R.drawable.lucide_ic_compass,
        "globe" to R.drawable.lucide_ic_globe,
        "luggage" to R.drawable.lucide_ic_luggage,
        "tent" to R.drawable.lucide_ic_tent,
        "mountain" to R.drawable.lucide_ic_mountain,
        "palmtree" to R.drawable.lucide_ic_tree_palm, // « tree-palm » dans les dernières versions de Lucide
        "sun" to R.drawable.lucide_ic_sun,
        "umbrella" to R.drawable.lucide_ic_umbrella,
        // --- Loisirs ---
        "music" to R.drawable.lucide_ic_music,
        "headphones" to R.drawable.lucide_ic_headphones,
        "guitar" to R.drawable.lucide_ic_guitar,
        "film" to R.drawable.lucide_ic_film,
        "tv" to R.drawable.lucide_ic_tv,
        "gamepad-2" to R.drawable.lucide_ic_gamepad_2,
        "puzzle" to R.drawable.lucide_ic_puzzle,
        "camera" to R.drawable.lucide_ic_camera,
        "palette" to R.drawable.lucide_ic_palette,
        "brush" to R.drawable.lucide_ic_brush,
        "scissors" to R.drawable.lucide_ic_scissors,
        "book" to R.drawable.lucide_ic_book,
        "newspaper" to R.drawable.lucide_ic_newspaper,
        "ticket" to R.drawable.lucide_ic_ticket,
    )

    /** Noms de toutes les icônes proposées (pour le test). */
    val names: Set<String> get() = icons.keys

    /** L'icône d'une liste. Une icône inconnue devient l'icône "list", comme sur le site. */
    @DrawableRes
    fun drawable(name: String): Int {
        return icons[name] ?: R.drawable.lucide_ic_list
    }

    /** "#2F6FEB" → Color. Une couleur illisible devient le gris de la palette. */
    fun color(hex: String): Color {
        val digits = hex.removePrefix("#")
        val rgb = if (digits.length == 6) digits.toLongOrNull(16) else null
        return Color(0xFF000000 or (rgb ?: 0x6B6B6B))
    }
}
