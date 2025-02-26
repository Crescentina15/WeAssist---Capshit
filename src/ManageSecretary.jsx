import React, { useState, useEffect } from "react";
import { auth, db } from "./script/firebase";
import { useNavigate } from "react-router-dom";
import { createUserWithEmailAndPassword, sendEmailVerification } from "firebase/auth";
import { ref, set, get } from "firebase/database";
import "./index.css";

const ManageSecretary = () => {
  const navigate = useNavigate(); 
  const [secretary, setSecretary] = useState({ name: "", email: "", phone: "", password: "" });
  const [lawFirmAdmin, setLawFirmAdmin] = useState(null); // Store logged-in admin's data

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

  const addSecretary = async () => {
    if (!lawFirmAdmin) {
      alert("Law firm admin data not loaded. Please try again.");
      return;
    }

    try {
      const userCredential = await createUserWithEmailAndPassword(auth, secretary.email, secretary.password);
      const secretaryUID = userCredential.user.uid;

      await set(ref(db, `secretaries/${secretaryUID}`), {
        name: secretary.name,
        email: secretary.email,
        phone: secretary.phone,
        role: "secretary",
        lawFirm: lawFirmAdmin.lawFirm, // Associate secretary with the admin's law firm
        adminUID: lawFirmAdmin.uid, // Store the admin's UID for reference
      });

      await sendEmailVerification(userCredential.user);
      alert("Secretary account created successfully! Verification email sent.");

      // Reset form fields
      setSecretary({ name: "", email: "", phone: "", password: "" });

      // Redirect to home
      setTimeout(() => navigate("/"), 1000);
    } catch (error) {
      alert("Error: " + error.message);
    }
  };

  return (
    <div className="profile-card">
      <h2>Manage Secretary</h2>
      
      {/* Hidden input to prevent autofill */}
      <input type="text" name="fake" autoComplete="off" style={{ display: "none" }} />

      <input type="text" placeholder="Name" value={secretary.name} onChange={(e) => setSecretary({ ...secretary, name: e.target.value })} autoComplete="off" />
      <input type="email" placeholder="Email" value={secretary.email} onChange={(e) => setSecretary({ ...secretary, email: e.target.value })} autoComplete="off" />
      <input type="password" placeholder="Password" value={secretary.password} onChange={(e) => setSecretary({ ...secretary, password: e.target.value })} autoComplete="new-password" />
      <input type="text" placeholder="Phone" value={secretary.phone} onChange={(e) => setSecretary({ ...secretary, phone: e.target.value })} autoComplete="off" />
      <button onClick={addSecretary}>Add Secretary</button>
      <button onClick={() => navigate("/")} className="cancel-button">Cancel</button>
    </div>
  );
};

export default ManageSecretary;
