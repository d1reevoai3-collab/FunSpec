<div align="center">
  <h1>🎮 FunSpec</h1>
  <p><strong>Advanced Admin & Spectator Dashboard for Minecraft Servers</strong></p>
  <img src="https://img.shields.io/badge/Node.js-18.x-green" alt="Node.js">
  <img src="https://img.shields.io/badge/Prisma-ORM-blue" alt="Prisma">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</div>

## 📌 About The Project

**FunSpec** is a highly optimized, open-source moderation and spectator dashboard designed specifically for Minecraft server administrators. Managing players, checking reports, and overseeing server health has never been this seamless. 

Our ecosystem consists of three parts:
1. **The Backend & Web Dashboard** (This repository) - Provides a powerful web interface for admins to review player statistics, manage access, and monitor server activity in real-time.
2. **The Minecraft Mod (Fabric)** - Integrates deeply with the Minecraft client to provide an advanced UI overlay, dynamic blur effects, and seamless communication with the backend.
3. **The Browser Extension** - Enhances the moderation workflow by integrating VK/Telegram data directly into the admin's browser, allowing for rapid ticket resolution.

## ✨ Features

- 🔒 **Secure Authorization:** Multi-platform login support (Telegram, VK, and internal credentials) using PKCE and secure cookie sessions.
- 📊 **Real-time Statistics:** Monitor active players, hardware IDs (HWID), and server performance metrics.
- 🛡️ **Advanced Rate Limiting:** Built-in protection against DDoS and brute-force attacks.
- ⚡ **Prisma ORM & PostgreSQL:** Fast, reliable, and scalable database architecture.
- 🌐 **Modern Dashboard:** A beautiful, responsive frontend built with modern web standards and sleek animations.

## 🚀 Getting Started

### Prerequisites
- Node.js (v18 or higher)
- PostgreSQL database
- Railway account (for quick deployment)

### Installation

1. Clone the repository
   ```sh
   git clone https://github.com/d1reevoai3-collab/FunSpec.git
   cd FunSpec
   ```
2. Install NPM packages
   ```sh
   npm install
   ```
3. Setup your `.env` file based on `.env.example`. You will need to provide your Database URL and Telegram Bot tokens.
4. Run Prisma migrations
   ```sh
   npx prisma migrate dev
   ```
5. Start the development server
   ```sh
   npm run dev
   ```

## 🤝 Contributing

Contributions make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
