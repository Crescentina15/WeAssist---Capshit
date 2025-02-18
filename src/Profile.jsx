import React, { useEffect, useState } from 'react';
import { getDatabase, ref, get, update } from 'firebase/database';
import { auth } from './script/firebase'; // assuming firebase is correctly initialized

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
    password: "", // Keep password empty since it's not fetched
  });

  const [isEditing, setIsEditing] = useState(false); // Toggle edit mode
  const user = auth.currentUser; // Assuming the user is authenticated

  useEffect(() => {
    if (user?.uid) {
      const db = getDatabase();
      const userRef = ref(db, 'law_firm_admin/' + user.uid); // Path to fetch data
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
            password: "", // Password not retrieved
          });
        } else {
          console.log("No data available");
        }
      }).catch((error) => {
        console.error("Error fetching data:", error);
      });
    }
  }, [user]);

  // Handle input changes for edit mode
  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prevData => ({
      ...prevData,
      [name]: value
    }));
  };

  // Toggle edit mode
  const handleEdit = () => {
    setIsEditing(!isEditing);
  };

  // Update data in Firebase
  const handleUpdate = () => {
    if (user?.uid) {
      const db = getDatabase();
      const userRef = ref(db, 'law_firm_admin/' + user.uid);
      update(userRef, formData)
        .then(() => {
          console.log("Data updated successfully!");
          setIsEditing(false); // Exit edit mode after update
        })
        .catch((error) => {
          console.error("Error updating data:", error);
        });
    }
  };

  return (
    <div>
      <h1>Profile Information</h1>
      
      {!isEditing ? (
        // Display profile info as a list (non-editable)
        <ul>
          <li><strong>Firm Name:</strong> {formData.lawFirm}</li>
          <li><strong>Firm Type:</strong> {formData.firmType}</li>
          <li><strong>Firm Description:</strong> {formData.firmDescription}</li>
          <li><strong>Phone Number:</strong> {formData.phoneNumber}</li>
          <li><strong>Email:</strong> {formData.email}</li>
          <li><strong>Website:</strong> {formData.website}</li>
          <li><strong>Specialization:</strong> {formData.specialization}</li>
          <li><strong>Operating Hours:</strong> {formData.operatingHours}</li>
          <li><strong>License Number:</strong> {formData.licenseNumber}</li>
          <li><strong>Office Address:</strong> {formData.officeAddress}</li>
        </ul>
      ) : (
        // Display editable fields
        <form>
          <input
            type="text"
            name="lawFirm"
            value={formData.lawFirm}
            onChange={handleChange}
          />
          <input
            type="text"
            name="firmType"
            value={formData.firmType}
            onChange={handleChange}
          />
          <input
            type="text"
            name="firmDescription"
            value={formData.firmDescription}
            onChange={handleChange}
          />
          <input
            type="tel"
            name="phoneNumber"
            value={formData.phoneNumber}
            onChange={handleChange}
          />
          <input
            type="email"
            name="email"
            value={formData.email}
            onChange={handleChange}
          />
          <input
            type="url"
            name="website"
            value={formData.website}
            onChange={handleChange}
          />
          <input
            type="text"
            name="specialization"
            value={formData.specialization}
            onChange={handleChange}
          />
          <input
            type="text"
            name="operatingHours"
            value={formData.operatingHours}
            onChange={handleChange}
          />
          <input
            type="text"
            name="licenseNumber"
            value={formData.licenseNumber}
            onChange={handleChange}
          />
          <input
            type="text"
            name="officeAddress"
            value={formData.officeAddress}
            onChange={handleChange}
          />
        </form>
      )}

      {/* Edit button */}
      <button onClick={handleEdit}>
        {isEditing ? 'Cancel' : 'Edit'}
      </button>

      {/* Update button (visible when editing) */}
      {isEditing && (
        <button onClick={handleUpdate}>Update</button>
      )}
    </div>
  );
};

export default Profile;
