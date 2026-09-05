# Protocol evidence — Mazda 3 BK / TS10 / RZ-MZD05

Status: research evidence only. No CAN write is authorized.

## OEM display facts from Mazda 3 BK owner's manual
- Information display supports clock, ambient temperature, audio display and trip computer.
- CLOCK switches between trip computer and clock display.
- Trip-computer-equipped cars use INFO instead of SET for clock adjustment/reset flow.

## Public CAN research found online
A community-maintained Mazda3 2nd-generation MS-CAN sheet documents frames including 0x28F, 0x400 and 0x401 related to LCD/trip-computer state and INFO/CLOCK button indications. This is useful as a research lead only.

It is NOT treated as proof for this Mazda 3 BK 2006/restyle + TS10S + RZ-MZD05 installation because:
- the sheet is explicitly for Mazda3 2nd generation;
- wiring/topology and gateway behavior may differ;
- TS10/RZ-MZD05 may expose a proprietary Android/MCU API instead of raw vehicle CAN;
- no write command has been validated on the target car.

Therefore MDC 1.0.1 keeps OEM INFO/CLOCK/RESET locked and collects read-only TS10/RZ-MZD05 evidence first.

Safety: CAN_WRITE=false
