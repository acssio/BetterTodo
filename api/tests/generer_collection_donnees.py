"""
Génère la collection Postman des données (étape 2b, complétée en 3c-2b).

Usage, depuis la racine du projet :
    python3 api/tests/generer_collection_donnees.py api/tests/bettertodo-api-donnees.postman_collection.json

Chaque appel à req() ajoute une requête : nom, méthode, chemin, compte (token ou tokenB),
corps JSON, et les lignes de test JavaScript exécutées par Postman sur la réponse.
"""
import json, sys

items = []


def req(name, method, path, token="token", body=None, tests=(), pre=()):
    request = {"method": method, "url": "{{baseUrl}}" + path, "header": []}
    if token:
        request["header"].append({"key": "Authorization", "value": "Bearer {{" + token + "}}"})
    if body is not None:
        request["header"].append({"key": "Content-Type", "value": "application/json"})
        request["body"] = {"mode": "raw", "raw": json.dumps(body, ensure_ascii=False, indent=2)}
    events = [{"listen": "test", "script": {"type": "text/javascript", "exec": list(tests)}}]
    if pre:
        events.append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": list(pre)}})
    items.append({"name": f"{len(items) + 1:02d} {name}", "request": request, "event": events})


def status(code, label=None):
    return f"pm.test('{code}{(' ' + label) if label else ''}', () => pm.response.to.have.status({code}));"


J = "const b = pm.response.json();"

# --- Préparation : deux comptes ------------------------------------------------
for who, var in (("A", "token"), ("B", "tokenB")):
    req(f"Compte {who} – inscription", "POST", "/auth/register", token=None,
        body={"email": "{{email" + who + "}}", "password": "motdepasse-test-123", "display_name": f"Testeur {who}"},
        pre=[f"pm.collectionVariables.set('email{who}', 'donnees-{who.lower()}-' + Date.now() + '@example.test');"],
        tests=[status(201), J, f"pm.collectionVariables.set('{var}', b.token);"])

# --- Niveaux d'urgence ---------------------------------------------------------
req("Niveaux d'urgence par défaut", "GET", "/urgency-levels", tests=[
    status(200), J,
    "pm.test('Basse, Moyenne, Haute dans l\\'ordre', () => pm.expect(b.map(u => u.name)).to.eql(['Basse', 'Moyenne', 'Haute']));",
    "pm.collectionVariables.set('urgHaute', b[2].id);",
])

# --- Groupes de listes ---------------------------------------------------------
req("Groupe de listes Personnel", "POST", "/folders", body={"name": "Personnel"}, tests=[
    status(201), J, "pm.test('position 0', () => pm.expect(b.position).to.eql(0));",
    "pm.collectionVariables.set('folderPerso', b.id);"])
req("Groupe de listes Travail", "POST", "/folders", body={"name": "Travail"}, tests=[
    status(201), J, "pm.test('ajouté à la fin (position 1)', () => pm.expect(b.position).to.eql(1));"])
req("Groupe de listes Études, inséré en tête", "POST", "/folders", body={"name": "Études", "position": 0}, tests=[
    status(201), J, "pm.test('position 0', () => pm.expect(b.position).to.eql(0));"])
req("Ordre des groupes de listes", "GET", "/folders", tests=[
    status(200), J,
    "pm.test('Études, Personnel, Travail', () => pm.expect(b.map(f => f.name)).to.eql(['Études', 'Personnel', 'Travail']));"])

# --- Listes --------------------------------------------------------------------
req("Liste Perso", "POST", "/lists",
    body={"name": "Perso", "folder_id": "{{folderPerso}}", "color": "#E5484D", "icon": "user"}, tests=[
        status(201), J,
        "pm.test('valeurs enregistrées', () => {",
        "    pm.expect(b.folder_id).to.eql(pm.collectionVariables.get('folderPerso'));",
        "    pm.expect(b.icon).to.eql('user');",
        "    pm.expect(b.default_view).to.eql('columns');",
        "    pm.expect(b.open_tasks).to.eql(0);",
        "});",
        "pm.collectionVariables.set('listId', b.id);"])
req("Nouvelle liste : un groupe « Tâches » par défaut", "GET", "/lists/{{listId}}/board", tests=[
    status(200), J,
    "pm.test('un seul groupe, nommé Tâches (compte en français)', () => pm.expect(b.groups.map(g => g.name)).to.eql(['Tâches']));",
    "pm.test('show_done = true par défaut', () => pm.expect(b.list.show_done).to.eql(true));",
    "pm.collectionVariables.set('groupA', b.groups[0].id);"])
