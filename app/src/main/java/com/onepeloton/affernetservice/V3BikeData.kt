package com.onepeloton.affernetservice

import android.os.Parcel
import android.os.Parcelable

/**
 * Reconstructed for OpenRide T3/#3 (interoperability — the owner's own Bike Gen 2 tablet).
 *
 * Nested Parcelable carried inside [BikeData] on newer ("V3" sensor board) frames. The class
 * MUST live at this exact fully-qualified name (`com.onepeloton.affernetservice.V3BikeData`)
 * because [BikeData] reads it back via `Parcel.readParcelable`, which resolves the concrete
 * type from the class-name string the service wrote into the parcel.
 *
 * Field order below mirrors the service's `writeToParcel` byte-for-byte — reading a [Parcel]
 * out of order silently returns garbage, so the order is the whole contract. OpenRide does
 * not consume any V3 field today; this exists purely so the outer [BikeData] parcel stays in
 * sync when a V3 payload is present. Reverse-engineered from the on-device service; see
 * docs/SENSOR_PROTOCOL.md.
 */
class V3BikeData() : Parcelable {
    @JvmField var tick: Int = 0
    @JvmField var brakePos: Float = 0f
    @JvmField var flywheelRpm: Float = 0f
    @JvmField var flywheelTemp: Float = 0f
    @JvmField var ambientTemp: Float = 0f
    @JvmField var boardTemp: Float = 0f
    @JvmField var posX: Float = 0f
    @JvmField var posY: Float = 0f
    @JvmField var posZ: Float = 0f
    @JvmField var posT: Float = 0f
    @JvmField var encX: Float = 0f
    @JvmField var encY: Float = 0f
    @JvmField var encZ: Float = 0f
    @JvmField var encT: Float = 0f
    @JvmField var loadCellWeight: Float = 0f
    @JvmField var loadCellDiff: Float = 0f
    @JvmField var loadCellTorque: Float = 0f
    @JvmField var motorFlags: Int = 0
    @JvmField var motorAppState: Byte = 0
    @JvmField var motorPosUsteps: Int = 0
    @JvmField var motorPosMm: Float = 0f

    private constructor(parcel: Parcel) : this() {
        tick = parcel.readInt()
        brakePos = parcel.readFloat()
        flywheelRpm = parcel.readFloat()
        flywheelTemp = parcel.readFloat()
        ambientTemp = parcel.readFloat()
        boardTemp = parcel.readFloat()
        posX = parcel.readFloat()
        posY = parcel.readFloat()
        posZ = parcel.readFloat()
        posT = parcel.readFloat()
        encX = parcel.readFloat()
        encY = parcel.readFloat()
        encZ = parcel.readFloat()
        encT = parcel.readFloat()
        loadCellWeight = parcel.readFloat()
        loadCellDiff = parcel.readFloat()
        loadCellTorque = parcel.readFloat()
        motorFlags = parcel.readInt()
        motorAppState = parcel.readByte()
        motorPosUsteps = parcel.readInt()
        motorPosMm = parcel.readFloat()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(tick)
        parcel.writeFloat(brakePos)
        parcel.writeFloat(flywheelRpm)
        parcel.writeFloat(flywheelTemp)
        parcel.writeFloat(ambientTemp)
        parcel.writeFloat(boardTemp)
        parcel.writeFloat(posX)
        parcel.writeFloat(posY)
        parcel.writeFloat(posZ)
        parcel.writeFloat(posT)
        parcel.writeFloat(encX)
        parcel.writeFloat(encY)
        parcel.writeFloat(encZ)
        parcel.writeFloat(encT)
        parcel.writeFloat(loadCellWeight)
        parcel.writeFloat(loadCellDiff)
        parcel.writeFloat(loadCellTorque)
        parcel.writeInt(motorFlags)
        parcel.writeByte(motorAppState)
        parcel.writeInt(motorPosUsteps)
        parcel.writeFloat(motorPosMm)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<V3BikeData> {
            override fun createFromParcel(parcel: Parcel): V3BikeData = V3BikeData(parcel)
            override fun newArray(size: Int): Array<V3BikeData?> = arrayOfNulls(size)
        }
    }
}
