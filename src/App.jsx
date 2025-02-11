import React, { useState, useEffect } from "react";
import { auth, db } from "./firebase";
import { 
  createUserWithEmailAndPassword, 
  signInWithEmailAndPassword, 
  signOut, 
  onAuthStateChanged, 
  sendEmailVerification 
} from "firebase/auth";
import { ref, set, onValue, get } from "firebase/database";

const App = () => {
  const [user, setUser] = useState(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [secretary, setSecretary] = useState({ name: "", email: "", phone: "", password: "" });
  const [lawyer, setLawyer] = useState({ name: "", specialization: "", lawFirm: "", licenseNumber: "", experience: "", contact: { phone: "", email: "", address: "" }, password: "" });
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
    createUserWithEmailAndPassword(auth, secretary.email, secretary.password)
      .then((userCredential) => {
        const newSecretaryRef = ref(db, "secretaries/" + userCredential.user.uid);
        set(newSecretaryRef, { 
          name: secretary.name, 
          email: secretary.email, 
          phone: secretary.phone, 
          role: "secretary" 
        }).then(() => {
          sendEmailVerification(userCredential.user).then(() => {
            alert("Secretary account created successfully! Verification email sent.");
          });
          setSecretary({ name: "", email: "", phone: "", password: "" });
        });
      })
      .catch(error => alert("Error: " + error.message));
  };

  const addLawyer = () => {
    createUserWithEmailAndPassword(auth, lawyer.contact.email, lawyer.password)
      .then((userCredential) => {
        const user = userCredential.user;
  
        // Store lawyer details in Firebase Realtime Database
        const newLawyerRef = ref(db, "lawyers/" + user.uid);
        set(newLawyerRef, {
          name: lawyer.name,
          specialization: lawyer.specialization,
          lawFirm: lawyer.lawFirm,
          licenseNumber: lawyer.licenseNumber,
          experience: lawyer.experience,
          contact: {
            phone: lawyer.contact.phone,
            email: lawyer.contact.email,
            address: lawyer.contact.address
          },
          role: "lawyer",
          verified: false // Store verification status
        }).then(() => {
          // Send email verification
          sendEmailVerification(user).then(() => {
            alert("Lawyer account created successfully! Verification email sent.");
  
            // Reset input fields after success
            setLawyer({
              name: "",
              specialization: "",
              lawFirm: "",
              licenseNumber: "",
              experience: "",
              contact: { phone: "", email: "", address: "" },
              password: ""
            });
          });
        });
      })
      .catch(error => alert("Error: " + error.message));
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

      <h2>Manage Secretary</h2>
      <input type="text" placeholder="Secretary Name" value={secretary.name} onChange={(e) => setSecretary({ ...secretary, name: e.target.value })} />
      <input type="email" placeholder="Secretary Email" value={secretary.email} onChange={(e) => setSecretary({ ...secretary, email: e.target.value })} />
      <input type="text" placeholder="Secretary Phone" value={secretary.phone} onChange={(e) => setSecretary({ ...secretary, phone: e.target.value })} />
      <input type="password" placeholder="Secretary Password" value={secretary.password} onChange={(e) => setSecretary({ ...secretary, password: e.target.value })} />
      <button onClick={addSecretary}>Add Secretary</button>

      <h2>Manage Lawyer</h2>
      <input type="text" placeholder="Lawyer Name" value={lawyer.name} onChange={(e) => setLawyer({ ...lawyer, name: e.target.value })} />
      <input type="text" placeholder="Specialization" value={lawyer.specialization} onChange={(e) => setLawyer({ ...lawyer, specialization: e.target.value })} />
      <input type="text" placeholder="Law Firm" value={lawyer.lawFirm} onChange={(e) => setLawyer({ ...lawyer, lawFirm: e.target.value })} />
      <input type="text" placeholder="License Number" value={lawyer.licenseNumber} onChange={(e) => setLawyer({ ...lawyer, licenseNumber: e.target.value })} />
      <input type="text" placeholder="Experience" value={lawyer.experience} onChange={(e) => setLawyer({ ...lawyer, experience: e.target.value })} />
      <input type="text" placeholder="Phone" value={lawyer.contact.phone} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, phone: e.target.value } })} />
      <input type="email" placeholder="Email" value={lawyer.contact.email} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, email: e.target.value } })} />
      <input type="text" placeholder="Address" value={lawyer.contact.address} onChange={(e) => setLawyer({ ...lawyer, contact: { ...lawyer.contact, address: e.target.value } })} />
      <input type="password" placeholder="Password" value={lawyer.password} onChange={(e) => setLawyer({ ...lawyer, password: e.target.value })} />
      <button onClick={addLawyer}>Add Lawyer</button>

      <h2>View Lawyers</h2>
      <button onClick={fetchLawyers}>Refresh Lawyers List</button>
      <ul>
        {lawyers.map((lawyer, index) => (
          <li key={index}>
            <strong>{index + 1}. Name:</strong> {lawyer.name} <br />
            <strong>Specialization:</strong> {lawyer.specialization} <br />
            <strong>Law Firm:</strong> {lawyer.lawFirm} <br />
            <strong>License Number:</strong> {lawyer.licenseNumber} <br />
            <strong>Experience:</strong> {lawyer.experience} <br />
            <strong>Phone:</strong> {lawyer.contact?.phone} <br />
            <strong>Email:</strong> {lawyer.contact?.email} <br />
            <strong>Address:</strong> {lawyer.contact?.address} <br />
          </li>
        ))}
      </ul>
    </div>
  );
};

export default App;
