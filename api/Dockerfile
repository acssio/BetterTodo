# Image du serveur API BetterTodo, construite en deux temps.

# 1) Compilation : image Go complète (~300 Mo), utilisée seulement pendant le build
FROM golang:1.27-alpine AS build
WORKDIR /src

# Dépendances d'abord : Docker garde cette couche en cache tant que go.mod/go.sum ne changent pas
COPY go.mod go.sum ./
RUN go mod download

COPY . .
# CGO_ENABLED=0 : binaire 100 % Go, sans dépendance au système → tourne sur une image vide
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /bettertodo-api ./cmd/server

# 2) Exécution : image minimale (pas de shell, pas de gestionnaire de paquets), utilisateur non-root
FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /bettertodo-api /bettertodo-api
EXPOSE 8080
ENTRYPOINT ["/bettertodo-api"]
