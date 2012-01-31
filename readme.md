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

On notera que la récupération des posts se fait dans un service, ce qui a peu de valeur ajoutée actuellement (et donc on aura tendance à stopper le service à la sortie), mais qui permettra plus tard d'avoir un bigorno-like.

## Alors oui, justement, c'est quoi les futures étapes

D'abord, de faire en sorte que ça juste marche.

Ensuite, et pas dans l'ordre:

* Possibilité de poster en anonyme
* Une vraie gestion des horloges
* Les notifications (bigornophone)
* La colorisation des posts (de l'utilisateur, des réponses à l'utilisateur, etc.)
* Une traduction des quelques bouts de texte, puisque c'est avant tout une appli pour francophones (mais je voulais tester l'internationalisation sur Android)

## Ça se chope où ?

Pas sur le market en tout cas. Il y a une [page de téléchargement](https://github.com/lesensei/Plopoid/wiki/Download) sur le wiki, pour les glandeurs qui veulent pas compiler (et donc pas contribuer, hein, bande de profiteurs !)