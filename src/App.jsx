import React, { useState, useEffect } from "react";
import { auth, db } from "./firebase";
import { signInWithEmailAndPassword, signOut, onAuthStateChanged } from "firebase/auth";
import { ref, set, push, onValue, get } from "firebase/database";

const App = () => {
  const [user, setUser] = useState(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [secretary, setSecretary] = useState({ name: "", email: "", phone: "" });
  const [lawyer, setLawyer] = useState({ name: "", specialization: "", lawFirm: "", licenseNumber: "", experience: "", contact: { phone: "", email: "", address: "" } });
  const [lawyers, setLawyers] = useState([]);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
    });
    return () => unsubscribe();
  }, []);

  const handleLogin = () => {
    signInWithEmailAndPassword(auth, email, password)
      .then((userCredential) => {
        const user = userCredential.user;
        get(ref(db, "users/" + user.uid)).then((snapshot) => {
          if (snapshot.exists() && snapshot.val().role === "admin") {
            setUser(user);
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

  const handleLogout = () => {
    signOut(auth).then(() => {
      setUser(null);
    });
  };

  const addSecretary = () => {
    const newRef = push(ref(db, "secretaries"));
    set(newRef, secretary).then(() => {
      alert("Secretary added successfully!");
      setSecretary({ name: "", email: "", phone: "" });
    }).catch(error => alert("Error: " + error.message));
  };

  const addLawyer = () => {
    const newRef = push(ref(db, "lawyers"));
    set(newRef, lawyer).then(() => {
      alert("Lawyer added successfully!");
      setLawyer({ name: "", specialization: "", lawFirm: "", licenseNumber: "", experience: "", contact: { phone: "", email: "", address: "" } });
    }).catch(error => alert("Error: " + error.message));
  };

  const fetchLawyers = () => {
    onValue(ref(db, "lawyers"), (snapshot) => {
      setLawyers(snapshot.val() ? Object.values(snapshot.val()) : []);
    });
  };

  if (!user) {
    return (
      <div>
        <h2>Admin Login</h2>
        <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <input type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <button onClick={handleLogin}>Login</button>
      </div>
    );
  }

  return (
    <div>
      <h1>Law Firm Admin Panel</h1>
      <button onClick={handleLogout}>Logout</button>

      <h2>Add Secretary</h2>
      <input placeholder="Name" value={secretary.name} onChange={(e) => setSecretary({ ...secretary, name: e.target.value })} />
      <input placeholder="Email" value={secretary.email} onChange={(e) => setSecretary({ ...secretary, email: e.target.value })} />
      <input placeholder="Phone" value={secretary.phone} onChange={(e) => setSecretary({ ...secretary, phone: e.target.value })} />
      <button onClick={addSecretary}>Add Secretary</button>

      <h2>Add Lawyer</h2>
      <input placeholder="Name" value={lawyer.name} onChange={(e) => setLawyer({ ...lawyer, name: e.target.value })} />
      <input placeholder="Specialization" value={lawyer.specialization} onChange={(e) => setLawyer({ ...lawyer, specialization: e.target.value })} />
      <input placeholder="Law Firm" value={lawyer.lawFirm} onChange={(e) => setLawyer({ ...lawyer, lawFirm: e.target.value })} />
      <input placeholder="License Number" value={lawyer.licenseNumber} onChange={(e) => setLawyer({ ...lawyer, licenseNumber: e.target.value })} />
      <input placeholder="Experience (years)" value={lawyer.experience} onChange={(e) => setLawyer({ ...lawyer, experience: e.target.value })} />
      <input placeholder="Phone" value={lawyer.contact.phone} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, phone: e.target.value } })} />
      <input placeholder="Email" value={lawyer.contact.email} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, email: e.target.value } })} />
      <input placeholder="Address" value={lawyer.contact.address} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, address: e.target.value } })} />
      <button onClick={addLawyer}>Add Lawyer</button>

      <h2>View Lawyers</h2>
      <button onClick={fetchLawyers}>Refresh Lawyers List</button>
      <ul>
        {lawyers.map((lawyer, index) => (
          <li key={index}>
            {lawyer.name} - {lawyer.specialization} - {lawyer.lawFirm} - License: {lawyer.licenseNumber} - {lawyer.experience} years - {lawyer.contact.phone} - {lawyer.contact.email} - {lawyer.contact.address}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default App;
