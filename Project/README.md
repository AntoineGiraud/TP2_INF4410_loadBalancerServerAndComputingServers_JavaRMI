# TP2 INF4410 1761581

## Lancement / initialisation

### serverRepartiteur

Placez vous dans le répertoire racine du projet où vous retrouverez le fichier `serverRepartiteur`.

1. Compilez avec la commande `ant`
2. Démarrez le registre RMI dans le dossier bin de votre projet. Ajoutez un & pour le détacher de la console. commande par exemple : `cd bin && rmiregistry 5000 &`
On choisira dans ce TP d'avoir tous les registres RMI lancés sur le port 5000. Que ce soit sur la machine sur laquelle on lancera le serverRepartiteur que sur les machines sur lesquelles on fera tourner les serveurs de calcul.
3. Le serveur répartiteur vous permet d'effectuer trois commandes : `listServers`, `secureCompute`, `nonSecureCompute`.

#### Commandes disponibles :

* `./serverRepartiteur listServers`
  * Permet de lister les serveurs de calcul disponibles via Java RMI.
* `./serverRepartiteur secureCompute data_files/donnees-2317.txt`
  * Permet de lancer le calcul en mode sécurisé du fichier d'opération passé en commande.
  * Nécessite au moins un serveur de calcul de lancé sinon rien de s'exécutera.
  * Le mode "sécurisé" signifie que l'on considère que les serveurs de calcul ne font pas d'erreurs et retournent le bon résultat.
* `./serverRepartiteur nonSecureCompute data_files/donnees-2317.txt`
  * Permet de lancer le calcul en mode non sécurisé du fichier d'opération passé en commande.
  * Nécessite au moins trois serveur de calcul de lancé sinon rien de s'exécutera. En effet, on considère d'après l'énoncé du TP que l'on fait en sorte d'avoir strictement plus de 50% de serveurs honnêtes.
  * Le mode"non sécurisé" signifie que l'on considère que les serveurs de calcul peuvent commettre des erreurs. De ce fait, on validera un résultat si l'on a au moins deux résultats retournés par deux serveurs différents égaux. On considère qu'il y a strictement plus de 50% de serveurs honnêtes. Si non, on affiche un message pour demander à connecter plus de serveurs honnêtes.

#### Configuration serverRepartiteur :

Vous pouvez jouer sur deux paramètres concernant le serveur répartiteur. Exemple :

    RmiRegistryIpsToCheck=127.0.0.1;192.168.56.102
    tacheOperationsLoad=5

 où l'on a :

* `RmiRegistryIpsToCheck` : Comme présenté ci-dessus, `RmiRegistryIpsToCheck` est une chaine de caractère dans laquelle seront concaténé les adresses IP des différentes machines sur lesquelles vous faites tourner un serveur RMI sur le port 5000.
De nouveau, cela sera une norme prise dans ce TP, tout registre RMI lancé tournera sur le port 5000.
* `tacheOperationsLoad` : Les opérations du fichier seront réparties en paquets/tâches de cette taille. On peut jouer avec la taille de ceux-ci et constater la répercutions que cette taille a sur le temps d'exécution du calcul.

### serverCalcul

Après avoir configuré le serveur répartiteur, passons aux serveurs de calcul. Ceux-ci peuvent être lancés sur n'importe quelle machine unix ayant comme les ports 5000 à 5050 ouverts et supportant Java RMI.
Nous allons devoir lancer sur chaque machine sur lesquelles nous voudrons avoir un serveur de calcul un registre RMI lui aussi lancé sur le port 5000 depuis le dossier bin du projet.

Etapes pour lancer un serveur de calcul sur une machine :

 1.  Se placer dans le répertoire du projet que vous avez placé sur la machine voulue qui fera tourner un ou plusieurs serveurs de calcul.
 2. lancer le rmiregistery si ce n'est pas déjà le cas sur le port 5000 `cd bin && rmiregistry 5000 &`
 3. s'assurer d'avoir un répertoire dédié à votre nouveau serveur de calcul. Exemple : `serverCalcul3` qui contiendra au moins un fichier `serverCalcul`. Il est fait exprès de créer un dossier par serveur. En effet, chaque serveur générera un fichier de configuration si il n'est pas déjà présent. Il vous faudra vérifier que les paramètres sont bons.
 4. lancez une première fois le serveur de calcul avec la commande `./serverCalcul`. Cela aura pour effet de créer un fichier `serverCalcul.properties` si il n'existait pas déjà avec des valeurs par défaut.
 5. Configurez les paramètres de ce nouveau serveur depuis ce fichier `.properties`.
 6. Un fois les paramètres remplis, relancez le serveur de calcul pour prendre en compte les nouveaux paramètres.

