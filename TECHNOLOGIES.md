# 📱 OfflineChatSecure: Technologies & architecture

This document outlines the primary libraries and software engineering techniques used to build **OfflineChatSecure**, a fully offline Android messaging application.

## 📚 Main Libraries

The project relies on native Android APIs combined with essential AndroidX Jetpack libraries:

| Library / Component | Purpose |
|---------------------|---------|
| **`androidx.biometric:biometric`** | Implements the app's security layer, requiring fingerprint or face unlock to access the application and chats. |
| **`androidx.recyclerview:recyclerview`** | Provides efficient, scrollable list UIs for the device discovery list and the chat message history. |
| **`androidx.swiperefreshlayout`** | Enables the "pull-to-refresh" gesture in the device discovery screen to restart Bluetooth scanning. |
| **`android.database.sqlite`** | Native SQLite database package used via `SQLiteOpenHelper` (`DBHelper`) to persist chat messages locally on the device. |

---

## 🛠️ Main Techniques & Architecture

### 1. Bluetooth RFCOMM Sockets (The Core Core)
The app operates entirely offline using classic Bluetooth **RFCOMM sockets**:
- **`AcceptThread` (Server):** Listens for incoming connections using `listenUsingRfcommWithServiceRecord` and a custom Service UUID.
- **`ConnectThread` (Client):** Initiates connections to nearby peers using `createRfcommSocketToServiceRecord`.
- **Insecure Fallbacks:** The app utilizes robust connection logic that falls back to `listenUsingInsecureRfcommWithServiceRecord` if secure socket creation fails, ensuring high compatibility across OEM devices.

### 2. "Presence Ping" Reachability System
Android aggressively caches Bluetooth Service Discovery Protocol (SDP) results, which causes "active" statuses to remain stuck. The app circumvents this by using a **Custom Presence Server**:
- A secondary background thread listens on a specific `PRESENCE_SERVICE_UUID`.
- When scanning for known peers, the app dispatches a rapid background ping connection to this UUID, immediately shutting the socket down upon success. This physically proves the peer is reachable and avoids cached UUID false-positives.

### 3. Multithreading & Executors
Bluetooth I/O is strictly blocking and cannot be run on the main thread:
- **`Thread` Extensions:** Dedicated classes (`AcceptThread`, `ConnectThread`, `ConnectedThread`) manage socket lifecycles.
- **`ExecutorService` / `Executors`:** Used for async operations like file sending (`singleThreadExecutor`), background presence pings (`cachedThreadPool`), and database queries.
- **`Handler(Looper.getMainLooper())`:** Safely posts background network events (messages received, connection lost) back to the UI thread to update the screen.

### 4. Custom Transport Protocol
Rather than sending raw bytes aimlessly, the app wraps data over the stream:
- **Prefix Routing:** Messages are prefixed (e.g., `T:` for text, `F:` for files).
- **File Chunking:** Large files and images are sent in **8192-byte (8KB) chunks** using `BufferedOutputStream` and base64 encoding to track progress percentages without memory overflows.

### 5. Biometric Activity Lifecycle Management
App security isn't just at startup. Using `AppAuthState` and activity lifecycle callbacks (`onResume`, `onPause`), the app tracks backgrounding events to prompt re-authentication seamlessly when the user switches tasks.

### 6. Modern Bluetooth Permissions (Android 12+)
The app correctly isolates legacy Bluetooth permissions from Android 12 (API 31+) permissions:
- Handled dynamically using `ActivityResultContracts.RequestMultiplePermissions`.
- Distinguishes between `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, and explicit `ACCESS_FINE_LOCATION` requirements to comply with recent Google Play privacy policies.

