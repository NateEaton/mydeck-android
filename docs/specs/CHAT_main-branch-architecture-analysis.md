# Main Branch Architecture Analysis (Sync, Resources, Reader, Annotations)

## High-level flow diagram (text)

```
User action / auto schedule
  ├─ ActionSyncWorker → BookmarkRepository.syncPendingActions (offline actions)
  ├─ LoadBookmarksWorker (initial/full or delta)
  │    ├─ BookmarkRepository.performFullSync / performDeltaSync
  │    └─ LoadBookmarksUseCase → MultipartSyncClient.fetchMetadata → BookmarkRepository.insertBookmarks
  │         └─ enqueue BatchArticleLoadWorker if offline auto-fetch enabled
  ├─ FullSyncWorker (auto/manual periodic)
  │    ├─ syncPendingActions → performFullSync or performDeltaSync (fallback to full)
  │    └─ LoadBookmarksUseCase for updated IDs + enqueue content sync
  └─ Content sync workers
       ├─ BatchArticleLoadWorker / DateRangeContentSyncWorker → LoadContentPackageUseCase.executeBatch
       │    ├─ MultipartSyncClient.fetchContentPackages / fetchTextOnly
       │    ├─ AnnotationHtmlEnricher + annotations API
       │    └─ ContentPackageManager.commitPackage (files + DB)
       └─ BookmarkDetailViewModel content load / annotation refresh
            ├─ ContentPackageManager.getContentDir / updateHtml
            └─ WebView renders via OfflineContentPathHandler + JS bridges
```

Citations: Action sync + full/delta sync pipeline @app/src/main/java/com/mydeck/app/worker/ActionSyncWorker.kt#19-47, @app/src/main/java/com/mydeck/app/worker/LoadBookmarksWorker.kt#42-123, @app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt#36-154, @app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt#34-137. Content workers + package load/commit @app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt#31-114, @app/src/main/java/com/mydeck/app/worker/DateRangeContentSyncWorker.kt#26-67, @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#87-221, @app/src/main/java/com/mydeck/app/domain/content/ContentPackageManager.kt#31-203. Reader/offline path @app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt#231-276, @app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt#113-385, @app/src/main/java/com/mydeck/app/domain/content/OfflineContentPathHandler.kt#11-54.

## Key classes/files (with roles)

### Sync flows
- **FullSyncWorker**: Orchestrates periodic/manual sync; drains pending actions, chooses delta vs full, falls back to full, then loads metadata for updated IDs and marks content freshness.  
  @app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt#34-137
- **LoadBookmarksWorker**: Initial full sync vs delta sync on refresh; handles timestamp cursor and triggers multipart metadata load.  
  @app/src/main/java/com/mydeck/app/worker/LoadBookmarksWorker.kt#42-123
- **LoadBookmarksUseCase**: Multipart metadata fetch (POST /bookmarks/sync) and enqueue content sync.  
  @app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt#36-154
- **BookmarkRepository**: Defines performFullSync/performDeltaSync and syncPendingActions contract.  
  @app/src/main/java/com/mydeck/app/domain/BookmarkRepository.kt#43-98
- **ActionSyncWorker**: Syncs queued offline actions via repository.  
  @app/src/main/java/com/mydeck/app/worker/ActionSyncWorker.kt#19-47
- **MultipartSyncClient**: Executes metadata/content multipart sync and parses batches.  
  @app/src/main/java/com/mydeck/app/io/rest/sync/MultipartSyncClient.kt#28-151

### Resource management
- **ContentPackageManager**: Atomic commit of package (stage → DB → swap), update HTML only, delete resources or full package, and calculate size.  
  @app/src/main/java/com/mydeck/app/domain/content/ContentPackageManager.kt#31-307
- **LoadContentPackageUseCase**: Fetch content packages, decide resource fetch vs text-only, enrich annotations, commit package.  
  @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#87-221
- **BatchArticleLoadWorker / DateRangeContentSyncWorker**: Background batch content download with constraints and storage pruning.  
  @app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt#31-114, @app/src/main/java/com/mydeck/app/worker/DateRangeContentSyncWorker.kt#26-67
- **OfflineContentPathHandler**: WebView asset loader for offline files (path traversal safeguards).  
  @app/src/main/java/com/mydeck/app/domain/content/OfflineContentPathHandler.kt#11-54

### Reader pipeline
- **BookmarkDetailViewModel**: Initializes bookmark, checks content state, triggers annotation sync for offline packages.  
  @app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt#231-276
- **BookmarkDetailWebViews**: Builds WebView + OfflineContentPathHandler loader, applies JS bridges, delivers annotation taps.  
  @app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt#113-385

