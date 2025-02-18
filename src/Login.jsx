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

  const handleLogin = (e) => {
    e.preventDefault(); 

    signInWithEmailAndPassword(auth, email, password)
      .then((userCredential) => {
        const user = userCredential.user;
        get(ref(db, "law_firm_admin/" + user.uid)).then((snapshot) => {
          if (snapshot.exists()) {
            onLogin(user);
          } else {
            alert("Access Denied: You are not an admin!");
            signOut(auth);
          }
        });
      })
      .catch((error) => {
        alert("Login failed: " + error.message);
      });
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
        <p>Don't have an account?</p>
        <button className="register-btn" onClick={() => navigate("/register")}>
          Register
        </button>
      </div>
    </div>
  );
};

export default Login;
