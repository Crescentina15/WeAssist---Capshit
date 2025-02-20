import React, { useEffect, useState } from 'react';
import { getDatabase, ref, get, update } from 'firebase/database';
import { getStorage, ref as storageRef, uploadBytes, getDownloadURL } from 'firebase/storage';
import { auth } from './script/firebase'; // Firebase initialization
import './index.css';

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
    profilePicture: "", 
  });

  const [isEditing, setIsEditing] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [preview, setPreview] = useState("");

  const user = auth.currentUser;

  useEffect(() => {
    if (user?.uid) {
      const db = getDatabase();
      const userRef = ref(db, 'law_firm_admin/' + user.uid);
      get(userRef).then((snapshot) => {
        if (snapshot.exists()) {
          setFormData({
            ...formData,
            ...snapshot.val(),
          });
        }
      }).catch((error) => {
        console.error("Error fetching data:", error);
      });
    }
  }, [user]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prevData => ({
      ...prevData,
      [name]: value
    }));
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setSelectedFile(file);
      setPreview(URL.createObjectURL(file)); // Show preview before uploading
    }
  };

  const handleEdit = () => {
    setIsEditing(!isEditing);
  };

  const handleUpdate = async () => {
    if (user?.uid) {
      const db = getDatabase();
      const userRef = ref(db, 'law_firm_admin/' + user.uid);

      let updatedData = { ...formData };

      if (selectedFile) {
        const storage = getStorage();
        const profilePicRef = storageRef(storage, `profile_pictures/${user.uid}`);
        
        try {
          // Upload the image to Firebase Storage
          await uploadBytes(profilePicRef, selectedFile);
          const downloadURL = await getDownloadURL(profilePicRef);
          updatedData.profilePicture = downloadURL; // Update profile picture URL
        } catch (error) {
          console.error("Error uploading image:", error);
          alert("Failed to upload image. Please try again.");
          return;
        }
      }

      update(userRef, updatedData)
        .then(() => {
          setFormData(updatedData);
          setIsEditing(false);
          alert("Profile updated successfully!");
        })
        .catch((error) => {
          console.error("Error updating data:", error);
        });
    }
  };

  return (
    <div className="profile-card">
      <div className="profile-header">
        <img 
          src={preview || formData.profilePicture || "https://via.placeholder.com/150"} 
          alt="Profile" 
          className="profile-img"
        />
        <h2>{formData.lawFirm}</h2>
        <p>{formData.firmType}</p>
      </div>

      {!isEditing ? (
        <div className="profile-details">
          <p><strong>Specialization:</strong> {formData.specialization}</p>
          <p><strong>License:</strong> {formData.licenseNumber}</p>
          <p><strong>Phone:</strong> {formData.phoneNumber}</p>
          <p><strong>Email:</strong> {formData.email}</p>
          <p><strong>Website:</strong> {formData.website}</p>
          <p><strong>Operating Hours:</strong> {formData.operatingHours}</p>
          <p><strong>Address:</strong> {formData.officeAddress}</p>
          <p><strong>Description:</strong> {formData.firmDescription}</p>
        </div>
      ) : (
        <div className="profile-edit">
          <input type="text" name="lawFirm" value={formData.lawFirm} onChange={handleChange} />
          <input type="text" name="firmType" value={formData.firmType} onChange={handleChange} />
          <input type="text" name="specialization" value={formData.specialization} onChange={handleChange} />
          <input type="text" name="licenseNumber" value={formData.licenseNumber} onChange={handleChange} />
          <input type="tel" name="phoneNumber" value={formData.phoneNumber} onChange={handleChange} />
          <input type="email" name="email" value={formData.email} onChange={handleChange} />
          <input type="url" name="website" value={formData.website} onChange={handleChange} />
          <input type="text" name="operatingHours" value={formData.operatingHours} onChange={handleChange} />
          <input type="text" name="officeAddress" value={formData.officeAddress} onChange={handleChange} />
          <textarea name="firmDescription" value={formData.firmDescription} onChange={handleChange} />

          {/* Profile Picture Upload */}
          <label>Profile Picture:</label>
          <input type="file" accept="image/*" onChange={handleFileChange} />
        </div>
      )}

      <div className="profile-actions">
        <button onClick={handleEdit} className="edit-btn">{isEditing ? 'Cancel' : 'Edit'}</button>
        {isEditing && <button onClick={handleUpdate} className="save-btn">Update</button>}
      </div>
    </div>
  );
};

export default Profile;
