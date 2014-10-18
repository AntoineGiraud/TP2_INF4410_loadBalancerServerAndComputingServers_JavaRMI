# TP2 INF4410 1761581-

1. Compilez avec la commande `ant`
2. Démarrez le registre RMI avec la commande `rmiregistry` à partir du dossier bin de votre projet. Ajoutez un & pour le détacher de la console.
3. Démarrez le serveur avec le script serverCalcul (`./serverCalcul` ou bash serverCalcul).
4. dans une nouvelle console, déplacez vous dans le répertoire `serverRepartiteur1` et exécutez la commande `./serverRepartiteur XX YY`voulue
5. réalisez de même que 4. dans le répertoire `serverRepartiteur2` pour contrôller un deuxième serverRepartiteur au serveur.

## Commandes disponibles :

* `./serverRepartiteur list`
  * Permet d'afficher la liste des fichiers présents sur le serveur de fichier
* `./serverRepartiteur create monSuperFichier`
  * Permet de créer le fichier monSuperFichier sur le serveur. Il sera récupéré dans la foulée sur le répertoire du serverRepartiteur
* `./serverRepartiteur get    monSuperFichier`
  * récupère le fichier monSuperFichier si présent sur le serveur
* `./serverRepartiteur lock   monSuperFichier`
  * vérouille le fichier monSuperFichier à ce serverRepartiteur pour qu'il puisse l'éditer et le mettre à jour sans crainte de voir sa MAJ écrasée.
* `./serverRepartiteur push   monSuperFichier`
  * Envoie la mise à jour au serveur et libère le fichier

Pour plus de précisions, veuillez vous reporter au sujet de ce TP et au Compte Rendu fourni. Vous y retrouverez des screenshots de deux cas : un cas simple avec un seul serverRepartiteur et un cas exposant les conflits entre deux serverRepartiteurs connectés à la fois sur le serveur et le bon fonctionnement de celui-ci.