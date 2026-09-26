# MDC 1.0.1 Safety

MDC 1.0.1 is read-only at the vehicle-command level.

Canonical profile:

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

The application must not expose arbitrary CAN transmission, OBD Mode 04 Clear DTC, Mode 08 actuator commands, unknown Binder transaction execution, unknown broadcast sending, or writable device-node probes.

OEM INFO/CLOCK/SET/TIME+/TIME-/vehicle reset remain locked until protocol evidence is independently validated and a future explicitly reviewed safety profile is created.

Developer and simulator modes do not weaken this policy.