req("Liste – couleur invalide", "POST", "/lists", body={"name": "X", "color": "rouge"}, tests=[status(400)])
req("Liste – groupe de listes inconnu", "POST", "/lists",
    body={"name": "X", "folder_id": "00000000-0000-4000-8000-000000000000"}, tests=[status(400)])

# --- Groupes -------------------------------------------------------------------
req("Renommer le groupe par défaut en Courses", "PATCH", "/groups/{{groupA}}", body={"name": "Courses"}, tests=[
    status(200), J, "pm.test('renommé', () => pm.expect(b.name).to.eql('Courses'));"])
req("Groupe Administratif", "POST", "/lists/{{listId}}/groups", body={"name": "Administratif"}, tests=[
    status(201), J, "pm.test('position 1', () => pm.expect(b.position).to.eql(1));",
    "pm.collectionVariables.set('groupB', b.id);"])

# --- Tâches --------------------------------------------------------------------
req("Tâche Pain (complète)", "POST", "/groups/{{groupA}}/tasks",
    body={"title": "  Acheter du pain ", "notes": "Complet", "due_date": "2026-11-20",
          "urgency_id": "{{urgHaute}}", "tags": ["courses", "#maison", "courses"]}, tests=[
        status(201), J,
        "pm.test('titre nettoyé, tags triés sans doublon', () => {",
        "    pm.expect(b.title).to.eql('Acheter du pain');",
        "    pm.expect(b.tags).to.eql(['courses', 'maison']);",
        "    pm.expect(b.due_date).to.eql('2026-11-20');",
        "    pm.expect(b.urgency_id).to.eql(pm.collectionVariables.get('urgHaute'));",
        "    pm.expect(b.done).to.eql(false);",
        "});",
        "pm.collectionVariables.set('task1', b.id);"])
req("Tâche Lait", "POST", "/groups/{{groupA}}/tasks", body={"title": "Lait"}, tests=[
    status(201), J, "pm.test('position 1, sans tag', () => { pm.expect(b.position).to.eql(1); pm.expect(b.tags).to.eql([]); });",
    "pm.collectionVariables.set('task2', b.id);"])
req("Tâche Œufs", "POST", "/groups/{{groupA}}/tasks", body={"title": "Œufs"}, tests=[
    status(201), J, "pm.collectionVariables.set('task3', b.id);"])
req("Tâche – date invalide", "POST", "/groups/{{groupA}}/tasks", body={"title": "X", "due_date": "2026-02-31"},
    tests=[status(400)])
req("Tâche – titre vide", "POST", "/groups/{{groupA}}/tasks", body={"title": "   "}, tests=[status(400)])

req("Glisser Œufs en tête", "PATCH", "/tasks/{{task3}}", body={"position": 0}, tests=[
    status(200), J, "pm.test('position 0', () => pm.expect(b.position).to.eql(0));"])
req("Ordre après le glisser-déposer", "GET", "/lists/{{listId}}/board", tests=[
    status(200), J,
    "pm.test('Œufs, Acheter du pain, Lait', () => pm.expect(b.groups[0].tasks.map(t => t.title)).to.eql(['Œufs', 'Acheter du pain', 'Lait']));",
    "pm.test('positions renumérotées 0, 1, 2', () => pm.expect(b.groups[0].tasks.map(t => t.position)).to.eql([0, 1, 2]));"])

req("Cocher Pain", "PATCH", "/tasks/{{task1}}", body={"done": True}, tests=[
    status(200), J, "pm.test('done_at rempli', () => { pm.expect(b.done).to.eql(true); pm.expect(b.done_at).to.be.a('string'); });"])
req("Décocher Pain", "PATCH", "/tasks/{{task1}}", body={"done": False}, tests=[
    status(200), J, "pm.test('done_at vidé', () => { pm.expect(b.done).to.eql(false); pm.expect(b.done_at).to.eql(null); });"])
req("Effacer échéance et urgence, remplacer les tags", "PATCH", "/tasks/{{task1}}",
    body={"due_date": None, "urgency_id": None, "tags": ["urgent"]}, tests=[
        status(200), J,
        "pm.test('null et nouveaux tags', () => {",
        "    pm.expect(b.due_date).to.eql(null);",
        "    pm.expect(b.urgency_id).to.eql(null);",
        "    pm.expect(b.tags).to.eql(['urgent']);",
        "    pm.expect(b.notes).to.eql('Complet');",
        "});"])
