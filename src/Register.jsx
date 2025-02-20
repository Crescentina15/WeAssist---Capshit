import React, { useState } from "react";
import { createUserWithEmailAndPassword } from "firebase/auth";
import { ref, set } from "firebase/database";
import { auth, db } from "./script/firebase"; 
import logo from "./assets/logo.png"

const Register = () => {
  const [formData, setFormData] = useState({
    lawFirm: "",
    firmType: "",
    firmDescription: "",
    phoneNumber: "",
    email: "",
    website: "",
    specialization: "",
    operatingHours: "",
    licenseNumber: "",
    officeAddress: "",
    password: ""
  });
  const [error, setError] = useState("");

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    try {
      const userCredential = await createUserWithEmailAndPassword(auth, formData.email, formData.password);
      const user = userCredential.user;

      const lawFirmAdminRef = ref(db, "law_firm_admin/" + user.uid);

      await set(lawFirmAdminRef, {
        lawFirm: formData.lawFirm,
        firmType: formData.firmType,
        firmDescription: formData.firmDescription,
        phoneNumber: formData.phoneNumber,
        email: formData.email,
        operatingHours: formData.operatingHours,
        licenseNumber: formData.licenseNumber,
        officeAddress: formData.officeAddress,
        uid: user.uid
      });

      alert("Registration successful!");
    } catch (error) {
      setError(error.message);
      alert("Registration failed: " + error.message);
    }
  };

  return (
    <div className="page-container">
      <header className="header">
        <img src={logo}/>
      </header>
      <div className="form-container">
        <h2>Register Law Firm Admin</h2>
        {error && <p className="error">{error}</p>}
        <form onSubmit={handleRegister}>
          <input type="text" name="lawFirm" placeholder="Firm Name" onChange={handleChange} required />
          <input type="text" name="firmType" placeholder="Firm Type" onChange={handleChange} required />
          <input type="text" name="firmDescription" placeholder="Firm Description" onChange={handleChange} required />
          <input type="tel" name="phoneNumber" placeholder="Phone Number" onChange={handleChange} required />
          <input type="email" name="email" placeholder="Email" onChange={handleChange} required />
          <input type="text" name="operatingHours" placeholder="Operating Hours" onChange={handleChange} required />
          <input type="text" name="licenseNumber" placeholder="License Number" onChange={handleChange} required />
          <input type="text" name="officeAddress" placeholder="Office Address" onChange={handleChange} required />
          <input type="password" name="password" placeholder="Password" onChange={handleChange} required />
          <button type="submit">Register</button>
        </form>
      </div>
    </div>
  );
};

export default Register;
