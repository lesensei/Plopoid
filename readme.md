# Plopoid est un coincoin

Ben oui, Plopoid, c'est un coincoin, soit un truc qui permet de partager des "coins ?", "pan !" et autres messages antipapistes sur les tribunes de par le web. Sa particularit�, c'est d'�tre �crit pour Android.

## Limitations & Fonctionnalit�s (un peu)

* Ne fonctionne pour l'instant que sous ICS (peut-�tre sous Honeycomb, pas test�)
* Ne permet pas les posts anonymes
* Pas encore de gestion des horloges (pas de highlight des posts auxquels un post donn� r�pond)
* Pour le reste, c'est du classique:
  * Affichage des tribunes par onglet
  * Un "clic" sur l'horloge d'un post l'ajoute � la fin du palmip�de

## �a fonctionne comment ?

Plopoid ex�cute toutes ses requ�tes sur un serveur olccs.

On notera que la r�cup�ration des posts se fait dans un service, ce qui a peu de valeur ajout�e actuellement (et donc on aura tendance � stopper le service � la sortie), mais qui permettra plus tard d'avoir un bigorno-like.

## Alors oui, justement, c'est quoi les futures �tapes

D'abord, de faire en sorte que �a juste marche.

Ensuite, et pas dans l'ordre:
* Possibilit� de poster en anonyme
* Une compatibilit� � partir d'Android 2.1 ?
* Les notifications (bigornophone)
* La colorisation des posts (de l'utilisateur, des r�ponses � l'utilisateur, etc.)
* Le highlight des posts auxquels un post donn� r�pond
* Une traduction des quelques bouts de texte, puisque c'est avant tout une appli pour francophones (mais je voulais tester l'internationalisation sur Android)

## �a se chope o� ?

Pas sur le market en tout cas. Il y a une page de t�l�chargement sur le wiki, pour les glandeurs qui veulent pas compiler (et donc pas contribuer, hein, bande de profiteurs !)