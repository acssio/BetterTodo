package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

// ApiClient appelle l'API BetterTodo avec un jeton d'accès personnel.
//
// Le serveur MCP est un client de l'API comme la page web ou les futures apps :
// il ne parle jamais à PostgreSQL.
type ApiClient struct {
	baseURL string // Ex. "https://todo.exemple.fr/api/v1"
	token   string // Jeton d'accès "btd_…" créé dans les Paramètres
	http    *http.Client
}

// NewApiClient crée le client. serverURL est l'adresse de la page web (nginx transmet /api au serveur API).
func NewApiClient(serverURL, token string) *ApiClient {
	return &ApiClient{
		baseURL: strings.TrimSuffix(serverURL, "/") + "/api/v1",
		token:   token,
		http:    &http.Client{Timeout: 15 * time.Second},
	}
}

// Get envoie un GET et décode la réponse JSON dans out.
func (c *ApiClient) Get(ctx context.Context, path string, out any) error {
	return c.Send(ctx, http.MethodGet, path, nil, out)
}

// Send envoie une requête (body est encodé en JSON s'il n'est pas nil) et décode la réponse dans out (si non nil).
// Si l'API répond avec une erreur, retourne son message : Claude le lit et peut corriger sa demande.
func (c *ApiClient) Send(ctx context.Context, method, path string, body any, out any) error {
	var reader *bytes.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return err
		}
		reader = bytes.NewReader(data)
	} else {
		reader = bytes.NewReader(nil)
	}

	req, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, reader)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("User-Agent", "bettertodo-mcp")
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	resp, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("impossible de joindre BetterTodo (%s) : %w", c.baseURL, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		var apiError struct {
			Error string `json:"error"`
		}
		json.NewDecoder(resp.Body).Decode(&apiError) // Corps vide ou pas du JSON : message vide
		if apiError.Error == "" {
			apiError.Error = resp.Status
		}
		return fmt.Errorf("BetterTodo a répondu %d : %s", resp.StatusCode, apiError.Error)
	}

	if out == nil || resp.StatusCode == http.StatusNoContent {
		return nil
	}
	return json.NewDecoder(resp.Body).Decode(out)
}
