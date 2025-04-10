package com.remedio.weassist.Lawyer

import android.os.Parcel
import android.os.Parcelable

data class Lawyer(
    var id: String = "",
    val name: String = "",
    val specialization: String = "",
    val lawFirm: String = "",
    val licenseNumber: String = "",
    val experience: String = "",
    val lawSchool: String? = null,
    val graduationYear: String? = null,
    val certifications: String? = null,
    val jurisdiction: String? = null,
    val employer: String? = null,
    val bio: String? = null,
    val rate: String? = null, // Changed from ratings to rate
    val profileImage: String? = null,
    val profileImageUrl: String? = null,
    var location: String = "",
    val contact: Contact? = null,
    val lawFirmAdminId: String = ""
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(), // Read profileImageUrl as nullable
        parcel.readString() ?: "",
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(specialization)
        parcel.writeString(lawFirm)
        parcel.writeString(licenseNumber)
        parcel.writeString(experience)
        parcel.writeString(lawSchool)
        parcel.writeString(graduationYear)
        parcel.writeString(certifications)
        parcel.writeString(jurisdiction)
        parcel.writeString(employer)
        parcel.writeString(bio)
        parcel.writeString(rate)
        parcel.writeString(profileImage)
        parcel.writeString(profileImageUrl) // Write profileImageUrl as nullable
        parcel.writeString(location)
        parcel.writeParcelable(contact, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Lawyer> {
        override fun createFromParcel(parcel: Parcel): Lawyer {
            return Lawyer(parcel)
        }

        override fun newArray(size: Int): Array<Lawyer?> {
            return arrayOfNulls(size)
        }
    }
}