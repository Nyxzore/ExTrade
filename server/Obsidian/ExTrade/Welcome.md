# Welcome to ExoTrade Documentation

Welcome to the comprehensive technical documentation for the ExoTrade project. This vault covers everything from high-level architecture to individual file-level logic for the Android app, Shared KMP module, and Go backend.

## Documentation Index

### Core Architecture
- [[Project Overview]]: High-level architecture, tech stack, and project structure.
- [[Backend]]: Documentation for the Go API, PostgreSQL schema, and hotness algorithm.
- [[Dev Setup]]: How to run the Go backend and Cloudflare Tunnel locally.
- [[Testing]]: Test suite inventory, run commands, and TC-* coverage matrix.
- [[Shared Logic]]: Exhaustive details on the Kotlin Multiplatform module and its files.

### Feature Systems (Kotlin & Go)
- [[Listings System]]: Discovery feed, selling flow, and taxonomy integration.
- [[Breeding System]]: Specialized matching and breeding feed.
- [[Messaging System]]: End-to-End Encrypted (E2EE) chat architecture.
- [[Friends System]]: Social graph, trigram search, and request flow.
- [[Profile & Social]]: User identity, [[Social Links]], and account management.

### Technical Deep Dives
- [[Utilities]]: Details on `Helpers.kt`, `ImageUtils.kt`, `SocialLinkUtils.kt`, etc.
- [[Adapters]]: Documentation for RecyclerView adapters and specialized ViewHolders.
- [[Moderation System]]: Safety tools, admin logic, and reporting workflow.
- [[Performance & Scaling]]: Optimizations for search, media, and networking.

### Running Locally
- [[Dev Setup]]: How to run the Go backend and Cloudflare Tunnel locally.
- [[Testing]]: Full test inventory and coverage matrix.

## Getting Started for Developers
1. **Browse [[Project Overview]]** to understand the hybrid app-shared-server model.
2. **Review [[Dev Setup]]** to get your local environment running.
3. **Review [[Shared Logic]]** to see how data and security are unified.
4. **Explore Feature Systems** for specific implementation details of the Kotlin files and backend handlers.
