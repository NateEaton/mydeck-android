# Branch Architecture Analysis: Sync, Resources, Reader, Annotations

## Scope
This document summarizes the current branch’s architecture for sync flows, offline resource management, reader pipeline, and annotation logic.

## High-level flow diagram (text)

```
User/auto sync
  -> FullSyncUseCase schedules WorkManager jobs
  -> FullSyncWorker
     -> syncPendingActions() (action queue)
     -> performDeltaSync() or performFullSync() via BookmarkRepositoryImpl
     -> LoadBookmarksUseCase (multipart metadata fetch)
     -> enqueueContentSyncIfNeeded() if policy allows
        -> BatchArticleLoadWorker
           -> LoadContentPackageUseCase.executeBatch()
              -> MultipartSyncClient (text-only or full packages)
              -> ContentPackageManager.commitPackage() + annotation parsing

Reader open
  -> BookmarkDetailViewModel resolves contentState
     -> ContentPackageManager for offline dir + HTML
     -> BookmarkDetailWebViews
        -> WebViewAssetLoader + OfflineContentPathHandler for offline assets
        -> WebView bridges (theme, typography, image, annotation JS)
  -> Annotation refresh (on load or edit)
     -> BookmarkDetailViewModel.syncAnnotationsForContentPackage()
        -> LoadContentPackageUseCase.refreshHtmlForAnnotations()
           -> Legacy /article HTML or multipart HTML-only + AnnotationHtmlEnricher
        -> ContentPackageManager.updateHtml() + JS in-place refresh
```

## Key classes/files (with citations)

### Sync flows
- **FullSyncUseCase**: schedules manual/auto full sync WorkManager jobs, exposes running state. @app/src/main/java/com/mydeck/app/domain/usecase/FullSyncUseCase.kt#18-96
- **FullSyncWorker**: drains pending actions, chooses delta vs full sync, falls back to full on delta error, then reloads metadata via multipart if needed and triggers content sync. @app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt#34-134
- **BookmarkRepositoryImpl**: implements `performFullSync`, `performDeltaSync`, and `syncPendingActions` to talk to Readeck and update local DB. @app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt#699-858
- **LoadBookmarksUseCase**: multipart metadata fetch for updated IDs and content-sync enqueue based on policy (now REPLACE work policy). @app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt#36-150
- **LoadBookmarksWorker**: pull-to-refresh worker now falls back to full sync when delta fails or no cursor is available. @app/src/main/java/com/mydeck/app/worker/LoadBookmarksWorker.kt#77-130
- **MultipartSyncClient**: shared POST `/bookmarks/sync` client for metadata, HTML-only, text-only, and full content packages. @app/src/main/java/com/mydeck/app/io/rest/sync/MultipartSyncClient.kt#28-149
- **ActionSyncWorker + SyncScheduler**: action queue flush for offline edits (read/archive/labels). @app/src/main/java/com/mydeck/app/worker/ActionSyncWorker.kt#19-48 @app/src/main/java/com/mydeck/app/domain/sync/SyncScheduler.kt#5-15

### Resource/offline content management
- **ContentPackageManager**: atomic staging/commit, path traversal guard for resources, delete-resources safety check, and image-size calculation via DB. @app/src/main/java/com/mydeck/app/domain/content/ContentPackageManager.kt#30-361
- **BatchArticleLoadWorker**: batch content download via multipart, with concurrency guard, adaptive batch sizing, and hysteresis-based storage pruning. @app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt#33-181
- **LoadContentPackageUseCase**: per-bookmark (and batch) fetch/commit, text-only mode, picture wrapper HTML, annotation enrichment, and annotation HTML refresh. @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#24-385
- **OfflineContentPathHandler**: WebView asset loader path handler for offline resources. @app/src/main/java/com/mydeck/app/domain/content/OfflineContentPathHandler.kt#10-54

### Reader pipeline
- **BookmarkDetailViewModel**: resolves content state, decides on-demand fetch, sets offline base URL, toggles image resources, and handles annotation refresh events. @app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt#260-675
- **BookmarkDetailWebViews**: WebView setup, offline asset loader, JS bridges, and in-page enhancement injection. @app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt#123-614

