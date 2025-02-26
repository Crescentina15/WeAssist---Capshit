import React, { useState, useEffect } from "react";
import { auth, db } from "./script/firebase";
import { ref, onValue } from "firebase/database";
import { useNavigate } from "react-router-dom";
import logo from "./assets/logo.png";
import "./index.css";

const AdminPanel = ({ user, onLogout }) => {
  const [lawyers, setLawyers] = useState([]);
  const [adminLawFirm, setAdminLawFirm] = useState("");
  const navigate = useNavigate();

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
          const filteredLawyers = Object.entries(snapshot.val()).map(([id, lawyer]) => ({
            id,
            ...lawyer
          })).filter(lawyer => lawyer.lawFirm === adminLawFirm);
          setLawyers(filteredLawyers);
        }
      });
    }
  }, [adminLawFirm]);

  return (
    <div className="admin-panel">
      <header className="admin-header">
        <img src={logo} alt="Logo" className="admin-logo" />
        <nav className="admin-nav">
          <button className="nav-button" onClick={() => navigate("/")}>Dashboard</button>
          <button className="nav-button" onClick={() => navigate("/addlawyer")}>Manage Lawyer</button>
          <button className="nav-button" onClick={() => navigate("/managesecretary")}>Manage Secretary</button>
          <button className="nav-button" onClick={() => navigate("/profile")}>Profile</button>
          <button className="nav-button logout-button" onClick={onLogout}>Logout</button>
        </nav>
      </header>

      <div className="admin-content">
        <div className="view-lawyers">
          <h2 className="lawyers">View Lawyers</h2>
          <ul>
            {lawyers.map((lawyer) => (
              <li key={lawyer.id}>
                <button className="lawyer-button" onClick={() => navigate(`/EditLawyer/${lawyer.id}`)}>
                    {lawyer.name} - {lawyer.specialization}
                </button>

              </li>
            ))}
          </ul>
        </div>

        <div className="analytics">
          <h2>Appointments & Analytics</h2>
        </div>
      </div>
    </div>
  );
};

export default AdminPanel;