req("Déplacer Lait dans Administratif", "PATCH", "/tasks/{{task2}}", body={"group_id": "{{groupB}}"}, tests=[
    status(200), J,
    "pm.test('dans le nouveau groupe', () => pm.expect(b.group_id).to.eql(pm.collectionVariables.get('groupB')));"])
req("Board complet", "GET", "/lists/{{listId}}/board", tests=[
    status(200), J,
    "pm.test('2 tâches dans Courses, 1 dans Administratif', () => {",
    "    pm.expect(b.groups.map(g => g.name)).to.eql(['Courses', 'Administratif']);",
    "    pm.expect(b.groups[0].tasks.length).to.eql(2);",
    "    pm.expect(b.groups[1].tasks.map(t => t.title)).to.eql(['Lait']);",
    "});",
    "pm.test('open_tasks = 3', () => pm.expect(b.list.open_tasks).to.eql(3));"])
req("Champ inconnu refusé", "PATCH", "/tasks/{{task1}}", body={"titel": "faute"}, tests=[status(400)])
req("Identifiant invalide dans l'URL", "GET", "/lists/pas-un-uuid/board", tests=[status(404)])

# --- Tags ----------------------------------------------------------------------
req("Liste des tags", "GET", "/tags", tests=[
    status(200), J,
    "const byName = Object.fromEntries(b.map(t => [t.name, t.task_count]));",
    "pm.test('courses et maison libérés, urgent utilisé une fois', () => {",
    "    pm.expect(byName).to.eql({ courses: 0, maison: 0, urgent: 1 });",
    "});",
    "pm.collectionVariables.set('tagUrgent', b.find(t => t.name === 'urgent').id);"])
req("Tag en double", "POST", "/tags", body={"name": "#urgent"}, tests=[status(409)])
req("Renommer un tag", "PATCH", "/tags/{{tagUrgent}}", body={"name": "prioritaire"}, tests=[
    status(200), J, "pm.test('renommé', () => pm.expect(b.name).to.eql('prioritaire'));"])
req("Le tag renommé apparaît sur la tâche", "GET", "/tasks/{{task1}}", tests=[
    status(200), J, "pm.test('tags = [prioritaire]', () => pm.expect(b.tags).to.eql(['prioritaire']));"])

# --- Niveaux d'urgence : ajout et réordonnancement -----------------------------
req("Nouveau niveau Critique", "POST", "/urgency-levels", body={"name": "Critique", "color": "#8E4EC6"}, tests=[
    status(201), J, "pm.test('rank 3 (le plus urgent)', () => pm.expect(b.rank).to.eql(3));",
    "pm.collectionVariables.set('urgCritique', b.id);"])
req("Critique remonté en rank 0", "PATCH", "/urgency-levels/{{urgCritique}}", body={"rank": 0}, tests=[status(200)])
req("Ordre des niveaux", "GET", "/urgency-levels", tests=[
    status(200), J,
    "pm.test('Critique, Basse, Moyenne, Haute', () => pm.expect(b.map(u => u.name)).to.eql(['Critique', 'Basse', 'Moyenne', 'Haute']));",
    "pm.test('rangs 0 à 3', () => pm.expect(b.map(u => u.rank)).to.eql([0, 1, 2, 3]));"])

# --- Isolation entre utilisateurs ----------------------------------------------
req("B ne voit aucune liste", "GET", "/lists", token="tokenB", tests=[
    status(200), "pm.test('liste vide', () => pm.expect(pm.response.json()).to.eql([]));"])
req("B ne peut pas lire le board de A", "GET", "/lists/{{listId}}/board", token="tokenB", tests=[status(404)])
req("B ne peut pas modifier une tâche de A", "PATCH", "/tasks/{{task1}}", token="tokenB",
    body={"title": "piraté"}, tests=[status(404)])
req("B ne peut pas ajouter une tâche chez A", "POST", "/groups/{{groupA}}/tasks", token="tokenB",
    body={"title": "intrus"}, tests=[status(404)])
req("B ne peut pas utiliser le groupe de listes de A", "POST", "/lists", token="tokenB",
    body={"name": "X", "folder_id": "{{folderPerso}}"}, tests=[status(400)])
req("B crée sa propre liste", "POST", "/lists", token="tokenB", body={"name": "Liste de B"}, tests=[
    status(201), J, "pm.collectionVariables.set('listOfB', b.id);"])
