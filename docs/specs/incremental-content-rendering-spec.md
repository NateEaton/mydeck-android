# Post Sync-Branch TODOs

Items to research/address after the `feature/sync-multipart-v012` branch is merged.

## Incremental content rendering

Research loading article content incrementally (progressive display) instead of showing a spinner until everything is ready. Currently, articles with many large images (30+) can take noticeable time before anything appears. Investigate:

- Loading HTML first and letting images stream in naturally via Coil/WebView
- Whether `loadDataWithBaseURL` blocks rendering until all resources are resolved
- Progressive image loading (placeholder → full resolution)
- Prioritising above-the-fold images
