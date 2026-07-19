# RelayNet

**RelayNet** is an offline, peer-to-peer mesh messaging app for Android. It lets nearby devices communicate directly — over Bluetooth and WiFi Direct — with no internet connection, cellular network, or centralized server required. Messages hop from device to device across a mesh of peers, and a dedicated SOS/broadcast channel ensures emergency messages reach everyone nearby.

Built for AERTHERA AI HACKATHON.

## The Problem

In disaster zones, remote areas, protests, or any situation where cellular and internet infrastructure is down or unavailable, people lose the ability to communicate with each other — even when they're standing just a few meters apart. RelayNet solves this by turning every phone into a relay node in a self-forming mesh network.

## How It Works

- **Peer discovery & connection** — powered by Google's Nearby Connections API (`P2P_CLUSTER` strategy), automatically negotiating Bluetooth, BLE, or WiFi Direct depending on device capability and proximity.
- **Multi-hop message relay** — messages carry a time-to-live (TTL) hop count and are flooded to connected peers, allowing a message to travel across multiple devices to reach someone out of direct range.
- **End-to-end encryption** — direct messages between two known peers are encrypted using per-peer public keys exchanged automatically on connection.
- **SOS broadcast** — emergency messages skip encryption and are broadcast openly to every reachable device in the mesh, ensuring maximum reach during emergencies.
- **Gossip-based sync** — devices exchange message ID catalogs on reconnect so any messages missed while offline get backfilled from peers (anti-entropy sync).
- **Local persistence** — all messages are stored locally via Room, so message history survives app restarts and reconnects.

## Tech Stack

- **Kotlin** + **Jetpack Compose** for the UI
- **Google Nearby Connections API** for peer discovery and transport
- **Room** for local message persistence
- **Kotlin Coroutines** for background networking and database work

## Key Features

- Fully offline, no internet or cellular connection required
- Multi-hop mesh relay (not just direct device-to-device)
- Emergency SOS broadcast channel
- Per-peer encrypted direct messaging
- Message deletion (single message or clear all)
- Automatic reconnection handling when devices move in and out of range
- Message history persisted across app restarts

## Getting Started (Development)

1. Clone the repository:
   ```bash
   git clone <your-repo-url>
   ```
2. Open the project in Android Studio.
3. Let Gradle sync and download dependencies.
4. Connect at least two physical Android devices (Nearby Connections requires real hardware — it does not work reliably on emulators).
5. Grant the app the required runtime permissions on first launch: **Nearby devices**, **Location**, and **Bluetooth**.

## Building a Demo APK

To generate an installable APK for demoing on physical devices:

1. In Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Once built, locate the file at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```
3. Transfer the APK to your test devices (USB, Bluetooth, or a shared cloud link) and install it directly. You'll need to allow **"Install from unknown sources"** on each device, since this isn't distributed via the Play Store.

## Known Limitations

- Requires at least 2 physical Android devices for testing (no emulator support for Nearby Connections).
- Cross-OEM Bluetooth pairing (e.g. newer vs. older Android versions/manufacturers) can occasionally be less stable due to differences in each device's Bluetooth stack.
- Mesh reconnection after backgrounding the app is handled automatically, but very large gaps in connectivity may still require reopening the app.

## Team / Credits

Built by [your team names] for FutureAI Global Hackathon 2026.
