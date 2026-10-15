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
    fr.cerelios.urne.ServeurUrne 5000 comptes.txt localhost:5000
```

`comptes.txt` contient une ligne `identifiant:email:motDePasse` par compte. Il alimente le fournisseur d'identité local (`IdentitesLocales`), qui tient lieu d'IdP en attendant OIDC ou CAS. Les e-mails ne partent pas : `MailJournal` les écrit dans le journal. Le dernier argument, facultatif, est l'adresse où les autres programmes joignent l'urne ; le serveur de credentials en a besoin pour savoir qui n'a pas voté.

Le serveur de credentials se lance de la même façon, avec `fr.cerelios.certification.ServeurCertification <port>` et `certification/target/classes` dans le classpath.

## Déroulement d'un référendum

1. L'administrateur crée le brouillon, fixe la question, les électeurs et les dates d'ouverture, de clôture et de publication, puis inscrit les scrutateurs, qui reçoivent leur jeton par e-mail.
2. Les scrutateurs génèrent la clé :
   - sans seuil, chacun la sienne (`Scrutateur.genererCle`), et tous devront déchiffrer ;
   - avec un seuil k, ensemble, en rappelant `Scrutateur.avancerPartage` jusqu'à l'étape `TERMINE` ; k d'entre eux suffiront à déchiffrer.
3. Les credentials sont générés, au choix :
   - par une autorité de certification manuelle (`AutoriteCertification.generer`), qui les remet à chaque électeur hors de l'urne ;
   - par le serveur de credentials, à la demande de l'urne. Il envoie la graine à l'opérateur, puis les credentials aux électeurs à la validation. Avec la graine, l'opérateur vérifie le certificat publié et fait renvoyer un credential perdu (`OperateurCredentials`).
4. L'administrateur valide, et peut sceller le calendrier.
5. Rien n'est ensuite manuel : à chaque lecture, l'urne ouvre, clôt, calcule le total chiffré après le délai de grâce et publie le résultat une fois la date de publication passée et assez de déchiffrements partiels reçus (`Scrutateur.dechiffrer`).
6. N'importe qui peut tout revérifier avec `Verificateur`.

## Messages réseau

Les messages échangés par socket sont décrits en [Protocol Buffers](https://protobuf.dev) dans `protocole/src/main/protobuf/`. Les classes Java correspondantes sont générées à la compilation dans `protocole/target/generated-sources/` et ne sont pas versionnées.

## Organisation du dépôt

| Dossier          | Contenu                                                                   |
| ---------------- | ------------------------------------------------------------------------- |
| `crypto/`        | Elgamal homomorphe, preuves à divulgation nulle, bulletin signé           |
| `protocole/`     | Messages échangés entre les programmes et leur sérialisation              |
| `urne/`          | Serveur : collecte des bulletins, agrégation, clôture, publication        |
| `electeur/`      | Client de vote                                                            |
| `gui/`           | Interface JavaFX : connexion et choix du référendum                       |
| `scrutateur/`    | Génération de la clé et déchiffrement du résultat                         |
| `autorite/`      | Autorité de certification manuelle et opérateur du serveur de credentials |
| `certification/` | Serveur de credentials : génère, garde chiffrés et envoie les credentials |
| `attaques/`      | Démonstrations des attaques et de leurs parades, une classe par attaque   |
| `docs/`          | Analyse, conception (schémas d'architecture) et suivi de projet           |

### Dépendances entre modules

```
electeur ──────┐
urne ──────────┤
scrutateur ────┼──> protocole ──> crypto
autorite ──────┤
certification ─┘

attaques ──> urne, electeur, scrutateur

gui        (JavaFX, ne dépend encore d'aucun module)
```

- `crypto` ne dépend que du JDK à l'exécution (les annotations JetBrains, communes à tous les modules, ne servent qu'à la compilation).
- La clé secrète ne quitte jamais `scrutateur`. Un credential secret n'est connu que de son électeur et de qui l'a généré (`autorite`, ou `certification` qui ne le garde que chiffré) : jamais de l'urne.
- Les tests de `urne` utilisent `electeur`, `scrutateur`, `autorite` et `certification` pour des référendums complets par socket.
- Aucun module ne dépend de `attaques`.

## Méthode de travail

- `main` est protégée : on n'y pousse pas directement.
- Une branche par user story, nommée d'après elle : `us-22-encrypt`.
- Fusion par merge request, relue par un autre membre de l'équipe, pipeline au vert.
