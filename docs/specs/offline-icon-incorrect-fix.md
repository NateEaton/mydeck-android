Based on the symptoms described—the app displaying an offline/disconnected icon (like a `cloud_off` indicator) despite the device having both active Wi-Fi and Cellular connections—this behavior points to a highly common state-management flaw in Android's `ConnectivityManager` implementation. 

Because MyDeck is a modern Jetpack Compose app tracking network state reactively, here is a detailed breakdown of the likely culprits in the codebase and how to fix them.

### 1. The `registerNetworkCallback` vs. `registerDefaultNetworkCallback` Bug (Most Likely)
When an app observes network connectivity, developers typically create a `NetworkCallback` inside a `Flow` to push online/offline states to the UI. If the codebase uses `registerNetworkCallback()` instead of `registerDefaultNetworkCallback()`, it listens to **all** available networks rather than just the one Android is actively using.

**How the bug triggers with both Wi-Fi and Cellular:**
1. Android registers both the Wi-Fi and Cellular connections as active networks. 
2. The app's `onAvailable(network)` fires twice (once for Wi-Fi, once for Cellular), setting the app state to **Online**.
3. To preserve battery, the Android OS frequently puts the cellular data radio to sleep when it detects a stable Wi-Fi connection.
4. When the cellular radio enters standby, the `ConnectivityManager` fires the `onLost(network)` callback for the cellular network.
5. **The Flaw:** The app's callback receives `onLost` and blindly updates the global UI state to **Offline**, completely ignoring the fact that the Wi-Fi network is still active and providing full internet access.

**The Fix:**
Change the network monitor implementation to observe only the default routing network:
```kotlin
// Change this:
val request = NetworkRequest.Builder().addCapability(NET_CAPABILITY_INTERNET).build()
connectivityManager.registerNetworkCallback(request, callback)

// To this:
connectivityManager.registerDefaultNetworkCallback(callback)
```
*Alternatively, if `registerNetworkCallback` must be used, the app should maintain a `MutableSet<Network>` of active connections and only declare itself offline when the set's size reaches `0`.*

### 2. Network Capability Validation Delays (`NET_CAPABILITY_VALIDATED`)
If the network observer explicitly checks for `NetworkCapabilities.NET_CAPABILITY_VALIDATED`, you can get false "offline" UI flickers.
When a device balances Wi-Fi and Cellular, the active default network can briefly switch. When Android connects to a network, it takes a few moments to ping its captive portal servers (e.g., `connectivitycheck.gstatic.com`). During this window, the network has `NET_CAPABILITY_INTERNET` but is not yet `VALIDATED`. If the app aggressively requires the validated flag, it will show the offline icon until the OS finishes its ping in the background.

### 3. DNS / Local IP Routing Conflicts (Self-Hosted Server Edge Case)
Because Readeck is a self-hosted service, your server URL might be a local network IP (e.g., `192.168.1.100`) or a local hostname (`readeck.local`). 
When both Wi-Fi and Cellular are active, Android sometimes exhibits aggressive multi-network routing. If the app tries to verify the server's specific reachability (e.g., a `/api/health` ping) rather than just general internet connectivity:
* Android might attempt to route the request through the Cellular interface (which cannot see your local Wi-Fi IP).
* The server ping fails, and the app's repository layer falls back to a locally cached "Offline" state, assuming the server is unreachable, and throws the offline icon into the app header.

### Summary of Action Items for Code Review:
1. Search the repository for `ConnectivityManager` and locate the `NetworkCallback`.
2. Check if the code is tracking all networks naively and overwriting the global connection state to `false` when a single network triggers `onLost`. 
3. Switch the tracking mechanism to `registerDefaultNetworkCallback()`. 
4. Check if the offline indicator is tied to standard Android connectivity APIs or tied directly to Retrofit/API timeout failures. If it's tied to API timeouts, the local network routing (Cellular overriding Wi-Fi for local IPs) is to blame.