#### Configuration serverRepartiteur :

Vous pouvez jouer sur deux paramètres concernant le serveur répartiteur. Exemple :

    malicious=50
    ipServerRMI=127.0.0.1
    portEcouteStubRMI=5002
    portServerRMI=5000
    thisServerIp=192.168.56.102
    quantiteRessources=2

 où l'on a :

* `malicious` : de 0 (honnête) à 100 (entièrement malicieux) : correspond à la fréquence à laquelle ce serveur retournera des résultats erronés.
Notez que même si un serveur est défini comme malicieux, il se comportera comme un serveur honnête lorsque l'on sera en mode sécurisé.
* `ipServerRMI` : IP du registre RMI auquel on veut enregistrer le serveur de Calcul.
Notez que maintenant, cela sera toujours l'adresse locale. Il est en effet pas possible d'enregistrer un objet sur un serveur RMI distant sans utiliser un raccourci non conseillé dans ce TP.
*  `portEcouteStubRMI` : Port d'écoute du stub de ce serveur en RMI. Ce port définira aussi le nom du serveur qui sera "server5002" ici. Faites donc attention à ne pas avoir plusieurs serveurs de calculs tournant avec le même port, id est le même nom...
* `portServerRMI` : désormais désactivé dans le code comme on s'est donné comme standard d'avoir tous les registres RMI en écoute sur le port 5000.
* `thisServerIp` : Important surtout en utilisant des VM. Permet de s'assurer que le registre RMI est configuré en écoutant sur la bonne IP.
*  `quantiteRessources` : confer article Simulation des ressources dans l'énoncé du TP. En bref, cela défini combien d'opération par tâche est ce que le serveur de calcul acceptera à coup sur. Sachant qu'il pourra accepter des tâches ayant jusque 10 fois (non compris) plus d'opérations que sa limite. 

## Plus d'informations

Pour plus de précisions, veuillez vous reporter au sujet de ce TP et au Compte Rendu fourni.

Vous y retrouverez deux screenshots de cas d'utilisation des méthodes de calcul en réparti en mode sécurisé puis non sécurisé.

Deux VM, tournant avec une debian 7.5 et configurées avec vagrant, ont été utilisées tout au long du développement :

*  VM1 : pour faire tourner le server Répartiteur
*  VM2 : pour faire tourner les serveurs de calculs

La machine, un ordinateur portable, utilisée disposait d'un Intel Core I5 CPU M 430 @ 2.27Ghz âgé maintenant de 4 années. De part son procésseur plus si à jour, les fichiers de donné on été simplifiés pour avoir des calculs moins importants et pour pouvoir faire les tests de charge en local sur cette même machine. Tous les fichiers de donnée contenant les listes d'opérations sont placés dans le dossier data_files.

Le projet a été développé en gardant à l'esprit les contraintes des ordinateurs du lab :

* ports limités entre 5000 et 5050
  * registres RMI tournant tous sur le port 5000
  * les serveurs de calculs écoute sur un port qui leur est propre. Ce même port servira d'identifiant : serveur 2 => serveur5002.
* Les serveurs de calcul sont lancé sur des machines différentes de celles où est éxécuté le serveur répartiteur.
  * Cela implique qu'il n'est pas possible pour un serveur de calcul distant de s'enregistrer sur le RMI local de la machine faisant tourner le serverRepartiteur.
  * On doit alors lancer un registre RMI sur le port 5000 sur chacunes des machines sur lesquelles nous lancerons des serveurs de calcul. Il est donc important de récupérer les IP de ces machines où tournent les registres RMI et serveurs de calcul pour les répertorier dans la configuration de serveur répartiteur dans la propriété RmiRegistryIpsToCheck.

### Auteur :

Antoine Giraud, 1761581