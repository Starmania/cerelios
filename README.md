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

## Lancer l'interface

```sh
./mvnw -pl gui javafx:run
```

Depuis l'IDE ou avec `java -cp`, lancer `fr.cerelios.gui.Launcher` et non `MainApp` : une classe qui étend `Application` exige JavaFX en modules.

## Lancer l'urne

```sh
./mvnw -pl urne -am package -DskipTests
java -cp "urne/target/classes:protocole/target/classes:crypto/target/classes:$HOME/.m2/repository/com/google/protobuf/protobuf-java/4.36.2/protobuf-java-4.36.2.jar" \
    fr.cerelios.urne.ServeurUrne 5000 comptes.txt
```

`comptes.txt` contient une ligne `identifiant:email:motDePasse` par compte. Il alimente le fournisseur d'identité local (`IdentitesLocales`), qui tient lieu d'IdP en attendant OIDC ou CAS. Les e-mails ne partent pas : `MailJournal` les écrit dans le journal.

## Déroulement d'un référendum

1. L'administrateur crée le brouillon, fixe la question, les électeurs et les dates d'ouverture, de clôture et de publication, puis inscrit les scrutateurs, qui reçoivent leur jeton par e-mail.
2. Chaque scrutateur génère sa part de clé (`Scrutateur.genererCle`) ; l'autorité de certification génère un credential par électeur (`AutoriteCertification.generer`) et le remet à chacun hors de l'urne.
3. L'administrateur valide, et peut sceller le calendrier.
4. Rien n'est ensuite manuel : à chaque lecture, l'urne ouvre, clôt, calcule le total chiffré après le délai de grâce et publie le résultat une fois la date de publication passée et tous les déchiffrements partiels reçus (`Scrutateur.dechiffrer`).
5. N'importe qui peut tout revérifier avec `Verificateur`.

## Messages réseau

Les messages échangés par socket sont décrits en [Protocol Buffers](https://protobuf.dev) dans `protocole/src/main/protobuf/`. Les classes Java correspondantes sont générées à la compilation dans `protocole/target/generated-sources/` et ne sont pas versionnées.

## Organisation du dépôt

| Dossier       | Contenu                                                                    |
| ------------- | -------------------------------------------------------------------------- |
| `crypto/`     | Elgamal homomorphe, preuves à divulgation nulle, bulletin signé            |
| `protocole/`  | Messages échangés entre les programmes et leur sérialisation               |
| `urne/`       | Serveur : collecte des bulletins, agrégation, clôture, publication         |
| `electeur/`   | Client de vote                                                             |
| `gui/`        | Interface JavaFX : connexion et choix du référendum                        |
| `scrutateur/` | Génération de la clé et déchiffrement du résultat                          |
| `autorite/`   | Autorité de certification : génère les credentials des électeurs           |
| `attaques/`   | Démonstrations des attaques et de leurs parades, une classe par attaque    |
| `docs/`       | Analyse, conception (schémas d'architecture) et suivi de projet            |

### Dépendances entre modules

```
electeur ───┐
urne ───────┤
scrutateur ─┼──> protocole ──> crypto
autorite ───┘

attaques ──> urne, electeur, scrutateur

gui        (JavaFX, ne dépend encore d'aucun module)
```

- `crypto` ne dépend que du JDK à l'exécution (les annotations JetBrains, communes à tous les modules, ne servent qu'à la compilation).
- La clé secrète ne quitte jamais `scrutateur`, ni un credential secret `autorite` et le poste de son électeur.
- Les tests de `urne` utilisent `electeur`, `scrutateur` et `autorite` pour un référendum complet par socket.
- Aucun module ne dépend de `attaques`.

## Méthode de travail

- `main` est protégée : on n'y pousse pas directement.
- Une branche par user story, nommée d'après elle : `us-22-encrypt`.
- Fusion par merge request, relue par un autre membre de l'équipe, pipeline au vert.
