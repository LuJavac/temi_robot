# Temi Robot — Application Android de bibliothèque

Application Android Kotlin/Jetpack Compose en mode **kiosque** pour le robot **Temi**, déployée à la NYP Library (Singapour). Le robot patrouille, accueille les visiteurs, répond à leurs questions vocales via un backend Python, et les guide vers les différents espaces de la bibliothèque.

---

## 1. Informations générales

| Élément | Valeur |
|---|---|
| Nom de l'app | `temi_robot` (release) / `Temi Test` (debug) |
| Package release | `com.temi.temi_robot` (signé par Johan, enregistré console dev Temi) |
| Package debug | `com.temi.temi_robot.test` (cohabite avec la release sur le robot) |
| Langage | Kotlin |
| Build system | Gradle 8.13 (Kotlin DSL) — AGP 8.12.3 |
| compileSdk / targetSdk | 35 |
| minSdk | 24 |
| JVM target | 11 (JDK 17+ requis pour exécuter Gradle) |
| Kotlin | 2.1.21 |
| Temi SDK | 1.137.1 |
| Mode | KIOSK (`com.robotemi.sdk.metadata.KIOSK = TRUE`) |
| Mot de passe d'accès aux réglages | `nyp123` |
| Map utilisée | `R4 Block Complete (USE THIS) for BOA1` |
| Serveur backend | `http://192.168.1.79:5000/process` |

---

## 2. Arborescence

```
temi_robot/
├── app/
│   ├── build.gradle.kts                    # debug : applicationIdSuffix ".test"
│   └── src/
│       ├── debug/
│       │   └── res/values/strings.xml      # override app_name = "Temi Test"
│       └── main/
│           ├── AndroidManifest.xml
│           ├── assets/
│           │   └── hand_landmarker.task    # modèle MediaPipe (non tracké git)
│           ├── java/com/temi/temi_robot/
│           │   ├── MainActivity.kt
│           │   ├── RobotController.kt
│           │   ├── JsonManager.kt
│           │   ├── dataclasses/
│           │   │   ├── PatrolStates.kt
│           │   │   └── TimeSlot.kt
│           │   ├── detecttion/             # (sic) — package "detection"
│           │   │   └── WaveGestureRecognizer.kt
│           │   ├── pages/
│           │   │   ├── FirstPage.kt
│           │   │   ├── MainPage.kt
│           │   │   ├── LoadingPage.kt
│           │   │   ├── GoToBasePage.kt
│           │   │   ├── LostConnectionPage.kt
│           │   │   ├── PasswordPage.kt
│           │   │   ├── RestartPage.kt
│           │   │   ├── LocationsSettingsPage.kt
│           │   │   └── TimeSettingsPage.kt
│           │   ├── time/
│           │   │   ├── AlarmScheduler.kt
│           │   │   └── TimeListener.kt
│           │   ├── ui/theme/               # Color.kt, Theme.kt, Type.kt
│           │   └── ui_utils/
│           │       ├── LoadingScreen.kt
│           │       └── SimpleAdapter.kt
│           └── res/
│               ├── drawable/               # boutons, icônes
│               ├── layout/                 # 12 layouts XML
│               ├── mipmap-anydpi-v26/
│               ├── values/                 # colors, strings, themes
│               └── xml/                    # backup_rules, network_security_config…
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.properties # Gradle 8.13
├── changes_2026-05-14.txt                  # journal : ajout build .test + diagnostics caméra
├── changes_2026-05-18.txt                  # journal : versions, perms, TTS fallback, open-palm, overlay
└── README.md                               # quasi-vide (#README)
```

---

## 3. Manifest & permissions

`AndroidManifest.xml` déclare :

