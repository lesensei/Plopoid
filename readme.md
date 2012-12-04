# Plopoid est un coincoin

Ben oui, Plopoid, c'est un coincoin, soit un truc qui permet de partager des "coins ?", "pan !" et autres messages antipapistes sur les tribunes de par le web. Sa particularité, c'est d'être écrit pour Android.

## Limitations & Fonctionnalités (un peu)

* Ne permet pas les posts anonymes
* Pas encore de gestion des horloges (pas de highlight des posts auxquels un post donné répond, pas de gestion des posts multiples à la même seconde, etc.)
* Pour le reste, c'est du classique:
  * Affichage des tribunes par onglet
  * Un "clic" sur l'horloge d'un post l'ajoute à la fin du palmipède

## Bugs connus

Voir la [liste des bugs](https://github.com/lesensei/Plopoid/issues). Vous pouvez aussi en ajouter.

## Ça fonctionne comment ?

Plopoid exécute toutes ses requêtes sur un serveur olccs.

On notera que la récupération des posts se fait dans un service, ce qui permet aux gens qui veulent gâcher leur forfait data de bénéficier d'un bigornophone (fort primitif, pour le moment). Il faut pour cela activer l'option "sticky service" dans la conf.

## Alors oui, justement, c'est quoi les futures étapes

D'abord, de faire en sorte que ça juste marche.

Ensuite, et pas dans l'ordre:

* Possibilité de poster en anonyme
* Une vraie gestion des horloges
* Un bigornophone amélioré (ouverture de la tribune invocatrice, onglet qui clignote quand l'appli est ouverte, etc.)
* La colorisation des posts (de l'utilisateur, des réponses à l'utilisateur, etc.)
* Une traduction des quelques bouts de texte, puisque c'est avant tout une appli pour francophones (mais je voulais tester l'internationalisation sur Android)

## Ça se chope où ?

Pas sur le market en tout cas. Il y a une [page de téléchargement](https://github.com/lesensei/Plopoid/downloads) sur github, pour les glandeurs qui veulent pas compiler (et donc pas contribuer, hein, bande de profiteurs !)