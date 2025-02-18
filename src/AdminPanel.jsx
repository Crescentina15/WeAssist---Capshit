import React, { useState, useEffect } from "react";
import { auth, db } from "./script/firebase";
import { createUserWithEmailAndPassword, sendEmailVerification } from "firebase/auth";
import { ref, set, onValue } from "firebase/database";
import { Link } from "react-router-dom";
import logo from "./assets/logo.png"
import './index.css';  

const AdminPanel = ({ user, onLogout }) => {
  const [secretary, setSecretary] = useState({ name: "", email: "", phone: "", password: "" });
  const [lawyer, setLawyer] = useState({ name: "", specialization: "", lawFirm: "", licenseNumber: "", experience: "", contact: { phone: "", email: "", address: "" }, password: "" });
  const [lawyers, setLawyers] = useState([]);
  const [adminLawFirm, setAdminLawFirm] = useState("");

  useEffect(() => {
    const adminRef = ref(db, "law_firm_admin/" + user.uid);
    onValue(adminRef, (snapshot) => {
      if (snapshot.exists()) {
        setAdminLawFirm(snapshot.val().lawFirm);
      }
    });
  }, [user]);

  useEffect(() => {
    if (adminLawFirm) {
      const lawyersRef = ref(db, "lawyers");
      onValue(lawyersRef, (snapshot) => {
        if (snapshot.exists()) {
          const filteredLawyers = Object.values(snapshot.val()).filter(lawyer => lawyer.lawFirm === adminLawFirm);
          setLawyers(filteredLawyers);
        }
      });
    }
  }, [adminLawFirm]);

  const addSecretary = () => {
    createUserWithEmailAndPassword(auth, secretary.email, secretary.password)
      .then((userCredential) => {
        set(ref(db, "secretaries/" + userCredential.user.uid), { 
          name: secretary.name, email: secretary.email, lawFirm: secretary.lawFirm, phone: secretary.phone, role: "secretary" 
        }).then(() => {
          sendEmailVerification(userCredential.user);
          alert("Secretary account created successfully! Verification email sent.");
          setSecretary({ name: "", email: "", phone: "", password: "", lawFirm: "" });
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
          setLawyer({ name: "", specialization: "", lawFirm: "", licenseNumber: "", experience: "", contact: { phone: "", email: "", address: "" }, password: "" });
        });
      })
      .catch(error => alert("Error: " + error.message));
  };

  return (
    <div className="admin-panel-container">
      <div className="admin-panel-header">
        <div className="admin-panel-logo">
          <img src={logo} alt="Logo" />
        </div>
        <div className="logout-btn-container">
          <Link to="/profile" className="admin-clickable-text">Profile</Link>
          <span className="logout-btn" onClick={onLogout}>Logout</span>
        </div>
      </div>

      <div className="admin-panel-content">
        <h1>Law Firm Admin Panel</h1>

        {/* Form Section: Manage Secretary */}
        <div className="form-section">
          <h2>Manage Secretary</h2>
          <div className="form-container">
            <input type="text" placeholder="Name" value={secretary.name} onChange={(e) => setSecretary({ ...secretary, name: e.target.value })} />
            <input type="email" placeholder="Email" value={secretary.email} onChange={(e) => setSecretary({ ...secretary, email: e.target.value })} />
            <input type="text" placeholder="Law Firm" value={secretary.lawFirm} onChange={(e) => setSecretary({ ...secretary, lawFirm: e.target.value })} />
            <input type="text" placeholder="Phone" value={secretary.phone} onChange={(e) => setSecretary({ ...secretary, phone: e.target.value })} />
            <input type="password" placeholder="Password" value={secretary.password} onChange={(e) => setSecretary({ ...secretary, password: e.target.value })} />
            <button onClick={addSecretary}>Add Secretary</button>
          </div>
        </div>

        {/* Form Section: Manage Lawyer */}
        <div className="form-section">
          <h2>Manage Lawyer</h2>
          <div className="form-container">
            <input type="text" placeholder="Lawyer Name" value={lawyer.name} onChange={(e) => setLawyer({ ...lawyer, name: e.target.value })} />
            <input type="text" placeholder="Specialization" value={lawyer.specialization} onChange={(e) => setLawyer({ ...lawyer, specialization: e.target.value })} />
            <input type="text" placeholder="Law Firm" value={lawyer.lawFirm} onChange={(e) => setLawyer({ ...lawyer, lawFirm: e.target.value })} />
            <input type="text" placeholder="License Number" value={lawyer.licenseNumber} onChange={(e) => setLawyer({ ...lawyer, licenseNumber: e.target.value })} />
            <input type="text" placeholder="Experience" value={lawyer.experience} onChange={(e) => setLawyer({ ...lawyer, experience: e.target.value })} />
            <input type="text" placeholder="Phone" value={lawyer.contact.phone} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, phone: e.target.value } })} />
            <input type="email" placeholder="Email" value={lawyer.contact.email} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, email: e.target.value } })} />
            <input type="text" placeholder="Address" value={lawyer.contact.address} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, address: e.target.value } })} />
            <input type="password" placeholder="Password" value={lawyer.password} onChange={(e) => setLawyer({ ...lawyer, password: e.target.value })} />
            <button onClick={addLawyer}>Add Lawyer</button>
          </div>
        </div>

        {/* View Lawyers Section */}
        <div className="view-lawyers">
          <h2>View Lawyers</h2>
          <ul>
            {lawyers.map((lawyer, index) => (
              <li key={index}>{lawyer.name} - {lawyer.specialization}</li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
};

export default AdminPanel;
