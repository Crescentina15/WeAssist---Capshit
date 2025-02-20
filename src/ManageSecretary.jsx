import React, { useState } from "react";
import { auth, db } from "./script/firebase";
import { createUserWithEmailAndPassword, sendEmailVerification } from "firebase/auth";
import { ref, set } from "firebase/database";
import "./index.css";

const ManageSecretary = () => {
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
    <div className="form-container">
      <h2>Manage Secretary</h2>
      <input type="text" placeholder="Name" value={secretary.name} onChange={(e) => setSecretary({ ...secretary, name: e.target.value })} />
      <input type="email" placeholder="Email" value={secretary.email} onChange={(e) => setSecretary({ ...secretary, email: e.target.value })} />
      <input type="password" placeholder="Password" value={secretary.password} onChange={(e) => setSecretary({ ...secretary, password: e.target.value })} />
      <input type="text" placeholder="Phone" value={secretary.phone} onChange={(e) => setSecretary({ ...secretary, phone: e.target.value })} />
      <button onClick={addSecretary}>Add Secretary</button>
    </div>
  );
};

export default ManageSecretary;
