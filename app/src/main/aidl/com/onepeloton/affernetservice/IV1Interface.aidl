// Reconstructed for OpenRide T3/#3 — interoperability reconstruction of the primary binder
// interface exposed by the Peloton affernet system service on the Bike Gen 2 tablet.
//
// Bound via: Intent(action = "com.onepeloton.affernetservice.IV1Interface"),
//            package = "com.onepeloton.affernetservice"
// (the exported, unguarded AffernetService — any app may bind, no signature permission).
//
// Method ORDER is load-bearing: AIDL assigns binder transaction codes 1..N in declaration
// order, and they must match the service's real codes:
//
//     registerCallback       -> transaction 1
//     unregisterCallback     -> transaction 2
//     setFakeDataMode        -> transaction 3
//     setCallbackReportRate  -> transaction 4
//
// Only the first four transactions are used by OpenRide, so only those four are declared
// (declaring more is unnecessary and only risks signature drift). Verified against grupetto's
// proven Gen 2 path and the decompiled on-device service. See docs/SENSOR_PROTOCOL.md.
package com.onepeloton.affernetservice;

import com.onepeloton.affernetservice.IV1Callback;

interface IV1Interface {
    // Transaction 1. Two-way: the service writes back an exception marker the caller reads.
    // `identifier` is a free-form client tag the service logs (any non-null string works).
    void registerCallback(IV1Callback callback, String identifier);

    // Transaction 2.
    void unregisterCallback(IV1Callback callback, String identifier);

    // Transaction 3. When enabled, the service streams synthetic sensor frames with NO rider
    // pedaling — OpenRide uses this only for on-device pipeline verification, never in
    // production. Returns whether the mode was accepted.
    boolean setFakeDataMode(boolean enabled);

    // Transaction 4. Requests a callback cadence; returns the rate the service actually
    // applied (milliseconds between frames).
    int setCallbackReportRate(int rateMillis);
}
