import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { auth, db } from "./script/firebase";
import { createUserWithEmailAndPassword, sendEmailVerification } from "firebase/auth";
import { ref, set, get } from "firebase/database";
import "./index.css";

const AddLawyer = () => {
  const navigate = useNavigate();
  const [lawyer, setLawyer] = useState({ 
    name: "", email: "", phone: "", specialization: "", 
    licenseNumber: "", experience: "", password: "" 
  });
  const [image, setImage] = useState(null);
  const [lawFirmAdmin, setLawFirmAdmin] = useState(null);

  useEffect(() => {
    const fetchAdminData = async () => {
      const user = auth.currentUser;
      if (user) {
        const adminRef = ref(db, `law_firm_admin/${user.uid}`);
        const snapshot = await get(adminRef);
        if (snapshot.exists()) {
          setLawFirmAdmin(snapshot.val());
        } else {
          alert("Error: Law firm admin not found!");
          navigate("/");
        }
      }
    };

    fetchAdminData();
  }, [navigate]);

  const addLawyer = async () => {
    if (!lawFirmAdmin) {
      alert("Law firm admin data not loaded.");
      return;
    }
  
    try {
      const userCredential = await createUserWithEmailAndPassword(auth, lawyer.email, lawyer.password);
      const lawyerUID = userCredential.user.uid;
  
      const secretariesRef = ref(db, "secretaries");
      const secretariesSnap = await get(secretariesRef);
  
      let secretaryID = null;
      if (secretariesSnap.exists()) {
        Object.entries(secretariesSnap.val()).forEach(([secID, secData]) => {
          if (secData.lawFirm === lawFirmAdmin.lawFirm) {
            secretaryID = secID;
          }
        });
      }
  
      await set(ref(db, `lawyers/${lawyerUID}`), {
        name: lawyer.name,
        email: lawyer.email,
        phone: lawyer.phone,
        specialization: lawyer.specialization,
        licenseNumber: lawyer.licenseNumber,
        experience: lawyer.experience,
        role: "lawyer",
        profileImage: image ? URL.createObjectURL(image) : "",
        lawFirm: lawFirmAdmin.lawFirm,
        adminUID: lawFirmAdmin.uid,
        secretaryID: secretaryID || "",
      });
  
      await sendEmailVerification(userCredential.user);
      alert("Lawyer account created successfully! Verification email sent.");
  
      setLawyer({ name: "", email: "", phone: "", specialization: "", licenseNumber: "", experience: "", password: "" });
      setImage(null);
      setTimeout(() => navigate("/"), 1000);
    } catch (error) {
      alert("Error: " + error.message);
    }
  };
  
  return (
    <div className="lawyer-card">
      <div className="lawyer-card-header">
        <button onClick={() => navigate("/")} className="lawyer-back-button">
        <span className="icon-back"></span>
        </button>
        <h2>Add Lawyer</h2>
        <div className="lawyer-header-underline"></div>
      </div>
      
      <div className="lawyer-card-content">
        <div className="lawyer-image-container">
          {image ? (
            <img src={URL.createObjectURL(image)} alt="Profile" className="lawyer-profile-image" />
          ) : (
            <div className="lawyer-profile-placeholder"></div>
          )}
          <button className="lawyer-photo-button" onClick={() => document.getElementById("fileUpload").click()}>
            Add Photo
          </button>
          <input 
            type="file" 
            accept="image/*" 
            id="fileUpload" 
            style={{ display: "none" }} 
            onChange={(e) => setImage(e.target.files[0])} 
          />
        </div>

        <div className="lawyer-form-container">
          <div className="lawyer-form-row">
            <label>Name:</label>
            <input 
              type="text" 
              placeholder="Full Name" 
              value={lawyer.name} 
              onChange={(e) => setLawyer({ ...lawyer, name: e.target.value })} 
              autoComplete="off" 
            />
          </div>
          
          <div className="lawyer-form-row">
            <label>Email:</label>
            <input 
              type="email" 
              placeholder="Email" 
              value={lawyer.email} 
              onChange={(e) => setLawyer({ ...lawyer, email: e.target.value })} 
              autoComplete="off" 
            />
          </div>
          
          <div className="lawyer-form-row">
            <label>Phone:</label>
            <input 
              type="text" 
              placeholder="Phone" 
              value={lawyer.phone} 
              onChange={(e) => setLawyer({ ...lawyer, phone: e.target.value })} 
              autoComplete="off" 
            />
          </div>
          
          <div className="lawyer-form-row">
            <label>Password:</label>
            <input 
              type="password" 
              placeholder="Password" 
              value={lawyer.password} 
              onChange={(e) => setLawyer({ ...lawyer, password: e.target.value })} 
              autoComplete="new-password" 
            />
          </div>
          
          <div className="lawyer-form-row">
            <label>Specialization:</label>
            <input 
              type="text" 
              placeholder="Specialization" 
              value={lawyer.specialization} 
              onChange={(e) => setLawyer({ ...lawyer, specialization: e.target.value })} 
              autoComplete="off" 
            />
          </div>
          
          <div className="lawyer-form-row">
            <label>License Number:</label>
            <input 
              type="text" 
              placeholder="License Number" 
              value={lawyer.licenseNumber} 
              onChange={(e) => setLawyer({ ...lawyer, licenseNumber: e.target.value })} 
              autoComplete="off" 
            />
          </div>
          
          <div className="lawyer-form-row">
            <label>Experience:</label>
            <input 
              type="text" 
              placeholder="Experience" 
              value={lawyer.experience} 
              onChange={(e) => setLawyer({ ...lawyer, experience: e.target.value })} 
              autoComplete="off" 
            />
          </div>
        </div>

        <div className="lawyer-button-container">
          <button onClick={addLawyer} className="lawyer-primary-button">
            <span className="lawyer-button-icon">✓</span> Add Lawyer
          </button>
        </div>
      </div>
    </div>
  );
};

export default AddLawyer;