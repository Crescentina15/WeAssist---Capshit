import React, { useState, useEffect } from "react";
import { auth, db } from "./script/firebase";
import { ref, onValue } from "firebase/database";
import { useNavigate } from "react-router-dom";
import logo from "./assets/logo.png";
import "./index.css";
import { analytics } from "./script/firebase"; // Import Firebase Analytics
import { logEvent } from "firebase/analytics"; // Import logEvent

const AdminPanel = ({ user, onLogout }) => {
  const [lawyers, setLawyers] = useState([]);
  const [adminLawFirm, setAdminLawFirm] = useState("");
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    const adminRef = ref(db, "law_firm_admin/" + user.uid);
    onValue(adminRef, (snapshot) => {
      if (snapshot.exists()) {
        setAdminLawFirm(snapshot.val().lawFirm);
        if (analytics) {
          logEvent(analytics, "admin_dashboard_view", { admin_id: user.uid });
        }
      }
    });
  }, [user]);

  useEffect(() => {
    if (adminLawFirm) {
      const lawyersRef = ref(db, "lawyers");
      onValue(lawyersRef, (snapshot) => {
        if (snapshot.exists()) {
          const filteredLawyers = Object.entries(snapshot.val())
            .map(([id, lawyer]) => ({
              id,
              ...lawyer
            }))
            .filter((lawyer) => lawyer.lawFirm === adminLawFirm);
          setLawyers(filteredLawyers);
          if (analytics) {
            logEvent(analytics, "lawyers_list_loaded", { law_firm: adminLawFirm, count: filteredLawyers.length });
          }
        }
      });
    }
  }, [adminLawFirm]);

  return (
    <div className="admin-panel">
      <header className="admin-header">
        <img src={logo} alt="Logo" className="admin-logo" />
        <nav className="admin-nav">
          <button className="nav-button" onClick={() => { 
            navigate("/"); 
            if (analytics) logEvent(analytics, "navigate", { destination: "Dashboard" });
          }}>Dashboard</button>
          <button className="nav-button" onClick={() => { 
            navigate("/addlawyer"); 
            if (analytics) logEvent(analytics, "navigate", { destination: "Manage Lawyer" });
          }}>Manage Lawyer</button>
          <button className="nav-button" onClick={() => { 
            navigate("/managesecretary"); 
            if (analytics) logEvent(analytics, "navigate", { destination: "Manage Secretary" });
          }}>Manage Secretary</button>

          {/* Profile Dropdown */}
          <div className="dropdown">
            <button className="nav-button dropdown-toggle" onClick={() => setDropdownOpen(!dropdownOpen)}>
              Profile
            </button>
            {dropdownOpen && (
              <ul className="dropdown-menu">
                <li><button className="dropdown-item" onClick={() => navigate("/Profile")}>Settings</button></li>
                <li><button className="dropdown-item" onClick={() => navigate("/privacy")}>Privacy Policy</button></li>
                <li><button className="dropdown-item" onClick={() => navigate("/plans-subscription")}>Plan & Subscription</button></li>
                <li><hr className="dropdown-divider" /></li>
                <li>
                  <button className="dropdown-item logout" onClick={() => { 
                    onLogout(); 
                    if (analytics) logEvent(analytics, "logout", { admin_id: user.uid });
                  }}>Logout</button>
                </li>
              </ul>
            )}
          </div>
        </nav>
      </header>

      <div className="admin-content">
        <div className="view-lawyers">
          <h2 className="lawyers">View Lawyers</h2>
          <ul>
            {lawyers.map((lawyer) => (
              <li key={lawyer.id}>
                <button className="lawyer-button" onClick={() => { 
                  navigate(`/EditLawyer/${lawyer.id}`); 
                  if (analytics) logEvent(analytics, "edit_lawyer", { lawyer_id: lawyer.id });
                }}>
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
