import React, { useEffect, useState } from 'react';
import { getDatabase, ref, get } from 'firebase/database';
import { auth } from './script/firebase'; // assuming you have firebase auth initialized

const Profile = () => {
  const [formData, setFormData] = useState({
    lawFirm: "",
    firmType: "",
    firmDescription: "",
    phoneNumber: "",
    email: "",
    website: "",
    specialization: "",
    operatingHours: "",
    licenseNumber: "",
    officeAddress: "",
    password: "", // Keep password as empty since it's not retrieved
  });

  const user = auth.currentUser; // Assuming the user is authenticated and we get the current user

  useEffect(() => {
    if (user?.uid) {
      const db = getDatabase();
      const userRef = ref(db, 'law_firm_admin/' + user.uid); // Adjust to the correct path
      get(userRef).then((snapshot) => {
        if (snapshot.exists()) {
          const userData = snapshot.val();
          setFormData({
            lawFirm: userData.lawFirm || "",
            firmType: userData.firmType || "",
            firmDescription: userData.firmDescription || "",
            phoneNumber: userData.phoneNumber || "",
            email: userData.email || "",
            website: userData.website || "",
            specialization: userData.specialization || "",
            operatingHours: userData.operatingHours || "",
            licenseNumber: userData.licenseNumber || "",
            officeAddress: userData.officeAddress || "",
            password: "", // Password is not retrieved
          });
        } else {
          console.log("No data available");
        }
      }).catch((error) => {
        console.error("Error fetching data:", error);
      });
    }
  }, [user]);

  return (
    <div>
      <h1>Profile Information</h1>
      <form>
        <input type="text" value={formData.lawFirm} placeholder="Firm Name" readOnly />
        <input type="text" value={formData.firmType} placeholder="Firm Type" readOnly />
        <input type="text" value={formData.firmDescription} placeholder="Firm Description" readOnly />
        <input type="tel" value={formData.phoneNumber} placeholder="Phone Number" readOnly />
        <input type="email" value={formData.email} placeholder="Email" readOnly />
        <input type="url" value={formData.website} placeholder="Website" readOnly />
        <input type="text" value={formData.specialization} placeholder="Specialization" readOnly />
        <input type="text" value={formData.operatingHours} placeholder="Operating Hours" readOnly />
        <input type="text" value={formData.licenseNumber} placeholder="License Number" readOnly />
        <input type="text" value={formData.officeAddress} placeholder="Office Address" readOnly />
        <input type="password" value={formData.password} placeholder="Password" readOnly />
        {/* Add an update button to trigger editing */}
        <button type="button" onClick={() => console.log('Enable editing')}>Edit</button>
      </form>
    </div>
  );
}

export default Profile;