### Annotation logic
- **AnnotationHtmlEnricher**: Restores attributes for bare `<rd-annotation>` tags from multipart sync.  
  @app/src/main/java/com/mydeck/app/domain/content/AnnotationHtmlEnricher.kt#7-85
- **LoadContentPackageUseCase.refreshHtmlForAnnotations**: Legacy article endpoint preferred, multipart fallback + enrichment + relative URL rewrite.  
  @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#254-385
- **LoadArticleUseCase**: Annotation snapshot caching for non‑multipart articles; refreshes HTML if annotations changed.  
  @app/src/main/java/com/mydeck/app/domain/usecase/LoadArticleUseCase.kt#90-138, @app/src/main/java/com/mydeck/app/domain/usecase/LoadArticleUseCase.kt#166-198
- **BookmarkDetailViewModel (content packages)**: Checks annotation snapshots, refreshes HTML if changed.  
  @app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt#1009-1035
- **WebViewAnnotationBridge**: JS injection for annotation taps/selection and rendered annotation extraction.  
  @app/src/main/java/com/mydeck/app/ui/detail/WebViewAnnotationBridge.kt#14-566

## Constraints / coupling points

1. **Sync ordering & timestamps**: FullSyncWorker drains pending actions before sync and relies on SettingsDataStore timestamps; delta sync failure triggers full sync fallback.  
   @app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt#37-137

2. **Delta/metadata coupling**: LoadBookmarksWorker uses delta sync cursor and then multipart metadata fetch to update bookmarks; it also updates last sync timestamps.  
   @app/src/main/java/com/mydeck/app/worker/LoadBookmarksWorker.kt#77-113

3. **Content sync policy**: Content auto-fetch depends on SettingsDataStore + connectivity/battery constraints.  
   @app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt#121-150, @app/src/main/java/com/mydeck/app/domain/sync/ContentSyncPolicyEvaluator.kt#15-39

4. **File system + DB atomicity**: ContentPackageManager commits both DB metadata and file system staging; rollback logic depends on successful rename/copy.  
   @app/src/main/java/com/mydeck/app/domain/content/ContentPackageManager.kt#31-203

5. **Offline resource URL assumptions**: Offline HTML expects relative resource URLs and uses OfflineContentPathHandler’s base URL; annotation refresh rewrites URLs when resources exist.  
   @app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt#123-133, @app/src/main/java/com/mydeck/app/domain/content/OfflineContentPathHandler.kt#11-54, @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#299-382

6. **Annotation enrichment is text‑matching**: AnnotationHtmlEnricher matches bare tags by text; accuracy depends on consistent HTML + annotation text.  
   @app/src/main/java/com/mydeck/app/domain/content/AnnotationHtmlEnricher.kt#24-75

7. **Annotation snapshot caching**: Both content packages and legacy article paths rely on SettingsDataStore snapshots to detect changes and avoid reloads.  
   @app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt#1009-1035, @app/src/main/java/com/mydeck/app/domain/usecase/LoadArticleUseCase.kt#90-138

## Risks likely impacted by changes

1. **Sync cursor drift / missed updates**: Changes to sync timestamps or delta/full selection can skip metadata or re-fetch unnecessarily.  
   @app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt#42-123, @app/src/main/java/com/mydeck/app/worker/LoadBookmarksWorker.kt#77-113

2. **Content rendering breakage**: Any change to resource prefixing or offline base URL handling can break image resolution in WebView.  
   @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#299-382, @app/src/main/java/com/mydeck/app/domain/content/OfflineContentPathHandler.kt#11-54, @app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt#123-133

3. **Annotation mismatch / stale data**: Enrichment relies on text matching and cached snapshot logic; HTML structure or API changes can desync highlights.  
   @app/src/main/java/com/mydeck/app/domain/content/AnnotationHtmlEnricher.kt#24-75, @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#254-345, @app/src/main/java/com/mydeck/app/domain/usecase/LoadArticleUseCase.kt#90-138

4. **Atomic commit regression**: Modifying file operations, DB updates, or rollback logic in ContentPackageManager risks corrupted or “DIRTY” content states.  
   @app/src/main/java/com/mydeck/app/domain/content/ContentPackageManager.kt#31-214

5. **Auto-sync behavior changes**: Content sync gating (Wi‑Fi/battery) is used in workers and in auto‑enqueue; updates could unexpectedly block downloads.  
   @app/src/main/java/com/mydeck/app/domain/sync/ContentSyncPolicyEvaluator.kt#15-39, @app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt#121-150, @app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt#46-49

6. **Storage pruning side‑effects**: Image storage pruning relies on text‑only overwrite; changes could leave broken HTML/resource links.  
   @app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt#88-113, @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#72-85
