# Production WebRTC Android

**YouTube Channel:** [Codewithkael](https://www.youtube.com/@codewithkael)  
**Tutorial Playlist:** [Tutorial link](https://www.youtube.com/playlist?list=PLFelST8t9nqhq_81H5D2zhp17jB5rnERI)

---

Production-ready WebRTC implementation for Android, featuring robust signaling, ICE restart logic, and live performance metrics. Built with modern Android development practices.

---

## Key Features

* Reliable peer-to-peer audio/video streaming using WebRTC.
* Real-time signaling implementation using Firebase Realtime Database.
* Intelligent ICE restart logic to handle network switching and temporary disconnections.
* Real-time display of bitrate, packet loss, RTT, Jitter, and FPS.
* Fully built with Jetpack Compose for a smooth and responsive user experience.
* Stable background execution for active calls with notification controls.
* Clean architecture powered by Hilt.

---
## ✅ What you will learn :
* → The full WebRTC happy path — signaling vs. media, how they are completely separate
* → How ICE collects host, SRFLX, and relay candidates and selects a path
* → Why your demo works on Wi-Fi but fails on 4G, VPNs, and corporate networks
* → All 4 NAT types (Full Cone, Address Restricted, Port Restricted, Symmetric) and when each one needs TURN
* → Why symmetric NAT (carrier-grade, mobile 4G/5G) always requires TURN — no exceptions
* → Firewalls, dirty networks, UDP blocking, and why TURN on TCP port 443 with TLS is the only reliable fix
* → Group call topologies: Mesh vs SFU vs MCU — cost, scale, battery, and when to choose each
* → How to read ICE failure signals and know exactly which layer to debug

## Getting Started

### Prerequisites
1. Android Studio Iguana or newer.
2. A Firebase project.

### Setup Instructions

1. Create a project in the Firebase Console.
2. Add an Android App to your Firebase project.
3. Download the google-services.json and place it in the app/ directory.
4. Enable Realtime Database and set your security rules.
5. Open the project in Android Studio and sync Gradle files.
6. Install the app on two different devices.
7. Enter the Participant ID of the other device and hit Call.

---

## License

```text
MIT License

Copyright (c) 2024 CodeWithKael

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
...
```

---
Made by [CodeWithKael](https://github.com/codewithkael)
