"""
Génère la collection Postman des données (étape 2b, complétée en 3c-2b, 3e, 7-3, 7-5 et 8-4).

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

# --- Préparation : deux comptes, créés par l'admin (étape 3e) -------------------
# L'admin est passé au lancement : --env-var adminEmail=… --env-var adminPassword=…
req("Admin – connexion", "POST", "/auth/login", token=None,
    body={"email": "{{adminEmail}}", "password": "{{adminPassword}}"},
    tests=[status(200), J, "pm.collectionVariables.set('adminToken', b.token);"])
for who, var in (("A", "token"), ("B", "tokenB")):
    req(f"Compte {who} – création par l'admin", "POST", "/admin/users", token="adminToken",
        body={"email": "{{email" + who + "}}", "password": "motdepasse-test-123", "display_name": f"Testeur {who}"},
        pre=[f"pm.collectionVariables.set('email{who}', 'donnees-{who.lower()}-' + Date.now() + '@example.test');"],
        tests=[status(201), J, f"pm.collectionVariables.set('{var}Id', b.id);"])
    req(f"Compte {who} – connexion", "POST", "/auth/login", token=None,
        body={"email": "{{email" + who + "}}", "password": "motdepasse-test-123"},
        tests=[status(200), J, f"pm.collectionVariables.set('{var}', b.token);"])

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
    "pm.test('show_done = false par défaut (tâches faites masquées)', () => pm.expect(b.list.show_done).to.eql(false));",
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

# --- Échéances : listes intelligentes « Aujourd'hui » et « Planifié » (étape 3d) ---
# On fixe la date du jour à 2026-06-15 (c'est le client qui l'envoie).
req("Liste pour les échéances", "POST", "/lists", body={"name": "Échéances"}, tests=[
    status(201), J, "pm.collectionVariables.set('listDue', b.id);"])
req("Son groupe par défaut", "GET", "/lists/{{listDue}}/board", tests=[
    status(200), J, "pm.collectionVariables.set('groupDue', b.groups[0].id);"])
for title, due in [("En retard", "2026-01-01"), ("Du jour", "2026-06-15"), ("Plus tard", "2027-01-01"), ("Sans date", None)]:
    body = {"title": title}
    if due:
        body["due_date"] = due
    req(f"Tâche « {title} »", "POST", "/groups/{{groupDue}}/tasks", body=body, tests=[
        status(201), J, f"pm.collectionVariables.set('due_{title.replace(' ', '_')}', b.id);"])
req("Tâche faite avec échéance (doit être ignorée)", "POST", "/groups/{{groupDue}}/tasks",
    body={"title": "Déjà faite", "due_date": "2026-06-15"}, tests=[
        status(201), J, "pm.collectionVariables.set('dueDone', b.id);"])
req("… cochée", "PATCH", "/tasks/{{dueDone}}", body={"done": True}, tests=[status(200)])

req("Compteurs au 2026-06-15", "GET", "/tasks/due/counts?today=2026-06-15", tests=[
    status(200), J,
    "pm.test('today = 2 (retard + jour), scheduled = 3', () => pm.expect(b).to.eql({ today: 2, scheduled: 3 }));"])
req("Aujourd'hui = jusqu'au 2026-06-15", "GET", "/tasks/due?until=2026-06-15", tests=[
    status(200), J,
    "pm.test('retard puis jour, ni futur, ni sans date, ni faite', () => pm.expect(b.map(t => t.title)).to.eql(['En retard', 'Du jour']));",
    'pm.test("avec la liste et le groupe d\'origine", () => {',
    "    pm.expect(b[0].list.name).to.eql('Échéances');",
    "    pm.expect(b[0].group_name).to.eql('Tâches');",
    "    pm.expect(b[0]).to.have.property('tags');",
    "});"])
req("Planifié = toutes les échéances", "GET", "/tasks/due", tests=[
    status(200), J,
    "pm.test('triées par date', () => pm.expect(b.map(t => t.title)).to.eql(['En retard', 'Du jour', 'Plus tard']));"])
req("Date invalide", "GET", "/tasks/due?until=15.06.2026", tests=[status(400)])
req("Compteurs sans date du jour", "GET", "/tasks/due/counts", tests=[status(400)])
req("B ne voit pas les échéances de A", "GET", "/tasks/due", token="tokenB", tests=[
    status(200), "pm.test('aucune tâche de A', () => pm.expect(pm.response.json().map(t => t.title)).to.not.include('Du jour'));"])
# --- Heure facultative (étape 7-3) -------------------------------------------------
req("Heure – tâche du jour à 09:00", "POST", "/groups/{{groupDue}}/tasks",
    body={"title": "Du jour 9h", "due_date": "2026-06-15", "due_time": "09:00"}, tests=[
        status(201), J, "pm.test('heure renvoyée', () => pm.expect(b.due_time).to.eql('09:00'));",
        "pm.collectionVariables.set('dueNine', b.id);"])
req("Heure – sans date refusée", "POST", "/groups/{{groupDue}}/tasks",
    body={"title": "X", "due_time": "09:00"}, tests=[status(400)])
req("Heure – format invalide", "POST", "/groups/{{groupDue}}/tasks",
    body={"title": "X", "due_date": "2026-06-15", "due_time": "25:00"}, tests=[status(400)])
req("Heure – ajouter 14:30 à « Du jour »", "PATCH", "/tasks/{{due_Du_jour}}", body={"due_time": "14:30"}, tests=[
    status(200), J, "pm.test('14:30, date gardée', () => { pm.expect(b.due_time).to.eql('14:30'); pm.expect(b.due_date).to.eql('2026-06-15'); });"])
req("Heure – triées par date puis heure", "GET", "/tasks/due?until=2026-06-15", tests=[
    status(200), J,
    "pm.test('retard, 09:00, 14:30', () => pm.expect(b.map(t => t.title)).to.eql(['En retard', 'Du jour 9h', 'Du jour']));"])
req("Heure – refusée sur une tâche sans date", "PATCH", "/tasks/{{due_Sans_date}}", body={"due_time": "10:00"}, tests=[status(400)])
req("Heure – enlever l'échéance enlève l'heure", "PATCH", "/tasks/{{due_Du_jour}}", body={"due_date": None}, tests=[
    status(200), J, "pm.test('date et heure à null', () => { pm.expect(b.due_date).to.eql(null); pm.expect(b.due_time).to.eql(null); });"])
req("Supprimer la liste des échéances", "DELETE", "/lists/{{listDue}}", tests=[status(204)])

# --- Listes intelligentes personnalisées (étape 7-5) -------------------------------
# Niveaux à ce stade : Critique (rank 0), Basse, Moyenne, Haute (rank 3, le plus urgent).
req("Liste pour les listes intelligentes", "POST", "/lists", body={"name": "Intelligentes"}, tests=[
    status(201), J, "pm.collectionVariables.set('listSmart', b.id);"])
req("Son groupe", "GET", "/lists/{{listSmart}}/board", tests=[
    status(200), J, "pm.collectionVariables.set('groupSmart', b.groups[0].id);"])
for var, title, body in [
    ("smartSpec", "Spec", {"tags": ["boulot"], "urgency_id": "{{urgHaute}}", "due_date": "2026-06-10"}),
    ("smartReunion", "Réunion", {"tags": ["boulot", "réunion"], "due_date": "2026-06-05"}),
    ("smartCourrier", "Courrier", {"tags": ["réunion"], "urgency_id": "{{urgCritique}}"}),
    ("smartDone", "Déjà faite", {"tags": ["boulot"], "urgency_id": "{{urgHaute}}"}),
]:
    req(f"Tâche « {title} »", "POST", "/groups/{{groupSmart}}/tasks", body={"title": title, **body}, tests=[
        status(201), J, f"pm.collectionVariables.set('{var}', b.id);"])
req("… « Déjà faite » cochée", "PATCH", "/tasks/{{smartDone}}", body={"done": True}, tests=[status(200)])
req("Ids des tags boulot et réunion", "GET", "/tags", tests=[
    status(200), J,
    "pm.collectionVariables.set('tagBoulot', b.find(t => t.name === 'boulot').id);",
    "pm.collectionVariables.set('tagReunion', b.find(t => t.name === 'réunion').id);"])

req("Liste intelligente – par tags sans tag", "POST", "/smart-lists",
    body={"name": "X", "kind": "tags", "tag_ids": []}, tests=[status(400)])
req("Liste intelligente – type inconnu", "POST", "/smart-lists",
    body={"name": "X", "kind": "dates", "tag_ids": ["{{tagBoulot}}"]}, tests=[status(400)])
req("Liste intelligente « Travail » (au moins #boulot)", "POST", "/smart-lists",
    body={"name": "Travail", "color": "#8250DF", "icon": "tag", "kind": "tags", "tag_ids": ["{{tagBoulot}}"]}, tests=[
        status(201), J,
        "pm.test('valeurs par défaut', () => {",
        "    pm.expect(b.tag_match).to.eql('any');",
        "    pm.expect(b.visible).to.eql(true);",
        "    pm.expect(b.labels).to.eql(['#boulot']);",
        "    pm.expect(b.open_tasks).to.eql(2);",
        "});",
        "pm.collectionVariables.set('smartTravail', b.id);"])
req("Travail : tâches à faire triées par échéance", "GET", "/smart-lists/{{smartTravail}}/tasks", tests=[
    status(200), J,
    "pm.test('Réunion (5 juin) puis Spec (10 juin), pas la tâche faite', () => pm.expect(b.map(t => t.title)).to.eql(['Réunion', 'Spec']));",
    "pm.test('avec la liste d\'origine', () => pm.expect(b[0].list.name).to.eql('Intelligentes'));"])
req("Travail : tous les tags #boulot et #réunion", "PATCH", "/smart-lists/{{smartTravail}}",
    body={"tag_match": "all", "tag_ids": ["{{tagBoulot}}", "{{tagReunion}}"]}, tests=[
        status(200), J, "pm.test('2 tags, 1 tâche', () => { pm.expect(b.tag_ids.length).to.eql(2); pm.expect(b.open_tasks).to.eql(1); });"])
req("Travail (tous) : seulement Réunion", "GET", "/smart-lists/{{smartTravail}}/tasks", tests=[
    status(200), J, "pm.test('Réunion', () => pm.expect(b.map(t => t.title)).to.eql(['Réunion']));"])
req("Liste intelligente – liste de tags vide refusée", "PATCH", "/smart-lists/{{smartTravail}}",
    body={"tag_ids": []}, tests=[status(400)])
req("Liste intelligente – changer de type sans ses niveaux", "PATCH", "/smart-lists/{{smartTravail}}",
    body={"kind": "urgency"}, tests=[status(400)])
req("Liste intelligente « Urgent » (Haute, Critique)", "POST", "/smart-lists",
    body={"name": "Urgent", "color": "#E5484D", "icon": "flag", "kind": "urgency",
          "urgency_ids": ["{{urgCritique}}", "{{urgHaute}}"]}, tests=[
        status(201), J,
        "pm.test('niveaux du plus urgent au moins urgent', () => pm.expect(b.labels).to.eql(['Haute', 'Critique']));",
        "pm.test('position 1', () => pm.expect(b.position).to.eql(1));",
        "pm.collectionVariables.set('smartUrgent', b.id);"])
req("Urgent : du plus urgent au moins urgent", "GET", "/smart-lists/{{smartUrgent}}/tasks", tests=[
    status(200), J, "pm.test('Spec (Haute) puis Courrier (Critique)', () => pm.expect(b.map(t => t.title)).to.eql(['Spec', 'Courrier']));"])
req("Urgent : masquée", "PATCH", "/smart-lists/{{smartUrgent}}", body={"visible": False}, tests=[
    status(200), J, "pm.test('visible = false', () => pm.expect(b.visible).to.eql(false));"])
req("Urgent remontée en tête", "PATCH", "/smart-lists/{{smartUrgent}}", body={"position": 0}, tests=[status(200)])
req("Ordre des listes intelligentes", "GET", "/smart-lists", tests=[
    status(200), J, "pm.test('Urgent, Travail', () => pm.expect(b.map(s => s.name)).to.eql(['Urgent', 'Travail']));"])
req("B ne voit pas les listes intelligentes de A", "GET", "/smart-lists", token="tokenB", tests=[
    status(200), "pm.test('liste vide', () => pm.expect(pm.response.json()).to.eql([]));"])
req("B ne peut pas lire les tâches d'une liste de A", "GET", "/smart-lists/{{smartTravail}}/tasks", token="tokenB",
    tests=[status(404)])
req("B ne peut pas utiliser un tag de A", "POST", "/smart-lists", token="tokenB",
    body={"name": "X", "kind": "tags", "tag_ids": ["{{tagBoulot}}"]}, tests=[status(400)])
req("Masquer « Aujourd'hui » et « Planifié »", "PATCH", "/me", body={"show_today": False, "show_scheduled": False}, tests=[
    status(200), J, "pm.test('masqués', () => { pm.expect(b.show_today).to.eql(false); pm.expect(b.show_scheduled).to.eql(false); });"])
req("Supprimer la liste intelligente Urgent", "DELETE", "/smart-lists/{{smartUrgent}}", tests=[status(204)])
req("Elle n'existe plus", "GET", "/smart-lists/{{smartUrgent}}/tasks", tests=[status(404)])
req("Supprimer la liste « Intelligentes »", "DELETE", "/lists/{{listSmart}}", tests=[status(204)])

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

# --- Synchronisation de l'app Android (étape 8-4) ----------------------------------
# L'app crée hors ligne avec ses propres UUID, rejoue ses requêtes, puis demande GET /sync?since=…
def new_ids(*names):
    return [f"pm.collectionVariables.set('{n}', pm.variables.replaceIn('{{{{$guid}}}}'));" for n in names]


req("Synchro complète (sans curseur)", "GET", "/sync", tests=[
    status(200), J,
    "pm.test('curseur, compte et tout le reste', () => {",
    "  pm.expect(b.cursor).to.be.a('string');",
    "  pm.expect(b.user.id).to.eql(pm.collectionVariables.get('tokenId'));",
    "  ['folders', 'lists', 'groups', 'tasks', 'urgency_levels', 'tags', 'smart_lists', 'deleted'].forEach(k => pm.expect(b[k]).to.be.an('array'));",
    "});",
    "pm.collectionVariables.set('cursor1', b.cursor);"])
req("Curseur invalide", "GET", "/sync?since=hier", tests=[status(400)])
req("Groupe de listes avec un id choisi par l'app", "POST", "/folders", body={"id": "{{syncFolder}}", "name": "Synchro"},
    pre=new_ids("syncFolder", "syncList", "syncGroup", "syncTask", "syncDone", "syncTag", "syncUrgency", "syncSmart"),
    tests=[status(201), J, "pm.test('id gardé', () => pm.expect(b.id).to.eql(pm.collectionVariables.get('syncFolder')));"])
req("Rejeu de la même création : l'existant, sans doublon", "POST", "/folders", body={"id": "{{syncFolder}}", "name": "Synchro"}, tests=[
    status(200), J, "pm.test('même id', () => pm.expect(b.id).to.eql(pm.collectionVariables.get('syncFolder')));"])
req("Il n'y a qu'un groupe de listes Synchro", "GET", "/folders", tests=[
    status(200), J, "pm.test('un seul', () => pm.expect(b.filter(f => f.name === 'Synchro').length).to.eql(1));"])
req("id qui n'est pas un UUID", "POST", "/folders", body={"id": "abc", "name": "X"}, tests=[status(400)])
req("B ne peut pas réutiliser l'id de A", "POST", "/folders", token="tokenB", body={"id": "{{syncFolder}}", "name": "X"}, tests=[status(409)])
req("Liste avec id et id de son groupe par défaut", "POST", "/lists",
    body={"id": "{{syncList}}", "default_group_id": "{{syncGroup}}", "name": "Synchro", "folder_id": "{{syncFolder}}"},
    tests=[status(201), J, "pm.test('id gardé', () => pm.expect(b.id).to.eql(pm.collectionVariables.get('syncList')));"])
req("Le groupe par défaut a l'id choisi", "GET", "/lists/{{syncList}}/board", tests=[
    status(200), J, "pm.test('un groupe, avec cet id', () => pm.expect(b.groups.map(g => g.id)).to.eql([pm.collectionVariables.get('syncGroup')]));"])
req("Rejeu de la liste", "POST", "/lists",
    body={"id": "{{syncList}}", "default_group_id": "{{syncGroup}}", "name": "Synchro", "folder_id": "{{syncFolder}}"},
    tests=[status(200)])
req("Toujours un seul groupe (pas de second groupe par défaut)", "GET", "/lists/{{syncList}}/board", tests=[
    status(200), J, "pm.test('un groupe', () => pm.expect(b.groups.length).to.eql(1));"])
req("Tâche avec id", "POST", "/groups/{{syncGroup}}/tasks", body={"id": "{{syncTask}}", "title": "Hors ligne", "due_date": "2026-11-19", "due_time": "09:30"},
    tests=[status(201), J, "pm.test('id gardé', () => pm.expect(b.id).to.eql(pm.collectionVariables.get('syncTask')));"])
req("Rejeu de la tâche", "POST", "/groups/{{syncGroup}}/tasks", body={"id": "{{syncTask}}", "title": "Hors ligne"}, tests=[
    status(200), J, "pm.test('la tâche existante (heure gardée)', () => pm.expect(b.due_time).to.eql('09:30'));"])
req("Tâche faite, avec id", "POST", "/groups/{{syncGroup}}/tasks", body={"id": "{{syncDone}}", "title": "Faite"}, tests=[status(201)])
req("La cocher", "PATCH", "/tasks/{{syncDone}}", body={"done": True}, tests=[status(200)])
req("Tag avec id", "POST", "/tags", body={"id": "{{syncTag}}", "name": "synchro"}, tests=[
    status(201), J, "pm.test('id gardé', () => pm.expect(b.id).to.eql(pm.collectionVariables.get('syncTag')));"])
req("Rejeu du tag", "POST", "/tags", body={"id": "{{syncTag}}", "name": "synchro"}, tests=[status(200)])
req("Niveau d'urgence avec id", "POST", "/urgency-levels", body={"id": "{{syncUrgency}}", "name": "Synchro", "color": "#2F6FEB"}, tests=[
    status(201), J, "pm.test('id gardé', () => pm.expect(b.id).to.eql(pm.collectionVariables.get('syncUrgency')));"])
req("Liste intelligente avec id", "POST", "/smart-lists", body={"id": "{{syncSmart}}", "name": "Synchro", "kind": "tags", "tag_ids": ["{{syncTag}}"]}, tests=[
    status(201), J, "pm.test('id gardé', () => pm.expect(b.id).to.eql(pm.collectionVariables.get('syncSmart')));"])
req("Synchro depuis le curseur : ce qui vient d'être créé", "GET", "/sync?since={{cursor1}}", tests=[
    status(200), J,
    "const v = k => pm.collectionVariables.get(k);",
    "pm.test('tout est là', () => {",
    "  pm.expect(b.folders.map(x => x.id)).to.include(v('syncFolder'));",
    "  pm.expect(b.lists.map(x => x.id)).to.include(v('syncList'));",
    "  pm.expect(b.groups.map(x => x.id)).to.include(v('syncGroup'));",
    "  pm.expect(b.tasks.map(x => x.id)).to.include.members([v('syncTask'), v('syncDone')]);",
    "  pm.expect(b.tags.map(x => x.id)).to.include(v('syncTag'));",
    "  pm.expect(b.urgency_levels.map(x => x.id)).to.include(v('syncUrgency'));",
    "  pm.expect(b.smart_lists.map(x => x.id)).to.include(v('syncSmart'));",
    "});",
    "pm.test('le groupe donne sa liste', () => pm.expect(b.groups.find(g => g.id === v('syncGroup')).list_id).to.eql(v('syncList')));",
    "pm.collectionVariables.set('cursor2', b.cursor);"])
req("Vider les tâches faites", "DELETE", "/lists/{{syncList}}/done-tasks", tests=[
    status(200), J, "pm.test('1 supprimée', () => pm.expect(b.deleted).to.eql(1));"])
for name, path in (("la tâche", "/tasks/{{syncTask}}"), ("la liste intelligente", "/smart-lists/{{syncSmart}}"),
                   ("le tag", "/tags/{{syncTag}}"), ("le niveau d'urgence", "/urgency-levels/{{syncUrgency}}"),
                   ("la liste", "/lists/{{syncList}}"), ("le groupe de listes", "/folders/{{syncFolder}}")):
    req(f"Supprimer {name}", "DELETE", path, tests=[status(204)])
req("Rejeu d'une suppression : 404 (déjà fait)", "DELETE", "/tasks/{{syncTask}}", tests=[status(404)])
req("Synchro : les suppressions", "GET", "/sync?since={{cursor2}}", tests=[
    status(200), J,
    "const v = k => pm.collectionVariables.get(k);",
    "const deleted = b.deleted.map(d => d.type + ':' + d.id);",
    "pm.test('chaque suppression est notée', () => pm.expect(deleted).to.include.members([",
    "  'task:' + v('syncDone'), 'task:' + v('syncTask'), 'smart_list:' + v('syncSmart'), 'tag:' + v('syncTag'),",
    "  'urgency_level:' + v('syncUrgency'), 'list:' + v('syncList'), 'folder:' + v('syncFolder')]));",
    "pm.test('liste supprimée absente', () => pm.expect(b.lists.map(x => x.id)).to.not.include(v('syncList')));"])
req("B ne voit pas les suppressions de A", "GET", "/sync", token="tokenB", tests=[
    status(200), J,
    "pm.test('aucune', () => pm.expect(b.deleted.map(d => d.id)).to.not.include(pm.collectionVariables.get('syncList')));",
    "pm.test('son propre compte', () => pm.expect(b.user.id).to.eql(pm.collectionVariables.get('tokenBId')));"])

# --- Ménage : l'admin supprime les deux comptes de test (et toutes leurs données) ---
req("Admin supprime le compte A", "DELETE", "/admin/users/{{tokenId}}", token="adminToken", tests=[status(204)])
req("Admin supprime le compte B", "DELETE", "/admin/users/{{tokenBId}}", token="adminToken", tests=[status(204)])
req("La session de B ne marche plus", "GET", "/lists", token="tokenB", tests=[status(401)])

collection = {
    "info": {
        "name": "BetterTodo API – données (étapes 2b, 3c-2b, 3e et 8-4)",
        "description": "Listes, groupes, tâches, tags, niveaux d'urgence, ordre et isolation entre utilisateurs. "
                       "À lancer dans l'ordre, avec --env-var adminEmail=… --env-var adminPassword=… "
                       "(voir docs/etapes/02b-api-donnees.md et 03e-admin-et-icones.md).",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "variable": [{"key": "baseUrl", "value": "http://localhost:8080/api/v1"}],
    "item": items,
}
with open(sys.argv[1], "w") as f:
    json.dump(collection, f, ensure_ascii=False, indent=2)
print(len(items), "requêtes")
