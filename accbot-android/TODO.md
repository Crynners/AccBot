# AccBot Android - Code Review TODO

## CRITICAL - Must Fix

- [x] 1. Fix memory leaks: cancel Flow collections in ViewModels (Dashboard, History, PlanDetails)
- [x] 2. Fix DcaForegroundService: add serviceScope.cancel() in onDestroy()
- [x] 3. Fix OkHttpClient duplication: inject singleton into exchange APIs via factory
- [x] 4. Replace Thread.sleep(1000) with delay(1000) in CoinmateApi
- [~] 5. N+1 queries in SyncDailyPricesUseCase — SKIPPED: N is number of crypto/fiat pairs (1-5), indexed Room queries, bottleneck is network calls with rate limiting
- [x] 6. Fix TypeConverter silent fallbacks: add logging instead of silent defaults
- [x] 7. Fix biometric lock bypass: use remember instead of rememberSaveable
- [x] 8. Fix BootReceiver/ExactAlarmPermissionReceiver: add withTimeout
- [x] 9. Fix DcaAlarmScheduler: add withContext(Dispatchers.IO)

## HIGH - Important

- [x] 10. Add compound database indexes for common query patterns (+migration 6→7)
- [x] 11. Fix MarketDataService cache stampede with Mutex
- [x] 12. Add backoff policy to expedited OneTimeWork in DcaWorker.runFromAlarm()
- [x] 13. Add error handling on DAO insert/update in DcaWorker
- [x] 14. Add missing keys to LazyColumn/LazyRow items
- [x] 15. Remove duplicate Navigation.kt (dead code)
- [x] 16. Add withTimeout to balance fetches in DashboardViewModel and PlanDetailsViewModel

## MEDIUM - Code Quality

- [~] 17. Extract cost estimation logic — SKIPPED: pure refactor, no perf/battery impact
- [x] 18. Remove unused dependencies (Retrofit, logging-interceptor, DataStore) + add explicit Gson
- [~] 19. Upgrade security-crypto — SKIPPED: library deprecated, no stable release; alpha06 is fine
- [x] 20. Fix ProGuard rules (remove dead Retrofit rules, add ML Kit/CameraX/Compose/Vico rules)
- [~] 21. KSP version — SKIPPED: KSP 2.3.5 is already compatible with Kotlin 2.3.10
- [~] 22. WAL journal mode — SKIPPED: Room 2.8.4 defaults to WAL already
- [x] 23. Remove dead UI components (ContentWithLoadingAndError)
- [~] 24. rememberSaveable for UI state — SKIPPED: minor UX, no battery/perf impact
- [x] 25. Add guard against concurrent deleteAllData() in SettingsViewModel
- [x] 26. Increase Gradle JVM heap (2G→4G) + add parallel/caching build flags
- [~] 27. hmacSha512Base64Secret refactor — SKIPPED: Kraken signing is multi-step, not a simple HMAC wrapper
- [x] 28. Validate clientId in CoinmateApi constructor
