# Bonnes pratiques transverses — testabilité & découplage du framework

Consigne utilisateur : faire une vraie **injection de dépendances** pour que tout soit testable, suivre les bonnes pratiques, et **ne pas être bloqué par Spring** pour les tests (ne pas exiger un contexte Spring pour tester la logique). Décisions « de la même famille » à appliquer systématiquement :

1. **Injection par constructeur partout** — jamais de `@Autowired` sur champ. Chaque classe est instanciable en test sans contexte Spring.
2. **Domaine sans annotations framework** — entités & value objects du domaine = POJO purs (pas de `@Entity`/`@Component`/`@Autowired`). La persistance a son **propre modèle JPA** mappé vers/depuis le domaine. → domaine testable, portable, renforce D7.
3. **Pyramide de tests** — beaucoup de tests unitaires POJO rapides (domaine + application, sans Spring) ; peu de tests d'intégration (`@SpringBootTest` + Testcontainers) réservés aux **adapters** (JPA, GCS, ClamAV, Pub/Sub).
4. **Doubles de test = adapters in-memory** — les ports (`FileStorage`, `AntivirusScanner`, `ScanQueue`, `FileMetadataRepository`) ont des implémentations in-memory/fakes qui servent au dev local **et** aux tests. Éviter le mocking massif.
5. **Injecter `Clock` (java.time.Clock)** — au lieu d'appeler `Instant.now()` en dur. Rend testables TTL, retries, timestamps (temps déterministe).
6. **Injecter la génération d'ID** (`IdGenerator`/UUID) — tests déterministes, pas de `UUID.randomUUID()` en dur.
7. **Config typée** — `@ConfigurationProperties` injectées plutôt que des `@Value` éparpillés. Explicite et testable.
8. **Exceptions du domaine → HTTP dans un `@ControllerAdvice`** — le domaine ne connaît pas HTTP ; la traduction se fait à la frontière web.
9. **Transactions à la frontière applicative** — dans les services d'application, pas dans le domaine.
10. **Value objects immuables** (`ScanVerdict`, `FileStatus`) — raisonnement et tests simplifiés.
11. **Pas d'état statique/singleton caché** ni d'appels statiques non substituables.

13. **Packages d'infrastructure organisés par rôle** — quand un package d'adapters accumule plusieurs rôles, le découper en **sous-packages** (`entities` / `repositories` / `adapters`) plutôt qu'à plat (exigence utilisateur, J3.1 : `infrastructure/persistence/jpa/{entities,repositories,adapters}`). Conséquence : les méthodes de mapping (`fromDomain`/`toDomain`) deviennent `public` car elles traversent des packages.

12. **DTOs groupés dans un conteneur au nom explicite** — regrouper les records liés dans une classe conteneur bien nommée (`UploadRequests`, `UploadCommands`, `FileViews`), plutôt qu'un fichier par record. Nom décrivant le groupe (pas de `ApiDtos` fourre-tout). Convention homogène couche web + application (décidée en Task 15 après avoir essayé l'éclatement).

Fil conducteur : la logique métier ne dépend que de **ports (interfaces)** injectés → testable en isolation, framework à la périphérie. Cohérent avec SOLID (D2, D7) et « sans sur-ingénierie » (un adapter réel + un adapter in-memory par port).
