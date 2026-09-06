# BortMazdaController

Mazda Display Controller — Android onboard computer and TS10 integration for Mazda 3 BK.

## Current milestone
`MDC 1.0.1 internal field candidate`

Implemented in the current implementation branch:
- landscape TS10 shell: OEM / Retro / Dashboard / Diagnostics / Settings
- paired Bluetooth device picker
- ELM327 SPP secure → insecure RFCOMM fallback
- ELM handshake and ECU probe
- read-only Mode 01 polling: RPM, speed, coolant, MAF, fuel level, fuel rate where supported
- adapter voltage via ATRV
- local TripComputer and MAF-derived fuel rate fallback
- steering input routing for physical UP=88 / DOWN=87
- read-only TS10/RZ-MZD05 discovery collector
- local sanitized support report
- Locked OEM controls

## Hardware target
- Mazda 3 BK 1.6 facelift
- TS10S / UIS7862A
- RZ-MZD05 CAN box
- Android 12

## Safety
`MDC_SAFETY_PROFILE=READ_ONLY_1_0_1`
`CAN_WRITE=false`

The internal field candidate does not send arbitrary CAN frames, clear DTCs, execute actuator tests, send unknown Binder transactions, or issue unverified OEM INFO/CLOCK/RESET commands.

## Field test
1. Pair the ELM adapter in Android Bluetooth settings.
2. Ensure Car Scanner is disconnected from the adapter.
3. Open MDC → Dashboard or Diagnostics → CONNECT / SELECT ELM.
4. Choose the paired ELM device.
5. Run `READ-ONLY TS10 DISCOVERY`.
6. Use `SAVE SUPPORT REPORT` and keep the generated TXT for analysis.

OEM INFO/CLOCK/SET/TIME commands remain intentionally locked until the Mazda/TS10/RZ-MZD05 path is validated.

## OEM protocol research
The Mazda 3 BK owner's manual confirms the physical INFO/CLOCK interaction semantics, but does not publish the underlying electrical/CAN command protocol. Public CAN data found for Mazda3 2nd generation is recorded only as a research lead in `docs/PROTOCOL-EVIDENCE.md`; it is not used to send commands on this BK target.
