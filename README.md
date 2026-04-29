# 📱 OfflineChatSecure

OfflineChatSecure is a fully offline, peer-to-peer secure messaging Android application. It allows users to communicate without an internet connection or cellular network by leveraging classic Bluetooth (RFCOMM) sockets. The app also features biometric authentication to secure your private conversations.

## ✨ Features

*   **100% Offline Messaging:** Chat with nearby peers using Bluetooth without relying on any external servers or internet.
*   **Biometric Security:** App entry and sensitive actions (like deleting history) are protected by fingerprint or face unlock.
*   **Real-Time Device Discovery:** Scan and discover nearby Bluetooth devices.
*   **Presence Detection:** Custom background "Presence Ping" system to accurately detect if a previously paired peer is currently reachable and has the app open.
*   **Media & File Sharing:** Send text messages, images, and files directly over Bluetooth with chunked real-time progress tracking.
*   **Local Storage:** All chat history is saved securely on the device using SQLite.
*   **Background Notifications:** Receive alerts when a paired device connects or sends a message while the app is in the background.

## 🛠️ Technologies Used

*   **Language:** Java
*   **Minimum SDK:** Android 6.0 (API Level 23)
*   **Target SDK:** Android 14 (API Level 34)
*   **UI Components:** RecyclerView, SwipeRefreshLayout
*   **Security:** AndroidX Biometric Library
*   **Networking:** Native Android Bluetooth API (RFCOMM Sockets)
*   **Storage:** SQLiteOpenHelper

## 🚀 How It Works

1.  **Authentication:** Upon launching or returning to the app from the background, the user is prompted for biometric authentication.
2.  **Discovery:** The app scans for nearby Bluetooth devices and filtering those that broadcast the app's specific chat UUID.
3.  **Connection:** Users can connect to active peers. A background thread establishes an RFCOMM socket between the two devices.
4.  **Communication:** Messages and files are transmitted as byte streams over the established socket, with real-time UI updates on both ends.

## 📦 Setup & Installation

1.  Clone the repository to your local machine:
    ```bash
    git clone https://github.com/yourusername/OfflineChatSecure.git
    ```
2.  Open the project in **Android Studio**.
3.  Build the project to resolve dependencies via Gradle.
4.  Connect an Android device (Emulators do not support Bluetooth development easily) or use multiple physical devices to test peer-to-peer connectivity.
5.  Click **Run** (`Shift + F10`) to deploy the app.

## 🛡️ Permissions

This app requires the following permissions to function correctly:
*   `BLUETOOTH`, `BLUETOOTH_ADMIN` (Legacy)
*   `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (Android 12+)
*   `ACCESS_FINE_LOCATION` (Required for Bluetooth scanning on older Android versions)
*   `USE_BIOMETRIC`

## 📝 License

This project is licensed under the MIT Engineering Diploma.

