import React, { useState, useEffect } from "react";
import { auth } from "./script/firebase";
import { onAuthStateChanged, signOut } from "firebase/auth";
import { BrowserRouter as Router, Route, Routes, Navigate } from "react-router-dom";
import Login from "./Login";
import Register from "./Register";
import AdminPanel from "./AdminPanel";
import Profile from "./Profile";  

const App = () => {
  const [user, setUser] = useState(null);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
    });
    return () => unsubscribe();
  }, []);

  const handleLogout = () => {
    signOut(auth)
      .then(() => {
        console.log("User logged out successfully");
      })
      .catch((error) => {
        console.error("Error signing out: ", error.message);
      });
  };

  return (
    <Router>
      <Routes>
        {/* Home Route - Redirects to AdminPanel if logged in, else Login */}
        <Route 
          path="/" 
          element={user ? <AdminPanel user={user} onLogout={handleLogout} /> : <Navigate to="/login" />} 
        />

        {/* Login Route */}
        <Route 
          path="/login" 
          element={user ? <Navigate to="/" /> : <Login onLogin={setUser} />} 
        />

        {/* Register Route */}
        <Route 
          path="/register" 
          element={user ? <Navigate to="/" /> : <Register />} 
        />

        {/* Profile Route - Only accessible if logged in */}
        <Route 
          path="/profile" 
          element={user ? <Profile user={user} /> : <Navigate to="/login" />} 
        />
      </Routes>
    </Router>
  );
};

export default App;
