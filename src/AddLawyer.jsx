import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { auth, db } from "./script/firebase";
import { createUserWithEmailAndPassword, sendEmailVerification } from "firebase/auth";
import { ref, set } from "firebase/database";
import "./index.css";

const AddLawyer = () => {
  const navigate = useNavigate();
  const [lawyer, setLawyer] = useState({ name: "", email: "", phone: "", specialization: "", licenseNumber: "", experience: "", password: "" });
  const [image, setImage] = useState(null);

  const addLawyer = () => {
    createUserWithEmailAndPassword(auth, lawyer.email, lawyer.password)
      .then((userCredential) => {
        set(ref(db, "lawyers/" + userCredential.user.uid), {
          name: lawyer.name,
          email: lawyer.email,
          phone: lawyer.phone,
          specialization: lawyer.specialization,
          licenseNumber: lawyer.licenseNumber,
          experience: lawyer.experience,
          role: "lawyer",
          profileImage: image ? URL.createObjectURL(image) : "", 
        }).then(() => {
          sendEmailVerification(userCredential.user);
          alert("Lawyer account created successfully! Verification email sent.");
          
          // Reset form fields
          setLawyer({ name: "", email: "", phone: "", specialization: "", licenseNumber: "", experience: "", password: "" });
          setImage(null);

          // Redirect to home after a short delay
          setTimeout(() => navigate("/"), 1000);
        });
      })
      .catch(error => alert("Error: " + error.message));
  };

  return (
    <div className="profile-card">
      <h2>Add Lawyer</h2>
      <div className="profile-image-container">
        {image ? <img src={URL.createObjectURL(image)} alt="Profile" className="profile-image" /> : <div className="image-placeholder">No Image</div>}
        <input type="file" accept="image/*" onChange={(e) => setImage(e.target.files[0])} className="file-input" />
      </div>
          <input type="text" placeholder="Name" value={lawyer.name} 
            onChange={(e) => setLawyer({ ...lawyer, name: e.target.value })} 
            autoComplete="off"
          />
          <input type="email" placeholder="Email" value={lawyer.email} 
            onChange={(e) => setLawyer({ ...lawyer, email: e.target.value })} 
            autoComplete="off"
          />
          <input type="password" placeholder="Password" value={lawyer.password} 
            onChange={(e) => setLawyer({ ...lawyer, password: e.target.value })} 
            autoComplete="new-password"
          />
          <input type="text" placeholder="Phone" value={lawyer.phone} 
            onChange={(e) => setLawyer({ ...lawyer, phone: e.target.value })} 
            autoComplete="off"
          />
          <input type="text" placeholder="Specialization" value={lawyer.specialization} 
            onChange={(e) => setLawyer({ ...lawyer, specialization: e.target.value })} 
            autoComplete="off"
          />
          <input type="text" placeholder="License Number" value={lawyer.licenseNumber} 
            onChange={(e) => setLawyer({ ...lawyer, licenseNumber: e.target.value })} 
            autoComplete="off"
          />
          <input type="text" placeholder="Experience" value={lawyer.experience} 
            onChange={(e) => setLawyer({ ...lawyer, experience: e.target.value })} 
            autoComplete="off"
          />
          <button onClick={addLawyer}>Add Lawyer</button>
          <button onClick={() => navigate("/")} className="cancel-button">Cancel</button>

      </div>


  );
};

export default AddLawyer;
