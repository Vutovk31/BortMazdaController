# Mazda Display Controller / BortMazdaController — Disaster Recovery Manifest

Snapshot date: 2026-09-11
Repository: Vutovk31/BortMazdaController
Recovery branch: backup/dr-2026-09-11
Source: main at the moment the recovery branch was created.

## Recovery intent
Non-production recovery anchor only. Do not auto-merge.

## Known reconciliation requirement
This repository began as an early project snapshot and may be behind later requirements discussed in ChatGPT for Mazda display/CANBUS behavior. Treat this branch as REMOTE_COMMITTED evidence, not proof of current functional scope.

## Restore rule
Preserve this branch, reconcile with exported conversations and local files, document hardware/firmware assumptions, then validate against a safe bench/test environment before any vehicle-side changes. Never store secret values in recovery artifacts.
