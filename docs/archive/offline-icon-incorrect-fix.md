## Offline Icon Incorrect Fix

**Status: COMPLETE**

### Symptom
The app displayed an offline/disconnected icon in the header despite the device having active Wi-Fi and/or Cellular connectivity.

### Root Cause
In `ConnectivityMonitorImpl.observeConnectivity()`, the `onLost` callback unconditionally emitted `false` (offline):

```kotlin
override fun onLost(network: Network) {
    trySend(false)  // bug: blindly assumed offline
}
```

The app correctly uses `registerDefaultNetworkCallback`, so `onLost` only fires when Android's *default* network is lost or replaced. However, during a network transition (e.g., Wi-Fi → Cellular handoff, or network re-evaluation), Android fires `onLost` for the outgoing network before `onAvailable` fires for the incoming one. During that brief gap, the app incorrectly set itself to offline — even if another network was already active.

### Fix
Changed `onLost` to re-check actual network availability instead of assuming offline:

```kotlin
override fun onLost(network: Network) {
    trySend(isNetworkAvailable())
}
```

`isNetworkAvailable()` uses `connectivityManager.activeNetwork` with `NET_CAPABILITY_VALIDATED`, which correctly reflects the post-transition state. If Android has already promoted a new default network by the time `onLost` fires, the app stays online.

**File changed:** `app/src/main/java/com/mydeck/app/domain/sync/ConnectivityMonitorImpl.kt`
