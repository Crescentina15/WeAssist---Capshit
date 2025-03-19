import React, { useEffect, useState } from 'react';
import { getDatabase, ref, get, update } from 'firebase/database';
import { useNavigate } from "react-router-dom";
import { getStorage, ref as storageRef, uploadBytes, getDownloadURL } from 'firebase/storage';
import { auth } from './script/firebase'; 
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
  const navigate = useNavigate(); 

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
      setPreview(URL.createObjectURL(file)); 
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
          await uploadBytes(profilePicRef, selectedFile);
          const downloadURL = await getDownloadURL(profilePicRef);
          updatedData.profilePicture = downloadURL; 
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
      <div className="profile-header-container">
        <button onClick={() => navigate("/")} className="profile-back-button">
        <span className="icon-back"></span>
        </button>
        <h2 className="profile-header-title">Law Firm Profile</h2>
        <div className="profile-header-underline"></div>
      </div>

    
        {!isEditing ? (
          <div className="profile-details">
            <div className="profile-detail-item">
              <div className="profile-detail-icon">
               
              </div>
              
            </div>

            <div className="profile-detail-item">
              <div className="profile-detail-icon">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <div className="profile-detail-label">License:</div>
              <div className="profile-detail-value">{formData.licenseNumber}</div>
            </div>

            <div className="profile-detail-item">
              <div className="profile-detail-icon">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
                </svg>
              </div>
              <div className="profile-detail-label">Phone:</div>
              <div className="profile-detail-value">{formData.phoneNumber}</div>
            </div>

            <div className="profile-detail-item">
              <div className="profile-detail-icon">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
              </div>
              <div className="profile-detail-label">Email:</div>
              <div className="profile-detail-value">{formData.email}</div>
            </div>

            <div className="profile-detail-item">
              <div className="profile-detail-icon">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <div className="profile-detail-label">Hours:</div>
              <div className="profile-detail-value">{formData.operatingHours}</div>
            </div>

            <div className="profile-detail-item">
              <div className="profile-detail-icon">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              </div>
              <div className="profile-detail-label">Address:</div>
              <div className="profile-detail-value">{formData.officeAddress}</div>
            </div>

            <div className="profile-detail-item">
              <div className="profile-detail-icon">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
              </div>
              <div className="profile-detail-label">Description:</div>
              <div className="profile-detail-value">{formData.firmDescription}</div>
            </div>
          </div>
        ) : (
          <div className="profile-edit">
            <div className="profile-edit-group">
              <label className="profile-edit-label">Law Firm Name</label>
              <input 
                type="text" 
                name="lawFirm" 
                value={formData.lawFirm} 
                onChange={handleChange} 
                className="profile-edit-input"
              />
            </div>
            
            <div className="profile-edit-group">
              <label className="profile-edit-label">Firm Type</label>
              <input 
                type="text" 
                name="firmType" 
                value={formData.firmType} 
                onChange={handleChange} 
                className="profile-edit-input"
              />
            </div>
            
            
            
            <div className="profile-edit-group">
              <label className="profile-edit-label">License Number</label>
              <input 
                type="text" 
                name="licenseNumber" 
                value={formData.licenseNumber} 
                onChange={handleChange} 
                className="profile-edit-input"
              />
            </div>
            
            <div className="profile-edit-group">
              <label className="profile-edit-label">Phone Number</label>
              <input 
                type="tel" 
                name="phoneNumber" 
                value={formData.phoneNumber} 
                onChange={handleChange} 
                className="profile-edit-input"
              />
            </div>
            
            <div className="profile-edit-group">
              <label className="profile-edit-label">Email Address</label>
              <input 
                type="email" 
                name="email" 
                value={formData.email} 
                onChange={handleChange} 
                className="profile-edit-input"
              />
            </div>
            
            <div className="profile-edit-group">
              <label className="profile-edit-label">Operating Hours</label>
              <input 
                type="text" 
                name="operatingHours" 
                value={formData.operatingHours} 
                onChange={handleChange} 
                className="profile-edit-input"
              />
            </div>
            
            <div className="profile-edit-group">
              <label className="profile-edit-label">Office Address</label>
              <input 
                type="text" 
                name="officeAddress" 
                value={formData.officeAddress} 
                onChange={handleChange} 
                className="profile-edit-input"
              />
            </div>
            
            <div className="profile-edit-group">
              <label className="profile-edit-label">Firm Description</label>
              <input 
                type="text" 
                name="firmDescription" 
                value={formData.firmDescription} 
                onChange={handleChange} 
                className="profile-edit-input"
              />
            </div>
            
            <div className="profile-edit-group">
              <input 
                type="file" 
                id="profilePicture" 
                accept="image/*" 
                onChange={handleFileChange} 
                className="profile-file-input"
              />
              
            </div>
          </div>
        )}

        <div className="profile-actions">
          {!isEditing ? (
            <>
              <button onClick={handleEdit} className="profile-edit-btn">
              <span className="icon-edit"></span>
              Update Secretary
              </button> 
            </>
          ) : (
            <>
              <button onClick={handleUpdate} className="profile-save-btn">
              <span className="icon-save"></span>
              Save Changes
              </button>
              
            </>
          )}
        </div>
      </div>
   
  );
};

export default Profile;