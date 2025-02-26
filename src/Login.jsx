import React, { useState } from "react";
import { auth, db } from "./script/firebase";
import { signInWithEmailAndPassword, signOut } from "firebase/auth";
import { ref, get } from "firebase/database";
import { useNavigate } from "react-router-dom"; // Import for navigation
import "./index.css"; 
import logo from "./assets/logo.png"; 

const Login = ({ onLogin }) => {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const navigate = useNavigate(); // Hook for navigation

  const handleLogin = async (e) => {
    e.preventDefault(); 
  
    try {
      const userCredential = await signInWithEmailAndPassword(auth, email, password);
      const user = userCredential.user;
      const adminRef = ref(db, "law_firm_admin/" + user.uid);
      const snapshot = await get(adminRef);
  
      if (snapshot.exists()) {
        const adminData = snapshot.val(); // Get admin details
  
        // Store admin details in session/local storage (optional)
        localStorage.setItem("adminData", JSON.stringify(adminData));
  
        onLogin(user, adminData); // Pass both user & admin data
      } else {
        alert("Access Denied: You are not an admin!");
        await signOut(auth);
      }
    } catch (error) {
      alert("Login failed: " + error.message);
    }
  };
  

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="logo-container">
          <img src={logo} alt="Law Firm Logo" />
        </div>
        <h2>Admin Login</h2>
        <form onSubmit={handleLogin}>
          <input
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            style={{ color: "black" }}
          />
          <input 
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
         
          />
          <button className="login-btn" type="submit">
            Login
          </button>
        </form>
        <p> Don't have an account?</p>
        <button className="register-btn" onClick={() => navigate("/register")}>
          Register
        </button>
      </div>
    </div>
  );
};

export default Login;
