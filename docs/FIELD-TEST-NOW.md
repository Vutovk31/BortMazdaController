# MDC 1.0.1 internal-1 — immediate field test

1. Install APK artifact from the build workflow.
2. Pair ELM in Android settings first.
3. Close Car Scanner so the adapter is not occupied.
4. Launch MDC.
5. Dashboard → CONNECT / SELECT ELM → choose paired adapter.
6. Observe status stages: TRANSPORT_CONNECT → ELM_HANDSHAKE → ECU_PROBE → STREAMING or exact FAILED reason.
7. Verify RPM, speed, coolant, MAF, fuel %, voltage.
8. Test steering physical UP/DOWN: single press should replay media; double press should emit MDC navigation toast.
9. Diagnostics → RUN READ-ONLY TS10 DISCOVERY.
10. SAVE SUPPORT REPORT and provide the TXT for analysis.

OEM INFO/CLOCK/SET/TIME vehicle commands are intentionally locked in this field candidate.
CAN_WRITE=false
