// Package migrations contient les fichiers SQL de migration.
//
// La directive go:embed les inclut dans le binaire au moment de la compilation :
// le serveur n'a besoin d'aucun fichier à côté de lui pour migrer la base.
package migrations

import "embed"

//go:embed *.sql
var Files embed.FS
