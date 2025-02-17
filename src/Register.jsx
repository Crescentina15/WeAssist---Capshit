import React, { useState } from "react";
import { createUserWithEmailAndPassword } from "firebase/auth";
import { ref, set } from "firebase/database"; // Ensure you import ref and set
import { auth, db } from "./script/firebase"; // Your firebase.js file

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

      // Create a reference in Realtime Database under "law_firm_admin"
      const lawFirmAdminRef = ref(db, "law_firm_admin/" + user.uid);

      // Save the additional firm data
      await set(lawFirmAdminRef, {
        lawFirm: formData.lawFirm,
        firmType: formData.firmType,
        firmDescription: formData.firmDescription,
        phoneNumber: formData.phoneNumber,
        email: formData.email,
        website: formData.website,
        specialization: formData.specialization,
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
    <div>
      <h2>Register Law Firm Admin</h2>
      {error && <p style={{ color: "red" }}>{error}</p>}
      <form onSubmit={handleRegister}>
        <input type="text" name="lawFirm" placeholder="Firm Name" onChange={handleChange} required />
        <input type="text" name="firmType" placeholder="Firm Type" onChange={handleChange} required />
        <input type="text" name="firmDescription" placeholder="Firm Description" onChange={handleChange} required />
        <input type="tel" name="phoneNumber" placeholder="Phone Number" onChange={handleChange} required />
        <input type="email" name="email" placeholder="Email" onChange={handleChange} required />
        <input type="url" name="website" placeholder="Website" onChange={handleChange} required />
        <input type="text" name="specialization" placeholder="Specialization" onChange={handleChange} required />
        <input type="text" name="operatingHours" placeholder="Operating Hours" onChange={handleChange} required />
        <input type="text" name="licenseNumber" placeholder="License Number" onChange={handleChange} required />
        <input type="text" name="officeAddress" placeholder="Office Address" onChange={handleChange} required />
        <input type="password" name="password" placeholder="Password" onChange={handleChange} required />
        <button type="submit">Register</button>
      </form>
    </div>
  );
};

export default Register;
