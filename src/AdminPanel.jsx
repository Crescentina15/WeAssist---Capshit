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
  const [appointmentData, setAppointmentData] = useState([]);
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

  // Fetch appointments data
  useEffect(() => {
    if (lawyers.length > 0) {
      const appointmentsRef = ref(db, "appointments");
      onValue(appointmentsRef, (snapshot) => {
        if (snapshot.exists()) {
          const allAppointments = snapshot.val();
          
          // Process appointments for the line graph
          const appointmentsByMonth = {};
          const lawyerColors = {};
          
          // Assign colors to lawyers
          lawyers.forEach((lawyer, index) => {
            // Generate different colors for each lawyer
            const hue = (index * 137.5) % 360; // Golden ratio to distribute colors
            lawyerColors[lawyer.id] = `hsl(${hue}, 70%, 50%)`;
          });
          
          // Structure the data for a line graph by month
          Object.entries(allAppointments).forEach(([id, appointment]) => {
            if (appointment.lawyerId && appointment.date) {
              const date = new Date(appointment.date);
              const monthYear = `${date.getMonth() + 1}/${date.getFullYear()}`;
              
              if (!appointmentsByMonth[monthYear]) {
                appointmentsByMonth[monthYear] = {};
                lawyers.forEach(lawyer => {
                  appointmentsByMonth[monthYear][lawyer.id] = 0;
                });
              }
              
              appointmentsByMonth[monthYear][appointment.lawyerId]++;
            }
          });
          
          // Convert to array and sort by date
          const chartData = Object.entries(appointmentsByMonth)
            .map(([monthYear, counts]) => {
              const [month, year] = monthYear.split('/');
              return {
                monthYear,
                sortDate: new Date(parseInt(year), parseInt(month) - 1, 1),
                ...counts
              };
            })
            .sort((a, b) => a.sortDate - b.sortDate);
          
          setAppointmentData({
            chartData,
            lawyerColors
          });
          
          if (analytics) {
            logEvent(analytics, "appointment_data_loaded");
          }
        }
      });
    }
  }, [lawyers]);

  // Function to draw the line graph
  const drawLineGraph = () => {
    if (!appointmentData.chartData || appointmentData.chartData.length === 0) {
      return <p>No appointment data available</p>;
    }

    const chartData = appointmentData.chartData;
    const lawyerColors = appointmentData.lawyerColors;
    
    // Calculate graph dimensions
    const graphHeight = 200;
    const graphWidth = chartData.length > 1 ? chartData.length * 80 : 320;
    
    // Find max appointment count for scaling
    let maxCount = 0;
    chartData.forEach(monthData => {
      lawyers.forEach(lawyer => {
        if (monthData[lawyer.id] > maxCount) {
          maxCount = monthData[lawyer.id];
        }
      });
    });
    
    // Add padding to max count
    maxCount = Math.ceil(maxCount * 1.2);
    
    // Create points for each lawyer's line
    const lawyerLines = {};
    lawyers.forEach(lawyer => {
      lawyerLines[lawyer.id] = chartData.map((monthData, index) => {
        const x = (index / (chartData.length - 1 || 1)) * graphWidth;
        const y = graphHeight - ((monthData[lawyer.id] || 0) / maxCount) * graphHeight;
        return { x, y };
      });
    });
    
    // Generate SVG path strings
    const paths = {};
    lawyers.forEach(lawyer => {
      const points = lawyerLines[lawyer.id];
      let pathString = '';
      
      if (points.length > 0) {
        pathString = `M ${points[0].x},${points[0].y}`;
        for (let i = 1; i < points.length; i++) {
          pathString += ` L ${points[i].x},${points[i].y}`;
        }
      }
      
      paths[lawyer.id] = pathString;
    });
    
    return (
      <div className="line-graph-container">
        <div className="graph-labels">
          <div className="y-axis-labels">
            {[...Array(5)].map((_, i) => (
              <div key={i} className="y-label">
                {Math.round((maxCount / 4) * (4 - i))}
              </div>
            ))}
          </div>
          
          <svg width={graphWidth} height={graphHeight} className="line-graph">
            {/* Horizontal grid lines */}
            {[...Array(5)].map((_, i) => (
              <line 
                key={`grid-${i}`}
                x1="0" 
                y1={(graphHeight / 4) * i} 
                x2={graphWidth} 
                y2={(graphHeight / 4) * i}
                stroke="#e0e0e0" 
                strokeWidth="1"
              />
            ))}
            
            {/* Lines for each lawyer */}
            {lawyers.map(lawyer => (
              <g key={lawyer.id}>
                <path
                  d={paths[lawyer.id]}
                  fill="none"
                  stroke={lawyerColors[lawyer.id]}
                  strokeWidth="2"
                />
                
                {/* Points for each month */}
                {lawyerLines[lawyer.id].map((point, i) => (
                  <circle 
                    key={`${lawyer.id}-${i}`}
                    cx={point.x} 
                    cy={point.y} 
                    r="4"
                    fill={lawyerColors[lawyer.id]}
                    onClick={() => {
                      navigate(`/lawyer-appointments/${lawyer.id}?month=${chartData[i].monthYear}`);
                      if (analytics) logEvent(analytics, "view_lawyer_appointments", { 
                        lawyer_id: lawyer.id,
                        month: chartData[i].monthYear
                      });
                    }}
                    className="data-point"
                  />
                ))}
              </g>
            ))}
          </svg>
        </div>
        
        <div className="x-axis-labels">
          {chartData.map((monthData, i) => (
            <div key={i} className="x-label">
              {monthData.monthYear}
            </div>
          ))}
        </div>
        
        <div className="graph-legend">
          {lawyers.map(lawyer => (
            <div key={lawyer.id} className="legend-item">
              <div className="color-swatch" style={{ backgroundColor: lawyerColors[lawyer.id] }}></div>
              <span style={{ color: 'black' }}>{lawyer.name}</span>
              
            </div>
          ))}
        </div>
      </div>
    );
  };

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
          <div className="lawyer-appointments">
            {lawyers.length > 0 ? (
              drawLineGraph()
            ) : (
              <p>No lawyers found in your law firm.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default AdminPanel;