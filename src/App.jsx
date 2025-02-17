// App.jsx
import React, { useState, useEffect } from "react";
import { BrowserRouter as Router, Routes, Route, Navigate } from "react-router-dom";
import { auth } from "./firebase";
import { onAuthStateChanged, signOut } from "firebase/auth";
import Login from "./Login";
import AdminPanel from "./AdminPanel";
import Register from "./Register";

const App = () => {
  const [user, setUser] = useState(null);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
    });
    return () => unsubscribe();
  }, []);

  return (
    <Router>
      <Routes>
        {/* Login Route */}
        <Route
          path="/login"
          element={
            user
              ? <AdminPanel user={user} onLogout={() => signOut(auth)} />
              : <Login onLogin={setUser} />
          }
        />

        {/* Register Route */}
        <Route path="/register" element={<Register />} />

        {/* Root Route: If there's a user, show AdminPanel; otherwise, go to /login */}
        <Route
          path="/"
          element={
            user
              ? <AdminPanel user={user} onLogout={() => signOut(auth)} />
              : <Navigate to="/login" replace />
          }
        />
      </Routes>
    </Router>
  );
};

export default App;