req("B crée un groupe dans sa liste", "POST", "/lists/{{listOfB}}/groups", token="tokenB", body={"name": "G"}, tests=[
    status(201), J, "pm.collectionVariables.set('groupOfB', b.id);"])
req("B ne peut pas utiliser un niveau d'urgence de A", "POST", "/groups/{{groupOfB}}/tasks", token="tokenB",
    body={"title": "X", "urgency_id": "{{urgHaute}}"}, tests=[
        status(400), "pm.test('message clair', () => pm.expect(pm.response.json().error).to.eql(\"Niveau d'urgence inconnu\"));"])
req("B ne peut pas déplacer sa tâche dans un groupe de A", "POST", "/groups/{{groupOfB}}/tasks", token="tokenB",
    body={"title": "Tâche de B"}, tests=[status(201), J, "pm.collectionVariables.set('taskOfB', b.id);"])
req("… le déplacement est refusé", "PATCH", "/tasks/{{taskOfB}}", token="tokenB",
    body={"group_id": "{{groupA}}"}, tests=[status(400)])
req("B ne peut pas supprimer la liste de A", "DELETE", "/lists/{{listId}}", token="tokenB", tests=[status(404)])
req("La tâche de A est intacte", "GET", "/tasks/{{task1}}", tests=[
    status(200), "pm.test('titre inchangé', () => pm.expect(pm.response.json().title).to.eql('Acheter du pain'));"])

# --- Tâches faites : masquer, vider (étape 3c-2b) ---------------------------------
req("Masquer les tâches faites de la liste", "PATCH", "/lists/{{listId}}", body={"show_done": False}, tests=[
    status(200), J, "pm.test('show_done = false', () => pm.expect(b.show_done).to.eql(false));"])
req("Cocher Pain (pour le vider ensuite)", "PATCH", "/tasks/{{task1}}", body={"done": True}, tests=[status(200)])
req("Cocher Œufs", "PATCH", "/tasks/{{task3}}", body={"done": True}, tests=[status(200)])
req("B ne peut pas vider les tâches faites de A", "DELETE", "/lists/{{listId}}/done-tasks", token="tokenB", tests=[status(404)])
req("Vider les tâches faites", "DELETE", "/lists/{{listId}}/done-tasks", tests=[
    status(200), J, "pm.test('2 tâches supprimées', () => pm.expect(b.deleted).to.eql(2));"])
req("Il ne reste que les tâches pas faites", "GET", "/lists/{{listId}}/board", tests=[
    status(200), J,
    "const tasks = b.groups.flatMap(g => g.tasks);",
    "pm.test('aucune tâche faite, Lait toujours là', () => {",
    "    pm.expect(tasks.filter(t => t.done)).to.have.lengthOf(0);",
    "    pm.expect(tasks.map(t => t.title)).to.include('Lait');",
    "});"])

# --- Suppressions ----------------------------------------------------------------
req("Supprimer le groupe de listes Personnel", "DELETE", "/folders/{{folderPerso}}", tests=[status(204)])
req("La liste Perso est passée hors groupe", "GET", "/lists", tests=[
    status(200), J,
    "pm.test('folder_id = null', () => pm.expect(b.find(l => l.name === 'Perso').folder_id).to.eql(null));"])
req("Supprimer le groupe Courses", "DELETE", "/groups/{{groupA}}", tests=[status(204)])
req("Ses tâches sont supprimées (ou déjà vidées)", "GET", "/tasks/{{task1}}", tests=[status(404)])
req("Supprimer la liste", "DELETE", "/lists/{{listId}}", tests=[status(204)])
req("La liste n'existe plus", "GET", "/lists/{{listId}}/board", tests=[status(404)])
req("Lait (dans Administratif) est supprimé avec la liste", "GET", "/tasks/{{task2}}", tests=[status(404)])

collection = {
    "info": {
        "name": "BetterTodo API – données (étapes 2b et 3c-2b)",
        "description": "Listes, groupes, tâches, tags, niveaux d'urgence, ordre et isolation entre utilisateurs. "
                       "À lancer dans l'ordre (voir docs/etapes/02b-api-donnees.md).",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "variable": [{"key": "baseUrl", "value": "http://localhost:8080/api/v1"}],
    "item": items,
}
with open(sys.argv[1], "w") as f:
    json.dump(collection, f, ensure_ascii=False, indent=2)
print(len(items), "requêtes")
