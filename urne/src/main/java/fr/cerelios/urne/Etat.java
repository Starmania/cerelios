package fr.cerelios.urne;

/** Étapes de la vie d'un référendum, dans l'ordre. */
public enum Etat {
    /** En préparation par l'administrateur. */
    BROUILLON,
    /** Figé, en attente de la date d'ouverture. */
    VALIDE,
    /** Les électeurs votent. */
    OUVERT,
    /** Fermé ; seuls les électeurs connectés avant la clôture finissent de voter. */
    CLOS,
    /** Total chiffré calculé, en attente des déchiffrements partiels. */
    DEPOUILLEMENT,
    /** Résultat publié. */
    PUBLIE,
    /** Données personnelles effacées, résultat conservé. */
    ARCHIVE
}
