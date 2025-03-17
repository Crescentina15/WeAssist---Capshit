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
    <div className="profile-card">
      <h2>Add Lawyer</h2>
      
      <div className="profile-image-container">
        {image ? (
          <img src={URL.createObjectURL(image)} alt="Profile" className="profile-image" />
        ) : (
          <div className="image-placeholder"></div>
        )}
        <button className="photo-button" onClick={() => document.getElementById("fileUpload").click()}>
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

      <div className="input-grid">
        <input type="text" className="full-width" placeholder="Full Name" value={lawyer.name} onChange={(e) => setLawyer({ ...lawyer, name: e.target.value })} autoComplete="off" />
        <input type="email" placeholder="Email" value={lawyer.email} onChange={(e) => setLawyer({ ...lawyer, email: e.target.value })} autoComplete="off" />
        <input type="password" placeholder="Password" value={lawyer.password} onChange={(e) => setLawyer({ ...lawyer, password: e.target.value })} autoComplete="new-password" />
        <input type="text" placeholder="Phone" value={lawyer.phone} onChange={(e) => setLawyer({ ...lawyer, phone: e.target.value })} autoComplete="off" />
        <input type="text" placeholder="Specialization" value={lawyer.specialization} onChange={(e) => setLawyer({ ...lawyer, specialization: e.target.value })} autoComplete="off" />
        <input type="text" placeholder="License Number" value={lawyer.licenseNumber} onChange={(e) => setLawyer({ ...lawyer, licenseNumber: e.target.value })} autoComplete="off" />
        <input type="text" placeholder="Experience" value={lawyer.experience} onChange={(e) => setLawyer({ ...lawyer, experience: e.target.value })} autoComplete="off" />
      </div>

      <button onClick={addLawyer} className="cancel-button">Add Lawyer</button>
      <button onClick={() => navigate("/")} className="cancel-button">Cancel</button>
    </div>
  );
};

export default AddLawyer;
