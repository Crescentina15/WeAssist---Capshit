// App.jsx
import React, { useState, useEffect } from "react";
import { auth } from "./firebase";
import { onAuthStateChanged, signOut } from "firebase/auth";
import Login from "./Login";
import AdminPanel from "./AdminPanel";

const App = () => {
  const [user, setUser] = useState(null);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
    });
    return () => unsubscribe();
  }, []);

  return user ? <AdminPanel user={user} onLogout={() => signOut(auth)} /> : <Login onLogin={setUser} />;
};

export default App;