### Annotation logic
- **AnnotationHtmlParser**: extracts cached annotations from HTML on package commit. @app/src/main/java/com/mydeck/app/domain/content/AnnotationHtmlParser.kt#7-74
- **AnnotationHtmlEnricher**: enriches bare `<rd-annotation>` tags with attributes from API, aborting if match quality is too low. @app/src/main/java/com/mydeck/app/domain/content/AnnotationHtmlEnricher.kt#7-89
- **LoadContentPackageUseCase.refreshHtmlForAnnotations**: prefers legacy article HTML, falls back to multipart HTML-only + enrichment. @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#287-354
- **BookmarkDetailViewModel sync paths**: legacy article refresh vs content-package refresh, lightweight HTML refresh, and annotation snapshot cache. @app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt#983-1061
- **WebViewAnnotationBridge**: JS injection for annotation taps and selection capture. @app/src/main/java/com/mydeck/app/ui/detail/WebViewAnnotationBridge.kt#298-488
- **Legacy LoadArticleUseCase**: annotation snapshot refresh for Room-stored article HTML. @app/src/main/java/com/mydeck/app/domain/usecase/LoadArticleUseCase.kt#90-139

## New behaviors vs main (observed in this branch)
> Based on in-code comments and behaviors that appear specific to this branch.

1. **Multipart sync as primary metadata/content path**: metadata updates route through POST `/bookmarks/sync`, and content downloads use multipart packages (with batch download size 10). @app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt#36-109 @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#387-492
2. **Delta sync with fallback full sync + server-time cursor**: FullSyncWorker prefers delta sync for deletions/updates but falls back to full sync on error, then uses server event time for cursor. @app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt#42-134
3. **Stage 4 removal of legacy content fallback in workers**: batch content worker explicitly notes no legacy fallback, leaving transient failures DIRTY for retry. @app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt#55-63
4. **Text-only content mode when image downloads disabled**: LoadContentPackageUseCase routes to `fetchTextOnly`; BatchArticleLoadWorker prunes resources by overwriting HTML before deleting images. @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#151-158 @app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt#147-161
5. **Image storage cap enforcement with hysteresis + DB-based sizing**: BatchArticleLoadWorker uses high/low watermarks, adaptive batch sizes, and DB-backed byte counts to prune images, restoring already-downloaded text when the limit is hit. @app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt#52-165 @app/src/main/java/com/mydeck/app/domain/content/ContentPackageManager.kt#354-361
6. **Delta sync fallback to full sync on refresh**: LoadBookmarksWorker now falls back to full sync when delta fails or no cursor is available, avoiding stale refreshes. @app/src/main/java/com/mydeck/app/worker/LoadBookmarksWorker.kt#77-130
7. **Annotation enrichment quality gate**: enrichment now aborts if fewer than 50% of annotations match, to avoid partially corrupted HTML. @app/src/main/java/com/mydeck/app/domain/content/AnnotationHtmlEnricher.kt#26-86

## Potentially fragile code paths
1. **Annotation enrichment text matching**: AnnotationHtmlEnricher relies on matching stripped inner text to annotation DTOs; subtle HTML differences or duplicate text can cause missed matches. @app/src/main/java/com/mydeck/app/domain/content/AnnotationHtmlEnricher.kt#27-75
2. **HTML refresh + resource URL rewriting**: `refreshHtmlForAnnotations` rewrites absolute URLs to relative for offline use; any mismatch in server URL patterns or local storage layout could break images. @app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt#310-384
3. **Content package atomic swap**: commitPackage uses staging/backup directory moves; failures in file moves trigger DB rollback + DIRTY state, which could leave file/DB mismatches if filesystem operations are partially successful. @app/src/main/java/com/mydeck/app/domain/content/ContentPackageManager.kt#68-216
4. **Delete-resources workflow ordering**: `deleteResources` now aborts when HTML still contains relative URLs; callers must successfully refresh HTML before deleting resources, or storage pruning will stall. @app/src/main/java/com/mydeck/app/domain/content/ContentPackageManager.kt#290-331
5. **Storage pruning + restoreDownloadedState**: the worker can restore DOWNLOADED state when the image limit is hit, which may mask incomplete image downloads if DB state and files drift. @app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt#70-99 @app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt#642-660
6. **Annotation refresh in reader relies on cached snapshots**: content-package annotation refresh compares REST snapshots to cached values; cache misses or stale snapshots can lead to unnecessary refresh or missed updates. @app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt#1000-1037
7. **WebView JS bridges for annotations**: annotation tap/selection relies on injected JS and DOM structure (`.container`, `rd-annotation` tags); template changes could break tap/selection handling. @app/src/main/java/com/mydeck/app/ui/detail/WebViewAnnotationBridge.kt#298-488

