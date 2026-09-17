# JevCraft

JevCraft is a NeoForge 1.21.1 mod implementing a shared typed-decision runtime for local-player takeover and persistent independent companions.

## Safety and credentials

Copy `.env.example` to `.env` for local tooling or provide the variables in the launch environment. Credentials are never stored in worlds, logs, or committed configuration.

## Build and test

Requires Java 21. On Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

`build/libs/jevcraft-full-*.jar` is the full client/server mod. `jevcraft-client-*.jar` is the client-only takeover edition.

See `docs/CAPABILITY_MATRIX.md` and `docs/VERIFICATION.md` for the difference between implemented mechanics, deterministic verification, and live Jev gameplay evidence.

## Current maturity

This repository is an executable feasibility slice. It proves the dual provider contract, live Vercel evaluation transport, stale-response safety, two artifact shapes, and a real registered companion in NeoForge GameTests. It does **not** yet satisfy the plan's complete vanilla action surface or release gates; those gaps are kept explicit in the capability matrix.
