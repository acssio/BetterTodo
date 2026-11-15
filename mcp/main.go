// Serveur MCP de BetterTodo : donne à Claude des outils pour lire et modifier tes tâches.
//
// Claude (Claude Code, Claude Desktop) lance ce programme et lui parle par l'entrée et la
// sortie standard (transport « stdio »). Le programme appelle ensuite l'API BetterTodo
// avec un jeton d'accès personnel, comme le ferait n'importe quel client.
//
// Configuration (variables d'environnement) :
//
//	BETTERTODO_URL   : adresse de la page web, ex. https://todo.exemple.fr (sans /api)
//	BETTERTODO_TOKEN : jeton d'accès "btd_…", créé dans Paramètres → Jetons d'accès
//
// Attention : la sortie standard est réservée au protocole MCP. Les messages pour
// l'humain (log) vont sur la sortie d'erreur.
package main

import (
	"context"
	"log"
	"os"
	"strings"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

// version : affichée à Claude à la connexion
const version = "0.1.0"

func main() {
	log.SetOutput(os.Stderr)
	log.SetPrefix("bettertodo-mcp : ")

	serverURL := os.Getenv("BETTERTODO_URL")
	token := os.Getenv("BETTERTODO_TOKEN")
	if serverURL == "" || token == "" {
		log.Fatal("BETTERTODO_URL et BETTERTODO_TOKEN sont obligatoires (voir docs/etapes/04-integration-mcp.md)")
	}
	if !strings.HasPrefix(token, "btd_") {
		log.Fatal("BETTERTODO_TOKEN doit être un jeton d'accès (il commence par btd_)")
	}

	server := mcp.NewServer(&mcp.Implementation{Name: "bettertodo", Title: "BetterTodo", Version: version}, nil)
	NewTools(NewApiClient(serverURL, token)).Register(server)

	// Tourne jusqu'à ce que Claude ferme la connexion
	if err := server.Run(context.Background(), &mcp.StdioTransport{}); err != nil {
		log.Fatal(err)
	}
}
