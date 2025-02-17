// Login.jsx
import React, { useState } from "react";
import { auth, db } from "./script/firebase";
import { signInWithEmailAndPassword, signOut } from "firebase/auth";
import { ref, get } from "firebase/database";
const Login = ({ onLogin, onRegister }) => {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const handleLogin = () => {
    signInWithEmailAndPassword(auth, email, password)
      .then((userCredential) => {
        const user = userCredential.user;
        // Change "users" to "law_firm_admin"
        get(ref(db, "law_firm_admin/" + user.uid)).then((snapshot) => {
          if (snapshot.exists()) {
            // Optionally, check for a role field if you stored one:
            // if (snapshot.val().role === "admin") { ... }
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
    <div>
      <h2>Admin Login</h2>
      <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
      <input type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} />
      <button onClick={handleLogin}>Login</button>
      <button onClick={onRegister}>Register</button>
    </div>
  );
};


export default Login;
