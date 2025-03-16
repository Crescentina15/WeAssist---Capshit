import React, { useState, useEffect } from "react";
import { auth } from "./script/firebase";
import { onAuthStateChanged, signOut } from "firebase/auth";
import { BrowserRouter as Router, Route, Routes, Navigate } from "react-router-dom";
import Login from "./Login";
import Register from "./Register";
import AdminPanel from "./AdminPanel";
import Profile from "./Profile";
import AddLawyer from "./AddLawyer";
import ManageSecretary from "./ManageSecretary";
import EditLawyer from "./EditLawyer";
import Privacy from "./PrivacyPolicy";
import PlansSubscription from "./PlansSubscription"; 
import Payment from "./Payment"; 

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
        <Route 
          path="/" 
          element={user ? <AdminPanel user={user} onLogout={handleLogout} /> : <Navigate to="/login" />} 
        />
        <Route 
          path="/login" 
          element={user ? <Navigate to="/" /> : <Login onLogin={setUser} />} 
        />
        <Route 
          path="/EditLawyer/:id" 
          element={user ? <EditLawyer /> : <Navigate to="/login" />} 
        />
        <Route 
          path="/register" 
          element={user ? <Navigate to="/" /> : <Register />} 
        />
        <Route 
          path="/profile" 
          element={user ? <Profile user={user} /> : <Navigate to="/login" />} 
        />
        <Route 
          path="/addlawyer" 
          element={user ? <AddLawyer /> : <Navigate to="/login" />} 
        />
        <Route 
          path="/managesecretary" 
          element={user ? <ManageSecretary /> : <Navigate to="/login" />} 
        />
        <Route 
          path="/privacy" 
          element={user ? <Privacy /> : <Navigate to="/login" />} 
        />
        <Route 
          path="/plans-subscription" 
          element={user ? <PlansSubscription /> : <Navigate to="/login" />} 
        />
        <Route 
          path="/payment" 
          element={user ? <Payment /> : <Navigate to="/login" />} // 
        />
      </Routes>
    </Router>
  );
};

export default App;
