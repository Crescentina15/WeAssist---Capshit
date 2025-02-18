import React, { useState, useEffect } from "react";
import { auth } from "./script/firebase";
import { onAuthStateChanged, signOut } from "firebase/auth";
import Login from "./Login";
import Register from "./Register";
import AdminPanel from "./AdminPanel";
import Profile from "./Profile";  // Import Profile component
import { BrowserRouter as Router, Route, Routes } from "react-router-dom";  // Import routing components

const App = () => {
  const [user, setUser] = useState(null);
  const [showRegister, setShowRegister] = useState(false);

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

  if (showRegister) {
    return <Register onBack={() => setShowRegister(false)} />;
  }

  return (
    <Router>
      <Routes>
        <Route
          path="/"
          element={
            user ? (
              <AdminPanel user={user} onLogout={handleLogout} />
            ) : (
              <Login onLogin={setUser} onRegister={() => setShowRegister(true)} />
            )
          }
        />
        <Route path="/profile" element={<Profile user={user} />} />  {/* Add route to Profile page */}
      </Routes>
    </Router>
  );
};

export default App;
