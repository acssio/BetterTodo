# Image de la page web BetterTodo, construite en deux temps.

# 1) Compilation : Node installe les dépendances et produit les fichiers dans dist/
FROM node:24-alpine AS build
WORKDIR /app

# Dépendances d'abord : couche gardée en cache tant que package*.json ne change pas
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# 2) Service : nginx seul, avec les fichiers compilés (pas de Node en production)
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
