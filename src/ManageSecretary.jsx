import React, { useState } from "react";
import { auth, db } from "./script/firebase";
import { useNavigate } from "react-router-dom";
import { createUserWithEmailAndPassword, sendEmailVerification } from "firebase/auth";
import { ref, set } from "firebase/database";
import "./index.css";

const ManageSecretary = () => {
  const navigate = useNavigate(); 
  const [secretary, setSecretary] = useState({ name: "", email: "", phone: "", password: "" });

  const addSecretary = () => {
    createUserWithEmailAndPassword(auth, secretary.email, secretary.password)
      .then((userCredential) => {
        set(ref(db, "secretaries/" + userCredential.user.uid), {
          name: secretary.name,
          email: secretary.email,
          phone: secretary.phone,
          role: "secretary",
        }).then(() => {
          sendEmailVerification(userCredential.user);
          alert("Secretary account created successfully! Verification email sent.");
          setSecretary({ name: "", email: "", phone: "", password: "" });
        });
      })
      .catch(error => alert("Error: " + error.message));
  };

  return (
    <div className="profile-card">
      <h2>Manage Secretary</h2>

      {/* Hidden input to prevent autofill */}
      <input type="text" name="fake" autoComplete="off" style={{ display: "none" }} />

          <input 
            type="text" 
            placeholder="Name" 
            value={secretary.name} 
            onChange={(e) => setSecretary({ ...secretary, name: e.target.value })} 
            autoComplete="off"
          />
          <input 
            type="email" 
            placeholder="Email" 
            value={secretary.email} 
            onChange={(e) => setSecretary({ ...secretary, email: e.target.value })} 
            autoComplete="off"
          />
          <input 
            type="password" 
            placeholder="Password" 
            value={secretary.password} 
            onChange={(e) => setSecretary({ ...secretary, password: e.target.value })} 
            autoComplete="new-password"
          />
          <input 
            type="text" 
            placeholder="Phone" 
            value={secretary.phone} 
            onChange={(e) => setSecretary({ ...secretary, phone: e.target.value })} 
            autoComplete="off"
          />
          <button onClick={addSecretary}>Add Secretary</button>
          <button onClick={() => navigate("/")} className="cancel-button">Cancel</button>

    </div>
);

};

export default ManageSecretary;
