# language: fr
Fonctionnalité: Email de confirmation de commande
  En tant que coopérateur,
  Je veux recevoir par email le récapitulatif de ma commande,
  Afin de savoir ce que j'ai commandé, combien je paierai et quand venir la retirer.

  Scénario: Un email récapitulatif est envoyé via Brevo à la confirmation de la commande
    Étant donné qu'une vente existe avec un produit ayant un stock de 10
    Quand "claire@exemple.fr" passe une commande de 2 unités du produit
    Alors un email de confirmation est envoyé via Brevo à "claire@exemple.fr"
    Et l'email reprend le numéro de commande, le produit commandé, le total et le créneau de retrait

  Scénario: Une commande rejouée avec la même clé d'idempotence n'envoie qu'un seul email
    Étant donné qu'une vente existe avec un produit ayant un stock de 10
    Quand "paul@exemple.fr" passe deux fois la même commande avec la même clé d'idempotence
    Alors un seul email de confirmation est envoyé à "paul@exemple.fr"

  Scénario: Aucun email n'est envoyé si la commande échoue
    Étant donné qu'une vente existe avec un produit ayant un stock de 1
    Quand "luc@exemple.fr" tente de commander 5 unités du produit
    Alors aucun email de confirmation n'est envoyé à "luc@exemple.fr"
