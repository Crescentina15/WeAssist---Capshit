import React, { useState, useEffect } from "react";
import { auth } from "./firebase";
import { onAuthStateChanged, signOut } from "firebase/auth";
import Login from "./Login";
import Register from "./Register";
import AdminPanel from "./AdminPanel";

const App = () => {
  const [user, setUser] = useState(null);
  const [showRegister, setShowRegister] = useState(false);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
    });
    return () => unsubscribe();
  }, []);

  if (showRegister) {
    return <Register onBack={() => setShowRegister(false)} />;
  }

  return user ? (
    <AdminPanel user={user} onLogout={() => signOut(auth)} />
  ) : (
    <Login onLogin={setUser} onRegister={() => setShowRegister(true)} />
  );
};

export default App;
