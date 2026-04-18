# 📱 OfflineChatSecure — Step-by-Step Execution Tracker (FOR CODEX)

## 🧠 INSTRUCTIONS FOR CODEX (MANDATORY)

You MUST follow these rules strictly:

1. Execute ONLY **one step at a time**
2. After completing a step:
   - Mark it as ✅ DONE
   - Briefly summarize what you implemented
   - STOP execution
   - Ask: **"Proceed to next step?"**
3. DO NOT continue unless user explicitly says "yes" or "next"
4. DO NOT skip steps
5. DO NOT implement future steps early
6. Ensure code compiles before marking step done
7. Generate FULL code for each step (no placeholders)

---

# 📦 PROJECT NAME
OfflineChatSecure

---

# 🏗️ STEP 1 — Project Initialization

## 🎯 Goal
Create base Android project structure

## 📌 Tasks
- [x] Create Android project (Java)
- [x] Set min SDK to 23+
- [x] Create package structure:
  - activities/
  - adapters/
  - models/
  - database/
  - network/
  - utils/
- [x] Create empty base activities:
  - SplashActivity
  - MainActivity

---

# 🔐 STEP 2 — Biometric Authentication

## 🎯 Goal
Secure app entry using biometrics

## 📌 Tasks
- [x] Add biometric dependency
- [x] Implement BiometricHelper class
- [x] Implement biometric check in SplashActivity
- [x] Handle:
  - Success → open MainActivity
  - Failure → retry or exit
- [x] Show biometric prompt UI

---

# 📡 STEP 3 — Bluetooth Setup & Permissions

## 🎯 Goal
Prepare Bluetooth system

## 📌 Tasks
- [x] Add required permissions in Manifest
- [x] Check if Bluetooth supported
- [x] Prompt user to enable Bluetooth
- [x] Handle runtime permissions

---

# 🔍 STEP 4 — Device Discovery

## 🎯 Goal
Discover nearby Bluetooth devices

## 📌 Tasks
- [x] Create DeviceListActivity
- [x] Implement device scanning
- [x] Display devices in RecyclerView
- [x] Handle device selection

---

# 🔗 STEP 5 — Connection System

## 🎯 Goal
Connect two devices

## 📌 Tasks
- [x] Implement AcceptThread (server)
- [x] Implement ConnectThread (client)
- [x] Establish BluetoothSocket connection
- [x] Handle connection states

---

# 💬 STEP 6 — Chat UI

## 🎯 Goal
Create messaging interface

## 📌 Tasks
- [x] Create ChatActivity
- [x] Design chat layout (RecyclerView)
- [x] Create MessageAdapter
- [x] Create message item UI (left/right)

---

# 🔄 STEP 7 — Real-Time Messaging

## 🎯 Goal
Send and receive messages

## 📌 Tasks
- [x] Implement send logic (OutputStream)
- [x] Implement receive logic (InputStream)
- [x] Use background thread
- [x] Update UI in real-time

---

# 💾 STEP 8 — SQLite Database

## 🎯 Goal
Store chat history

## 📌 Tasks
- [ ] Create DBHelper
- [ ] Create messages table
- [ ] Save messages
- [ ] Load messages in ChatActivity

---

# 🔔 STEP 9 — Notifications

## 🎯 Goal
Notify user on new message

## 📌 Tasks
- [ ] Create notification channel
- [ ] Show notification on receive
- [ ] Handle background state

---

# 🎨 STEP 10 — UI Improvements

## 🎯 Goal
Enhance user experience

## 📌 Tasks
- [ ] Add dark mode
- [ ] Improve chat bubbles
- [ ] Add animations
- [ ] Polish layouts

---

# 🔐 STEP 11 — Advanced Security (Optional)

## 🎯 Goal
Enhance security

## 📌 Tasks
- [ ] Require biometric for chat access
- [ ] Require biometric for delete actions
- [ ] Optional message encryption

---

# 🧪 STEP 12 — Testing & Fixes

## 🎯 Goal
Stabilize app

## 📌 Tasks
- [ ] Test Bluetooth connection
- [ ] Test biometric flows
- [ ] Handle edge cases
- [ ] Fix crashes

---

# 📦 STEP 13 — Finalization

## 🎯 Goal
Prepare final project

## 📌 Tasks
- [ ] Clean code
- [ ] Add comments
- [ ] Ensure build success
- [ ] Prepare README

---

# 📊 PROGRESS TRACKER

| Step | Status |
|------|--------|
| 1 | ✅ DONE |
| 2 | ✅ DONE |
| 3 | ✅ DONE |
| 4 | ✅ DONE |
| 5 | ✅ DONE |
| 6 | ✅ DONE |
| 7 | ✅ DONE |
| 8 | ⬜ |
| 9 | ⬜ |
| 10 | ⬜ |
| 11 | ⬜ |
| 12 | ⬜ |
| 13 | ⬜ |

---

# 🛑 STOP RULE (CRITICAL)

After completing ANY step, you MUST output:

1. ✅ Step X completed
2. 📄 Summary of what was implemented
3. ❓ Ask: "Proceed to next step?"

DO NOT generate further code until user confirms.

