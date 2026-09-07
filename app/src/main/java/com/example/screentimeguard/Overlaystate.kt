package com.example.screentimeguard

/**
 * État partagé de l'overlay (InterstitialActivity / BlockActivity).
 *
 * Avant, ce champ vivait uniquement dans AppMonitorService et était remis
 * à null dès qu'un évènement MOVE_TO_FOREGROUND correspondant était vu.
 * Problème : Android peut générer un blip BACKGROUND/FOREGROUND transitoire
 * pour l'app surveillée pendant l'ANIMATION D'OUVERTURE de notre propre
 * overlay, avant même que l'utilisateur ait vu l'écran. Ce blip était
 * interprété à tort comme "l'utilisateur est revenu, overlay fermé",
 * ce qui cassait l'état (fin de session prématurée, interstitiel réaffiché
 * une deuxième fois après que l'utilisateur ait répondu).
 *
 * Maintenant : seule l'activité overlay elle-même (dans onDestroy) a le
 * droit de remettre ce champ à null, car elle seule sait avec certitude
 * quand elle a vraiment fini d'être affichée.
 */
object OverlayState {

    /**
     * Package pour lequel un overlay est ACTUELLEMENT visible.
     *
     * Cette valeur doit être définie par l'Activity dans onStart()
     * et supprimée dans onStop().
     */
    @Volatile
    var activeFor: String? = null

    /**
     * Package pour lequel l'utilisateur a réellement validé
     * l'interstitiel pendant la session actuelle.
     *
     * null = aucune application validée.
     */
    @Volatile
    var completedFor: String? = null
}