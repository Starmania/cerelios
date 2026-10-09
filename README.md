# Cerelios

Application client-serveur de vote électronique en Java — SAÉ S3.B.01, BUT Informatique, IUT Montpellier-Sète.

## Prérequis

- JDK 21 ou plus récent

Maven n'a pas besoin d'être installé : le script `mvnw` télécharge la bonne version au premier lancement.

## Compiler et tester

```sh
./mvnw verify                 # tout le projet
./mvnw -pl crypto test        # un seul module
./mvnw -pl protocole -am test # un module et ceux dont il dépend
```

Utiliser `./mvnw` plutôt que `mvn` : le Maven des paquets Debian et Ubuntu fait échouer la génération protobuf.

## Messages réseau

Les messages échangés par socket sont décrits en [Protocol Buffers](https://protobuf.dev) dans `protocole/src/main/protobuf/`. Les classes Java correspondantes sont générées à la compilation dans `protocole/target/generated-sources/` et ne sont pas versionnées.

## Organisation du dépôt

| Dossier       | Contenu                                                                    |
| ------------- | -------------------------------------------------------------------------- |
| `crypto/`     | Chiffrement Elgamal homomorphe : KeyGen, Encrypt, Agrege, Decrypt          |
| `protocole/`  | Messages échangés entre les programmes et leur sérialisation               |
| `urne/`       | Serveur : collecte des bulletins, agrégation, clôture, publication         |
| `electeur/`   | Client de vote                                                             |
| `scrutateur/` | Génération de la clé et déchiffrement du résultat                          |
| `attaques/`   | Démonstrations des attaques et de leurs parades, une classe par attaque    |
| `docs/`       | Analyse, conception (schémas d'architecture) et suivi de projet            |

### Dépendances entre modules

```
electeur ───┐
urne ───────┼──> protocole ──> crypto
scrutateur ─┘

attaques ──> urne, electeur, scrutateur
```

- `crypto` ne dépend que du JDK à l'exécution (les annotations JetBrains, communes à tous les modules, ne servent qu'à la compilation).
- La clé secrète ne quitte jamais `scrutateur`.
- Aucun module ne dépend de `attaques`.

## Méthode de travail

- `main` est protégée : on n'y pousse pas directement.
- Une branche par user story, nommée d'après elle : `us-22-encrypt`.
- Fusion par merge request, relue par un autre membre de l'équipe, pipeline au vert.
