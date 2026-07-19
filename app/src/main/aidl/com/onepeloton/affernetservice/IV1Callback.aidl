// Reconstructed for OpenRide T3/#3 — interoperability reconstruction of the callback
// interface the Peloton affernet service invokes to push live sensor frames to a registered
// client. Descriptor and method ORDER are load-bearing: the AIDL compiler assigns binder
// transaction codes 1..N in declaration order, and they must match what the service sends:
//
//     onSensorDataChange   -> transaction 1   (carries a BikeData frame)
//     onSensorError        -> transaction 2
//     onCalibrationStatus  -> transaction 3
//
// The interface is `oneway`: the service fires these with FLAG_ONEWAY (no reply parcel), so
// the generated Stub must not attempt to write a reply. Verified against grupetto's proven
// Gen 2 decode and the on-device service. See docs/SENSOR_PROTOCOL.md.
package com.onepeloton.affernetservice;

import com.onepeloton.affernetservice.BikeData;

oneway interface IV1Callback {
    // Transaction 1. Wire form: [int nonNullFlag][BikeData if flag != 0]. AIDL's nullable
    // parcelable marshalling produces exactly the [hasData][payload] framing the service uses.
    void onSensorDataChange(in BikeData bikeData);

    // Transaction 2.
    void onSensorError(long errorCode);

    // Transaction 3. Not used by OpenRide (we never trigger calibration) but declared to keep
    // transaction codes 1 and 2 correctly assigned.
    void onCalibrationStatus(int status, boolean success, long timestamp);
}
