import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { createUserWithEmailAndPassword } from "firebase/auth";
import { ref, set } from "firebase/database";
import { auth, db } from "./script/firebase";
import logo from "./assets/logo.png";
import "./index.css"; 

const Register = () => {
  const [formData, setFormData] = useState({
    lawFirm: "",
    phoneNumber: "",
    email: "",
    specialization: "",
    operatingHours: "",
    licenseNumber: "",
    officeAddress: "",
    password: "",
    confirmPassword: ""
  });
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleRegister = async (e) => {
    e.preventDefault();

    if (formData.password !== formData.confirmPassword) {
      setError("Passwords do not match");
      return;
    }

    try {
      const userCredential = await createUserWithEmailAndPassword(auth, formData.email, formData.password);
      const user = userCredential.user;
      const lawFirmAdminRef = ref(db, "law_firm_admin/" + user.uid);
      await set(lawFirmAdminRef, {
        lawFirm: formData.lawFirm,
        phoneNumber: formData.phoneNumber,
        email: formData.email,
        operatingHours: formData.operatingHours,
        licenseNumber: formData.licenseNumber,
        officeAddress: formData.officeAddress,
        uid: user.uid
      });
      alert("Registration successful! Redirecting to payment...");
      // Redirect to Payment page
      navigate(`/plans-subscription?name=Law Firm Registration&price=₱4,800`);
    } catch (error) {
      setError(error.message);
    }
  };

  return (
    <div className="register-page">
      <div className="register-card">
        <div className="register-logo-side">
          <div className="logo-hexagon">
            <div className="logo-content">
            <img src={logo} alt="WeAssist Logo" />
            </div>
          </div>
        </div>

        <div className="register-form-side">
          <div className="form-header">
            <h2>REGISTER</h2>
            <p className="subtitle">SIGN-UP NOW!</p>
          </div>

          {error && <p className="error-message">{error}</p>}

          <form onSubmit={handleRegister} autoComplete="off">
            <div className="form-group">
              <label>FIRM NAME</label>
              <input 
                type="text" 
                name="lawFirm" 
                value={formData.lawFirm}
                onChange={handleChange} 
                required 
                style={{ color: "black" }}
              />
            </div>

            <div className="form-group">
              <label>PHONE NUMBER</label>
              <input 
                type="tel" 
                name="phoneNumber" 
                value={formData.phoneNumber}
                onChange={handleChange} 
                required 
                style={{ color: "black" }}
              />
            </div>

            <div className="form-group">
              <label>EMAIL</label>
              <input 
                type="email" 
                name="email" 
                value={formData.email}
                onChange={handleChange} 
                required 
                style={{ color: "black" }}
              />
            </div>

            <div className="form-group">
              <label>PASSWORD</label>
              <input 
                type="password" 
                name="password" 
                value={formData.password}
                onChange={handleChange} 
                required 
                style={{ color: "black" }}
              />
            </div>

            <div className="form-group">
              <label>CONFIRM PASSWORD</label>
              <input 
                type="password" 
                name="confirmPassword" 
                value={formData.confirmPassword}
                onChange={handleChange} 
                required 
                style={{ color: "black" }}
              />
            </div>

            <div className="form-group">
              <label>OPERATING HOURS</label>
              <input 
                type="text" 
                name="operatingHours" 
                value={formData.operatingHours}
                onChange={handleChange} 
                required 
                style={{ color: "black" }}
              />
            </div>

            <div className="form-group">
              <label>LICENSE NUMBER</label>
              <input 
                type="text" 
                name="licenseNumber" 
                value={formData.licenseNumber}
                onChange={handleChange} 
                required 
                style={{ color: "black" }}
              />
            </div>

            <div className="form-group">
              <label>OFFICE ADDRESS</label>
              <input 
                type="text" 
                name="officeAddress" 
                value={formData.officeAddress}
                onChange={handleChange} 
                required 
                style={{ color: "black" }}
              />
            </div>


            <button type="submit" className="create-account-btn">CREATE ACCOUNT</button>
            
            <p className="login-link">
              Already have an account? <a href="/login">Login</a>
            </p>
          </form>
          
        </div>
      </div>
    </div>
  );
};

export default Register;