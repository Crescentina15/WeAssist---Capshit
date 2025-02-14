// AdminPanel.jsx
import React, { useState, useEffect } from "react";
import { auth, db } from "./firebase";
import { createUserWithEmailAndPassword, signOut, sendEmailVerification } from "firebase/auth";
import { ref, set, onValue } from "firebase/database";

const AdminPanel = ({ user, onLogout }) => {
  const [secretary, setSecretary] = useState({ name: "", email: "", phone: "", password: "" });
  const [lawyer, setLawyer] = useState({ name: "", specialization: "", lawFirm: "", licenseNumber: "", experience: "", contact: { phone: "", email: "", address: "" }, password: "" });
  const [lawyers, setLawyers] = useState([]);

  useEffect(() => {
    onValue(ref(db, "lawyers"), (snapshot) => {
      setLawyers(snapshot.val() ? Object.values(snapshot.val()) : []);
    });
  }, []);

  const addSecretary = () => {
    createUserWithEmailAndPassword(auth, secretary.email, secretary.password)
      .then((userCredential) => {
        set(ref(db, "secretaries/" + userCredential.user.uid), { 
          name: secretary.name, email: secretary.email, lawFirm: secretary.lawFirm,phone: secretary.phone, role: "secretary" 
        }).then(() => {
          sendEmailVerification(userCredential.user);
          alert("Secretary account created successfully! Verification email sent.");
          setSecretary({ name: "", email: "", phone: "", password: "" , lawFirm:""});
        });
      })
      .catch(error => alert("Error: " + error.message));
  };

  const addLawyer = () => {
    createUserWithEmailAndPassword(auth, lawyer.contact.email, lawyer.password)
      .then((userCredential) => {
        set(ref(db, "lawyers/" + userCredential.user.uid), {
          ...lawyer,
          role: "lawyer",
          verified: false
        }).then(() => {
          sendEmailVerification(userCredential.user);
          alert("Lawyer account created successfully! Verification email sent.");
          setLawyer({ name: "", specialization: "", lawFirm: "", licenseNumber: "", experience: "", backGround:" ", contact: { phone: "", email: "", address: "" }, password: "" });
        });
      })
      .catch(error => alert("Error: " + error.message));
  };

  return (
    <div>
      <h1>Law Firm Admin Panel</h1>
      <button onClick={onLogout}>Logout</button>

      <h2>Manage Secretary</h2>
      <input type="text" placeholder="Name" value={secretary.name} onChange={(e) => setSecretary({ ...secretary, name: e.target.value })} />
      <input type="email" placeholder="Email" value={secretary.email} onChange={(e) => setSecretary({ ...secretary, email: e.target.value })} />
      <input type="text" placeholder="Law Firm" value={secretary.lawFirm} onChange={(e) => setSecretary({ ...secretary, lawFirm: e.target.value })} />
      <input type="text" placeholder="Phone" value={secretary.phone} onChange={(e) => setSecretary({ ...secretary, phone: e.target.value })} />
      <input type="password" placeholder="Password" value={secretary.password} onChange={(e) => setSecretary({ ...secretary, password: e.target.value })} />
      <button onClick={addSecretary}>Add Secretary</button>

      <h2>Manage Lawyer</h2>
      <input type="text" placeholder="Lawyer Name" value={lawyer.name} onChange={(e) => setLawyer({ ...lawyer, name: e.target.value })} />
      <input type="text" placeholder="Specialization" value={lawyer.specialization} onChange={(e) => setLawyer({ ...lawyer, specialization: e.target.value })} />
      <input type="text" placeholder="Law Firm" value={lawyer.lawFirm} onChange={(e) => setLawyer({ ...lawyer, lawFirm: e.target.value })} />
      <input type="text" placeholder="License Number" value={lawyer.licenseNumber} onChange={(e) => setLawyer({ ...lawyer, licenseNumber: e.target.value })} />
      <input type="text" placeholder="Background" value={lawyer.backGround} onChange={(e) => setLawyer({ ...lawyer, backGround: e.target.value })} />
      <input type="text" placeholder="Experience" value={lawyer.experience} onChange={(e) => setLawyer({ ...lawyer, experience: e.target.value })} />
      <input type="text" placeholder="Phone" value={lawyer.contact.phone} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, phone: e.target.value } })} />
      <input type="email" placeholder="Email" value={lawyer.contact.email} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, email: e.target.value } })} />
      <input type="text" placeholder="Address" value={lawyer.contact.address} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, address: e.target.value } })} />
      <input type="password" placeholder="Password" value={lawyer.password} onChange={(e) => setLawyer({ ...lawyer, password: e.target.value })} />
      <button onClick={addLawyer}>Add Lawyer</button>

      <h2>View Lawyers</h2>
      <ul>
        {lawyers.map((lawyer, index) => (
          <li key={index}>{lawyer.name} - {lawyer.specialization}</li>
        ))}
      </ul>
    </div>
  );
};

export default AdminPanel;