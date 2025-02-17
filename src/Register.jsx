import React, { useState } from "react";
import { auth, db } from "./firebase";
import { createUserWithEmailAndPassword } from "firebase/auth";
import { ref, set } from "firebase/database";
import { useNavigate } from "react-router-dom";

const Register = () => {
  const [firmName, setFirmName] = useState("");
  const [firmType, setFirmType] = useState("");
  const [firmDescription, setFirmDescription] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [email, setEmail] = useState("");
  const [website, setWebsite] = useState("");
  const [specialization, setSpecialization] = useState("");
  const [operatingHours, setOperatingHours] = useState("");
  const [licenseNumber, setLicenseNumber] = useState("");
  const [officeAddress, setOfficeAddress] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const navigate = useNavigate();

  const handleRegister = async (e) => {
    e.preventDefault();
    try {
      const userCredential = await createUserWithEmailAndPassword(auth, email, password);
      const user = userCredential.user;

      await set(ref(db, "users/" + user.uid), {
        firmName,
        firmType,
        firmDescription,
        phoneNumber,
        email,
        website,
        specialization,
        operatingHours,
        licenseNumber,
        officeAddress,
      });

      navigate("/login");
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div>
      <h2>Register</h2>
      {error && <p style={{ color: "red" }}>{error}</p>}

      <form onSubmit={handleRegister}>
        <div>
          <label>Firm Name:</label>
          <input type="text" value={firmName} onChange={(e) => setFirmName(e.target.value)} required />
        </div>

        <div>
          <label>Firm Type:</label>
          <input type="text" value={firmType} onChange={(e) => setFirmType(e.target.value)} required />
        </div>

        <div>
          <label>Firm Description:</label>
          <textarea value={firmDescription} onChange={(e) => setFirmDescription(e.target.value)} required />
        </div>

        <div>
          <label>Phone Number:</label>
          <input type="tel" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} required />
        </div>

        <div>
          <label>Email:</label>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>

        <div>
          <label>Website:</label>
          <input type="url" value={website} onChange={(e) => setWebsite(e.target.value)} required />
        </div>

        <div>
          <label>Specialization:</label>
          <input type="text" value={specialization} onChange={(e) => setSpecialization(e.target.value)} required />
        </div>

        <div>
          <label>Operating Hours:</label>
          <input type="text" value={operatingHours} onChange={(e) => setOperatingHours(e.target.value)} required />
        </div>

        <div>
          <label>License Number:</label>
          <input type="text" value={licenseNumber} onChange={(e) => setLicenseNumber(e.target.value)} required />
        </div>

        <div>
          <label>Office Address:</label>
          <input type="text" value={officeAddress} onChange={(e) => setOfficeAddress(e.target.value)} required />
        </div>

        <div>
          <label>Password:</label>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </div>

        <button type="submit">Register</button>
        <button type="button" onClick={() => navigate("/login")} style={{ marginLeft: "10px" }}>
          Back
        </button>
      </form>
    </div>
  );
};

export default Register;
