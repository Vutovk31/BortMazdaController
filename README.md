# BortMazdaController

Mazda Display Controller — Android onboard computer and TS10 integration for Mazda 3 BK.

## Target hardware

- Mazda 3 BK facelift 1.6
- TS10S / UIS7862A Android head unit
- Raise/RZC RZ-MZD05 CAN box
- Android 12 target device

## MDC 1.0.1

Current repository stage: implementation bootstrap.

Planned product modes:

- OEM Computer
- Retro
- Dashboard
- Diagnostics
- Settings

The first field candidate is intended to validate Android compatibility, steering input, OBD v2 transport/ELM/ECU stages, read-only TS10/RZ-MZD05 discovery, and support-report export.

## Safety

MDC 1.0.1 is read-only at the vehicle-command level.

```text
MDC_SAFETY_PROFILE=READ_ONLY_1_0_1
CAN_WRITE=false
OBD_WRITE=false
OEM_WRITE=false
RAW_CAN_WRITE=false
UNKNOWN_BINDER_CALL=false
UNKNOWN_BROADCAST_SEND=false
DEVICE_NODE_WRITE=false
```

No arbitrary CAN transmission or unverified OEM vehicle command is permitted in 1.0.1.

See `docs/SAFETY.md`.
