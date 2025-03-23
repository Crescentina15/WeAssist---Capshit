import React, { useState } from "react";
import { auth, db } from "./script/firebase";
import { signInWithEmailAndPassword, signOut } from "firebase/auth";
import { ref, get } from "firebase/database";
import { useNavigate } from "react-router-dom";
import "./index.css"; // Make sure this CSS file exists in your project
import logo from "./assets/logo.png";

const Login = ({ onLogin }) => {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const navigate = useNavigate();

  const handleLogin = async (e) => {
    e.preventDefault();
  
    try {
      const userCredential = await signInWithEmailAndPassword(auth, email, password);
      const user = userCredential.user;
      const adminRef = ref(db, "law_firm_admin/" + user.uid);
      const snapshot = await get(adminRef);
  
      if (snapshot.exists()) {
        const adminData = snapshot.val();
        
        if (rememberMe) {
          localStorage.setItem("adminData", JSON.stringify(adminData));
        } else {
          sessionStorage.setItem("adminData", JSON.stringify(adminData));
        }
  
        onLogin(user, adminData);
      } else {
        alert("Access Denied: You are not an admin!");
        await signOut(auth);
      }
    } catch (error) {
      alert("Login failed: " + error.message);
    }
  };

  return (
    <div className="login-page">
      <div className="login-left">
        <div className="login-logo">
          <img src={logo} alt="WeAssist Logo" />
        </div>
        
        <div className="login-form-container">
          <h1>Welcome back</h1>
          <p className="login-subtitle">Please enter your details</p>
          
          <form onSubmit={handleLogin} className="login-form">
            <div className="form-group">
              <label htmlFor="email">Email address</label>
              <input
                type="email"
                id="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                style={{ color: "black" }}
              />
            </div>
            
            <div className="form-group">
              <label htmlFor="password">Password</label>
              <input
                type="password"
                id="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                style={{ color: "black" }}
              />
            </div>
            
          
            
            <button type="submit" className="signin-button">
              Sign in
            </button>
            
          
          </form>
          
          <div className="signup-prompt">
            Don't have an account? <button className="signup-link" onClick={() => navigate("/register")}>Sign up</button>
          </div>
        </div>
      </div>
      
      <div className="login-right">
        <div className="illustration-container">
         
        </div>
      </div>
    </div>
  );
};

export default Login;