- Permissions : `FOREGROUND_SERVICE`, `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `CAMERA`
- Features : caméra et caméra frontale (non requises pour autoriser l'install)
- Métadonnée Temi : permissions `settings`, `map`, `meetings`
- Mode KIOSK activé
- Activité unique `MainActivity` (launcher, `turnScreenOn`, `showWhenLocked`)
- Receiver `time.TimeListener` (alarmes)

---

## 4. Cœur applicatif

### `MainActivity.kt`
- Hôte de tous les fragments (`R.id.fragment_container`).
- Initialise `Robot.getInstance()`, `RobotController`, `AlarmScheduler`, `TimeListener`, le `ConnectivityManager`.
- Charge `FirstPage` au démarrage.
- Gère la **restauration de fragment** via `SharedPreferences "temi_state"` (utile après un appel/meeting).
- `onNewIntent` ouvre `MainPage` ou `GoToBasePage` selon l'extra `fragment_to_open` (déclenché par les alarmes).
- Champs partagés : `savePatrolStatesFileName`, `saveTimeSlotsFileName`, `serverUrl`, `userRequest`.

### `RobotController.kt` (singleton)
Couche unique au-dessus du SDK Temi. Implémente :
`AsrListener`, `TtsListener`, `OnRobotReadyListener`, `OnDetectionStateChangedListener`, `OnGoToLocationStatusChangedListener`, `OnDistanceToDestinationChangedListener`, `OnRequestPermissionResultListener`, `OnLoadMapStatusChangedListener`, `OnTelepresenceStatusChangedListener`.

Responsabilités :
- **TTS / ASR** : `speak`, `askQuestion`, `finishConversation`, `onAsrResult`. `speak()` est en **XOR** : sur la build `.test` il utilise un fallback `android.speech.tts.TextToSpeech` (initialisé via `initAndroidTts(context)` depuis `MainActivity.onCreate`, libéré dans `onDestroy`) ; en prod il utilise `Robot.speak(TtsRequest)` du SDK Temi. Le drapeau `useFallbackTts = packageName.endsWith(".test")` est posé à l'init.
- **Reconnaissance vocale par mots-clés** : ~22 destinations de la bibliothèque (`think space`, `dream space`, `idea space`, `smart learning hub`, `management collection`, `learn for life pod`, `dvds`, `smart kiosk`, `exhibition`, `book recommendations`, `lifestyle magazines`, `lifestyle books`, `cafe`, `smart space`, `design collection`, `health sciences`, `life sciences collection`, `fiction books`, `project reports`, `photocopying stations`, `performance stage`, `lifestyle media`). Si aucun mot-clé ne matche → la requête est envoyée au serveur Python.
- **Navigation** : `loadMap`, `goTo`, `patrol`, `goToHomeBase`, `stopMovement`, `tiltHead`.
- **Détection de personne** : `setDetectionModeOn`, déclenche `"Hi, how can I help you ?"` (cooldown 20 s).
- **Appel librarian** : `callLibrarian()` → `startMeeting` avec le contact nommé `Kamil`.
- **Permissions Temi** : `askRequiredPermissions()` (SETTINGS, MAP, MEETINGS).
- **Inactivité** : timer 20 s qui relance la patrouille.
- **Discours périodique** : « Please do not eat in the library… » toutes les 15 minutes pendant la patrouille.
- **Callbacks personnalisés** : `RobotReadyCallback`, `MapReadyCallback`, `RequestReadyCallback`, `BackToMainPageCallback`, `BackToBaseCallback`, `MeetingStartedCallback`.
- **États** : `isMoveRequest`, `blockMode`, `isAskSatisfiedRequest`, `isAtHomeBase`, `lastRequestTime`.
- Après une réponse du serveur, propose : *« Do you want me to call a librarian in case you're not satisfied with the answer? »*.

### `JsonManager.kt`
Helpers `restoreFromFile<T>` / `writeToFile<T>` via `kotlinx.serialization` dans `context.filesDir`.

---

## 5. Pages (Fragments)

| Page | Rôle |
|---|---|
| **FirstPage** | Splash + init système. **Demande la permission `CAMERA` une seule fois au tout démarrage** via `ActivityCompat.requestPermissions` (Android persiste l'accord). Charge la map, restaure `patrolState.json` et `timeSlots.json`. Demande « Suis-je à la home base ? Yes/No ». Règle volume (4), désactive wake-up, badge top, boutons hard. |
| **MainPage** | Page principale. Bouton **interaction** (déclenche `askQuestion`), bouton **settings**, bouton **time**. Surveille la **connectivité Wi-Fi** (→ `LostConnectionPage` en cas de perte). Démarre la **détection de geste de main** (`WaveGestureRecognizer`) → fait dire « Hello! » via `RobotController.speak` ET affiche un **overlay noir plein écran « Hello ! »** pendant 2 s (`showHelloOverlay()` → toggle `R.id.helloOverlay`). Désactive `Robot.setDetectionModeOn` à `initWaveDetector()` pour libérer la caméra frontale (réactivée à `onDestroyView`). Si pas `notPatrolAgain="true"` → lance la patrouille. La permission caméra n'est plus demandée ici (déplacée dans `FirstPage`). |
| **LoadingPage** | Pendant l'envoi de la requête utilisateur au serveur Python via OkHttp (`POST /process`). Indicateur circulaire Compose. Le robot prononce la réponse reçue. |
| **GoToBasePage** | Affichée pendant le retour à la base. Désactive la détection. |
| **LostConnectionPage** | Wi-Fi perdu → envoie le robot à la base et attend le retour de la connexion. |
| **PasswordPage** | Saisie de mot de passe (`nyp123`) avant `LocationsSettingsPage` ou `TimeSettingsPage`. Checkbox pour afficher/masquer. |
| **RestartPage** | Demande à redémarrer l'app après accord de nouvelles permissions. |
| **LocationsSettingsPage** | Liste `RecyclerView` des emplacements avec drag-and-drop (ItemTouchHelper) + checkbox pour activer/désactiver. Min **3 emplacements** pour patrouiller. Sauve dans `patrolState.json`. |
| **TimeSettingsPage** | Création de **3 max** créneaux horaires de patrouille (start/end + active). Validation : non vide, plage 0–23:0–59, fin > début, pas de chevauchement. Planifie via `AlarmScheduler`. Sauve dans `timeSlots.json`. |

---

## 6. Data classes

### `PatrolStates`
```kotlin
data class PatrolStates(
    private var locations: List<String>,
    private var states: MutableMap<String, Boolean>
)
```
- `getPatrolLocations()` : ne renvoie que les emplacements actifs.
- `putPatrolLocationsFirst()` : trie pour mettre les actifs en tête.
- `appendFirstPatrolLocation()` : fait tourner la file lors d'une étape de patrouille atteinte.

### `TimeSlot`
```kotlin
data class TimeSlot(hoursStart, minutesStart, hoursEnd, minutesEnd, isActive)
```
- `oneIsBlank()`, `isInvalidTime()`, `endsAfterStart()`, `startInMinutes()`, `endInMinutes()`.

---

## 7. Planification temporelle

### `time/AlarmScheduler.kt`
- `timeSlotsMaxNumber = 3`.
- `scheduleTimeSlotAlarm(...)` → `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, ...)`, replanifie au lendemain si l'heure est passée.
- `setAllAlarms(slots)` : annule les 1000 premiers `requestCode` puis pose 2 alarmes par slot actif (start = index, end = index + 3).
- `cancelAlarm(requestCode)`.

### `time/TimeListener.kt` (BroadcastReceiver)
- À chaque réception, replanifie l'alarme **pour le lendemain**.
- **Skip week-end** (samedi/dimanche).
- Réveille l'écran via `PARTIAL_WAKE_LOCK` (3 s).
- `type = "start"` : `finishConversation`, `patrol`, ouvre `MainPage` via `Intent` avec `FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP`, annonce « Starting my daily jogging routine ».
- `type = "end"` : `stopMovement`, `goToHomeBase`, ouvre `GoToBasePage`, annonce « I finished my work bye bye ».

---

## 8. Détection de geste (vague de main)

### `detecttion/WaveGestureRecognizer.kt`

**Pipeline** : CameraX → MediaPipe Tasks Vision `HandLandmarker` (mode `LIVE_STREAM`, 1 main, confidences 0.3) → filtre open-palm → heuristique d'oscillation → callback.

**Caméra** : énumère les caméras disponibles au bind. Préfère `DEFAULT_FRONT_CAMERA`, sinon fallback sur `DEFAULT_BACK_CAMERA` (sur le Temi, la cam unique est parfois exposée comme `LENS_FACING_BACK` malgré l'orientation vers l'utilisateur). Si aucune dispo → abort + log d'erreur.

**Modèle** : `assets/hand_landmarker.task` (non tracké git — à redéployer manuellement sur un fresh clone).

**Filtre open-palm** (`isOpenPalm()`) : ne nourrit l'historique de mouvement QUE si au moins **3 doigts sur 4** (index, majeur, annulaire, auriculaire — pouce ignoré) sont étendus. Critère : `dist(wrist, fingertip) > dist(wrist, PIP_joint)` en 2D normalisé. Tue les faux positifs « main posée en frame » qui transformaient le wave en simple détecteur de présence.

**Heuristique de wave** (post-#11/#12, valeurs courantes) :

| Param | Valeur | Rôle |
|---|---|---|
| `WINDOW_MS` | 2000 | fenêtre glissante d'évaluation |
| `MIN_SAMPLES` | 4 | nb min de samples open-palm dans la fenêtre |
| `MIN_AMPLITUDE` | 0.10 | déplacement min du poignet sur axe X (fraction de largeur) |
| `MIN_DIRECTION_CHANGES` | 3 | nb min de reversals dans la fenêtre |
| `NOISE_THRESHOLD` | 0.010 | delta sous ce seuil = bruit, ignoré |
| `COOLDOWN_MS` | 4 000 | **valeur de test** (cible prod : 10 000) |
| `MIN_EXTENDED_FINGERS` | 3 | seuil de l'open-palm |
| MediaPipe confidences | 0.3 | detection / presence / tracking |

Après chaque trigger, `wristXHistory.clear()` pour éviter le re-trigger immédiat.

**Callback** `onWaveDetected` → `MainPage` exécute sur le UI thread : `RobotController.speak("Hello!")` + `showHelloOverlay()` (2 s).

**Conflit caméra Temi** : `MainPage.initWaveDetector()` appelle `RobotController.setDetectionModeOn(false, 0.5f)` pour libérer la cam frontale (sinon CameraX échoue silencieusement). Réactivée dans `onDestroyView`. Conséquence : tant que `MainPage` est affichée, la détection de personne native Temi (auto « Hi, how can I help you? ») est désactivée — il faut cliquer le bouton ou agiter la main.

**Logs de diagnostic** (tag `WaveGestureRecognizer`) :
- `MediaPipe HandLandmarker initialized` au chargement du modèle.
- `Available cameras: N` + liste lensFacing par caméra.
- `Using FRONT camera` ou `falling back to BACK camera`.
- `Camera bound + analysis use case active`.
- `Camera frames processed: N (hand-detected: M, open-palm: P)` à la 1re frame et toutes les 60. Le ratio `open-palm / hand-detected` indique si le filtre rejette trop.
- `First hand detected` à la première main vue.
- `Wave detected!` au déclenchement.

---

## 9. UI utils

- `ui_utils/LoadingScreen.kt` : `CircularProgressIndicator` (Compose) blanc 80 dp.
- `ui_utils/SimpleAdapter.kt` : adapter `RecyclerView` pour la liste des emplacements (drag-and-drop, checkbox, `moveItem`, `updatePatrolStates`).
- `ui/theme/` : thème Material3 Compose (Color, Theme, Type).

---

## 10. Layouts XML (res/layout)

`box_bar`, `layout_first`, `layout_go_base`, `layout_loading`, `layout_lost_connection`, `layout_main_activity` (FrameLayout `fragment_container`), `layout_main_page` (root devenu `FrameLayout` pour empiler le `helloOverlay` plein écran noir avec « Hello ! » au-dessus du contenu normal), `layout_password`, `layout_restart`, `layout_settings`, `layout_time`, `time_slot`.

Drawables : boutons colorés transparents (beige/blue/brown/red/turquoise/yellow), `checkbox_*`, `drawable_first_page`, `drawable_hourglass`, `drawable_password_background`, `drawable_settings`, `trash`/`trash2`, etc.

---

## 11. Dépendances (gradle/libs.versions.toml)

- **Build** : AGP **8.12.3**, Gradle wrapper **8.13**, Kotlin **2.1.21**
- **AndroidX** : core-ktx 1.16.0, lifecycle-runtime-ktx **2.9.4**, activity-compose 1.10.1, appcompat **1.7.1**, recyclerview 1.4.0, constraintlayout 2.2.1
- **Jetpack Compose** : BOM 2026.04.01 (ui, ui-graphics, material3, ui-tooling-preview)
- **Temi SDK** : `com.robotemi:sdk:1.137.1`
- **MediaPipe** : `com.google.mediapipe:tasks-vision:0.10.21`
- **CameraX** : 1.5.2 (core, camera2, lifecycle)
- **Réseau** : `okhttp:4.12.0` (dernière 4.x — 5.x dispo mais non adopté)
- **Sérialisation** : `kotlinx-serialization-json:`**1.7.3** (plugin Kotlin **2.1.21**)
- **Tests** : junit 4.13.2, androidx test/espresso, compose ui-test
- (Déclarés non utilisés dans `build.gradle.kts` actuel : glide, hilt, jsch, media3, navigation-fragment)

---

## 12. Flux d'exécution typique

1. **Boot app** → `MainActivity.onCreate` → `FirstPage`.
2. `onRobotReady` → permissions Temi → restauration `patrolState.json` + `timeSlots.json` → chargement map.
3. Utilisateur clique **Yes** (à la base) → `MainPage` → patrouille auto.
4. Personne détectée (≤ 0.5 m, hors cooldown) → `askQuestion("Hi, how can I help you?")`.
5. **ASR** : si mot-clé connu → `goTo(destination)` ; sinon → `LoadingPage` → POST `serverUrl` → robot prononce la réponse → propose appel librarian (Kamil) si non satisfait.
6. Inactivité 20 s → reprise de la patrouille.
7. Toutes les 15 min en patrouille → rappel « do not eat in the library ».
8. **Alarme `start`** (jour de semaine) → ouvre `MainPage`, lance patrouille. **Alarme `end`** → `GoToBasePage`, retour base.
9. Wi-Fi perdu → `LostConnectionPage` + retour base ; rétabli → retour `MainPage`.
10. Wave de main détecté (paume ouverte 3/4 doigts + oscillation latérale) → robot dit « Hello! » + overlay noir « Hello ! » plein écran 2 s. Cooldown 4 s en test (10 s ciblé en prod).

---

## 13. Persistance

| Fichier (`filesDir`) | Contenu |
|---|---|
| `patrolState.json` | `PatrolStates` sérialisé |
| `timeSlots.json` | `List<TimeSlot>` sérialisé |

`SharedPreferences "temi_state"` :
- `last_fragment` (FQN du fragment à restaurer après un meeting/alarme)
- `should_restore_fragment` (Boolean)

---

## 14. Points notables / pièges connus

- Package `detecttion/` (double `t`) — l'import Kotlin utilise `com.temi.temi_robot.detection` (le package interne déclaré).
- Mot de passe en clair dans `PasswordPage.kt` (`nyp123`).
- URL serveur codée en dur dans `MainActivity` (`192.168.1.79:5000`).
- Contact librarian codé en dur (`name == "Kamil"`).
- `setAllAlarms` boucle de 0 à 999 pour annuler — fonctionne mais peu élégant.
- Pas de tests unitaires en place (les dépendances JUnit sont présentes mais inutilisées).
- `README.md` quasi vide (`#README`).
- **Wave gesture vs détection Temi** : pendant que `MainPage` est affichée, la détection native Temi est coupée pour libérer la caméra → l'auto-prompt « Hi, how can I help you ? » au passage d'une personne ne se déclenche plus dans cette page. Trade-off accepté pour le wave gesture.
- **Fichier modèle `hand_landmarker.task`** dans `assets/` non tracké par git (`??`). À ne pas oublier de redéployer manuellement sur un fresh clone.
- **Build variant `.test`** : la build debug a `applicationIdSuffix = ".test"` et cohabite avec la release sur le robot (deux icônes : « Temi library assistant » et « Temi Test »). La console dev Temi n'a enregistré QUE le package `com.temi.temi_robot` (+ son SHA de signature). En conséquence, sur la build `.test` :
  - Navigation (`goTo`, `patrol`, `goToHomeBase`), permissions Temi (`MAP`, `MEETINGS`) et les appels via `startMeeting` **ne fonctionnent pas**.
  - `Robot.speak()` semble fonctionner (à vérifier au cas par cas), mais on a observé un double Hello quand on doublait avec Android TTS → `speak()` est désormais en **XOR** (Android TTS seul sur `.test`, Temi TTS seul en prod).
  - Wave gesture + UI + sauvegarde JSON locale **fonctionnent** sur les deux variants.
- **Cooldown wave** actuellement à **4 000 ms** (valeur de test pour calibration) — à remonter à 10 000 ms avant déploiement production pour éviter le spam de Hello.
- **Pertes de bumps de versions** : les modifs de `libs.versions.toml`, `gradle-wrapper.properties` et `app/build.gradle.kts` ont été reverts au moins 2 fois entre sessions (probablement à cause de sync OneDrive ou rollback côté Johan). À commiter dès que possible.
